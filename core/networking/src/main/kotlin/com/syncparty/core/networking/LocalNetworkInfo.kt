package com.syncparty.core.networking

import android.content.Context
import android.net.wifi.WifiManager
import java.net.NetworkInterface
import java.util.Locale

/**
 * Reads the device's actual current local IP (Wi-Fi station IP, or hotspot
 * IP if this device is the hotspot owner). Never hardcode an address —
 * hotspot/network configs vary per Section 23.
 */
object LocalNetworkInfo {

    /** Best-effort local IPv4 address for this device on the active local network. */
    fun getLocalIpAddress(context: Context): String? {
        // Try WifiManager first — works when connected to a Wi-Fi AP (including
        // another phone's hotspot).
        runCatching {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US, "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        }

        // Fallback: enumerate network interfaces (covers the case where this
        // device is itself running the hotspot, e.g. interface "ap0"/"wlan1").
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                if (!iface.isUp || iface.isLoopback) return@forEach
                iface.inetAddresses?.toList()?.forEach { addr ->
                    val host = addr.hostAddress ?: return@forEach
                    if (!addr.isLoopbackAddress && host.indexOf(':') < 0) { // IPv4 only
                        return host
                    }
                }
            }
        }

        return null
    }
}
