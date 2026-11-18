package com.fcastellanos.voxlibris.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.data.Highlight
import com.fcastellanos.voxlibris.data.HighlightRepository
import com.fcastellanos.voxlibris.di.AppContainer
import com.fcastellanos.voxlibris.dictionary.DictionaryResult
import com.fcastellanos.voxlibris.dictionary.DictionaryWords
import com.fcastellanos.voxlibris.dictionary.WiktionaryDictionary
import com.fcastellanos.voxlibris.reading.ReaderRepository
import com.fcastellanos.voxlibris.reading.ReaderSession
import com.fcastellanos.voxlibris.reading.ReadingPreferencesStore
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesEditor
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.tokenizer.DefaultTextContentTokenizer
import org.readium.r2.shared.util.tokenizer.TextUnit

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
    val bookId: Long,
    private val readerRepository: ReaderRepository,
    private val bookRepository: BookRepository,
    private val preferencesStore: ReadingPreferencesStore,
    private val applicationScope: CoroutineScope,
    val readAloud: ReadAloudManager,
    private val highlightRepository: HighlightRepository,
    private val highlightTint: Int,
    private val noteMarkTint: Int,
    private val dictionary: WiktionaryDictionary,
) : ViewModel(), EpubNavigatorFragment.Listener {

    sealed interface Navigation {
        data class ToLocator(val locator: Locator) : Navigation
        data class ToLink(val link: Link) : Navigation
    }

    val session: ReaderSession? = readerRepository[bookId]

    val title: String = session?.title.orEmpty()

    /** Índice aplanado; si el EPUB no declara índice, se usa el orden de lectura. */
    val toc: List<FlatEntry<Link>> = session?.publication?.let { publication ->
        val roots = publication.tableOfContents.ifEmpty { publication.readingOrder }
        flattenTree(roots) { it.children }
    } ?: emptyList()

    private val _positions = MutableStateFlow<List<Locator>>(emptyList())
    val positions: StateFlow<List<Locator>> = _positions.asStateFlow()

    private val _currentLocator = MutableStateFlow(session?.initialLocator)
    val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    private val _overlayVisible = MutableStateFlow(true)
    val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

    val preferences: StateFlow<EpubPreferences> = preferencesStore.preferences

    /** Resaltados del libro, en orden de lectura. */
    val highlights: StateFlow<List<Highlight>> = highlightRepository.observe(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Los mismos resaltados como decoraciones para que Readium los dibuje sobre el texto.
     * Los que tienen nota llevan además un icono al final del pasaje, como en Kindle.
     */
    val highlightDecorations: StateFlow<List<Decoration>> = highlights
        .map { list ->
            list.flatMap { highlight ->
                listOfNotNull(
                    Decoration(id = decorationId(highlight.id), locator = highlight.locator, style = Decoration.Style.Highlight(tint = highlight.color)),
                    highlight.note?.let {
                        Decoration(id = noteDecorationId(highlight.id), locator = highlight.locator, style = NoteMarkStyle(tint = noteMarkTint))
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tarjeta de diccionario, como la de Kindle al mantener pulsada una palabra. */
    sealed interface DefinitionState {
        val word: String

        data class Loading(override val word: String) : DefinitionState
        data class Found(override val word: String, val result: DictionaryResult.Found) : DefinitionState
        data class NotFound(override val word: String) : DefinitionState
        data class Offline(override val word: String) : DefinitionState
    }

    private val _definition = MutableStateFlow<DefinitionState?>(null)
    val definition: StateFlow<DefinitionState?> = _definition.asStateFlow()
    private var lookupJob: Job? = null

    /** Con una sola palabra seleccionada se busca su definición; con más texto, la tarjeta se cierra. */
    fun onSelectionChanged(selectedText: String?) {
        val word = DictionaryWords.singleWord(selectedText)
        if (word == null) {
            dismissDefinition()
            return
        }
        if (_definition.value?.word == word) return
        lookupJob?.cancel()
        _definition.value = DefinitionState.Loading(word)
        lookupJob = viewModelScope.launch {
            _definition.value = when (val result = dictionary.lookUp(word)) {
                is DictionaryResult.Found -> DefinitionState.Found(word, result)
                DictionaryResult.NotFound -> DefinitionState.NotFound(word)
                DictionaryResult.Offline -> DefinitionState.Offline(word)
            }
        }
    }

    fun dismissDefinition() {
        lookupJob?.cancel()
        lookupJob = null
        _definition.value = null
    }

    /** Resaltado que el usuario tocó en el texto, para ofrecer eliminarlo. */
    private val _tappedHighlight = MutableStateFlow<Highlight?>(null)
    val tappedHighlight: StateFlow<Highlight?> = _tappedHighlight.asStateFlow()

    /** Nota que el usuario está escribiendo, para un resaltado recién guardado o para uno que ya tenía. */
    data class NoteEditor(val highlightId: Long, val text: String, val note: String?, val isNew: Boolean)

    private val _noteEditor = MutableStateFlow<NoteEditor?>(null)
    val noteEditor: StateFlow<NoteEditor?> = _noteEditor.asStateFlow()

    /** Guarda el resaltado y abre el editor de nota; cerrarlo sin escribir deja el resaltado igual. */
    fun addHighlight(selection: Selection) {
        val text = selection.locator.text.highlight.orEmpty().trim()
        viewModelScope.launch {
            val id = highlightRepository.add(bookId, selection.locator, highlightTint)
            _noteEditor.value = NoteEditor(id, text, note = null, isNew = true)
        }
    }

    fun editNote(highlight: Highlight) {
        _tappedHighlight.value = null
        _noteEditor.value = NoteEditor(highlight.id, highlight.text, highlight.note, isNew = false)
    }

    fun saveNote(highlightId: Long, note: String) {
        viewModelScope.launch { highlightRepository.updateNote(highlightId, note) }
        _noteEditor.value = null
    }

    fun dismissNoteEditor() {
        _noteEditor.value = null
    }

    fun deleteHighlight(id: Long) {
        viewModelScope.launch { highlightRepository.delete(id) }
        if (_tappedHighlight.value?.id == id) _tappedHighlight.value = null
        if (_noteEditor.value?.highlightId == id) _noteEditor.value = null
    }

    fun goToHighlight(highlight: Highlight) {
        _navigation.trySend(Navigation.ToLocator(highlight.locator))
    }

    fun onHighlightDecorationTapped(decorationId: String) {
        val id = HIGHLIGHT_DECORATION_ID.find(decorationId)?.groupValues?.get(1)?.toLongOrNull() ?: return
        _tappedHighlight.value = highlights.value.firstOrNull { it.id == id }
    }

    fun dismissTappedHighlight() {
        _tappedHighlight.value = null
    }

    private val _navigation = Channel<Navigation>(Channel.BUFFERED)
    val navigation: Flow<Navigation> = _navigation.receiveAsFlow()

    /**
     * Con la pantalla bloqueada o fuera del lector, la página deja de seguir la voz. Si mientras
     * tanto la lectura avanzó, al volver se lleva la página hasta la frase actual, también en pausa.
     * Si no avanzó (se pausó en el lector y se hojeó otra cosa), se respeta la página que se mira.
     */
    private fun followReadAloudWhileAway() {
        viewModelScope.launch {
            var lastSeen = readAloud.highlight.value
            var wasFollowing = readAloud.readerFollowing.value
            var missed: Locator? = null
            combine(readAloud.readerFollowing, readAloud.highlight, readAloud.activeBookId) { following, highlight, activeId ->
                Triple(following, highlight, activeId)
            }.collect { (following, highlight, activeId) ->
                if (activeId != bookId) {
                    missed = null
                } else {
                    if (!following && highlight != null && highlight != lastSeen) missed = highlight
                    if (following && !wasFollowing) {
                        missed?.let { _navigation.trySend(Navigation.ToLocator(it)) }
                        missed = null
                    }
                }
                lastSeen = highlight
                wasFollowing = following
            }
        }
    }

    private val _externalLinks = Channel<AbsoluteUrl>(Channel.BUFFERED)
    val externalLinks: Flow<AbsoluteUrl> = _externalLinks.receiveAsFlow()

    private var saveJob: Job? = null
    private var lastSavedJson: String? = session?.initialLocator?.toJSON()?.toString()

    init {
        followReadAloudWhileAway()
        session?.let { s ->
            viewModelScope.launch {
                _positions.value = runCatching { s.publication.positions() }.getOrDefault(emptyList())
            }
        }
    }

    fun toggleOverlay() {
        _overlayVisible.value = !_overlayVisible.value
    }

    /** Empieza a leer en voz alta desde la posición actual del lector. */
    fun listen() {
        readAloud.start(bookId, _currentLocator.value)
    }

    /** Punto de arranque para la voz: el párrafo que contiene la selección y cuántas frases suyas saltar. */
    data class ReadAloudStart(val locator: Locator, val skipSentences: Int)

    /** Empieza (o mueve) la lectura en voz alta a un punto elegido por el usuario. */
    fun listenFrom(start: ReadAloudStart) {
        readAloud.start(bookId, start.locator, start.skipSentences)
    }

    /**
     * El Locator de una selección de Readium es el de la página, sin posición dentro del recurso.
     * Para arrancar donde el usuario tocó, se busca el texto seleccionado en el contenido a partir
     * del primer elemento visible: el segmento que lo contiene trae un Locator con `cssSelector`, y
     * el tokenizador de frases (el mismo que usa el TtsNavigator) dice cuántas frases preceden a la
     * selección dentro de ese segmento. Si no se encuentra, se arranca al principio de la página visible.
     */
    suspend fun resolveReadAloudStart(selection: Selection, firstVisible: Locator?): ReadAloudStart = withContext(Dispatchers.Default) {
        val fallback = ReadAloudStart(firstVisible ?: selection.locator, 0)
        val publication = session?.publication ?: return@withContext fallback
        val needle = selection.locator.text.highlight?.collapseSpaces()?.take(NEEDLE_CHARS)?.takeIf { it.length >= 2 }
            ?: return@withContext fallback
        val language = publication.metadata.languages.firstOrNull()?.let { Language(it) }
        val tokenizer = DefaultTextContentTokenizer(TextUnit.Sentence, language)
        val iterator = runCatching { publication.content(firstVisible ?: selection.locator)?.iterator() }.getOrNull()
            ?: return@withContext fallback
        var visited = 0
        while (visited < MAX_ELEMENTS_TO_SCAN && iterator.hasNext()) {
            val element = iterator.next()
            visited++
            val segments = (element as? Content.TextElement)?.segments ?: continue
            for (segment in segments) {
                val text = segment.text.collapseSpaces()
                val index = text.indexOf(needle)
                if (index < 0) continue
                val skip = tokenizer.tokenize(text).count { range ->
                    range.last < index && text.substring(range).isNotBlank()
                }
                return@withContext ReadAloudStart(segment.locator, skip)
            }
        }
        fallback
    }

    private fun String.collapseSpaces(): String = replace(WHITESPACE, " ").trim()

    fun onLocatorChanged(locator: Locator) {
        _currentLocator.value = locator
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist(locator)
        }
    }

    private suspend fun persist(locator: Locator) {
        val json = locator.toJSON().toString()
        if (json == lastSavedJson) return
        lastSavedJson = json
        bookRepository.saveProgression(bookId, json)
    }

    fun go(link: Link) {
        _navigation.trySend(Navigation.ToLink(link))
    }

    fun goToPosition(index: Int) {
        positions.value.getOrNull(index)?.let { _navigation.trySend(Navigation.ToLocator(it)) }
    }

    fun createPreferencesEditor(): EpubPreferencesEditor? =
        session?.navigatorFactory?.createPreferencesEditor(preferences.value)

    fun updatePreferences(preferences: EpubPreferences) = preferencesStore.update(preferences)

    // EpubNavigatorFragment.Listener
    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        _externalLinks.trySend(url)
    }

    override fun onCleared() {
        saveJob?.cancel()
        val pending = _currentLocator.value?.toJSON()?.toString()?.takeIf { it != lastSavedJson }
        // La lectura en voz alta sigue en segundo plano: solo soltamos nuestra referencia a la publicación.
        readerRepository.release(bookId)
        if (pending != null) {
            applicationScope.launch { bookRepository.saveProgression(bookId, pending) }
        }
    }

    companion object {
        const val HIGHLIGHTS_GROUP = "highlights"
        private const val HIGHLIGHT_DECORATION_PREFIX = "highlight-"
        private val HIGHLIGHT_DECORATION_ID = Regex("^$HIGHLIGHT_DECORATION_PREFIX(\\d+)")
        private const val SAVE_DEBOUNCE_MS = 600L

        fun decorationId(highlightId: Long): String = HIGHLIGHT_DECORATION_PREFIX + highlightId

        fun noteDecorationId(highlightId: Long): String = decorationId(highlightId) + "-note"
        private const val NEEDLE_CHARS = 24
        private const val MAX_ELEMENTS_TO_SCAN = 400
        private val WHITESPACE = Regex("\\s+")

        fun factory(container: AppContainer, bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReaderViewModel(
                    bookId = bookId,
                    readerRepository = container.readerRepository,
                    bookRepository = container.bookRepository,
                    preferencesStore = container.readingPreferences,
                    applicationScope = container.applicationScope,
                    readAloud = container.readAloud,
                    highlightRepository = container.highlightRepository,
                    highlightTint = container.highlightTint,
                    noteMarkTint = container.noteMarkTint,
                    dictionary = container.dictionary,
                )
            }
        }
    }
}
