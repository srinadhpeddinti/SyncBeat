package com.syncparty.core.partyengine

import com.syncparty.core.common.AudioOutput
import com.syncparty.core.common.DeviceInfo
import com.syncparty.core.common.DeviceRole
import com.syncparty.core.common.HostTransport
import com.syncparty.core.common.PlaybackReport
import com.syncparty.core.common.SyncMessage
import com.syncparty.core.synchronization.ClockSyncResponder
import com.syncparty.core.synchronization.SequenceGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** How far in the future a PLAY_AT/SEEK_AT is scheduled, giving clients time to prepare (Section 11). */
private const val SCHEDULE_LEAD_TIME_MS = 3000L

data class HostPartyUiState(
    val partyId: String = "",
    val partyName: String = "",
    val devices: List<DeviceInfo> = emptyList(),
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val waitingForDeviceIds: Set<String> = emptySet(),
    val localAudioOutput: AudioOutput = AudioOutput.UNKNOWN
)

/**
 * Runs on the HOST device. Owns the authoritative PlaybackState and issues
 * PLAY_AT/PAUSE_AT/SEEK_AT commands with sequence numbers (Section 13/14).
 * Also answers client clock-sync requests (Section 12) and tracks per-client
 * readiness before starting playback (Section 18).
 */
class HostPartyEngine(
    private val hostTransport: HostTransport,
    partyName: String
) {
    val partyId: String = Random.nextInt(100000, 999999).toString() // 6-digit party code, Section 5
    // The party code doubles as the shared auth token (Section 39): it's
    // already the one piece of information every legitimate client learns
    // (typed in, scanned from QR, or resolved via NSD+manual entry), so
    // requiring it again as a "secret" adds friction without adding real
    // security for a same-room local party. A longer random UUID token
    // remains straightforward to swap in later via this single property.
    val token: String get() = partyId

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sequenceGenerator = SequenceGenerator()

    private val _uiState = MutableStateFlow(HostPartyUiState(partyId = partyId, partyName = partyName))
    val uiState: StateFlow<HostPartyUiState> = _uiState.asStateFlow()

    private val deviceReadiness = mutableMapOf<String, Boolean>()

    fun start() {
        scope.launch {
            hostTransport.clientMessages().collect { (deviceId, message) ->
                handleMessage(deviceId, message)
            }
        }
    }

    private suspend fun handleMessage(deviceId: String, message: SyncMessage) {
        when (message) {
            is SyncMessage.Hello -> {
                val device = DeviceInfo(id = deviceId, name = deviceId, role = DeviceRole.CLIENT)
                _uiState.value = _uiState.value.copy(devices = _uiState.value.devices + device)
                hostTransport.sendTo(
                    deviceId,
                    SyncMessage.Welcome(
                        partyId = partyId,
                        partyName = _uiState.value.partyName,
                        assignedDeviceId = deviceId,
                        hostDeviceName = android.os.Build.MODEL ?: "Host"
                    )
                )
                broadcastDeviceList()
            }

            is SyncMessage.Leave -> {
                _uiState.value = _uiState.value.copy(
                    devices = _uiState.value.devices.filterNot { it.id == deviceId }
                )
                deviceReadiness.remove(deviceId)
                broadcastDeviceList()
            }

            is SyncMessage.ClockSyncRequest -> {
                val response = ClockSyncResponder.respond(message)
                hostTransport.sendTo(deviceId, response)
            }

            is SyncMessage.ClientReady -> {
                deviceReadiness[deviceId] = true
                updateWaitingSet()
            }

            is SyncMessage.ClientNotReady -> {
                deviceReadiness[deviceId] = false
                updateWaitingSet()
            }

            is SyncMessage.PlaybackReportMsg -> {
                // Surfaced to diagnostics screen via a separate reports flow (see reports below).
                _reports.value = _reports.value + (deviceId to message.report)
            }

            is SyncMessage.AudioOutputChanged -> {
                _uiState.value = _uiState.value.copy(
                    devices = _uiState.value.devices.map {
                        if (it.id == deviceId) it.copy(audioOutput = message.output) else it
                    }
                )
            }

            else -> Unit
        }
    }

    private val _reports = MutableStateFlow<Map<String, PlaybackReport>>(emptyMap())
    val reports: StateFlow<Map<String, PlaybackReport>> = _reports.asStateFlow()

    private fun updateWaitingSet() {
        val notReady = deviceReadiness.filterValues { !it }.keys
        _uiState.value = _uiState.value.copy(waitingForDeviceIds = notReady)
    }

    /**
     * Broadcasts track metadata so clients know to fetch it (Section 10 TRACK_INFO),
     * then waits (up to [waitTimeoutMs]) for all connected devices to report ready
     * before actually scheduling playback (Section 18), unless [waitForAll] is false
     * in which case it starts with whichever devices are ready ("start with ready
     * devices" per Section 18).
     */
    suspend fun playTrack(
        trackId: String,
        fileName: String,
        fileSizeBytes: Long,
        sha256: String,
        durationMs: Long,
        isVideo: Boolean,
        filePort: Int,
        startPositionMs: Long = 0L,
        waitForAll: Boolean = true,
        waitTimeoutMs: Long = 30_000
    ) {
        deviceReadiness.clear()
        hostTransport.connectedDeviceIds.forEach { deviceReadiness[it] = false }
        updateWaitingSet()

        hostTransport.broadcast(
            SyncMessage.TrackInfo(trackId, fileName, fileSizeBytes, sha256, durationMs, isVideo, filePort)
        )

        if (waitForAll) {
            val deadline = System.currentTimeMillis() + waitTimeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (deviceReadiness.values.all { it }) break
                delay(200)
            }
        }

        schedulePlayAt(trackId, startPositionMs)
    }

    private suspend fun schedulePlayAt(trackId: String, startPositionMs: Long) {
        val scheduledStart = System.currentTimeMillis() + SCHEDULE_LEAD_TIME_MS
        val seq = sequenceGenerator.next()

        hostTransport.broadcast(
            SyncMessage.PlayAt(
                sequenceNumber = seq,
                trackId = trackId,
                startPositionMs = startPositionMs,
                scheduledStartTimestampMs = scheduledStart
            )
        )

        _uiState.value = _uiState.value.copy(
            currentTrackId = trackId,
            isPlaying = true,
            positionMs = startPositionMs
        )
    }

    suspend fun pause() {
        val scheduledPause = System.currentTimeMillis() + 300 // small lead time, Section 19
        val seq = sequenceGenerator.next()
        hostTransport.broadcast(
            SyncMessage.PauseAt(
                sequenceNumber = seq,
                pausePositionMs = _uiState.value.positionMs,
                scheduledPauseTimestampMs = scheduledPause
            )
        )
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }

    suspend fun seek(positionMs: Long) {
        val scheduledStart = System.currentTimeMillis() + 1000 // Section 20 example: +1s
        val seq = sequenceGenerator.next()
        hostTransport.broadcast(
            SyncMessage.SeekAt(
                sequenceNumber = seq,
                positionMs = positionMs,
                scheduledStartTimestampMs = scheduledStart
            )
        )
        _uiState.value = _uiState.value.copy(positionMs = positionMs)
    }

    private suspend fun broadcastDeviceList() {
        hostTransport.broadcast(SyncMessage.DeviceListUpdate(_uiState.value.devices))
    }

    suspend fun shutdown() {
        hostTransport.broadcast(SyncMessage.Leave("host"))
        hostTransport.stopHost()
    }
}
