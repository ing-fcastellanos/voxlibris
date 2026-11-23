package com.fcastellanos.voxlibris.ui.library

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.data.Book
import com.fcastellanos.voxlibris.data.EpubImporter
import com.fcastellanos.voxlibris.data.LibraryQuery.isFinished
import com.fcastellanos.voxlibris.data.LibraryShelf
import com.fcastellanos.voxlibris.data.LibrarySort
import com.fcastellanos.voxlibris.reading.OpenBookError
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import com.fcastellanos.voxlibris.tts.SherpaTtsDefaults
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.ui.MainNavBar
import com.fcastellanos.voxlibris.ui.MainSection
import com.fcastellanos.voxlibris.ui.components.Banner
import com.fcastellanos.voxlibris.ui.components.BookCover
import com.fcastellanos.voxlibris.ui.components.BookRow
import com.fcastellanos.voxlibris.ui.components.BookStyle
import com.fcastellanos.voxlibris.ui.components.BookTint
import com.fcastellanos.voxlibris.ui.components.ContinueCard
import com.fcastellanos.voxlibris.ui.components.CoverShape
import com.fcastellanos.voxlibris.ui.components.CoverSize
import com.fcastellanos.voxlibris.ui.components.MiniPlayer
import com.fcastellanos.voxlibris.ui.components.StreakCard
import com.fcastellanos.voxlibris.ui.components.StreakDot
import com.fcastellanos.voxlibris.ui.components.VoxBottomSheet
import com.fcastellanos.voxlibris.ui.components.VoxButton
import com.fcastellanos.voxlibris.ui.components.VoxButtonSize
import com.fcastellanos.voxlibris.ui.components.VoxButtonVariant
import com.fcastellanos.voxlibris.ui.components.VoxChip
import com.fcastellanos.voxlibris.ui.components.VoxConfirmDialog
import com.fcastellanos.voxlibris.ui.components.VoxDialog
import com.fcastellanos.voxlibris.ui.components.VoxDropdownMenu
import com.fcastellanos.voxlibris.ui.components.VoxIconButton
import com.fcastellanos.voxlibris.ui.components.VoxMenuItem
import com.fcastellanos.voxlibris.ui.components.VoxMenuSeparator
import com.fcastellanos.voxlibris.ui.components.VoxSnackbar
import com.fcastellanos.voxlibris.ui.components.VoxSnackbarVisuals
import com.fcastellanos.voxlibris.ui.components.bookStyleFor
import com.fcastellanos.voxlibris.ui.components.voxShadow
import com.fcastellanos.voxlibris.ui.reader.ContentsSheet
import com.fcastellanos.voxlibris.ui.reader.ReaderActivity
import com.fcastellanos.voxlibris.ui.reader.formatRemaining
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import com.fcastellanos.voxlibris.util.Sizes
import java.util.Locale
import kotlinx.coroutines.launch

private const val FEATURED_KEY = "featured"

