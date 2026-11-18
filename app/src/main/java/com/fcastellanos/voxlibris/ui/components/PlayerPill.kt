package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/**
 * Píldora oscura y flotante (`vx-player-pill`): base del reproductor flotante y de la barra de
 * lectura del texto completo. Los íconos de adentro toman `on-fill-strong`.
 */
@Composable
fun StrongPill(label: String, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c = VoxLibrisTheme.colors
    val shape = RoundedCornerShape(VoxDimens.radiusPill)
    CompositionLocalProvider(LocalContentColor provides c.onFillStrong) {
        Row(
            modifier
                .fillMaxWidth()
                .height(VoxDimens.sizePlayer)
                .voxShadow(VoxLibrisTheme.shadows.shadowFloat, shape)
                .clip(shape)
                .background(c.fillStrong)
                .padding(horizontal = VoxDimens.space3)
                .semantics { paneTitle = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = content,
        )
    }
}

/**
 * Reproductor flotante (`vx-player-pill`): lector, frase anterior, play o pausa, frase siguiente
 * y velocidad. El lector y la velocidad abren sus propias opciones.
 */
@Composable
fun PlayerPill(
    label: String,
    voiceName: String,
    voiceId: Int,
    voiceDescription: String,
    speedText: String,
    speedDescription: String,
    playing: Boolean,
    playDescription: String,
    previousDescription: String,
    nextDescription: String,
    onVoice: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSpeed: () -> Unit,
    modifier: Modifier = Modifier,
    speedAnchor: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
) {
    val c = VoxLibrisTheme.colors
    StrongPill(label, modifier) {
        Surface(
            onClick = onVoice,
            modifier = Modifier.size(52.dp).semantics { contentDescription = voiceDescription },
            shape = CircleShape,
            color = c.fillStrong,
        ) {
            Box(contentAlignment = Alignment.Center) { VoiceAvatar(voiceName.take(1), voiceTint(voiceId)) }
        }
        VoxIconButton(R.drawable.ic_vx_previous, previousDescription, onPrevious, variant = VoxIconButtonVariant.Clear, iconSize = 24.dp)
        PlayButton(playing = playing, onClick = onPlayPause, contentDescription = playDescription)
        VoxIconButton(R.drawable.ic_vx_next, nextDescription, onNext, variant = VoxIconButtonVariant.Clear, iconSize = 24.dp)
        speedAnchor {
            Surface(
                onClick = onSpeed,
                modifier = Modifier.height(44.dp).semantics { contentDescription = speedDescription },
                shape = RoundedCornerShape(VoxDimens.radiusPill),
                color = c.fillStrongRaised,
                contentColor = c.onFillStrong,
            ) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(speedText, style = VoxLibrisTheme.typography.label)
                }
            }
        }
    }
}

/** Píldora clara con el estado de la lectura en voz alta y dos acciones chicas a la derecha. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, isError: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    val c = VoxLibrisTheme.colors
    val shape = RoundedCornerShape(VoxDimens.radiusPill)
    Row(
        modifier
            .fillMaxWidth()
            .height(VoxDimens.sizeTouch)
            .voxShadow(VoxLibrisTheme.shadows.shadowFloat, shape)
            .clip(shape)
            .background(c.surface)
            .padding(start = VoxDimens.space4, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text,
            style = VoxLibrisTheme.typography.caption,
            color = if (isError) c.danger else c.inkMuted,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}
