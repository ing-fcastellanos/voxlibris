package com.fcastellanos.voxlibris.tts

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.fcastellanos.voxlibris.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Servicio de sesión multimedia. Media3 se encarga de la notificación con controles, la pantalla
 * de bloqueo y de pasar el servicio a primer plano mientras el reproductor esté sonando.
 * El reproductor es el adaptador Media3 del TtsNavigator de Readium.
 */
class ReadAloudService : MediaSessionService() {

    class Session(val bookId: Long, val mediaSession: MediaSession)

    inner class LocalBinder : Binder() {
        private val _session = MutableStateFlow<Session?>(null)
        val session: StateFlow<Session?> = _session.asStateFlow()

        fun openSession(bookId: Long, player: Player) {
            closeSession()
            val mediaSession = MediaSession.Builder(this@ReadAloudService, player)
                .setId("voxlibris-$bookId")
                .setSessionActivity(readerPendingIntent(bookId))
                .build()
            addSession(mediaSession)
            _session.value = Session(bookId, mediaSession)
        }

        fun closeSession() {
            _session.value?.let {
                removeSession(it.mediaSession)
                it.mediaSession.release()
            }
            _session.value = null
        }

        fun stop() {
            closeSession()
            ServiceCompat.stopForeground(this@ReadAloudService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private val localBinder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_LOCAL) localBinder else super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        localBinder.session.value?.mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Si el usuario descarta la app y no se está leyendo, no tiene sentido seguir vivos.
        val player = localBinder.session.value?.mediaSession?.player
        if (player == null || !player.playWhenReady) localBinder.stop()
    }

    override fun onDestroy() {
        localBinder.closeSession()
        super.onDestroy()
    }

    private fun readerPendingIntent(bookId: Long): PendingIntent =
        TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(ReaderActivity.intent(this, bookId))
            .getPendingIntent(bookId.toInt(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!

    companion object {
        private const val ACTION_LOCAL = "com.fcastellanos.voxlibris.tts.LOCAL"

        fun intent(context: Context): Intent =
            Intent(context, ReadAloudService::class.java).setAction(ACTION_LOCAL)
    }
}
