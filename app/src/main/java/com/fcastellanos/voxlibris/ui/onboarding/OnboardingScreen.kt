package com.fcastellanos.voxlibris.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.ui.components.ProgressBar
import com.fcastellanos.voxlibris.ui.components.StepCard
import com.fcastellanos.voxlibris.ui.components.VoxButton
import com.fcastellanos.voxlibris.ui.components.VoxButtonSize
import com.fcastellanos.voxlibris.ui.components.VoxButtonVariant
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import java.io.File

/**
 * Primer inicio, «Antes de empezar» en el lienzo: dos pasos que se hacen una sola vez, la
 * exención de batería (para que la voz siga con la pantalla apagada) y la descarga de las voces,
 * con el avance de la descarga y de la instalación.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    val batteryUnrestricted by viewModel.batteryUnrestricted.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val installProgress by viewModel.installProgress.collectAsStateWithLifecycle()

    // Al volver del diálogo de batería o de los ajustes del sistema, el paso pasa a «Listo».
    LifecycleResumeEffect(Unit) {
        viewModel.refreshBattery(context)
        onPauseOrDispose { }
    }

    OnboardingContent(
        batteryUnrestricted = batteryUnrestricted,
        voices = voices,
        installProgress = installProgress,
        onRequestBattery = { viewModel.requestBattery(context) },
        onOpenBatterySettings = { viewModel.openBatterySettings(context) },
        onDownloadVoices = viewModel::downloadVoices,
        onCancelDownload = viewModel::cancelDownload,
        onContinue = viewModel::finish,
    )
}

@Composable
private fun OnboardingContent(
    batteryUnrestricted: Boolean,
    voices: VoiceModelManager.State,
    installProgress: Float?,
    onRequestBattery: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onDownloadVoices: () -> Unit,
    onCancelDownload: () -> Unit,
    onContinue: () -> Unit,
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val voicesReady = voices is VoiceModelManager.State.Ready
    val allDone = batteryUnrestricted && voicesReady

    Column(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = VoxDimens.space5, end = VoxDimens.space5, top = VoxDimens.space8, bottom = VoxDimens.space4),
            verticalArrangement = Arrangement.spacedBy(VoxDimens.space4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
                Text(
                    stringResource(R.string.onboarding_title),
                    style = t.displayXl,
                    color = c.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.onboarding_intro), style = t.body, color = c.inkMuted)
            }

            // El paso que toca hacer es el primero sin terminar: su acción va en Primary y la de
            // los demás en Secondary. La batería es el primero, así que su acción siempre es Primary.
            StepCard(
                number = 1,
                title = stringResource(R.string.onboarding_battery_title),
                body = stringResource(R.string.onboarding_battery_body),
                doneLabel = if (batteryUnrestricted) stringResource(R.string.onboarding_battery_done) else null,
            ) {
                VoxButton(
                    text = stringResource(R.string.onboarding_battery_allow),
                    onClick = onRequestBattery,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.onboarding_battery_pending), style = t.caption, color = c.inkMuted)
                VoxButton(
                    text = stringResource(R.string.onboarding_battery_settings),
                    onClick = onOpenBatterySettings,
                    modifier = Modifier.fillMaxWidth(),
                    variant = VoxButtonVariant.Secondary,
                )
            }

            StepCard(
                number = 2,
                title = stringResource(R.string.onboarding_voices_title),
                body = stringResource(R.string.onboarding_voices_body, SupertonicModel.SIZE_MB),
                doneLabel = if (voicesReady) stringResource(R.string.onboarding_voices_done) else null,
            ) {
                VoicesStepActions(
                    state = voices,
                    installProgress = installProgress,
                    variant = if (batteryUnrestricted) VoxButtonVariant.Primary else VoxButtonVariant.Secondary,
                    onDownload = onDownloadVoices,
                    onCancel = onCancelDownload,
                )
            }
        }

        // «Continuar» queda fijo al pie: discreto mientras falta algo, principal cuando está todo listo.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = VoxDimens.space5, end = VoxDimens.space5, top = VoxDimens.space3, bottom = VoxDimens.space6),
            verticalArrangement = Arrangement.spacedBy(VoxDimens.space2),
        ) {
            VoxButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
                variant = if (allDone) VoxButtonVariant.Primary else VoxButtonVariant.OnGround,
                size = VoxButtonSize.Block,
            )
            if (!allDone) {
                Text(
                    text = stringResource(R.string.onboarding_later),
                    style = t.caption,
                    color = c.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Acciones del paso de voces según el estado del paquete: descargar, avance, error o reintento. */
