package com.fcastellanos.voxlibris.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fcastellanos.voxlibris.data.Book
import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.data.Highlight
import com.fcastellanos.voxlibris.data.HighlightRepository
import com.fcastellanos.voxlibris.di.AppContainer
import com.fcastellanos.voxlibris.reading.ReaderRepository
import com.fcastellanos.voxlibris.tts.ChapterSentences
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import com.fcastellanos.voxlibris.tts.SherpaTtsDefaults
import com.fcastellanos.voxlibris.tts.SherpaTtsSettings
import com.fcastellanos.voxlibris.tts.TtsPreferencesStore
import com.fcastellanos.voxlibris.tts.resolve
import com.fcastellanos.voxlibris.ui.reader.FlatEntry
import com.fcastellanos.voxlibris.ui.reader.ReaderViewModel
import com.fcastellanos.voxlibris.ui.reader.flattenTree
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Reproductor a pantalla completa de un libro: lo que suena, en qué frase del capítulo va, cuánto
 * falta, y los controles. Si el libro no está sonando, muestra dónde quedó y ofrece seguir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    val bookId: Long,
    val readAloud: ReadAloudManager,
    private val books: BookRepository,
    private val highlights: HighlightRepository,
    private val readerRepository: ReaderRepository,
    private val ttsStore: TtsPreferencesStore,
) : ViewModel() {

    /** Frase que suena dentro del capítulo, desde 1, y cuántas tiene el capítulo. */
    data class Position(val sentence: Int, val total: Int)

    val book: StateFlow<Book?> = books.books
        .map { list -> list.firstOrNull { it.id == bookId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isActive: StateFlow<Boolean> = readAloud.activeBookId
        .map { it == bookId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, readAloud.isActiveFor(bookId))

    val status: StateFlow<ReadAloudManager.Status> = combine(isActive, readAloud.status) { active, status ->
        if (active) status else ReadAloudManager.Status.Idle
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReadAloudManager.Status.Idle)

    /** La frase que suena, con su texto anterior y posterior dentro del párrafo. */
    val highlight: StateFlow<Locator?> = combine(isActive, readAloud.highlight) { active, locator ->
        if (active) locator else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<SherpaTtsSettings> = readAloud.preferences
        .map { it.resolve(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, readAloud.preferences.value.resolve(null))

    val highlightList: StateFlow<List<Highlight>> = highlights.observe(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val publication: Publication? get() = readerRepository[bookId]?.publication

    /** Índice del libro, aplanado, mientras la publicación está abierta (es decir, mientras suena). */
    val toc: StateFlow<List<FlatEntry<Link>>> = isActive.map { active ->
        if (!active) emptyList() else publication?.let { pub -> flattenTree(pub.tableOfContents.ifEmpty { pub.readingOrder }) { it.children } }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Título del capítulo que suena según el índice; si el índice no lo nombra, su número en el orden de lectura. */
    val chapterTitle: StateFlow<String?> = combine(highlight, toc) { locator, entries ->
        val href = locator?.href?.toString() ?: return@combine null
        entries.firstOrNull { it.item.url().toString().substringBefore('#') == href }?.item?.title
            ?: publication?.readingOrder?.indexOfFirst { it.url().toString() == href }?.takeIf { it >= 0 }?.let { "#${it + 1}" }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val chapters = mutableMapOf<String, ChapterSentences>()

    /** «Frase 38 de 172»: se calcula una vez por capítulo, en segundo plano, y luego por frase. */
    val position: StateFlow<Position?> = highlight.mapLatest { locator ->
        locator ?: return@mapLatest null
        val href = locator.href.toString()
        val chapter = chapters[href]
            ?: publication?.let { ChapterSentences.build(it, locator) }?.also { chapters[href] = it }
            ?: return@mapLatest null
        chapter.indexOf(locator.locations.progression, locator.text.highlight)?.let { Position(it, chapter.total) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _noteEditor = MutableStateFlow<ReaderViewModel.NoteEditor?>(null)
    val noteEditor: StateFlow<ReaderViewModel.NoteEditor?> = _noteEditor.asStateFlow()

    fun togglePlayPause() = readAloud.togglePlayPause()

    fun next() = readAloud.next()

    fun previous() = readAloud.previous()

    fun stop() = readAloud.stop()

    fun setSpeed(speed: Double) {
        val rounded = ((speed * 10).roundToInt() / 10.0).coerceIn(SherpaTtsDefaults.SPEED_RANGE)
        ttsStore.update(ttsStore.preferences.value.copy(speed = rounded))
    }

    fun setSleepTimer(minutes: Int?) = readAloud.setSleepTimer(minutes)

    /** Lleva la lectura al principio de un capítulo del índice. */
    fun goToChapter(link: Link) {
        val locator = publication?.locatorFromLink(link) ?: return
        readAloud.start(bookId, locator)
    }

    /** Sigue leyendo desde un pasaje resaltado. */
    fun listenFrom(highlight: Highlight) = readAloud.start(bookId, highlight.locator)

    fun editNote(highlight: Highlight) {
        _noteEditor.value = ReaderViewModel.NoteEditor(highlight.id, highlight.text, highlight.note, isNew = false)
    }

    fun saveNote(highlightId: Long, note: String) {
        viewModelScope.launch { highlights.updateNote(highlightId, note) }
        _noteEditor.value = null
    }

    fun dismissNoteEditor() {
        _noteEditor.value = null
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch { highlights.delete(id) }
    }

    companion object {
        fun factory(container: AppContainer, bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlayerViewModel(bookId, container.readAloud, container.bookRepository, container.highlightRepository, container.readerRepository, container.ttsPreferences)
            }
        }
    }
}
