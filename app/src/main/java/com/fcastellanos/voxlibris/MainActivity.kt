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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.fcastellanos.voxlibris.ui.library.SearchScreen
import com.fcastellanos.voxlibris.ui.onboarding.OnboardingScreen
import com.fcastellanos.voxlibris.ui.onboarding.OnboardingViewModel
import com.fcastellanos.voxlibris.ui.player.PlayerScreen
import com.fcastellanos.voxlibris.ui.player.PlayerViewModel
import com.fcastellanos.voxlibris.ui.settings.SettingsScreen
import com.fcastellanos.voxlibris.ui.settings.SettingsViewModel
import com.fcastellanos.voxlibris.ui.settings.StorageScreen
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import com.fcastellanos.voxlibris.ui.voices.DefaultVoiceScreen
import com.fcastellanos.voxlibris.ui.voices.VoicesViewModel

/** Pantallas de la actividad principal; el lector es otra actividad. */
private enum class MainScreen { LIBRARY, SEARCH, PLAYER, SETTINGS, DEFAULT_VOICE, STORAGE }

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
                    MainScreens(darkTheme)
                }
            }
        }
    }

    @Composable
    private fun MainScreens(darkTheme: Boolean) {
        var screen by rememberSaveable { mutableStateOf(MainScreen.LIBRARY) }
        var playerBookId by rememberSaveable { mutableLongStateOf(-1L) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.importAll(uris)
            screen = MainScreen.LIBRARY
        }
        val addBook = { picker.launch(EPUB_MIME_TYPES) }
        val nowPlaying = {
            val target = viewModel.nowPlayingTarget()
            if (target != null) {
                playerBookId = target
                screen = MainScreen.PLAYER
            } else {
                screen = MainScreen.LIBRARY
            }
        }
        // Si desde la notificación empieza a sonar otro libro, el reproductor lo sigue.
        val activeBookId by container.readAloud.activeBookId.collectAsStateWithLifecycle()
        LaunchedEffect(activeBookId) {
            if (screen == MainScreen.PLAYER && activeBookId != null && activeBookId != playerBookId) playerBookId = activeBookId!!
        }
        when (screen) {
            MainScreen.PLAYER -> {
                val playerViewModel: PlayerViewModel = viewModel(
                    key = "player-$playerBookId",
                    factory = PlayerViewModel.factory(container, playerBookId),
                )
                PlayerScreen(
                    viewModel = playerViewModel,
                    darkTheme = darkTheme,
                    onMinimize = { screen = MainScreen.LIBRARY },
                    onListen = viewModel::listen,
                )
            }
            MainScreen.LIBRARY -> LibraryScreen(
                viewModel = viewModel,
                onAddBook = addBook,
                onNowPlaying = nowPlaying,
                onOpenSettings = { screen = MainScreen.SETTINGS },
                onOpenVoices = { screen = MainScreen.DEFAULT_VOICE },
                onSearch = { screen = MainScreen.SEARCH },
            )
            MainScreen.SEARCH -> SearchScreen(
                viewModel = viewModel,
                onBack = { screen = MainScreen.LIBRARY },
                onNowPlaying = nowPlaying,
                onAddBook = addBook,
                onOpenSettings = { screen = MainScreen.SETTINGS },
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
