package com.fcastellanos.readapp.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fcastellanos.readapp.data.Book
import com.fcastellanos.readapp.data.BookRepository
import com.fcastellanos.readapp.data.EpubImporter
import com.fcastellanos.readapp.di.AppContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface UiMessage {
    data class Imported(val count: Int) : UiMessage
    data class ImportFailed(val error: EpubImporter.ImportError) : UiMessage
    data class Deleted(val title: String) : UiMessage
    data object ReaderNotReady : UiMessage
}

data class ImportProgress(val current: Int, val total: Int)

class LibraryViewModel(
    private val repository: BookRepository,
    private val importer: EpubImporter,
) : ViewModel() {

    val books: StateFlow<List<Book>> = repository.books
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

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
            repository.delete(book)
            _messages.send(UiMessage.Deleted(book.title))
        }
    }

    fun openBook(@Suppress("UNUSED_PARAMETER") book: Book) {
        // El lector se conecta en la siguiente fase.
        viewModelScope.launch { _messages.send(UiMessage.ReaderNotReady) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container.bookRepository, container.importer) }
        }
    }
}
