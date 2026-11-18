package com.fcastellanos.voxlibris.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.ui.components.ProgressBar
import com.fcastellanos.voxlibris.ui.components.VoxButton
import com.fcastellanos.voxlibris.ui.components.VoxButtonVariant
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/**
 * Primer inicio: dos pasos que se hacen una sola vez, la exención de batería (para que la voz siga
 * con la pantalla apagada) y la descarga de las voces, con el avance de la descarga y la instalación.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val batteryUnrestricted by viewModel.batteryUnrestricted.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshBattery(context)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VoxDimens.space5, vertical = VoxDimens.space6),
        verticalArrangement = Arrangement.spacedBy(VoxDimens.space4),
    ) {
        Text(stringResource(R.string.onboarding_title), style = t.displayM, color = c.ink)
        Text(stringResource(R.string.onboarding_intro), style = t.body, color = c.inkMuted)

        StepCard(
            number = 1,
            done = batteryUnrestricted,
            title = stringResource(R.string.onboarding_battery_title),
            body = stringResource(R.string.onboarding_battery_body),
        ) {
            if (batteryUnrestricted) {
                DoneRow(stringResource(R.string.onboarding_battery_done))
            } else {
                VoxButton(
                    text = stringResource(R.string.onboarding_battery_allow),
                    onClick = { viewModel.requestBattery(context) },
                    modifier = Modifier.fillMaxWidth(),
                )
                VoxButton(
                    text = stringResource(R.string.onboarding_battery_settings),
                    onClick = { viewModel.openBatterySettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = VoxButtonVariant.Secondary,
                )
                Text(stringResource(R.string.onboarding_battery_pending), style = t.caption, color = c.inkMuted)
            }
        }

        val totalMb = (SupertonicModel.SIZE_BYTES / 1_000_000).toInt()
        StepCard(
            number = 2,
            done = voices is VoiceModelManager.State.Ready,
            title = stringResource(R.string.onboarding_voices_title),
            body = stringResource(R.string.onboarding_voices_body, totalMb),
        ) {
            when (val state = voices) {
                is VoiceModelManager.State.Ready -> DoneRow(stringResource(R.string.onboarding_voices_done))
                is VoiceModelManager.State.Downloading -> {
                    val receivedMb = (state.progress * totalMb).toInt().coerceIn(0, totalMb)
                    ProgressRow(
                        progress = state.progress,
                        title = stringResource(R.string.onboarding_voices_downloading),
                        detail = stringResource(R.string.onboarding_voices_downloading_detail, receivedMb, totalMb),
                    )
                    VoxButton(
                        text = stringResource(R.string.cancel),
                        onClick = viewModel::cancelDownload,
                        variant = VoxButtonVariant.Ghost,
                    )
                }
                VoiceModelManager.State.Installing -> ProgressRow(
                    progress = installProgress,
                    title = stringResource(R.string.onboarding_voices_installing),
                    detail = installProgress?.let { "${(it * 100).toInt()} %" },
                )
                is VoiceModelManager.State.Failed -> {
                    Text(stringResource(R.string.onboarding_voices_failed, state.message), style = t.caption, color = c.danger)
                    VoxButton(
                        text = stringResource(R.string.onboarding_voices_retry),
                        onClick = viewModel::downloadVoices,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                VoiceModelManager.State.NotInstalled -> VoxButton(
                    text = stringResource(R.string.onboarding_voices_download),
                    onClick = viewModel::downloadVoices,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(VoxDimens.space2))
        VoxButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = viewModel::finish,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.onboarding_later),
            style = t.caption,
            color = c.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepCard(
    number: Int,
    done: Boolean,
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoxDimens.radiusXl))
            .background(c.surface)
            .padding(VoxDimens.space5),
        verticalArrangement = Arrangement.spacedBy(VoxDimens.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3)) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (done) c.mint else c.fillStrong),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(painterResource(R.drawable.ic_vx_check), contentDescription = null, tint = c.mintDeep, modifier = Modifier.size(16.dp))
                } else {
                    Text(number.toString(), style = t.label, color = c.onFillStrong)
                }
            }
            Text(title, style = t.title, color = c.ink)
        }
        Text(body, style = t.body, color = c.inkMuted)
        content()
    }
}

@Composable
private fun DoneRow(text: String) {
    val c = VoxLibrisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
        Icon(painterResource(R.drawable.ic_vx_check), contentDescription = null, tint = c.mintDeep, modifier = Modifier.size(18.dp))
        Text(text, style = VoxLibrisTheme.typography.bodyStrong, color = c.ink)
    }
}

@Composable
private fun ProgressRow(progress: Float?, title: String, detail: String?) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3)) {
            Text(title, style = t.label, color = c.ink, modifier = Modifier.weight(1f))
            if (detail != null) Text(detail, style = t.caption, color = c.inkMuted)
        }
        ProgressBar(progress, fillColor = c.tomato)
    }
}
