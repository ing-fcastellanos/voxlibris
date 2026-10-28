package com.fcastellanos.voxlibris.ui.voices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.tts.SherpaTtsDefaults
import com.fcastellanos.voxlibris.tts.SherpaTtsQuality
import com.fcastellanos.voxlibris.tts.SherpaVoice
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.VoiceModelManager

/** Contenido reutilizable: se muestra como pantalla desde la biblioteca y como hoja inferior desde el lector. */
@Composable
fun VoiceSettingsContent(
    viewModel: VoicesViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val previewing by viewModel.previewing.collectAsStateWithLifecycle()
    val ready = modelState is VoiceModelManager.State.Ready

    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item {
            ModelCard(
                state = modelState,
                onInstall = viewModel::install,
                onCancel = viewModel::cancelDownload,
                onDelete = viewModel::deleteModel,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.voices_title), style = MaterialTheme.typography.titleMedium)
        }
        items(viewModel.voices, key = { it.id }) { voice ->
            VoiceRow(
                voice = voice,
                selected = settings.voiceId == voice.id,
                enabled = ready,
                previewing = previewing == voice.id,
                previewEnabled = ready && previewing == null,
                onSelect = { viewModel.selectVoice(voice.id) },
                onPreview = { viewModel.preview(voice) },
            )
        }
        item {
            val locale = LocalConfiguration.current.locales[0]
            // Se aplica al soltar: cada cambio reinicia la frase en curso en el lector.
            var sliderSpeed by remember(settings.speed) { mutableFloatStateOf(settings.speed.toFloat()) }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.speed_label, String.format(locale, "%.1f", sliderSpeed)),
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = sliderSpeed,
                onValueChange = { sliderSpeed = it },
                onValueChangeFinished = { viewModel.setSpeed(sliderSpeed.toDouble()) },
                valueRange = SherpaTtsDefaults.SPEED_RANGE.start.toFloat()..SherpaTtsDefaults.SPEED_RANGE.endInclusive.toFloat(),
                steps = 11,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.quality), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            QualitySelector(settings.quality, viewModel::setQuality)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.quality_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelCard(
    state: VoiceModelManager.State,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when (state) {
                VoiceModelManager.State.NotInstalled -> {
                    Text(stringResource(R.string.model_not_installed, SupertonicModel.SIZE_MB))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onInstall) { Text(stringResource(R.string.download_voices)) }
                }
                is VoiceModelManager.State.Downloading -> {
                    Text(stringResource(R.string.downloading_voices, (state.progress * 100).toInt()))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
                VoiceModelManager.State.Installing -> {
                    Text(stringResource(R.string.installing_voices))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is VoiceModelManager.State.Ready -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.voices_ready))
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.delete_voices)) }
                    }
                }
                is VoiceModelManager.State.Failed -> {
                    Text(stringResource(R.string.tts_error, state.message), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onInstall) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    }
}

@Composable
private fun VoiceRow(
    voice: SherpaVoice,
    selected: Boolean,
    enabled: Boolean,
    previewing: Boolean,
    previewEnabled: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(voice.name) },
        supportingContent = { Text(stringResource(if (voice.isFemale) R.string.voice_female else R.string.voice_male)) },
        leadingContent = { RadioButton(selected = selected, onClick = onSelect, enabled = enabled) },
        trailingContent = {
            IconButton(onClick = onPreview, enabled = previewEnabled) {
                if (previewing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = stringResource(R.string.voice_sample))
                }
            }
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onSelect),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualitySelector(quality: SherpaTtsQuality, onChange: (SherpaTtsQuality) -> Unit) {
    val options = listOf(
        SherpaTtsQuality.AUTO to R.string.quality_auto,
        SherpaTtsQuality.FAST to R.string.quality_fast,
        SherpaTtsQuality.HIGH to R.string.quality_high,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = quality == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(stringResource(label)) },
            )
        }
    }
}
