package com.fcastellanos.voxlibris.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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

/** Elemento de menú (`vx-menu__item`): 48 dp, ícono opcional y texto `label`. [danger] va al final, separado. */
@Composable
fun VoxMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    danger: Boolean = false,
) {
    val c = VoxLibrisTheme.colors
    val color = if (danger) c.danger else c.ink
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = VoxDimens.sizeTouch)
            .clip(RoundedCornerShape(VoxDimens.radiusM))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = VoxLibrisTheme.typography.label, color = color)
    }
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
