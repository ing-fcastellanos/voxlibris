// Archivo generado por tools/gen_tokens.py a partir de design/tokens.json. No lo edites a mano.
// Para cambiar un valor, cambialo en el Sistema de diseño VoxLibris, copiá su tokens.json a
// design/tokens.json y corré: python3 tools/gen_tokens.py

package com.fcastellanos.voxlibris.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colores del Sistema de diseño VoxLibris para un tema. */
@Immutable
data class VoxColors(
    /** Fondo de todas las pantallas. */
    val ground: Color,
    /** Tarjetas, hojas inferiores, menús, diálogos, buscador y chips sobre ground. */
    val surface: Color,
    /** Chips, pestañas y botones secundarios sobre surface. */
    val surfaceMuted: Color,
    /** Riel de las barras de avance, días pendientes de la racha y switch apagado. */
    val track: Color,
    /** Divisores de listas y agarradera de las hojas. Decorativo, nunca lleva texto. */
    val line: Color,
    /** Texto principal e íconos sobre ground, surface, surface-muted y page. */
    val ink: Color,
    /** Autor, avance, ayudas y frases ya leídas, sobre ground, surface, surface-muted y page. */
    val inkMuted: Color,
    /** Relleno de la barra de navegación, el reproductor flotante, los avisos y los chips o botones seleccionados. En oscuro se vuelve claro. */
    val fillStrong: Color,
    /** Botones dentro de fill-strong: la velocidad del reproductor flotante y la acción de un aviso. */
    val fillStrongRaised: Color,
    /** Texto e íconos sobre fill-strong y fill-strong-raised. */
    val onFillStrong: Color,
    /** Contorno de foco de 3 px, separado 2 px del control. */
    val focus: Color,
    /** Velo detrás de hojas inferiores y diálogos. */
    val scrim: Color,
    /** Fondo cálido del texto completo del libro. */
    val page: Color,
    /** Solo el botón Agregar libro y el día de hoy en la racha. */
    val accent: Color,
    /** Ícono y texto sobre accent. */
    val onAccent: Color,
    /** Fondo del texto que la persona selecciona en el lector; el texto encima sigue en ink. */
    val accentSoft: Color,
    /** Eliminar y errores. Como texto, sobre surface y surface-muted. */
    val danger: Color,
    /** Texto del botón Eliminar. */
    val onDanger: Color,
    /** Texto e íconos sobre tomato, tomato-track, mustard, butter, mint, mint-deep, pink y violet-light, en los dos temas. */
    val onColor: Color,
    /** Texto e íconos sobre cobalt, violet y night. */
    val onDeep: Color,
    /** Escuchar: botón de play, tarjeta del libro actual, fondo del reproductor y avance del audio. */
    val tomato: Color,
    /** Riel de avance sobre tomato y forma de las portadas tomato. */
    val tomatoTrack: Color,
    /** Arco del anillo de avance del libro. No es color de texto. */
    val tomatoDeep: Color,
    /** Riel del anillo de avance del libro. */
    val ringTrack: Color,
    /** Como fondo de texto, solo la frase que está sonando. También color de libro y de voz. */
    val mustard: Color,
    /** Color de libro y de voz; forma de las portadas mustard. */
    val butter: Color,
    /** Voz activa, indicador Siguiendo la voz, aviso de éxito y color de libro. */
    val mint: Color,
    /** Forma de las portadas mint y color de libro. */
    val mintDeep: Color,
    /** Color de libro y de voz. */
    val pink: Color,
    /** Forma de las portadas pink. */
    val pinkDeep: Color,
    /** Color de libro y de voz; texto encima en on-deep. */
    val cobalt: Color,
    /** Forma de las portadas cobalt. */
    val cobaltLight: Color,
    /** Color de libro y de voz; texto encima en on-deep. */
    val violet: Color,
    /** Color de libro y de voz y forma de las portadas violet; texto encima en on-color. */
    val violetLight: Color,
    /** Color de libro: portada oscura con título en mustard. No cambia con el tema. */
    val night: Color,
    /** Borde interior de las portadas night para que no se pierdan sobre el fondo oscuro. */
    val coverEdge: Color,
)

