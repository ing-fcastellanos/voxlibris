package com.fcastellanos.readapp.util

object FileNames {

    /** Convierte "mi_libro-v2.epub" en "mi libro v2". Devuelve null si no queda nada útil. */
    fun titleFromFileName(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        val base = fileName.substringAfterLast('/').substringBeforeLast('.')
        val cleaned = base
            .replace(Regex("[_\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.ifBlank { null }
    }
}
