package com.fcastellanos.voxlibris.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.VoxLibrisApp
import com.fcastellanos.voxlibris.data.Book
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import com.fcastellanos.voxlibris.tts.SherpaTtsDefaults
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.ui.components.BookCover
import com.fcastellanos.voxlibris.ui.components.CoverSize
import com.fcastellanos.voxlibris.ui.components.PlayButton
import com.fcastellanos.voxlibris.ui.components.PlayButtonSize
import com.fcastellanos.voxlibris.ui.components.ProgressBar
import com.fcastellanos.voxlibris.ui.components.VoiceAvatar
import com.fcastellanos.voxlibris.ui.components.VoxButton
import com.fcastellanos.voxlibris.ui.components.VoxButtonSize
import com.fcastellanos.voxlibris.ui.components.VoxButtonVariant
import com.fcastellanos.voxlibris.ui.components.VoxConfirmDialog
import com.fcastellanos.voxlibris.ui.components.VoxDropdownMenu
import com.fcastellanos.voxlibris.ui.components.VoxIconButton
import com.fcastellanos.voxlibris.ui.components.VoxIconButtonVariant
import com.fcastellanos.voxlibris.ui.components.VoxMenuItem
import com.fcastellanos.voxlibris.ui.components.VoxTextIconButton
import com.fcastellanos.voxlibris.ui.components.bookStyleFor
import com.fcastellanos.voxlibris.ui.components.formatSpeed
import com.fcastellanos.voxlibris.ui.components.voiceTint
import com.fcastellanos.voxlibris.ui.components.voxShadow
import com.fcastellanos.voxlibris.ui.reader.ContentsSheet
import com.fcastellanos.voxlibris.ui.reader.NoteDialog
import com.fcastellanos.voxlibris.ui.reader.ReaderActivity
import com.fcastellanos.voxlibris.ui.reader.SleepTimerSheet
import com.fcastellanos.voxlibris.ui.reader.VoicesSheet
import com.fcastellanos.voxlibris.ui.reader.formatRemaining
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import com.fcastellanos.voxlibris.ui.voices.VoicesViewModel
import kotlin.math.abs

private const val BEFORE_CHARS = 60

