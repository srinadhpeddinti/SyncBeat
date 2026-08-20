package com.syncparty.core.networking

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.syncparty.core.common.DiscoveredHost
import com.syncparty.core.common.HostDiscovery
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress

private const val SERVICE_TYPE = "_syncparty._tcp."

/**
 * Advertises the host's TCP server on the local network via mDNS/NSD so
 * clients can find it without knowing its IP ahead of time (Section 5/23:
 * "do not hardcode 192.168.1.1... must dynamically discover the host").
 */
class NsdHostAdvertiser(private val context: Context) {

    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var registrationListener: NsdManager.RegistrationListener? = null

    fun advertise(partyId: String, partyName: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "SyncParty-$partyId"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("partyId", partyId)
            // TXT record values are limited in length; keep party name short or truncate.
            setAttribute("partyName", partyName.take(63))
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        registrationListener?.let {
            runCatching { nsdManager.unregisterService(it) }
        }
        registrationListener = null
    }
}

/** Client-side: browses for advertised parties on the local network. */
class NsdHostDiscovery(private val context: Context) : HostDiscovery {

    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override fun discover(): Flow<DiscoveredHost> = callbackFlow {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val partyId = serviceInfo.attributes["partyId"]?.toString(Charsets.UTF_8) ?: return
                val partyName = serviceInfo.attributes["partyName"]?.toString(Charsets.UTF_8) ?: "Party"
                val address: InetAddress = serviceInfo.host ?: return
                trySend(
                    DiscoveredHost(
                        partyId = partyId,
                        partyName = partyName,
                        hostDeviceName = serviceInfo.serviceName,
                        hostAddress = address.hostAddress ?: return,
                        port = serviceInfo.port
                    )
                )
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.startsWith("_syncparty")) {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(serviceInfo, resolveListener)
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("NSD discovery start failed: $errorCode"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }

    override suspend fun stop() {
        discoveryListener?.let {
            runCatching { nsdManager.stopServiceDiscovery(it) }
        }
        discoveryListener = null
    }
}
