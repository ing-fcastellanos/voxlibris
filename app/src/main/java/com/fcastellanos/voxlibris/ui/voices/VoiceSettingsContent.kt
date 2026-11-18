package com.fcastellanos.voxlibris.ui.voices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.tts.SherpaTtsDefaults
import com.fcastellanos.voxlibris.tts.SherpaTtsQuality
import com.fcastellanos.voxlibris.tts.SherpaVoice
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.ui.components.PlayButton
import com.fcastellanos.voxlibris.ui.components.PlayButtonSize
import com.fcastellanos.voxlibris.ui.components.ProgressBar
import com.fcastellanos.voxlibris.ui.components.SectionLabel
import com.fcastellanos.voxlibris.ui.components.SettingsList
import com.fcastellanos.voxlibris.ui.components.SettingsRow
import com.fcastellanos.voxlibris.ui.components.VoiceAvatar
import com.fcastellanos.voxlibris.ui.components.VoxButton
import com.fcastellanos.voxlibris.ui.components.VoxButtonSize
import com.fcastellanos.voxlibris.ui.components.VoxButtonVariant
import com.fcastellanos.voxlibris.ui.components.VoxChip
import com.fcastellanos.voxlibris.ui.components.VoxChipGroup
import com.fcastellanos.voxlibris.ui.components.VoxSlider
import com.fcastellanos.voxlibris.ui.components.colors
import com.fcastellanos.voxlibris.ui.components.formatSpeed
import com.fcastellanos.voxlibris.ui.components.voiceTint
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import kotlin.math.roundToInt

/**
 * Elección del lector (pantalla «Elegir voz» del lienzo): el paquete de voces, las diez voces en dos
 * filas de colores, una muestra, la velocidad y la calidad. Se muestra como pantalla desde la
 * biblioteca ([onGround] verdadero, sobre `ground`) y como hoja inferior desde el lector.
 */
@Composable
fun VoiceSettingsContent(
    viewModel: VoicesViewModel,
    modifier: Modifier = Modifier,
    onGround: Boolean = false,
) {
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val previewing by viewModel.previewing.collectAsStateWithLifecycle()
    val ready = modelState is VoiceModelManager.State.Ready
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (!ready) {
            ModelCard(
                state = modelState,
                onGround = onGround,
                onInstall = viewModel::install,
                onCancel = viewModel::cancelDownload,
            )
        }

        Column(
            Modifier.alpha(if (ready) 1f else 0.4f),
            verticalArrangement = Arrangement.spacedBy(VoxDimens.space3),
        ) {
            val (female, male) = viewModel.voices.partition { it.isFemale }
            SectionLabel(stringResource(R.string.voices_female_group))
            VoiceRow(female, settings.voiceId, ready, viewModel::selectVoice)
            SectionLabel(stringResource(R.string.voices_male_group))
            VoiceRow(male, settings.voiceId, ready, viewModel::selectVoice)
        }

        val selected = SupertonicModel.voice(settings.voiceId)
        SampleRow(
            voice = selected,
            onGround = onGround,
            previewing = previewing == selected.id,
            enabled = ready && previewing == null,
            onPreview = { viewModel.preview(selected) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
            // Se aplica al soltar: cada cambio reinicia la frase en curso en el lector.
            var sliderSpeed by remember(settings.speed) { mutableFloatStateOf(settings.speed.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.reader_speed), Modifier.weight(1f))
                Text(formatSpeed((sliderSpeed * 10).roundToInt() / 10.0), style = t.label, color = c.ink)
            }
            VoxSlider(
                value = sliderSpeed,
                onValueChange = { sliderSpeed = it },
                onValueChangeFinished = { viewModel.setSpeed(sliderSpeed.toDouble()) },
                valueRange = SherpaTtsDefaults.SPEED_RANGE.start.toFloat()..SherpaTtsDefaults.SPEED_RANGE.endInclusive.toFloat(),
                steps = ((SherpaTtsDefaults.SPEED_RANGE.endInclusive - SherpaTtsDefaults.SPEED_RANGE.start) / SherpaTtsDefaults.SPEED_STEP).roundToInt() - 1,
                stateDescription = formatSpeed((sliderSpeed * 10).roundToInt() / 10.0),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
            SectionLabel(stringResource(R.string.quality))
            VoxChipGroup {
                listOf(
                    SherpaTtsQuality.AUTO to R.string.quality_auto,
                    SherpaTtsQuality.FAST to R.string.quality_fast,
                    SherpaTtsQuality.HIGH to R.string.quality_high,
                ).forEach { (value, label) ->
                    VoxChip(stringResource(label), selected = settings.quality == value, onClick = { viewModel.setQuality(value) }, onGround = onGround)
                }
            }
            Text(stringResource(R.string.quality_hint), style = t.caption, color = c.inkMuted)
        }

        if (ready) {
            SettingsList(onGround = onGround) {
                SettingsRow(
                    title = stringResource(R.string.voices_ready),
                    subtitle = stringResource(R.string.voices_installed_subtitle, SupertonicModel.SIZE_MB),
                ) {
                    VoxButton(
                        stringResource(R.string.delete_voices),
                        onClick = viewModel::deleteModel,
                        variant = VoxButtonVariant.Ghost,
                        size = VoxButtonSize.Small,
                    )
                }
            }
        }
    }
}

