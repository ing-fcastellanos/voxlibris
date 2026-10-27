package com.fcastellanos.voxlibris.tts

import android.content.Context
import java.io.BufferedInputStream
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
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

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
                downloadAndInstall()
                _state.value = State.Ready(modelDir)
            } catch (e: CancellationException) {
                _state.value = State.NotInstalled
                throw e
            } catch (e: Exception) {
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

    private suspend fun downloadAndInstall() {
        modelsRoot.mkdirs()
        val archive = File(modelsRoot, "${SupertonicModel.ARCHIVE_NAME}.part")
        try {
            download(archive)
            _state.value = State.Installing
            val staging = File(modelsRoot, "${SupertonicModel.DIR_NAME}.tmp").also {
                it.deleteRecursively()
                it.mkdirs()
            }
            extract(archive, staging)
            if (!isInstalled(staging)) throw IOException("El paquete no contiene todos los archivos esperados")
            modelDir.deleteRecursively()
            if (!staging.renameTo(modelDir)) throw IOException("No se pudo mover el modelo a su carpeta")
        } finally {
            archive.delete()
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

    private fun extract(archive: File, into: File) {
        val root = into.canonicalPath
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(archive.inputStream()))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (entry.isDirectory) continue
                // El paquete trae todo dentro de una carpeta raíz; la descartamos.
                val relative = entry.name.substringAfter('/', missingDelimiterValue = "")
                if (relative.isEmpty()) continue
                val file = File(into, relative)
                if (!file.canonicalPath.startsWith(root)) throw IOException("Ruta inválida en el paquete: ${entry.name}")
                file.parentFile?.mkdirs()
                file.outputStream().use { tar.copyTo(it) }
            }
        }
    }

    private fun isInstalled(dir: File): Boolean = SupertonicModel.FILES.all { File(dir, it).length() > 0 }
}

private suspend fun currentCoroutineContext() = kotlin.coroutines.coroutineContext
