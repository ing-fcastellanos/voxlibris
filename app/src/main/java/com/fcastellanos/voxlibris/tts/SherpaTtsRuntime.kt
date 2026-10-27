package com.fcastellanos.voxlibris.tts

import android.os.SystemClock
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Instancia compartida de sherpa-onnx con el modelo cargado. Cargar el modelo cuesta medio
 * segundo y ~270 MB de RAM, así que se mantiene viva mientras la app exista.
 */
class SherpaTtsRuntime(
    private val models: VoiceModelManager,
    private val store: TtsPreferencesStore,
) {

    private val loadLock = Mutex()
    private var loaded: Pair<File, OfflineTts>? = null

    /** Devuelve el motor cargado, o null si las voces no están instaladas. */
    suspend fun acquire(): OfflineTts? = loadLock.withLock {
        val dir = models.readyModelDir() ?: return null
        loaded?.takeIf { it.first == dir }?.second
            ?: withContext(Dispatchers.IO) { load(dir) }.also { loaded = dir to it }
    }

    fun release() {
        loaded?.second?.release()
        loaded = null
    }

    /** Factor de tiempo real con 8 pasos en este teléfono. Se mide una vez y se guarda. */
    suspend fun calibratedRtf(tts: OfflineTts): Double =
        store.calibratedRtf ?: withContext(Dispatchers.IO) { measure(tts) }.also { store.calibratedRtf = it }

    /** La síntesis se serializa: la instancia nativa no se comparte entre hilos a la vez. */
    fun synthesize(tts: OfflineTts, text: String, voiceId: Int, speed: Double, numSteps: Int, language: String): GeneratedAudio =
        synchronized(tts) {
            tts.generateWithConfig(
                text,
                GenerationConfig(
                    speed = speed.toFloat(),
                    sid = voiceId,
                    numSteps = numSteps,
                    extra = mapOf("lang" to language),
                ),
            )
        }

    private fun load(dir: File): OfflineTts {
        fun path(name: String) = File(dir, name).absolutePath
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                supertonic = OfflineTtsSupertonicModelConfig(
                    durationPredictor = path("duration_predictor.int8.onnx"),
                    textEncoder = path("text_encoder.int8.onnx"),
                    vectorEstimator = path("vector_estimator.int8.onnx"),
                    vocoder = path("vocoder.int8.onnx"),
                    ttsJson = path("tts.json"),
                    unicodeIndexer = path("unicode_indexer.bin"),
                    voiceStyle = path("voice.bin"),
                ),
                numThreads = NUM_THREADS,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return OfflineTts(config = config)
    }

    private fun measure(tts: OfflineTts): Double {
        synthesize(tts, "Calibrando la voz.", 0, 1.0, SherpaTtsDefaults.STEPS_HIGH, "es") // calienta el motor
        val start = SystemClock.elapsedRealtime()
        val audio = synthesize(tts, CALIBRATION_TEXT, 0, 1.0, SherpaTtsDefaults.STEPS_HIGH, "es")
        val seconds = (SystemClock.elapsedRealtime() - start) / 1000.0
        val audioSeconds = audio.samples.size.toDouble() / audio.sampleRate
        return if (audioSeconds > 0) seconds / audioSeconds else 1.0
    }

    private companion object {
        const val NUM_THREADS = 2
        const val CALIBRATION_TEXT =
            "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo."
    }
}
