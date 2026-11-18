package com.fcastellanos.voxlibris.dictionary

object DictionaryWords {
    private val WORD = Regex("^\\p{L}[\\p{L}\\p{M}'’-]*$")

    /** La palabra seleccionada, sin signos alrededor, o nula si la selección tiene más de una palabra o no es una. */
    fun singleWord(selection: String?): String? {
        val word = selection?.trim()?.trim { !it.isLetterOrDigit() } ?: return null
        if (word.length !in 2..40) return null
        return word.takeIf { WORD.matches(it) }
    }
}
