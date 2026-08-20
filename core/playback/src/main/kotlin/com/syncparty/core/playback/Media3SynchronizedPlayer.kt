package com.syncparty.core.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Media3/ExoPlayer-backed implementation.
 *
 * Scheduling approach: rather than polling, we compute the exact delay
 * (timestampMs - now) and post a single delayed Runnable on the main looper
 * to flip play state. ExoPlayer itself is kept fully prepared and paused
 * ahead of time (Section 18: "clients buffer, then send ready") so the only
 * action taken at the scheduled instant is `player.play()` / `player.pause()`
 * / `player.seekTo()` — minimizing the jitter between "should start" and
 * "actually starts."
 *
 * Even so, Handler.postDelayed has best-effort precision (typically single-
 * digit ms jitter on modern Android, but not hard-real-time). Combined with
 * clock sync error and Bluetooth output latency (Section 32), do not expect
 * sub-10ms accuracy across devices — the drift corrector (Section 17) exists
 * specifically to clean up the residual error continuously.
 */
class Media3SynchronizedPlayer(private val context: Context) : SynchronizedPlayer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingScheduledAction: Runnable? = null

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(isPlaying = isPlaying)
                }
            })
        }
    }

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    override suspend fun prepare(uri: Uri, isVideo: Boolean) {
        cancelPending()
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playWhenReady = false
        exoPlayer.prepare()
        _state.value = _state.value.copy(
            isPrepared = true,
            isVideo = isVideo,
            currentTrackId = uri.toString()
        )
    }

    override suspend fun playAt(positionMs: Long, timestampMs: Long) {
        cancelPending()
        exoPlayer.seekTo(positionMs)
        exoPlayer.playWhenReady = false

        val delay = (timestampMs - System.currentTimeMillis()).coerceAtLeast(0)
        val action = Runnable {
            exoPlayer.play()
        }
        pendingScheduledAction = action
        mainHandler.postDelayed(action, delay)
    }

    override suspend fun pauseAt(timestampMs: Long) {
        cancelPending()
        val delay = (timestampMs - System.currentTimeMillis()).coerceAtLeast(0)
        val action = Runnable {
            exoPlayer.pause()
        }
        pendingScheduledAction = action
        mainHandler.postDelayed(action, delay)
    }

    override suspend fun seekAt(positionMs: Long, timestampMs: Long) {
        cancelPending()
        // Pre-seek immediately so buffering happens now, ahead of the
        // scheduled instant — same "prepare early, flip late" strategy.
        exoPlayer.seekTo(positionMs)
        exoPlayer.playWhenReady = false

        val delay = (timestampMs - System.currentTimeMillis()).coerceAtLeast(0)
        val action = Runnable {
            exoPlayer.play()
        }
        pendingScheduledAction = action
        mainHandler.postDelayed(action, delay)
    }

    override fun setPlaybackRate(rate: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(rate)
        _state.value = _state.value.copy(playbackRate = rate)
    }

    override fun currentPosition(): Long = exoPlayer.currentPosition

    override fun isPlaying(): Boolean = exoPlayer.isPlaying

    override fun bufferedPosition(): Long = exoPlayer.bufferedPosition

    override fun attachVideoSurface(playerView: Any?) {
        (playerView as? PlayerView)?.player = exoPlayer
    }

    override fun release() {
        cancelPending()
        exoPlayer.release()
    }

    private fun cancelPending() {
        pendingScheduledAction?.let { mainHandler.removeCallbacks(it) }
        pendingScheduledAction = null
    }
}
