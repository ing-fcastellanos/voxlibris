package com.fcastellanos.voxlibris.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
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
                Log.w(TAG, "Falló la síntesis de la frase ${requestId.value.take(8)}", e)
                if (!request.cancelled) {
                    listener?.onError(requestId, SherpaTtsError.Synthesis(e.message ?: e.javaClass.simpleName))
                }
                return@execute
            }
            if (request.cancelled) {
                Log.d(TAG, "Síntesis descartada (frase cancelada ${requestId.value.take(8)})")
                return@execute
            }
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

    /**
     * Corta el audio de inmediato (también el que ya estaba en cola) y descarta la frase en curso.
     * El AudioTrack se libera y la próxima frase crea uno nuevo: así el cabezal de reproducción
     * vuelve a cero de forma fiable en cualquier dispositivo.
     */
    fun interrupt() {
        val request = current
        current = null
        request?.cancelled = true
        val stale = track
        runCatching { stale?.pause() } // silencio inmediato; además interrumpe un write bloqueante
        Log.d(TAG, "interrupt(): frase=${request?.id?.value?.take(8)} track=${stale != null}")
        playExecutor.execute {
            if (track === stale) {
                stale?.let { runCatching { it.flush() }; runCatching { it.release() } }
                track = null
                framesWritten = 0
            }
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
        val synthSeconds = (SystemClock.elapsedRealtime() - start) / 1000.0
        val audioSeconds = samples.size.toDouble() / generated.sampleRate
        rtf.update(synthSeconds, audioSeconds)
        Log.d(TAG, "Frase sintetizada: %.2f s de audio en %.2f s (RTF=%.2f, pasos=%d, voz=%d, lang=%s, lead=%.1f s)".format(audioSeconds, synthSeconds, if (audioSeconds > 0) synthSeconds / audioSeconds else 0.0, steps, settings.voiceId, languageCode, TtsMath.leadSeconds(rtf.value)))
        return samples to generated.sampleRate
    }

    private fun play(request: Request, samples: FloatArray, sampleRate: Int, attempt: Int = 0) {
        if (request.cancelled || closed.get()) return
        val track = ensureTrack(sampleRate) ?: run {
            Log.w(TAG, "No se pudo crear el AudioTrack")
            listener?.onError(request.id, SherpaTtsError.Audio("no se pudo crear la salida de audio"))
            return
        }
        val startFrame = framesWritten
        val endFrame = startFrame + samples.size
        val lead = TtsMath.leadSeconds(rtf.value)
        Log.d(TAG, "play(): frase=${request.id.value.take(8)} ${"%.2f".format(samples.size.toDouble() / sampleRate)} s, cola previa=${"%.2f".format((startFrame - headPosition(track)).toDouble() / sampleRate)} s, lead=${"%.1f".format(lead)} s" + if (attempt > 0) " (reintento $attempt)" else "")

        val chunk = sampleRate / 10
        var offset = 0
        var lastHead = headPosition(track)
        var lastAdvanceAt = SystemClock.elapsedRealtime()
        var restarted = false
        while (!request.cancelled) {
            // 1) Escribir lo que quepa, sin bloquear. El track arranca recién cuando ya tiene datos:
            //    un track vacío en reproducción sufre underrun y AudioFlinger puede deshabilitarlo.
            if (offset < samples.size) {
                val count = minOf(chunk, samples.size - offset)
                val written = track.write(samples, offset, count, AudioTrack.WRITE_NON_BLOCKING)
                if (written < 0) {
                    Log.w(TAG, "AudioTrack.write devolvió $written (estado=${track.playState})")
                    listener?.onError(request.id, SherpaTtsError.Audio("write devolvió $written"))
                    return
                }
                offset += written
                framesWritten += written
                if (written > 0 && track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
            }

            // 2) Progreso y fin anticipado para que Readium pida la frase siguiente a tiempo.
            reportProgress(request, track, startFrame, endFrame)
            val head = headPosition(track)
            if (offset >= samples.size && (endFrame - head).toDouble() / sampleRate <= lead) break

            // 3) Detección de atasco: el cabezal no avanza aunque hay audio pendiente.
            val now = SystemClock.elapsedRealtime()
            if (head != lastHead) {
                lastHead = head
                lastAdvanceAt = now
            } else if (framesWritten > head && now - lastAdvanceAt > STALL_MS) {
                if (!restarted) {
                    Log.w(TAG, "AudioTrack sin avance durante ${now - lastAdvanceAt} ms (estado=${track.playState}); reintento play()")
                    runCatching { track.play() }
                    restarted = true
                    lastAdvanceAt = now
                } else if (attempt < MAX_PLAY_ATTEMPTS) {
                    Log.w(TAG, "AudioTrack sigue sin avanzar; lo recreo y repito lo que faltaba")
                    val played = (head - startFrame).coerceIn(0L, samples.size.toLong()).toInt()
                    runCatching { track.pause() }
                    runCatching { track.flush() }
                    runCatching { track.release() }
                    this.track = null
                    framesWritten = 0
                    play(request, samples.copyOfRange(played, samples.size), sampleRate, attempt + 1)
                    return
                } else {
                    Log.w(TAG, "La salida de audio no responde; abandono la frase ${request.id.value.take(8)}")
                    listener?.onError(request.id, SherpaTtsError.Audio("la salida de audio se detuvo"))
                    return
                }
            }
            SystemClock.sleep(if (offset < samples.size) 10L else 30L)
        }
        if (request.cancelled) return
        markStarted(request)
        Log.d(TAG, "onDone anticipado: frase=${request.id.value.take(8)} quedan ${"%.1f".format((endFrame - headPosition(track)).toDouble() / sampleRate)} s en cola")
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
        const val TAG = "VoxLibris.TTS"
        const val STALL_MS = 600L
        const val MAX_PLAY_ATTEMPTS = 2
        /** Debe superar TtsMath.MAX_LEAD_SECONDS para que la frase siguiente quepa detrás de la actual. */
        const val BUFFER_SECONDS = 8
    }
}
