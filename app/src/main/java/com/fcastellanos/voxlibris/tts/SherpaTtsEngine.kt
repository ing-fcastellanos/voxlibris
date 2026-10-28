package com.fcastellanos.voxlibris.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
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
 * Cada frase se parte en trozos cortos que se sintetizan en orden y se escriben al AudioTrack a
 * medida que están listos; así la primera palabra suena antes y cancelar desperdicia poco trabajo.
 *
 * Readium pide la frase siguiente recién cuando recibe `onDone` de la actual. Para que no haya
 * silencio entre frases, `onDone` se emite cuando lo que falta por sonar (encolado, sin escribir y
 * sin sintetizar) baja del tiempo estimado de síntesis de la próxima frase; la siguiente se escribe
 * a continuación en el mismo hilo y suena sin corte. `onStart` sí se emite cuando la frase empieza
 * a sonar de verdad, para que el resaltado sea fiel.
 *
 * La velocidad por encima de [SherpaTtsDefaults.MAX_SYNTH_SPEED] no se pide al modelo (se come
 * sílabas): se sintetiza al tope y el AudioTrack estira el audio conservando el tono.
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

    private class Request(
        val id: TtsEngine.RequestId,
        val text: String,
        val pieces: List<TtsMath.Piece>,
        val playbackRate: Float,
    ) {
        @Volatile var cancelled = false
        @Volatile var started = false
        @Volatile var doneReported = false
        /** Caracteres de los trozos que todavía no se sintetizaron. */
        @Volatile var charsPending: Int = pieces.sumOf { it.text.length }
        /** Muestras ya sintetizadas que todavía no se escribieron al AudioTrack. */
        val samplesPending = AtomicLong(0)
        @Volatile var firstStartFrame: Long = -1
        var lastRange: IntRange? = null
    }

    override val voices: Set<SherpaVoice> = SupertonicModel.VOICES.toSet()

    private val settingsFlow = MutableStateFlow(initialPreferences.resolve(contentLanguage))
    override val settings: StateFlow<SherpaTtsSettings> = settingsFlow.asStateFlow()

    private val rtf = TtsMath.RtfEstimator(calibratedRtf ?: 0.5)

    /** Segundos de audio por carácter, para estimar lo que falta de los trozos sin sintetizar. */
    @Volatile private var secondsPerChar = 0.07

    @Volatile private var listener: TtsEngine.Listener<SherpaTtsError>? = null
    @Volatile private var current: Request? = null
    /** Frase cuyo audio se está escribiendo ahora; puede ser distinta de [current] por el aviso anticipado. */
    @Volatile private var playing: Request? = null
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
        val settings = settingsFlow.value
        val synthSpeed = minOf(settings.speed, SherpaTtsDefaults.MAX_SYNTH_SPEED)
        val playbackRate = (settings.speed / synthSpeed).toFloat()
        val pieces = TtsMath.splitForSynthesis(text)
        val request = Request(requestId, text, pieces, playbackRate)
        current = request
        if (pieces.isEmpty()) {
            listener?.onDone(requestId)
            return
        }
        synthExecutor.execute {
            // Si el primer trozo es muy corto, se retiene hasta tener el segundo: así no hay un
            // hueco justo después de arrancar la frase.
            var held: Runnable? = null
            for ((index, piece) in pieces.withIndex()) {
                if (request.cancelled) {
                    Log.d(TAG, "Síntesis descartada (frase cancelada ${requestId.value.take(8)}, trozo ${index + 1}/${pieces.size})")
                    return@execute
                }
                val audio = try {
                    synthesize(piece.text, language, settings, synthSpeed)
                } catch (e: Throwable) {
                    Log.w(TAG, "Falló la síntesis de la frase ${requestId.value.take(8)}", e)
                    if (!request.cancelled) {
                        listener?.onError(requestId, SherpaTtsError.Synthesis(e.message ?: e.javaClass.simpleName))
                    }
                    return@execute
                }
                request.charsPending -= piece.text.length
                if (request.cancelled) {
                    Log.d(TAG, "Síntesis descartada (frase cancelada ${requestId.value.take(8)}, trozo ${index + 1}/${pieces.size})")
                    return@execute
                }
                val isLast = index == pieces.lastIndex
                if (audio == null) {
                    if (isLast) playExecutor.execute { finishIfNeeded(request) }
                    continue
                }
                // Entre trozos no debe quedar el silencio que el modelo pone al principio y al final
                // de cada síntesis: sonaría como una pausa que el texto no tiene.
                var samples = audio.first
                val sampleRate = audio.second
                if (index > 0) samples = TtsMath.trimLeadingSilence(samples, sampleRate, keepMs = LEADING_KEEP_MS)
                if (!isLast) samples = TtsMath.trimTrailingSilence(samples, sampleRate, keepMs = JOIN_KEEP_MS)
                if (pieces.size > 1) samples = TtsMath.fadeEdges(samples, sampleRate, fadeInMs = if (index > 0) EDGE_FADE_MS else 0, fadeOutMs = if (!isLast) EDGE_FADE_MS else 0)
                request.samplesPending.addAndGet(samples.size.toLong())
                val task = Runnable { playPiece(request, piece, index, samples, sampleRate, isLast) }
                if (index == 0 && !isLast && samples.size < sampleRate * HOLD_FIRST_PIECE_UNDER_SECONDS) {
                    held = task
                    continue
                }
                held?.let { playExecutor.execute(it) }
                held = null
                playExecutor.execute(task)
            }
            held?.let { if (!request.cancelled) playExecutor.execute(it) }
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
        playing?.cancelled = true
        val stale = track
        runCatching { stale?.pause() } // silencio inmediato
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
                val remaining = ((framesWritten - headPosition(t)).toDouble() / t.sampleRate).coerceIn(0.0, 1.0)
                runCatching { t.stop() } // deja sonar lo que queda en cola
                SystemClock.sleep((remaining * 1000).toLong() + 50)
                runCatching { t.release() }
            }
            track = null
        }
        playExecutor.shutdown()
        synthExecutor.shutdown()
    }

    private fun synthesize(text: String, language: Language?, settings: SherpaTtsSettings, synthSpeed: Double): Pair<FloatArray, Int>? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val languageCode = when {
            settings.overrideContentLanguage -> SupertonicModel.languageCode(settings.language)
            else -> SupertonicModel.languageCode(language) ?: SupertonicModel.languageCode(settings.language)
        } ?: "es"
        val steps = TtsMath.numSteps(settings.quality, calibratedRtf)
        val start = SystemClock.elapsedRealtime()
        val generated = runtime.synthesize(tts, clean, settings.voiceId, synthSpeed, steps, languageCode)
        val samples = TtsMath.trimTrailingSilence(generated.samples, generated.sampleRate)
        val synthSeconds = (SystemClock.elapsedRealtime() - start) / 1000.0
        val audioSeconds = samples.size.toDouble() / generated.sampleRate
        rtf.update(synthSeconds, audioSeconds)
        if (clean.length >= 20 && audioSeconds > 0.2) {
            secondsPerChar = secondsPerChar * 0.7 + (audioSeconds / clean.length) * 0.3
        }
        Log.d(TAG, "Trozo sintetizado: %.2f s de audio en %.2f s (RTF=%.2f, pasos=%d, voz=%d, lang=%s, %d chars, lead=%.1f s)".format(audioSeconds, synthSeconds, if (audioSeconds > 0) synthSeconds / audioSeconds else 0.0, steps, settings.voiceId, languageCode, clean.length, TtsMath.leadSeconds(rtf.value)))
        return samples to generated.sampleRate
    }

    private fun finishIfNeeded(request: Request) {
        if (request.cancelled || request.doneReported) return
        request.doneReported = true
        markStarted(request)
        Log.d(TAG, "onDone: frase=${request.id.value.take(8)} escrita por completo")
        listener?.onDone(request.id)
    }

    /**
     * Escribe un trozo en el AudioTrack al ritmo de la reproducción (búfer corto, escrituras no
     * bloqueantes). Avisa `onDone` de la frase completa cuando lo que falta por sonar baja del adelanto.
     */
    private fun playPiece(
        request: Request,
        piece: TtsMath.Piece,
        pieceIndex: Int,
        samples: FloatArray,
        sampleRate: Int,
        isLast: Boolean,
        attempt: Int = 0,
    ) {
        if (request.cancelled || closed.get()) return
        val track = ensureTrack(sampleRate, request.playbackRate) ?: run {
            Log.w(TAG, "No se pudo crear el AudioTrack")
            listener?.onError(request.id, SherpaTtsError.Audio("no se pudo crear la salida de audio"))
            return
        }
        playing = request
        try {
            // Arranca antes de escribir: con el búfer vacío AudioFlinger no exige llenarlo para empezar.
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
            val startFrame = framesWritten
            if (request.firstStartFrame < 0) request.firstStartFrame = startFrame
            val endFrame = startFrame + samples.size
            val lead = TtsMath.leadSeconds(rtf.value)
            val rate = request.playbackRate.toDouble()
            Log.d(TAG, "play(): frase=${request.id.value.take(8)} trozo ${pieceIndex + 1}/${request.pieces.size} ${"%.2f".format(samples.size.toDouble() / sampleRate)} s, cola previa=${"%.2f".format((startFrame - headPosition(track)).toDouble() / sampleRate)} s, lead=${"%.1f".format(lead)} s" + if (attempt > 0) " (reintento $attempt)" else "")

            val chunk = sampleRate / 20
            var offset = 0
            var lastHead = headPosition(track)
            var lastAdvanceAt = SystemClock.elapsedRealtime()
            var restarted = false
            while (!request.cancelled) {
                if (offset < samples.size) {
                    val written = track.write(samples, offset, minOf(chunk, samples.size - offset), AudioTrack.WRITE_NON_BLOCKING)
                    if (written < 0) {
                        Log.w(TAG, "AudioTrack.write devolvió $written (estado=${track.playState})")
                        listener?.onError(request.id, SherpaTtsError.Audio("write devolvió $written"))
                        return
                    }
                    offset += written
                    framesWritten += written
                    request.samplesPending.addAndGet(-written.toLong())
                }
                reportProgress(request, piece, track, startFrame, endFrame)
                val head = headPosition(track)
                if (!request.doneReported) {
                    val queued = (framesWritten - head).coerceAtLeast(0L).toDouble() / sampleRate
                    val pending = request.samplesPending.get().coerceAtLeast(0L).toDouble() / sampleRate
                    val unsynthesized = request.charsPending.coerceAtLeast(0) * secondsPerChar
                    val remaining = (queued + pending + unsynthesized) / rate
                    if (remaining <= lead) {
                        request.doneReported = true
                        markStarted(request)
                        Log.d(TAG, "onDone anticipado: frase=${request.id.value.take(8)} quedan ${"%.1f".format(remaining)} s por sonar (trozo ${pieceIndex + 1}/${request.pieces.size})")
                        listener?.onDone(request.id)
                    }
                }
                if (offset >= samples.size) break

                // Vigilante: el cabezal no avanza aunque hay audio en cola.
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
                        val played = (head - startFrame).coerceIn(0L, offset.toLong()).toInt()
                        runCatching { track.pause() }
                        runCatching { track.flush() }
                        runCatching { track.release() }
                        this.track = null
                        framesWritten = 0
                        request.samplesPending.addAndGet((samples.size - played).toLong())
                        playPiece(request, piece, pieceIndex, samples.copyOfRange(played, samples.size), sampleRate, isLast, attempt + 1)
                        return
                    } else {
                        Log.w(TAG, "La salida de audio no responde; abandono la frase ${request.id.value.take(8)}")
                        listener?.onError(request.id, SherpaTtsError.Audio("la salida de audio se detuvo"))
                        return
                    }
                }
                SystemClock.sleep(10)
            }
            if (request.cancelled) return
            if (isLast) finishIfNeeded(request)
        } finally {
            if (playing === request) playing = null
        }
    }

    private fun reportProgress(request: Request, piece: TtsMath.Piece, track: AudioTrack, startFrame: Long, endFrame: Long) {
        val head = headPosition(track)
        if (request.firstStartFrame < 0 || head < request.firstStartFrame) return
        markStarted(request)
        if (head < startFrame) return
        val fraction = (head - startFrame).toDouble() / (endFrame - startFrame).coerceAtLeast(1L)
        val index = piece.offset + (fraction.coerceIn(0.0, 1.0) * piece.text.length).toInt()
        val range = TtsMath.wordRangeAtIndex(request.text, index) ?: return
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

    private fun ensureTrack(sampleRate: Int, playbackRate: Float): AudioTrack? {
        var result = track
        if (result != null && result.sampleRate != sampleRate) {
            runCatching { result.release() }
            track = null
            result = null
        }
        if (result == null) {
            val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            // Búfer corto: AudioFlinger no mezcla un track nuevo hasta que su búfer se llena, así que
            // uno grande dejaría mudas las frases que no alcanzan a llenarlo.
            val bufferBytes = maxOf(minBuffer, (sampleRate * BUFFER_SECONDS).toInt() * Float.SIZE_BYTES)
            result = try {
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching { it.setStartThresholdInFrames(maxOf(1, minBuffer / Float.SIZE_BYTES / 2)) }
                        }
                        track = it
                        framesWritten = 0
                        Log.d(TAG, "AudioTrack nuevo: búfer ${bufferBytes / Float.SIZE_BYTES} frames, mínimo ${minBuffer / Float.SIZE_BYTES}")
                    }
            } catch (e: Exception) {
                null
            } ?: return null
        }
        val currentRate = runCatching { result.playbackParams.speed }.getOrDefault(1f)
        if (abs(currentRate - playbackRate) > 0.01f) {
            runCatching { result.playbackParams = PlaybackParams().setSpeed(playbackRate).setPitch(1f) }
                .onFailure { Log.w(TAG, "No se pudo fijar la velocidad de reproducción $playbackRate", it) }
        }
        return result
    }

    private companion object {
        const val TAG = "VoxLibris.TTS"
        const val STALL_MS = 1500L
        const val MAX_PLAY_ATTEMPTS = 2
        /** El adelanto para pedir la frase siguiente no depende del búfer: se mide sobre lo que falta por sonar. */
        const val BUFFER_SECONDS = 0.25
        const val HOLD_FIRST_PIECE_UNDER_SECONDS = 1.5
        /** Silencio que se conserva en la unión entre trozos (una coma natural dura unos 150 ms). */
        const val JOIN_KEEP_MS = 60
        const val LEADING_KEEP_MS = 10
        const val EDGE_FADE_MS = 5
    }
}
