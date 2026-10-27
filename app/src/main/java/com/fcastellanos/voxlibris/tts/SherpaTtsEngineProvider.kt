package com.fcastellanos.voxlibris.tts

import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.readium.navigator.media.tts.TtsEngine
import org.readium.navigator.media.tts.TtsEngineProvider
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.Try

/** Fabrica motores [SherpaTtsEngine] para el TtsNavigator de Readium. */
@OptIn(ExperimentalReadiumApi::class)
class SherpaTtsEngineProvider(
    private val runtime: SherpaTtsRuntime,
) : TtsEngineProvider<SherpaTtsSettings, SherpaTtsPreferences, SherpaTtsPreferencesEditor, SherpaTtsError, SherpaVoice> {

    private val _events = MutableSharedFlow<SherpaTtsEngine.Event>(extraBufferCapacity = 32)
    val events: SharedFlow<SherpaTtsEngine.Event> = _events.asSharedFlow()

    @Volatile
    var activeEngine: SherpaTtsEngine? = null
        private set

    override suspend fun createEngine(
        publication: Publication,
        initialPreferences: SherpaTtsPreferences,
    ): Try<TtsEngine<SherpaTtsSettings, SherpaTtsPreferences, SherpaTtsError, SherpaVoice>, Error> {
        val tts = runtime.acquire() ?: return Try.failure(SherpaTtsError.ModelMissing)
        val rtf = runCatching { runtime.calibratedRtf(tts) }.getOrNull()
        val engine = SherpaTtsEngine(runtime, tts, contentLanguage(publication), initialPreferences, rtf, _events)
        activeEngine = engine
        return Try.success(engine)
    }

    override fun createPreferencesEditor(publication: Publication, initialPreferences: SherpaTtsPreferences): SherpaTtsPreferencesEditor =
        SherpaTtsPreferencesEditor(initialPreferences, contentLanguage(publication))

    override fun createEmptyPreferences(): SherpaTtsPreferences = SherpaTtsPreferences()

    override fun getPlaybackParameters(settings: SherpaTtsSettings): PlaybackParameters =
        PlaybackParameters(settings.speed.toFloat(), 1f)

    override fun updatePlaybackParameters(previousPreferences: SherpaTtsPreferences, playbackParameters: PlaybackParameters): SherpaTtsPreferences =
        previousPreferences.copy(speed = playbackParameters.speed.toDouble())

    // El constructor público de PlaybackException está marcado como inestable en media3.
    @androidx.annotation.OptIn(UnstableApi::class)
    override fun mapEngineError(error: SherpaTtsError): PlaybackException =
        PlaybackException(error.message, null, PlaybackException.ERROR_CODE_UNSPECIFIED)

    private fun contentLanguage(publication: Publication): Language? =
        publication.metadata.languages.firstOrNull()?.let { Language(it) }
}
