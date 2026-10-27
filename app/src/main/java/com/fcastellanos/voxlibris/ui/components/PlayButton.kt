package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Tamaños del botón de escuchar (`vx-play--hero`, base y `--small`). */
enum class PlayButtonSize(val diameter: Dp, val icon: Dp) {
    Hero(VoxDimens.sizePlay, 32.dp),
    Default(VoxDimens.sizeLarge, 24.dp),
    Small(VoxDimens.sizeTouch, 20.dp),
}

/**
 * El botón de escuchar: redondo, en `tomato` por defecto, con play o pausa.
 *
 * Con [onClick] nulo se dibuja solo el círculo, para usarlo dentro de una fila que ya es el botón.
 * Para el play de un libro, pasá los colores de su [BookTint]; sobre una tarjeta `tomato`, `fill-strong`.
 */
@Composable
fun PlayButton(
    playing: Boolean,
    onClick: (() -> Unit)?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: PlayButtonSize = PlayButtonSize.Default,
    containerColor: Color = VoxLibrisTheme.colors.tomato,
    contentColor: Color = VoxLibrisTheme.colors.onColor,
) {
    val iconRes = if (playing) R.drawable.ic_vx_pause else R.drawable.ic_vx_play
    if (onClick == null) {
        Box(
            modifier.size(size.diameter).clip(CircleShape).background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(size.icon), tint = contentColor)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier
                .size(size.diameter)
                .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(size.icon))
            }
        }
    }
}
