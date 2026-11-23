package com.fcastellanos.voxlibris.ui.settings

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fcastellanos.voxlibris.R
import com.fcastellanos.voxlibris.tts.SupertonicModel
import com.fcastellanos.voxlibris.tts.VoiceModelManager
import com.fcastellanos.voxlibris.ui.components.ProgressBar
import com.fcastellanos.voxlibris.ui.components.SectionLabel
import com.fcastellanos.voxlibris.ui.components.SettingsChevron
import com.fcastellanos.voxlibris.ui.components.SettingsDivider
import com.fcastellanos.voxlibris.ui.components.SettingsList
import com.fcastellanos.voxlibris.ui.components.SettingsRow
import com.fcastellanos.voxlibris.ui.components.SettingsValue
import com.fcastellanos.voxlibris.ui.components.Tag
import com.fcastellanos.voxlibris.ui.components.VoxConfirmDialog
import com.fcastellanos.voxlibris.ui.components.VoxIconButton
import com.fcastellanos.voxlibris.ui.components.VoxIconButtonVariant
import com.fcastellanos.voxlibris.ui.components.VoxSnackbar
import com.fcastellanos.voxlibris.ui.components.VoxSnackbarVisuals
import com.fcastellanos.voxlibris.ui.theme.VoxDimens
import com.fcastellanos.voxlibris.ui.theme.VoxLibrisTheme
import com.fcastellanos.voxlibris.util.Sizes

/**
 * Voces y almacenamiento (pantalla homónima del lienzo): cuánto ocupan las voces y los libros,
 * el estado del paquete de voces, los restos de descargas e importaciones, y eliminar las voces.
 */
