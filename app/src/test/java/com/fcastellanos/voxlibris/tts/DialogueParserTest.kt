package com.fcastellanos.voxlibris.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueParserTest {

    /** Devuelve el texto que dice el personaje, con los tramos separados por «|». */
    private fun spoken(text: String, style: DialogueStyle = DialogueStyle.DASHES): String =
        DialogueParser.parse(text, style).filter { it.dialogue }.joinToString("|") { text.substring(it.start, it.end).trim() }

    private fun narrated(text: String, style: DialogueStyle = DialogueStyle.DASHES): String =
        DialogueParser.parse(text, style)
            .filter { !it.dialogue }
            .map { text.substring(it.start, it.end).trim() }
            .filter { part -> part.any { it.isLetterOrDigit() } }
            .joinToString("|")

    @Test
    fun `paragraph without dash is all narration`() {
        assertEquals("", spoken("Rin parpadeó."))
        assertEquals("Rin parpadeó.", narrated("Rin parpadeó."))
    }

    @Test
    fun `plain dialogue line belongs to the character`() {
        assertEquals("¿Cómo?", spoken("—¿Cómo?"))
        assertEquals("", narrated("—¿Cómo?"))
    }

    @Test
    fun `en dash also opens dialogue`() {
        assertEquals("Quítate la ropa.", spoken("–Quítate la ropa."))
    }

    @Test
    fun `inciso in the middle goes to the narrator with its closing punctuation`() {
        val text = "—No sé —dijo Juan—. Tal vez mañana."
        assertEquals("No sé|Tal vez mañana.", spoken(text))
        assertEquals("—dijo Juan—.", narrated(text))
    }

    @Test
    fun `inciso without closing dash runs to the end of the paragraph`() {
        val text = "—Póntelo —le dijo. Rin obedeció."
        assertEquals("Póntelo", spoken(text))
        assertEquals("—le dijo. Rin obedeció.", narrated(text))
    }

    @Test
    fun `action beat between two speeches`() {
        val text = "—Sí. —La supervisora le indicó un banco—. El año pasado pillamos a doce. Abre la boca."
        assertEquals("Sí.|El año pasado pillamos a doce. Abre la boca.", spoken(text))
        assertEquals("—La supervisora le indicó un banco—.", narrated(text))
    }

    @Test
    fun `dash inside a number range does not change the speaker`() {
        assertEquals("Fue en 1914–1918, creo.", spoken("—Fue en 1914–1918, creo."))
    }

    @Test
    fun `continuation paragraph keeps the character speaking`() {
        val text = "»Sigue dándole más. —La tía Fang ladeó la cabeza—. ¿Te dolerá tanto?"
        assertEquals("Sigue dándole más.|¿Te dolerá tanto?", spoken(text))
    }

    @Test
    fun `quotes are narration in a dash book`() {
        assertEquals("", spoken("Rin inspiró. «Concéntrate, que falta poco»."))
    }

    @Test
    fun `quotes are dialogue in a quote book`() {
        val text = "“I don't know,” said John. “Maybe tomorrow.”"
        assertEquals("I don't know,|Maybe tomorrow.", spoken(text, DialogueStyle.QUOTES))
        // Las comillas quedan del lado del narrador: no suenan.
        assertEquals("” said John. “", narrated(text, DialogueStyle.QUOTES))
    }

    @Test
    fun `a short quoted word is not dialogue`() {
        assertEquals("", spoken("He was the so-called “expert” of the town.", DialogueStyle.QUOTES))
    }

    @Test
    fun `kind of paragraph`() {
        assertEquals(DialogueParser.Kind.DASH, DialogueParser.kindOf("—Hola."))
        assertEquals(DialogueParser.Kind.QUOTED, DialogueParser.kindOf("“Hello there, friend,” she said."))
        assertEquals(DialogueParser.Kind.PLAIN, DialogueParser.kindOf("Era de noche."))
    }

    @Test
    fun `voiced pieces keep offsets into the sentence`() {
        val text = "—No sé —dijo Juan—."
        val pieces = DialogueParser.splitVoiced(text, DialogueParser.parse(text, DialogueStyle.DASHES))
        assertEquals(listOf(true, false), pieces.map { it.dialogue })
        assertEquals("No sé", pieces[0].piece.text)
        assertEquals(text.indexOf("No sé"), pieces[0].piece.offset)
        assertTrue(text.startsWith(pieces[1].piece.text, pieces[1].piece.offset))
        assertTrue(pieces[1].piece.text.contains("dijo Juan"))
    }

    @Test
    fun `registry knows a sentence continues a speech`() {
        val paragraph = "—No sé —dijo Juan—. Tal vez mañana."
        val registry = DialogueRegistry()
        val cut = paragraph.indexOf("Tal") - 1
        registry.register(paragraph, listOf(0..cut, cut + 1 until paragraph.length))
        val second = registry.spansFor("Tal vez mañana.")
        assertEquals(listOf(DialogueParser.Span(0, 15, true)), second)
        // La misma frase, en un párrafo de narración, es del narrador.
        registry.register("Lo pensó. Tal vez mañana.", listOf(0..9, 10..24))
        assertEquals(listOf(DialogueParser.Span(0, 15, false)), registry.spansFor("Tal vez mañana."))
    }

    @Test
    fun `registry falls back to parsing the sentence alone`() {
        val spans = DialogueRegistry().spansFor(" —¿Cómo?")
        assertEquals("¿Cómo?", spans.filter { it.dialogue }.joinToString { " —¿Cómo?".substring(it.start, it.end) })
    }
}
