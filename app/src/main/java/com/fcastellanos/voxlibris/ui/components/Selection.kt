package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/**
 * Chip de una opción corta (`vx-chip`): velocidad, temporizador, calidad o interlineado.
 * El elegido se pinta con `fill-strong`. [onGround] es para chips sobre `ground` en lugar de `surface`.
 * [leading] pone algo chico delante del texto, como el avatar de una voz.
 */
@Composable
fun VoxChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onGround: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = VoxLibrisTheme.colors
    val container = when {
        selected -> c.fillStrong
        onGround -> c.surface
        else -> c.surfaceMuted
    }
    Box(
        modifier
            .height(VoxDimens.sizeControl)
            .clip(RoundedCornerShape(VoxDimens.radiusPill))
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = if (leading != null) 4.dp else 14.dp, end = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leading?.invoke()
            Text(text, style = VoxLibrisTheme.typography.label, color = if (selected) c.onFillStrong else c.ink, maxLines = 1)
        }
    }
}

/** Grupo de [VoxChip] que se acomoda en varias líneas (`vx-chips`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoxChipGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2),
        verticalArrangement = Arrangement.spacedBy(VoxDimens.space2),
    ) { content() }
}

/** Pestañas sobre `surface` (`vx-tabs`): dos o tres vistas del mismo panel. */
@Composable
fun VoxTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    val shape = RoundedCornerShape(VoxDimens.radiusPill)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceMuted)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(VoxDimens.sizeControl)
                    .then(if (selected) Modifier.voxShadow(VoxLibrisTheme.shadows.shadowRaise, shape) else Modifier)
                    .clip(shape)
                    .background(if (selected) c.surface else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = VoxLibrisTheme.typography.label, color = if (selected) c.ink else c.inkMuted, maxLines = 1)
            }
        }
    }
}

/** Interruptor del sistema (`vx-switch`): encendido en `fill-strong`, apagado en `track`. */
@Composable
fun VoxSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onFillStrong,
            checkedTrackColor = c.fillStrong,
            checkedBorderColor = c.fillStrong,
            uncheckedThumbColor = c.inkMuted,
            uncheckedTrackColor = c.track,
            uncheckedBorderColor = c.track,
        ),
    )
}

/**
 * Deslizador del sistema (`vx-slider`): riel de 10 dp como la barra de avance y un círculo de 24 dp.
 * Sobre `fill-strong`, pasá `onStrong = true` para invertir los colores.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoxSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    onStrong: Boolean = false,
    stateDescription: String? = null,
) {
    val c = VoxLibrisTheme.colors
    val trackColor = if (onStrong) c.fillStrongRaised else c.track
    val fillColor = if (onStrong) c.onFillStrong else c.fillStrong
    val thumbColor = if (onStrong) c.onFillStrong else c.fillStrong
    val ringColor = if (onStrong) c.fillStrong else c.surface
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { if (stateDescription != null) this.stateDescription = stateDescription },
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        thumb = {
            Box(
                Modifier
                    .size(24.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(ringColor)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(trackColor, cornerRadius = radius)
                if (fraction > 0f) {
                    drawRoundRect(fillColor, topLeft = Offset.Zero, size = Size(size.width * fraction, size.height), cornerRadius = radius)
                }
            }
        },
    )
}

/**
 * Tarjeta de opción (`vx-option`): una de dos o tres opciones visuales, como la tipografía o el
 * fondo de lectura. La elegida lleva un borde de 2 dp en `ink`.
 */
@Composable
fun OptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = VoxLibrisTheme.colors
    val shape = RoundedCornerShape(VoxDimens.radiusL)
    Column(
        modifier
            .heightIn(min = VoxDimens.sizeLarge)
            .clip(shape)
            .background(c.surfaceMuted)
            .then(if (selected) Modifier.border(2.dp, c.ink, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Botón redondo de texto (`vx-icon-btn` con letras): A− y A+ del tamaño de letra. */
@Composable
fun VoxTextIconButton(text: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(VoxDimens.sizeTouch)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = c.surfaceMuted,
        contentColor = c.ink,
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, style = VoxLibrisTheme.typography.label) }
    }
}
