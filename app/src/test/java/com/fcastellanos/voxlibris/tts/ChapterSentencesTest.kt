package com.fcastellanos.voxlibris.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterSentencesTest {

    private val chapter = ChapterSentences(
        href = "cap1.xhtml",
        elements = listOf(
            ChapterSentences.ElementSentences(0.0, listOf("Capítulo primero.")),
            ChapterSentences.ElementSentences(0.25, listOf("En un lugar de la Mancha vivía un hidalgo.", "Una olla de algo más vaca que carnero.", "El resto della concluían sayo de velarte.")),
            ChapterSentences.ElementSentences(0.5, listOf("Tenía en su casa una ama.", "Frisaba la edad con los cincuenta años.")),
        ),
    )

    @Test
    fun `counts every sentence of the chapter`() {
        assertEquals(6, chapter.total)
    }

    @Test
    fun `finds the sentence by element progression and text`() {
        assertEquals(3, chapter.indexOf(0.25, "Una olla de algo más vaca que carnero."))
        assertEquals(6, chapter.indexOf(0.5, "Frisaba la edad con los cincuenta años."))
    }

    @Test
    fun `matches a sentence by its beginning when spacing differs`() {
        assertEquals(4, chapter.indexOf(0.25, "El  resto della\nconcluían sayo de velarte."))
    }

    @Test
    fun `unknown text counts as the first sentence of its element`() {
        assertEquals(5, chapter.indexOf(0.5, "Algo que no está."))
        assertEquals(2, chapter.indexOf(0.25, null))
    }

    @Test
    fun `progression between elements picks the previous element`() {
        assertEquals(2, chapter.indexOf(0.3, null))
        assertEquals(1, chapter.indexOf(null, "Capítulo primero."))
    }

    @Test
    fun `empty chapter has no index`() {
        assertNull(ChapterSentences("x", emptyList()).indexOf(0.0, "a"))
    }
}
