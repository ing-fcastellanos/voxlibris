package com.fcastellanos.voxlibris.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Un destino de la barra. [accent] es solo para Agregar libro, que no es una sección. */
@Immutable
data class NavBarItem(
    val label: String,
    @DrawableRes val icon: Int,
    val selected: Boolean = false,
    val accent: Boolean = false,
    val onClick: () -> Unit,
)

/** Barra de navegación flotante (`vx-nav`). La sección actual muestra su nombre; las demás, solo el ícono. */
@Composable
fun NavBar(items: List<NavBarItem>, label: String, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    val shape = RoundedCornerShape(VoxDimens.radiusPill)
    Row(
        modifier
            .fillMaxWidth()
            .height(VoxDimens.sizeNav)
            .voxShadow(VoxLibrisTheme.shadows.shadowFloat, shape)
            .clip(shape)
            .background(c.fillStrong)
            .padding(horizontal = VoxDimens.space2)
            .semantics { paneTitle = label }
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { NavBarButton(it) }
    }
}

@Composable
private fun NavBarButton(item: NavBarItem) {
    val c = VoxLibrisTheme.colors
    if (item.selected) {
        Surface(
            onClick = item.onClick,
            modifier = Modifier.height(52.dp).semantics { selected = true },
            shape = RoundedCornerShape(VoxDimens.radiusPill),
            color = c.surface,
            contentColor = c.ink,
        ) {
            Row(
                Modifier.padding(start = 14.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2),
            ) {
                Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(item.label, style = VoxLibrisTheme.typography.label)
            }
        }
    } else {
        Surface(
            onClick = item.onClick,
            modifier = Modifier.size(52.dp).semantics { contentDescription = item.label },
            shape = CircleShape,
            color = if (item.accent) c.accent else Color.Transparent,
            contentColor = if (item.accent) c.onAccent else c.onFillStrong,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(if (item.accent) 24.dp else 22.dp))
            }
        }
    }
}
