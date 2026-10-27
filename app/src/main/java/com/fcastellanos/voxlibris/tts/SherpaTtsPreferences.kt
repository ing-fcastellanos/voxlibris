package com.fcastellanos.voxlibris.tts

import org.readium.navigator.media.tts.TtsEngine
import org.readium.r2.navigator.preferences.PreferencesEditor
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Language

@OptIn(ExperimentalReadiumApi::class)
data class SherpaVoice(
    val id: Int,
    val name: String,
    val isFemale: Boolean,
    override val language: Language = SherpaTtsDefaults.LANGUAGE,
) : TtsEngine.Voice

/** Calidad de síntesis: pasos de difusión de Supertonic. AUTO se decide midiendo el teléfono. */
enum class SherpaTtsQuality { AUTO, FAST, HIGH }

object SherpaTtsDefaults {
    val LANGUAGE: Language = Language("es")
    const val VOICE_ID = 0
    const val SPEED = 1.0
    val SPEED_RANGE = 0.6..1.8
    const val SPEED_STEP = 0.1
    const val STEPS_FAST = 4
    const val STEPS_HIGH = 8
    /** Por encima de este factor de tiempo real (medido con 8 pasos) se pasa a 4 pasos. */
    const val AUTO_RTF_THRESHOLD = 0.5
}

@OptIn(ExperimentalReadiumApi::class)
data class SherpaTtsPreferences(
    override val language: Language? = null,
    val voiceId: Int? = null,
    val speed: Double? = null,
    val quality: SherpaTtsQuality? = null,
) : TtsEngine.Preferences<SherpaTtsPreferences> {

    override fun plus(other: SherpaTtsPreferences): SherpaTtsPreferences = SherpaTtsPreferences(
        language = other.language ?: language,
        voiceId = other.voiceId ?: voiceId,
        speed = other.speed ?: speed,
        quality = other.quality ?: quality,
    )
}

@OptIn(ExperimentalReadiumApi::class)
data class SherpaTtsSettings(
    override val language: Language,
    override val overrideContentLanguage: Boolean,
    val voiceId: Int,
    val speed: Double,
    val quality: SherpaTtsQuality,
) : TtsEngine.Settings

fun SherpaTtsPreferences.resolve(contentLanguage: Language?): SherpaTtsSettings = SherpaTtsSettings(
    language = language ?: contentLanguage ?: SherpaTtsDefaults.LANGUAGE,
    overrideContentLanguage = language != null,
    voiceId = voiceId ?: SherpaTtsDefaults.VOICE_ID,
    speed = (speed ?: SherpaTtsDefaults.SPEED).coerceIn(SherpaTtsDefaults.SPEED_RANGE),
    quality = quality ?: SherpaTtsQuality.AUTO,
)

/** Editor mínimo: Readium solo exige `preferences` y `clear()`; la interfaz usa los setters. */
@OptIn(ExperimentalReadiumApi::class)
class SherpaTtsPreferencesEditor(
    initial: SherpaTtsPreferences,
    private val contentLanguage: Language?,
) : PreferencesEditor<SherpaTtsPreferences> {

    override var preferences: SherpaTtsPreferences = initial
        private set

    val settings: SherpaTtsSettings get() = preferences.resolve(contentLanguage)

    override fun clear() {
        preferences = SherpaTtsPreferences()
    }

    fun setVoice(id: Int) {
        preferences = preferences.copy(voiceId = id)
    }

    fun setSpeed(speed: Double) {
        preferences = preferences.copy(speed = speed.coerceIn(SherpaTtsDefaults.SPEED_RANGE))
    }

    fun setQuality(quality: SherpaTtsQuality) {
        preferences = preferences.copy(quality = quality)
    }
}

@OptIn(ExperimentalReadiumApi::class)
sealed class SherpaTtsError(
    override val message: String,
    override val cause: Error? = null,
) : TtsEngine.Error {
    data object ModelMissing : SherpaTtsError("Las voces no están descargadas")
    data class Synthesis(val detail: String) : SherpaTtsError("No se pudo sintetizar la voz: $detail")
    data class Audio(val detail: String) : SherpaTtsError("Error de audio: $detail")
}
