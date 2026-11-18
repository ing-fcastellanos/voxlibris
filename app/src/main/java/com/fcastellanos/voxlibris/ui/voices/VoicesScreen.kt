package com.fcastellanos.voxlibris.ui.voices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.components.Tag
import com.fcastellanos.voxlibris.ui.components.VoxIconButton
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Lectores, desde la barra de la biblioteca: la misma elección de voz que en el lector, a pantalla completa. */
@Composable
fun VoicesScreen(viewModel: VoicesViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val c = VoxLibrisTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = VoxDimens.space5, end = VoxDimens.space5, top = VoxDimens.space3),
        verticalArrangement = Arrangement.spacedBy(VoxDimens.space4),
    ) {
        VoxIconButton(R.drawable.ic_vx_back, stringResource(R.string.back), onBack)
        Column(verticalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
            Text(
                stringResource(R.string.voices_title),
                style = VoxLibrisTheme.typography.displayXl,
                color = c.ink,
                modifier = Modifier.semantics { heading() },
            )
            Tag(stringResource(R.string.voices_tag))
        }
        VoiceSettingsContent(
            viewModel = viewModel,
            onGround = true,
            modifier = Modifier.weight(1f).padding(bottom = VoxDimens.space4),
        )
    }
}
