package com.fcastellanos.voxlibris.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Orden de la biblioteca (menú «Ordenar por»). RECENT es el orden de la base: último abierto primero. */
enum class LibrarySort { RECENT, TITLE, AUTHOR, PROGRESS }

/** Estantes de la biblioteca (chips bajo «Tu biblioteca»). */
enum class LibraryShelf { ALL, UNFINISHED, NEW, FINISHED }

/** Orden, estantes y búsqueda de la biblioteca, sobre la lista que ya viene ordenada por último abierto. */
object LibraryQuery {

    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("es")).apply { strength = Collator.PRIMARY }

    fun Book.isFinished(): Boolean = finished || (progressPercent ?: 0) >= 100

    fun Book.inShelf(shelf: LibraryShelf): Boolean = when (shelf) {
        LibraryShelf.ALL -> true
        LibraryShelf.NEW -> isNew
        LibraryShelf.FINISHED -> isFinished()
        LibraryShelf.UNFINISHED -> !isNew && !isFinished()
    }

    fun List<Book>.sortedWith(sort: LibrarySort): List<Book> = when (sort) {
        LibrarySort.RECENT -> this
        LibrarySort.TITLE -> sortedWith { a, b -> collator.compare(a.title, b.title) }
        LibrarySort.AUTHOR -> sortedWith(
            compareBy<Book> { it.author == null }
                .thenComparator { a, b -> collator.compare(a.author.orEmpty(), b.author.orEmpty()) }
                .thenComparator { a, b -> collator.compare(a.title, b.title) },
        )
        LibrarySort.PROGRESS -> sortedWith(compareByDescending<Book> { it.progressPercent ?: -1 }.thenComparator { a, b -> collator.compare(a.title, b.title) })
    }

    /** Lo que ve la biblioteca: el estante elegido, sin los terminados si así se pidió, en el orden pedido. */
    fun List<Book>.visible(sort: LibrarySort, shelf: LibraryShelf, hideFinished: Boolean): List<Book> =
        filter { it.inShelf(shelf) }
            .filterNot { hideFinished && shelf != LibraryShelf.FINISHED && it.isFinished() }
            .sortedWith(sort)

    /** Libros cuyo título o autor contienen [query], sin distinguir mayúsculas ni tildes. */
    fun List<Book>.search(query: String): List<Book> {
        val needle = fold(query.trim())
        if (needle.isEmpty()) return emptyList()
        return filter { matchRange(it.title, query) != null || (it.author != null && matchRange(it.author, query) != null) }
    }

    /**
     * Dónde aparece [query] dentro de [text], ignorando mayúsculas y tildes, para subrayarlo. El plegado
     * conserva la longitud (un carácter por carácter), así el rango vale sobre el texto original.
     */
    fun matchRange(text: String, query: String): IntRange? {
        val needle = fold(query.trim())
        if (needle.isEmpty()) return null
        val start = fold(text).indexOf(needle)
        return if (start < 0) null else start until start + needle.length
    }

    /** Minúsculas sin diacríticos, carácter a carácter: «Qüijote» → «quijote», misma longitud. */
    fun fold(text: String): String = buildString(text.length) {
        for (ch in text) {
            val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).firstOrNull() ?: ch
            append(base.lowercaseChar())
        }
    }
}
