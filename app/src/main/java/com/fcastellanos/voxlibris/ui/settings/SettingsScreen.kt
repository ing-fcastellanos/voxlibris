package com.fcastellanos.voxlibris.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.settings.ThemeMode
import com.fcastellanos.voxlibris.tts.ReadingMode
import com.fcastellanos.voxlibris.tts.SherpaTtsDefaults
import com.fcastellanos.voxlibris.tts.SherpaTtsQuality
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.resolve
import com.fcastellanos.voxlibris.tts.voiceChoice
import com.fcastellanos.voxlibris.ui.MainNavBar
import com.fcastellanos.voxlibris.ui.MainSection
import com.fcastellanos.voxlibris.ui.components.SectionLabel
import com.fcastellanos.voxlibris.ui.components.SettingsChevron
import com.fcastellanos.voxlibris.ui.components.SettingsDivider
import com.fcastellanos.voxlibris.ui.components.SettingsList
import com.fcastellanos.voxlibris.ui.components.SettingsRow
import com.fcastellanos.voxlibris.ui.components.SettingsSwitchRow
import com.fcastellanos.voxlibris.ui.components.SettingsValue
import com.fcastellanos.voxlibris.ui.components.VoiceAvatar
import com.fcastellanos.voxlibris.ui.components.VoxButton
import com.fcastellanos.voxlibris.ui.components.VoxButtonSize
import com.fcastellanos.voxlibris.ui.components.VoxButtonVariant
import com.fcastellanos.voxlibris.ui.components.VoxDialog
import com.fcastellanos.voxlibris.ui.components.VoxDropdownMenu
import com.fcastellanos.voxlibris.ui.components.VoxMenuItem
import com.fcastellanos.voxlibris.ui.components.formatSpeed
import com.fcastellanos.voxlibris.ui.components.voiceTint
import com.fcastellanos.voxlibris.ui.reader.AppearanceSheet
import com.fcastellanos.voxlibris.ui.reader.ReaderPaper
import com.fcastellanos.voxlibris.ui.reader.fontFamilyLabel
import com.fcastellanos.voxlibris.ui.reader.fontSizePercent
import com.fcastellanos.voxlibris.ui.reader.paper
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import com.fcastellanos.voxlibris.util.Sizes
import kotlin.math.abs

private const val REPO_URL = "https://github.com/ing-fcastellanos/voxlibris"

