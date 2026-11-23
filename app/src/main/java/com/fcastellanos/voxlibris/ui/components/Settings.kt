package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Rótulo corto de una sección (`vx-list-title` en `overline`). Se escribe en mayúsculas. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = VoxLibrisTheme.colors.inkMuted) {
    Text(
        text.uppercase(),
        style = VoxLibrisTheme.typography.overline,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Lista agrupada de ajustes (`vx-list`): filas sobre una tarjeta, separadas por `line`.
 * [onGround] pinta la tarjeta en `surface`; dentro de una hoja, que ya es `surface`, va en `surface-muted`.
 */
@Composable
fun SettingsList(modifier: Modifier = Modifier, onGround: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = VoxLibrisTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoxDimens.radiusXl))
            .background(if (onGround) c.surface else c.surfaceMuted),
        content = content,
    )
}

/** Separador entre filas de una [SettingsList]. */
@Composable
fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = VoxDimens.space4).height(1.dp).background(VoxLibrisTheme.colors.line))
}

/** Fila con título, ayuda opcional y un interruptor; toda la fila lo cambia. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    SettingsRowFrame(
        title,
        subtitle,
        modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        VoxSwitch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Fila que abre algo o ejecuta una acción. [leading] va delante del texto (el avatar de una voz, un
 * ícono); [trailing] muestra un valor, un botón o una flecha.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = VoxLibrisTheme.colors.ink,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val clickable = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier
    SettingsRowFrame(title, subtitle, clickable, titleColor, leading, trailing)
}

/** Flecha de una fila que abre otra pantalla ([down] para un menú desplegable). */
@Composable
fun SettingsChevron(down: Boolean = false) {
    Icon(
        painterResource(if (down) R.drawable.ic_vx_chevron_down else R.drawable.ic_vx_chevron),
        contentDescription = null,
        tint = VoxLibrisTheme.colors.ink,
        modifier = Modifier.size(20.dp),
    )
}

/** Valor actual de una fila (`vx-list__value`), antes de la flecha. */
@Composable
fun SettingsValue(text: String) {
    Text(text, style = VoxLibrisTheme.typography.caption, color = VoxLibrisTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SettingsRowFrame(
    title: String,
    subtitle: String?,
    modifier: Modifier,
    titleColor: Color = VoxLibrisTheme.colors.ink,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    val c = VoxLibrisTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = VoxDimens.space4, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = VoxLibrisTheme.typography.bodyStrong, color = titleColor)
            if (subtitle != null) Text(subtitle, style = VoxLibrisTheme.typography.caption, color = c.inkMuted)
        }
        trailing()
    }
}
