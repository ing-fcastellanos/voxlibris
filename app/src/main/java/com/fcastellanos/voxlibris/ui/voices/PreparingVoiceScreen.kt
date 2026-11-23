package com.fcastellanos.voxlibris.ui.voices

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.ui.components.Banner
import com.fcastellanos.voxlibris.ui.components.SettingsList
import com.fcastellanos.voxlibris.ui.components.VoiceAvatar
import com.fcastellanos.voxlibris.ui.components.VoxButton
import com.fcastellanos.voxlibris.ui.components.VoxButtonSize
import com.fcastellanos.voxlibris.ui.components.VoxIconButton
import com.fcastellanos.voxlibris.ui.components.voiceTint
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/** Las voces que decoran la cabecera: F1, M1, F3, M5 y F5. */
private val SAMPLE_VOICES = listOf(0, 5, 2, 9, 4)

/**
 * Preparando la voz (pantalla homónima del lienzo): aparece mientras el paquete de voces se descarga
 * e instala, con los MB, cuánto falta según la conexión y qué se gana. «Seguir en segundo plano» la
 * cierra y pide avisar con una notificación cuando esté lista. Cerrar también la minimiza: la
 * descarga sigue.
 */
@Composable
fun PreparingVoiceScreen(
    state: VoiceModelManager.State,
    installProgress: Float?,
    onMinimize: () -> Unit,
    onContinueInBackground: () -> Unit,
    onRetry: () -> Unit,
) {
    BackHandler(onBack = onMinimize)
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onContinueInBackground() }
    val continueInBackground = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onContinueInBackground()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = VoxDimens.space5, vertical = VoxDimens.space3),
    ) {
        VoxIconButton(R.drawable.ic_vx_close, stringResource(R.string.close), onMinimize)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = VoxDimens.space4),
            verticalArrangement = Arrangement.spacedBy(VoxDimens.space5),
        ) {
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy((-10).dp, Alignment.CenterHorizontally),
            ) {
                SAMPLE_VOICES.forEach { id ->
                    val voice = SupertonicModel.voice(id)
                    VoiceAvatar(voice.name.take(1), voiceTint(voice.id), size = 56.dp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
                Text(
                    stringResource(R.string.preparing_title),
                    style = t.displayXl,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Text(
                    stringResource(R.string.preparing_body),
                    style = t.body,
                    color = c.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (state) {
                is VoiceModelManager.State.Downloading -> {
                    val totalMb = (state.totalBytes / 1_048_576L).toInt().coerceAtLeast(1)
                    val receivedMb = (state.receivedBytes / 1_048_576L).toInt().coerceIn(0, totalMb)
                    Banner(
                        title = stringResource(R.string.preparing_downloading),
                        detail = stringResource(R.string.preparing_bytes, receivedMb, totalMb),
                        progress = state.progress,
                        footer = state.etaSeconds?.let { stringResource(R.string.preparing_eta, formatEta(it)) },
                    )
                }
                VoiceModelManager.State.Installing -> Banner(
                    title = stringResource(R.string.preparing_installing),
                    detail = installProgress?.let { stringResource(R.string.progress_percent, (it * 100).toInt()) },
                    progress = installProgress,
                )
                is VoiceModelManager.State.Failed -> Column(verticalArrangement = Arrangement.spacedBy(VoxDimens.space3)) {
                    Text(stringResource(R.string.preparing_failed, state.message), style = t.body, color = c.danger)
                    VoxButton(stringResource(R.string.retry), onClick = onRetry)
                }
                VoiceModelManager.State.NotInstalled, is VoiceModelManager.State.Ready -> Unit
            }
            SettingsList(onGround = true) {
                listOf(R.string.preparing_point_voices, R.string.preparing_point_offline, R.string.preparing_point_change).forEach { point ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = VoxDimens.space4, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(c.mint), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_vx_check), contentDescription = null, tint = c.onColor, modifier = Modifier.size(16.dp))
                        }
                        Text(stringResource(point), style = t.body, color = c.ink)
                    }
                }
            }
        }
        Spacer(Modifier.size(VoxDimens.space4))
        Column(verticalArrangement = Arrangement.spacedBy(VoxDimens.space2), horizontalAlignment = Alignment.CenterHorizontally) {
            VoxButton(stringResource(R.string.preparing_background), onClick = continueInBackground, size = VoxButtonSize.Block)
            Text(stringResource(R.string.preparing_notify_hint), style = t.caption, color = c.inkMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun formatEta(seconds: Long): String =
    if (seconds < 60) stringResource(R.string.preparing_eta_seconds, seconds.coerceAtLeast(1)) else stringResource(R.string.preparing_eta_minutes, ((seconds + 59) / 60).toInt())
