package com.fcastellanos.voxlibris.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Hoja inferior del sistema (`vx-sheet`): `surface`, esquinas de 32 dp y agarradera en `line`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxBottomSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = VoxLibrisTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = VoxDimens.radiusSheet, topEnd = VoxDimens.radiusSheet),
        containerColor = c.surface,
        contentColor = c.ink,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = VoxDimens.space3, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(VoxDimens.radiusPill))
                    .background(c.line),
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = VoxDimens.space5, end = VoxDimens.space5, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

/**
 * Elemento de menú (`vx-menu__item`): 48 dp, ícono opcional y texto `label`. [danger] va al final,
 * separado. Con [checked] distinto de nulo es una opción elegible: la elegida lleva fondo
 * `surface-muted` y una tilde.
 */
@Composable
fun VoxMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    danger: Boolean = false,
    checked: Boolean? = null,
) {
    val c = VoxLibrisTheme.colors
    val color = if (danger) c.danger else c.ink
    val interaction = if (checked != null) {
        Modifier.selectable(selected = checked, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = VoxDimens.sizeTouch)
            .clip(RoundedCornerShape(VoxDimens.radiusM))
            .then(if (checked == true) Modifier.background(c.surfaceMuted) else Modifier)
            .then(interaction)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = VoxLibrisTheme.typography.label, color = color, modifier = Modifier.weight(1f))
        if (checked == true) Icon(painterResource(R.drawable.ic_vx_check), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

/** Menú desplegable del sistema (`vx-menu`): sale del control que lo abre, en `surface` con `shadow-float`. */
@Composable
fun VoxDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 200.dp).padding(horizontal = VoxDimens.space2),
        shape = RoundedCornerShape(VoxDimens.radiusXl),
        containerColor = VoxLibrisTheme.colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        if (label != null) SectionLabel(label, Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp))
        content()
    }
}

/** Cabecera de una hoja inferior (`vx-sheet__head`): título en `display-l` y una etiqueta opcional. */
@Composable
fun SheetHeader(title: String, modifier: Modifier = Modifier, tag: String? = null) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
    ) {
        Text(
            title,
            style = VoxLibrisTheme.typography.displayL,
            color = VoxLibrisTheme.colors.ink,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (tag != null) Tag(tag)
    }
}

/** Etiqueta chica de contexto (`vx-tag`). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    Box(
        modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(VoxDimens.radiusPill))
            .background(c.surfaceMuted)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = VoxLibrisTheme.typography.caption, color = c.ink, maxLines = 1)
    }
}

/**
 * Diálogo del sistema (`vx-dialog`) con contenido libre: título en `display-l`, el contenido y una
 * fila de botones [VoxButton] chicos alineados a la derecha. Para confirmar algo destructivo,
 * [VoxConfirmDialog].
 */
@Composable
fun VoxDialog(
    title: String,
    onDismissRequest: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = VoxLibrisTheme.colors
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2), verticalAlignment = Alignment.CenterVertically, content = actions)
        },
        title = { Text(title, style = VoxLibrisTheme.typography.displayL) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(VoxDimens.space3), content = content) },
        shape = RoundedCornerShape(VoxDimens.radius2xl),
        containerColor = c.surface,
        titleContentColor = c.ink,
        textContentColor = c.ink,
    )
}

/** Separador de menú (`vx-menu__sep`). */
@Composable
fun VoxMenuSeparator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = VoxDimens.space2, vertical = 6.dp).heightIn(min = 1.dp, max = 1.dp).background(VoxLibrisTheme.colors.line))
}

/**
 * Diálogo para confirmar algo que no se puede deshacer (`vx-dialog`): pregunta en `display-l`,
 * explicación y dos botones, cancelar primero y la acción después.
 */
@Composable
fun VoxConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    val c = VoxLibrisTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            VoxButton(
                confirmLabel,
                onConfirm,
                variant = if (destructive) VoxButtonVariant.Danger else VoxButtonVariant.Primary,
                size = VoxButtonSize.Small,
            )
        },
        dismissButton = {
            VoxButton(stringResource(R.string.cancel), onDismiss, variant = VoxButtonVariant.Secondary, size = VoxButtonSize.Small)
        },
        title = { Text(title, style = VoxLibrisTheme.typography.displayL) },
        text = { Text(body, style = VoxLibrisTheme.typography.body) },
        shape = RoundedCornerShape(VoxDimens.radius2xl),
        containerColor = c.surface,
        titleContentColor = c.ink,
        textContentColor = c.inkMuted,
    )
}
