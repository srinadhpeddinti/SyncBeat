# Local Transport Choice

Section 3 of the spec asks for an investigation into the most reliable
Android-supported method for local networking. Decision for this MVP:

## Chosen: NSD (mDNS) discovery + raw TCP sockets, same Wi-Fi network

**Why this over the alternatives:**

| Option | Verdict | Reason |
|---|---|---|
| **NSD + TCP (chosen)** | ✅ MVP | No special runtime permission dance beyond `NEARBY_WIFI_DEVICES` (API 33+) / location (API <33) for discovery. Every phone already has Wi-Fi. Works great if everyone's on the same home/venue Wi-Fi, or if the host turns on their **Wi-Fi hotspot** and everyone joins that — both cases are literally the same code path once connected, since it's just TCP/IP once on a shared subnet. |
| Wi-Fi Direct | ⚠️ Phase 2 candidate | No router needed at all, but Android's Wi-Fi Direct API is notoriously flaky across OEMs (group formation failures, especially Samsung/Xiaomi), and a phone typically can't stay on Wi-Fi Direct *and* use its own internet-free Wi-Fi simultaneously in a simple way. Kept behind the same `SyncTransport` interface for a later `WifiDirectTransport`, not implemented in this pass. |
| Nearby Connections API | ⚠️ Fallback candidate | Handles discovery+connection nicely and can auto-select Wi-Fi Direct/Bluetooth/local Wi-Fi under the hood, but it's higher-level, less control over latency-critical paths, and (per Section 2) we must not depend on Google Play Services being "phone-home" — it works offline but adds a heavy dependency for what raw sockets do fine. Good v2 option if OEM Wi-Fi Direct issues make TCP-only insufficient. |
| Cloud/Firebase/etc | ❌ Forbidden | Explicitly disallowed by Section 2. |

## How "no fixed IP" discovery works (Section 5, 23)

1. Host calls `NsdManager.registerService(...)` advertising `_syncparty._tcp` on the local network with TXT records for `partyId` and a short `partyName`.
2. Client calls `NsdManager.discoverServices(...)`, gets a list of `DiscoveredHost` (resolved IP + port) — this satisfies "do not hardcode 192.168.1.1."
3. As a fallback/faster path (especially if NSD is slow on some routers), the host also shows a 6-digit party code + QR containing `{partyId, hostAddress, port}` (Section 5 Option A/B) — client can skip discovery entirely and connect directly.
4. Whichever hotspot/network the host is using, its actual local IP is read at runtime via `WifiManager`/`ConnectivityManager`, never assumed.

## Requires no internet

TCP sockets between two devices on the same L2/L3 local network (Wi-Fi AP or
phone hotspot) never touch the internet. NSD/mDNS is also purely local
(link-local multicast, 224.0.0.251). This satisfies Section 2 fully — the app
works with airplane-mode-plus-wifi-on, mobile data off, and no internet
gateway on the Wi-Fi network at all.
