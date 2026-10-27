package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import java.io.File

/**
 * Mini reproductor flotante (`vx-mini`): lo que suena mientras recorrés la app. Tocarlo abre el
 * libro; a la derecha, play o pausa y dejar de escuchar. La línea de abajo es el avance del libro.
 */
@Composable
fun MiniPlayer(
    title: String,
    statusText: String,
    style: BookStyle,
    coverFile: File?,
    progress: Float?,
    isPlaying: Boolean,
    canTogglePlayback: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val shape = RoundedCornerShape(VoxDimens.radiusXl)
    val paneTitle = stringResource(R.string.vx_now_playing)
    Box(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .voxShadow(VoxLibrisTheme.shadows.shadowFloat, shape)
            .clip(shape)
            .background(c.surface)
            .semantics { this.paneTitle = paneTitle },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(VoxDimens.radiusM))
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.vx_open_book, title), onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
            ) {
                BookCover(title, style, coverFile, CoverSize.Mini)
                Column(Modifier.weight(1f)) {
                    Text(title, style = t.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        statusText,
                        style = t.caption,
                        color = c.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            if (canTogglePlayback) {
                PlayButton(
                    playing = isPlaying,
                    onClick = onPlayPause,
                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                    size = PlayButtonSize.Small,
                )
            }
            VoxIconButton(
                R.drawable.ic_vx_close,
                stringResource(R.string.stop_listening),
                onStop,
                variant = VoxIconButtonVariant.Clear,
            )
        }
        if (progress != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(c.tomato),
            )
        }
    }
}
