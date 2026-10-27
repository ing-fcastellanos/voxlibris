package com.fcastellanos.readapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.fcastellanos.readapp.ui.library.LibraryScreen
import com.fcastellanos.readapp.ui.library.LibraryViewModel
import com.fcastellanos.readapp.ui.theme.ReadAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LibraryViewModel by viewModels {
        LibraryViewModel.factory((application as ReadApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) importFromViewIntent(intent)
        setContent {
            ReadAppTheme {
                LibraryScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importFromViewIntent(intent)
    }

    /** Un EPUB abierto con "Abrir con ReadApp" se importa directamente. */
    private fun importFromViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let { viewModel.importAll(listOf(it)) }
    }
}
