package com.fcastellanos.voxlibris.tts

import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content

/**
 * Caracteres de texto de cada recurso del libro, elemento por elemento, en orden de lectura.
 * Con la posición de la voz (recurso y progresión, que Readium calcula como índice de elemento
 * sobre el total) se sabe cuánto texto queda por leer.
 */
class BookTextIndex internal constructor(private val resources: List<ResourceText>) {

    /** `href` tal como lo imprime el Locator; `elementChars` cuenta cero para los elementos sin texto. */
    data class ResourceText(val href: String, val elementChars: IntArray) {
        val total: Long = elementChars.sumOf { it.toLong() }
    }

    val totalChars: Long = resources.sumOf { it.total }
    val resourceCount: Int = resources.size

    /** Caracteres que quedan desde `locator` (incluido su elemento) hasta el final, o nulo si no se ubica. */
    fun charsRemaining(locator: Locator): Long? = charsRemaining(locator.href.toString(), locator.locations.progression)

    internal fun charsRemaining(href: String, progression: Double?): Long? {
        val index = resources.indexOfFirst { it.href == href }.takeIf { it >= 0 } ?: return null
        val resource = resources[index]
        val count = resource.elementChars.size
        val first = ((progression ?: 0.0) * count).roundToInt().coerceIn(0, count)
        var remaining = 0L
        for (i in first until count) remaining += resource.elementChars[i]
        for (i in index + 1 until resources.size) remaining += resources[i].total
        return remaining
    }

    companion object {
        /** Recorre todo el contenido del libro; tarda unos segundos en libros largos, así que va en segundo plano. */
        suspend fun build(publication: Publication): BookTextIndex? = withContext(Dispatchers.Default) {
            val iterator = publication.content()?.iterator() ?: return@withContext null
            val byHref = LinkedHashMap<String, MutableList<Int>>()
            while (iterator.hasNext()) {
                val element = iterator.next()
                val chars = (element as? Content.TextElement)?.segments?.sumOf { it.text.trim().length } ?: 0
                byHref.getOrPut(element.locator.href.toString()) { mutableListOf() }.add(chars)
            }
            BookTextIndex(byHref.map { (href, chars) -> ResourceText(href, chars.toIntArray()) })
        }
    }
}
