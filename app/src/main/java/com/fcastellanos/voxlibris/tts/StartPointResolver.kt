package com.fcastellanos.voxlibris.tts

import android.util.Log
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
 * El navegador de voz de Readium se ubica por elemento (`cssSelector`), nunca por frase: arrancar
 * desde una frase del medio de un párrafo exige empezar en el párrafo y saltar las frases anteriores.
 * Dado un punto de partida y el texto donde se quiere arrancar (una selección, o la frase donde se
 * cortó la lectura), busca ese texto en el contenido y devuelve el segmento que lo contiene más
 * cuántas frases suyas saltar, contadas con el mismo tokenizador que usa el navegador.
 */
object StartPointResolver {

    data class StartPoint(val locator: Locator, val skipSentences: Int)

    suspend fun resolve(publication: Publication, from: Locator, text: String?): StartPoint = withContext(Dispatchers.Default) {
        val fallback = StartPoint(from, 0)
        val full = text?.collapseSpaces()?.takeIf { it.length >= 2 } ?: return@withContext fallback
        val short = full.take(NEEDLE_CHARS)
        val language = publication.metadata.languages.firstOrNull()?.let { Language(it) }
        val tokenizer = DefaultTextContentTokenizer(TextUnit.Sentence, language)
        val iterator = runCatching { publication.content(from)?.iterator() }.getOrNull() ?: return@withContext fallback
        var visited = 0
        while (visited < MAX_ELEMENTS_TO_SCAN && iterator.hasNext()) {
            val element = iterator.next()
            visited++
            val segments = (element as? Content.TextElement)?.segments ?: continue
            for (segment in segments) {
                val segmentText = segment.text.collapseSpaces()
                // Primero la frase entera (exacta); si no está, su comienzo, que alcanza para una selección.
                val index = segmentText.indexOf(full).takeIf { it >= 0 } ?: segmentText.indexOf(short)
                if (index < 0) {
                    if (visited == 1) Log.d(TAG, "Frase no hallada en el primer elemento: «${short}» vs «${segmentText.take(60)}»")
                    continue
                }
                val skip = tokenizer.tokenize(segmentText).count { range ->
                    range.last < index && segmentText.substring(range).isNotBlank()
                }
                Log.d(TAG, "Frase hallada en el elemento $visited, índice $index, saltar $skip: «${short}»")
                return@withContext StartPoint(segment.locator, skip)
            }
        }
        fallback
    }

    private val WHITESPACE = Regex("\\s+")

    private fun String.collapseSpaces(): String = replace(WHITESPACE, " ").trim()

    private const val NEEDLE_CHARS = 24
    private const val TAG = "VoxLibris.TTS"
    private const val MAX_ELEMENTS_TO_SCAN = 400
}
