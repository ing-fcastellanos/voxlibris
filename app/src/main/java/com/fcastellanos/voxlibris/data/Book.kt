package com.fcastellanos.voxlibris.data

import com.fcastellanos.voxlibris.data.db.BookEntity
import java.io.File

/** Libro tal como lo consume la interfaz. */
data class Book(
    val id: Long,
    val title: String,
    val author: String?,
    val language: String?,
    val file: File,
    val coverFile: File?,
    val addedAt: Long,
    val progression: String?,
)

internal fun BookEntity.toBook(storage: LibraryStorage): Book = Book(
    id = id,
    title = title,
    author = author,
    language = language,
    file = storage.bookFile(fileName),
    coverFile = storage.coverFile(coverFileName),
    addedAt = createdAt,
    progression = progression,
)
