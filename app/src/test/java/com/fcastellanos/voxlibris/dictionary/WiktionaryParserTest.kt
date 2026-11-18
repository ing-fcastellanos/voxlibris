package com.fcastellanos.voxlibris.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WiktionaryParserTest {

    private val casa = """
        {{desambiguación|Casa|casá}}

        == {{lengua|es}} ==
        {{pron-graf|1audio1=Es-am-lat-casa.ogg}}

        === Etimología 1 ===
        {{etimología|la|casa|choza}}, {{etim|incierta}}.
        [[Archivo:PeraltaAdobe-cropped.jpg|thumb|[1]]]

        ==== {{sustantivo femenino|es}} ====
        {{es.sust}}
        ;1 {{csem|vivienda}}: {{plm|edificación}} destinada a [[vivienda]].

        ;2: {{plm|domicilio}}.
        {{uso|generalmente con preposición y sin artículo}}
        {{sinónimo|domicilio|hogar|lar|morada|residencia|vivienda}}

        ;3: {{plm|piso}}, departamento o local que tiene las dependencias mínimas para que lo habite una [[familia]].

        ;5: {{plm|descendencia}} o [[linaje]] que tiene un mismo apellido.<ref name="dlc1914">{{DLC1914|211}}</ref>

        ;8 {{csem|astrología}}: Cada una de las 12 divisiones de la esfera celeste.<ref name="dlc1914" />

        ==== Locuciones ====
        * [[casa de citas]]

        === Forma verbal ===
        ;1: {{forma verbo|casar|p=3s|t=presente|m=indicativo}}.

        == {{lengua|it}} ==
        === {{sustantivo femenino|it}} ===
        ;1: {{plm|casa}}.
    """.trimIndent()

    @Test
    fun `toma solo la sección en español, con categorías y acepciones limpias`() {
        val entry = WiktionaryParser.parse("casa", casa)!!

        assertEquals(listOf("Sustantivo femenino", "Forma verbal"), entry.sections.map { it.partOfSpeech })
        val senses = entry.sections[0].senses
        assertEquals(5, senses.size)
        assertEquals("vivienda", senses[0].label)
        assertEquals("Edificación destinada a vivienda.", senses[0].text)
        assertEquals("Domicilio.", senses[1].text)
        assertEquals("Piso, departamento o local que tiene las dependencias mínimas para que lo habite una familia.", senses[2].text)
        assertEquals("Descendencia o linaje que tiene un mismo apellido.", senses[3].text)
        assertEquals("astrología", senses[4].label)
        assertEquals("Cada una de las 12 divisiones de la esfera celeste.", senses[4].text)
        assertNull(senses[0].formOf)
        assertFalse(entry.isOnlyForms)
    }

    @Test
    fun `una conjugación remite a su verbo`() {
        val wikitext = """
            == {{lengua|es}} ==
            {{pron-graf}}

            === Forma verbal ===

            ;1: {{forma verbo|estimular|p=3s|t=presente|m=indicativo}}.

            ;2: {{forma verbo|estimular|2s|imperativo}}.

            == Referencias y notas ==
            <references />
        """.trimIndent()

        val entry = WiktionaryParser.parse("estimula", wikitext)!!

        assertEquals(listOf("Forma verbal"), entry.sections.map { it.partOfSpeech })
        assertEquals(2, entry.sections[0].senses.size)
        assertEquals(FormOf("estimular", FormOf.Kind.VERB), entry.sections[0].senses[0].formOf)
        assertEquals("", entry.sections[0].senses[0].text)
        assertEquals(listOf("estimular"), entry.lemmas)
        assertTrue(entry.isOnlyForms)
    }

    @Test
    fun `un plural remite a su adjetivo aunque el lema no sea el primer parámetro`() {
        val wikitext = """
            == {{lengua|es}} ==
            === Forma adjetiva ===
            ;1: {{forma adjetivo|leng=es|inmejorable|género=|número=plural}}.
        """.trimIndent()

        val entry = WiktionaryParser.parse("inmejorables", wikitext)!!

        assertEquals(FormOf("inmejorable", FormOf.Kind.ADJECTIVE), entry.sections[0].senses[0].formOf)
        assertTrue(entry.isOnlyForms)
    }

    @Test
    fun `sin sección en español no hay entrada`() {
        assertNull(WiktionaryParser.parse("house", "== {{lengua|en}} ==\n=== {{sustantivo|en}} ===\n;1: Casa."))
        assertNull(WiktionaryParser.parse("x", "== {{lengua|es}} ==\n=== Etimología ===\nNada."))
    }

    @Test
    fun `limpia plantillas anidadas, enlaces y referencias`() {
        assertEquals("Perteneciente a la casa o relativo a ella.", WiktionaryParser.clean("{{plm|perteneciente}} a la [[casa]] o {{l|es|relativo}} a ella.<ref>DRAE</ref>"))
        assertEquals("Nombre de un lugar.", WiktionaryParser.clean("'''Nombre''' de un [[lugar geográfico|lugar]]."))
    }
}
