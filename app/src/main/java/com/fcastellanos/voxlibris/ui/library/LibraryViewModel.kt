package com.fcastellanos.voxlibris.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fcastellanos.voxlibris.data.Book
import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.data.EpubImporter
import com.fcastellanos.voxlibris.data.Highlight
import com.fcastellanos.voxlibris.data.HighlightRepository
import com.fcastellanos.voxlibris.data.LibraryQuery.isFinished
import com.fcastellanos.voxlibris.data.LibraryQuery.search
import com.fcastellanos.voxlibris.data.LibraryQuery.visible
import com.fcastellanos.voxlibris.data.LibraryShelf
import com.fcastellanos.voxlibris.data.LibrarySort
import com.fcastellanos.voxlibris.data.ListeningStatsStore
import com.fcastellanos.voxlibris.data.ListeningStreak
import com.fcastellanos.voxlibris.di.AppContainer
import com.fcastellanos.voxlibris.reading.OpenBookError
import com.fcastellanos.voxlibris.reading.OpenBookResult
import com.fcastellanos.voxlibris.reading.ReaderRepository
import com.fcastellanos.voxlibris.settings.AppPreferencesStore
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import com.fcastellanos.voxlibris.ui.reader.FlatEntry
import com.fcastellanos.voxlibris.ui.reader.flattenTree
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

sealed interface UiMessage {
    /** Libros agregados; con uno solo, [bookId] permite ofrecer escucharlo desde el aviso. */
    data class Imported(val count: Int, val bookId: Long?) : UiMessage
    data class ImportFailed(val error: EpubImporter.ImportError) : UiMessage
    data class Deleted(val title: String) : UiMessage
    data class OpenFailed(val error: OpenBookError) : UiMessage
    data class MarkedFinished(val title: String, val finished: Boolean) : UiMessage
    data object NothingToPlay : UiMessage
}

sealed interface UiEvent {
    data class OpenReader(val bookId: Long) : UiEvent
}

data class ImportProgress(val current: Int, val total: Int, val fileName: String?)

/** Lo que ve cada estante, para los chips. */
data class ShelfCounts(val all: Int, val unfinished: Int, val new: Int, val finished: Int) {
    fun of(shelf: LibraryShelf): Int = when (shelf) {
        LibraryShelf.ALL -> all
        LibraryShelf.UNFINISHED -> unfinished
        LibraryShelf.NEW -> new
        LibraryShelf.FINISHED -> finished
    }
}

