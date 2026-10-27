package com.fcastellanos.voxlibris.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Las parejas de texto y fondo que define el Sistema de diseño VoxLibris pasan 4,5:1 en los dos
 * temas. Si un cambio en design/tokens.json rompe alguna, esta prueba lo avisa.
 */
class VoxColorsContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertReadable(theme: String, pair: String, text: Color, ground: Color) {
        val r = ratio(text, ground)
        assertTrue("Tema $theme, $pair: contraste %.2f:1, menor que 4,5:1".format(r), r >= 4.5)
    }

    @Test
    fun textPairsAreReadableInBothThemes() {
        for ((theme, c) in listOf("claro" to LightVoxColors, "oscuro" to DarkVoxColors)) {
            val grounds = mapOf("ground" to c.ground, "surface" to c.surface, "surface-muted" to c.surfaceMuted, "page" to c.page)
            for ((name, ground) in grounds) {
                assertReadable(theme, "ink sobre $name", c.ink, ground)
                assertReadable(theme, "ink-muted sobre $name", c.inkMuted, ground)
            }
            assertReadable(theme, "on-fill-strong sobre fill-strong", c.onFillStrong, c.fillStrong)
            assertReadable(theme, "on-fill-strong sobre fill-strong-raised", c.onFillStrong, c.fillStrongRaised)
            assertReadable(theme, "on-accent sobre accent", c.onAccent, c.accent)
            assertReadable(theme, "on-danger sobre danger", c.onDanger, c.danger)
            assertReadable(theme, "danger sobre surface", c.danger, c.surface)
            assertReadable(theme, "ink sobre accent-soft", c.ink, c.accentSoft)
            val brightFills = mapOf(
                "tomato" to c.tomato, "tomato-track" to c.tomatoTrack, "mustard" to c.mustard, "butter" to c.butter,
                "mint" to c.mint, "mint-deep" to c.mintDeep, "pink" to c.pink, "violet-light" to c.violetLight,
            )
            for ((name, fill) in brightFills) assertReadable(theme, "on-color sobre $name", c.onColor, fill)
            for ((name, fill) in mapOf("cobalt" to c.cobalt, "violet" to c.violet, "night" to c.night)) {
                assertReadable(theme, "on-deep sobre $name", c.onDeep, fill)
            }
        }
    }
}
