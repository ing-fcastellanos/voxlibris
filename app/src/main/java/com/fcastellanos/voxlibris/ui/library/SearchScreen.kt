package com.fcastellanos.voxlibris.ui.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import com.fcastellanos.voxlibris.ui.MainNavBar
import com.fcastellanos.voxlibris.ui.MainSection
import com.fcastellanos.voxlibris.ui.components.BookRow
import com.fcastellanos.voxlibris.ui.components.SectionLabel
import com.fcastellanos.voxlibris.ui.components.VoxChip
import com.fcastellanos.voxlibris.ui.components.VoxChipGroup
import com.fcastellanos.voxlibris.ui.components.VoxIconButton
import com.fcastellanos.voxlibris.ui.components.VoxIconButtonVariant
import com.fcastellanos.voxlibris.ui.components.bookStyleFor
import com.fcastellanos.voxlibris.ui.reader.ReaderActivity
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/**
 * Buscar (pantalla «Buscar» del lienzo): el campo arriba, los libros cuyo título o autor coinciden,
 * con la coincidencia subrayada, y las búsquedas recientes como chips.
 */
@Composable
fun SearchScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onNowPlaying: () -> Unit,
    onAddBook: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val recent by viewModel.recentSearches.collectAsStateWithLifecycle()
    val activeBookId by viewModel.readAloud.activeBookId.collectAsStateWithLifecycle()
    val status by viewModel.readAloud.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.OpenReader -> context.startActivity(ReaderActivity.intent(context, event.bookId))
            }
        }
    }

    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val playing = status == ReadAloudManager.Status.Playing || status == ReadAloudManager.Status.Preparing
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.ground)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = VoxDimens.space5,
                end = VoxDimens.space5,
                top = topInset + VoxDimens.space6,
                bottom = bottomInset + VoxDimens.space6 + VoxDimens.sizeNav + VoxDimens.space5,
            ),
        ) {
            item(key = "field") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
                    VoxIconButton(R.drawable.ic_vx_back, stringResource(R.string.back), onBack, variant = VoxIconButtonVariant.Clear, iconSize = 24.dp)
                    SearchField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        onSearch = viewModel::rememberSearch,
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                }
            }
            if (query.isNotBlank()) {
                item(key = "count") {
                    Text(
                        if (results.isEmpty()) stringResource(R.string.search_empty, query.trim()) else pluralStringResource(R.plurals.library_count, results.size, results.size),
                        style = t.caption,
                        color = c.inkMuted,
                        modifier = Modifier.padding(top = VoxDimens.space4, bottom = VoxDimens.space1),
                    )
                }
                items(results, key = { it.id }) { book ->
                    BookRow(
                        title = book.title,
                        author = book.author,
                        style = bookStyleFor(book.title),
                        coverFile = book.coverFile,
                        progressPercent = book.progressPercent,
                        finished = book.finished,
                        emphasis = query,
                        isPlaying = book.id == activeBookId && playing,
                        onOpen = {
                            viewModel.rememberSearch()
                            viewModel.openBook(book)
                        },
                        onMenu = {},
                        onListen = {
                            viewModel.rememberSearch()
                            viewModel.listen(book)
                        },
                    )
                }
            }
            if (recent.isNotEmpty()) {
                item(key = "recent") {
                    Column(Modifier.padding(top = VoxDimens.space6), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel(stringResource(R.string.search_recent))
                        VoxChipGroup {
                            recent.forEach { term ->
                                VoxChip(term, selected = false, onClick = { viewModel.setQuery(term) }, onGround = true)
                            }
                        }
                    }
                }
            }
        }
        MainNavBar(
            current = MainSection.HOME,
            onHome = onBack,
            onNowPlaying = onNowPlaying,
            onAddBook = onAddBook,
            onSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = VoxDimens.space5, end = VoxDimens.space5, bottom = VoxDimens.space6),
        )
    }
}

/** Campo de búsqueda (`vx-search`): píldora en `surface` con la lupa, el texto y borrar. */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val label = stringResource(R.string.library_search)
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(VoxDimens.radiusPill))
            .background(c.surface)
            .padding(start = VoxDimens.space4, end = VoxDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_vx_search), contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            textStyle = t.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(stringResource(R.string.search_placeholder), style = t.body, color = c.inkMuted)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            VoxIconButton(
                R.drawable.ic_vx_close,
                stringResource(R.string.search_clear),
                onClick = { onValueChange("") },
                variant = VoxIconButtonVariant.Clear,
                size = 40.dp,
                iconSize = 18.dp,
            )
        }
    }
}
