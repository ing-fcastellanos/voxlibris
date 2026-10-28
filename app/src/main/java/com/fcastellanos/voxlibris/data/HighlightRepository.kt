package com.fcastellanos.voxlibris.data

import com.fcastellanos.voxlibris.data.db.HighlightDao
import com.fcastellanos.voxlibris.data.db.HighlightEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** Pasaje resaltado por el usuario. */
data class Highlight(
    val id: Long,
    val bookId: Long,
    val locator: Locator,
    val text: String,
    val color: Int,
    val createdAt: Long,
)

class HighlightRepository(private val dao: HighlightDao) {

    fun observe(bookId: Long): Flow<List<Highlight>> = dao.observeForBook(bookId).map { rows ->
        rows.mapNotNull { row ->
            val locator = runCatching { Locator.fromJSON(JSONObject(row.locator)) }.getOrNull() ?: return@mapNotNull null
            Highlight(row.id, row.bookId, locator, row.text, row.color, row.createdAt)
        }
    }

    suspend fun add(bookId: Long, locator: Locator, color: Int): Long = dao.insert(
        HighlightEntity(
            bookId = bookId,
            createdAt = System.currentTimeMillis(),
            href = locator.href.toString(),
            locator = locator.toJSON().toString(),
            text = locator.text.highlight.orEmpty().trim(),
            color = color,
            totalProgression = locator.locations.totalProgression,
        ),
    )

    suspend fun delete(id: Long) = dao.delete(id)
}
