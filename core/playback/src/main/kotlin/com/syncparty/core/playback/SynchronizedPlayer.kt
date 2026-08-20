package com.syncparty.core.playback

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over Media3/ExoPlayer that exposes scheduled, clock-aligned
 * playback operations (Section 15). Works for audio OR video URIs — video
 * rendering is attached separately via [attachVideoSurface] on the
 * PlayerView/SurfaceView the UI provides; audio-only playback simply never
 * calls that.
 *
 * All "At" methods take a LOCAL device timestamp (already converted from
 * host-clock time via ClockOffsetHolder.hostTimeToLocalTime()) and schedule
 * the action to occur precisely then, rather than immediately — this is
 * what makes multi-device playback actually synchronized (Section 11).
 */
interface SynchronizedPlayer {

    val state: StateFlow<PlayerState>

    suspend fun prepare(uri: Uri, isVideo: Boolean)

    /** Schedules playback to begin at [positionMs] once local clock reaches [timestampMs]. */
    suspend fun playAt(positionMs: Long, timestampMs: Long)

    /** Schedules a pause to take effect once local clock reaches [timestampMs]. */
    suspend fun pauseAt(timestampMs: Long)

    /** Schedules a seek to [positionMs], resuming playback at [timestampMs]. */
    suspend fun seekAt(positionMs: Long, timestampMs: Long)

    /** Applies a temporary playback-rate correction (drift correction, Section 17). */
    fun setPlaybackRate(rate: Float)

    fun currentPosition(): Long
    fun isPlaying(): Boolean
    fun bufferedPosition(): Long

    /** Attach a Compose-hosted PlayerView's surface for video output. No-op for audio-only tracks. */
    fun attachVideoSurface(playerView: Any?)

    fun release()
}

data class PlayerState(
    val isPrepared: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTrackId: String? = null,
    val playbackRate: Float = 1.0f,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L
)
