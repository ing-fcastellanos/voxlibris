package com.fcastellanos.voxlibris.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictionaryWordsTest {

    @Test
    fun `una palabra sola, con o sin signos alrededor, se busca`() {
        assertEquals("casa", DictionaryWords.singleWord("casa"))
        assertEquals("casa", DictionaryWords.singleWord(" «casa», "))
        assertEquals("Goethe", DictionaryWords.singleWord("Goethe."))
        assertEquals("veraneó", DictionaryWords.singleWord("¿veraneó?"))
        assertEquals("tira-y-afloje", DictionaryWords.singleWord("tira-y-afloje"))
    }

    @Test
    fun `varias palabras, números o nada no se buscan`() {
        assertNull(DictionaryWords.singleWord("dos palabras"))
        assertNull(DictionaryWords.singleWord("casa,\nárbol"))
        assertNull(DictionaryWords.singleWord("1914"))
        assertNull(DictionaryWords.singleWord("a"))
        assertNull(DictionaryWords.singleWord("  "))
        assertNull(DictionaryWords.singleWord(null))
    }
}
