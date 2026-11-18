package com.fcastellanos.voxlibris.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fcastellanos.voxlibris.data.BookRepository
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.fcastellanos.voxlibris.di.AppContainer
import com.fcastellanos.voxlibris.tts.DialogueParser
import com.fcastellanos.voxlibris.tts.DialogueStyle
import com.fcastellanos.voxlibris.tts.ReadingMode
import com.fcastellanos.voxlibris.tts.VoiceChoice
import com.fcastellanos.voxlibris.tts.voiceChoice
import com.fcastellanos.voxlibris.tts.PcmPlayer
import com.fcastellanos.voxlibris.tts.SherpaTtsDefaults
import com.fcastellanos.voxlibris.tts.SherpaTtsQuality
import com.fcastellanos.voxlibris.tts.SherpaTtsRuntime
import com.fcastellanos.voxlibris.tts.SherpaTtsSettings
import com.fcastellanos.voxlibris.tts.SherpaVoice
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.TtsMath
import com.fcastellanos.voxlibris.tts.TtsPreferencesStore
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.tts.resolve
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Catálogo de lectores: estado del paquete de voces, modo de lectura, voces, velocidad, calidad y
 * muestras. Con [bookId], el modo y las voces son los de ese libro; sin él, los valores por defecto.
 * Velocidad y calidad son siempre generales.
 */
class VoicesViewModel(
    private val models: VoiceModelManager,
    private val store: TtsPreferencesStore,
    private val runtime: SherpaTtsRuntime,
    private val books: BookRepository,
    private val sampleTemplate: String,
    private val dialogueSample: String,
    private val bookId: Long?,
) : ViewModel() {

    /** Verdadero si se están editando los ajustes de un libro y no los valores por defecto. */
    val forBook: Boolean = bookId != null

    private val ownChoice: StateFlow<VoiceChoice?> =
        (if (bookId != null) books.voiceChoice(bookId) else flowOf(null))
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Modo y voces vigentes: los del libro si los tiene; si no, los valores por defecto. */
    val voice: StateFlow<VoiceChoice> = combine(store.preferences, ownChoice) { defaults, own -> own ?: defaults.voiceChoice() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.preferences.value.voiceChoice())

    /** Verdadero cuando el libro tiene ajustes propios y ya no sigue los valores por defecto. */
    val customized: StateFlow<Boolean> = ownChoice.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val voices: List<SherpaVoice> = SupertonicModel.VOICES

    val modelState: StateFlow<VoiceModelManager.State> = models.state

    val settings: StateFlow<SherpaTtsSettings> = store.preferences
        .map { it.resolve(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.preferences.value.resolve(null))

    private val _previewing = MutableStateFlow<Int?>(null)
    val previewing: StateFlow<Int?> = _previewing.asStateFlow()

    fun install() = models.install()

    fun cancelDownload() = models.cancel()

    fun deleteModel() {
        runtime.release()
        models.delete()
    }

    fun setMode(mode: ReadingMode) = updateVoice { it.copy(mode = mode) }

    /** Elige la voz del narrador o, con [forDialogue], la de los diálogos. */
    fun selectVoice(id: Int, forDialogue: Boolean) = updateVoice {
        if (forDialogue) it.copy(dialogueVoiceId = id) else it.copy(narratorVoiceId = id)
    }

    /** El libro vuelve a seguir los valores por defecto. */
    fun useDefaults() {
        val id = bookId ?: return
        viewModelScope.launch { books.setVoiceChoice(id, null) }
    }

    /** Un cambio en un libro guarda las tres cosas: desde ahí el libro ya no sigue los valores por defecto. */
    private fun updateVoice(change: (VoiceChoice) -> VoiceChoice) {
        val next = change(voice.value)
        if (bookId != null) {
            viewModelScope.launch { books.setVoiceChoice(bookId, next) }
        } else {
            store.update(store.preferences.value.copy(mode = next.mode, voiceId = next.narratorVoiceId, dialogueVoiceId = next.dialogueVoiceId))
        }
    }

    fun setSpeed(speed: Double) {
        val rounded = ((speed * 10).roundToInt() / 10.0).coerceIn(SherpaTtsDefaults.SPEED_RANGE)
        store.update(store.preferences.value.copy(speed = rounded))
    }

    fun setQuality(quality: SherpaTtsQuality) = store.update(store.preferences.value.copy(quality = quality))

    fun preview(voice: SherpaVoice) = playSample(voice.id) { say ->
        val audio = say(sampleTemplate.format(voice.name), voice.id)
        audio.samples to audio.sampleRate
    }

    /** Muestra a dos voces: una frase con narración y diálogo, cada parte con su lector. */
    fun previewDialogue() {
        val choice = voice.value
        playSample(DIALOGUE_SAMPLE_ID) { say ->
            val pieces = DialogueParser.splitVoiced(dialogueSample, DialogueParser.parse(dialogueSample, DialogueStyle.DASHES))
            var sampleRate = 0
            val parts = pieces.mapIndexed { index, voiced ->
                val audio = say(voiced.piece.text, if (voiced.dialogue) choice.dialogueVoiceId else choice.narratorVoiceId)
                sampleRate = audio.sampleRate
                var samples = audio.samples
                if (index > 0) samples = TtsMath.trimLeadingSilence(samples, sampleRate)
                if (index < pieces.lastIndex) samples = TtsMath.trimTrailingSilence(samples, sampleRate, keepMs = SAMPLE_JOIN_KEEP_MS)
                samples
            }
            val joined = FloatArray(parts.sumOf { it.size })
            var at = 0
            parts.forEach { it.copyInto(joined, at); at += it.size }
            joined to sampleRate
        }
    }

    private fun playSample(id: Int, build: (say: (String, Int) -> GeneratedAudio) -> Pair<FloatArray, Int>) {
        if (_previewing.value != null) return
        _previewing.value = id
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tts = runtime.acquire() ?: return@launch
                val current = settings.value
                val rtf = runtime.calibratedRtf(tts)
                val synthSpeed = minOf(current.speed, SherpaTtsDefaults.MAX_SYNTH_SPEED)
                val steps = TtsMath.numSteps(current.quality, rtf)
                val (samples, sampleRate) = build { text, voiceId -> runtime.synthesize(tts, text, voiceId, synthSpeed, steps, "es") }
                if (samples.isNotEmpty()) PcmPlayer.playBlocking(samples, sampleRate, (current.speed / synthSpeed).toFloat())
            } catch (e: Exception) {
                // La muestra es opcional: si falla, simplemente no suena.
            } finally {
                _previewing.value = null
            }
        }
    }

    companion object {
        /** Valor de [previewing] mientras suena la muestra a dos voces. */
        const val DIALOGUE_SAMPLE_ID = -1
        private const val SAMPLE_JOIN_KEEP_MS = 140

        /** Con [bookId], edita los ajustes de ese libro; pedilo con una `key` propia para no compartir instancia. */
        fun factory(container: AppContainer, sampleTemplate: String, dialogueSample: String, bookId: Long? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VoicesViewModel(
                    container.voiceModels,
                    container.ttsPreferences,
                    container.ttsRuntime,
                    container.bookRepository,
                    sampleTemplate,
                    dialogueSample,
                    bookId,
                )
            }
        }
    }
}
