package com.fcastellanos.voxlibris.tts

import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/** Descomprime un .tar.bz2 dentro de una carpeta, descartando la carpeta raíz del paquete. */
object ArchiveExtractor {

    /** [onProgress] recibe la fracción leída del archivo comprimido, de 0 a 1, en pasos de un 1 %. */
    fun extractTarBz2(archive: File, into: File, stripRootDirectory: Boolean = true, onProgress: ((Float) -> Unit)? = null) {
        into.mkdirs()
        val root = into.canonicalPath
        val source: InputStream = onProgress?.let { CountingInputStream(archive.inputStream(), archive.length(), it) } ?: archive.inputStream()
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(source))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (entry.isDirectory) continue
                val relative = if (stripRootDirectory) entry.name.substringAfter('/', missingDelimiterValue = "") else entry.name
                if (relative.isEmpty()) continue
                val file = File(into, relative)
                if (!file.canonicalPath.startsWith(root + File.separator)) {
                    throw IOException("Ruta inválida en el paquete: ${entry.name}")
                }
                file.parentFile?.mkdirs()
                file.outputStream().use { tar.copyTo(it) }
            }
        }
        onProgress?.invoke(1f)
    }

    private class CountingInputStream(
        input: InputStream,
        total: Long,
        private val onProgress: (Float) -> Unit,
    ) : FilterInputStream(input) {
        private val total = total.coerceAtLeast(1)
        private var count = 0L
        private var lastPercent = -1

        override fun read(): Int = super.read().also { if (it >= 0) advance(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

        override fun skip(n: Long): Long = super.skip(n).also { advance(it) }

        private fun advance(bytes: Long) {
            count += bytes
            val percent = (count * 100 / total).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress((count.toFloat() / total).coerceIn(0f, 1f))
            }
        }
    }
}
