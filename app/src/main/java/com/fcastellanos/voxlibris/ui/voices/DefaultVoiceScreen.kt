package com.fcastellanos.voxlibris.ui.voices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.components.VoxIconButton
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/**
 * Voz predeterminada (pantalla «Voz predeterminada» del lienzo), desde Ajustes: el modo de lectura
 * y las voces que siguen todos los libros sin ajustes propios. La velocidad y la calidad son
 * generales y quedan en Ajustes.
 */
@Composable
fun DefaultVoiceScreen(viewModel: VoicesViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    Column(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = VoxDimens.space5, end = VoxDimens.space5, top = VoxDimens.space3),
        verticalArrangement = Arrangement.spacedBy(VoxDimens.space4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3)) {
            VoxIconButton(R.drawable.ic_vx_back, stringResource(R.string.back), onBack)
            Text(
                stringResource(R.string.settings_default_voice),
                style = t.displayL,
                color = c.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            stringResource(R.string.voices_defaults_hint),
            style = t.body,
            color = c.inkMuted,
            modifier = Modifier.padding(horizontal = VoxDimens.space1),
        )
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .clip(RoundedCornerShape(VoxDimens.radiusXl))
                .background(c.surface)
                .padding(VoxDimens.space4),
        ) {
            VoiceSettingsContent(
                viewModel = viewModel,
                showGeneral = false,
                modifier = Modifier.padding(bottom = VoxDimens.space1),
            )
        }
    }
}
