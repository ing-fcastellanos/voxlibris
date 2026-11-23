package com.fcastellanos.voxlibris

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fcastellanos.voxlibris.onboarding.OnboardingStore
import com.fcastellanos.voxlibris.settings.ThemeMode
import com.fcastellanos.voxlibris.ui.library.LibraryScreen
import com.fcastellanos.voxlibris.ui.library.LibraryViewModel
import com.fcastellanos.voxlibris.ui.onboarding.OnboardingScreen
import com.fcastellanos.voxlibris.ui.onboarding.OnboardingViewModel
import com.fcastellanos.voxlibris.ui.settings.SettingsScreen
import com.fcastellanos.voxlibris.ui.settings.SettingsViewModel
import com.fcastellanos.voxlibris.ui.settings.StorageScreen
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import com.fcastellanos.voxlibris.ui.voices.DefaultVoiceScreen
import com.fcastellanos.voxlibris.ui.voices.VoicesViewModel

/** Pantallas de la actividad principal; el lector es otra actividad. */
private enum class MainScreen { LIBRARY, SETTINGS, DEFAULT_VOICE, STORAGE }

private val EPUB_MIME_TYPES = arrayOf("application/epub+zip")

class MainActivity : ComponentActivity() {

    private val container get() = (application as VoxLibrisApp).container

    private val viewModel: LibraryViewModel by viewModels { LibraryViewModel.factory(container) }

    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(container) }

    private val onboarding by lazy { OnboardingStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) importFromViewIntent(intent)
        setContent {
            val themeMode by container.appPreferences.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            VoxLibrisTheme(darkTheme = darkTheme) {
                val onboardingDone by onboarding.completed.collectAsStateWithLifecycle()
                if (!onboardingDone) {
                    val onboardingViewModel: OnboardingViewModel =
                        viewModel(factory = OnboardingViewModel.factory(container.voiceModels, onboarding))
                    OnboardingScreen(viewModel = onboardingViewModel)
                } else {
                    MainScreens()
                }
            }
        }
    }

    @Composable
    private fun MainScreens() {
        var screen by rememberSaveable { mutableStateOf(MainScreen.LIBRARY) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.importAll(uris)
            screen = MainScreen.LIBRARY
        }
        val addBook = { picker.launch(EPUB_MIME_TYPES) }
        val nowPlaying = {
            screen = MainScreen.LIBRARY
            viewModel.openNowPlaying()
        }
        when (screen) {
            MainScreen.LIBRARY -> LibraryScreen(
                viewModel = viewModel,
                onAddBook = addBook,
                onNowPlaying = nowPlaying,
                onOpenSettings = { screen = MainScreen.SETTINGS },
                onOpenVoices = { screen = MainScreen.DEFAULT_VOICE },
            )
            MainScreen.SETTINGS -> SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { screen = MainScreen.LIBRARY },
                onNowPlaying = nowPlaying,
                onAddBook = addBook,
                onOpenDefaultVoice = { screen = MainScreen.DEFAULT_VOICE },
                onOpenStorage = { screen = MainScreen.STORAGE },
            )
            MainScreen.DEFAULT_VOICE -> {
                val voicesViewModel: VoicesViewModel = viewModel(
                    factory = VoicesViewModel.factory(container, stringResource(R.string.sample_sentence), stringResource(R.string.sample_dialogue)),
                )
                DefaultVoiceScreen(viewModel = voicesViewModel, onBack = { screen = MainScreen.SETTINGS })
            }
            MainScreen.STORAGE -> StorageScreen(viewModel = settingsViewModel, onBack = { screen = MainScreen.SETTINGS })
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
