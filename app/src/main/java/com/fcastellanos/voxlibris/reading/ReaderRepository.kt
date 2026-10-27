package com.fcastellanos.voxlibris.reading

import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.data.LibraryStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.getOrElse

/** Publicación abierta lista para mostrarse, más lo necesario para crear el navegador. */
class ReaderSession(
    val bookId: Long,
    val title: String,
    val publication: Publication,
    val navigatorFactory: EpubNavigatorFactory,
    val initialLocator: Locator?,
)

sealed interface OpenBookError {
    data object BookNotFound : OpenBookError
    data object FileMissing : OpenBookError
    data object NotSupported : OpenBookError
    data class Readium(val message: String) : OpenBookError
}

sealed interface OpenBookResult {
    data class Success(val session: ReaderSession) : OpenBookResult
    data class Failure(val error: OpenBookError) : OpenBookResult
}

/**
 * Mantiene abiertas las publicaciones que el lector está mostrando. Vive en el Application
 * para sobrevivir a rotaciones; cada sesión se cierra cuando el lector termina.
 */
class ReaderRepository(
    private val readium: Readium,
    private val books: BookRepository,
    private val storage: LibraryStorage,
) {

    private val sessions = mutableMapOf<Long, ReaderSession>()
    private val refs = mutableMapOf<Long, Int>()

    operator fun get(bookId: Long): ReaderSession? = synchronized(sessions) { sessions[bookId] }

    /**
     * Abre la publicación (o reutiliza la sesión existente) y suma una referencia.
     * Cada `open`/`retain` debe emparejarse con un `release`; la publicación se cierra al llegar a cero.
     */
    suspend fun open(bookId: Long): OpenBookResult = withContext(Dispatchers.IO) {
        get(bookId)?.let {
            retain(bookId)
            return@withContext OpenBookResult.Success(it)
        }

        val entity = books.get(bookId)
            ?: return@withContext OpenBookResult.Failure(OpenBookError.BookNotFound)
        val file = storage.bookFile(entity.fileName)
        if (!file.exists()) return@withContext OpenBookResult.Failure(OpenBookError.FileMissing)

        val asset = readium.assetRetriever.retrieve(file)
            .getOrElse { return@withContext OpenBookResult.Failure(OpenBookError.Readium(it.message)) }
        val publication = readium.publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse { return@withContext OpenBookResult.Failure(OpenBookError.Readium(it.message)) }

        if (!publication.conformsTo(Publication.Profile.EPUB)) {
            publication.close()
            return@withContext OpenBookResult.Failure(OpenBookError.NotSupported)
        }

        val initialLocator = entity.progression?.let { json ->
            runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
        }
        val session = ReaderSession(
            bookId = bookId,
            title = entity.title,
            publication = publication,
            navigatorFactory = EpubNavigatorFactory(publication),
            initialLocator = initialLocator,
        )
        synchronized(sessions) {
            sessions[bookId] = session
            refs[bookId] = (refs[bookId] ?: 0) + 1
        }
        OpenBookResult.Success(session)
    }

    /** Suma una referencia a una sesión ya abierta (por ejemplo, la lectura en voz alta en segundo plano). */
    fun retain(bookId: Long) {
        synchronized(sessions) {
            if (sessions.containsKey(bookId)) refs[bookId] = (refs[bookId] ?: 0) + 1
        }
    }

    /** Resta una referencia; cierra la publicación cuando nadie más la usa. */
    fun release(bookId: Long) {
        val toClose = synchronized(sessions) {
            val remaining = (refs[bookId] ?: 1) - 1
            if (remaining > 0) {
                refs[bookId] = remaining
                null
            } else {
                refs.remove(bookId)
                sessions.remove(bookId)
            }
        }
        toClose?.publication?.close()
    }
}
