package com.fcastellanos.voxlibris.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookStyleTest {

    @Test
    fun sameTitleAlwaysGetsTheSameStyle() {
        assertEquals(bookStyleFor("Niebla"), bookStyleFor("Niebla"))
        assertEquals(bookStyleFor("Niebla"), bookStyleFor("  niebla "))
    }

    @Test
    fun differentTitlesSpreadAcrossColorsAndShapes() {
        val titles = (1..60).map { "Libro de prueba número $it" }
        val styles = titles.map(::bookStyleFor)
        assertTrue("Pocos colores distintos", styles.map { it.tint }.toSet().size >= 8)
        assertTrue("Pocas formas distintas", styles.map { it.shape }.toSet().size >= 6)
    }

    @Test
    fun voicesKeepTheColorsOfTheDesignSystem() {
        // F1..F5 son los ids 0..4 y M1..M5 los 5..9.
        val expected = listOf(
            BookTint.Mint, BookTint.Pink, BookTint.Mustard, BookTint.Lilac, BookTint.Cobalt,
            BookTint.Tomato, BookTint.Night, BookTint.Butter, BookTint.Jade, BookTint.Violet,
        )
        assertEquals(expected, (0..9).map(::voiceTint))
        assertEquals(BookTint.Mint, voiceTint(42))
    }

    @Test
    fun speedUsesDecimalComma() {
        assertEquals("1×", formatSpeed(1.0))
        assertEquals("1,2×", formatSpeed(1.2))
        assertEquals("0,75×", formatSpeed(0.75))
    }
}
