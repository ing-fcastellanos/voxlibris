package com.fcastellanos.voxlibris.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsMathTest {

    @Test
    fun `trims trailing silence but keeps a short tail`() {
        val sr = 1000
        val samples = FloatArray(3000) { if (it < 1000) 0.5f else 0f }
        val trimmed = TtsMath.trimTrailingSilence(samples, sr, keepMs = 100)
        assertEquals(1100, trimmed.size)
    }

    @Test
    fun `does not touch audio without trailing silence`() {
        val samples = FloatArray(500) { 0.3f }
        assertTrue(TtsMath.trimTrailingSilence(samples, 1000) === samples)
    }

    @Test
    fun `word range follows playback fraction`() {
        val text = "Hola mundo cruel"
        assertEquals(0 until 4, TtsMath.wordRangeAt(text, 0.0))
        assertEquals(5 until 10, TtsMath.wordRangeAt(text, 0.4))
        assertEquals(11 until 16, TtsMath.wordRangeAt(text, 0.99))
        assertNull(TtsMath.wordRangeAt("   ", 0.5))
    }

    @Test
    fun `lead grows with slower devices within bounds`() {
        assertEquals(1.1, TtsMath.leadSeconds(0.1), 1e-9)
        assertEquals(2.9, TtsMath.leadSeconds(0.4), 1e-9)
        assertEquals(5.3, TtsMath.leadSeconds(0.8), 1e-9)
        assertEquals(6.0, TtsMath.leadSeconds(2.0), 1e-9)
    }

    @Test
    fun `auto quality picks fast steps on slow phones`() {
        assertEquals(8, TtsMath.numSteps(SherpaTtsQuality.AUTO, 0.2))
        assertEquals(4, TtsMath.numSteps(SherpaTtsQuality.AUTO, 0.8))
        assertEquals(8, TtsMath.numSteps(SherpaTtsQuality.AUTO, null))
        assertEquals(4, TtsMath.numSteps(SherpaTtsQuality.FAST, 0.1))
        assertEquals(8, TtsMath.numSteps(SherpaTtsQuality.HIGH, 2.0))
    }

    @Test
    fun `rtf estimator smooths samples`() {
        val e = TtsMath.RtfEstimator(0.5)
        e.update(synthSeconds = 1.0, audioSeconds = 10.0) // 0.1
        assertEquals(0.38, e.value, 1e-9)
        e.update(synthSeconds = 0.0, audioSeconds = 0.01) // ignorado
        assertEquals(0.38, e.value, 1e-9)
    }

    @Test
    fun `language codes are reduced to what the model accepts`() {
        assertEquals("es", SupertonicModel.languageCode(org.readium.r2.shared.util.Language("es-AR")))
        assertEquals("en", SupertonicModel.languageCode(org.readium.r2.shared.util.Language("en_US")))
        assertNull(SupertonicModel.languageCode(org.readium.r2.shared.util.Language("eu")))
        assertNull(SupertonicModel.languageCode(null))
    }

    @Test
    fun `short text stays whole`() {
        val pieces = TtsMath.splitForSynthesis("Hola, mundo.", 150)
        assertEquals(listOf(TtsMath.Piece("Hola, mundo.", 0)), pieces)
    }

    @Test
    fun `long text splits only at punctuation with correct offsets`() {
        val text = "Ramsa había reclutado a Rin para que lo ayudase, y fortificar los callejones traseros era lo único que importaba; el embarcadero esperaba. Nadie dijo nada más."
        val pieces = TtsMath.splitForSynthesis(text, softMax = 60, hardMax = 100, firstSoftMax = 60)
        assertTrue(pieces.size >= 3)
        for (piece in pieces) {
            assertTrue(piece.text.length <= 100)
            assertEquals(piece.text, text.substring(piece.offset, piece.offset + piece.text.length))
        }
        // Todos los cortes caen tras puntuación, nunca a mitad de una cláusula.
        for (piece in pieces.dropLast(1)) assertTrue(piece.text, piece.text.last() in ",;:.!?")
        assertTrue(pieces.last().text.endsWith("Nadie dijo nada más."))
    }

    @Test
    fun `a sentence without pauses is kept whole up to the hard limit`() {
        val text = "una " + "palabra ".repeat(20) + "final" // 169 caracteres sin puntuación
        assertEquals(1, TtsMath.splitForSynthesis(text, softMax = 60, hardMax = 200).size)
        val long = "una " + "palabra ".repeat(60) + "final"
        val pieces = TtsMath.splitForSynthesis(long, softMax = 50, hardMax = 80)
        assertTrue(pieces.all { it.text.isNotBlank() && it.text.length <= 80 })
        assertEquals(emptyList<TtsMath.Piece>(), TtsMath.splitForSynthesis("   ", 50))
    }

    @Test
    fun `trims leading silence and fades edges`() {
        val sr = 1000
        val samples = FloatArray(1000) { if (it in 300..600) 0.5f else 0f }
        val trimmed = TtsMath.trimLeadingSilence(samples, sr, keepMs = 10)
        assertEquals(1000 - 290, trimmed.size)
        val faded = TtsMath.fadeEdges(FloatArray(100) { 1f }, sr, fadeInMs = 10, fadeOutMs = 10)
        assertEquals(0f, faded[0], 1e-6f)
        assertEquals(1f, faded[50], 1e-6f)
        assertEquals(0f, faded[99], 1e-6f)
    }

    @Test
    fun `word range by absolute index`() {
        assertEquals(5 until 10, TtsMath.wordRangeAtIndex("Hola mundo cruel", 7))
        assertEquals(0 until 4, TtsMath.wordRangeAtIndex("Hola mundo cruel", -3))
    }
}
