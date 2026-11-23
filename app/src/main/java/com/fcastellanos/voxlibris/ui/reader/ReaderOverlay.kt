package com.fcastellanos.voxlibris.ui.reader

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.VoxLibrisApp
import com.fcastellanos.voxlibris.data.Highlight
import com.fcastellanos.voxlibris.dictionary.DictionaryEntry
import com.fcastellanos.voxlibris.dictionary.DictionarySense
import com.fcastellanos.voxlibris.dictionary.FormOf
import com.fcastellanos.voxlibris.dictionary.WiktionaryDictionary
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import com.fcastellanos.voxlibris.tts.SherpaTtsDefaults
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.tts.resolve
import com.fcastellanos.voxlibris.ui.components.Banner
import com.fcastellanos.voxlibris.ui.components.DictionaryCard
import com.fcastellanos.voxlibris.ui.components.PlayButton
import com.fcastellanos.voxlibris.ui.components.PlayerPill
import com.fcastellanos.voxlibris.ui.components.ProgressRing
import com.fcastellanos.voxlibris.ui.components.SectionLabel
import com.fcastellanos.voxlibris.ui.components.SheetHeader
import com.fcastellanos.voxlibris.ui.components.StatusPill
import com.fcastellanos.voxlibris.ui.components.StrongPill
import com.fcastellanos.voxlibris.ui.components.VoxBottomSheet
import com.fcastellanos.voxlibris.ui.components.VoxButton
import com.fcastellanos.voxlibris.ui.components.VoxButtonSize
import com.fcastellanos.voxlibris.ui.components.VoxButtonVariant
import com.fcastellanos.voxlibris.ui.components.VoxChip
import com.fcastellanos.voxlibris.ui.components.VoxChipGroup
import com.fcastellanos.voxlibris.ui.components.VoxConfirmDialog
import com.fcastellanos.voxlibris.ui.components.VoxDialog
import com.fcastellanos.voxlibris.ui.components.VoxDropdownMenu
import com.fcastellanos.voxlibris.ui.components.VoxIconButton
import com.fcastellanos.voxlibris.ui.components.VoxIconButtonVariant
import com.fcastellanos.voxlibris.ui.components.VoxMenuItem
import com.fcastellanos.voxlibris.ui.components.VoxSlider
import com.fcastellanos.voxlibris.ui.components.VoxTabs
import com.fcastellanos.voxlibris.ui.components.VoxTextField
import com.fcastellanos.voxlibris.ui.components.formatSpeed
import com.fcastellanos.voxlibris.ui.components.voxShadow
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import com.fcastellanos.voxlibris.ui.voices.VoiceSettingsContent
import com.fcastellanos.voxlibris.ui.voices.VoicesViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

private val SLEEP_TIMER_OPTIONS = listOf(10, 20, 30, 45, 60)