/** Una fila de cinco voces: el círculo de color con la inicial y el nombre debajo. */
@Composable
private fun VoiceRow(voices: List<SherpaVoice>, selectedId: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val c = VoxLibrisTheme.colors
    Row(Modifier.fillMaxWidth().selectableGroup()) {
        voices.forEach { voice ->
            val selected = voice.id == selectedId
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(VoxDimens.radiusM))
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(voice.id) })
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .border(2.dp, if (selected) c.ink else Color.Transparent, CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VoiceAvatar(voice.name.take(1), voiceTint(voice.id))
                }
                Text(
                    voice.name,
                    style = if (selected) VoxLibrisTheme.typography.label else VoxLibrisTheme.typography.caption,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Muestra del lector elegido, con su color. */
@Composable
private fun SampleRow(voice: SherpaVoice, onGround: Boolean, previewing: Boolean, enabled: Boolean, onPreview: () -> Unit) {
    val c = VoxLibrisTheme.colors
    val tint = voiceTint(voice.id).colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoxDimens.radiusXl))
            .background(if (onGround) c.surface else c.surfaceMuted)
            .padding(VoxDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
    ) {
        if (previewing) {
            Box(Modifier.size(VoxDimens.sizeTouch), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = c.fillStrong, trackColor = c.track, strokeWidth = 3.dp)
            }
        } else {
            PlayButton(
                playing = false,
                onClick = if (enabled) onPreview else null,
                contentDescription = stringResource(R.string.voices_listen_sample, voice.name),
                size = PlayButtonSize.Small,
                containerColor = tint.container,
                contentColor = tint.content,
                modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.voices_sample_of, voice.name), style = VoxLibrisTheme.typography.label, color = c.ink)
            Text(
                stringResource(if (voice.isFemale) R.string.voice_female else R.string.voice_male),
                style = VoxLibrisTheme.typography.caption,
                color = c.inkMuted,
            )
        }
    }
}

/** Estado del paquete de voces mientras no está instalado: descargar, el avance o el error. */
@Composable
private fun ModelCard(
    state: VoiceModelManager.State,
    onGround: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    CardBox(onGround) {
        when (state) {
            VoiceModelManager.State.NotInstalled -> {
                Text(stringResource(R.string.model_not_installed, SupertonicModel.SIZE_MB), style = t.body, color = c.ink)
                VoxButton(stringResource(R.string.download_voices), onClick = onInstall)
            }
            is VoiceModelManager.State.Downloading -> {
                Text(stringResource(R.string.downloading_voices, (state.progress * 100).toInt()), style = t.label, color = c.ink)
                ProgressBar(state.progress, fillColor = c.tomato)
                VoxButton(stringResource(R.string.cancel), onClick = onCancel, variant = VoxButtonVariant.Secondary, size = VoxButtonSize.Small)
            }
            VoiceModelManager.State.Installing -> {
                Text(stringResource(R.string.installing_voices), style = t.label, color = c.ink)
                ProgressBar(null, fillColor = c.tomato)
            }
            is VoiceModelManager.State.Failed -> {
                Text(stringResource(R.string.tts_error, state.message), style = t.body, color = c.danger)
                VoxButton(stringResource(R.string.retry), onClick = onInstall)
            }
            is VoiceModelManager.State.Ready -> Spacer(Modifier)
        }
    }
}

@Composable
private fun CardBox(onGround: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val c = VoxLibrisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoxDimens.radiusXl))
            .background(if (onGround) c.surface else c.surfaceMuted)
            .padding(VoxDimens.space4),
        verticalArrangement = Arrangement.spacedBy(VoxDimens.space3),
        content = content,
    )
}
