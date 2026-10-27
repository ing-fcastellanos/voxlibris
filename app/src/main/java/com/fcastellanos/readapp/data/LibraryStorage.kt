package com.fcastellanos.readapp.data

import android.content.Context
import java.io.File

/** Carpetas privadas de la app donde viven los EPUB importados y sus portadas. */
class LibraryStorage(filesDir: File, cacheDir: File) {

    constructor(context: Context) : this(context.filesDir, context.cacheDir)

    val booksDir: File = File(filesDir, "books").also { it.mkdirs() }
    val coversDir: File = File(filesDir, "covers").also { it.mkdirs() }
    val importTempDir: File = File(cacheDir, "import").also { it.mkdirs() }

    fun bookFile(fileName: String): File = File(booksDir, fileName)

    fun coverFile(fileName: String?): File? = fileName?.let { File(coversDir, it) }
}