/**
 * Inicio de la app, según la pantalla «Inicio» del lienzo de diseño: la tarjeta del libro actual,
 * la lista de libros con play directo, los estantes y el orden, el mini reproductor y la barra de
 * navegación flotante.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onAddBook: () -> Unit,
    onNowPlaying: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVoices: () -> Unit,
    onSearch: () -> Unit,
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val visibleBooks by viewModel.visibleBooks.collectAsStateWithLifecycle()
    val shelf by viewModel.shelf.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val hideFinished by viewModel.hideFinished.collectAsStateWithLifecycle()
    val counts by viewModel.shelfCounts.collectAsStateWithLifecycle()
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val openingBookId by viewModel.openingBookId.collectAsStateWithLifecycle()
    val activeBookId by viewModel.readAloud.activeBookId.collectAsStateWithLifecycle()
    val activeTitle by viewModel.readAloud.activeTitle.collectAsStateWithLifecycle()
    val status by viewModel.readAloud.status.collectAsStateWithLifecycle()
    val remainingSeconds by viewModel.readAloud.remainingSeconds.collectAsStateWithLifecycle()
    val ttsPreferences by viewModel.readAloud.preferences.collectAsStateWithLifecycle()
    val modelState by viewModel.readAloud.modelState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var menuBook by remember { mutableStateOf<Book?>(null) }
    var infoBook by remember { mutableStateOf<Book?>(null) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val listenId = (message as? UiMessage.Imported)?.bookId
            val result = snackbarHostState.showSnackbar(
                VoxSnackbarVisuals(
                    message.toText(context),
                    isError = message.isError,
                    actionLabel = if (listenId != null) context.getString(R.string.listen) else null,
                ),
            )
            if (result == SnackbarResult.ActionPerformed && listenId != null) viewModel.listen(listenId)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.OpenReader -> context.startActivity(ReaderActivity.intent(context, event.bookId))
            }
        }
    }

    val playing = status == ReadAloudManager.Status.Playing || status == ReadAloudManager.Status.Preparing
    val statusText = readAloudStatusText(status)
    val activeBook = books.firstOrNull { it.id == activeBookId }
    // La tarjeta muestra lo que suena; si no suena nada, el último libro empezado y sin terminar.
    val featured = activeBook ?: books.firstOrNull { !it.isFinished() }
    val others = if (featured == null) visibleBooks else visibleBooks.filter { it.id != featured.id }
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
                Row(
                    Modifier.fillMaxWidth().padding(bottom = VoxDimens.space4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
                ) {
                    Text(
                        stringResource(R.string.library_heading),
                        style = t.displayXl,
                        color = c.ink,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    if (books.isNotEmpty()) VoxIconButton(R.drawable.ic_vx_search, stringResource(R.string.library_search), onSearch)
                }
            }
            importProgress?.let { progress ->
                item(key = "importing") {
                    Banner(
                        stringResource(R.string.importing, progress.current, progress.total),
                        Modifier.padding(bottom = VoxDimens.space4),
                        detail = progress.fileName,
                        progress = (progress.current - 1).toFloat() / progress.total.coerceAtLeast(1),
                    )
                }
            }
            if (openingBookId != null) {
                item(key = "opening") {
                    Banner(stringResource(R.string.opening_book), Modifier.padding(bottom = VoxDimens.space4))
                }
            }
            if (books.isEmpty()) {
                item(key = "empty") { EmptyLibrary(onAdd = onAddBook, modifier = Modifier.fillParentMaxHeight(0.75f)) }
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
                            chapterText = chapterText(featured),
                            remainingText = remainingSeconds?.takeIf { isActive }?.let { formatRemaining(it) },
                            modifier = Modifier.padding(bottom = VoxDimens.space6),
                        )
                    }
                }
                if (!streak.isEmpty) {
                    item(key = "streak") {
                        StreakCard(
                            title = pluralStringResource(R.plurals.streak_days, streak.streakDays, streak.streakDays),
                            subtitle = if (streak.weekSeconds > 0L) {
                                stringResource(R.string.streak_week, formatListened(streak.weekSeconds))
                            } else {
                                stringResource(R.string.streak_week_none)
                            },
                            days = streak.week.map { StreakDot(it.listened, it.isToday) },
                            modifier = Modifier.padding(bottom = VoxDimens.space6),
                        )
                    }
                }
                item(key = "section") {
                    SectionHeader(sort = sort, hideFinished = hideFinished, onSort = viewModel::setSort, onHideFinished = viewModel::setHideFinished)
                }
                item(key = "shelves") {
                    ShelfChips(shelf = shelf, counts = counts, onSelect = viewModel::setShelf)
                }
                if (others.isEmpty()) {
                    item(key = "shelf-empty") {
                        Text(
                            stringResource(R.string.library_shelf_empty),
                            style = t.body,
                            color = c.inkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = VoxDimens.space6),
                        )
                    }
                }
                items(others, key = { it.id }) { book ->
                    BookRow(
                        title = book.title,
                        author = book.author,
                        style = bookStyleFor(book.title),
                        coverFile = book.coverFile,
                        progressPercent = book.progressPercent,
                        finished = book.finished,
                        isPlaying = book.id == activeBookId && playing,
                        onOpen = { viewModel.openBook(book) },
                        onMenu = { menuBook = book },
                        onListen = { viewModel.listen(book) },
                    )
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
                    onOpen = { if (status == ReadAloudManager.Status.NeedsModel) onOpenVoices() else onNowPlaying() },
                    onPlayPause = viewModel.readAloud::togglePlayPause,
                    onStop = viewModel.readAloud::stop,
                )
            }
            MainNavBar(
                current = MainSection.HOME,
                onHome = { scope.launch { listState.animateScrollToItem(0) } },
                onNowPlaying = onNowPlaying,
                onAddBook = onAddBook,
                onSettings = onOpenSettings,
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
            onContents = {
                menuBook = null
                viewModel.openContents(book)
            },
            onToggleFinished = {
                menuBook = null
                viewModel.setFinished(book, !book.isFinished())
            },
            onInfo = {
                menuBook = null
                infoBook = book
            },
            onDelete = {
                menuBook = null
                bookToDelete = book
            },
        )
    }

    infoBook?.let { book -> BookInfoDialog(book = book, onDismiss = { infoBook = null }) }

    if (status == ReadAloudManager.Status.NeedsModel &&
        modelState !is VoiceModelManager.State.Downloading && modelState !is VoiceModelManager.State.Installing
    ) {
        VoxConfirmDialog(
            title = stringResource(R.string.tts_need_model_title),
            body = stringResource(R.string.tts_need_model_body, SupertonicModel.SIZE_MB),
            confirmLabel = stringResource(R.string.download),
            onConfirm = viewModel.readAloud::downloadVoices,
            onDismiss = viewModel.readAloud::stop,
            destructive = false,
        )
    }

    contents?.let { state ->
        ContentsSheet(
            entries = state.toc,
            current = null,
            highlights = state.highlights,
            onSelect = viewModel::listenFromChapter,
            onGoToHighlight = viewModel::listenFromHighlight,
            onEditNote = { highlight ->
                viewModel.closeContents()
                viewModel.openBook(highlight.bookId)
            },
            onDeleteHighlight = viewModel::deleteHighlight,
            onDismiss = viewModel::closeContents,
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

/** «2 h 40 min» o «35 min» escuchados. */
@Composable
private fun formatListened(seconds: Long): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes < 60) stringResource(R.string.listened_minutes, minutes) else stringResource(R.string.listened_hours_minutes, minutes / 60, minutes % 60)
}

