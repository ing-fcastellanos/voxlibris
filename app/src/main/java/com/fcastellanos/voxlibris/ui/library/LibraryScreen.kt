package com.fcastellanos.voxlibris.ui.library

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.data.Book
import com.fcastellanos.voxlibris.data.EpubImporter
import com.fcastellanos.voxlibris.reading.OpenBookError
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import com.fcastellanos.voxlibris.tts.SherpaTtsDefaults
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.ui.components.Banner
import com.fcastellanos.voxlibris.ui.components.BookCover
import com.fcastellanos.voxlibris.ui.components.BookRow
import com.fcastellanos.voxlibris.ui.components.BookStyle
import com.fcastellanos.voxlibris.ui.components.BookTint
import com.fcastellanos.voxlibris.ui.components.ContinueCard
import com.fcastellanos.voxlibris.ui.components.CoverShape
import com.fcastellanos.voxlibris.ui.components.CoverSize
import com.fcastellanos.voxlibris.ui.components.MiniPlayer
import com.fcastellanos.voxlibris.ui.components.NavBar
import com.fcastellanos.voxlibris.ui.components.NavBarItem
import com.fcastellanos.voxlibris.ui.components.VoxBottomSheet
import com.fcastellanos.voxlibris.ui.components.VoxButton
import com.fcastellanos.voxlibris.ui.components.VoxButtonSize
import com.fcastellanos.voxlibris.ui.components.VoxButtonVariant
import com.fcastellanos.voxlibris.ui.components.VoxConfirmDialog
import com.fcastellanos.voxlibris.ui.components.VoxMenuItem
import com.fcastellanos.voxlibris.ui.components.VoxMenuSeparator
import com.fcastellanos.voxlibris.ui.components.VoxSnackbar
import com.fcastellanos.voxlibris.ui.components.VoxSnackbarVisuals
import com.fcastellanos.voxlibris.ui.components.bookStyleFor
import com.fcastellanos.voxlibris.ui.components.voxShadow
import com.fcastellanos.voxlibris.ui.reader.ReaderActivity
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import kotlinx.coroutines.launch

private val EPUB_MIME_TYPES = arrayOf("application/epub+zip")
private const val FEATURED_KEY = "featured"

