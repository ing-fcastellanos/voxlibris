package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val VoiceTints = listOf(
    BookTint.Mint, BookTint.Pink, BookTint.Mustard, BookTint.Lilac, BookTint.Cobalt,
    BookTint.Tomato, BookTint.Night, BookTint.Butter, BookTint.Jade, BookTint.Violet,
)

/** Color de cada voz Supertonic: los ids 0 a 4 son F1 a F5 y los 5 a 9 son M1 a M5, como en el sistema. */
fun voiceTint(voiceId: Int): BookTint = VoiceTints.getOrElse(voiceId) { BookTint.Mint }

/** Velocidad en formato local: 1 → «1×», 1.25 → «1,25×». */
fun formatSpeed(speed: Double): String =
    DecimalFormat("0.##", DecimalFormatSymbols(Locale.forLanguageTag("es"))).format(speed) + "×"

/** Círculo con la inicial o el código de una voz, en su color. Decorativo: quien lo usa pone la etiqueta. */
@Composable
fun VoiceAvatar(label: String, tint: BookTint, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val colors = tint.colors
    val content = if (tint == BookTint.Night) VoxLibrisTheme.colors.onDeep else colors.content
    val fontSize = if (size < 40.dp) 13.sp else 17.sp
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = VoxLibrisTheme.typography.emphasis.copy(fontSize = fontSize, lineHeight = fontSize),
            color = content,
        )
    }
}