/** «Capítulo 1 de 52» cuando la posición guardada trae el capítulo. */
@Composable
private fun chapterText(book: Book): String? {
    val index = book.chapterIndex ?: return null
    return stringResource(R.string.chapter_of, index, book.chapterCount ?: index)
}

/** «Tu biblioteca» con el control de orden («Recientes ▾»), que abre el menú «Ordenar por». */
@Composable
private fun SectionHeader(sort: LibrarySort, hideFinished: Boolean, onSort: (LibrarySort) -> Unit, onHideFinished: (Boolean) -> Unit) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        LibrarySort.RECENT to R.string.sort_recent,
        LibrarySort.TITLE to R.string.sort_title,
        LibrarySort.AUTHOR to R.string.sort_author,
        LibrarySort.PROGRESS to R.string.sort_progress,
    )
    Row(
        Modifier.fillMaxWidth().padding(bottom = VoxDimens.space2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.library_section), style = t.title, color = c.ink, modifier = Modifier.semantics { heading() })
        Box {
            val label = stringResource(R.string.sort_by)
            Surface(
                onClick = { expanded = true },
                modifier = Modifier.height(VoxDimens.sizeControl).semantics { contentDescription = label },
                shape = RoundedCornerShape(VoxDimens.radiusPill),
                color = c.surface,
                contentColor = c.ink,
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(options.first { it.first == sort }.second), style = t.label)
                    Icon(painterResource(R.drawable.ic_vx_chevron_down), contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            VoxDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, label = label) {
                options.forEach { (value, text) ->
                    VoxMenuItem(
                        stringResource(text),
                        onClick = {
                            onSort(value)
                            expanded = false
                        },
                        checked = value == sort,
                    )
                }
                VoxMenuSeparator()
                VoxMenuItem(
                    stringResource(R.string.sort_hide_finished),
                    onClick = {
                        onHideFinished(!hideFinished)
                        expanded = false
                    },
                    checked = hideFinished,
                )
            }
        }
    }
}

