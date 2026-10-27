package com.fcastellanos.voxlibris.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Colores de libro y de voz del sistema (`vx-tint--*`). */
enum class BookTint { Tomato, Mustard, Butter, Mint, Jade, Pink, Cobalt, Violet, Lilac, Night }

/** Formas de las portadas generadas. */
enum class CoverShape { Disc, Orb, HalfRound, Stripes, Diamond, Ring, Quarter }

/** Color y forma con que se dibuja un libro. */
@Immutable
data class BookStyle(val tint: BookTint, val shape: CoverShape)

/** Fondo, contenido (texto e íconos) y forma decorativa de un [BookTint]. */
@Immutable
data class TintColors(val container: Color, val content: Color, val shape: Color)

val BookTint.colors: TintColors
    @Composable @ReadOnlyComposable get() {
        val c = VoxLibrisTheme.colors
        return when (this) {
            BookTint.Tomato -> TintColors(c.tomato, c.onColor, c.tomatoTrack)
            BookTint.Mustard -> TintColors(c.mustard, c.onColor, c.butter)
            BookTint.Butter -> TintColors(c.butter, c.onColor, c.mustard)
            BookTint.Mint -> TintColors(c.mint, c.onColor, c.mintDeep)
            BookTint.Jade -> TintColors(c.mintDeep, c.onColor, c.mint)
            BookTint.Pink -> TintColors(c.pink, c.onColor, c.pinkDeep)
            BookTint.Cobalt -> TintColors(c.cobalt, c.onDeep, c.cobaltLight)
            BookTint.Violet -> TintColors(c.violet, c.onDeep, c.violetLight)
            BookTint.Lilac -> TintColors(c.violetLight, c.onColor, c.violet)
            BookTint.Night -> TintColors(c.night, c.mustard, c.tomato)
        }
    }

/**
 * Color y forma estables para un libro, calculados a partir de su título. El mismo título da
 * siempre el mismo resultado, así que no hace falta guardarlos en la base de datos.
 */
fun bookStyleFor(key: String): BookStyle {
    val hash = fnv1a(key.trim().lowercase()).toUInt()
    val tints = BookTint.entries
    val shapes = CoverShape.entries
    return BookStyle(
        tint = tints[(hash % tints.size.toUInt()).toInt()],
        shape = shapes[((hash shr 16) % shapes.size.toUInt()).toInt()],
    )
}

private fun fnv1a(text: String): Int {
    var hash = 0x811C9DC5.toInt()
    for (byte in text.encodeToByteArray()) {
        hash = hash xor (byte.toInt() and 0xFF)
        hash *= 0x01000193
    }
    return hash
}
