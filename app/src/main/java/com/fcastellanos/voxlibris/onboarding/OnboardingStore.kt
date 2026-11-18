package com.fcastellanos.voxlibris.onboarding

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Recuerda si ya se mostró la pantalla de primer inicio. */
class OnboardingStore(context: Context) {

    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    private val _completed = MutableStateFlow(prefs.getBoolean(KEY_COMPLETED, false))
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    fun markCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
        _completed.value = true
    }

    private companion object {
        const val KEY_COMPLETED = "completed"
    }
}
