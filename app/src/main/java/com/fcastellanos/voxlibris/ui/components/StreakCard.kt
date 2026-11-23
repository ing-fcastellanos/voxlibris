package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Un punto de la semana en la tarjeta de racha: escuchado, hoy (anillo en `accent`) o nada. */
data class StreakDot(val listened: Boolean, val isToday: Boolean)

/**
 * Tarjeta de racha (`vx-streak`): días seguidos escuchando, el tiempo de la semana y siete puntos,
 * de lunes a domingo. `accent` marca solo el día de hoy.
 */
@Composable
fun StreakCard(title: String, subtitle: String, days: List<StreakDot>, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoxDimens.radiusXl))
            .background(c.surface)
            .padding(horizontal = VoxDimens.space4, vertical = VoxDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = t.emphasis, color = c.ink)
            Text(subtitle, style = t.caption, color = c.inkMuted)
        }
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            days.forEach { day ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .then(
                            when {
                                day.isToday && !day.listened -> Modifier.border(3.dp, c.accent, CircleShape)
                                day.listened -> Modifier.background(if (day.isToday) c.accent else c.fillStrong)
                                else -> Modifier.background(c.track)
                            },
                        ),
                )
            }
        }
    }
}
