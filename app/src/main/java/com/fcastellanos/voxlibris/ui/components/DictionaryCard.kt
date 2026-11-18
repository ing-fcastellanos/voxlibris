package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/**
 * Tarjeta flotante del diccionario (`vx-dict`): la palabra en `display-l`, un botón para cerrar,
 * las acepciones con desplazamiento propio y una fila de acciones al pie. Aparece sobre el texto
 * del libro al seleccionar una sola palabra.
 */
@Composable
fun DictionaryCard(
    word: String,
    closeDescription: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = VoxLibrisTheme.colors
    val shape = RoundedCornerShape(VoxDimens.radiusXl)
    Column(
        modifier
            .fillMaxWidth()
            .voxShadow(VoxLibrisTheme.shadows.shadowFloat, shape)
            .clip(shape)
            .background(c.surface)
            .padding(start = VoxDimens.space5, end = VoxDimens.space2, top = VoxDimens.space2, bottom = VoxDimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                word,
                style = VoxLibrisTheme.typography.displayL,
                color = c.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            VoxIconButton(R.drawable.ic_vx_close, closeDescription, onClose, variant = VoxIconButtonVariant.Muted, size = VoxDimens.sizeControl, iconSize = 18.dp)
        }
        Column(
            Modifier
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
                .padding(end = VoxDimens.space3, top = VoxDimens.space1)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(VoxDimens.space1),
            content = content,
        )
        if (footer != null) {
            Row(
                Modifier.padding(top = VoxDimens.space3, end = VoxDimens.space3),
                horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2),
                verticalAlignment = Alignment.CenterVertically,
                content = footer,
            )
        }
    }
}
