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
import com.fcastellanos.voxlibris.di.AppContainer
import com.fcastellanos.voxlibris.reading.OpenBookError
import com.fcastellanos.voxlibris.reading.OpenBookResult
import com.fcastellanos.voxlibris.reading.ReaderRepository
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface UiMessage {
    data class Imported(val count: Int) : UiMessage
    data class ImportFailed(val error: EpubImporter.ImportError) : UiMessage
    data class Deleted(val title: String) : UiMessage
    data class OpenFailed(val error: OpenBookError) : UiMessage
}

sealed interface UiEvent {
    data class OpenReader(val bookId: Long) : UiEvent
}

data class ImportProgress(val current: Int, val total: Int)

class LibraryViewModel(
    private val repository: BookRepository,
    private val importer: EpubImporter,
    private val readerRepository: ReaderRepository,
    val readAloud: ReadAloudManager,
) : ViewModel() {

    val books: StateFlow<List<Book>> = repository.books
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private val _openingBookId = MutableStateFlow<Long?>(null)
    val openingBookId: StateFlow<Long?> = _openingBookId.asStateFlow()

    /** Serializa las importaciones para que el indicador de progreso sea coherente. */
    private val importLock = Mutex()

    fun importAll(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importLock.withLock {
                var imported = 0
                uris.forEachIndexed { index, uri ->
                    _importProgress.value = ImportProgress(index + 1, uris.size)
                    when (val result = importer.import(uri)) {
                        is EpubImporter.Result.Success -> imported++
                        is EpubImporter.Result.Failure -> _messages.send(UiMessage.ImportFailed(result.error))
                    }
                }
                _importProgress.value = null
                if (imported > 0) _messages.send(UiMessage.Imported(imported))
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

    /**
     * Escucha un libro sin abrir el lector. Si ya es el que suena, alterna play y pausa.
     *
     * La lectura en voz alta necesita la publicación abierta: la abrimos (una referencia),
     * arrancamos desde la posición guardada y soltamos nuestra referencia cuando el gestor ya
     * tomó la suya, o cuando terminó de intentarlo. Mientras falten las voces la sostenemos,
     * porque el gestor arranca solo cuando terminan de instalarse.
     */
    fun listen(book: Book) {
        if (readAloud.isActiveFor(book.id)) {
            readAloud.togglePlayPause()
            return
        }
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
                is OpenBookResult.Success -> try {
                    readAloud.start(book.id, result.session.initialLocator)
                    readAloud.status.first {
                        it != ReadAloudManager.Status.Preparing && it != ReadAloudManager.Status.NeedsModel
                    }
                } finally {
                    readerRepository.release(book.id)
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container.bookRepository, container.importer, container.readerRepository, container.readAloud) }
        }
    }
}
