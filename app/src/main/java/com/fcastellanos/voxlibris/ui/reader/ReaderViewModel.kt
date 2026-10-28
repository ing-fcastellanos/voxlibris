package com.fcastellanos.voxlibris.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.di.AppContainer
import com.fcastellanos.voxlibris.reading.ReaderRepository
import com.fcastellanos.voxlibris.reading.ReaderSession
import com.fcastellanos.voxlibris.reading.ReadingPreferencesStore
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.readium.r2.navigator.Selection
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.tokenizer.DefaultTextContentTokenizer
import org.readium.r2.shared.util.tokenizer.TextUnit
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesEditor
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.AbsoluteUrl

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
    val bookId: Long,
    private val readerRepository: ReaderRepository,
    private val bookRepository: BookRepository,
    private val preferencesStore: ReadingPreferencesStore,
    private val applicationScope: CoroutineScope,
    val readAloud: ReadAloudManager,
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

    private val _navigation = Channel<Navigation>(Channel.BUFFERED)
    val navigation: Flow<Navigation> = _navigation.receiveAsFlow()

    private val _externalLinks = Channel<AbsoluteUrl>(Channel.BUFFERED)
    val externalLinks: Flow<AbsoluteUrl> = _externalLinks.receiveAsFlow()

    private var saveJob: Job? = null
    private var lastSavedJson: String? = session?.initialLocator?.toJSON()?.toString()

    init {
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
        private const val SAVE_DEBOUNCE_MS = 600L
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
                )
            }
        }
    }
}