/**
 * Reproduciendo (pantalla «Reproduciendo» del lienzo): portada, la frase que suena entre la anterior
 * y la siguiente, el avance del capítulo, cuánto falta del libro y los controles. En claro va sobre
 * `tomato`; en oscuro, sobre `ground`, con `tomato` solo en el play y el avance.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    darkTheme: Boolean,
    onMinimize: () -> Unit,
    onListen: (Book) -> Unit,
) {
    BackHandler(onBack = onMinimize)
    val book by viewModel.book.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val chapterTitle by viewModel.chapterTitle.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toc by viewModel.toc.collectAsStateWithLifecycle()
    val highlights by viewModel.highlightList.collectAsStateWithLifecycle()
    val noteEditor by viewModel.noteEditor.collectAsStateWithLifecycle()
    val remainingSeconds by viewModel.readAloud.remainingSeconds.collectAsStateWithLifecycle()
    val sleepEndsAt by viewModel.readAloud.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val modelState by viewModel.readAloud.modelState.collectAsStateWithLifecycle()
    var showTimer by rememberSaveable { mutableStateOf(false) }
    var showVoices by rememberSaveable { mutableStateOf(false) }
    var showContents by rememberSaveable { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val container = (context.applicationContext as VoxLibrisApp).container
    val voicesViewModel: VoicesViewModel = viewModel(
        key = "player-voices-${viewModel.bookId}",
        factory = VoicesViewModel.factory(container, stringResource(R.string.sample_sentence), stringResource(R.string.sample_dialogue), viewModel.bookId),
    )

    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val current = book ?: return
    val active = status != ReadAloudManager.Status.Idle
    val playing = status == ReadAloudManager.Status.Playing || status == ReadAloudManager.Status.Preparing
    val background = if (darkTheme) c.ground else c.tomato
    val headerColor = if (darkTheme) c.ink else c.onColor
    val headerMuted = if (darkTheme) c.inkMuted else c.onColor
    val voice = SupertonicModel.voice(settings.voiceId)
    val speedText = formatSpeed(settings.speed)
    val downloading = status == ReadAloudManager.Status.NeedsModel &&
        (modelState is VoiceModelManager.State.Downloading || modelState is VoiceModelManager.State.Installing)

    Column(Modifier.fillMaxSize().background(background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = VoxDimens.space5, end = VoxDimens.space5, top = VoxDimens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoxIconButton(R.drawable.ic_vx_chevron_down, stringResource(R.string.player_minimize), onMinimize, iconSize = 24.dp)
            Column(Modifier.weight(1f).padding(horizontal = VoxDimens.space2), horizontalAlignment = Alignment.CenterHorizontally) {
                chapterTitle?.let { title ->
                    Text(
                        chapterLabel(title).uppercase(),
                        style = t.overline,
                        color = headerMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    current.title,
                    style = t.label,
                    color = headerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Box {
                VoxIconButton(R.drawable.ic_vx_more, stringResource(R.string.player_more), { showMore = true })
                VoxDropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    VoxMenuItem(
                        stringResource(R.string.player_full_text),
                        onClick = {
                            showMore = false
                            context.startActivity(ReaderActivity.intent(context, current.id))
                        },
                        icon = R.drawable.ic_vx_book,
                    )
                    if (sleepEndsAt != null) {
                        VoxMenuItem(
                            stringResource(R.string.player_cancel_timer),
                            onClick = {
                                showMore = false
                                viewModel.setSleepTimer(null)
                            },
                            icon = R.drawable.ic_vx_moon,
                        )
                    }
                    if (active) {
                        VoxMenuItem(
                            stringResource(R.string.stop_listening),
                            onClick = {
                                showMore = false
                                viewModel.stop()
                            },
                            icon = R.drawable.ic_vx_close,
                            danger = true,
                        )
                    }
                }
            }
        }

        BookCover(
            current.title,
            bookStyleFor(current.title),
            current.coverFile,
            CoverSize.Medium,
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 14.dp)
                .rotate(-3f)
                .voxShadow(VoxLibrisTheme.shadows.shadowCover, RoundedCornerShape(VoxDimens.radiusL)),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 22.dp)
                .clip(RoundedCornerShape(topStart = VoxDimens.radiusSheet, topEnd = VoxDimens.radiusSheet))
                .background(c.surface)
                .navigationBarsPadding()
                .padding(start = VoxDimens.space5, end = VoxDimens.space5, top = VoxDimens.space6, bottom = 28.dp),
        ) {
            Column(
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    downloading -> {
                        val progress = (modelState as? VoiceModelManager.State.Downloading)?.progress
                        Text(
                            if (progress != null) stringResource(R.string.downloading_voices, (progress * 100).toInt()) else stringResource(R.string.installing_voices),
                            style = t.label,
                            color = c.ink,
                        )
                        ProgressBar(progress, fillColor = c.tomato)
                    }
                    highlight != null -> {
                        val text = highlight!!.text
                        text.before?.takeIf { it.isNotBlank() }?.let { before ->
                            Text("…" + before.trimEnd().takeLast(BEFORE_CHARS), style = t.body, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(background = c.mustard, color = c.onColor)) { append(text.highlight.orEmpty()) }
                            },
                            style = t.nowPlaying,
                            maxLines = 7,
                            overflow = TextOverflow.Ellipsis,
                        )
                        text.after?.takeIf { it.isNotBlank() }?.let { after ->
                            Text(after.trimStart(), style = t.body, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    active -> Text(stringResource(R.string.tts_preparing), style = t.body, color = c.inkMuted)
                    else -> Text(
                        stringResource(if (current.progressPercent == null) R.string.player_idle_new else R.string.player_idle_hint),
                        style = t.body,
                        color = c.inkMuted,
                    )
                }
            }

            Column(Modifier.padding(top = VoxDimens.space4), verticalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
                val fraction = position?.let { it.sentence.toFloat() / it.total.coerceAtLeast(1) }
                    ?: ((current.progressPercent ?: 0) / 100f)
                ProgressBar(fraction, fillColor = c.tomato)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        position?.let { stringResource(R.string.player_sentence_of, it.sentence, it.total) }
                            ?: statusCaption(status, current.progressPercent),
                        style = t.caption,
                        color = if (status is ReadAloudManager.Status.Error) c.danger else c.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    remainingSeconds?.takeIf { active }?.let { seconds ->
                        Text(formatRemaining(seconds), style = t.caption, color = c.inkMuted, maxLines = 1)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = VoxDimens.space4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var speedMenu by remember { mutableStateOf(false) }
                Box {
                    VoxTextIconButton(speedText, stringResource(R.string.reader_speed_description, speedText), onClick = { speedMenu = true })
                    VoxDropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }, label = stringResource(R.string.player_speed_menu)) {
                        SherpaTtsDefaults.SPEED_PRESETS.forEach { value ->
                            VoxMenuItem(
                                text = formatSpeed(value),
                                onClick = {
                                    viewModel.setSpeed(value)
                                    speedMenu = false
                                },
                                checked = abs(value - settings.speed) < 0.05,
                            )
                        }
                    }
                }
                VoxIconButton(
                    R.drawable.ic_vx_previous,
                    stringResource(R.string.previous_sentence),
                    onClick = { if (active) viewModel.previous() },
                    variant = VoxIconButtonVariant.Muted,
                    size = VoxDimens.sizeLarge,
                    iconSize = 24.dp,
                    modifier = Modifier.alpha(if (active) 1f else 0.4f),
                )
                PlayButton(
                    playing = playing,
                    onClick = { if (active) viewModel.togglePlayPause() else onListen(current) },
                    contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                    size = PlayButtonSize.Hero,
                )
                VoxIconButton(
                    R.drawable.ic_vx_next,
                    stringResource(R.string.next_sentence),
                    onClick = { if (active) viewModel.next() },
                    variant = VoxIconButtonVariant.Muted,
                    size = VoxDimens.sizeLarge,
                    iconSize = 24.dp,
                    modifier = Modifier.alpha(if (active) 1f else 0.4f),
                )
                VoxIconButton(
                    R.drawable.ic_vx_moon,
                    stringResource(R.string.sleep_timer),
                    onClick = { showTimer = true },
                    variant = VoxIconButtonVariant.Muted,
                    iconSize = 20.dp,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = VoxDimens.space4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val voiceLabel = stringResource(R.string.reader_change_voice, voice.name)
                Surface(
                    onClick = { showVoices = true },
                    modifier = Modifier.height(VoxDimens.sizeControl).semantics { contentDescription = voiceLabel },
                    shape = RoundedCornerShape(VoxDimens.radiusPill),
                    color = c.surfaceMuted,
                    contentColor = c.ink,
                ) {
                    Row(
                        Modifier.padding(start = 4.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2),
                    ) {
                        VoiceAvatar(voice.name.take(1), voiceTint(voice.id), size = 32.dp)
                        Text(voice.name, style = t.label, maxLines = 1)
                    }
                }
                VoxButton(
                    stringResource(R.string.player_full_text),
                    onClick = { context.startActivity(ReaderActivity.intent(context, current.id)) },
                    variant = VoxButtonVariant.Secondary,
                    size = VoxButtonSize.Small,
                    icon = R.drawable.ic_vx_book,
                )
                VoxIconButton(
                    R.drawable.ic_vx_list,
                    stringResource(R.string.player_chapters),
                    onClick = { showContents = true },
                    variant = VoxIconButtonVariant.Muted,
                    iconSize = 20.dp,
                    modifier = Modifier.alpha(if (active || highlights.isNotEmpty()) 1f else 0.4f),
                )
            }
        }
    }

    if (status == ReadAloudManager.Status.NeedsModel && !downloading) {
        VoxConfirmDialog(
            title = stringResource(R.string.tts_need_model_title),
            body = stringResource(R.string.tts_need_model_body, SupertonicModel.SIZE_MB),
            confirmLabel = stringResource(R.string.download),
            onConfirm = viewModel.readAloud::downloadVoices,
            onDismiss = viewModel::stop,
            destructive = false,
        )
    }
    if (showTimer) {
        SleepTimerSheet(sleepEndsAt = sleepEndsAt, onSelect = viewModel::setSleepTimer, onDismiss = { showTimer = false })
    }
    if (showVoices) {
        VoicesSheet(viewModel = voicesViewModel, onDismiss = { showVoices = false })
    }
    if (showContents) {
        ContentsSheet(
            entries = toc,
            current = highlight,
            highlights = highlights,
            onSelect = { link ->
                viewModel.goToChapter(link)
                showContents = false
            },
            onGoToHighlight = { highlight ->
                viewModel.listenFrom(highlight)
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
    noteEditor?.let { editor ->
        NoteDialog(editor = editor, onSave = { note -> viewModel.saveNote(editor.highlightId, note) }, onDismiss = viewModel::dismissNoteEditor)
    }
}

/** El índice puede traer solo el número del capítulo («#3»): se muestra como «Capítulo 3». */
@Composable
private fun chapterLabel(title: String): String =
    if (title.startsWith("#")) stringResource(R.string.player_chapter_n, title.drop(1).toIntOrNull() ?: 1) else title

@Composable
private fun statusCaption(status: ReadAloudManager.Status, progressPercent: Int?): String = when (status) {
    ReadAloudManager.Status.Preparing -> stringResource(R.string.tts_preparing)
    ReadAloudManager.Status.Playing -> stringResource(R.string.reader_status_playing)
    ReadAloudManager.Status.Paused -> stringResource(R.string.tts_paused)
    ReadAloudManager.Status.Ended -> stringResource(R.string.tts_ended)
    ReadAloudManager.Status.NeedsModel -> stringResource(R.string.library_status_needs_model)
    is ReadAloudManager.Status.Error -> stringResource(R.string.tts_error, status.message)
    ReadAloudManager.Status.Idle -> progressPercent?.let { stringResource(R.string.vx_read_percent, it) } ?: stringResource(R.string.vx_new)
}
