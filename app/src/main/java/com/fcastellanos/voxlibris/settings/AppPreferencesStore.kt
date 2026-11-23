package com.fcastellanos.voxlibris.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tema de la interfaz: el del teléfono, siempre claro o siempre oscuro. El lector sigue su fondo de lectura. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Preferencias generales de la app (pantalla Ajustes), fuera de las de lectura y las de voz:
 * el tema, si la frase que suena se resalta en el texto y cuándo se revisó el paquete de voces.
 */
class AppPreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _highlightSpokenSentence = MutableStateFlow(prefs.getBoolean(KEY_HIGHLIGHT_SPOKEN, true))
    val highlightSpokenSentence: StateFlow<Boolean> = _highlightSpokenSentence.asStateFlow()

    private val _voicesCheckedAt = MutableStateFlow(prefs.getLong(KEY_VOICES_CHECKED_AT, 0L).takeIf { it > 0L })
    /** Última vez (epoch ms) que se comparó el paquete de voces instalado con el que trae la app; null si nunca. */
    val voicesCheckedAt: StateFlow<Long?> = _voicesCheckedAt.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setHighlightSpokenSentence(enabled: Boolean) {
        _highlightSpokenSentence.value = enabled
        prefs.edit().putBoolean(KEY_HIGHLIGHT_SPOKEN, enabled).apply()
    }

    fun markVoicesChecked(at: Long = System.currentTimeMillis()) {
        _voicesCheckedAt.value = at
        prefs.edit().putLong(KEY_VOICES_CHECKED_AT, at).apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_HIGHLIGHT_SPOKEN = "highlight_spoken_sentence"
        const val KEY_VOICES_CHECKED_AT = "voices_checked_at"
    }
}
