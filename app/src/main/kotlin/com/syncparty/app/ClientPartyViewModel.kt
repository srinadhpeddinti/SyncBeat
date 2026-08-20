package com.syncparty.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.syncparty.core.audiotransfer.FileTransferClient
import com.syncparty.core.bluetooth.AudioOutputMonitor
import com.syncparty.core.common.DiscoveredHost
import com.syncparty.core.common.SyncMessage
import com.syncparty.core.networking.NsdHostDiscovery
import com.syncparty.core.networking.TcpClientTransport
import com.syncparty.core.partyengine.ClientPartyEngine
import com.syncparty.core.partyengine.ClientPartyUiState
import com.syncparty.core.playback.Media3SynchronizedPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Owns the CLIENT-side session: discovery, TCP connection to the host,
 * ClientPartyEngine (clock sync + drift correction + command handling),
 * and the local Media3 player driving this device's own audio output.
 */
class ClientPartyViewModel(application: Application) : AndroidViewModel(application) {

    private val discovery = NsdHostDiscovery(application)
    private val audioMonitor = AudioOutputMonitor(application)
    private val player = Media3SynchronizedPlayer(application)
    private val deviceId = UUID.randomUUID().toString()

    private var transport: TcpClientTransport? = null
    private var engine: ClientPartyEngine? = null
    private var fileTransferClient: FileTransferClient? = null

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredHost>> = _discoveredHosts.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _uiState = MutableStateFlow(ClientPartyUiState())
    val uiState: StateFlow<ClientPartyUiState> = _uiState.asStateFlow()

    fun startDiscovery() {
        _isDiscovering.value = true
        viewModelScope.launch {
            discovery.discover().collect { host ->
                if (_discoveredHosts.value.none { it.partyId == host.partyId }) {
                    _discoveredHosts.value = _discoveredHosts.value + host
                }
            }
        }
    }

    fun stopDiscovery() {
        viewModelScope.launch {
            discovery.stop()
            _isDiscovering.value = false
        }
    }

    /** Joins a party discovered via NSD, or manually entered/scanned from a QR code. */
    fun joinParty(hostAddress: String, port: Int, partyToken: String) {
        viewModelScope.launch {
            val client = TcpClientTransport(
                hostAddress = hostAddress,
                port = port,
                deviceId = deviceId,
                deviceName = android.os.Build.MODEL ?: "Phone",
                partyToken = partyToken
            )
            client.connect()
            transport = client
            fileTransferClient = FileTransferClient(mediaDirectory = getApplication<Application>().cacheDir)

            val clientEngine = ClientPartyEngine(
                transport = client,
                player = player,
                deviceId = deviceId,
                getAudioOutput = { audioMonitor.currentOutput() }
            )
            engine = clientEngine
            clientEngine.start()

            viewModelScope.launch {
                clientEngine.uiState.collect { _uiState.value = it }
            }

            // Watch for TrackInfo -> fetch file -> prepare player -> report ready
            viewModelScope.launch {
                client.receive().collect { message ->
                    if (message is SyncMessage.TrackInfo) {
                        handleIncomingTrack(message, hostAddress)
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingTrack(info: SyncMessage.TrackInfo, hostAddress: String) {
        val ftClient = fileTransferClient ?: return
        val file = ftClient.downloadTrack(
            trackId = info.trackId,
            expectedSha256 = info.sha256,
            expectedFileName = info.fileName,
            hostAddress = hostAddress,
            filePort = info.filePort
        )

        if (file != null) {
            player.prepare(Uri.fromFile(file), info.isVideo)
            engine?.sendReady(info.trackId)
        } else {
            engine?.sendNotReady(info.trackId, "Transfer or checksum failed")
        }
    }

    fun leaveParty() {
        viewModelScope.launch {
            engine?.leave()
            transport?.disconnect()
            player.release()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            discovery.stop()
            transport?.disconnect()
            player.release()
        }
    }
}
