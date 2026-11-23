package com.fcastellanos.voxlibris.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.components.NavBar
import com.fcastellanos.voxlibris.ui.components.NavBarItem

/** Secciones de la barra de navegación. Agregar libro es una acción, no una sección. */
enum class MainSection { HOME, NOW_PLAYING, SETTINGS }

/**
 * La barra flotante de toda la app (`vx-nav`): Inicio, Reproduciendo, Agregar libro y Ajustes.
 * [current] es la sección en pantalla, que muestra su nombre.
 */
@Composable
fun MainNavBar(
    current: MainSection,
    onHome: () -> Unit,
    onNowPlaying: () -> Unit,
    onAddBook: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavBar(
        label = stringResource(R.string.library_nav),
        modifier = modifier,
        items = listOf(
            NavBarItem(stringResource(R.string.library_nav_home), R.drawable.ic_vx_home, selected = current == MainSection.HOME, onClick = onHome),
            NavBarItem(stringResource(R.string.vx_now_playing), R.drawable.ic_vx_headphones, selected = current == MainSection.NOW_PLAYING, onClick = onNowPlaying),
            NavBarItem(stringResource(R.string.add_book), R.drawable.ic_vx_plus, accent = true, onClick = onAddBook),
            NavBarItem(stringResource(R.string.settings_title), R.drawable.ic_vx_settings, selected = current == MainSection.SETTINGS, onClick = onSettings),
        ),
    )
}
