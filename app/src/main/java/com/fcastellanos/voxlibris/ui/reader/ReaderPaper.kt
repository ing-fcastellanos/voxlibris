@file:OptIn(ExperimentalReadiumApi::class)

package com.fcastellanos.voxlibris.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.fcastellanos.voxlibris.ui.theme.DarkVoxColors
import com.fcastellanos.voxlibris.ui.theme.LightVoxColors
import com.fcastellanos.voxlibris.ui.theme.VoxColors
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.navigator.preferences.Color as ReadiumColor

/**
 * Fondo de lectura del Sistema de diseño VoxLibris (Apariencia › Fondo). Cada uno fija el fondo y
 * el color del texto de Readium con los tokens del sistema, y el tema de la interfaz del lector.
 */
enum class ReaderPaper {
    /** `page` del tema claro: el fondo cálido de lectura, el predeterminado. */
    Warm,

    /** `surface` del tema claro. */
    White,

    /** `page` del tema oscuro. */
    Dark,
    ;

    val isDark: Boolean get() = this == Dark

    /** Tokens del tema que corresponde a este fondo. */
    val colors: VoxColors get() = if (isDark) DarkVoxColors else LightVoxColors

    val background: Color
        get() = when (this) {
            Warm -> LightVoxColors.page
            White -> LightVoxColors.surface
            Dark -> DarkVoxColors.page
        }

    val text: Color get() = colors.ink
}

/** Fondo elegido según las preferencias guardadas. Lo que no se reconoce, como el Sepia de antes, es Cálido. */
fun EpubPreferences.paper(): ReaderPaper = when {
    theme == Theme.DARK -> ReaderPaper.Dark
    backgroundColor?.int == LightVoxColors.surface.toArgb() -> ReaderPaper.White
    else -> ReaderPaper.Warm
}

/** Las mismas preferencias con el tema, el fondo y el color del texto de [paper]. */
fun EpubPreferences.withPaper(paper: ReaderPaper): EpubPreferences = copy(
    theme = if (paper.isDark) Theme.DARK else Theme.LIGHT,
    backgroundColor = ReadiumColor(paper.background.toArgb()),
    textColor = ReadiumColor(paper.text.toArgb()),
)

/**
 * Pinta las decoraciones con los tokens del fondo actual: los resaltados en `reading-highlight` y el
 * ícono de nota en `note-mark`. El color guardado en cada resaltado no se usa para dibujar.
 */
fun List<Decoration>.withPaperColors(paper: ReaderPaper): List<Decoration> = map { decoration ->
    when (val style = decoration.style) {
        is Decoration.Style.Highlight ->
            decoration.copy(style = Decoration.Style.Highlight(tint = paper.colors.readingHighlight.toArgb(), isActive = style.isActive))
        is NoteMarkStyle -> decoration.copy(style = NoteMarkStyle(tint = paper.colors.noteMark.toArgb()))
        else -> decoration
    }
}

/** Fuentes propias para el texto del libro, servidas desde `assets/fonts/`. */
object ReaderFonts {
    val Figtree = FontFamily("Figtree")
    val Literata = FontFamily("Literata")

    /** Registra Figtree y Literata en Readium, con sus versiones itálicas. */
    fun register(configuration: EpubNavigatorFragment.Configuration) {
        configuration.servedAssets = configuration.servedAssets + "fonts/.*"
        configuration.addFontFamilyDeclaration(Literata, listOf(FontFamily.SERIF)) {
            addFontFace {
                addSource("fonts/Literata.ttf")
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(200..900)
            }
            addFontFace {
                addSource("fonts/Literata-Italic.ttf")
                setFontStyle(FontStyle.ITALIC)
                setFontWeight(200..900)
            }
        }
        configuration.addFontFamilyDeclaration(Figtree, listOf(FontFamily.SANS_SERIF)) {
            addFontFace {
                addSource("fonts/Figtree.ttf")
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(300..900)
            }
            addFontFace {
                addSource("fonts/Figtree-Italic.ttf")
                setFontStyle(FontStyle.ITALIC)
                setFontWeight(300..900)
            }
        }
    }
}
