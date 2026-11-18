package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/**
 * Tarjeta de un paso de «Antes de empezar» (`vx-step`): número, título, explicación y, debajo, las
 * acciones del paso. Con [doneLabel] el paso está hecho (`vx-step--done`): el número pasa a un
 * tilde sobre `mint`, la explicación y las acciones se ocultan y queda esa línea, como «Listo:
 * voces instaladas».
 */
@Composable
fun StepCard(
    number: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    doneLabel: String? = null,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val done = doneLabel != null
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoxDimens.radiusXl))
            .background(c.surface)
            .padding(VoxDimens.space5),
        verticalArrangement = Arrangement.spacedBy(VoxDimens.space3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
        ) {
            Box(
                Modifier
                    .size(STEP_BADGE)
                    .clip(CircleShape)
                    .background(if (done) c.mint else c.fillStrong)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(painterResource(R.drawable.ic_vx_check), contentDescription = null, tint = c.onColor, modifier = Modifier.size(16.dp))
                } else {
                    Text(number.toString(), style = t.label, color = c.onFillStrong)
                }
            }
            Text(title, style = t.title, color = c.ink, modifier = Modifier.weight(1f).semantics { heading() })
        }
        if (doneLabel != null) {
            Text(doneLabel, style = t.bodyStrong, color = c.ink)
        } else {
            Text(body, style = t.body, color = c.inkMuted)
            actions()
        }
    }
}

/** Diámetro del número del paso, el mismo que el ícono de `vx-snack`. */
private val STEP_BADGE = 28.dp