/**
 * Interfaz del lector según la pantalla «Texto completo» del lienzo de diseño: barra superior con el
 * avance, la barra de lectura o el reproductor flotante abajo, y las hojas de Contenido, Apariencia,
 * Lectores y Temporizador. Los colores salen del tema que el fragmento elige según el fondo de lectura.
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderOverlay(viewModel: ReaderViewModel, onBack: () -> Unit, onListen: () -> Unit) {
    val visible by viewModel.overlayVisible.collectAsStateWithLifecycle()
    val locator by viewModel.currentLocator.collectAsStateWithLifecycle()
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    var showContents by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showVoices by rememberSaveable { mutableStateOf(false) }
    var showTimer by rememberSaveable { mutableStateOf(false) }
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val tappedHighlight by viewModel.tappedHighlight.collectAsStateWithLifecycle()
    val noteEditor by viewModel.noteEditor.collectAsStateWithLifecycle()
    val definition by viewModel.definition.collectAsStateWithLifecycle()

    val readAloud = viewModel.readAloud
    val activeBookId by readAloud.activeBookId.collectAsStateWithLifecycle()
    val rawStatus by readAloud.status.collectAsStateWithLifecycle()
    val ttsActive = activeBookId == viewModel.bookId
    val ttsStatus = if (ttsActive) rawStatus else ReadAloudManager.Status.Idle
    val modelState by readAloud.modelState.collectAsStateWithLifecycle()
    val ttsPreferences by readAloud.preferences.collectAsStateWithLifecycle()
    val remainingSeconds by readAloud.remainingSeconds.collectAsStateWithLifecycle()
    val ttsSettings = ttsPreferences.resolve(null)
    val sleepEndsAt by readAloud.sleepTimerEndsAt.collectAsStateWithLifecycle()

    val container = (LocalContext.current.applicationContext as VoxLibrisApp).container
    val sample = stringResource(R.string.sample_sentence)
    val dialogueSample = stringResource(R.string.sample_dialogue)
    val voicesViewModel: VoicesViewModel = viewModel(
        key = "voices-${viewModel.bookId}",
        factory = VoicesViewModel.factory(container, sample, dialogueSample, viewModel.bookId),
    )

    val paper = preferences.paper()
    val percent = locator?.locations?.totalProgression?.let { (it * 100).roundToInt().coerceIn(0, 100) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            ReaderTopBar(
                title = viewModel.title,
                percent = percent,
                background = paper.background,
                onBack = onBack,
                onContents = { showContents = true },
                onAppearance = { showAppearance = true },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .consumeTaps()
                    .navigationBarsPadding()
                    .padding(start = VoxDimens.space4, end = VoxDimens.space4, top = VoxDimens.space2, bottom = VoxDimens.space4),
                verticalArrangement = Arrangement.spacedBy(VoxDimens.space2),
            ) {
                val downloading = ttsStatus == ReadAloudManager.Status.NeedsModel &&
                    (modelState is VoiceModelManager.State.Downloading || modelState is VoiceModelManager.State.Installing)
                when {
                    ttsStatus == ReadAloudManager.Status.Idle ->
                        ReadingBar(locator = locator, positions = positions, percent = percent, onSeek = viewModel::goToPosition, onListen = onListen)
                    downloading -> {
                        val progress = (modelState as? VoiceModelManager.State.Downloading)?.progress
                        Banner(
                            title = if (progress != null) {
                                stringResource(R.string.downloading_voices, (progress * 100).toInt())
                            } else {
                                stringResource(R.string.installing_voices)
                            },
                            progress = progress,
                            modifier = Modifier.voxShadow(VoxLibrisTheme.shadows.shadowFloat, RoundedCornerShape(VoxDimens.radiusXl)),
                        )
                    }
                    else -> ListeningControls(
                        status = ttsStatus,
                        voiceId = ttsSettings.voiceId,
                        speed = ttsSettings.speed,
                        sleepEndsAt = sleepEndsAt,
                        remainingSeconds = remainingSeconds,
                        onPlayPause = readAloud::togglePlayPause,
                        onPrevious = { readAloud.previous() },
                        onNext = { readAloud.next() },
                        onStop = readAloud::stop,
                        onVoices = { showVoices = true },
                        onTimer = { showTimer = true },
                        onSpeed = voicesViewModel::setSpeed,
                    )
                }
            }
        }
        definition?.let { state ->
            DefinitionCard(
                state = state,
                onDismiss = viewModel::dismissDefinition,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(VoxDimens.space4),
            )
        }
    }

    if (ttsStatus == ReadAloudManager.Status.NeedsModel &&
        modelState !is VoiceModelManager.State.Downloading && modelState !is VoiceModelManager.State.Installing
    ) {
        VoxConfirmDialog(
            title = stringResource(R.string.tts_need_model_title),
            body = stringResource(R.string.tts_need_model_body, SupertonicModel.SIZE_MB),
            confirmLabel = stringResource(R.string.download),
            onConfirm = readAloud::downloadVoices,
            onDismiss = readAloud::stop,
            destructive = false,
        )
    }
    if (showContents) {
        ContentsSheet(
            entries = viewModel.toc,
            current = locator,
            highlights = highlights,
            onSelect = { link ->
                viewModel.go(link)
                showContents = false
            },
            onGoToHighlight = { highlight ->
                viewModel.goToHighlight(highlight)
                showContents = false
            },
            onEditNote = { highlight ->
                viewModel.editNote(highlight)
                showContents = false
            },
            onDeleteHighlight = viewModel::deleteHighlight,
            onDismiss = { showContents = false },
        )
    }
    if (showAppearance) {
        AppearanceSheet(
            preferences = preferences,
            onChange = viewModel::updatePreferences,
            onDismiss = { showAppearance = false },
        )
    }
    if (showTimer) {
        SleepTimerSheet(
            sleepEndsAt = sleepEndsAt,
            onSelect = readAloud::setSleepTimer,
            onDismiss = { showTimer = false },
        )
    }
    if (showVoices) {
        VoicesSheet(viewModel = voicesViewModel, onDismiss = { showVoices = false })
    }
    tappedHighlight?.let { highlight ->
        HighlightDialog(
            highlight = highlight,
            onEditNote = { viewModel.editNote(highlight) },
            onDelete = { viewModel.deleteHighlight(highlight.id) },
            onDismiss = viewModel::dismissTappedHighlight,
        )
    }
    noteEditor?.let { editor ->
        NoteDialog(
            editor = editor,
            onSave = { note -> viewModel.saveNote(editor.highlightId, note) },
            onDismiss = viewModel::dismissNoteEditor,
        )
    }
}

/** Lector del libro abierto (pantalla «Elegir voz» del lienzo): modo, voces, muestra, velocidad y calidad. */
@Composable
internal fun VoicesSheet(viewModel: VoicesViewModel, onDismiss: () -> Unit) {
    VoxBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.voices_who), tag = stringResource(R.string.voices_tag))
        VoiceSettingsContent(viewModel = viewModel, modifier = Modifier.weight(1f, fill = false))
        VoxButton(
            stringResource(R.string.reader_done),
            onClick = onDismiss,
            size = VoxButtonSize.Block,
        )
    }
}

