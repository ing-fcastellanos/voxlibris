package com.fcastellanos.voxlibris.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalVoxColors = staticCompositionLocalOf { LightVoxColors }
private val LocalVoxShadows = staticCompositionLocalOf { LightVoxShadows }
private val LocalVoxTypography = staticCompositionLocalOf { VoxDefaultTypography }

/**
 * Tema de VoxLibris, armado con el Sistema de diseño VoxLibris (ver CLAUDE.md).
 *
 * Expone los tokens propios en [VoxLibrisTheme] y además arma el esquema de Material 3, para
 * que los componentes de Material salgan con la paleta de la marca. No usa el color dinámico de
 * Android: reemplazaría la paleta por los colores del fondo de pantalla.
 */
@Composable
fun VoxLibrisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkVoxColors else LightVoxColors
    val shadows = if (darkTheme) DarkVoxShadows else LightVoxShadows
    // El primario inverso de Material es el acento del tema opuesto, que se lee sobre los avisos.
    val inverseAccent = if (darkTheme) LightVoxColors.accent else DarkVoxColors.accent
    CompositionLocalProvider(
        LocalVoxColors provides colors,
        LocalVoxShadows provides shadows,
        LocalVoxTypography provides VoxDefaultTypography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(darkTheme, inverseAccent),
            typography = VoxDefaultTypography.toMaterialTypography(),
            shapes = VoxShapes,
            content = content,
        )
    }
}

/** Acceso a los tokens del sistema desde cualquier composable dentro de [VoxLibrisTheme]. */
object VoxLibrisTheme {
    val colors: VoxColors
        @Composable @ReadOnlyComposable get() = LocalVoxColors.current

    val typography: VoxTypography
        @Composable @ReadOnlyComposable get() = LocalVoxTypography.current

    val shadows: VoxShadows
        @Composable @ReadOnlyComposable get() = LocalVoxShadows.current

    val dimens: VoxDimens
        get() = VoxDimens
}

private val VoxShapes = Shapes(
    extraSmall = RoundedCornerShape(VoxDimens.radiusS),
    small = RoundedCornerShape(VoxDimens.radiusM),
    medium = RoundedCornerShape(VoxDimens.radiusL),
    large = RoundedCornerShape(VoxDimens.radiusXl),
    extraLarge = RoundedCornerShape(VoxDimens.radius2xl),
)

/**
 * Traduce los tokens a los roles de Material 3.
 *
 * - `primary` es el negro de la marca (`fill-strong`): botones, switches, sliders y avance.
 * - `primaryContainer` es `accent`, que Material usa en el botón flotante de Agregar libro.
 * - `secondary` es `tomato`, el color de escuchar.
 * - `secondaryContainer` es `track` porque Material lo usa a la vez como riel de las barras de
 *   avance y como fondo seleccionado; si fuera oscuro, el avance no se vería.
 * - `background` es `ground` y las superficies (hojas, diálogos, menús) son `surface`.
 */
private fun VoxColors.toMaterialColorScheme(dark: Boolean, inversePrimary: Color): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = fillStrong,
        onPrimary = onFillStrong,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        inversePrimary = inversePrimary,
        secondary = tomato,
        onSecondary = onColor,
        secondaryContainer = track,
        onSecondaryContainer = ink,
        tertiary = mustard,
        onTertiary = onColor,
        tertiaryContainer = mustard,
        onTertiaryContainer = onColor,
        background = ground,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = track,
        onSurfaceVariant = inkMuted,
        surfaceTint = Color.Transparent,
        inverseSurface = fillStrong,
        inverseOnSurface = onFillStrong,
        error = danger,
        onError = onDanger,
        errorContainer = danger,
        onErrorContainer = onDanger,
        outline = inkMuted,
        outlineVariant = line,
        scrim = scrim.copy(alpha = 1f),
        surfaceBright = surface,
        surfaceDim = ground,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceMuted,
    )
}
