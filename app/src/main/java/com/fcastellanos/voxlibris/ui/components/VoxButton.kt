package com.fcastellanos.voxlibris.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Variantes de `vx-btn`: principal, secundario sobre surface, secundario sobre ground, eliminar, agregar y sin fondo. */
enum class VoxButtonVariant { Primary, Secondary, OnGround, Danger, Accent, Ghost }

/** Alturas de `vx-btn`: chico, base, grande (56 dp con texto `emphasis`) y de ancho completo. */
enum class VoxButtonSize(val height: Dp, val horizontalPadding: Dp) {
    Small(VoxDimens.sizeControl, 16.dp),
    Default(VoxDimens.sizeTouch, 20.dp),
    Large(VoxDimens.sizeLarge, 28.dp),
    Block(VoxDimens.sizeLarge, 20.dp),
}

/** Botón de texto con forma de píldora (`vx-btn`). Para escuchar se usa [PlayButton]. */
@Composable
fun VoxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: VoxButtonVariant = VoxButtonVariant.Primary,
    size: VoxButtonSize = VoxButtonSize.Default,
    @DrawableRes icon: Int? = null,
) {
    val c = VoxLibrisTheme.colors
    val (container, content) = when (variant) {
        VoxButtonVariant.Primary -> c.fillStrong to c.onFillStrong
        VoxButtonVariant.Secondary -> c.surfaceMuted to c.ink
        VoxButtonVariant.OnGround -> c.surface to c.ink
        VoxButtonVariant.Danger -> c.danger to c.onDanger
        VoxButtonVariant.Accent -> c.accent to c.onAccent
        VoxButtonVariant.Ghost -> Color.Transparent to c.ink
    }
    val big = size == VoxButtonSize.Large || size == VoxButtonSize.Block
    Surface(
        onClick = onClick,
        modifier = modifier.height(size.height).then(if (size == VoxButtonSize.Block) Modifier.fillMaxWidth() else Modifier),
        shape = RoundedCornerShape(VoxDimens.radiusPill),
        color = container,
        contentColor = content,
    ) {
        Row(
            Modifier.padding(horizontal = size.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(if (big) 22.dp else 20.dp))
            Text(text, style = if (big) VoxLibrisTheme.typography.emphasis else VoxLibrisTheme.typography.label, maxLines = 1)
        }
    }
}

/** Variantes de `vx-icon-btn`: sobre ground o color, sobre surface, y transparente dentro de barras. */
enum class VoxIconButtonVariant { Surface, Muted, Clear }

/** Botón redondo con un solo ícono (`vx-icon-btn`). Siempre lleva [contentDescription]. */
@Composable
fun VoxIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: VoxIconButtonVariant = VoxIconButtonVariant.Surface,
    size: Dp = VoxDimens.sizeTouch,
    iconSize: Dp = 22.dp,
) {
    val c = VoxLibrisTheme.colors
    val (container, content) = when (variant) {
        VoxIconButtonVariant.Surface -> c.surface to c.ink
        VoxIconButtonVariant.Muted -> c.surfaceMuted to c.ink
        VoxIconButtonVariant.Clear -> Color.Transparent to LocalContentColor.current
    }
    Surface(
        onClick = onClick,
        modifier = modifier.size(size).semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}