/** Evita que los toques sobre las barras lleguen al navegador y pasen de página. */
private fun Modifier.consumeTaps(): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = {},
)

/** Barra superior del texto completo: volver, el avance del libro con su anillo, Contenido y Apariencia. */
@Composable
private fun ReaderTopBar(
    title: String,
    percent: Int?,
    background: Color,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onAppearance: () -> Unit,
) {
    val c = VoxLibrisTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .consumeTaps()
            .background(background)
            .statusBarsPadding()
            .padding(horizontal = VoxDimens.space4, vertical = VoxDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2),
    ) {
        VoxIconButton(R.drawable.ic_vx_back, stringResource(R.string.back), onBack)
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2, Alignment.CenterHorizontally),
        ) {
            if (percent != null) ProgressRing(percent / 100f, stringResource(R.string.vx_read_percent, percent))
            Text(
                title,
                style = VoxLibrisTheme.typography.label,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (percent != null) {
                Text(stringResource(R.string.reader_title_percent, percent), style = VoxLibrisTheme.typography.label, color = c.ink, maxLines = 1)
            }
        }
        VoxIconButton(R.drawable.ic_vx_list, stringResource(R.string.reader_contents), onContents)
        VoxIconButton(R.drawable.ic_vx_text_size, stringResource(R.string.reader_appearance), onAppearance)
    }
}

/** Barra de lectura cuando no suena nada: escuchar desde acá y saltar a otra posición del libro. */
@Composable
private fun ReadingBar(locator: Locator?, positions: List<Locator>, percent: Int?, onSeek: (Int) -> Unit, onListen: () -> Unit) {
    val c = VoxLibrisTheme.colors
    val currentIndex = ((locator?.locations?.position ?: 1) - 1).coerceAtLeast(0)
    var sliderValue by remember(currentIndex) { mutableFloatStateOf(currentIndex.toFloat()) }
    val position = if (positions.isNotEmpty()) {
        stringResource(R.string.position_of, sliderValue.roundToInt() + 1, positions.size)
    } else {
        stringResource(R.string.reading_position)
    }
    StrongPill(label = stringResource(R.string.reader_reading_bar)) {
        PlayButton(playing = false, onClick = onListen, contentDescription = stringResource(R.string.listen))
        Column(Modifier.weight(1f).padding(start = VoxDimens.space3, end = VoxDimens.space2)) {
            if (positions.size > 1) {
                VoxSlider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSeek(sliderValue.roundToInt()) },
                    valueRange = 0f..(positions.size - 1).toFloat(),
                    onStrong = true,
                    stateDescription = position,
                    modifier = Modifier.height(32.dp),
                )
            }
            Text(
                if (percent != null) stringResource(R.string.reader_position_percent, position, percent) else position,
                style = VoxLibrisTheme.typography.caption,
                color = c.onFillStrong,
                maxLines = 1,
            )
        }
    }
}