/**
 * Ajustes (pantalla «Ajustes» del lienzo): voz predeterminada, velocidad, calidad y silencios;
 * apariencia del texto y resaltado de la frase que suena; tema, almacenamiento y acerca de.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNowPlaying: () -> Unit,
    onAddBook: () -> Unit,
    onOpenDefaultVoice: () -> Unit,
    onOpenStorage: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val tts by viewModel.ttsPreferences.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val highlightSpoken by viewModel.highlightSpokenSentence.collectAsStateWithLifecycle()
    val reading by viewModel.reading.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.refreshStorage() }

    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val settings = tts.resolve(null)
    val choice = tts.voiceChoice()
    val narrator = SupertonicModel.voice(choice.narratorVoiceId)
    val dialogue = SupertonicModel.voice(choice.dialogueVoiceId)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.ground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = VoxDimens.space5,
                    end = VoxDimens.space5,
                    top = topInset + VoxDimens.space6,
                    bottom = bottomInset + VoxDimens.space6 + VoxDimens.sizeNav + VoxDimens.space5,
                ),
            verticalArrangement = Arrangement.spacedBy(VoxDimens.space2),
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = t.displayXl,
                color = c.ink,
                modifier = Modifier.padding(bottom = VoxDimens.space2).semantics { heading() },
            )

            SectionLabel(stringResource(R.string.settings_section_voice))
            SettingsList(onGround = true) {
                SettingsRow(
                    title = stringResource(R.string.settings_default_voice),
                    subtitle = if (choice.mode == ReadingMode.NARRATOR_DIALOGUE) {
                        stringResource(R.string.settings_default_voice_dialogue, narrator.name, dialogue.name)
                    } else {
                        stringResource(R.string.settings_default_voice_single, narrator.name)
                    },
                    onClick = onOpenDefaultVoice,
                    leading = { VoiceAvatar(narrator.name.take(1), voiceTint(narrator.id), size = 32.dp) },
                ) { SettingsChevron() }
                SettingsDivider()
                SpeedRow(speed = settings.speed, onSelect = viewModel::setSpeed)
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_high_quality),
                    subtitle = stringResource(R.string.settings_high_quality_hint),
                    checked = settings.quality == SherpaTtsQuality.HIGH,
                    onCheckedChange = viewModel::setHighQuality,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_trim_silence),
                    subtitle = stringResource(R.string.settings_trim_silence_hint),
                    checked = settings.trimSilence,
                    onCheckedChange = viewModel::setTrimSilence,
                )
            }

            SectionLabel(stringResource(R.string.settings_section_reading), Modifier.padding(top = VoxDimens.space2))
            SettingsList(onGround = true) {
                val paperLabel = stringResource(
                    when (reading.paper()) {
                        ReaderPaper.Warm -> R.string.settings_paper_warm
                        ReaderPaper.White -> R.string.settings_paper_white
                        ReaderPaper.Dark -> R.string.settings_paper_dark
                    },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_text_appearance),
                    subtitle = stringResource(
                        R.string.settings_text_appearance_summary,
                        reading.fontFamilyLabel(),
                        stringResource(R.string.progress_percent, reading.fontSizePercent()),
                        paperLabel,
                    ),
                    onClick = { showAppearance = true },
                ) { SettingsChevron() }
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_highlight_spoken),
                    checked = highlightSpoken,
                    onCheckedChange = viewModel::setHighlightSpokenSentence,
                )
            }

            SectionLabel(stringResource(R.string.settings_section_app), Modifier.padding(top = VoxDimens.space2))
            SettingsList(onGround = true) {
                ThemeRow(mode = themeMode, onSelect = viewModel::setThemeMode)
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_storage),
                    onClick = onOpenStorage,
                ) {
                    storage?.let { SettingsValue(stringResource(R.string.megabytes, Sizes.megabytes(it.totalBytes))) }
                    SettingsChevron()
                }
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_about),
                    onClick = { showAbout = true },
                ) {
                    SettingsValue(stringResource(R.string.settings_version, viewModel.versionName))
                    SettingsChevron()
                }
            }
        }

        MainNavBar(
            current = MainSection.SETTINGS,
            onHome = onBack,
            onNowPlaying = onNowPlaying,
            onAddBook = onAddBook,
            onSettings = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = VoxDimens.space5, end = VoxDimens.space5, bottom = VoxDimens.space6),
        )
    }

    if (showAppearance) {
        AppearanceSheet(preferences = reading, onChange = viewModel::updateReading, onDismiss = { showAppearance = false })
    }
    if (showAbout) {
        AboutDialog(versionName = viewModel.versionName, onDismiss = { showAbout = false })
    }
}

/** Velocidad general de la voz, con el mismo menú desplegable que el reproductor. */
@Composable
private fun SpeedRow(speed: Double, onSelect: (Double) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsRow(
            title = stringResource(R.string.reader_speed),
            onClick = { expanded = true },
        ) {
            SettingsValue(formatSpeed(speed))
            SettingsChevron(down = true)
        }
        VoxDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, label = stringResource(R.string.reader_speed)) {
            SherpaTtsDefaults.SPEED_PRESETS.forEach { value ->
                VoxMenuItem(
                    text = formatSpeed(value),
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    checked = abs(value - speed) < 0.05,
                )
            }
        }
    }
}

/** Tema de la app: según el sistema, claro u oscuro. */
@Composable
private fun ThemeRow(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        ThemeMode.SYSTEM to R.string.settings_theme_system,
        ThemeMode.LIGHT to R.string.settings_theme_light,
        ThemeMode.DARK to R.string.settings_theme_dark,
    )
    Box {
        SettingsRow(
            title = stringResource(R.string.settings_theme),
            onClick = { expanded = true },
        ) {
            SettingsValue(stringResource(options.first { it.first == mode }.second))
            SettingsChevron(down = true)
        }
        VoxDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, label = stringResource(R.string.settings_theme)) {
            options.forEach { (value, label) ->
                VoxMenuItem(
                    text = stringResource(label),
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    checked = value == mode,
                )
            }
        }
    }
}

/** Acerca de VoxLibris: versión, qué es, con qué está hecho y dónde está el código. */
@Composable
private fun AboutDialog(versionName: String, onDismiss: () -> Unit) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val uriHandler = LocalUriHandler.current
    VoxDialog(
        title = stringResource(R.string.settings_about),
        onDismissRequest = onDismiss,
        actions = {
            VoxButton(stringResource(R.string.close), onDismiss, variant = VoxButtonVariant.Secondary, size = VoxButtonSize.Small)
            VoxButton(
                stringResource(R.string.about_open_repo),
                onClick = { uriHandler.openUri(REPO_URL) },
                size = VoxButtonSize.Small,
                icon = R.drawable.ic_vx_external,
            )
        },
    ) {
        Text(stringResource(R.string.settings_version, versionName), style = t.label, color = c.inkMuted)
        Text(stringResource(R.string.about_body), style = t.body, color = c.ink)
        Text(stringResource(R.string.about_credits), style = t.caption, color = c.inkMuted)
    }
}
