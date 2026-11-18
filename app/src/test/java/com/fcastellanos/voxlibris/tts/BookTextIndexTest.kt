package com.fcastellanos.voxlibris.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookTextIndexTest {

    private val index = BookTextIndex(
        listOf(
            BookTextIndex.ResourceText("OEBPS/cap1.xhtml", intArrayOf(100, 200, 300, 400)),
            BookTextIndex.ResourceText("OEBPS/cap2.xhtml", intArrayOf(50, 50)),
        ),
    )

    @Test
    fun `cuenta desde el elemento actual hasta el final del libro`() {
        assertEquals(1100L, index.totalChars)
        assertEquals(1100L, index.charsRemaining("OEBPS/cap1.xhtml", 0.0))
        // progresión 0,5 de 4 elementos = elemento 2: quedan 300 + 400 + 100
        assertEquals(800L, index.charsRemaining("OEBPS/cap1.xhtml", 0.5))
        assertEquals(50L, index.charsRemaining("OEBPS/cap2.xhtml", 0.5)) // progresión 0,5 de 2 elementos = el segundo
        assertEquals(0L, index.charsRemaining("OEBPS/cap2.xhtml", 1.0))
        assertEquals(1100L, index.charsRemaining("OEBPS/cap1.xhtml", null))
    }

    @Test
    fun `un recurso desconocido no se puede ubicar`() {
        assertNull(index.charsRemaining("OEBPS/otro.xhtml", 0.0))
    }

    @Test
    fun `tiempo restante según el ritmo y la velocidad`() {
        // 1100 caracteres a 0,075 s por carácter son 82,5 s a 1×; a 1,5× son 55 s
        assertEquals(83L, TtsMath.remainingSeconds(1100, 0.075, 1.0))
        assertEquals(55L, TtsMath.remainingSeconds(1100, 0.075, 1.5))
        assertEquals(0L, TtsMath.remainingSeconds(0, 0.075, 1.0))
    }

    @Test
    fun `el ritmo se normaliza a velocidad 1 y promedia`() {
        // 5 s de audio para 100 caracteres sintetizados a 1,3× equivalen a 0,065 s por carácter a 1×
        SpeechRate.record(100, 5.0, 1.3)
        assertEquals(0.065, SpeechRate.secondsPerCharAtUnitSpeed, 1e-9)
        SpeechRate.record(100, 10.0, 1.0)
        assertEquals(0.065 * 0.9 + 0.1 * 0.1, SpeechRate.secondsPerCharAtUnitSpeed, 1e-9)
        SpeechRate.record(3, 10.0, 1.0) // muestras diminutas se ignoran
        assertEquals(0.065 * 0.9 + 0.1 * 0.1, SpeechRate.secondsPerCharAtUnitSpeed, 1e-9)
    }
}