/** El reproductor flotante y, arriba, el estado de la voz con el temporizador y el botón para dejar de escuchar. */
@Composable
private fun ListeningControls(
    status: ReadAloudManager.Status,
    voiceId: Int,
    speed: Double,
    sleepEndsAt: Long?,
    remainingSeconds: Long?,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onVoices: () -> Unit,
    onTimer: () -> Unit,
    onSpeed: (Double) -> Unit,
) {
    val playing = status == ReadAloudManager.Status.Playing || status == ReadAloudManager.Status.Preparing
    val statusText = buildString {
        append(
            when (status) {
                ReadAloudManager.Status.Preparing -> stringResource(R.string.tts_preparing)
                ReadAloudManager.Status.Playing -> stringResource(R.string.reader_status_playing)
                ReadAloudManager.Status.Paused -> stringResource(R.string.tts_paused)
                ReadAloudManager.Status.Ended -> stringResource(R.string.tts_ended)
                is ReadAloudManager.Status.Error -> stringResource(R.string.tts_error, status.message)
                else -> ""
            },
        )
        // Lo que falta del libro solo mientras se lee o en pausa; la píldora es angosta, así que el estado va corto.
        remainingSeconds?.takeIf { status == ReadAloudManager.Status.Playing || status == ReadAloudManager.Status.Paused }?.let { seconds ->
            if (isNotEmpty()) append(" · ")
            append(formatRemaining(seconds))
        }
        sleepEndsAt?.let { endsAt ->
            if (isNotEmpty()) append(" · ")
            append(stringResource(R.string.sleep_timer_remaining, remainingMinutes(endsAt)))
        }
    }
    StatusPill(text = statusText, isError = status is ReadAloudManager.Status.Error) {
        VoxIconButton(R.drawable.ic_vx_moon, stringResource(R.string.sleep_timer), onTimer, variant = VoxIconButtonVariant.Muted, size = VoxDimens.sizeControl, iconSize = 18.dp)
        VoxIconButton(R.drawable.ic_vx_close, stringResource(R.string.stop_listening), onStop, variant = VoxIconButtonVariant.Muted, size = VoxDimens.sizeControl, iconSize = 18.dp)
    }
    val voice = SupertonicModel.voice(voiceId)
    val speedText = formatSpeed(speed)
    var speedMenu by remember { mutableStateOf(false) }
    PlayerPill(
        label = stringResource(R.string.reader_player),
        voiceName = voice.name,
        voiceId = voice.id,
        voiceDescription = stringResource(R.string.reader_change_voice, voice.name),
        speedText = speedText,
        speedDescription = stringResource(R.string.reader_speed_description, speedText),
        playing = playing,
        playDescription = stringResource(if (playing) R.string.pause else R.string.play),
        previousDescription = stringResource(R.string.previous_sentence),
        nextDescription = stringResource(R.string.next_sentence),
        onVoice = onVoices,
        onPrevious = onPrevious,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onSpeed = { speedMenu = true },
        speedAnchor = { chip ->
            Box {
                chip()
                VoxDropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }, label = stringResource(R.string.reader_speed)) {
                    SherpaTtsDefaults.SPEED_PRESETS.forEach { value ->
                        VoxMenuItem(
                            text = formatSpeed(value),
                            onClick = {
                                onSpeed(value)
                                speedMenu = false
                            },
                            checked = abs(value - speed) < 0.05,
                        )
                    }
                }
            }
        },
    )
}

