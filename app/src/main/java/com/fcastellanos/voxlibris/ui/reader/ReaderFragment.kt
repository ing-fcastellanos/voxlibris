package com.fcastellanos.voxlibris.ui.reader

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.VoxLibrisApp
import com.fcastellanos.voxlibris.di.AppContainer
import com.fcastellanos.voxlibris.tts.ReadAloudManager
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.AbsoluteUrl

/**
 * Aloja el [EpubNavigatorFragment] de Readium y, encima, la interfaz Compose del lector.
 * Sigue el patrón de la app de referencia de Readium: la fábrica de fragments se fija
 * en el FragmentManager hijo antes de `super.onCreate`.
 */
@OptIn(ExperimentalReadiumApi::class, FlowPreview::class)
class ReaderFragment : Fragment() {

    private val bookId: Long get() = requireArguments().getLong(ARG_BOOK_ID)
    private val container: AppContainer get() = (requireActivity().application as VoxLibrisApp).container
    private val viewModel: ReaderViewModel by activityViewModels { ReaderViewModel.factory(container, bookId) }

    private var navigator: EpubNavigatorFragment? = null
    private var navigatorContainer: FragmentContainerView? = null

    /** En Android 13+ la notificación con controles requiere permiso; se pide al tocar "Escuchar". */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.listen() }

    private fun onListen() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.listen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = container.readerRepository[bookId]
        if (session == null) {
            // Tras una muerte de proceso la publicación ya no está abierta: cerramos el lector.
            childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            requireActivity().finish()
            return
        }
        childFragmentManager.fragmentFactory = session.navigatorFactory.createFragmentFactory(
            initialLocator = session.initialLocator,
            initialPreferences = viewModel.preferences.value,
            listener = viewModel,
        )
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val root = FrameLayout(context)
        if (container.readerRepository[bookId] == null) return root

        val navigatorView = FragmentContainerView(context).apply { id = R.id.reader_navigator_container }
        ViewCompat.setOnApplyWindowInsetsListener(navigatorView) { view, insets ->
            val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val overlay = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VoxLibrisTheme {
                    ReaderOverlay(viewModel = viewModel, onBack = { requireActivity().finish() }, onListen = ::onListen)
                }
            }
        }
        val match = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(navigatorView, match)
        root.addView(overlay, match)
        navigatorContainer = navigatorView

        if (savedInstanceState == null) {
            childFragmentManager.commitNow {
                add(R.id.reader_navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
        }
        navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navigator = navigator ?: return

        // Toques en los bordes pasan de página; en el centro muestran u ocultan la interfaz.
        navigator.addInputListener(DirectionalNavigationAdapter(navigator, animatedTransition = true))
        navigator.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                viewModel.toggleOverlay()
                return true
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { navigator.currentLocator.collect { viewModel.onLocatorChanged(it) } }
                launch {
                    viewModel.preferences.collect { prefs ->
                        navigator.submitPreferences(prefs)
                        navigatorContainer?.setBackgroundColor(backgroundFor(prefs.theme))
                    }
                }
                launch {
                    viewModel.navigation.collect { request ->
                        when (request) {
                            is ReaderViewModel.Navigation.ToLocator -> navigator.go(request.locator, animated = false)
                            is ReaderViewModel.Navigation.ToLink -> navigator.go(request.link, animated = false)
                        }
                    }
                }
                launch { viewModel.externalLinks.collect { openExternal(it) } }
                launch { viewModel.overlayVisible.collect { setSystemBarsVisible(it) } }
                val readAloud = viewModel.readAloud
                val bookId = bookId
                launch {
                    combine(readAloud.activeBookId, readAloud.highlight) { activeId, locator ->
                        if (activeId == bookId) locator else null
                    }.distinctUntilChanged().collect { locator ->
                        val decorations = locator?.let {
                            listOf(Decoration(id = TTS_DECORATION_ID, locator = it, style = Decoration.Style.Highlight(tint = TTS_HIGHLIGHT_TINT, isActive = true)))
                        } ?: emptyList()
                        navigator.applyDecorations(decorations, TTS_DECORATION_GROUP)
                    }
                }
                launch {
                    readAloud.pageSync.sample(PAGE_SYNC_MS).collect { locator ->
                        if (readAloud.isActiveFor(bookId) && readAloud.status.value == ReadAloudManager.Status.Playing) {
                            navigator.go(locator, animated = true)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        navigator = null
        navigatorContainer = null
        super.onDestroyView()
    }

    private fun backgroundFor(theme: Theme?): Int = when (theme) {
        Theme.DARK -> Color.BLACK
        Theme.SEPIA -> SEPIA_BACKGROUND
        else -> Color.WHITE
    }

    private fun setSystemBarsVisible(visible: Boolean) {
        val window = activity?.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun openExternal(url: AbsoluteUrl) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toString().toUri()))
        } catch (e: ActivityNotFoundException) {
            // Sin navegador instalado: no hay nada razonable que hacer.
        }
    }

    companion object {
        const val TAG = "reader"
        private const val NAVIGATOR_TAG = "navigator"
        private const val ARG_BOOK_ID = "book_id"
        private const val SEPIA_BACKGROUND = 0xFFFAF4E8.toInt()
        private const val TTS_DECORATION_GROUP = "tts"
        private const val TTS_DECORATION_ID = "tts-utterance"
        private const val TTS_HIGHLIGHT_TINT = 0x66FFC107
        private const val PAGE_SYNC_MS = 800L

        fun arguments(bookId: Long): Bundle = Bundle().apply { putLong(ARG_BOOK_ID, bookId) }
    }
}
