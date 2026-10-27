package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import java.io.File

/**
 * Fila de la biblioteca (`vx-book-row`): portada chica, título, autor, avance y un play con el
 * color del libro. Tocar la fila abre el libro; mantenerla presionada abre sus opciones; el play
 * empieza a escuchar sin salir de la lista.
 */
@Composable
fun BookRow(
    title: String,
    author: String?,
    style: BookStyle,
    coverFile: File?,
    progressPercent: Int?,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onListen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoxDimens.space3),
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(VoxDimens.radiusM))
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.vx_open_book, title),
                    onLongClickLabel = stringResource(R.string.vx_book_actions, title),
                    onLongClick = onMenu,
                    onClick = onOpen,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BookCover(title, style, coverFile, CoverSize.Small)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = t.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (author != null) {
                    Text(author, style = t.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BookProgress(progressPercent, Modifier.padding(top = 4.dp))
            }
        }
        val tint = style.tint.colors
        PlayButton(
            playing = isPlaying,
            onClick = onListen,
            contentDescription = stringResource(if (isPlaying) R.string.vx_pause_book else R.string.vx_listen_to, title),
            size = PlayButtonSize.Small,
            containerColor = tint.container,
            contentColor = tint.content,
        )
    }
}

/** Avance de un libro: «Nuevo» si nunca se abrió, «Terminado» al 100 %, y si no la barra con el porcentaje. */
@Composable
fun BookProgress(percent: Int?, modifier: Modifier = Modifier) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    when {
        percent == null -> Text(stringResource(R.string.vx_new), modifier = modifier, style = t.caption, color = c.inkMuted)
        percent >= 100 -> Text(stringResource(R.string.vx_finished), modifier = modifier, style = t.caption, color = c.inkMuted)
        else -> Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoxDimens.space2)) {
            ProgressBar(percent / 100f, Modifier.weight(1f), thin = true)
            Text(stringResource(R.string.progress_percent, percent), style = t.caption, color = c.inkMuted)
        }
    }
}
