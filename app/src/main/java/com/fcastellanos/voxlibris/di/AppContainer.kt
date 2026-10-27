package com.fcastellanos.voxlibris.di

import android.app.Application
import android.content.Context
import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.data.EpubImporter
import com.fcastellanos.voxlibris.data.LibraryStorage
import com.fcastellanos.voxlibris.data.db.AppDatabase
import com.fcastellanos.voxlibris.reading.ReaderRepository
import com.fcastellanos.voxlibris.reading.ReadingPreferencesStore
import com.fcastellanos.voxlibris.reading.Readium
import com.fcastellanos.voxlibris.tts.SherpaTtsEngineProvider
import com.fcastellanos.voxlibris.tts.SherpaTtsRuntime
import com.fcastellanos.voxlibris.tts.TtsPreferencesStore
import com.fcastellanos.voxlibris.tts.VoiceModelManager
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
    val importer: EpubImporter by lazy { EpubImporter(appContext, readium, storage, database.bookDao()) }
    val readingPreferences: ReadingPreferencesStore by lazy { ReadingPreferencesStore(appContext) }
    val readerRepository: ReaderRepository by lazy { ReaderRepository(readium, bookRepository, storage) }

    // Voz on-device
    val voiceModels: VoiceModelManager by lazy { VoiceModelManager(appContext, applicationScope) }
    val ttsPreferences: TtsPreferencesStore by lazy { TtsPreferencesStore(appContext) }
    val ttsRuntime: SherpaTtsRuntime by lazy { SherpaTtsRuntime(voiceModels, ttsPreferences) }
    val ttsEngineProvider: SherpaTtsEngineProvider by lazy { SherpaTtsEngineProvider(ttsRuntime) }
}
