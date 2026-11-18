package com.fcastellanos.voxlibris.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fcastellanos.voxlibris.onboarding.BatteryOptimization
import com.fcastellanos.voxlibris.onboarding.OnboardingStore
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingViewModel(
    private val voiceModels: VoiceModelManager,
    private val store: OnboardingStore,
) : ViewModel() {

    private val _batteryUnrestricted = MutableStateFlow(false)
    val batteryUnrestricted: StateFlow<Boolean> = _batteryUnrestricted.asStateFlow()

    val voices: StateFlow<VoiceModelManager.State> = voiceModels.state
    val installProgress: StateFlow<Float?> = voiceModels.installProgress

    /** El estado se vuelve a leer al volver del diálogo o de los ajustes del sistema. */
    fun refreshBattery(context: Context) {
        _batteryUnrestricted.value = BatteryOptimization.isUnrestricted(context)
    }

    fun requestBattery(context: Context) {
        BatteryOptimization.request(context)
    }

    fun openBatterySettings(context: Context) {
        BatteryOptimization.openSettings(context)
    }

    fun downloadVoices() = voiceModels.install()

    fun cancelDownload() = voiceModels.cancel()

    fun finish() = store.markCompleted()

    companion object {
        fun factory(voiceModels: VoiceModelManager, store: OnboardingStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(voiceModels, store) }
        }
    }
}
