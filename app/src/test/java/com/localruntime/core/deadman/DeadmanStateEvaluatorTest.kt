package com.localruntime.core.deadman

import org.junit.Assert.assertEquals
import org.junit.Test

class DeadmanStateEvaluatorTest {

    private val bootCount =
        42

    private val nowElapsedRealtimeMs =
        500_000L

    private val nowWallClockMs =
        1_800_000_000_000L

    private fun state(
        enabled: Boolean,
        intervalMs: Long =
            DeadmanDirectBootStore.DEFAULT_INTERVAL_MS,
        stateBootCount: Int =
            bootCount,
        expiryElapsedRealtimeMs: Long =
            nowElapsedRealtimeMs +
                DeadmanDirectBootStore
                    .DEFAULT_INTERVAL_MS,
        anchorElapsedRealtimeMs: Long =
            nowElapsedRealtimeMs,
        anchorWallClockMs: Long =
            nowWallClockMs,
    ) =
        DeadmanDirectBootStore.State(
            enabled = enabled,
            intervalMs = intervalMs,
            bootCount = stateBootCount,
            expiryElapsedRealtimeMs =
                expiryElapsedRealtimeMs,
            anchorElapsedRealtimeMs =
                anchorElapsedRealtimeMs,
            anchorWallClockMs =
                anchorWallClockMs,
        )

    @Test
    fun disabledStateClassifiesAsDisabled() {
        assertEquals(
            DeadmanStateEvaluator
                .Classification.Disabled,
            DeadmanStateEvaluator.classify(
                state =
                    state(
                        enabled = false,
                        stateBootCount = -1,
                        expiryElapsedRealtimeMs = 0L,
                        anchorElapsedRealtimeMs = 0L,
                        anchorWallClockMs = 0L,
                    ),
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                currentBootCount =
                    bootCount,
            ),
        )
    }

    @Test
    fun malformedIntervalClassifiesAsInvalid() {
        assertEquals(
            DeadmanStateEvaluator
                .Classification.Invalid,
            DeadmanStateEvaluator.classify(
                state =
                    state(
                        enabled = true,
                        intervalMs = 1L,
                    ),
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                currentBootCount =
                    bootCount,
            ),
        )
    }

    @Test
    fun differentBootClassifiesAsInvalid() {
        assertEquals(
            DeadmanStateEvaluator
                .Classification.Invalid,
            DeadmanStateEvaluator.classify(
                state =
                    state(
                        enabled = true,
                        stateBootCount =
                            bootCount - 1,
                    ),
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                currentBootCount =
                    bootCount,
            ),
        )
    }

    @Test
    fun futureDeadlineClassifiesAsArmed() {
        assertEquals(
            DeadmanStateEvaluator
                .Classification.Armed,
            DeadmanStateEvaluator.classify(
                state =
                    state(
                        enabled = true,
                        expiryElapsedRealtimeMs =
                            nowElapsedRealtimeMs +
                                1L,
                    ),
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                currentBootCount =
                    bootCount,
            ),
        )
    }

    @Test
    fun exactDeadlineClassifiesAsExpired() {
        assertEquals(
            DeadmanStateEvaluator
                .Classification.Expired,
            DeadmanStateEvaluator.classify(
                state =
                    state(
                        enabled = true,
                        expiryElapsedRealtimeMs =
                            nowElapsedRealtimeMs,
                    ),
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                currentBootCount =
                    bootCount,
            ),
        )
    }

    @Test
    fun pastDeadlineClassifiesAsExpired() {
        assertEquals(
            DeadmanStateEvaluator
                .Classification.Expired,
            DeadmanStateEvaluator.classify(
                state =
                    state(
                        enabled = true,
                        expiryElapsedRealtimeMs =
                            nowElapsedRealtimeMs -
                                1L,
                        anchorElapsedRealtimeMs =
                            nowElapsedRealtimeMs -
                                2L,
                    ),
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                currentBootCount =
                    bootCount,
            ),
        )
    }
}