@Composable
fun StorageScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val usage by viewModel.storage.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val checkedAt by viewModel.voicesCheckedAt.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var offerUpdate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.refreshStorage() }
    LaunchedEffect(modelState) { if (modelState is VoiceModelManager.State.Ready || modelState is VoiceModelManager.State.NotInstalled) viewModel.refreshStorage() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                SettingsViewModel.Message.VoicesUpToDate -> snackbarHostState.showSnackbar(VoxSnackbarVisuals(context.getString(R.string.storage_up_to_date)))
                SettingsViewModel.Message.TemporaryFilesCleared -> snackbarHostState.showSnackbar(VoxSnackbarVisuals(context.getString(R.string.storage_temp_cleared)))
                SettingsViewModel.Message.VoicesUpdateAvailable -> offerUpdate = true
            }
        }
    }

    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val installed = modelState is VoiceModelManager.State.Ready
    val voicesBytes = usage?.voicesBytes ?: 0L
    val booksBytes = usage?.booksBytes ?: 0L

    Box(Modifier.fillMaxSize().background(c.ground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = VoxDimens.space5, vertical = VoxDimens.space3),
            verticalArrangement = Arrangement.spacedBy(VoxDimens.space2),
        ) {
            VoxIconButton(R.drawable.ic_vx_back, stringResource(R.string.back), onBack, variant = VoxIconButtonVariant.Clear)
            Text(
                stringResource(R.string.settings_storage),
                style = t.displayXl,
                color = c.ink,
                modifier = Modifier.padding(bottom = VoxDimens.space2).semantics { heading() },
            )

            UsageCard(voicesBytes = voicesBytes, booksBytes = booksBytes)

            SectionLabel(stringResource(R.string.storage_section_voices), Modifier.padding(top = VoxDimens.space2))
            SettingsList(onGround = true) {
                SettingsRow(
                    title = stringResource(R.string.storage_voice_pack),
                    subtitle = stringResource(
                        R.string.storage_voice_pack_detail,
                        SupertonicModel.VOICES.size,
                        stringResource(R.string.megabytes, Sizes.megabytes(if (installed) voicesBytes else SupertonicModel.SIZE_BYTES)),
                    ),
                    leading = {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c.surfaceMuted),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_vx_voice), contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                        }
                    },
                ) {
                    if (installed) {
                        Tag(stringResource(R.string.storage_installed), containerColor = c.mint, contentColor = c.onColor)
                    } else {
                        Tag(stringResource(R.string.storage_not_installed))
                    }
                }
                if (installed) {
                    SettingsDivider()
                    SettingsRow(
                        title = stringResource(R.string.storage_check_updates),
                        subtitle = checkedAt?.let {
                            stringResource(R.string.storage_last_checked, DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString())
                        } ?: stringResource(R.string.storage_never_checked),
                        onClick = viewModel::checkVoices,
                    ) { SettingsChevron() }
                }
            }

            SectionLabel(stringResource(R.string.storage_section_books), Modifier.padding(top = VoxDimens.space2))
            SettingsList(onGround = true) {
                val count = usage?.bookCount ?: 0
                SettingsRow(
                    title = pluralStringResource(R.plurals.library_count, count, count),
                    subtitle = stringResource(R.string.storage_books_detail),
                ) { SettingsValue(stringResource(R.string.megabytes, Sizes.megabytes(booksBytes))) }
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.storage_clear_temp),
                    subtitle = stringResource(R.string.storage_clear_temp_hint),
                    onClick = viewModel::clearTemporaryFiles,
                ) { SettingsValue(stringResource(R.string.megabytes, Sizes.megabytes(usage?.tempBytes ?: 0L))) }
            }

            if (installed) {
                SettingsList(Modifier.padding(top = VoxDimens.space4), onGround = true) {
                    SettingsRow(
                        title = stringResource(R.string.storage_delete_voices),
                        subtitle = stringResource(R.string.storage_delete_voices_hint),
                        titleColor = c.danger,
                        onClick = { confirmDelete = true },
                    )
                }
            }
        }
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = VoxDimens.space5, end = VoxDimens.space5, bottom = VoxDimens.space6),
        ) { VoxSnackbar(it) }
    }

    if (confirmDelete) {
        VoxConfirmDialog(
            title = stringResource(R.string.storage_delete_voices_title),
            body = stringResource(R.string.storage_delete_voices_body, stringResource(R.string.megabytes, Sizes.megabytes(voicesBytes))),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteVoices()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (offerUpdate) {
        VoxConfirmDialog(
            title = stringResource(R.string.storage_update_available_title),
            body = stringResource(R.string.storage_update_available_body, SupertonicModel.SIZE_MB),
            confirmLabel = stringResource(R.string.storage_update_now),
            onConfirm = {
                offerUpdate = false
                viewModel.updateVoices()
            },
            onDismiss = { offerUpdate = false },
            destructive = false,
        )
    }
}

/** Cuánto hay en uso, con la barra repartida entre voces (`tomato`) y libros (`cobalt`) y su leyenda. */
@Composable
private fun UsageCard(voicesBytes: Long, booksBytes: Long) {
    val c = VoxLibrisTheme.colors
    val t = VoxLibrisTheme.typography
    val total = voicesBytes + booksBytes
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VoxDimens.radiusXl))
            .background(c.surface)
            .padding(VoxDimens.space4),
        verticalArrangement = Arrangement.spacedBy(VoxDimens.space3),
    ) {
        Text(stringResource(R.string.storage_in_use, stringResource(R.string.megabytes, Sizes.megabytes(total))), style = t.emphasis, color = c.ink)
        if (total > 0) {
            Row(Modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (voicesBytes > 0) UsageSegment(voicesBytes.toFloat() / total, c.tomato)
                if (booksBytes > 0) UsageSegment(booksBytes.toFloat() / total, c.cobalt)
            }
        } else {
            ProgressBar(0f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VoxDimens.space4)) {
            Legend(c.tomato, stringResource(R.string.storage_voices_legend, stringResource(R.string.megabytes, Sizes.megabytes(voicesBytes))))
            Legend(c.cobalt, stringResource(R.string.storage_books_legend, stringResource(R.string.megabytes, Sizes.megabytes(booksBytes))))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.UsageSegment(fraction: Float, color: Color) {
    Box(
        Modifier
            .weight(fraction.coerceAtLeast(0.02f))
            .fillMaxSize()
            .clip(RoundedCornerShape(VoxDimens.radiusPill))
            .background(color),
    )
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, style = VoxLibrisTheme.typography.caption, color = VoxLibrisTheme.colors.inkMuted)
    }
}