/** Minutos restantes hasta `endsAt`, actualizados cada 20 segundos mientras el temporizador esté activo. */
/** Tiempo que falta para terminar el libro, en minutos u horas y minutos. */
@Composable
internal fun formatRemaining(seconds: Long): String {
    val minutes = ((seconds + 59) / 60).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.remaining_under_minute)
        minutes < 60 -> stringResource(R.string.remaining_minutes, minutes)
        else -> stringResource(R.string.remaining_hours_minutes, minutes / 60, minutes % 60)
    }
}

@Composable
internal fun remainingMinutes(endsAt: Long): Int {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }
    return (((endsAt - now) + 59_999) / 60_000).toInt().coerceAtLeast(0)
}

/** Temporizador de sueño (pantalla «Temporizador» del lienzo): cuánto falta y las opciones. */
@Composable
internal fun SleepTimerSheet(sleepEndsAt: Long?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    val c = VoxLibrisTheme.colors
    var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
    VoxBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.sleep_timer), tag = stringResource(R.string.reader_sleep_tag))
        if (sleepEndsAt != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(VoxDimens.radiusXl))
                    .background(c.surfaceMuted)
                    .padding(VoxDimens.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(VoxDimens.sizeTouch).clip(CircleShape).background(c.fillStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_vx_moon), contentDescription = null, tint = c.onFillStrong, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(stringResource(R.string.reader_sleep_minutes, remainingMinutes(sleepEndsAt)), style = VoxLibrisTheme.typography.displayL, color = c.ink)
                    Text(stringResource(R.string.reader_sleep_hint), style = VoxLibrisTheme.typography.caption, color = c.inkMuted)
                }
            }
        }
        VoxChipGroup {
            VoxChip(
                stringResource(R.string.reader_sleep_off),
                selected = sleepEndsAt == null,
                onClick = {
                    chosen = null
                    onSelect(null)
                },
            )
            SLEEP_TIMER_OPTIONS.forEach { minutes ->
                VoxChip(
                    stringResource(R.string.reader_sleep_minutes, minutes),
                    selected = sleepEndsAt != null && chosen == minutes,
                    onClick = {
                        chosen = minutes
                        onSelect(minutes)
                    },
                )
            }
        }
        VoxButton(stringResource(R.string.reader_done), onClick = onDismiss, size = VoxButtonSize.Block)
    }
}

/** Contenido del libro (pantalla «Capítulos y marcadores» del lienzo): capítulos y resaltados en pestañas. */
@Composable
internal fun ContentsSheet(
    entries: List<FlatEntry<Link>>,
    current: Locator?,
    highlights: List<Highlight>,
    onSelect: (Link) -> Unit,
    onGoToHighlight: (Highlight) -> Unit,
    onEditNote: (Highlight) -> Unit,
    onDeleteHighlight: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    VoxBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(
            stringResource(R.string.reader_contents),
            tag = if (entries.isNotEmpty()) pluralStringResource(R.plurals.reader_chapters_count, entries.size, entries.size) else null,
        )
        VoxTabs(
            tabs = listOf(
                stringResource(R.string.reader_chapters),
                if (highlights.isEmpty()) stringResource(R.string.highlights_title) else stringResource(R.string.reader_highlights_count, highlights.size),
            ),
            selectedIndex = tab,
            onSelect = { tab = it },
        )
        if (tab == 0) {
            ChapterList(entries, current, onSelect)
        } else {
            HighlightList(highlights, onGoToHighlight, onEditNote, onDeleteHighlight)
        }
    }
}

