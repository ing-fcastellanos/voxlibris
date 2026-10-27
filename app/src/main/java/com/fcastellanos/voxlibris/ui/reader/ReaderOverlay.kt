package com.fcastellanos.voxlibris.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fcastellanos.voxlibris.VoxLibrisApp
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.tts.resolve
import com.fcastellanos.voxlibris.ui.voices.VoiceSettingsContent
import com.fcastellanos.voxlibris.ui.voices.VoicesViewModel
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcastellanos.voxlibris.R
import kotlin.math.roundToInt
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesEditor
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.preferences.toggle
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
fun ReaderOverlay(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val visible by viewModel.overlayVisible.collectAsStateWithLifecycle()
    val locator by viewModel.currentLocator.collectAsStateWithLifecycle()
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    var showToc by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showVoices by rememberSaveable { mutableStateOf(false) }

    val readAloud = viewModel.readAloud
    val ttsStatus by (readAloud?.status ?: remember { kotlinx.coroutines.flow.MutableStateFlow(ReadAloudController.Status.Idle) }).collectAsStateWithLifecycle()
    val modelState by (readAloud?.modelState ?: remember { kotlinx.coroutines.flow.MutableStateFlow(VoiceModelManager.State.NotInstalled) }).collectAsStateWithLifecycle()
    val ttsSettings = (readAloud?.preferences?.collectAsStateWithLifecycle()?.value ?: com.fcastellanos.voxlibris.tts.SherpaTtsPreferences()).resolve(null)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            TopAppBar(
                modifier = Modifier.consumeTaps(),
                title = { Text(viewModel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (readAloud != null && ttsStatus == ReadAloudController.Status.Idle) {
                        IconButton(onClick = viewModel::listen) {
                            Icon(painterResource(R.drawable.ic_headphones), contentDescription = stringResource(R.string.listen))
                        }
                    }
                    IconButton(onClick = { showToc = true }) {
                        Icon(painterResource(R.drawable.ic_toc), contentDescription = stringResource(R.string.toc))
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(painterResource(R.drawable.ic_text_size), contentDescription = stringResource(R.string.reading_settings))
                    }
                },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            if (readAloud == null || ttsStatus == ReadAloudController.Status.Idle) {
                ProgressBar(locator = locator, positions = positions, onSeek = viewModel::goToPosition)
            } else {
                TtsBar(
                    status = ttsStatus,
                    modelState = modelState,
                    voiceName = SupertonicModel.voice(ttsSettings.voiceId).name,
                    speed = ttsSettings.speed,
                    onPlayPause = readAloud::togglePlayPause,
                    onPrevious = { readAloud.previous() },
                    onNext = { readAloud.next() },
                    onStop = readAloud::stop,
                    onVoices = { showVoices = true },
                )
            }
        }
    }

    if (readAloud != null && ttsStatus == ReadAloudController.Status.NeedsModel &&
        modelState !is VoiceModelManager.State.Downloading && modelState !is VoiceModelManager.State.Installing
    ) {
        AlertDialog(
            onDismissRequest = readAloud::stop,
            title = { Text(stringResource(R.string.tts_need_model_title)) },
            text = { Text(stringResource(R.string.tts_need_model_body, SupertonicModel.SIZE_MB)) },
            confirmButton = { TextButton(onClick = readAloud::downloadVoices) { Text(stringResource(R.string.download)) } },
            dismissButton = { TextButton(onClick = readAloud::stop) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showVoices) {
        val container = (LocalContext.current.applicationContext as VoxLibrisApp).container
        val sample = stringResource(R.string.sample_sentence)
        val voicesViewModel: VoicesViewModel = viewModel(factory = VoicesViewModel.factory(container, sample))
        ModalBottomSheet(onDismissRequest = { showVoices = false }) {
            VoiceSettingsContent(viewModel = voicesViewModel, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
        }
    }

    if (showToc) {
        TocSheet(
            entries = viewModel.toc,
            current = locator,
            onSelect = { link ->
                viewModel.go(link)
                showToc = false
            },
            onDismiss = { showToc = false },
        )
    }
    if (showSettings) {
        SettingsSheet(
            preferences = preferences,
            createEditor = viewModel::createPreferencesEditor,
            onChange = viewModel::updatePreferences,
            onDismiss = { showSettings = false },
        )
    }
}

/** Evita que los toques sobre las barras lleguen al navegador y pasen de página. */
private fun Modifier.consumeTaps(): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = {},
)

@Composable
private fun TtsBar(
    status: ReadAloudController.Status,
    modelState: VoiceModelManager.State,
    voiceName: String,
    speed: Double,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onVoices: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .consumeTaps(),
        tonalElevation = 3.dp,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            val downloading = status == ReadAloudController.Status.NeedsModel &&
                (modelState is VoiceModelManager.State.Downloading || modelState is VoiceModelManager.State.Installing)
            if (downloading) {
                val progress = (modelState as? VoiceModelManager.State.Downloading)?.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.downloading_voices, (progress * 100).toInt()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.installing_voices), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onStop) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.stop_listening)) }
                    IconButton(onClick = onPrevious) { Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = stringResource(R.string.previous_sentence)) }
                    FilledIconButton(onClick = onPlayPause) {
                        val playing = status == ReadAloudController.Status.Playing || status == ReadAloudController.Status.Preparing
                        Icon(
                            painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                        )
                    }
                    IconButton(onClick = onNext) { Icon(painterResource(R.drawable.ic_skip_next), contentDescription = stringResource(R.string.next_sentence)) }
                    val locale = LocalConfiguration.current.locales[0]
                    TextButton(onClick = onVoices) {
                        Text("$voiceName · ${String.format(locale, "%.1f", speed)}×", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val statusText = when (status) {
                    ReadAloudController.Status.Preparing -> stringResource(R.string.tts_preparing)
                    ReadAloudController.Status.Playing -> stringResource(R.string.tts_playing)
                    ReadAloudController.Status.Paused -> stringResource(R.string.tts_paused)
                    ReadAloudController.Status.Ended -> stringResource(R.string.tts_ended)
                    is ReadAloudController.Status.Error -> stringResource(R.string.tts_error, status.message)
                    else -> ""
                }
                if (statusText.isNotEmpty()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status is ReadAloudController.Status.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(locator: Locator?, positions: List<Locator>, onSeek: (Int) -> Unit) {
    val currentIndex = ((locator?.locations?.position ?: 1) - 1).coerceAtLeast(0)
    var sliderValue by remember(currentIndex) { mutableFloatStateOf(currentIndex.toFloat()) }
    val percent = locator?.locations?.totalProgression?.let { (it * 100).roundToInt() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .consumeTaps(),
        tonalElevation = 3.dp,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (positions.size > 1) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSeek(sliderValue.roundToInt()) },
                    valueRange = 0f..(positions.size - 1).toFloat(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (positions.isNotEmpty()) {
                        stringResource(R.string.position_of, sliderValue.roundToInt() + 1, positions.size)
                    } else {
                        stringResource(R.string.reading_position)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                percent?.let {
                    Text(stringResource(R.string.progress_percent, it), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    entries: List<FlatEntry<Link>>,
    current: Locator?,
    onSelect: (Link) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentHref = current?.href?.toString()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.toc),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.toc_empty),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.navigationBarsPadding()) {
                items(entries) { entry ->
                    val link = entry.item
                    val selected = currentHref != null &&
                        link.url().toString().substringBefore('#') == currentHref
                    ListItem(
                        headlineContent = {
                            Text(
                                text = link.title ?: link.href.toString(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier
                            .clickable { onSelect(link) }
                            .padding(start = (entry.depth * 16).dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
private fun SettingsSheet(
    preferences: EpubPreferences,
    createEditor: () -> EpubPreferencesEditor?,
    onChange: (EpubPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val editor = remember(preferences) { createEditor() } ?: return
    fun commit() = onChange(editor.preferences)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.reading_settings), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            val themes = listOf(
                Theme.LIGHT to R.string.theme_light,
                Theme.SEPIA to R.string.theme_sepia,
                Theme.DARK to R.string.theme_dark,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                themes.forEachIndexed { index, (theme, label) ->
                    SegmentedButton(
                        selected = editor.theme.effectiveValue == theme,
                        onClick = {
                            editor.theme.set(theme)
                            commit()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size),
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            StepperRow(
                label = stringResource(R.string.font_size),
                value = editor.fontSize.formatValue(editor.fontSize.effectiveValue),
                onDecrease = { editor.fontSize.decrement(); commit() },
                onIncrease = { editor.fontSize.increment(); commit() },
            )

            SwitchRow(
                label = stringResource(R.string.scroll_mode),
                checked = editor.scroll.effectiveValue,
                onToggle = { editor.scroll.toggle(); commit() },
            )
            SwitchRow(
                label = stringResource(R.string.publisher_styles),
                checked = editor.publisherStyles.effectiveValue,
                onToggle = { editor.publisherStyles.toggle(); commit() },
            )
            if (editor.lineHeight.isEffective) {
                StepperRow(
                    label = stringResource(R.string.line_height),
                    value = editor.lineHeight.formatValue(editor.lineHeight.effectiveValue),
                    onDecrease = { editor.lineHeight.decrement(); commit() },
                    onIncrease = { editor.lineHeight.increment(); commit() },
                )
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val decrease = stringResource(R.string.decrease)
        val increase = stringResource(R.string.increase)
        Text(label, Modifier.weight(1f))
        IconButton(onClick = onDecrease, modifier = Modifier.semantics { contentDescription = decrease }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onIncrease, modifier = Modifier.semantics { contentDescription = increase }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
