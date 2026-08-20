package com.syncparty.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.syncparty.core.audiotransfer.FileHasher
import com.syncparty.core.audiotransfer.FileTransferServer
import com.syncparty.core.bluetooth.AudioOutputMonitor
import com.syncparty.core.common.Track
import com.syncparty.core.networking.LocalNetworkInfo
import com.syncparty.core.networking.NsdHostAdvertiser
import com.syncparty.core.networking.TcpHostTransport
import com.syncparty.core.partyengine.HostPartyEngine
import com.syncparty.core.partyengine.HostPartyUiState
import com.syncparty.core.playback.Media3SynchronizedPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the HOST-side session: TCP server + NSD advertisement + HostPartyEngine
 * + the local Media3 player (host plays through its own speaker/output,
 * per Section 8 — it does not need to reach every client's Bluetooth device).
 */
class HostPartyViewModel(application: Application) : AndroidViewModel(application) {

    private val hostTransport = TcpHostTransport()
    private val advertiser = NsdHostAdvertiser(application)
    private val fileServer = FileTransferServer(mediaDirectory = application.cacheDir)
    private val audioMonitor = AudioOutputMonitor(application)
    private val player = Media3SynchronizedPlayer(application)

    private val engine = HostPartyEngine(hostTransport, partyName = "Local Party")

    private val _connectionInfo = MutableStateFlow<HostConnectionInfo?>(null)
    val connectionInfo: StateFlow<HostConnectionInfo?> = _connectionInfo.asStateFlow()

    val uiState: StateFlow<HostPartyUiState> get() = engine.uiState

    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks.asStateFlow()

    init {
        viewModelScope.launch {
            val controlPort = hostTransport.startHost(
                partyId = engine.partyId,
                partyName = "Local Party",
                partyToken = engine.token
            )
            val filePort = fileServer.start()
            val localIp = LocalNetworkInfo.getLocalIpAddress(application)

            advertiser.advertise(engine.partyId, "Local Party", controlPort)
            engine.start()

            _connectionInfo.value = HostConnectionInfo(
                hostAddress = localIp,
                controlPort = controlPort,
                filePort = filePort
            )
        }
    }

    /**
     * Registers a locally-picked file as a playable Track: hashes it,
     * registers it with the file transfer server so clients can pull it,
     * and adds it to the visible library (Section 9 steps 1-2).
     */
    fun addLocalTrack(uri: Uri, displayName: String, isVideo: Boolean) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val cachedFile = File(context.cacheDir, sanitize(displayName))
            context.contentResolver.openInputStream(uri)?.use { input ->
                cachedFile.outputStream().use { output -> input.copyTo(output) }
            }
            val hash = FileHasher.sha256(cachedFile)
            val track = Track(
                id = hash.take(16),
                name = displayName,
                artist = null,
                durationMs = 0L, // populated by player once prepared; kept simple for MVP wiring
                localPath = cachedFile.absolutePath,
                sha256 = hash,
                fileSizeBytes = cachedFile.length(),
                isVideo = isVideo
            )
            fileServer.registerTrack(track, cachedFile)
            _localTracks.value = _localTracks.value + track
        }
    }

    fun playTrack(track: Track) {
        viewModelScope.launch {
            player.prepare(Uri.fromFile(File(track.localPath!!)), track.isVideo)
            val filePort = _connectionInfo.value?.filePort ?: return@launch
            engine.playTrack(
                trackId = track.id,
                fileName = track.name,
                fileSizeBytes = track.fileSizeBytes,
                sha256 = track.sha256,
                durationMs = track.durationMs,
                isVideo = track.isVideo,
                filePort = filePort,
                waitForAll = true
            )
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            if (uiState.value.isPlaying) engine.pause()
            // Resuming from pause re-uses playTrack's schedule path in a fuller
            // implementation; MVP wiring focuses on the play/pause/seek command
            // path itself (Sections 14/19), which is the part requiring careful
            // clock-synchronized scheduling.
        }
    }

    fun seek(positionMs: Long) {
        viewModelScope.launch { engine.seek(positionMs) }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            engine.shutdown()
            advertiser.stop()
            fileServer.stop()
            player.release()
        }
    }

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(200)
}

data class HostConnectionInfo(
    val hostAddress: String?,
    val controlPort: Int,
    val filePort: Int
)
