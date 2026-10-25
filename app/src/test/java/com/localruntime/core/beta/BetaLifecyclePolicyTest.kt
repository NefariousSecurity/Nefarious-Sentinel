package com.localruntime.core.beta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaLifecyclePolicyTest {

    @Test
    fun beforeExpiry_allowsDestructiveAction() {
        val result =
            BetaLifecyclePolicy.evaluate(
                betaBuild = true,
                expiryEpochMs = 2_000L,
                nowWallClockMs = 1_000L,
                maxObservedWallClockMs = 900L,
                rearmRequired = false,
            )

        assertFalse(
            result.currentBuildExpired,
        )

        assertFalse(
            result.destructiveSuppressed,
        )

        assertFalse(
            result.shouldLatchRearm,
        )

        assertEquals(
            1_000L,
            result.remainingMs,
        )
    }

    @Test
    fun expiry_latchesSuppression() {
        val result =
            BetaLifecyclePolicy.evaluate(
                betaBuild = true,
                expiryEpochMs = 2_000L,
                nowWallClockMs = 2_000L,
                maxObservedWallClockMs = 1_900L,
                rearmRequired = false,
            )

        assertTrue(
            result.currentBuildExpired,
        )

        assertTrue(
            result.destructiveSuppressed,
        )

        assertTrue(
            result.shouldLatchRearm,
        )

        assertEquals(
            0L,
            result.remainingMs,
        )
    }

    @Test
    fun wallClockRollback_cannotUnexpireBuild() {
        val result =
            BetaLifecyclePolicy.evaluate(
                betaBuild = true,
                expiryEpochMs = 2_000L,
                nowWallClockMs = 1_000L,
                maxObservedWallClockMs = 2_100L,
                rearmRequired = false,
            )

        assertEquals(
            2_100L,
            result.effectiveWallClockMs,
        )

        assertTrue(
            result.currentBuildExpired,
        )

        assertTrue(
            result.destructiveSuppressed,
        )
    }

    @Test
    fun newerNonBetaBuild_staysSuppressedWhenRearmIsRequired() {
        val result =
            BetaLifecyclePolicy.evaluate(
                betaBuild = false,
                expiryEpochMs = 2_000L,
                nowWallClockMs = 3_000L,
                maxObservedWallClockMs = 3_000L,
                rearmRequired = true,
            )

        assertFalse(
            result.currentBuildExpired,
        )

        assertTrue(
            result.destructiveSuppressed,
        )

        assertFalse(
            result.shouldLatchRearm,
        )
    }

    @Test
    fun newerNonBetaBuild_withoutLatch_isAllowed() {
        val result =
            BetaLifecyclePolicy.evaluate(
                betaBuild = false,
                expiryEpochMs = 2_000L,
                nowWallClockMs = 3_000L,
                maxObservedWallClockMs = 3_000L,
                rearmRequired = false,
            )

        assertFalse(
            result.currentBuildExpired,
        )

        assertFalse(
            result.destructiveSuppressed,
        )
    }
    @Test
    fun rearm_newerHealthyBuild_isAllowed() {
        assertTrue(
            BetaLifecyclePolicy.canRearm(
                currentBuildExpired = false,
                rearmRequired = true,
                currentVersionCode = 3,
                expiredVersionCode = 2,
                stateHealthy = true,
            ),
        )
    }

    @Test
    fun rearm_sameVersion_isRejected() {
        assertFalse(
            BetaLifecyclePolicy.canRearm(
                currentBuildExpired = false,
                rearmRequired = true,
                currentVersionCode = 2,
                expiredVersionCode = 2,
                stateHealthy = true,
            ),
        )
    }

    @Test
    fun rearm_expiredCurrentBuild_isRejected() {
        assertFalse(
            BetaLifecyclePolicy.canRearm(
                currentBuildExpired = true,
                rearmRequired = true,
                currentVersionCode = 3,
                expiredVersionCode = 2,
                stateHealthy = true,
            ),
        )
    }

    @Test
    fun rearm_unhealthyState_isRejected() {
        assertFalse(
            BetaLifecyclePolicy.canRearm(
                currentBuildExpired = false,
                rearmRequired = true,
                currentVersionCode = 3,
                expiredVersionCode = 2,
                stateHealthy = false,
            ),
        )
    }

    @Test
    fun rearm_withoutLatch_isRejected() {
        assertFalse(
            BetaLifecyclePolicy.canRearm(
                currentBuildExpired = false,
                rearmRequired = false,
                currentVersionCode = 3,
                expiredVersionCode = 2,
                stateHealthy = true,
            ),
        )
    }

    @Test
    fun highWater_firstObservation_isPersisted() {
        assertTrue(
            BetaLifecyclePolicy.shouldPersistHighWater(
                storedWallClockMs = 0L,
                effectiveWallClockMs = 1_000L,
                force = false,
            ),
        )
    }

    @Test
    fun highWater_subHourlyObservation_isNotPersisted() {
        assertFalse(
            BetaLifecyclePolicy.shouldPersistHighWater(
                storedWallClockMs = 1_000L,
                effectiveWallClockMs =
                    1_000L +
                        BetaLifecyclePolicy
                            .HIGH_WATER_PERSIST_INTERVAL_MS -
                        1L,
                force = false,
            ),
        )
    }

    @Test
    fun highWater_hourlyObservation_isPersisted() {
        assertTrue(
            BetaLifecyclePolicy.shouldPersistHighWater(
                storedWallClockMs = 1_000L,
                effectiveWallClockMs =
                    1_000L +
                        BetaLifecyclePolicy
                            .HIGH_WATER_PERSIST_INTERVAL_MS,
                force = false,
            ),
        )
    }

    @Test
    fun highWater_expiryForcesImmediatePersistence() {
        assertTrue(
            BetaLifecyclePolicy.shouldPersistHighWater(
                storedWallClockMs = 10_000L,
                effectiveWallClockMs = 10_001L,
                force = true,
            ),
        )
    }

}
