package com.fcastellanos.voxlibris.tts

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.reading.ReaderRepository
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.getOrElse

/**
 * Lectura en voz alta a nivel de aplicación: una sola sesión activa que sobrevive a la pantalla
 * del lector. Crea el TtsNavigator de Readium con el motor sherpa-onnx, lo publica en el servicio
 * multimedia (notificación, pantalla de bloqueo), guarda el progreso y expone estado para la UI.
 */
@OptIn(ExperimentalReadiumApi::class, FlowPreview::class)
class ReadAloudManager(
    private val application: Application,
    private val provider: SherpaTtsEngineProvider,
    private val models: VoiceModelManager,
    private val ttsPreferences: TtsPreferencesStore,
    private val readerRepository: ReaderRepository,
    private val bookRepository: BookRepository,
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _activeBookId = MutableStateFlow<Long?>(null)
    val activeBookId: StateFlow<Long?> = _activeBookId.asStateFlow()

    private val _activeTitle = MutableStateFlow<String?>(null)
    val activeTitle: StateFlow<String?> = _activeTitle.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Frase que está sonando ahora mismo, para resaltarla en el lector. */
    private val _highlight = MutableStateFlow<Locator?>(null)
    val highlight: StateFlow<Locator?> = _highlight.asStateFlow()

    /** Posición a la que conviene llevar la página para seguir la lectura. */
    private val _pageSync = MutableSharedFlow<Locator>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val pageSync: SharedFlow<Locator> = _pageSync.asSharedFlow()

    /** Instante (epoch ms) en que el temporizador de sueño pausará la lectura, o null. */
    private val _sleepTimerEndsAt = MutableStateFlow<Long?>(null)
    val sleepTimerEndsAt: StateFlow<Long?> = _sleepTimerEndsAt.asStateFlow()

    val modelState: StateFlow<VoiceModelManager.State> = models.state
    val preferences: StateFlow<SherpaTtsPreferences> = ttsPreferences.preferences

    private var navigator: TtsNavigator<SherpaTtsSettings, SherpaTtsPreferences, SherpaTtsError, SherpaVoice>? = null
    private var retainedBookId: Long? = null
    private var serviceBinder: ReadAloudService.LocalBinder? = null
    private var serviceConnection: ServiceConnection? = null
    private var collectors: Job? = null
    private var startJob: Job? = null
    private var sleepJob: Job? = null
    private var highlightFallback: Job? = null
    private var pendingLocator: Locator? = null
    private var hasStartedSpeaking = false
    /** True desde que se pidió reproducir: antes de eso, "sin reproducir" significa "preparando", no "en pausa". */
    private var playRequested = false
    private var resumeFrom: Locator? = null

    // El foco de audio y el "audio becoming noisy" los gestiona el adaptador Media3 de Readium
    // (TtsSessionAdapter trae su propio AudioFocusManager). Pedirlo también desde acá hacía que
    // nuestro listener recibiera "pérdida de foco" al arrancar y pausara la lectura.

    private val wakeLock: PowerManager.WakeLock =
        (application.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoxLibris:tts")
            .apply { setReferenceCounted(false) }

    init {
        // Si se pidió escuchar sin voces instaladas, arrancamos solos cuando terminen de instalarse.
        scope.launch {
            models.state.collect { state ->
                val bookId = _activeBookId.value
                if (state is VoiceModelManager.State.Ready && _status.value == Status.NeedsModel && bookId != null) {
                    start(bookId, resumeFrom)
                }
            }
        }
    }

    fun isActiveFor(bookId: Long): Boolean = _activeBookId.value == bookId

    /** Empieza a leer `bookId` desde `from`. Si otro libro estaba sonando, lo detiene antes. */
    fun start(bookId: Long, from: Locator?, skipSentences: Int = 0) {
        if (_activeBookId.value == bookId && navigator != null) {
            if (from != null) {
                Log.d(TAG, "Mover la lectura a un punto elegido (saltar $skipSentences frases)")
                hasStartedSpeaking = false
                if (_status.value == Status.Playing) _status.value = Status.Preparing
                navigator?.go(from)
                repeat(skipSentences) { navigator?.skipToNextUtterance() }
            }
            play()
            return
        }
        if (_activeBookId.value != null && _activeBookId.value != bookId) stop()
        if (startJob?.isActive == true) return

        val session = readerRepository[bookId] ?: run {
            _status.value = Status.Error("El libro no está abierto")
            return
        }
        _activeBookId.value = bookId
        _activeTitle.value = session.title
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
                listener = this@ReadAloudManager,
                initialLocator = from,
                initialPreferences = ttsPreferences.preferences.value,
            ).getOrElse {
                _status.value = Status.Error(it.message)
                return@launch
            }
            readerRepository.retain(bookId)
            retainedBookId = bookId
            navigator = created
            hasStartedSpeaking = false
            playRequested = false
            try {
                bindService()
                serviceBinder?.openSession(bookId, created.asMedia3Player())
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo publicar la sesión multimedia; se lee sin notificación", e)
            }
            observe(created, bookId)
            repeat(skipSentences) { created.skipToNextUtterance() }
            playRequested = true
            created.play()
            Log.i(TAG, "Lectura iniciada: libro $bookId" + if (skipSentences > 0) " (saltando $skipSentences frases)" else "")
        }
    }

    fun play() {
        val nav = navigator ?: return
        playRequested = true
        hasStartedSpeaking = false
        nav.play()
    }

    fun pause() {
        Log.d(TAG, "pause() pedido desde:", Throwable())
        navigator?.pause()
        provider.activeEngine?.interrupt()
    }

    fun togglePlayPause() {
        when (_status.value) {
            Status.Playing, Status.Preparing -> pause()
            Status.Paused -> play()
            else -> Unit
        }
    }

    fun next() {
        Log.d(TAG, "Frase siguiente")
        hasStartedSpeaking = false
        _status.value = Status.Preparing.takeIf { _status.value == Status.Playing } ?: _status.value
        navigator?.skipToNextUtterance()
    }

    fun previous() {
        Log.d(TAG, "Frase anterior")
        hasStartedSpeaking = false
        _status.value = Status.Preparing.takeIf { _status.value == Status.Playing } ?: _status.value
        navigator?.skipToPreviousUtterance()
    }

    fun downloadVoices() = models.install()

    /** Pausa la lectura pasados `minutes`; null cancela el temporizador. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _sleepTimerEndsAt.value = null
            return
        }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimerEndsAt.value = endsAt
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            _sleepTimerEndsAt.value = null
            pause()
        }
    }

    fun stop() {
        startJob?.cancel()
        collectors?.cancel()
        highlightFallback?.cancel()
        sleepJob?.cancel()
        _sleepTimerEndsAt.value = null
        navigator?.let { nav ->
            nav.pause()
            provider.activeEngine?.interrupt()
            nav.close()
        }
        navigator = null
        unbindService()
        if (wakeLock.isHeld) wakeLock.release()
        retainedBookId?.let { readerRepository.release(it) }
        retainedBookId = null
        _highlight.value = null
        pendingLocator = null
        hasStartedSpeaking = false
        playRequested = false
        _activeBookId.value = null
        _activeTitle.value = null
        _status.value = Status.Idle
        Log.i(TAG, "Lectura detenida")
    }

    override fun onStopRequested() = stop()

    private fun observe(nav: TtsNavigator<SherpaTtsSettings, SherpaTtsPreferences, SherpaTtsError, SherpaVoice>, bookId: Long) {
        collectors = scope.launch {
            launch {
                nav.playback.collect { playback ->
                    Log.d(TAG, "Playback de Readium: state=${playback.state} playWhenReady=${playback.playWhenReady} index=${playback.index}")
                    val status = when (val state = playback.state) {
                        is TtsNavigator.State.Failure -> Status.Error(state.error.message)
                        TtsNavigator.State.Ended -> Status.Ended
                        else -> when {
                            !playback.playWhenReady && playRequested -> Status.Paused
                            hasStartedSpeaking && playback.playWhenReady -> Status.Playing
                            else -> Status.Preparing
                        }
                    }
                    _status.value = status
                    updateWakeLock(status == Status.Playing || status == Status.Preparing)
                    if (status == Status.Ended) {
                        Log.i(TAG, "Fin del libro")
                        stop()
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
                nav.location.sample(PROGRESS_SAVE_MS).collect { location ->
                    bookRepository.saveProgression(bookId, location.utteranceLocator.toJSON().toString())
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
            launch {
                ttsPreferences.preferences.collect { prefs ->
                    Log.d(TAG, "Preferencias de voz: voz=${prefs.voiceId} velocidad=${prefs.speed} calidad=${prefs.quality}")
                    hasStartedSpeaking = false
                    if (_status.value == Status.Playing) _status.value = Status.Preparing
                    nav.submitPreferences(prefs)
                }
            }
        }
    }

    private fun applyPendingHighlight() {
        highlightFallback?.cancel()
        pendingLocator?.let { _highlight.value = it }
    }

    private fun updateWakeLock(active: Boolean) {
        if (active) {
            if (!wakeLock.isHeld) wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        } else if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    private suspend fun bindService() {
        if (serviceBinder != null) return
        val intent = ReadAloudService.intent(application)
        application.startService(intent)
        val binder = suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                    if (continuation.isActive) continuation.resume(service as ReadAloudService.LocalBinder)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    serviceBinder = null
                }

                override fun onNullBinding(name: ComponentName?) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("El servicio no devolvió binder"))
                }
            }
            serviceConnection = connection
            if (!application.bindService(intent, connection, 0)) {
                continuation.resumeWithException(IllegalStateException("bindService devolvió false"))
            }
        }
        serviceBinder = binder
    }

    private fun unbindService() {
        serviceBinder?.closeSession()
        serviceConnection?.let { runCatching { application.unbindService(it) } }
        serviceConnection = null
        serviceBinder = null
        runCatching { application.stopService(ReadAloudService.intent(application)) }
    }

    private companion object {
        const val TAG = "VoxLibris.TTS"
        /** Mayor que el adelanto máximo con que el motor pide la frase siguiente (TtsMath.leadSeconds). */
        const val HIGHLIGHT_FALLBACK_MS = 6500L
        const val PROGRESS_SAVE_MS = 3000L
        const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L
    }
}
