package com.fcastellanos.voxlibris.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("book_id")],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val href: String,
    /** Locator de Readium en JSON; con su texto, Readium vuelve a encontrar el pasaje en la página. */
    val locator: String,
    /** Texto resaltado, para listarlo sin abrir el libro. */
    val text: String,
    val color: Int,
    @ColumnInfo(name = "total_progression") val totalProgression: Double?,
)
