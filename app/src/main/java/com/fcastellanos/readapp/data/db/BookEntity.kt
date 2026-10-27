package com.fcastellanos.readapp.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Nombre del archivo dentro de la carpeta de libros de la app. */
    @ColumnInfo(name = "file_name") val fileName: String,
    val title: String,
    val author: String?,
    /** Etiqueta BCP 47 del idioma principal, por ejemplo "es". Decide el idioma del TTS. */
    val language: String?,
    /** Identificador declarado por el EPUB (dc:identifier). Evita importar el mismo libro dos veces. */
    val identifier: String?,
    @ColumnInfo(name = "media_type") val mediaType: String,
    @ColumnInfo(name = "cover_file_name") val coverFileName: String?,
    /** Locator de Readium serializado en JSON. Lo escribe el lector. */
    val progression: String? = null,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long? = null,
)
