package com.syncparty.core.synchronization

import com.syncparty.core.common.ClockSyncResult
import com.syncparty.core.common.SyncMessage
import com.syncparty.core.common.SyncTransport
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Client-side clock synchronization against the host, implementing the
 * classic Cristian's-algorithm exchange described in Section 12:
 *
 *   CLIENT -> HOST   T1 (client send time)
 *   HOST received    T2 (host receive time)
 *   HOST -> CLIENT   T3 (host send time)
 *   CLIENT received  T4 (client receive time)
 *
 *   roundTripTime = (T4 - T1) - (T3 - T2)
 *   clockOffset   = ((T2 - T1) + (T3 - T4)) / 2
 *
 * `clockOffset` is added to the client's local clock to get "host time":
 *   hostTimeEquivalent = System.currentTimeMillis() + offsetMs
 */
class ClockSyncEngine(
    private val transport: SyncTransport,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Performs [sampleCount] round trips and returns the best estimate,
     * using the median-of-lowest-RTT strategy to reduce network jitter
     * (Section 12: "use the best/median measurements").
     */
    suspend fun sync(sampleCount: Int = 8, perSampleTimeoutMs: Long = 1500): ClockSyncResult? {
        val samples = mutableListOf<ClockSyncResult>()

        repeat(sampleCount) {
            val result = withTimeoutOrNull(perSampleTimeoutMs) { oneRoundTrip() }
            if (result != null) samples.add(result)
        }

        if (samples.isEmpty()) return null

        // Discard the worst half by RTT (outliers from Wi-Fi jitter / GC pauses),
        // then take the median offset of what remains.
        val sorted = samples.sortedBy { it.roundTripTimeMs }
        val keep = sorted.take((sorted.size + 1) / 2).ifEmpty { sorted }
        val medianOffset = keep.map { it.offsetMs }.sorted()[keep.size / 2]
        val bestRtt = keep.minOf { it.roundTripTimeMs }

        return ClockSyncResult(offsetMs = medianOffset, roundTripTimeMs = bestRtt)
    }

    private suspend fun oneRoundTrip(): ClockSyncResult {
        val t1 = nowMs()
        transport.send(SyncMessage.ClockSyncRequest(clientSendTimestampMs = t1))

        val response = transport.receive()
            .filterIsInstance<SyncMessage.ClockSyncResponse>()
            .first { it.clientSendTimestampMs == t1 }

        val t4 = nowMs()
        val t2 = response.hostReceiveTimestampMs
        val t3 = response.hostSendTimestampMs

        val rtt = (t4 - t1) - (t3 - t2)
        val offset = ((t2 - t1) + (t3 - t4)) / 2

        return ClockSyncResult(offsetMs = offset, roundTripTimeMs = abs(rtt))
    }
}

/**
 * Host-side responder: answers a ClockSyncRequest immediately with T2/T3.
 * Should be wired into the host's message-handling loop.
 */
object ClockSyncResponder {
    fun respond(request: SyncMessage.ClockSyncRequest, nowMs: () -> Long = { System.currentTimeMillis() }): SyncMessage.ClockSyncResponse {
        val t2 = nowMs()
        // t3 captured as close to send-time as possible by the caller;
        // here we approximate t2 == t3 since responding is near-instant.
        val t3 = nowMs()
        return SyncMessage.ClockSyncResponse(
            clientSendTimestampMs = request.clientSendTimestampMs,
            hostReceiveTimestampMs = t2,
            hostSendTimestampMs = t3
        )
    }
}

/**
 * Holds the latest offset for a device and exposes helpers to convert
 * between local time and "host time equivalent."
 */
class ClockOffsetHolder {
    @Volatile private var offsetMs: Long = 0L
    @Volatile private var lastRttMs: Long = 0L
    @Volatile private var hasSynced: Boolean = false

    fun update(result: ClockSyncResult) {
        offsetMs = result.offsetMs
        lastRttMs = result.roundTripTimeMs
        hasSynced = true
    }

    fun currentOffsetMs(): Long = offsetMs
    fun currentRttMs(): Long = lastRttMs
    fun isSynced(): Boolean = hasSynced

    /** Convert a HOST-clock timestamp into the equivalent LOCAL timestamp to schedule against. */
    fun hostTimeToLocalTime(hostTimestampMs: Long): Long = hostTimestampMs - offsetMs

    /** Convert current LOCAL time into the equivalent HOST-clock time (for reporting). */
    fun localTimeToHostTime(localTimestampMs: Long): Long = localTimestampMs + offsetMs
}
