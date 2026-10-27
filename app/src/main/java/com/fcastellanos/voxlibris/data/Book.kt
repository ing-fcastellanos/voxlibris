package com.fcastellanos.voxlibris.data

import com.fcastellanos.voxlibris.data.db.BookEntity
import java.io.File
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

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
    /** Porcentaje leído (0 a 100) según el último Locator guardado, o null si nunca se abrió. */
    val progressPercent: Int?,
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
    progressPercent = progression?.let { json ->
        runCatching { Locator.fromJSON(JSONObject(json))?.locations?.totalProgression }
            .getOrNull()
            ?.let { (it * 100).toInt().coerceIn(0, 100) }
    },
)
