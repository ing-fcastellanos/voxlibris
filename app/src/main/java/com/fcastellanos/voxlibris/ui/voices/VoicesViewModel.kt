package com.fcastellanos.voxlibris.ui.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fcastellanos.voxlibris.di.AppContainer
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Catálogo de lectores: estado del paquete de voces, voz elegida, velocidad, calidad y muestras. */
class VoicesViewModel(
    private val models: VoiceModelManager,
    private val store: TtsPreferencesStore,
    private val runtime: SherpaTtsRuntime,
    private val sampleTemplate: String,
) : ViewModel() {

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

    fun selectVoice(id: Int) = store.update(store.preferences.value.copy(voiceId = id))

    fun setSpeed(speed: Double) {
        val rounded = ((speed * 10).roundToInt() / 10.0).coerceIn(SherpaTtsDefaults.SPEED_RANGE)
        store.update(store.preferences.value.copy(speed = rounded))
    }

    fun setQuality(quality: SherpaTtsQuality) = store.update(store.preferences.value.copy(quality = quality))

    fun preview(voice: SherpaVoice) {
        if (_previewing.value != null) return
        _previewing.value = voice.id
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tts = runtime.acquire() ?: return@launch
                val current = settings.value
                val rtf = runtime.calibratedRtf(tts)
                val synthSpeed = minOf(current.speed, SherpaTtsDefaults.MAX_SYNTH_SPEED)
                val audio = runtime.synthesize(
                    tts,
                    sampleTemplate.format(voice.name),
                    voice.id,
                    synthSpeed,
                    TtsMath.numSteps(current.quality, rtf),
                    "es",
                )
                PcmPlayer.playBlocking(audio.samples, audio.sampleRate, (current.speed / synthSpeed).toFloat())
            } catch (e: Exception) {
                // La muestra es opcional: si falla, simplemente no suena.
            } finally {
                _previewing.value = null
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, sampleTemplate: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { VoicesViewModel(container.voiceModels, container.ttsPreferences, container.ttsRuntime, sampleTemplate) }
        }
    }
}
