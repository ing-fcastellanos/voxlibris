package com.fcastellanos.readapp.data

import com.fcastellanos.readapp.data.db.BookDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BookRepository(
    private val dao: BookDao,
    private val storage: LibraryStorage,
) {

    val books: Flow<List<Book>> = dao.observeAll().map { rows -> rows.map { it.toBook(storage) } }

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        dao.delete(book.id)
        book.file.delete()
        book.coverFile?.delete()
    }
}
