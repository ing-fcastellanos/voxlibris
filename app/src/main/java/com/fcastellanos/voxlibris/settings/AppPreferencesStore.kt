package com.fcastellanos.voxlibris.settings

import android.content.Context
import com.fcastellanos.voxlibris.data.LibrarySort
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

    private val _librarySort = MutableStateFlow(
        prefs.getString(KEY_LIBRARY_SORT, null)?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() } ?: LibrarySort.RECENT,
    )
    val librarySort: StateFlow<LibrarySort> = _librarySort.asStateFlow()

    private val _hideFinished = MutableStateFlow(prefs.getBoolean(KEY_HIDE_FINISHED, false))
    val hideFinished: StateFlow<Boolean> = _hideFinished.asStateFlow()

    private val _recentSearches = MutableStateFlow(prefs.getString(KEY_RECENT_SEARCHES, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
    /** Últimas búsquedas de la biblioteca, la más nueva primero. */
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    fun setLibrarySort(sort: LibrarySort) {
        _librarySort.value = sort
        prefs.edit().putString(KEY_LIBRARY_SORT, sort.name).apply()
    }

    fun setHideFinished(hide: Boolean) {
        _hideFinished.value = hide
        prefs.edit().putBoolean(KEY_HIDE_FINISHED, hide).apply()
    }

    fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        val next = (listOf(clean) + _recentSearches.value.filterNot { it.equals(clean, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
        _recentSearches.value = next
        prefs.edit().putString(KEY_RECENT_SEARCHES, next.joinToString("\n")).apply()
    }

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
        const val KEY_LIBRARY_SORT = "library_sort"
        const val KEY_HIDE_FINISHED = "library_hide_finished"
        const val KEY_RECENT_SEARCHES = "library_recent_searches"
        const val MAX_RECENT_SEARCHES = 6
    }
}
