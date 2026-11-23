package com.fcastellanos.voxlibris.tts

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Descarga, verifica e instala el paquete de voces en el almacenamiento privado de la app.
 * El paquete pesa ~123 MB, así que no viaja dentro del APK.
 */
class VoiceModelManager(context: Context, private val scope: CoroutineScope) {

    sealed interface State {
        data object NotInstalled : State
        data class Downloading(val progress: Float) : State
        data object Installing : State
        data class Ready(val dir: File) : State
        data class Failed(val message: String) : State
    }

    private val modelsRoot = File(context.filesDir, "models")
    val modelDir = File(modelsRoot, SupertonicModel.DIR_NAME)

    private val _state = MutableStateFlow<State>(if (isInstalled(modelDir)) State.Ready(modelDir) else State.NotInstalled)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Avance de la descompresión mientras el estado es [State.Installing], de 0 a 1; nulo el resto del tiempo. */
    private val _installProgress = MutableStateFlow<Float?>(null)
    val installProgress: StateFlow<Float?> = _installProgress.asStateFlow()

    private var job: Job? = null

    fun readyModelDir(): File? = modelDir.takeIf { isInstalled(it) }

    fun install() {
        if (job?.isActive == true) return
        readyModelDir()?.let {
            _state.value = State.Ready(it)
            return
        }
        job = scope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Descargando voces desde ${SupertonicModel.URL}")
                downloadAndInstall()
                Log.i(TAG, "Voces instaladas en $modelDir")
                _state.value = State.Ready(modelDir)
            } catch (e: CancellationException) {
                _state.value = State.NotInstalled
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falló la instalación de voces", e)
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun delete() {
        cancel()
        modelDir.deleteRecursively()
        _state.value = State.NotInstalled
    }

    /** Borra lo instalado y vuelve a descargar el paquete que trae la app. */
    fun reinstall() {
        delete()
        install()
    }

    /** Nombre del paquete instalado, anotado al instalar; null en instalaciones anteriores a la marca. */
    fun installedVersion(): String? = File(modelDir, VERSION_MARKER).takeIf { it.isFile }?.readText()?.trim()

    /** Falso solo cuando la marca dice que lo instalado es un paquete distinto del que trae la app. */
    fun isCurrentVersion(): Boolean = installedVersion()?.let { it == SupertonicModel.ARCHIVE_NAME } ?: true

    /** Restos de descargas o instalaciones que no terminaron (`.part`, `.tmp`). */
    fun temporaryFiles(): List<File> =
        modelsRoot.listFiles { file -> file.name.endsWith(".part") || file.name.endsWith(".tmp") }?.toList().orEmpty()

    /** Borra los restos, salvo que haya una instalación en curso que los esté usando. */
    fun deleteTemporaryFiles() {
        if (job?.isActive == true) return
        temporaryFiles().forEach { it.deleteRecursively() }
    }

    private suspend fun downloadAndInstall() {
        modelsRoot.mkdirs()
        val archive = File(modelsRoot, "${SupertonicModel.ARCHIVE_NAME}.part")
        try {
            download(archive)
            _state.value = State.Installing
            _installProgress.value = 0f
            val staging = File(modelsRoot, "${SupertonicModel.DIR_NAME}.tmp").also {
                it.deleteRecursively()
                it.mkdirs()
            }
            ArchiveExtractor.extractTarBz2(archive, staging) { _installProgress.value = it }
            if (!isInstalled(staging)) throw IOException("El paquete no contiene todos los archivos esperados")
            modelDir.deleteRecursively()
            if (!staging.renameTo(modelDir)) throw IOException("No se pudo mover el modelo a su carpeta")
            File(modelDir, VERSION_MARKER).writeText(SupertonicModel.ARCHIVE_NAME)
        } finally {
            archive.delete()
            _installProgress.value = null
        }
    }

    private suspend fun download(target: File) {
        _state.value = State.Downloading(0f)
        val connection = (URL(SupertonicModel.URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: SupertonicModel.SIZE_BYTES
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var lastReported = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        val percent = (received * 100 / total).toInt()
                        if (percent != lastReported) {
                            lastReported = percent
                            _state.value = State.Downloading(received.toFloat() / total)
                        }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != SupertonicModel.SHA256) throw IOException("La descarga llegó corrupta (SHA-256 distinto)")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "VoxLibris.TTS"
        const val VERSION_MARKER = "installed.txt"
    }

    private fun isInstalled(dir: File): Boolean = SupertonicModel.FILES.all { File(dir, it).length() > 0 }
}

private suspend fun currentCoroutineContext() = kotlin.coroutines.coroutineContext
