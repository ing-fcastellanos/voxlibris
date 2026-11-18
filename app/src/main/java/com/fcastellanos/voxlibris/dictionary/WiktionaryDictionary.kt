package com.fcastellanos.voxlibris.dictionary

import android.util.LruCache
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface DictionaryResult {
    /** La entrada pedida y, si era solo una forma flexionada, la del lema para mostrar sus acepciones. */
    data class Found(val entry: DictionaryEntry, val lemma: DictionaryEntry?) : DictionaryResult
    data object NotFound : DictionaryResult
    data object Offline : DictionaryResult
}

/**
 * Diccionario en línea sobre el Wikcionario en español (CC BY-SA). No requiere clave; pide el wikitexto
 * por la API de MediaWiki y lo reduce con [WiktionaryParser]. Recuerda las últimas consultas.
 */
class WiktionaryDictionary(private val userAgent: String) {

    private val cache = LruCache<String, DictionaryResult>(64)

    suspend fun lookUp(word: String): DictionaryResult = withContext(Dispatchers.IO) {
        cache.get(word)?.let { return@withContext it }
        val result = try {
            val lower = word.lowercase(SPANISH)
            val entry = fetchEntry(lower) ?: lower.takeIf { it != word }?.let { fetchEntry(word) }
            when {
                entry == null -> DictionaryResult.NotFound
                else -> {
                    val lemma = entry.lemmas.firstOrNull()
                        ?.takeIf { entry.isOnlyForms && it != entry.word }
                        ?.let { fetchEntry(it) }
                    DictionaryResult.Found(entry, lemma)
                }
            }
        } catch (e: IOException) {
            DictionaryResult.Offline
        }
        if (result !is DictionaryResult.Offline) cache.put(word, result)
        result
    }

    private fun fetchEntry(title: String): DictionaryEntry? =
        fetchWikitext(title)?.let { WiktionaryParser.parse(title, it) }

    /** Wikitexto de la página, o nulo si no existe. Lanza [IOException] sin red o ante una respuesta inesperada. */
    private fun fetchWikitext(title: String): String? {
        val url = URL(API + URLEncoder.encode(title, "UTF-8"))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            json.optJSONObject("error")?.let { error ->
                if (error.optString("code") == "missingtitle") return null
                throw IOException(error.optString("info"))
            }
            return json.getJSONObject("parse").getString("wikitext")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val SPANISH = Locale("es")
        private const val TIMEOUT_MS = 8_000
        private const val API =
            "https://es.wiktionary.org/w/api.php?action=parse&prop=wikitext&format=json&formatversion=2&redirects=1&page="

        fun pageUrl(word: String): String = "https://es.wiktionary.org/wiki/" + URLEncoder.encode(word, "UTF-8")
    }
}
