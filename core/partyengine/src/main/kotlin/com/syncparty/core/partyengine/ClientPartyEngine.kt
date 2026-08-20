package com.syncparty.core.partyengine

import com.syncparty.core.common.AudioOutput
import com.syncparty.core.common.DeviceInfo
import com.syncparty.core.common.PlaybackReport
import com.syncparty.core.common.SyncMessage
import com.syncparty.core.common.SyncQuality
import com.syncparty.core.common.SyncTransport
import com.syncparty.core.common.driftToSyncQuality
import com.syncparty.core.playback.SynchronizedPlayer
import com.syncparty.core.synchronization.ClockOffsetHolder
import com.syncparty.core.synchronization.ClockSyncEngine
import com.syncparty.core.synchronization.DriftAction
import com.syncparty.core.synchronization.DriftCorrector
import com.syncparty.core.synchronization.SequenceGate
import com.syncparty.core.synchronization.expectedPositionMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClientPartyUiState(
    val partyName: String = "",
    val hostDeviceName: String = "",
    val devices: List<DeviceInfo> = emptyList(),
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val syncQuality: SyncQuality = SyncQuality.EXCELLENT,
    val driftMs: Long = 0,
    val clockOffsetMs: Long = 0,
    val rttMs: Long = 0,
    val audioOutput: AudioOutput = AudioOutput.UNKNOWN
)

/**
 * Runs on a CLIENT device. Maintains clock sync against the host, applies
 * PLAY_AT/PAUSE_AT/SEEK_AT commands (converted from host-time to local time),
 * runs the continuous drift-detection/correction loop (Section 16-17), and
 * reports periodic PlaybackReports back to the host (Section 28: every
 * 500-1000ms, not more often, to conserve battery).
 *
 * The client does NOT expose independent playback controls (Section 7) —
 * this engine only reacts to host commands.
 */