/** Índice y resaltados de un libro abiertos desde la biblioteca («Capítulos y marcadores»). */
data class LibraryContents(val book: Book, val toc: List<FlatEntry<Link>>, val highlights: List<Highlight>)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: BookRepository,
    private val importer: EpubImporter,
    private val readerRepository: ReaderRepository,
    private val highlightRepository: HighlightRepository,
    private val preferences: AppPreferencesStore,
    private val listeningStats: ListeningStatsStore,
    val readAloud: ReadAloudManager,
) : ViewModel() {

    /** Racha y tiempo de escucha de la semana; se recalcula con cada anotación. */
    val streak: StateFlow<ListeningStreak.Summary> = listeningStats.version
        .map { listeningStats.summary() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listeningStats.summary())

    /** Todos los libros, en el orden de la base: último abierto primero. */
    val books: StateFlow<List<Book>> = repository.books
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sort: StateFlow<LibrarySort> = preferences.librarySort
    val hideFinished: StateFlow<Boolean> = preferences.hideFinished

    private val _shelf = MutableStateFlow(LibraryShelf.ALL)
    val shelf: StateFlow<LibraryShelf> = _shelf.asStateFlow()

    /** Los libros del estante elegido, en el orden elegido. */
    val visibleBooks: StateFlow<List<Book>> = combine(books, sort, shelf, hideFinished) { all, sort, shelf, hide ->
        all.visible(sort, shelf, hide)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shelfCounts: StateFlow<ShelfCounts> = books.combine(hideFinished) { all, hide ->
        val finished = all.count { it.isFinished() }
        ShelfCounts(
            all = if (hide) all.size - finished else all.size,
            unfinished = all.count { !it.isNew && !it.isFinished() },
            new = all.count { it.isNew },
            finished = finished,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfCounts(0, 0, 0, 0))

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchResults: StateFlow<List<Book>> = combine(books, query) { all, query -> all.search(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSearches: StateFlow<List<String>> = preferences.recentSearches

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private val _openingBookId = MutableStateFlow<Long?>(null)
    val openingBookId: StateFlow<Long?> = _openingBookId.asStateFlow()

    private val _contentsBookId = MutableStateFlow<Long?>(null)

    /** Índice y resaltados del libro cuyo «Capítulos y marcadores» está abierto. */
    val contents: StateFlow<LibraryContents?> = _contentsBookId.flatMapLatest { id ->
        val book = id?.let { bookId -> books.value.firstOrNull { it.id == bookId } }
        val publication = id?.let { readerRepository[it]?.publication }
        if (id == null || book == null || publication == null) {
            flowOf(null)
        } else {
            val toc = flattenTree(publication.tableOfContents.ifEmpty { publication.readingOrder }) { it.children }
            highlightRepository.observe(id).combine(flowOf(toc)) { highlights, entries -> LibraryContents(book, entries, highlights) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Serializa las importaciones para que el indicador de progreso sea coherente. */
    private val importLock = Mutex()

    fun setSort(sort: LibrarySort) = preferences.setLibrarySort(sort)

    fun setHideFinished(hide: Boolean) = preferences.setHideFinished(hide)

    fun setShelf(shelf: LibraryShelf) {
        _shelf.value = shelf
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    /** Anota la búsqueda en las recientes (al abrir un resultado o confirmar con el teclado). */
    fun rememberSearch() = preferences.addRecentSearch(_query.value)

    fun importAll(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importLock.withLock {
                var imported = 0
                var lastId: Long? = null
                uris.forEachIndexed { index, uri ->
                    _importProgress.value = ImportProgress(index + 1, uris.size, importer.displayName(uri))
                    when (val result = importer.import(uri)) {
                        is EpubImporter.Result.Success -> {
                            imported++
                            lastId = result.bookId
                        }
                        is EpubImporter.Result.Failure -> _messages.send(UiMessage.ImportFailed(result.error))
                    }
                }
                _importProgress.value = null
                if (imported > 0) _messages.send(UiMessage.Imported(imported, lastId.takeIf { imported == 1 }))
            }
        }
    }

    fun delete(book: Book) {
        viewModelScope.launch {
            // No se puede seguir leyendo un libro cuyo archivo se borra.
            if (readAloud.isActiveFor(book.id)) readAloud.stop()
            repository.delete(book)
            _messages.send(UiMessage.Deleted(book.title))
        }
    }

    fun setFinished(book: Book, finished: Boolean) {
        viewModelScope.launch {
            if (finished && readAloud.isActiveFor(book.id)) readAloud.stop()
            repository.setFinished(book.id, finished)
            _messages.send(UiMessage.MarkedFinished(book.title, finished))
        }
    }

    /**
     * Libro para «Reproduciendo» en la barra: el que suena o, si nada suena, el que la tarjeta de
     * Inicio ofrece seguir. Sin libros devuelve null y avisa.
     */
    fun nowPlayingTarget(): Long? {
        val target = readAloud.activeBookId.value
            ?: books.value.firstOrNull { !it.isFinished() }?.id
            ?: books.value.firstOrNull()?.id
        if (target == null) viewModelScope.launch { _messages.send(UiMessage.NothingToPlay) }
        return target
    }

    /** Abre la publicación con Readium y, si funciona, pide a la pantalla que lance el lector. */
    fun openBook(book: Book) = openBook(book.id)

    fun openBook(bookId: Long) {
        if (_openingBookId.value != null) return
        viewModelScope.launch {
            _openingBookId.value = bookId
            try {
                when (val result = readerRepository.open(bookId)) {
                    is OpenBookResult.Success -> _events.send(UiEvent.OpenReader(bookId))
                    is OpenBookResult.Failure -> _messages.send(UiMessage.OpenFailed(result.error))
                }
            } finally {
                _openingBookId.value = null
            }
        }
    }

    /** Escucha un libro sin abrir el lector. Si ya es el que suena, alterna play y pausa. */
    fun listen(book: Book) {
        if (readAloud.isActiveFor(book.id)) {
            readAloud.togglePlayPause()
            return
        }
        listenAt(book.id, null)
    }

    fun listen(bookId: Long) {
        books.value.firstOrNull { it.id == bookId }?.let { listen(it) }
    }

    /**
     * Arranca la voz en [bookId] desde [at], o desde la posición guardada si es null. La lectura
     * necesita la publicación abierta: la abrimos (una referencia), arrancamos y soltamos nuestra
     * referencia cuando el gestor ya tomó la suya o terminó de intentarlo. Mientras falten las
     * voces la sostenemos, porque el gestor arranca solo cuando terminan de instalarse.
     */
    private fun listenAt(bookId: Long, at: Locator?) {
        if (_openingBookId.value != null) return
        viewModelScope.launch {
            _openingBookId.value = bookId
            val result = try {
                readerRepository.open(bookId)
            } finally {
                _openingBookId.value = null
            }
            when (result) {
                is OpenBookResult.Failure -> _messages.send(UiMessage.OpenFailed(result.error))
                is OpenBookResult.Success -> try {
                    readAloud.start(bookId, at ?: result.session.initialLocator)
                    readAloud.status.first {
                        it != ReadAloudManager.Status.Preparing && it != ReadAloudManager.Status.NeedsModel
                    }
                } finally {
                    readerRepository.release(bookId)
                }
            }
        }
    }

    /** «Capítulos y marcadores» de un libro: abre su publicación para listar el índice. */
    fun openContents(book: Book) {
        if (_openingBookId.value != null) return
        viewModelScope.launch {
            _openingBookId.value = book.id
            val result = try {
                readerRepository.open(book.id)
            } finally {
                _openingBookId.value = null
            }
            when (result) {
                is OpenBookResult.Failure -> _messages.send(UiMessage.OpenFailed(result.error))
                is OpenBookResult.Success -> _contentsBookId.value = book.id
            }
        }
    }

    fun closeContents() {
        _contentsBookId.value?.let { readerRepository.release(it) }
        _contentsBookId.value = null
    }

    /** Desde el índice: escuchar desde el principio de un capítulo. */
    fun listenFromChapter(link: Link) {
        val bookId = _contentsBookId.value ?: return
        val locator = readerRepository[bookId]?.publication?.locatorFromLink(link)
        closeContents()
        if (readAloud.isActiveFor(bookId)) {
            readAloud.start(bookId, locator)
        } else {
            listenAt(bookId, locator)
        }
    }

    /** Desde los resaltados: escuchar desde el pasaje. */
    fun listenFromHighlight(highlight: Highlight) {
        val bookId = _contentsBookId.value ?: return
        closeContents()
        if (readAloud.isActiveFor(bookId)) {
            readAloud.start(bookId, highlight.locator)
        } else {
            listenAt(bookId, highlight.locator)
        }
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch { highlightRepository.delete(id) }
    }

    override fun onCleared() {
        closeContents()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    container.bookRepository,
                    container.importer,
                    container.readerRepository,
                    container.highlightRepository,
                    container.appPreferences,
                    container.listeningStats,
                    container.readAloud,
                )
            }
        }
    }
}
