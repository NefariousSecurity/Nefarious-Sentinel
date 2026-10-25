package com.localruntime.core.deadman

import kotlin.math.max

/**
 * Pure reboot-recovery arithmetic for the Dead Man's Switch.
 *
 * Security properties:
 *
 * 1. A backwards wall-clock change must never increase remaining time.
 * 2. Time already spent in the new boot must count against the deadline.
 * 3. Recovery may shorten a deadline, but must never extend it.
 */
object DeadmanRecoveryMath {

    fun recoverRemainingMs(
        remainingAtAnchorMs: Long,
        anchorWallClockMs: Long,
        nowWallClockMs: Long,
        nowElapsedRealtimeMs: Long,
    ): Long {
        require(
            remainingAtAnchorMs >= 0L,
        ) {
            "Remaining interval cannot be negative"
        }

        require(
            anchorWallClockMs > 0L,
        ) {
            "Anchor wall clock must be positive"
        }

        require(
            nowElapsedRealtimeMs >= 0L,
        ) {
            "Elapsed realtime cannot be negative"
        }

        /*
         * A backwards wall-clock move contributes zero elapsed wall time.
         * It is never permitted to create negative elapsed time and therefore
         * never increases the stored remaining interval.
         */
        val wallElapsedMs =
            max(
                0L,
                nowWallClockMs -
                    anchorWallClockMs,
            )

        /*
         * elapsedRealtime() in the new boot provides a minimum amount of time
         * that definitely passed after boot.
         *
         * Use the greater value so recovery is conservative.
         */
        val conservativeElapsedMs =
            max(
                wallElapsedMs,
                nowElapsedRealtimeMs,
            )

        return max(
            0L,
            remainingAtAnchorMs -
                conservativeElapsedMs,
        )
    }
}
