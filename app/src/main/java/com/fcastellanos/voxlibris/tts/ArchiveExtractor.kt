package com.fcastellanos.voxlibris.tts

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/** Descomprime un .tar.bz2 dentro de una carpeta, descartando la carpeta raíz del paquete. */
object ArchiveExtractor {

    fun extractTarBz2(archive: File, into: File, stripRootDirectory: Boolean = true) {
        into.mkdirs()
        val root = into.canonicalPath
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(archive.inputStream()))).use { tar ->
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
    }
}
