package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import java.io.File

/**
 * Tarjeta del libro actual (`vx-continue`), siempre en `tomato`. Arriba, tocar la portada o el
 * título abre el libro; abajo, el botón retoma el audio y el chip abre los lectores.
 */
@Composable
fun ContinueCard(
    title: String,
    author: String?,
    style: BookStyle,
    coverFile: File?,
    progressPercent: Int,
    label: String,
    isPlaying: Boolean,
    statusText: String?,
    voiceName: String,
    voiceId: Int,
    speed: Double,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onListen: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoxDimens.radius2xl))
            .background(c.tomato)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(VoxDimens.space4),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(VoxDimens.radiusL))
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.vx_open_book, title),
                    onLongClickLabel = stringResource(R.string.vx_book_actions, title),
                    onLongClick = onMenu,
                    onClick = onOpen,
                ),
            horizontalArrangement = Arrangement.spacedBy(VoxDimens.space4),
        ) {
            BookCover(title, style, coverFile, CoverSize.Medium)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(VoxDimens.space1)) {
                Text(label, style = t.caption, color = c.onColor)
                Text(title, style = t.displayM, color = c.onColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (author != null) {
                    Text(author, style = t.caption, color = c.onColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.vx_read_percent, progressPercent), style = t.caption, color = c.onColor)
                ProgressBar(progressPercent / 100f, trackColor = c.tomatoTrack, fillColor = c.onColor)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3)) {
            val action = stringResource(if (isPlaying) R.string.pause else R.string.library_continue_listen)
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(VoxDimens.radiusPill))
                    .clickable(role = Role.Button, onClick = onListen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
            ) {
                PlayButton(
                    playing = isPlaying,
                    onClick = null,
                    contentDescription = null,
                    containerColor = c.fillStrong,
                    contentColor = c.onFillStrong,
                )
                Column(Modifier.weight(1f)) {
                    Text(action, style = t.emphasis, color = c.onColor)
                    Text(
                        statusText ?: stringResource(R.string.library_continue_hint),
                        style = t.caption,
                        color = c.onColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            VoiceChip(voiceName, voiceId, speed, onVoice)
        }
    }
}

/** Chip con el lector y la velocidad (`vx-continue__voice`). Abre la elección de lector. */
@Composable
fun VoiceChip(voiceName: String, voiceId: Int, speed: Double, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    val speedText = formatSpeed(speed)
    val description = stringResource(R.string.vx_voice_chip, voiceName, speedText)
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(VoxDimens.radiusPill),
        color = c.surface,
        contentColor = c.ink,
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2),
        ) {
            VoiceAvatar(voiceName.take(1), voiceTint(voiceId), size = 32.dp)
            Text(speedText, style = VoxLibrisTheme.typography.label)
        }
    }
}
