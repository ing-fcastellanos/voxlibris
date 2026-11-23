package com.fcastellanos.voxlibris.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.tokenizer.DefaultTextContentTokenizer
import org.readium.r2.shared.util.tokenizer.TextUnit

/**
 * Las frases de un capítulo (un recurso del EPUB), tal como las corta el tokenizador que usa el
 * navegador de voz, agrupadas por elemento. Sirve para decir «Frase 38 de 172» en el reproductor:
 * el navegador no numera las frases, solo ubica cada una por su elemento y su texto.
 */
class ChapterSentences(val href: String, val elements: List<ElementSentences>) {

    /** Frases de un elemento de texto; [progression] es la del elemento dentro del recurso. */
    data class ElementSentences(val progression: Double?, val sentences: List<String>)

    val total: Int = elements.sumOf { it.sentences.size }

    /**
     * Número de frase (desde 1) de la que suena, dada la progresión de su elemento y su texto.
     * Si el texto no se encuentra en el elemento, cuenta como la primera de ese elemento.
     */
    fun indexOf(progression: Double?, highlight: String?): Int? {
        if (elements.isEmpty()) return null
        val elementIndex = progression
            ?.let { target -> elements.indexOfLast { (it.progression ?: 0.0) <= target + TOLERANCE } }
            ?.takeIf { it >= 0 }
            ?: 0
        val before = elements.take(elementIndex).sumOf { it.sentences.size }
        val element = elements[elementIndex]
        val needle = highlight?.collapseSpaces().orEmpty()
        val within = if (needle.isEmpty()) {
            0
        } else {
            val short = needle.take(NEEDLE_CHARS)
            element.sentences.indexOfFirst { it == needle || it.startsWith(short) || needle.startsWith(it.take(NEEDLE_CHARS)) }.coerceAtLeast(0)
        }
        return (before + within + 1).coerceAtMost(total.coerceAtLeast(1))
    }

    companion object {
        private const val TOLERANCE = 1e-6
        private const val NEEDLE_CHARS = 24
        private const val MAX_ELEMENTS = 6000
        private val WHITESPACE = Regex("\\s+")

        internal fun String.collapseSpaces(): String = replace(WHITESPACE, " ").trim()

        /** Recorre el recurso de [at] desde el principio y corta cada segmento en frases. */
        suspend fun build(publication: Publication, at: Locator): ChapterSentences? = withContext(Dispatchers.Default) {
            val href = at.href.toString()
            val start = at.copy(locations = Locator.Locations(progression = 0.0), text = Locator.Text())
            val iterator = runCatching { publication.content(start)?.iterator() }.getOrNull() ?: return@withContext null
            val language = publication.metadata.languages.firstOrNull()?.let { Language(it) }
            val tokenizer = DefaultTextContentTokenizer(TextUnit.Sentence, language)
            val elements = ArrayList<ElementSentences>()
            var seen = 0
            while (seen++ < MAX_ELEMENTS && iterator.hasNext()) {
                val element = iterator.next()
                if (element.locator.href.toString() != href) break
                val text = element as? Content.TextElement ?: continue
                val sentences = text.segments.flatMap { segment ->
                    val segmentText = segment.text
                    tokenizer.tokenize(segmentText).map { segmentText.substring(it).collapseSpaces() }.filter { it.isNotEmpty() }
                }
                if (sentences.isNotEmpty()) elements += ElementSentences(text.locator.locations.progression, sentences)
            }
            ChapterSentences(href, elements)
        }
    }
}