/**
 * Inicio de la app, según la pantalla «Inicio» del lienzo de diseño: la tarjeta del libro actual,
 * la lista de libros con play directo, el mini reproductor y la barra de navegación flotante.
 */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onOpenVoices: () -> Unit) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val openingBookId by viewModel.openingBookId.collectAsStateWithLifecycle()
    val activeBookId by viewModel.readAloud.activeBookId.collectAsStateWithLifecycle()
    val activeTitle by viewModel.readAloud.activeTitle.collectAsStateWithLifecycle()
    val status by viewModel.readAloud.status.collectAsStateWithLifecycle()
    val ttsPreferences by viewModel.readAloud.preferences.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var menuBook by remember { mutableStateOf<Book?>(null) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(VoxSnackbarVisuals(message.toText(context), isError = message.isError))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.OpenReader -> context.startActivity(ReaderActivity.intent(context, event.bookId))
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importAll(uris)
    }
    val addBook = { picker.launch(EPUB_MIME_TYPES) }

    val playing = status == ReadAloudManager.Status.Playing || status == ReadAloudManager.Status.Preparing
    val statusText = readAloudStatusText(status)
    val activeBook = books.firstOrNull { it.id == activeBookId }
    // La tarjeta muestra lo que suena; si no suena nada, el último libro empezado y sin terminar.
    val featured = activeBook ?: books.firstOrNull { book -> book.progressPercent?.let { it < 100 } == true }
    val others = if (featured == null) books else books.filter { it.id != featured.id }
    val featuredVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == FEATURED_KEY } }
    }
    // Si la tarjeta ya muestra el libro que suena, el mini reproductor aparece recién al perderla de vista.
    val showMiniPlayer = activeBookId != null && !(featured != null && featured.id == activeBookId && featuredVisible)

    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = bottomInset + VoxDimens.space6 + VoxDimens.sizeNav + VoxDimens.space5 +
        if (showMiniPlayer) 80.dp else 0.dp

    Box(Modifier.fillMaxSize().background(c.ground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = VoxDimens.space5,
                end = VoxDimens.space5,
                top = topInset + VoxDimens.space6,
                bottom = bottomPadding,
            ),
        ) {
            item(key = "heading") {
                Text(
                    stringResource(R.string.library_heading),
                    style = t.displayXl,
                    color = c.ink,
                    modifier = Modifier.padding(bottom = VoxDimens.space4).semantics { heading() },
                )
            }
            importProgress?.let { progress ->
                item(key = "importing") {
                    Banner(stringResource(R.string.importing, progress.current, progress.total), Modifier.padding(bottom = VoxDimens.space4))
                }
            }
            if (openingBookId != null) {
                item(key = "opening") {
                    Banner(stringResource(R.string.opening_book), Modifier.padding(bottom = VoxDimens.space4))
                }
            }
            if (books.isEmpty()) {
                item(key = "empty") { EmptyLibrary(onAdd = addBook, modifier = Modifier.fillParentMaxHeight(0.75f)) }
            } else {
                if (featured != null) {
                    item(key = FEATURED_KEY) {
                        val voiceId = ttsPreferences.voiceId ?: SherpaTtsDefaults.VOICE_ID
                        val isActive = featured.id == activeBookId
                        ContinueCard(
                            title = featured.title,
                            author = featured.author,
                            style = bookStyleFor(featured.title),
                            coverFile = featured.coverFile,
                            progressPercent = featured.progressPercent ?: 0,
                            label = stringResource(R.string.library_continue_label),
                            isPlaying = isActive && playing,
                            statusText = if (isActive) statusText else null,
                            voiceName = SupertonicModel.voice(voiceId).name,
                            voiceId = voiceId,
                            speed = ttsPreferences.speed ?: SherpaTtsDefaults.SPEED,
                            onOpen = { viewModel.openBook(featured) },
                            onMenu = { menuBook = featured },
                            onListen = { viewModel.listen(featured) },
                            onVoice = onOpenVoices,
                            modifier = Modifier.padding(bottom = VoxDimens.space6),
                        )
                    }
                }
                if (others.isNotEmpty()) {
                    item(key = "section") { SectionHeader(count = books.size) }
                    items(others, key = { it.id }) { book ->
                        BookRow(
                            title = book.title,
                            author = book.author,
                            style = bookStyleFor(book.title),
                            coverFile = book.coverFile,
                            progressPercent = book.progressPercent,
                            isPlaying = book.id == activeBookId && playing,
                            onOpen = { viewModel.openBook(book) },
                            onMenu = { menuBook = book },
                            onListen = { viewModel.listen(book) },
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = VoxDimens.space5, end = VoxDimens.space5, bottom = VoxDimens.space6),
            verticalArrangement = Arrangement.spacedBy(VoxDimens.space3),
        ) {
            SnackbarHost(snackbarHostState) { VoxSnackbar(it) }
            val activeId = activeBookId
            if (showMiniPlayer && activeId != null) {
                val title = activeBook?.title ?: activeTitle.orEmpty()
                MiniPlayer(
                    title = title,
                    statusText = statusText,
                    style = bookStyleFor(title),
                    coverFile = activeBook?.coverFile,
                    progress = activeBook?.progressPercent?.let { it / 100f },
                    isPlaying = playing,
                    canTogglePlayback = playing || status == ReadAloudManager.Status.Paused,
                    onOpen = {
                        if (status == ReadAloudManager.Status.NeedsModel) onOpenVoices() else viewModel.openBook(activeId)
                    },
                    onPlayPause = viewModel.readAloud::togglePlayPause,
                    onStop = viewModel.readAloud::stop,
                )
            }
            NavBar(
                label = stringResource(R.string.library_nav),
                items = listOf(
                    NavBarItem(stringResource(R.string.library_nav_home), R.drawable.ic_vx_home, selected = true) {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    NavBarItem(stringResource(R.string.add_book), R.drawable.ic_vx_plus, accent = true, onClick = addBook),
                    NavBarItem(stringResource(R.string.voices_title), R.drawable.ic_vx_voice, onClick = onOpenVoices),
                ),
            )
        }
    }

    menuBook?.let { book ->
        BookMenuSheet(
            book = book,
            isPlaying = book.id == activeBookId && playing,
            onDismiss = { menuBook = null },
            onListen = {
                menuBook = null
                viewModel.listen(book)
            },
            onRead = {
                menuBook = null
                viewModel.openBook(book)
            },
            onDelete = {
                menuBook = null
                bookToDelete = book
            },
        )
    }

    bookToDelete?.let { book ->
        VoxConfirmDialog(
            title = stringResource(R.string.delete_dialog_title),
            body = stringResource(R.string.delete_dialog_body, book.title),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.delete(book)
                bookToDelete = null
            },
            onDismiss = { bookToDelete = null },
        )
    }
}

@Composable
private fun SectionHeader(count: Int) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    Row(
        Modifier.fillMaxWidth().padding(bottom = VoxDimens.space1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.library_section), style = t.title, color = c.ink, modifier = Modifier.semantics { heading() })
        Text(pluralStringResource(R.plurals.library_count, count, count), style = t.caption, color = c.inkMuted)
    }
}

