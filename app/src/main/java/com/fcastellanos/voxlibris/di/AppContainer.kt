package com.fcastellanos.voxlibris.di

import android.content.Context
import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.data.EpubImporter
import com.fcastellanos.voxlibris.data.LibraryStorage
import com.fcastellanos.voxlibris.data.db.AppDatabase
import com.fcastellanos.voxlibris.reading.Readium

/**
 * Composición manual de dependencias. Alcanza para el tamaño actual de la app;
 * si crece, se reemplaza por Hilt o Koin sin tocar el resto del código.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val readium: Readium by lazy { Readium(appContext) }
    val storage: LibraryStorage by lazy { LibraryStorage(appContext) }
    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val bookRepository: BookRepository by lazy { BookRepository(database.bookDao(), storage) }
    val importer: EpubImporter by lazy { EpubImporter(appContext, readium, storage, database.bookDao()) }
}
