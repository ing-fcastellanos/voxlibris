package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/**
 * Barra de avance del sistema (`vx-progress`): 10 dp, o 6 dp con [thin]. Con [progress] nulo
 * es indeterminada. Acompañala siempre con el número en texto.
 *
 * Colores: avance de lectura con los valores por defecto; avance del audio con `fillColor = tomato`;
 * dentro de la tarjeta `tomato`, `trackColor = tomatoTrack` y `fillColor = onColor`.
 */
@Composable
fun ProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    thin: Boolean = false,
    trackColor: Color = VoxLibrisTheme.colors.track,
    fillColor: Color = VoxLibrisTheme.colors.fillStrong,
) {
    val height = if (thin) 6.dp else 10.dp
    val shape = RoundedCornerShape(VoxDimens.radiusPill)
    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth().height(height).clip(shape),
            color = fillColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    } else {
        val value = progress.coerceIn(0f, 1f)
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(trackColor)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value).clip(shape).background(fillColor))
        }
    }
}
