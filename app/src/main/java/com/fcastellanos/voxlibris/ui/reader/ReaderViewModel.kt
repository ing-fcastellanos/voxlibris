package com.fcastellanos.voxlibris.ui.reader

import android.app.Application
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
import com.fcastellanos.voxlibris.tts.SherpaTtsEngineProvider
import com.fcastellanos.voxlibris.tts.TtsPreferencesStore
import com.fcastellanos.voxlibris.tts.VoiceModelManager
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
    private val bookId: Long,
    private val application: Application,
    private val readerRepository: ReaderRepository,
    private val bookRepository: BookRepository,
    private val preferencesStore: ReadingPreferencesStore,
    private val applicationScope: CoroutineScope,
    ttsEngineProvider: SherpaTtsEngineProvider,
    voiceModels: VoiceModelManager,
    ttsPreferences: TtsPreferencesStore,
) : ViewModel(), EpubNavigatorFragment.Listener {

    sealed interface Navigation {
        data class ToLocator(val locator: Locator) : Navigation
        data class ToLink(val link: Link) : Navigation
    }

    val session: ReaderSession? = readerRepository[bookId]

    val title: String = session?.title.orEmpty()

    /** Lectura en voz alta; null solo si la sesión no existe (el lector se cierra en ese caso). */
    val readAloud: ReadAloudController? = session?.let {
        ReadAloudController(application, it, ttsEngineProvider, voiceModels, ttsPreferences, viewModelScope)
    }

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

    /** Empieza a leer desde la posición actual del lector. */
    fun listen() {
        readAloud?.start(from = _currentLocator.value)
    }

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
        readAloud?.stop()
        saveJob?.cancel()
        val pending = _currentLocator.value?.toJSON()?.toString()?.takeIf { it != lastSavedJson }
        readerRepository.close(bookId)
        if (pending != null) {
            applicationScope.launch { bookRepository.saveProgression(bookId, pending) }
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 600L

        fun factory(container: AppContainer, bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReaderViewModel(
                    bookId = bookId,
                    application = container.application,
                    readerRepository = container.readerRepository,
                    bookRepository = container.bookRepository,
                    preferencesStore = container.readingPreferences,
                    applicationScope = container.applicationScope,
                    ttsEngineProvider = container.ttsEngineProvider,
                    voiceModels = container.voiceModels,
                    ttsPreferences = container.ttsPreferences,
                )
            }
        }
    }
}
