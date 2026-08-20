package com.syncparty.service.playback

import android.app.Notification
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps synchronized playback alive when the screen locks or the app is
 * backgrounded (Section 27). Wraps the SAME ExoPlayer instance driven by
 * Media3SynchronizedPlayer — the service does not own scheduling logic
 * itself, it only keeps the process/player alive and exposes system media
 * controls (notification, lock-screen controls) via MediaSession.
 *
 * The HostPartyEngine / ClientPartyEngine continue to run in the app
 * process; this service's only job is the Android lifecycle contract
 * required for uninterrupted background audio.
 */
class SyncPartyPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // The actual ExoPlayer is provided by the app layer through a singleton
    // holder (PlayerProvider) so the same player instance used for
    // synchronized scheduling backs this session — no duplicate player.
    override fun onCreate() {
        super.onCreate()
        val player = PlayerHolder.player ?: ExoPlayer.Builder(this).build().also {
            PlayerHolder.player = it
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

/**
 * Process-wide holder so the ExoPlayer instance created by
 * Media3SynchronizedPlayer (app/ViewModel layer) can be reused by the
 * foreground service's MediaSession instead of creating a second player.
 */
object PlayerHolder {
    var player: ExoPlayer? = null
}