/** Tema Claro. */
val LightVoxColors = VoxColors(
    ground = Color(0xFFF4F3F8),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF4F3F8),
    track = Color(0xFFE2E0EA),
    line = Color(0xFFD9D7E2),
    ink = Color(0xFF17151F),
    inkMuted = Color(0xFF57536A),
    fillStrong = Color(0xFF17151F),
    fillStrongRaised = Color(0xFF2A2733),
    onFillStrong = Color(0xFFFFFFFF),
    focus = Color(0xFF17151F),
    scrim = Color(0x8C17151F),
    page = Color(0xFFFFF3EE),
    accent = Color(0xFF6D4AFF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFDCD3FF),
    danger = Color(0xFFC62D1F),
    onDanger = Color(0xFFFFFFFF),
    onColor = Color(0xFF17151F),
    onDeep = Color(0xFFFFFFFF),
    tomato = Color(0xFFFF6B4A),
    tomatoTrack = Color(0xFFFFB3A3),
    tomatoDeep = Color(0xFFE8492A),
    ringTrack = Color(0xFFFFD5C9),
    mustard = Color(0xFFFFC83D),
    butter = Color(0xFFFFE08A),
    mint = Color(0xFF3FD0A0),
    mintDeep = Color(0xFF2BB389),
    pink = Color(0xFFFF7AB6),
    pinkDeep = Color(0xFFFF4F9A),
    cobalt = Color(0xFF3A5BFF),
    cobaltLight = Color(0xFF6F86FF),
    violet = Color(0xFF6A3FE0),
    violetLight = Color(0xFF8F6BFF),
    night = Color(0xFF17151F),
    coverEdge = Color(0x0017151F),
)

/** Tema Oscuro. */
val DarkVoxColors = VoxColors(
    ground = Color(0xFF121118),
    surface = Color(0xFF1E1C26),
    surfaceMuted = Color(0xFF2A2733),
    track = Color(0xFF3A3646),
    line = Color(0xFF4A4658),
    ink = Color(0xFFF4F3F8),
    inkMuted = Color(0xFFB4B0C4),
    fillStrong = Color(0xFFF4F3F8),
    fillStrongRaised = Color(0xFFDDDBE6),
    onFillStrong = Color(0xFF121118),
    focus = Color(0xFFF4F3F8),
    scrim = Color(0x99000000),
    page = Color(0xFF1C1719),
    accent = Color(0xFF9D85FF),
    onAccent = Color(0xFF17151F),
    accentSoft = Color(0xFF3B2F73),
    danger = Color(0xFFFF8A7A),
    onDanger = Color(0xFF17151F),
    onColor = Color(0xFF17151F),
    onDeep = Color(0xFFFFFFFF),
    tomato = Color(0xFFFF6B4A),
    tomatoTrack = Color(0xFFFFB3A3),
    tomatoDeep = Color(0xFFFF8A70),
    ringTrack = Color(0xFF4A2A24),
    mustard = Color(0xFFFFC83D),
    butter = Color(0xFFFFE08A),
    mint = Color(0xFF3FD0A0),
    mintDeep = Color(0xFF2BB389),
    pink = Color(0xFFFF7AB6),
    pinkDeep = Color(0xFFFF4F9A),
    cobalt = Color(0xFF3A5BFF),
    cobaltLight = Color(0xFF6F86FF),
    violet = Color(0xFF6A3FE0),
    violetLight = Color(0xFF8F6BFF),
    night = Color(0xFF17151F),
    coverEdge = Color(0xFF3A3646),
)

