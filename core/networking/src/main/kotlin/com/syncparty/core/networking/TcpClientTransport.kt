package com.syncparty.core.networking

import com.syncparty.core.common.SyncMessage
import com.syncparty.core.common.SyncTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Client-side TCP connection to the host. Connects to a specific
 * (hostAddress, port) discovered via NSD or QR/manual entry (Section 5/23) —
 * this class does NOT do discovery itself, see NsdHostDiscovery.
 */
class TcpClientTransport(
    private val hostAddress: String,
    private val port: Int,
    private val deviceId: String,
    private val deviceName: String,
    private val partyToken: String,
    private val connectTimeoutMs: Int = 5000
) : SyncTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private val incoming = Channel<SyncMessage>(capacity = Channel.UNLIMITED)

    override val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(hostAddress, port), connectTimeoutMs)
        socket = s
        val out = DataOutputStream(s.getOutputStream())
        output = out
        val input = DataInputStream(s.getInputStream())

        WireFormat.writeMessage(out, SyncMessage.Hello(deviceId, deviceName, partyToken))

        scope.launch {
            try {
                while (!s.isClosed) {
                    val message = WireFormat.readMessage(input) ?: break
                    incoming.send(message)
                }
            } catch (e: Exception) {
                // stream closed / error — receive() flow simply stops emitting
            } finally {
                runCatching { s.close() }
            }
        }
    }

    override suspend fun send(message: SyncMessage) = withContext(Dispatchers.IO) {
        val out = output ?: error("Not connected")
        synchronized(out) {
            WireFormat.writeMessage(out, message)
        }
    }

    override fun receive(): Flow<SyncMessage> = incoming.receiveAsFlow()

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { socket?.close() }
        socket = null
        output = null
    }
}