@Composable
private fun ColumnScope.VoicesStepActions(
    state: VoiceModelManager.State,
    installProgress: Float?,
    variant: VoxButtonVariant,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = VoxLibrisTheme.colors
    when (state) {
        VoiceModelManager.State.NotInstalled -> VoxButton(
            text = stringResource(R.string.onboarding_voices_download),
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
            variant = variant,
        )
        is VoiceModelManager.State.Downloading -> {
            val totalMb = SupertonicModel.SIZE_MB
            val receivedMb = (state.progress * totalMb).toInt().coerceIn(0, totalMb)
            ProgressRow(
                progress = state.progress,
                title = stringResource(R.string.onboarding_voices_downloading),
                detail = stringResource(R.string.onboarding_voices_downloading_detail, receivedMb, totalMb),
            )
            VoxButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                modifier = Modifier.align(Alignment.Start),
                variant = VoxButtonVariant.Ghost,
            )
        }
        // Al principio de la instalación todavía no hay avance: la barra va indeterminada.
        VoiceModelManager.State.Installing -> ProgressRow(
            progress = installProgress,
            title = stringResource(R.string.onboarding_voices_installing),
            detail = installProgress?.let { stringResource(R.string.onboarding_voices_installing_detail, (it * 100).toInt()) },
        )
        is VoiceModelManager.State.Failed -> {
            Text(stringResource(R.string.onboarding_voices_failed, state.message), style = VoxLibrisTheme.typography.caption, color = c.danger)
            VoxButton(
                text = stringResource(R.string.onboarding_voices_retry),
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                variant = variant,
            )
        }
        is VoiceModelManager.State.Ready -> Unit
    }
}

/** Qué está pasando, el avance en texto y la barra en `tomato` (`vx-progress--audio`). */
@Composable
private fun ProgressRow(progress: Float?, title: String, detail: String?) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3)) {
            // Se anuncia el cambio de etapa, no cada megabyte.
            Text(
                title,
                style = t.label,
                color = c.ink,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (detail != null) Text(detail, style = t.caption, color = c.inkMuted)
        }
        ProgressBar(progress, fillColor = c.tomato)
    }
}

/* Vistas previas de las tres pantallas «Antes de empezar» del lienzo, claras y oscuras. */

@Composable
private fun OnboardingSample(darkTheme: Boolean, battery: Boolean, voices: VoiceModelManager.State) {
    VoxLibrisTheme(darkTheme = darkTheme) {
        OnboardingContent(
            batteryUnrestricted = battery,
            voices = voices,
            installProgress = null,
            onRequestBattery = {},
            onOpenBatterySettings = {},
            onDownloadVoices = {},
            onCancelDownload = {},
            onContinue = {},
        )
    }
}

@Preview(name = "Antes de empezar · claro", widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStartLightPreview() = OnboardingSample(false, battery = false, voices = VoiceModelManager.State.NotInstalled)

@Preview(name = "Antes de empezar · oscuro", widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingStartDarkPreview() = OnboardingSample(true, battery = false, voices = VoiceModelManager.State.NotInstalled)

@Preview(name = "Antes de empezar, descargando · claro", widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingDownloadLightPreview() = OnboardingSample(false, battery = true, voices = VoiceModelManager.State.Downloading(0.47f))

@Preview(name = "Antes de empezar, descargando · oscuro", widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingDownloadDarkPreview() = OnboardingSample(true, battery = true, voices = VoiceModelManager.State.Downloading(0.47f))

@Preview(name = "Antes de empezar, listo · claro", widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingDoneLightPreview() = OnboardingSample(false, battery = true, voices = VoiceModelManager.State.Ready(File("")))

@Preview(name = "Antes de empezar, listo · oscuro", widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingDoneDarkPreview() = OnboardingSample(true, battery = true, voices = VoiceModelManager.State.Ready(File("")))
