@file:OptIn(ExperimentalReadiumApi::class)

package com.fcastellanos.voxlibris.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.components.OptionCard
import com.fcastellanos.voxlibris.ui.components.SectionLabel
import com.fcastellanos.voxlibris.ui.components.SettingsDivider
import com.fcastellanos.voxlibris.ui.components.SettingsList
import com.fcastellanos.voxlibris.ui.components.SettingsSwitchRow
import com.fcastellanos.voxlibris.ui.components.SheetHeader
import com.fcastellanos.voxlibris.ui.components.VoxBottomSheet
import com.fcastellanos.voxlibris.ui.components.VoxChip
import com.fcastellanos.voxlibris.ui.components.VoxChipGroup
import com.fcastellanos.voxlibris.ui.components.VoxTextIconButton
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi

private val LINE_HEIGHTS = listOf(R.string.reader_line_compact to 1.2, R.string.reader_line_normal to 1.5, R.string.reader_line_wide to 1.8)
private const val DEFAULT_FONT_SIZE = 1.0
private const val DEFAULT_LINE_HEIGHT = 1.2
private const val FONT_SIZE_STEP = 0.1
private val FONT_SIZE_RANGE = 0.5..3.0

/** Tamaño de letra como porcentaje del tamaño base del libro («100 %»). */
internal fun EpubPreferences.fontSizePercent(): Int = ((fontSize ?: DEFAULT_FONT_SIZE) * 100).roundToInt()

/** Nombre de la tipografía elegida, como lo muestra la hoja de Apariencia. */
@Composable
internal fun EpubPreferences.fontFamilyLabel(): String = when (fontFamily) {
    ReaderFonts.Figtree -> "Figtree"
    ReaderFonts.Literata -> "Literata"
    else -> stringResource(R.string.reader_font_publisher)
}

/**
 * Apariencia del texto (pantalla «Apariencia» del lienzo): tamaño, tipografía, fondo e interlineado.
 * Edita las [EpubPreferences] directamente, así sirve tanto dentro del lector como desde Ajustes, sin
 * necesidad de un libro abierto. Cada cambio se guarda al instante.
 */
@Composable
internal fun AppearanceSheet(
    preferences: EpubPreferences,
    onChange: (EpubPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val paper = preferences.paper()
    val context = LocalContext.current
    val literata = remember { ComposeFontFamily(Font("fonts/Literata.ttf", context.assets)) }
    val figtree = remember { ComposeFontFamily(Font(R.font.figtree)) }
    val fontSize = preferences.fontSize ?: DEFAULT_FONT_SIZE
    fun setFontSize(value: Double) = onChange(preferences.copy(fontSize = ((value * 10).roundToInt() / 10.0).coerceIn(FONT_SIZE_RANGE)))

    VoxBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.reader_appearance), tag = stringResource(R.string.reader_appearance_tag))
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.font_size))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3)) {
                    VoxTextIconButton("A−", stringResource(R.string.reader_font_smaller), onClick = { setFontSize(fontSize - FONT_SIZE_STEP) })
                    Text(
                        stringResource(R.string.progress_percent, preferences.fontSizePercent()),
                        style = t.emphasis,
                        color = c.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    VoxTextIconButton("A+", stringResource(R.string.reader_font_bigger), onClick = { setFontSize(fontSize + FONT_SIZE_STEP) })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.reader_font_family))
                val families = listOf(
                    Triple(null as FontFamily?, stringResource(R.string.reader_font_publisher), ComposeFontFamily.Default),
                    Triple(ReaderFonts.Figtree, "Figtree", figtree),
                    Triple(ReaderFonts.Literata, "Literata", literata),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
                    families.forEach { (family, label, sampleFont) ->
                        OptionCard(
                            selected = preferences.fontFamily == family,
                            onClick = { onChange(preferences.copy(fontFamily = family)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Aa", style = t.title.copy(fontFamily = sampleFont, fontSize = 26.sp, lineHeight = 32.sp), color = c.ink)
                            Text(label, style = t.caption, color = c.ink, maxLines = 1)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.reader_paper))
                val papers = listOf(
                    ReaderPaper.Warm to R.string.reader_paper_warm,
                    ReaderPaper.White to R.string.reader_paper_white,
                    ReaderPaper.Dark to R.string.reader_paper_dark,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
                    papers.forEach { (option, label) ->
                        OptionCard(
                            selected = paper == option,
                            onClick = { onChange(preferences.withPaper(option)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(option.background)
                                    .border(1.dp, c.line, CircleShape),
                            )
                            Text(stringResource(label), style = t.caption, color = c.ink, maxLines = 1)
                        }
                    }
                }
            }

            // Readium solo aplica el interlineado propio cuando no se respetan los estilos del editor.
            if (preferences.publisherStyles == false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.line_height))
                    val currentLine = preferences.lineHeight ?: DEFAULT_LINE_HEIGHT
                    val nearest = LINE_HEIGHTS.minBy { abs(it.second - currentLine) }.second
                    VoxChipGroup {
                        LINE_HEIGHTS.forEach { (label, value) ->
                            VoxChip(stringResource(label), selected = value == nearest, onClick = { onChange(preferences.copy(lineHeight = value)) })
                        }
                    }
                }
            }

            SettingsList {
                SettingsSwitchRow(
                    title = stringResource(R.string.scroll_mode),
                    subtitle = stringResource(R.string.reader_scroll_hint),
                    checked = preferences.scroll == true,
                    onCheckedChange = { onChange(preferences.copy(scroll = it)) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.publisher_styles),
                    subtitle = stringResource(R.string.reader_publisher_styles_hint),
                    checked = preferences.publisherStyles != false,
                    onCheckedChange = { onChange(preferences.copy(publisherStyles = it)) },
                )
            }
        }
    }
}
