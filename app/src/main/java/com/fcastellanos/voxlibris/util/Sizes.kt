package com.fcastellanos.voxlibris.util

import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Tamaños de archivos y carpetas, y su formato en megabytes para la pantalla de almacenamiento. */
object Sizes {

    private const val MEGABYTE = 1_048_576.0

    /** Megabytes en formato local: menos de 10 MB con un decimal («0,4»), el resto entero («171»). */
    fun megabytes(bytes: Long): String {
        val mb = bytes / MEGABYTE
        val symbols = DecimalFormatSymbols(Locale.forLanguageTag("es"))
        return if (mb < 10.0) DecimalFormat("0.0", symbols).format(mb) else DecimalFormat("#,##0", symbols).format(mb)
    }

    /** Bytes que ocupa un archivo o, recursivamente, una carpeta. Lo que no existe pesa 0. */
    fun of(file: File): Long = when {
        !file.exists() -> 0L
        file.isDirectory -> file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        else -> file.length()
    }

    fun of(files: Iterable<File>): Long = files.sumOf { of(it) }
}
