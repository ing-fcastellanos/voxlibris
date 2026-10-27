package com.fcastellanos.voxlibris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

/*
 * Vistas previas para comparar los componentes con el Sistema de diseño VoxLibris y con la
 * pantalla «Inicio» del lienzo. Los textos son de ejemplo y no se muestran en la app.
 */

@Preview(name = "Componentes · claro", widthDp = 390, heightDp = 980)
@Composable
private fun ComponentsLightPreview() = ComponentsSample(darkTheme = false)

@Preview(name = "Componentes · oscuro", widthDp = 390, heightDp = 980)
@Composable
private fun ComponentsDarkPreview() = ComponentsSample(darkTheme = true)

@Composable
private fun ComponentsSample(darkTheme: Boolean) {
    VoxLibrisTheme(darkTheme = darkTheme) {
        Column(
            Modifier.background(VoxLibrisTheme.colors.ground).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ContinueCard(
                title = "Don Quijote de la Mancha",
                author = "Miguel de Cervantes",
                style = BookStyle(BookTint.Night, CoverShape.Disc),
                coverFile = null,
                progressPercent = 12,
                label = "Seguís con",
                isPlaying = false,
                statusText = null,
                voiceName = "Lucía",
                voiceId = 0,
                speed = 1.0,
                onOpen = {},
                onMenu = {},
                onListen = {},
                onVoice = {},
            )
            Column {
                BookRow("Niebla", "Miguel de Unamuno", BookStyle(BookTint.Cobalt, CoverShape.Orb), null, 34, false, {}, {}, {})
                BookRow("Platero y yo", "Juan Ramón Jiménez", BookStyle(BookTint.Mustard, CoverShape.HalfRound), null, null, false, {}, {}, {})
                BookRow("Martín Fierro", "José Hernández", BookStyle(BookTint.Pink, CoverShape.Diamond), null, 100, false, {}, {}, {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    BookStyle(BookTint.Mint, CoverShape.Stripes),
                    BookStyle(BookTint.Violet, CoverShape.Ring),
                    BookStyle(BookTint.Jade, CoverShape.Quarter),
                    BookStyle(BookTint.Tomato, CoverShape.Disc),
                    BookStyle(BookTint.Butter, CoverShape.Orb),
                ).forEach { BookCover("Facundo", it, null, CoverSize.Small) }
            }
            Banner("Importando 2 de 3…", detail = "Facundo.epub")
            MiniPlayer(
                title = "Don Quijote de la Mancha",
                statusText = "Leyendo en voz alta",
                style = BookStyle(BookTint.Night, CoverShape.Disc),
                coverFile = null,
                progress = 0.22f,
                isPlaying = true,
                canTogglePlayback = true,
                onOpen = {},
                onPlayPause = {},
                onStop = {},
            )
            NavBar(
                label = "Navegación principal",
                items = listOf(
                    NavBarItem("Inicio", R.drawable.ic_vx_home, selected = true) {},
                    NavBarItem("Agregar libro", R.drawable.ic_vx_plus, accent = true) {},
                    NavBarItem("Lectores", R.drawable.ic_vx_voice) {},
                ),
            )
        }
    }
}
