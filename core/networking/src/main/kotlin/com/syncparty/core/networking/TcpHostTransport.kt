package com.syncparty.core.networking

import com.syncparty.core.common.HostTransport
import com.syncparty.core.common.SyncMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * TCP server that the HOST device runs. Accepts one connection per client
 * device, each handled on its own coroutine. This satisfies "clients should
 * NOT communicate independently with each other" (Section 4) — there is no
 * client-to-client link at all, only star topology through the host.
 */
class TcpHostTransport : HostTransport {

    private data class ClientConnection(
        val deviceId: String,
        val socket: Socket,
        val output: DataOutputStream
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = ConcurrentHashMap<String, ClientConnection>()
    private val clientsLock = Mutex()
    private val incoming = Channel<Pair<String, SyncMessage>>(capacity = Channel.UNLIMITED)

    private var serverSocket: ServerSocket? = null

    override val connectedDeviceIds: List<String>
        get() = clients.keys.toList()

    override suspend fun startHost(partyId: String, partyName: String, partyToken: String): Int {
        val socket = ServerSocket(0) // 0 = let the OS pick a free port
        serverSocket = socket
        val boundPort = socket.localPort

        scope.launch {
            while (!socket.isClosed) {
                val clientSocket = try {
                    socket.accept()
                } catch (e: Exception) {
                    break // socket closed, stop accepting
                }
                scope.launch { handleClient(clientSocket, partyToken) }
            }
        }

        return boundPort
    }

    private suspend fun handleClient(socket: Socket, expectedToken: String) {
        socket.tcpNoDelay = true // low latency matters more than throughput for sync messages
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        var deviceId: String? = null
        try {
            // First message MUST be Hello with the correct party token (Section 39: auth).
            val first = WireFormat.readMessage(input) ?: return
            if (first !is SyncMessage.Hello || first.partyToken != expectedToken) {
                WireFormat.writeMessage(output, SyncMessage.Rejected("Invalid party token"))
                socket.close()
                return
            }

            deviceId = first.deviceId
            clientsLock.withLock {
                clients[deviceId] = ClientConnection(deviceId, socket, output)
            }
            incoming.send(deviceId to first)

            while (!socket.isClosed) {
                val message = WireFormat.readMessage(input) ?: break
                incoming.send(deviceId to message)
            }
        } catch (e: Exception) {
            // Connection dropped; fall through to cleanup.
        } finally {
            deviceId?.let { id ->
                clientsLock.withLock { clients.remove(id) }
                incoming.trySend(id to SyncMessage.Leave(id))
            }
            runCatching { socket.close() }
        }
    }

    override fun clientMessages(): Flow<Pair<String, SyncMessage>> = incoming.receiveAsFlow()

    override suspend fun sendTo(deviceId: String, message: SyncMessage) {
        val client = clients[deviceId] ?: return
        runCatching {
            synchronized(client.output) {
                WireFormat.writeMessage(client.output, message)
            }
        }
    }

    override suspend fun broadcast(message: SyncMessage) {
        clients.values.forEach { client ->
            runCatching {
                synchronized(client.output) {
                    WireFormat.writeMessage(client.output, message)
                }
            }
        }
    }

    override suspend fun disconnectClient(deviceId: String) {
        clients.remove(deviceId)?.let { runCatching { it.socket.close() } }
    }

    override suspend fun stopHost() {
        clients.values.forEach { runCatching { it.socket.close() } }
        clients.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
