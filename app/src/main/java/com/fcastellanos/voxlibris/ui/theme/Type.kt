package com.fcastellanos.voxlibris.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import com.fcastellanos.voxlibris.R

/*
 * Fuentes del Sistema de diseño VoxLibris.
 *
 * Bricolage Grotesque (títulos) y Figtree (todo lo demás) son variables y van en res/font.
 * Literata, la alternativa de lectura, todavía no está empaquetada: el texto del libro lo
 * dibuja Readium en una vista web y la va a servir desde assets cuando se migre el lector.
 * Hasta entonces, el rol Serif usa la serif del sistema.
 */

private val Figtree = FontFamily(
    listOf(400, 600, 700, 800).map { weight -> Font(R.font.figtree, FontWeight(weight)) },
)

/**
 * Bricolage Grotesque trae por defecto el tamaño óptico 96, pensado para titulares enormes.
 * Se fija igual al tamaño del texto, como hace el navegador con `font-optical-sizing: auto`.
 */
private fun bricolage(weight: FontWeight, opticalSize: Float) = FontFamily(
    Font(
        R.font.bricolage_grotesque,
        weight,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight.weight),
            FontVariation.Setting("opsz", opticalSize.coerceIn(12f, 96f)),
        ),
    ),
)

/** Reparte el interlineado arriba y abajo del texto, como CSS. */
private val CssLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun VoxTextSpec.toTextStyle() = TextStyle(
    fontFamily = when (role) {
        VoxFontRole.Display -> bricolage(fontWeight, fontSize.value)
        VoxFontRole.Body -> Figtree
        VoxFontRole.Serif -> FontFamily.Serif
    },
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    lineHeightStyle = CssLineHeight,
)

/** Los estilos de texto del sistema con sus fuentes. */
val VoxDefaultTypography: VoxTypography = VoxTypography.build { it.toTextStyle() }

/** Asigna los estilos del sistema a los roles que usan los componentes de Material. */
internal fun VoxTypography.toMaterialTypography() = Typography(
    displayLarge = displayXl,
    displayMedium = displayXl,
    displaySmall = displayL,
    headlineLarge = displayXl,
    headlineMedium = displayL,
    headlineSmall = displayL,
    titleLarge = title,
    titleMedium = bodyStrong,
    titleSmall = label,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = label,
    labelMedium = caption,
    labelSmall = overline,
)
