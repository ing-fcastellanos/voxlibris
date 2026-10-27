package com.fcastellanos.voxlibris

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.fcastellanos.voxlibris.ui.library.LibraryScreen
import com.fcastellanos.voxlibris.ui.library.LibraryViewModel
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LibraryViewModel by viewModels {
        LibraryViewModel.factory((application as VoxLibrisApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) importFromViewIntent(intent)
        setContent {
            VoxLibrisTheme {
                LibraryScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importFromViewIntent(intent)
    }

    /** Un EPUB abierto con "Abrir con VoxLibrisApp" se importa directamente. */
    private fun importFromViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let { viewModel.importAll(listOf(it)) }
    }
}
