package com.syncparty.core.common

import kotlinx.coroutines.flow.Flow

/**
 * Abstract local-network transport for control-plane SyncMessages.
 *
 * The chosen concrete implementation for MVP is TCP sockets over local Wi-Fi
 * (same Wi-Fi network OR host-created hotspot), advertised/discovered via NSD
 * (Android Network Service Discovery, i.e. mDNS). This is the most reliable,
 * least permission-heavy option on modern Android (see Section 3/25 tradeoffs
 * documented in TransportChoice.md at the project root).
 *
 * Wi-Fi Direct and Nearby Connections are left as alternative implementations
 * behind this same interface (see core:networking) for phones not on a shared
 * Wi-Fi network, but are not required for the MVP.
 */
interface SyncTransport {
    suspend fun connect()
    suspend fun send(message: SyncMessage)
    fun receive(): Flow<SyncMessage>
    suspend fun disconnect()
    val isConnected: Boolean
}

/** Host-side: accepts connections from N clients. */
interface HostTransport {
    /** Starts listening on the local network. Returns the bound port. */
    suspend fun startHost(partyId: String, partyName: String, partyToken: String): Int

    /** Per-connected-client message stream, keyed by deviceId. */
    fun clientMessages(): Flow<Pair<String, SyncMessage>>

    suspend fun sendTo(deviceId: String, message: SyncMessage)
    suspend fun broadcast(message: SyncMessage)
    suspend fun disconnectClient(deviceId: String)
    suspend fun stopHost()

    val connectedDeviceIds: List<String>
}

/** A host discovered on the local network, exposed to the "Join Party" screen. */
interface HostDiscovery {
    fun discover(): Flow<DiscoveredHost>
    suspend fun stop()
}

/**
 * Local file transfer, used to get the audio/video file itself onto client
 * devices before synchronized playback can start (Section 9/10).
 */
interface LocalMediaTransfer {
    suspend fun sendTrack(track: Track, filePath: String, destinationDeviceId: String, onProgress: (Float) -> Unit = {})
    fun incomingTransfers(): Flow<TransferProgress>
}

data class TransferProgress(
    val trackId: String,
    val bytesReceived: Long,
    val totalBytes: Long,
    val complete: Boolean,
    val verified: Boolean = false
)
