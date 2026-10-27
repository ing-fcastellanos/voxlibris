package com.fcastellanos.voxlibris.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import com.fcastellanos.voxlibris.ui.theme.VoxShadow

/** Dibuja una sombra del sistema (`shadow-float`, `shadow-cover`…) con el mismo desenfoque y desplazamiento que en CSS. */
fun Modifier.voxShadow(spec: VoxShadow, shape: Shape): Modifier = dropShadow(
    shape = shape,
    shadow = Shadow(radius = spec.blur, color = spec.color, offset = DpOffset(spec.offsetX, spec.offsetY)),
)
