package com.fcastellanos.voxlibris.tts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferencias de voz persistidas (lector elegido, velocidad, calidad) y la calibración del teléfono. */
class TtsPreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences("tts_preferences", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(load())
    val preferences: StateFlow<SherpaTtsPreferences> = _preferences.asStateFlow()

    /** Factor de tiempo real medido con 8 pasos en este teléfono; null si todavía no se midió. */
    var calibratedRtf: Double?
        get() = prefs.getFloat(KEY_RTF, -1f).takeIf { it >= 0f }?.toDouble()
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_RTF) else putFloat(KEY_RTF, value.toFloat()) }.apply()
        }

    fun update(preferences: SherpaTtsPreferences) {
        _preferences.value = preferences
        prefs.edit().apply {
            preferences.voiceId?.let { putInt(KEY_VOICE, it) } ?: remove(KEY_VOICE)
            preferences.speed?.let { putFloat(KEY_SPEED, it.toFloat()) } ?: remove(KEY_SPEED)
            preferences.quality?.let { putString(KEY_QUALITY, it.name) } ?: remove(KEY_QUALITY)
        }.apply()
    }

    private fun load(): SherpaTtsPreferences = SherpaTtsPreferences(
        voiceId = if (prefs.contains(KEY_VOICE)) prefs.getInt(KEY_VOICE, SherpaTtsDefaults.VOICE_ID) else null,
        speed = if (prefs.contains(KEY_SPEED)) prefs.getFloat(KEY_SPEED, 1f).toDouble() else null,
        quality = prefs.getString(KEY_QUALITY, null)?.let { runCatching { SherpaTtsQuality.valueOf(it) }.getOrNull() },
    )

    private companion object {
        const val KEY_VOICE = "voice_id"
        const val KEY_SPEED = "speed"
        const val KEY_QUALITY = "quality"
        const val KEY_RTF = "calibrated_rtf"
    }
}