@Composable
private fun ChapterList(entries: List<FlatEntry<Link>>, current: Locator?, onSelect: (Link) -> Unit) {
    val c = VoxLibrisTheme.colors
    if (entries.isEmpty()) {
        EmptyText(stringResource(R.string.toc_empty))
        return
    }
    val currentHref = current?.href?.toString()
    val isCurrent = { link: Link -> currentHref != null && link.url().toString().substringBefore('#') == currentHref }
    val currentIndex = entries.indexOfFirst { isCurrent(it.item) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0))
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(entries) { entry ->
            val link = entry.item
            val selected = isCurrent(link)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(VoxDimens.radiusL))
                    .background(if (selected) c.surfaceMuted else Color.Transparent)
                    .clickable { onSelect(link) }
                    .padding(start = (12 + entry.depth * 16).dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    text = link.title ?: link.href.toString(),
                    style = if (selected) VoxLibrisTheme.typography.bodyStrong else VoxLibrisTheme.typography.body,
                    color = c.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HighlightList(
    highlights: List<Highlight>,
    onGo: (Highlight) -> Unit,
    onEditNote: (Highlight) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val c = VoxLibrisTheme.colors
    if (highlights.isEmpty()) {
        EmptyText(stringResource(R.string.highlights_empty))
        return
    }
    val goLabel = stringResource(R.string.go_to_highlight)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(VoxDimens.space1)) {
        items(highlights, key = { it.id }) { highlight ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(VoxDimens.radiusL))
                    .clickable(onClickLabel = goLabel) { onGo(highlight) }
                    .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HighlightedQuote(highlight.text, maxLines = 4, modifier = Modifier.padding(end = 8.dp))
                highlight.note?.let { note ->
                    Text(
                        note,
                        style = VoxLibrisTheme.typography.body,
                        color = c.inkMuted,
                        fontStyle = FontStyle.Italic,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateUtils.getRelativeTimeSpanString(highlight.createdAt).toString(),
                        style = VoxLibrisTheme.typography.caption,
                        color = c.inkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    VoxIconButton(
                        R.drawable.ic_vx_edit,
                        stringResource(if (highlight.note == null) R.string.add_note else R.string.edit_note),
                        onClick = { onEditNote(highlight) },
                        variant = VoxIconButtonVariant.Clear,
                        size = VoxDimens.sizeTouch,
                        iconSize = 20.dp,
                    )
                    VoxIconButton(
                        R.drawable.ic_vx_trash,
                        stringResource(R.string.delete_highlight),
                        onClick = { onDelete(highlight.id) },
                        variant = VoxIconButtonVariant.Clear,
                        size = VoxDimens.sizeTouch,
                        iconSize = 20.dp,
                    )
                }
            }
        }
    }
}

/** La cita de un resaltado con el mismo fondo `reading-highlight` que tiene en el libro. */
@Composable
private fun HighlightedQuote(text: String, maxLines: Int, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(background = c.readingHighlight)) { append(text) }
        },
        style = VoxLibrisTheme.typography.body,
        color = c.ink,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = VoxLibrisTheme.typography.body,
        color = VoxLibrisTheme.colors.inkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = VoxDimens.space6),
    )
}

