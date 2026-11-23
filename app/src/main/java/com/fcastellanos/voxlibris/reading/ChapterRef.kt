package com.fcastellanos.voxlibris.reading

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Capítulo de una posición: el recurso del orden de lectura donde cae (desde 1), cuántos recursos
 * tiene el libro y el título que le da el índice, si lo tiene. Se guarda con la posición para que
 * la biblioteca pueda decir «Capítulo 12 de 33» sin abrir el libro.
 */
data class ChapterRef(val index: Int, val count: Int, val title: String?) {

    companion object {
        fun of(publication: Publication, locator: Locator): ChapterRef? {
            val href = locator.href.toString()
            val order = publication.readingOrder
            val index = order.indexOfFirst { it.url().toString() == href }.takeIf { it >= 0 } ?: return null
            return ChapterRef(index + 1, order.size, titleFor(publication.tableOfContents, href))
        }

        private fun titleFor(entries: List<Link>, href: String): String? {
            for (entry in entries) {
                if (entry.url().toString().substringBefore('#') == href) return entry.title
                titleFor(entry.children, href)?.let { return it }
            }
            return null
        }
    }
}
