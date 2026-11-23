package com.fcastellanos.voxlibris.data

import com.fcastellanos.voxlibris.data.LibraryQuery.search
import com.fcastellanos.voxlibris.data.LibraryQuery.visible
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryQueryTest {

    private fun book(id: Long, title: String, author: String?, progress: Int?, finished: Boolean = false) = Book(
        id = id,
        title = title,
        author = author,
        language = "es",
        file = File("$id.epub"),
        coverFile = null,
        addedAt = id,
        progression = if (progress == null) null else "{}",
        progressPercent = progress,
        finished = finished,
        chapterIndex = null,
        chapterCount = null,
        chapterTitle = null,
    )

    // En el orden de la base: último abierto primero.
    private val books = listOf(
        book(1, "Don Quijote de la Mancha", "Miguel de Cervantes", 12),
        book(2, "Niebla", "Miguel de Unamuno", 34),
        book(3, "Cuentos de amor de locura y de muerte", "Horacio Quiroga", 61),
        book(4, "Facundo", "Domingo F. Sarmiento", 8),
        book(5, "Platero y yo", "Juan Ramón Jiménez", null),
        book(6, "Martín Fierro", "José Hernández", 100),
        book(7, "Ángeles", null, 50, finished = true),
    )

    @Test
    fun `shelves split new, unfinished and finished books`() {
        assertEquals(listOf(5L), books.visible(LibrarySort.RECENT, LibraryShelf.NEW, false).map { it.id })
        assertEquals(listOf(6L, 7L), books.visible(LibrarySort.RECENT, LibraryShelf.FINISHED, false).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), books.visible(LibrarySort.RECENT, LibraryShelf.UNFINISHED, false).map { it.id })
    }

    @Test
    fun `hide finished removes them from every shelf but their own`() {
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), books.visible(LibrarySort.RECENT, LibraryShelf.ALL, true).map { it.id })
        assertEquals(listOf(6L, 7L), books.visible(LibrarySort.RECENT, LibraryShelf.FINISHED, true).map { it.id })
    }

    @Test
    fun `sorts by title and author ignoring accents, authorless books last`() {
        assertEquals(listOf(7L, 3L, 1L, 4L, 6L, 2L, 5L), books.visible(LibrarySort.TITLE, LibraryShelf.ALL, false).map { it.id })
        assertEquals(listOf(4L, 3L, 6L, 5L, 1L, 2L, 7L), books.visible(LibrarySort.AUTHOR, LibraryShelf.ALL, false).map { it.id })
    }

    @Test
    fun `sorts by progress with new books last`() {
        assertEquals(listOf(6L, 3L, 7L, 2L, 1L, 4L, 5L), books.visible(LibrarySort.PROGRESS, LibraryShelf.ALL, false).map { it.id })
    }

    @Test
    fun `search matches title or author without case or accents`() {
        assertEquals(listOf(1L, 3L), books.search("qui").map { it.id })
        assertEquals(listOf(6L), books.search("MARTIN").map { it.id })
        assertEquals(listOf(7L), books.search("angeles").map { it.id })
        assertEquals(emptyList<Long>(), books.search("   "))
    }

    @Test
    fun `match range points at the original text`() {
        assertEquals(4..6, LibraryQuery.matchRange("Don Quijote", "qui"))
        assertEquals(0..5, LibraryQuery.matchRange("Ángeles caídos", "ANGELE"))
        assertNull(LibraryQuery.matchRange("Niebla", "qui"))
    }
}
