package com.fcastellanos.voxlibris.reading

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.shared.ExperimentalReadiumApi

/** Preferencias de lectura globales (tema, tamaño de letra, etc.), serializadas con el propio serializador de Readium. */
@OptIn(ExperimentalReadiumApi::class)
class ReadingPreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences("reading_preferences", Context.MODE_PRIVATE)
    private val serializer = EpubPreferencesSerializer()

    private val _preferences = MutableStateFlow(load())
    val preferences: StateFlow<EpubPreferences> = _preferences.asStateFlow()

    fun update(preferences: EpubPreferences) {
        _preferences.value = preferences
        prefs.edit().putString(KEY_EPUB, serializer.serialize(preferences)).apply()
    }

    private fun load(): EpubPreferences =
        prefs.getString(KEY_EPUB, null)
            ?.let { runCatching { serializer.deserialize(it) }.getOrNull() }
            ?: EpubPreferences()

    private companion object {
        const val KEY_EPUB = "epub"
    }
}