/** Espacios, radios y tamaños del sistema, en dp. */
object VoxDimens {
    /** Entre título y autor en una fila. */
    val space1: Dp = 4.dp
    /** Entre chips y entre ícono y texto en un botón. */
    val space2: Dp = 8.dp
    /** Entre portada y texto en filas chicas; entre botones de una fila. */
    val space3: Dp = 12.dp
    /** Entre bloques de una pantalla y relleno de tarjetas chicas. */
    val space4: Dp = 16.dp
    /** Margen lateral de pantalla y de hojas inferiores. */
    val space5: Dp = 20.dp
    /** Relleno de diálogos y separación antes de un bloque nuevo. */
    val space6: Dp = 24.dp
    /** Margen superior de pantalla. */
    val space8: Dp = 32.dp
    /** Resaltado de la frase que suena, rombo de portadas, portadas mini. */
    val radiusS: Dp = 8.dp
    /** Portadas chicas y elementos de menú. */
    val radiusM: Dp = 12.dp
    /** Portadas y avisos. */
    val radiusL: Dp = 16.dp
    /** Tarjetas chicas, listas de ajustes, menús, racha, mini reproductor. */
    val radiusXl: Dp = 20.dp
    /** Tarjeta del libro actual y diálogos. */
    val radius2xl: Dp = 28.dp
    /** Esquinas superiores de hojas inferiores y del panel del reproductor. */
    val radiusSheet: Dp = 32.dp
    /** Botones, chips, pestañas, barra de navegación y reproductor flotante. */
    val radiusPill: Dp = 999.dp
    /** Íconos de trazo. */
    val sizeIcon: Dp = 24.dp
    /** Chips, pestañas y botones chicos. */
    val sizeControl: Dp = 40.dp
    /** Mínimo táctil: botones de ícono y botones. */
    val sizeTouch: Dp = 48.dp
    /** Botón de ancho completo y play de la lista. */
    val sizeLarge: Dp = 56.dp
    /** Play principal del reproductor. */
    val sizePlay: Dp = 80.dp
    /** Alto de la barra de navegación. */
    val sizeNav: Dp = 68.dp
    /** Alto del reproductor flotante. */
    val sizePlayer: Dp = 76.dp
}

/** Una sombra del sistema: desplazamiento, desenfoque y color, como en CSS. */
@Immutable
data class VoxShadow(val offsetX: Dp, val offsetY: Dp, val blur: Dp, val color: Color)

/** Sombras del sistema para un tema. Solo para lo que flota. */
@Immutable
data class VoxShadows(
    /** Pestaña seleccionada. */
    val shadowRaise: VoxShadow,
    /** Todo lo que flota: barra de navegación, reproductor flotante, mini reproductor, menús, diálogos y avisos. */
    val shadowFloat: VoxShadow,
    /** Portada grande del reproductor. */
    val shadowCover: VoxShadow,
)

/** Sombras del tema Claro. */
val LightVoxShadows = VoxShadows(
    shadowRaise = VoxShadow(0.dp, 1.dp, 3.dp, Color(0x1F17151F)),
    shadowFloat = VoxShadow(0.dp, 12.dp, 24.dp, Color(0x4017151F)),
    shadowCover = VoxShadow(0.dp, 16.dp, 32.dp, Color(0x3817151F)),
)

/** Sombras del tema Oscuro. */
val DarkVoxShadows = VoxShadows(
    shadowRaise = VoxShadow(0.dp, 1.dp, 3.dp, Color(0x80000000)),
    shadowFloat = VoxShadow(0.dp, 12.dp, 24.dp, Color(0x8C000000)),
    shadowCover = VoxShadow(0.dp, 16.dp, 32.dp, Color(0x99000000)),
)

/** Familia tipográfica de un estilo. Type.kt decide qué fuente usa cada una. */
enum class VoxFontRole { Display, Body, Serif }

/** Métricas de un estilo de texto del sistema, sin la fuente. */
@Immutable
data class VoxTextSpec(
    val role: VoxFontRole,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight,
    val letterSpacing: TextUnit,
)

