package com.fcastellanos.voxlibris.ui.reader

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.fcastellanos.voxlibris.reading.ReaderSession
import com.fcastellanos.voxlibris.tts.SherpaTtsEngine
import com.fcastellanos.voxlibris.tts.SherpaTtsEngineProvider
import com.fcastellanos.voxlibris.tts.SherpaTtsError
import com.fcastellanos.voxlibris.tts.SherpaTtsPreferences
import com.fcastellanos.voxlibris.tts.SherpaTtsSettings
import com.fcastellanos.voxlibris.tts.SherpaVoice
import com.fcastellanos.voxlibris.tts.TtsPreferencesStore
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.getOrElse

/**
 * Orquesta la lectura en voz alta de una publicación: crea el TtsNavigator de Readium con el
 * motor sherpa-onnx, expone el estado para la interfaz y la frase que suena para resaltarla.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReadAloudController(
    private val application: Application,
    private val session: ReaderSession,
    private val provider: SherpaTtsEngineProvider,
    private val models: VoiceModelManager,
    private val ttsPreferences: TtsPreferencesStore,
    private val scope: CoroutineScope,
) : TtsNavigator.Listener {

    sealed interface Status {
        data object Idle : Status
        data object NeedsModel : Status
        data object Preparing : Status
        data object Playing : Status
        data object Paused : Status
        data object Ended : Status
        data class Error(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Frase que está sonando ahora mismo, para resaltarla. */
    private val _highlight = MutableStateFlow<Locator?>(null)
    val highlight: StateFlow<Locator?> = _highlight.asStateFlow()

    /** Posición a la que conviene llevar la página para seguir la lectura. */
    private val _pageSync = MutableSharedFlow<Locator>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val pageSync: SharedFlow<Locator> = _pageSync.asSharedFlow()

    val modelState: StateFlow<VoiceModelManager.State> = models.state
    val preferences: StateFlow<SherpaTtsPreferences> = ttsPreferences.preferences

    private var navigator: TtsNavigator<SherpaTtsSettings, SherpaTtsPreferences, SherpaTtsError, SherpaVoice>? = null
    private var collectors: Job? = null
    private var highlightFallback: Job? = null
    private var pendingLocator: Locator? = null
    private var hasStartedSpeaking = false
    private var resumeFrom: Locator? = null
    private var startJob: Job? = null

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
        }
        .build()

    init {
        // Si el usuario pidió escuchar sin tener las voces, arrancamos solos cuando terminen de instalarse.
        scope.launch {
            models.state.collect { state ->
                if (state is VoiceModelManager.State.Ready && _status.value == Status.NeedsModel) start(resumeFrom)
            }
        }
    }

    fun start(from: Locator?) {
        if (navigator != null) {
            play()
            return
        }
        if (startJob?.isActive == true) return
        resumeFrom = from
        if (models.readyModelDir() == null) {
            _status.value = Status.NeedsModel
            return
        }
        _status.value = Status.Preparing
        startJob = scope.launch {
            val factory = TtsNavigatorFactory(application, session.publication, provider)
            if (factory == null) {
                _status.value = Status.Error("El libro no expone texto legible")
                return@launch
            }
            val created = factory.createNavigator(
                listener = this@ReadAloudController,
                initialLocator = from,
                initialPreferences = ttsPreferences.preferences.value,
            ).getOrElse {
                _status.value = Status.Error(it.message)
                return@launch
            }
            navigator = created
            hasStartedSpeaking = false
            observe(created)
            requestFocus()
            created.play()
        }
    }

    fun play() {
        val nav = navigator ?: return
        requestFocus()
        nav.play()
    }

    fun pause() {
        navigator?.pause()
        provider.activeEngine?.interrupt()
    }

    fun togglePlayPause() {
        when (_status.value) {
            Status.Playing, Status.Preparing -> pause()
            Status.Paused, Status.Ended -> play()
            else -> Unit
        }
    }

    fun next() = navigator?.skipToNextUtterance()

    fun previous() = navigator?.skipToPreviousUtterance()

    fun downloadVoices() = models.install()

    fun stop() {
        startJob?.cancel()
        collectors?.cancel()
        highlightFallback?.cancel()
        navigator?.let { nav ->
            nav.pause()
            provider.activeEngine?.interrupt()
            nav.close()
        }
        navigator = null
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        _highlight.value = null
        pendingLocator = null
        hasStartedSpeaking = false
        _status.value = Status.Idle
    }

    override fun onStopRequested() = stop()

    private fun observe(nav: TtsNavigator<SherpaTtsSettings, SherpaTtsPreferences, SherpaTtsError, SherpaVoice>) {
        collectors = scope.launch {
            launch {
                nav.playback.collect { playback ->
                    _status.value = when (val state = playback.state) {
                        is TtsNavigator.State.Failure -> Status.Error(state.error.message)
                        TtsNavigator.State.Ended -> Status.Ended
                        else -> when {
                            !playback.playWhenReady -> Status.Paused
                            hasStartedSpeaking -> Status.Playing
                            else -> Status.Preparing
                        }
                    }
                }
            }
            launch {
                nav.location.collect { location ->
                    pendingLocator = location.utteranceLocator
                    _pageSync.tryEmit(location.utteranceLocator)
                    highlightFallback?.cancel()
                    highlightFallback = launch {
                        delay(HIGHLIGHT_FALLBACK_MS)
                        applyPendingHighlight()
                    }
                }
            }
            launch {
                provider.events.collect { event ->
                    if (event is SherpaTtsEngine.Event.UtteranceStarted) {
                        hasStartedSpeaking = true
                        if (_status.value == Status.Preparing) _status.value = Status.Playing
                        applyPendingHighlight()
                    }
                }
            }
            launch { ttsPreferences.preferences.collect { nav.submitPreferences(it) } }
        }
    }

    private fun applyPendingHighlight() {
        highlightFallback?.cancel()
        pendingLocator?.let { _highlight.value = it }
    }

    private fun requestFocus() {
        runCatching { audioManager.requestAudioFocus(focusRequest) }
    }

    private companion object {
        const val HIGHLIGHT_FALLBACK_MS = 1500L
    }
}
