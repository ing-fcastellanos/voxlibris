package com.fcastellanos.voxlibris.data.db

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
    /**
     * Modo y voces propios del libro (nombre de `ReadingMode` e ids de voz). Los tres en null: el
     * libro sigue los valores por defecto.
     */
    @ColumnInfo(name = "voice_mode") val voiceMode: String? = null,
    @ColumnInfo(name = "narrator_voice_id") val narratorVoiceId: Int? = null,
    @ColumnInfo(name = "dialogue_voice_id") val dialogueVoiceId: Int? = null,
    /** Cuándo se marcó como terminado; null mientras no. */
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    /** Capítulo (recurso del orden de lectura, desde 1) donde está la posición guardada, y cuántos hay. */
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int? = null,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int? = null,
    @ColumnInfo(name = "chapter_title") val chapterTitle: String? = null,
)

/** Las columnas de voz de un libro, para observarlas sin el resto de la fila. */
data class BookVoiceRow(
    @ColumnInfo(name = "voice_mode") val voiceMode: String?,
    @ColumnInfo(name = "narrator_voice_id") val narratorVoiceId: Int?,
    @ColumnInfo(name = "dialogue_voice_id") val dialogueVoiceId: Int?,
)
