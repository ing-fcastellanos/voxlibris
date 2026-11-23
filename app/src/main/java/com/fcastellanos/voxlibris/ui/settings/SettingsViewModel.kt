package com.fcastellanos.voxlibris.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.data.LibraryStorage
import com.fcastellanos.voxlibris.di.AppContainer
import com.fcastellanos.voxlibris.reading.ReadingPreferencesStore
import com.fcastellanos.voxlibris.settings.AppPreferencesStore
import com.fcastellanos.voxlibris.settings.ThemeMode
import com.fcastellanos.voxlibris.tts.SherpaTtsDefaults
import com.fcastellanos.voxlibris.tts.SherpaTtsPreferences
import com.fcastellanos.voxlibris.tts.SherpaTtsQuality
import com.fcastellanos.voxlibris.tts.SherpaTtsRuntime
import com.fcastellanos.voxlibris.tts.TtsPreferencesStore
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.util.Sizes
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Ajustes y «Voces y almacenamiento»: las preferencias generales de voz, lectura y app, y lo que
 * ocupan las voces, los libros y los restos de descargas e importaciones.
 */
@OptIn(ExperimentalReadiumApi::class)
class SettingsViewModel(
    application: Application,
    private val ttsStore: TtsPreferencesStore,
    private val appPreferences: AppPreferencesStore,
    private val readingPreferences: ReadingPreferencesStore,
    private val models: VoiceModelManager,
    private val runtime: SherpaTtsRuntime,
    private val files: LibraryStorage,
    private val books: BookRepository,
) : ViewModel() {

    /** Lo que ocupa cada cosa, en bytes. Los temporales no cuentan en el total que se muestra. */
    data class StorageUsage(val voicesBytes: Long, val booksBytes: Long, val tempBytes: Long, val bookCount: Int) {
        val totalBytes: Long get() = voicesBytes + booksBytes
    }

    sealed interface Message {
        data object VoicesUpToDate : Message
        data object VoicesUpdateAvailable : Message
        data object TemporaryFilesCleared : Message
    }

    val ttsPreferences: StateFlow<SherpaTtsPreferences> = ttsStore.preferences
    val themeMode: StateFlow<ThemeMode> = appPreferences.themeMode
    val highlightSpokenSentence: StateFlow<Boolean> = appPreferences.highlightSpokenSentence
    val voicesCheckedAt: StateFlow<Long?> = appPreferences.voicesCheckedAt
    val reading: StateFlow<EpubPreferences> = readingPreferences.preferences
    val modelState: StateFlow<VoiceModelManager.State> = models.state

    val versionName: String =
        runCatching { application.packageManager.getPackageInfo(application.packageName, 0).versionName }.getOrNull() ?: "0"

    private val _storage = MutableStateFlow<StorageUsage?>(null)
    val storage: StateFlow<StorageUsage?> = _storage.asStateFlow()

    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = _messages.receiveAsFlow()

    fun setSpeed(speed: Double) {
        val rounded = ((speed * 10).roundToInt() / 10.0).coerceIn(SherpaTtsDefaults.SPEED_RANGE)
        ttsStore.update(ttsStore.preferences.value.copy(speed = rounded))
    }

    /** «Alta calidad» encendido fuerza 8 pasos; apagado vuelve a Auto, que mide el teléfono. */
    fun setHighQuality(enabled: Boolean) {
        ttsStore.update(ttsStore.preferences.value.copy(quality = if (enabled) SherpaTtsQuality.HIGH else SherpaTtsQuality.AUTO))
    }

    fun setTrimSilence(enabled: Boolean) = ttsStore.update(ttsStore.preferences.value.copy(trimSilence = enabled))

    fun setThemeMode(mode: ThemeMode) = appPreferences.setThemeMode(mode)

    fun setHighlightSpokenSentence(enabled: Boolean) = appPreferences.setHighlightSpokenSentence(enabled)

    fun updateReading(preferences: EpubPreferences) = readingPreferences.update(preferences)

    fun refreshStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = books.books.first().size
            _storage.value = StorageUsage(
                voicesBytes = Sizes.of(models.modelDir),
                booksBytes = Sizes.of(files.booksDir) + Sizes.of(files.coversDir),
                tempBytes = Sizes.of(files.importTempDir) + Sizes.of(models.temporaryFiles()),
                bookCount = count,
            )
        }
    }

    /** Compara el paquete instalado con el que trae la app y anota la revisión. */
    fun checkVoices() {
        appPreferences.markVoicesChecked()
        viewModelScope.launch {
            _messages.send(if (models.isCurrentVersion()) Message.VoicesUpToDate else Message.VoicesUpdateAvailable)
        }
    }

    fun updateVoices() {
        runtime.release()
        models.reinstall()
    }

    fun deleteVoices() {
        runtime.release()
        models.delete()
        refreshStorage()
    }

    fun clearTemporaryFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            files.importTempDir.listFiles()?.forEach { it.deleteRecursively() }
            models.deleteTemporaryFiles()
            refreshStorage()
            _messages.send(Message.TemporaryFilesCleared)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    container.application,
                    container.ttsPreferences,
                    container.appPreferences,
                    container.readingPreferences,
                    container.voiceModels,
                    container.ttsRuntime,
                    container.storage,
                    container.bookRepository,
                )
            }
        }
    }
}