/** Estilos de texto del sistema. */
object VoxTextSpecs {
    /** Título de pantalla. */
    val displayXl = VoxTextSpec(VoxFontRole.Display, 30.sp, 34.sp, FontWeight(800), (-0.5).sp)
    /** Título de hojas inferiores y diálogos. */
    val displayL = VoxTextSpec(VoxFontRole.Display, 24.sp, 30.sp, FontWeight(800), (-0.3).sp)
    /** Título del libro en la tarjeta del libro actual y en el reproductor. */
    val displayM = VoxTextSpec(VoxFontRole.Display, 22.sp, 26.sp, FontWeight(800), 0.sp)
    /** Título de sección. */
    val title = VoxTextSpec(VoxFontRole.Display, 20.sp, 26.sp, FontWeight(800), 0.sp)
    /** Acción principal dentro de una tarjeta y botón de ancho completo. */
    val emphasis = VoxTextSpec(VoxFontRole.Display, 17.sp, 22.sp, FontWeight(800), 0.sp)
    /** Texto del libro. */
    val reading = VoxTextSpec(VoxFontRole.Body, 19.sp, 31.sp, FontWeight(400), 0.sp)
    /** Texto del libro cuando la persona elige Literata en Apariencia. */
    val readingSerif = VoxTextSpec(VoxFontRole.Serif, 19.sp, 32.sp, FontWeight(400), 0.sp)
    /** Frase que suena, en grande, en el reproductor. */
    val nowPlaying = VoxTextSpec(VoxFontRole.Body, 19.sp, 28.sp, FontWeight(700), 0.sp)
    /** Título de una fila de libro o de ajuste. */
    val bodyStrong = VoxTextSpec(VoxFontRole.Body, 16.sp, 22.sp, FontWeight(700), 0.sp)
    /** Descripciones y texto de diálogos. */
    val body = VoxTextSpec(VoxFontRole.Body, 15.sp, 22.sp, FontWeight(400), 0.sp)
    /** Botones, chips, pestañas y elementos de menú. */
    val label = VoxTextSpec(VoxFontRole.Body, 14.sp, 20.sp, FontWeight(700), 0.sp)
    /** Autor, avance y metadatos. */
    val caption = VoxTextSpec(VoxFontRole.Body, 13.sp, 18.sp, FontWeight(600), 0.sp)
    /** Rótulos cortos, siempre en mayúsculas. */
    val overline = VoxTextSpec(VoxFontRole.Body, 12.sp, 16.sp, FontWeight(700), (1).sp)
}

/** Estilos de texto del sistema, ya resueltos con su fuente. */
@Immutable
data class VoxTypography(
    /** Título de pantalla. */
    val displayXl: TextStyle,
    /** Título de hojas inferiores y diálogos. */
    val displayL: TextStyle,
    /** Título del libro en la tarjeta del libro actual y en el reproductor. */
    val displayM: TextStyle,
    /** Título de sección. */
    val title: TextStyle,
    /** Acción principal dentro de una tarjeta y botón de ancho completo. */
    val emphasis: TextStyle,
    /** Texto del libro. */
    val reading: TextStyle,
    /** Texto del libro cuando la persona elige Literata en Apariencia. */
    val readingSerif: TextStyle,
    /** Frase que suena, en grande, en el reproductor. */
    val nowPlaying: TextStyle,
    /** Título de una fila de libro o de ajuste. */
    val bodyStrong: TextStyle,
    /** Descripciones y texto de diálogos. */
    val body: TextStyle,
    /** Botones, chips, pestañas y elementos de menú. */
    val label: TextStyle,
    /** Autor, avance y metadatos. */
    val caption: TextStyle,
    /** Rótulos cortos, siempre en mayúsculas. */
    val overline: TextStyle,
) {
    companion object {
        /** Arma la tipografía convirtiendo cada [VoxTextSpec] con [style]. */
        fun build(style: (VoxTextSpec) -> TextStyle) = VoxTypography(
            displayXl = style(VoxTextSpecs.displayXl),
            displayL = style(VoxTextSpecs.displayL),
            displayM = style(VoxTextSpecs.displayM),
            title = style(VoxTextSpecs.title),
            emphasis = style(VoxTextSpecs.emphasis),
            reading = style(VoxTextSpecs.reading),
            readingSerif = style(VoxTextSpecs.readingSerif),
            nowPlaying = style(VoxTextSpecs.nowPlaying),
            bodyStrong = style(VoxTextSpecs.bodyStrong),
            body = style(VoxTextSpecs.body),
            label = style(VoxTextSpecs.label),
            caption = style(VoxTextSpecs.caption),
            overline = style(VoxTextSpecs.overline),
        )
    }
}
