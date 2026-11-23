package com.fcastellanos.voxlibris.di

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.data.EpubImporter
import com.fcastellanos.voxlibris.data.HighlightRepository
import com.fcastellanos.voxlibris.dictionary.WiktionaryDictionary
import com.fcastellanos.voxlibris.data.LibraryStorage
import com.fcastellanos.voxlibris.data.ListeningStatsStore
import com.fcastellanos.voxlibris.data.db.AppDatabase
import com.fcastellanos.voxlibris.reading.ReaderRepository
import com.fcastellanos.voxlibris.reading.ReadingPreferencesStore
import com.fcastellanos.voxlibris.reading.Readium
import com.fcastellanos.voxlibris.settings.AppPreferencesStore
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import com.fcastellanos.voxlibris.tts.SherpaTtsEngineProvider
import com.fcastellanos.voxlibris.tts.SherpaTtsRuntime
import com.fcastellanos.voxlibris.tts.TtsPreferencesStore
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.ui.theme.LightVoxColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Composición manual de dependencias. Alcanza para el tamaño actual de la app;
 * si crece, se reemplaza por Hilt o Koin sin tocar el resto del código.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext
    val application: Application get() = appContext as Application

    /** Trabajo que debe terminar aunque la pantalla que lo lanzó ya no exista (guardar progreso, etc.). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val readium: Readium by lazy { Readium(appContext) }
    val storage: LibraryStorage by lazy { LibraryStorage(appContext) }
    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val bookRepository: BookRepository by lazy { BookRepository(database.bookDao(), storage) }
    val highlightRepository: HighlightRepository by lazy { HighlightRepository(database.highlightDao()) }

    /**
     * Color que se guarda con cada resaltado nuevo: `reading-highlight` del tema claro. Al dibujarlo,
     * el lector usa el token del fondo de lectura actual (ver ReaderPaper.withPaperColors).
     */
    val highlightTint: Int by lazy { LightVoxColors.readingHighlight.toArgb() }

    /** Color del ícono de nota: `note-mark`. El lector también lo ajusta al fondo de lectura. */
    val noteMarkTint: Int by lazy { LightVoxColors.noteMark.toArgb() }

    /** Diccionario en línea; Wikimedia pide identificar la app en cada consulta. */
    val dictionary: WiktionaryDictionary by lazy {
        val version = runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull() ?: "0"
        WiktionaryDictionary("VoxLibris/$version (https://github.com/ing-fcastellanos/voxlibris)")
    }
    val importer: EpubImporter by lazy { EpubImporter(appContext, readium, storage, database.bookDao()) }
    val readingPreferences: ReadingPreferencesStore by lazy { ReadingPreferencesStore(appContext) }
    val appPreferences: AppPreferencesStore by lazy { AppPreferencesStore(appContext) }
    val listeningStats: ListeningStatsStore by lazy { ListeningStatsStore(appContext) }
    val readerRepository: ReaderRepository by lazy { ReaderRepository(readium, bookRepository, storage) }

    // Voz on-device
    val voiceModels: VoiceModelManager by lazy { VoiceModelManager(appContext, applicationScope) }
    val ttsPreferences: TtsPreferencesStore by lazy { TtsPreferencesStore(appContext) }
    val ttsRuntime: SherpaTtsRuntime by lazy { SherpaTtsRuntime(voiceModels, ttsPreferences) }
    val ttsEngineProvider: SherpaTtsEngineProvider by lazy { SherpaTtsEngineProvider(ttsRuntime) }
    val readAloud: ReadAloudManager by lazy {
        ReadAloudManager(application, ttsEngineProvider, voiceModels, ttsPreferences, readerRepository, bookRepository, listeningStats)
    }
}
