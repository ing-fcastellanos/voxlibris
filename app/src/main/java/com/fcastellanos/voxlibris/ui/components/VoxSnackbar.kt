package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Contenido de un aviso del sistema. [isError] cambia el ícono y lo anuncia de inmediato. */
class VoxSnackbarVisuals(
    override val message: String,
    val isError: Boolean = false,
    override val actionLabel: String? = null,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long
}

/** Aviso breve (`vx-snack`), para usar como contenido de un `SnackbarHost`. */
@Composable
fun VoxSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    val visuals = data.visuals
    val isError = (visuals as? VoxSnackbarVisuals)?.isError == true
    val shape = RoundedCornerShape(VoxDimens.radiusL)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .voxShadow(VoxLibrisTheme.shadows.shadowFloat, shape)
            .clip(shape)
            .background(c.fillStrong)
            .padding(start = 14.dp, end = VoxDimens.space2, top = VoxDimens.space2, bottom = VoxDimens.space2)
            .semantics { liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (isError) c.danger else c.mint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (isError) R.drawable.ic_vx_alert else R.drawable.ic_vx_check),
                contentDescription = null,
                tint = if (isError) c.onDanger else c.onColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            visuals.message,
            style = VoxLibrisTheme.typography.label,
            color = c.onFillStrong,
            modifier = Modifier.weight(1f).padding(vertical = VoxDimens.space2),
        )
        val action = visuals.actionLabel
        if (action != null) {
            Surface(
                onClick = data::performAction,
                shape = RoundedCornerShape(VoxDimens.radiusPill),
                color = c.fillStrongRaised,
                contentColor = c.onFillStrong,
            ) {
                Text(action, style = VoxLibrisTheme.typography.label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}