/** Resaltado tocado en el texto: la cita, su nota y qué hacer con él. */
@Composable
private fun HighlightDialog(highlight: Highlight, onEditNote: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val c = VoxLibrisTheme.colors
    VoxDialog(
        title = stringResource(R.string.highlight_dialog_title),
        onDismissRequest = onDismiss,
        actions = {
            VoxButton(stringResource(R.string.close), onDismiss, variant = VoxButtonVariant.Secondary, size = VoxButtonSize.Small)
            VoxButton(
                stringResource(if (highlight.note == null) R.string.add_note else R.string.edit_note),
                onEditNote,
                size = VoxButtonSize.Small,
            )
        },
    ) {
        HighlightedQuote(highlight.text, maxLines = 6)
        highlight.note?.let { note ->
            Text(note, style = VoxLibrisTheme.typography.body, color = c.inkMuted, fontStyle = FontStyle.Italic, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        VoxMenuItem(stringResource(R.string.delete_highlight), onDelete, icon = R.drawable.ic_vx_trash, danger = true)
    }
}

/** Editor de la nota personal de un resaltado: al crearlo se puede omitir, al editarlo se puede cancelar. */
@Composable
internal fun NoteDialog(
    editor: ReaderViewModel.NoteEditor,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable(editor.highlightId) { mutableStateOf(editor.note.orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(editor.highlightId) { focusRequester.requestFocus() }
    VoxDialog(
        title = stringResource(if (editor.isNew) R.string.highlight_saved else R.string.note_title),
        onDismissRequest = onDismiss,
        actions = {
            VoxButton(
                stringResource(if (editor.isNew) R.string.no_note else R.string.cancel),
                onDismiss,
                variant = VoxButtonVariant.Secondary,
                size = VoxButtonSize.Small,
            )
            VoxButton(stringResource(R.string.save), { onSave(note) }, size = VoxButtonSize.Small)
        },
    ) {
        if (editor.isNew) {
            Text(stringResource(R.string.note_prompt), style = VoxLibrisTheme.typography.body, color = VoxLibrisTheme.colors.inkMuted)
        }
        HighlightedQuote(editor.text, maxLines = 4)
        VoxTextField(
            value = note,
            onValueChange = { note = it },
            label = stringResource(R.string.note_label),
            minLines = 3,
            maxLines = 6,
            fieldModifier = Modifier.focusRequester(focusRequester),
        )
    }
}

/** Tarjeta de diccionario al pie de la página, como la de Kindle: palabra, categorías y acepciones. */
@Composable
private fun DefinitionCard(
    state: ReaderViewModel.DefinitionState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val uriHandler = LocalUriHandler.current
    DictionaryCard(
        word = state.word,
        closeDescription = stringResource(R.string.close),
        onClose = onDismiss,
        modifier = modifier.consumeTaps(),
        footer = if (state is ReaderViewModel.DefinitionState.Loading) {
            null
        } else {
            {
                VoxButton(
                    stringResource(R.string.definition_open),
                    onClick = { uriHandler.openUri(WiktionaryDictionary.pageUrl(state.word)) },
                    variant = VoxButtonVariant.Secondary,
                    size = VoxButtonSize.Small,
                    icon = R.drawable.ic_vx_external,
                )
            }
        },
    ) {
        when (state) {
            is ReaderViewModel.DefinitionState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = c.fillStrong, trackColor = c.track, strokeWidth = 2.dp)
                Text(stringResource(R.string.definition_loading), style = t.body, color = c.inkMuted, modifier = Modifier.padding(start = 12.dp))
            }
            is ReaderViewModel.DefinitionState.NotFound -> Text(stringResource(R.string.definition_not_found, state.word), style = t.body, color = c.inkMuted)
            is ReaderViewModel.DefinitionState.Offline -> Text(stringResource(R.string.definition_offline), style = t.body, color = c.inkMuted)
            is ReaderViewModel.DefinitionState.Found -> {
                EntrySections(state.result.entry)
                state.result.lemma?.let { lemma ->
                    Box(Modifier.fillMaxWidth().padding(vertical = VoxDimens.space2).height(1.dp).background(c.line))
                    Text(lemma.word, style = t.title, color = c.ink)
                    EntrySections(lemma)
                }
                Text(
                    text = stringResource(R.string.definition_attribution),
                    style = t.caption,
                    color = c.inkMuted,
                    modifier = Modifier.padding(top = VoxDimens.space2),
                )
            }
        }
    }
}

@Composable
private fun EntrySections(entry: DictionaryEntry) {
    val c = VoxLibrisTheme.colors
    entry.sections.take(MAX_SECTIONS).forEach { section ->
        SectionLabel(section.partOfSpeech, Modifier.padding(top = VoxDimens.space2, bottom = 2.dp))
        section.senses.take(MAX_SENSES).forEachIndexed { index, sense ->
            Text(senseText(index, sense), style = VoxLibrisTheme.typography.body, color = c.ink, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
private fun senseText(index: Int, sense: DictionarySense): String {
    sense.formOf?.let { form ->
        val resource = when (form.kind) {
            FormOf.Kind.VERB -> R.string.form_of_verb
            FormOf.Kind.ADJECTIVE -> R.string.form_of_adjective
            FormOf.Kind.NOUN -> R.string.form_of_noun
            FormOf.Kind.OTHER -> R.string.form_of_other
        }
        return stringResource(resource, form.lemma)
    }
    val label = sense.label?.let { "($it) " }.orEmpty()
    return "${index + 1}. $label${sense.text}"
}

private const val MAX_SECTIONS = 3
private const val MAX_SENSES = 4
