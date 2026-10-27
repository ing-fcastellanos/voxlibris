package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Tarjeta de estado para una tarea que tarda (`vx-banner`): qué pasa, un detalle y el avance del audio o la tarea. */
@Composable
fun Banner(title: String, modifier: Modifier = Modifier, detail: String? = null, progress: Float? = null) {
    val c = VoxLibrisTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoxDimens.radiusXl))
            .background(c.surface)
            .padding(horizontal = VoxDimens.space4, vertical = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3)) {
            Text(title, style = VoxLibrisTheme.typography.label, color = c.ink, modifier = Modifier.weight(1f))
            if (detail != null) {
                Text(detail, style = VoxLibrisTheme.typography.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ProgressBar(progress, fillColor = c.tomato)
    }
}
