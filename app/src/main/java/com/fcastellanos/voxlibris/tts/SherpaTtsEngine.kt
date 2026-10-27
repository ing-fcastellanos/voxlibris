package com.fcastellanos.voxlibris.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.navigator.media.tts.TtsEngine
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Language

/**
 * Motor de voz para el TtsNavigator de Readium: sintetiza cada frase con sherpa-onnx y la
 * reproduce con un AudioTrack.
 *
 * Readium pide la frase siguiente recién cuando recibe `onDone` de la actual. Para que no haya
 * silencio entre frases, `onDone` se emite cuando al audio en cola le quedan pocos segundos
 * (el tiempo estimado de síntesis de la próxima frase); la siguiente se escribe a continuación
 * en el mismo AudioTrack y suena sin corte. `onStart` sí se emite cuando la frase empieza a
 * sonar de verdad, para que el resaltado sea fiel.
 */
@OptIn(ExperimentalReadiumApi::class)
class SherpaTtsEngine(
    private val runtime: SherpaTtsRuntime,
    private val tts: OfflineTts,
    private val contentLanguage: Language?,
    initialPreferences: SherpaTtsPreferences,
    private val calibratedRtf: Double?,
    private val events: MutableSharedFlow<Event>,
) : TtsEngine<SherpaTtsSettings, SherpaTtsPreferences, SherpaTtsError, SherpaVoice> {

    sealed interface Event {
        /** La frase empezó a sonar (no solo a sintetizarse). */
        data class UtteranceStarted(val requestId: String) : Event
    }

    private class Request(val id: TtsEngine.RequestId, val text: String) {
        @Volatile var cancelled = false
        @Volatile var started = false
        var lastRange: IntRange? = null
    }

    override val voices: Set<SherpaVoice> = SupertonicModel.VOICES.toSet()

    private val settingsFlow = MutableStateFlow(initialPreferences.resolve(contentLanguage))
    override val settings: StateFlow<SherpaTtsSettings> = settingsFlow.asStateFlow()

    private val rtf = TtsMath.RtfEstimator(calibratedRtf ?: 0.5)

    @Volatile private var listener: TtsEngine.Listener<SherpaTtsError>? = null
    @Volatile private var current: Request? = null
    private val closed = AtomicBoolean(false)

    private val synthExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "voxlibris-tts-synth") }
    private val playExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "voxlibris-tts-play").apply { priority = Thread.MAX_PRIORITY }
    }

    // Solo se tocan desde el hilo de reproducción (salvo pause(), que es seguro desde cualquier hilo).
    @Volatile private var track: AudioTrack? = null
    private var framesWritten = 0L

    override fun submitPreferences(preferences: SherpaTtsPreferences) {
        settingsFlow.value = preferences.resolve(contentLanguage)
    }

    override fun setListener(listener: TtsEngine.Listener<SherpaTtsError>?) {
        this.listener = listener
    }

    override fun speak(requestId: TtsEngine.RequestId, text: String, language: Language?) {
        if (closed.get()) return
        val request = Request(requestId, text)
        current = request
        val settings = settingsFlow.value
        synthExecutor.execute {
            if (request.cancelled) return@execute
            val audio = try {
                synthesize(text, language, settings)
            } catch (e: Throwable) {
                if (!request.cancelled) {
                    listener?.onError(requestId, SherpaTtsError.Synthesis(e.message ?: e.javaClass.simpleName))
                }
                return@execute
            }
            if (request.cancelled) return@execute
            if (audio == null) {
                listener?.onDone(requestId)
                return@execute
            }
            playExecutor.execute { play(request, audio.first, audio.second) }
        }
    }

    override fun stop() {
        interrupt()
    }

    /** Corta el audio de inmediato (también el que ya estaba en cola) y descarta la frase en curso. */
    fun interrupt() {
        val request = current
        current = null
        request?.cancelled = true
        runCatching { track?.pause() } // silencio inmediato; además interrumpe un write bloqueante
        playExecutor.execute {
            runCatching { track?.flush() }
            framesWritten = 0
        }
        request?.let { listener?.onInterrupted(it.id) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        current?.cancelled = true
        current = null
        playExecutor.execute {
            track?.let { t ->
                val remaining = ((framesWritten - headPosition(t)).toDouble() / t.sampleRate).coerceIn(0.0, 3.0)
                runCatching { t.stop() } // deja sonar lo que queda en cola
                SystemClock.sleep((remaining * 1000).toLong() + 50)
                runCatching { t.release() }
            }
            track = null
        }
        playExecutor.shutdown()
        synthExecutor.shutdown()
    }

    private fun synthesize(text: String, language: Language?, settings: SherpaTtsSettings): Pair<FloatArray, Int>? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val languageCode = when {
            settings.overrideContentLanguage -> SupertonicModel.languageCode(settings.language)
            else -> SupertonicModel.languageCode(language) ?: SupertonicModel.languageCode(settings.language)
        } ?: "es"
        val steps = TtsMath.numSteps(settings.quality, calibratedRtf)
        val start = SystemClock.elapsedRealtime()
        val generated = runtime.synthesize(tts, clean, settings.voiceId, settings.speed, steps, languageCode)
        val samples = TtsMath.trimTrailingSilence(generated.samples, generated.sampleRate)
        rtf.update((SystemClock.elapsedRealtime() - start) / 1000.0, samples.size.toDouble() / generated.sampleRate)
        return samples to generated.sampleRate
    }

    private fun play(request: Request, samples: FloatArray, sampleRate: Int) {
        if (request.cancelled || closed.get()) return
        val track = ensureTrack(sampleRate) ?: run {
            listener?.onError(request.id, SherpaTtsError.Audio("no se pudo crear la salida de audio"))
            return
        }
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()

        val startFrame = framesWritten
        val endFrame = startFrame + samples.size
        val lead = TtsMath.leadSeconds(rtf.value)
        val chunk = sampleRate / 10
        var offset = 0
        while (offset < samples.size) {
            if (request.cancelled) return
            val count = minOf(chunk, samples.size - offset)
            val written = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                listener?.onError(request.id, SherpaTtsError.Audio("write devolvió $written"))
                return
            }
            if (written == 0) SystemClock.sleep(5)
            offset += written
            framesWritten += written
            reportProgress(request, track, startFrame, endFrame)
        }
        // Todo en cola: esperamos hasta que quede poco por sonar y recién entonces avisamos onDone,
        // para que Readium pida la frase siguiente con tiempo de sintetizarla.
        while (!request.cancelled) {
            reportProgress(request, track, startFrame, endFrame)
            val remaining = (endFrame - headPosition(track)).toDouble() / sampleRate
            if (remaining <= lead) break
            SystemClock.sleep(((remaining - lead) * 1000).toLong().coerceIn(5L, 50L))
        }
        if (request.cancelled) return
        markStarted(request)
        listener?.onDone(request.id)
    }

    private fun reportProgress(request: Request, track: AudioTrack, startFrame: Long, endFrame: Long) {
        val head = headPosition(track)
        if (head < startFrame) return
        markStarted(request)
        val fraction = (head - startFrame).toDouble() / (endFrame - startFrame).coerceAtLeast(1L)
        val range = TtsMath.wordRangeAt(request.text, fraction) ?: return
        if (range != request.lastRange) {
            request.lastRange = range
            listener?.onRange(request.id, range)
        }
    }

    private fun markStarted(request: Request) {
        if (request.started) return
        request.started = true
        listener?.onStart(request.id)
        events.tryEmit(Event.UtteranceStarted(request.id.value))
    }

    private fun headPosition(track: AudioTrack): Long = track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL

    private fun ensureTrack(sampleRate: Int): AudioTrack? {
        track?.let { existing ->
            if (existing.sampleRate == sampleRate) return existing
            runCatching { existing.release() }
            track = null
        }
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val bufferBytes = maxOf(minBuffer, sampleRate * Float.SIZE_BYTES * BUFFER_SECONDS)
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also {
                    track = it
                    framesWritten = 0
                }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val BUFFER_SECONDS = 3
    }
}
