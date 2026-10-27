package com.fcastellanos.voxlibris

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fcastellanos.voxlibris.ui.library.LibraryScreen
import com.fcastellanos.voxlibris.ui.library.LibraryViewModel
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import com.fcastellanos.voxlibris.ui.voices.VoicesScreen
import com.fcastellanos.voxlibris.ui.voices.VoicesViewModel

class MainActivity : ComponentActivity() {

    private val container get() = (application as VoxLibrisApp).container

    private val viewModel: LibraryViewModel by viewModels { LibraryViewModel.factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) importFromViewIntent(intent)
        setContent {
            VoxLibrisTheme {
                var showVoices by rememberSaveable { mutableStateOf(false) }
                if (showVoices) {
                    val sample = stringResource(R.string.sample_sentence)
                    val voicesViewModel: VoicesViewModel = viewModel(factory = VoicesViewModel.factory(container, sample))
                    VoicesScreen(viewModel = voicesViewModel, onBack = { showVoices = false })
                } else {
                    LibraryScreen(viewModel = viewModel, onOpenVoices = { showVoices = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importFromViewIntent(intent)
    }

    /** Un EPUB abierto con "Abrir con VoxLibris" se importa directamente. */
    private fun importFromViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let { viewModel.importAll(listOf(it)) }
    }
}
