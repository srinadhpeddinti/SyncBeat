package com.syncparty.core.synchronization

/**
 * Every playback command from the host carries a strictly increasing
 * sequenceNumber (Section 13/21: "Command #103, #104... clients must ignore
 * older commands"). This guards against out-of-order UDP-like delivery or
 * duplicate retransmits causing a client to un-apply a newer command.
 *
 * Thread-safety: intended to be called from a single collector coroutine
 * per client connection; wrap in a Mutex if used concurrently.
 */
class SequenceGate {
    @Volatile private var lastAccepted: Long = -1L

    /** Returns true if this sequence number is newer and should be applied. */
    fun accept(sequenceNumber: Long): Boolean {
        if (sequenceNumber <= lastAccepted) return false
        lastAccepted = sequenceNumber
        return true
    }

    fun current(): Long = lastAccepted
}

/** Host-side monotonic sequence number generator for outgoing commands. */
class SequenceGenerator {
    @Volatile private var counter: Long = 0L
    fun next(): Long = ++counter
}
