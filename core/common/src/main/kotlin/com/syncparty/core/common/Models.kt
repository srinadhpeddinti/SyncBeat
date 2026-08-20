package com.syncparty.core.common

import kotlinx.serialization.Serializable

/**
 * A local, offline "party" session. One HOST device + N CLIENT devices,
 * all on the same local network (no internet involved).
 */
@Serializable
data class Party(
    val id: String,
    val name: String,
    val hostDeviceId: String
)

enum class DeviceRole {
    HOST,
    CLIENT
}

enum class AudioOutput {
    BLUETOOTH_A2DP,
    WIRED_HEADSET,
    PHONE_SPEAKER,
    USB,
    UNKNOWN
}

@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val role: DeviceRole,
    val audioOutput: AudioOutput? = null,
    val isReady: Boolean = false
)

/**
 * A media item (audio OR video — Media3 handles both through the same
 * synchronized-playback pipeline). "Track" name kept for spec continuity,
 * but this represents any local media file that can be synchronized.
 */
@Serializable
data class Track(
    val id: String,
    val name: String,
    val artist: String? = null,
    val durationMs: Long,
    val localPath: String? = null,
    val sha256: String,
    val fileSizeBytes: Long,
    val isVideo: Boolean = false
)

/**
 * Authoritative playback state, owned by the HOST only.
 * Every command carries a monotonically increasing sequenceNumber;
 * clients must discard anything older than the last sequence number seen.
 */
@Serializable
data class PlaybackState(
    val sequenceNumber: Long,
    val trackId: String,
    val positionMs: Long,
    val isPlaying: Boolean,
    val playbackRate: Float = 1.0f,
    val hostTimestampMs: Long,
    val scheduledStartTimestampMs: Long? = null
)

/**
 * Result of one round of Cristian's-algorithm style clock sync
 * between a client and the host. See ClockSync in core:synchronization.
 */
@Serializable
data class ClockSyncResult(
    val offsetMs: Long,
    val roundTripTimeMs: Long
)

@Serializable
data class PlaybackReport(
    val deviceId: String,
    val positionMs: Long,
    val expectedPositionMs: Long,
    val driftMs: Long,
    val bufferedPositionMs: Long
)

enum class SyncQuality {
    EXCELLENT, // < 30ms drift
    GOOD,      // < 80ms drift
    DRIFTING,  // < 300ms drift, correcting
    RESYNCING  // >= 300ms drift, hard resync in progress
}

fun driftToSyncQuality(driftMs: Long): SyncQuality {
    val absDrift = kotlin.math.abs(driftMs)
    return when {
        absDrift < 30 -> SyncQuality.EXCELLENT
        absDrift < 80 -> SyncQuality.GOOD
        absDrift < 300 -> SyncQuality.DRIFTING
        else -> SyncQuality.RESYNCING
    }
}

/** Discovery info a client sees when browsing for parties, or scans from a QR code. */
@Serializable
data class DiscoveredHost(
    val partyId: String,
    val partyName: String,
    val hostDeviceName: String,
    val hostAddress: String,
    val port: Int
)
