package com.syncparty.core.synchronization

import kotlin.math.abs

/**
 * Configurable drift-correction thresholds and resulting action, per Section 17.
 * Tuned defaults match the spec's suggested policy but are adjustable for
 * on-device measurement (Section 38: "measure and report actual results").
 */
data class DriftCorrectionPolicy(
    val noCorrectionThresholdMs: Long = 50,
    val gentleCorrectionThresholdMs: Long = 150,
    val strongCorrectionThresholdMs: Long = 300,
    val gentlePlaybackRateDelta: Float = 0.005f,  // +/- 0.5%
    val strongPlaybackRateDelta: Float = 0.02f    // +/- 2%
)

sealed class DriftAction {
    /** Drift is within tolerance; no action, ensure rate is 1.0x. */
    data object None : DriftAction()

    /** Small drift: nudge playback rate temporarily, then return to 1.0x once caught up. */
    data class RateNudge(val targetRate: Float) : DriftAction()

    /** Hard resync: drift too large for rate correction, must reseek to the expected position. */
    data class HardResync(val seekToPositionMs: Long) : DriftAction()
}

class DriftCorrector(private val policy: DriftCorrectionPolicy = DriftCorrectionPolicy()) {

    /**
     * @param expectedPositionMs where playback SHOULD be, per host's authoritative
     *   timeline (scheduledStart + elapsed host-time-equivalent since start)
     * @param actualPositionMs where the local player actually reports being
     */
    fun evaluate(expectedPositionMs: Long, actualPositionMs: Long): DriftAction {
        val drift = actualPositionMs - expectedPositionMs // positive = ahead, negative = behind
        val absDrift = abs(drift)

        return when {
            absDrift < policy.noCorrectionThresholdMs -> DriftAction.None

            absDrift < policy.gentleCorrectionThresholdMs -> {
                // Behind -> speed up slightly. Ahead -> slow down slightly.
                val rate = if (drift < 0) 1.0f + policy.gentlePlaybackRateDelta
                           else 1.0f - policy.gentlePlaybackRateDelta
                DriftAction.RateNudge(rate)
            }

            absDrift < policy.strongCorrectionThresholdMs -> {
                val rate = if (drift < 0) 1.0f + policy.strongPlaybackRateDelta
                           else 1.0f - policy.strongPlaybackRateDelta
                DriftAction.RateNudge(rate)
            }

            else -> DriftAction.HardResync(seekToPositionMs = expectedPositionMs)
        }
    }
}

/** Computes the position a correctly-synced player should be at right now. */
fun expectedPositionMs(
    scheduledStartTimestampMs: Long,
    startPositionMs: Long,
    hostTimeNowMs: Long,
    playbackRate: Float = 1.0f
): Long {
    val elapsedSinceStart = hostTimeNowMs - scheduledStartTimestampMs
    if (elapsedSinceStart <= 0) return startPositionMs // hasn't started yet
    return startPositionMs + (elapsedSinceStart * playbackRate).toLong()
}
