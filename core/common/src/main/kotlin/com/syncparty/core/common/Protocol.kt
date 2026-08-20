package com.syncparty.core.common

import kotlinx.serialization.Serializable

/**
 * All control-plane messages exchanged over SyncTransport (TCP/local socket).
 * File bytes for track transfer are NOT sent as base64 inside these messages —
 * see LocalAudioTransfer / binary chunk framing in core:audiotransfer.
 */
@Serializable
sealed class SyncMessage {

    // ---- Connection / membership ----

    @Serializable
    data class Hello(
        val deviceId: String,
        val deviceName: String,
        val partyToken: String
    ) : SyncMessage()

    @Serializable
    data class Welcome(
        val partyId: String,
        val partyName: String,
        val assignedDeviceId: String,
        val hostDeviceName: String
    ) : SyncMessage()

    @Serializable
    data class Rejected(val reason: String) : SyncMessage()

    @Serializable
    data class DeviceListUpdate(val devices: List<DeviceInfo>) : SyncMessage()

    @Serializable
    data class Leave(val deviceId: String) : SyncMessage()

    // ---- Clock sync (Section 12) ----

    @Serializable
    data class ClockSyncRequest(val clientSendTimestampMs: Long) : SyncMessage()

    @Serializable
    data class ClockSyncResponse(
        val clientSendTimestampMs: Long, // T1, echoed back
        val hostReceiveTimestampMs: Long, // T2
        val hostSendTimestampMs: Long     // T3
    ) : SyncMessage()

    // ---- Track lifecycle (Section 10) ----

    @Serializable
    data class TrackInfo(
        val trackId: String,
        val fileName: String,
        val fileSize: Long,
        val sha256: String,
        val durationMs: Long,
        val isVideo: Boolean,
        val filePort: Int
    ) : SyncMessage()

    @Serializable
    data class FileRequest(val trackId: String, val haveBytes: Long = 0) : SyncMessage()

    @Serializable
    data class TransferComplete(val trackId: String) : SyncMessage()

    @Serializable
    data class ChecksumVerify(val trackId: String, val sha256Matches: Boolean) : SyncMessage()

    @Serializable
    data class TrackReady(val trackId: String, val deviceId: String) : SyncMessage()

    // ---- Playback commands, host -> clients (Sections 14, 19, 20, 21) ----

    @Serializable
    data class PlayAt(
        val sequenceNumber: Long,
        val trackId: String,
        val startPositionMs: Long,
        val scheduledStartTimestampMs: Long // in HOST clock time
    ) : SyncMessage()

    @Serializable
    data class PauseAt(
        val sequenceNumber: Long,
        val pausePositionMs: Long,
        val scheduledPauseTimestampMs: Long
    ) : SyncMessage()

    @Serializable
    data class SeekAt(
        val sequenceNumber: Long,
        val positionMs: Long,
        val scheduledStartTimestampMs: Long
    ) : SyncMessage()

    @Serializable
    data class SetPlaybackRate(
        val sequenceNumber: Long,
        val rate: Float
    ) : SyncMessage()

    @Serializable
    data class NextTrackPrepare(val trackId: String) : SyncMessage()

    @Serializable
    data class StateSnapshot(val state: PlaybackState) : SyncMessage()

    // ---- Client -> host reporting (Section 16, 18) ----

    @Serializable
    data class ClientReady(val deviceId: String, val trackId: String) : SyncMessage()

    @Serializable
    data class ClientNotReady(val deviceId: String, val trackId: String, val reason: String) : SyncMessage()

    @Serializable
    data class PlaybackReportMsg(val report: PlaybackReport) : SyncMessage()

    @Serializable
    data class AudioOutputChanged(val deviceId: String, val output: AudioOutput) : SyncMessage()

    // ---- Heartbeat ----

    @Serializable
    data class Ping(val timestampMs: Long) : SyncMessage()

    @Serializable
    data class Pong(val timestampMs: Long) : SyncMessage()
}
