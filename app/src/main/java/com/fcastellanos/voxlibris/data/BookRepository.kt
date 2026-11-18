package com.fcastellanos.voxlibris.data

import com.fcastellanos.voxlibris.data.db.BookDao
import com.fcastellanos.voxlibris.data.db.BookEntity
import com.fcastellanos.voxlibris.tts.ReadingMode
import com.fcastellanos.voxlibris.tts.VoiceChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BookRepository(
    private val dao: BookDao,
    private val storage: LibraryStorage,
) {

    val books: Flow<List<Book>> = dao.observeAll().map { rows -> rows.map { it.toBook(storage) } }

    suspend fun get(bookId: Long): BookEntity? = dao.get(bookId)

    /** Guarda el Locator (JSON de Readium) de la posición actual y marca el libro como abierto. */
    suspend fun saveProgression(bookId: Long, locatorJson: String) {
        dao.updateProgression(bookId, locatorJson, System.currentTimeMillis())
    }

    /** Modo y voces propios del libro, o `null` mientras sigue los valores por defecto. */
    fun voiceChoice(bookId: Long): Flow<VoiceChoice?> = dao.observeVoice(bookId)
        .map { row ->
            val mode = row?.voiceMode?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
            val narrator = row?.narratorVoiceId
            val dialogue = row?.dialogueVoiceId
            if (mode != null && narrator != null && dialogue != null) VoiceChoice(mode, narrator, dialogue) else null
        }
        .distinctUntilChanged()

    /** Guarda el modo y las voces del libro; `null` lo devuelve a los valores por defecto. */
    suspend fun setVoiceChoice(bookId: Long, choice: VoiceChoice?) {
        dao.updateVoice(bookId, choice?.mode?.name, choice?.narratorVoiceId, choice?.dialogueVoiceId)
    }

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        dao.delete(book.id)
        book.file.delete()
        book.coverFile?.delete()
    }
}