class ClientPartyEngine(
    private val transport: SyncTransport,
    private val player: SynchronizedPlayer,
    private val deviceId: String,
    private val getAudioOutput: () -> AudioOutput,
    private val reportIntervalMs: Long = 750
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clockOffsetHolder = ClockOffsetHolder()
    private val clockSyncEngine = ClockSyncEngine(transport)
    private val driftCorrector = DriftCorrector()
    private val sequenceGate = SequenceGate()

    private val _uiState = MutableStateFlow(ClientPartyUiState())
    val uiState: StateFlow<ClientPartyUiState> = _uiState.asStateFlow()

    // Track the currently-active schedule so the drift loop knows the
    // expected-position baseline (Section 16).
    private var activeScheduledStartMs: Long? = null
    private var activeStartPositionMs: Long = 0

    fun start() {
        scope.launch { periodicClockSync() }
        scope.launch { collectMessages() }
        scope.launch { driftMonitorLoop() }
        scope.launch { periodicReportLoop() }
        scope.launch { audioOutputLoop() }
    }

    private suspend fun periodicClockSync() {
        while (true) {
            val result = clockSyncEngine.sync(sampleCount = 8)
            if (result != null) {
                clockOffsetHolder.update(result)
                _uiState.value = _uiState.value.copy(
                    clockOffsetMs = result.offsetMs,
                    rttMs = result.roundTripTimeMs
                )
            }
            delay(15_000) // re-sync periodically; clocks drift slowly, no need to spam
        }
    }

    private suspend fun collectMessages() {
        transport.receive().collect { message ->
            when (message) {
                is SyncMessage.Welcome -> {
                    _uiState.value = _uiState.value.copy(
                        partyName = message.partyName,
                        hostDeviceName = message.hostDeviceName
                    )
                }

                is SyncMessage.DeviceListUpdate -> {
                    _uiState.value = _uiState.value.copy(devices = message.devices)
                }

                is SyncMessage.PlayAt -> {
                    if (!sequenceGate.accept(message.sequenceNumber)) return@collect
                    val localStart = clockOffsetHolder.hostTimeToLocalTime(message.scheduledStartTimestampMs)
                    activeScheduledStartMs = message.scheduledStartTimestampMs
                    activeStartPositionMs = message.startPositionMs
                    player.playAt(message.startPositionMs, localStart)
                    _uiState.value = _uiState.value.copy(
                        currentTrackId = message.trackId,
                        isPlaying = true
                    )
                }

                is SyncMessage.PauseAt -> {
                    if (!sequenceGate.accept(message.sequenceNumber)) return@collect
                    val localPause = clockOffsetHolder.hostTimeToLocalTime(message.scheduledPauseTimestampMs)
                    player.pauseAt(localPause)
                    activeScheduledStartMs = null
                    _uiState.value = _uiState.value.copy(isPlaying = false)
                }

                is SyncMessage.SeekAt -> {
                    if (!sequenceGate.accept(message.sequenceNumber)) return@collect
                    val localStart = clockOffsetHolder.hostTimeToLocalTime(message.scheduledStartTimestampMs)
                    activeScheduledStartMs = message.scheduledStartTimestampMs
                    activeStartPositionMs = message.positionMs
                    player.seekAt(message.positionMs, localStart)
                }

                is SyncMessage.SetPlaybackRate -> {
                    if (!sequenceGate.accept(message.sequenceNumber)) return@collect
                    player.setPlaybackRate(message.rate)
                }

                is SyncMessage.TrackInfo -> {
                    _uiState.value = _uiState.value.copy(currentTrackId = message.trackId)
                    // Actual file fetch is orchestrated by the media-transfer layer
                    // (see FileTransferClient), triggered by the ViewModel observing
                    // this state change — kept out of the engine to avoid coupling
                    // networking-file-IO into the realtime sync path.
                }

                is SyncMessage.ClockSyncResponse -> Unit // handled inside ClockSyncEngine.sync()

                else -> Unit
            }
        }
    }

    /** Continuously compares expected vs actual position and applies corrections (Section 16-17). */
    private suspend fun driftMonitorLoop() {
        while (true) {
            delay(500)
            val scheduledStart = activeScheduledStartMs ?: continue
            if (!player.isPlaying()) continue

            val hostTimeNow = clockOffsetHolder.localTimeToHostTime(System.currentTimeMillis())
            val expected = expectedPositionMs(
                scheduledStartTimestampMs = scheduledStart,
                startPositionMs = activeStartPositionMs,
                hostTimeNowMs = hostTimeNow
            )
            val actual = player.currentPosition()
            val drift = actual - expected

            _uiState.value = _uiState.value.copy(
                driftMs = drift,
                syncQuality = driftToSyncQuality(drift)
            )

            when (val action = driftCorrector.evaluate(expected, actual)) {
                is DriftAction.None -> player.setPlaybackRate(1.0f)
                is DriftAction.RateNudge -> player.setPlaybackRate(action.targetRate)
                is DriftAction.HardResync -> {
                    player.setPlaybackRate(1.0f)
                    player.seekAt(action.seekToPositionMs, System.currentTimeMillis())
                }
            }
        }
    }

    private suspend fun periodicReportLoop() {
        while (true) {
            delay(reportIntervalMs)
            val scheduledStart = activeScheduledStartMs ?: continue
            val hostTimeNow = clockOffsetHolder.localTimeToHostTime(System.currentTimeMillis())
            val expected = expectedPositionMs(scheduledStart, activeStartPositionMs, hostTimeNow)
            val actual = player.currentPosition()

            transport.send(
                SyncMessage.PlaybackReportMsg(
                    PlaybackReport(
                        deviceId = deviceId,
                        positionMs = actual,
                        expectedPositionMs = expected,
                        driftMs = actual - expected,
                        bufferedPositionMs = player.bufferedPosition()
                    )
                )
            )
        }
    }

    private suspend fun audioOutputLoop() {
        var last: AudioOutput? = null
        while (true) {
            val current = getAudioOutput()
            if (current != last) {
                last = current
                _uiState.value = _uiState.value.copy(audioOutput = current)
                transport.send(SyncMessage.AudioOutputChanged(deviceId, current))
            }
            delay(2000)
        }
    }

    suspend fun sendReady(trackId: String) {
        transport.send(SyncMessage.ClientReady(deviceId, trackId))
    }

    suspend fun sendNotReady(trackId: String, reason: String) {
        transport.send(SyncMessage.ClientNotReady(deviceId, trackId, reason))
    }

    suspend fun leave() {
        transport.send(SyncMessage.Leave(deviceId))
        transport.disconnect()
    }
}