/** Estantes: Todos, Sin terminar, Nuevos y Terminados, cada uno con su cantidad. */
@Composable
private fun ShelfChips(shelf: LibraryShelf, counts: ShelfCounts, onSelect: (LibraryShelf) -> Unit) {
    val label = stringResource(R.string.shelf_label)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = VoxDimens.space2)
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = label }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2),
    ) {
        listOf(
            LibraryShelf.ALL to R.string.shelf_all,
            LibraryShelf.UNFINISHED to R.string.shelf_unfinished,
            LibraryShelf.NEW to R.string.shelf_new,
            LibraryShelf.FINISHED to R.string.shelf_finished,
        ).forEach { (value, text) ->
            VoxChip(stringResource(text, counts.of(value)), selected = value == shelf, onClick = { onSelect(value) }, onGround = true)
        }
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

/**
 * Opciones de un libro, al mantenerlo presionado (pantalla «Menú del libro» del lienzo): escuchar
 * desde el capítulo donde va, leer, capítulos y marcadores, marcar como terminado, información y eliminar.
 */
@Composable
private fun BookMenuSheet(
    book: Book,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onListen: () -> Unit,
    onRead: () -> Unit,
    onContents: () -> Unit,
    onToggleFinished: () -> Unit,
    onInfo: () -> Unit,
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
                Text(progressSummary(book), style = t.caption, color = c.inkMuted)
            }
        }
        Column {
            val listenLabel = when {
                isPlaying -> stringResource(R.string.pause)
                book.chapterIndex != null && !book.isFinished() -> stringResource(R.string.menu_listen_from_chapter, book.chapterIndex)
                else -> stringResource(R.string.listen)
            }
            VoxMenuItem(listenLabel, onListen, icon = if (isPlaying) R.drawable.ic_vx_pause else R.drawable.ic_vx_play)
            VoxMenuItem(stringResource(R.string.library_read), onRead, icon = R.drawable.ic_vx_book)
            VoxMenuItem(stringResource(R.string.menu_contents), onContents, icon = R.drawable.ic_vx_list)
            VoxMenuItem(
                stringResource(if (book.isFinished()) R.string.menu_mark_unfinished else R.string.menu_mark_finished),
                onToggleFinished,
                icon = R.drawable.ic_vx_check,
            )
            VoxMenuItem(stringResource(R.string.menu_info), onInfo, icon = R.drawable.ic_vx_info)
            VoxMenuSeparator()
            VoxMenuItem(stringResource(R.string.delete), onDelete, icon = R.drawable.ic_vx_trash, danger = true)
        }
    }
}

/** «Información del libro»: autor, idioma, cuándo se agregó, cuánto pesa, avance y capítulo. */
@Composable
private fun BookInfoDialog(book: Book, onDismiss: () -> Unit) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val unknown = stringResource(R.string.info_unknown)
    val size = remember(book.file) { Sizes.of(book.file) }
    val rows = listOf(
        R.string.info_author to (book.author ?: unknown),
        R.string.info_language to (book.language?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.forLanguageTag("es")).replaceFirstChar { ch -> ch.uppercase() } } ?: unknown),
        R.string.info_added to DateUtils.getRelativeTimeSpanString(book.addedAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString(),
        R.string.info_size to stringResource(R.string.megabytes, Sizes.megabytes(size)),
        R.string.info_progress to progressSummary(book),
    ) + listOfNotNull(book.chapterTitle?.let { R.string.info_chapter to it })
    VoxDialog(
        title = book.title,
        onDismissRequest = onDismiss,
        actions = { VoxButton(stringResource(R.string.close), onDismiss, variant = VoxButtonVariant.Secondary, size = VoxButtonSize.Small) },
    ) {
        rows.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(label), style = t.caption, color = c.inkMuted)
                Text(value, style = t.body, color = c.ink)
            }
        }
    }
}

@Composable
private fun progressSummary(book: Book): String {
    val percent = book.progressPercent
    return when {
        book.isFinished() -> stringResource(R.string.vx_finished)
        percent == null -> stringResource(R.string.vx_new)
        book.chapterIndex != null -> stringResource(R.string.chapter_progress, book.chapterIndex, book.chapterCount ?: book.chapterIndex, percent)
        else -> stringResource(R.string.vx_read_percent, percent)
    }
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
    is UiMessage.MarkedFinished -> context.getString(if (finished) R.string.msg_marked_finished else R.string.msg_marked_unfinished, title)
    UiMessage.NothingToPlay -> context.getString(R.string.library_nothing_playing)
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
