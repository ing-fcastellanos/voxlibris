package com.fcastellanos.voxlibris.data

import com.fcastellanos.voxlibris.data.db.BookDao
import com.fcastellanos.voxlibris.data.db.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        dao.delete(book.id)
        book.file.delete()
        book.coverFile?.delete()
    }
}
