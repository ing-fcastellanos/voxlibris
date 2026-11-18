package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/**
 * Campo de texto del sistema (`vx-field`): rótulo en `caption` arriba, caja `surface-muted` con
 * esquinas de 16 dp y, con el foco, el contorno de 3 dp en `focus`. Sirve para notas y textos largos.
 */
@Composable
fun VoxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    fieldModifier: Modifier = Modifier,
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(VoxDimens.radiusL)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = t.caption, color = c.inkMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier
                .fillMaxWidth()
                .then(if (focused) Modifier.border(3.dp, c.focus, shape) else Modifier)
                .clip(shape)
                .background(c.surfaceMuted),
            textStyle = t.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            minLines = minLines,
            maxLines = maxLines,
            interactionSource = interaction,
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = t.body, color = c.inkMuted)
                    inner()
                }
            },
        )
    }
}
