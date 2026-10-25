package com.localruntime.core.beta

/**
 * Pure policy logic for Sentinel Beta lifecycle decisions.
 *
 * The effective wall clock never moves backwards relative to the greatest
 * wall-clock value previously observed by Core.
 */
object BetaLifecyclePolicy {

    /*
     * Persisting the high-water wall clock once per hour prevents ordinary
     * clock rollback from extending a Beta indefinitely without writing to
     * Device Protected Storage on every status refresh.
     *
     * Expiry itself is always persisted immediately regardless of this
     * interval.
     */
    const val HIGH_WATER_PERSIST_INTERVAL_MS =
        60L * 60L * 1_000L

    data class Evaluation(
        val effectiveWallClockMs: Long,
        val currentBuildExpired: Boolean,
        val destructiveSuppressed: Boolean,
        val shouldLatchRearm: Boolean,
        val remainingMs: Long,
    )

    fun evaluate(
        betaBuild: Boolean,
        expiryEpochMs: Long,
        nowWallClockMs: Long,
        maxObservedWallClockMs: Long,
        rearmRequired: Boolean,
    ): Evaluation {
        val effectiveWallClockMs =
            maxOf(
                nowWallClockMs,
                maxObservedWallClockMs,
            )

        val currentBuildExpired =
            betaBuild &&
                effectiveWallClockMs >=
                expiryEpochMs

        val shouldLatchRearm =
            currentBuildExpired &&
                !rearmRequired

        val destructiveSuppressed =
            rearmRequired ||
                currentBuildExpired

        val remainingMs =
            if (!betaBuild) {
                Long.MAX_VALUE
            } else if (
                effectiveWallClockMs >=
                expiryEpochMs
            ) {
                0L
            } else {
                expiryEpochMs -
                    effectiveWallClockMs
            }

        return Evaluation(
            effectiveWallClockMs =
                effectiveWallClockMs,
            currentBuildExpired =
                currentBuildExpired,
            destructiveSuppressed =
                destructiveSuppressed,
            shouldLatchRearm =
                shouldLatchRearm,
            remainingMs =
                remainingMs,
        )
    }

    fun canRearm(
        currentBuildExpired: Boolean,
        rearmRequired: Boolean,
        currentVersionCode: Int,
        expiredVersionCode: Int,
        stateHealthy: Boolean,
    ): Boolean {
        if (!stateHealthy) {
            return false
        }

        if (currentBuildExpired) {
            return false
        }

        if (!rearmRequired) {
            return false
        }

        if (expiredVersionCode <= 0) {
            return false
        }

        return currentVersionCode >
            expiredVersionCode
    }

    fun shouldPersistHighWater(
        storedWallClockMs: Long,
        effectiveWallClockMs: Long,
        force: Boolean,
    ): Boolean {
        if (
            effectiveWallClockMs <=
            storedWallClockMs
        ) {
            return false
        }

        if (force) {
            return true
        }

        if (storedWallClockMs <= 0L) {
            return true
        }

        return effectiveWallClockMs -
            storedWallClockMs >=
            HIGH_WATER_PERSIST_INTERVAL_MS
    }
}