/** Biblioteca vacía: tres portadas decorativas, qué hacer y el botón para agregar el primer libro. */
@Composable
private fun EmptyLibrary(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VoxDimens.space3, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(width = 240.dp, height = 176.dp).clearAndSetSemantics {}) {
            BookCover("", BookStyle(BookTint.Cobalt, CoverShape.Orb), null, CoverSize.Medium, Modifier.offset(20.dp, 24.dp).rotate(-10f))
            BookCover("", BookStyle(BookTint.Mint, CoverShape.Stripes), null, CoverSize.Medium, Modifier.offset(124.dp, 24.dp).rotate(10f))
            BookCover(
                "",
                BookStyle(BookTint.Night, CoverShape.Disc),
                null,
                CoverSize.Medium,
                Modifier.offset(72.dp, 4.dp).voxShadow(VoxLibrisTheme.shadows.shadowCover, RoundedCornerShape(VoxDimens.radiusL)),
            )
        }
        Text(
            stringResource(R.string.empty_library_title),
            style = t.displayL,
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = VoxDimens.space3),
        )
        Text(
            stringResource(R.string.empty_library_body),
            style = t.body,
            color = c.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        VoxButton(
            stringResource(R.string.add_book),
            onAdd,
            modifier = Modifier.padding(top = VoxDimens.space2),
            variant = VoxButtonVariant.Accent,
            size = VoxButtonSize.Large,
            icon = R.drawable.ic_vx_plus,
        )
        Text(
            stringResource(R.string.library_empty_caption),
            style = t.caption,
            color = c.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

/** Opciones de un libro, al mantenerlo presionado: escuchar o pausar, leer y eliminar. */
@Composable
private fun BookMenuSheet(
    book: Book,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onListen: () -> Unit,
    onRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    VoxBottomSheet(onDismissRequest = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BookCover(book.title, bookStyleFor(book.title), book.coverFile, CoverSize.Small)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.title, style = t.displayM, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let { Text(it, style = t.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(progressSummary(book.progressPercent), style = t.caption, color = c.inkMuted)
            }
        }
        Column {
            VoxMenuItem(
                stringResource(if (isPlaying) R.string.pause else R.string.listen),
                onListen,
                icon = if (isPlaying) R.drawable.ic_vx_pause else R.drawable.ic_vx_play,
            )
            VoxMenuItem(stringResource(R.string.library_read), onRead, icon = R.drawable.ic_vx_book)
            VoxMenuSeparator()
            VoxMenuItem(stringResource(R.string.delete), onDelete, icon = R.drawable.ic_vx_trash, danger = true)
        }
    }
}

@Composable
private fun progressSummary(percent: Int?): String = when {
    percent == null -> stringResource(R.string.vx_new)
    percent >= 100 -> stringResource(R.string.vx_finished)
    else -> stringResource(R.string.vx_read_percent, percent)
}

@Composable
private fun readAloudStatusText(status: ReadAloudManager.Status): String = when (status) {
    ReadAloudManager.Status.Preparing -> stringResource(R.string.tts_preparing)
    ReadAloudManager.Status.Playing -> stringResource(R.string.library_status_playing)
    ReadAloudManager.Status.Paused -> stringResource(R.string.tts_paused)
    ReadAloudManager.Status.Ended -> stringResource(R.string.tts_ended)
    ReadAloudManager.Status.NeedsModel -> stringResource(R.string.library_status_needs_model)
    is ReadAloudManager.Status.Error -> stringResource(R.string.tts_error, status.message)
    else -> ""
}

private val UiMessage.isError: Boolean
    get() = this is UiMessage.ImportFailed || this is UiMessage.OpenFailed

private fun UiMessage.toText(context: Context): String = when (this) {
    is UiMessage.Imported -> context.resources.getQuantityString(R.plurals.msg_imported, count, count)
    is UiMessage.Deleted -> context.getString(R.string.msg_deleted, title)
    is UiMessage.OpenFailed -> when (val e = error) {
        OpenBookError.BookNotFound -> context.getString(R.string.msg_book_not_found)
        OpenBookError.FileMissing -> context.getString(R.string.msg_book_file_missing)
        OpenBookError.NotSupported -> context.getString(R.string.msg_not_supported)
        is OpenBookError.Readium -> context.getString(R.string.msg_open_failed, e.message)
    }
    is UiMessage.ImportFailed -> when (val e = error) {
        EpubImporter.ImportError.CannotRead -> context.getString(R.string.msg_cannot_read)
        is EpubImporter.ImportError.NotAnEpub ->
            e.detected?.let { context.getString(R.string.msg_not_epub_detected, it) }
                ?: context.getString(R.string.msg_not_epub)
        is EpubImporter.ImportError.OpenFailed -> context.getString(R.string.msg_open_failed, e.message)
        is EpubImporter.ImportError.AlreadyInLibrary -> context.getString(R.string.msg_already_in_library, e.title)
    }
}
