package com.localruntime.core.deadman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadmanRecoveryMathTest {

    private val anchorWall =
        1_800_000_000_000L

    @Test
    fun normalRebootReducesRemainingByElapsedTime() {
        val remaining =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        3_600_000L,
                    anchorWallClockMs =
                        anchorWall,
                    nowWallClockMs =
                        anchorWall + 120_000L,
                    nowElapsedRealtimeMs =
                        5_000L,
                )

        assertEquals(
            3_480_000L,
            remaining,
        )
    }

    @Test
    fun backwardsWallClockCannotIncreaseRemainingTime() {
        val originalRemaining =
            3_600_000L

        val recovered =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        originalRemaining,
                    anchorWallClockMs =
                        anchorWall,
                    nowWallClockMs =
                        anchorWall -
                            86_400_000L,
                    nowElapsedRealtimeMs =
                        10_000L,
                )

        assertEquals(
            originalRemaining -
                10_000L,
            recovered,
        )

        assertTrue(
            recovered <
                originalRemaining,
        )
    }

    @Test
    fun hugeBackwardWallClockStillCannotAddTime() {
        val originalRemaining =
            172_800_000L

        val recovered =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        originalRemaining,
                    anchorWallClockMs =
                        anchorWall,
                    nowWallClockMs =
                        anchorWall -
                            365L *
                            24L *
                            60L *
                            60L *
                            1000L,
                    nowElapsedRealtimeMs =
                        30_000L,
                )

        assertEquals(
            originalRemaining -
                30_000L,
            recovered,
        )
    }

    @Test
    fun forwardWallClockCanOnlyReduceRemainingTime() {
        val recovered =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        3_600_000L,
                    anchorWallClockMs =
                        anchorWall,
                    nowWallClockMs =
                        anchorWall +
                            1_800_000L,
                    nowElapsedRealtimeMs =
                        5_000L,
                )

        assertEquals(
            1_800_000L,
            recovered,
        )
    }

    @Test
    fun elapsedRealtimeWinsWhenGreaterThanWallDelta() {
        val recovered =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        3_600_000L,
                    anchorWallClockMs =
                        anchorWall,
                    nowWallClockMs =
                        anchorWall +
                            5_000L,
                    nowElapsedRealtimeMs =
                        60_000L,
                )

        assertEquals(
            3_540_000L,
            recovered,
        )
    }

    @Test
    fun recoveryNeverReturnsNegativeRemainingTime() {
        val recovered =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        60_000L,
                    anchorWallClockMs =
                        anchorWall,
                    nowWallClockMs =
                        anchorWall +
                            120_000L,
                    nowElapsedRealtimeMs =
                        10_000L,
                )

        assertEquals(
            0L,
            recovered,
        )
    }

    @Test
    fun repeatedRebootRecoveryNeverIncreasesRemainingTime() {
        val first =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        3_600_000L,
                    anchorWallClockMs =
                        anchorWall,
                    nowWallClockMs =
                        anchorWall +
                            30_000L,
                    nowElapsedRealtimeMs =
                        5_000L,
                )

        val second =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        first,
                    anchorWallClockMs =
                        anchorWall +
                            30_000L,
                    nowWallClockMs =
                        anchorWall +
                            60_000L,
                    nowElapsedRealtimeMs =
                        5_000L,
                )

        assertTrue(
            first <=
                3_600_000L,
        )

        assertTrue(
            second <= first,
        )
    }

    @Test
    fun rollbackPlusRepeatedRebootStillConsumesElapsedTime() {
        val first =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        3_600_000L,
                    anchorWallClockMs =
                        anchorWall,
                    nowWallClockMs =
                        anchorWall -
                            86_400_000L,
                    nowElapsedRealtimeMs =
                        10_000L,
                )

        val second =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        first,
                    anchorWallClockMs =
                        anchorWall,
                    nowWallClockMs =
                        anchorWall -
                            172_800_000L,
                    nowElapsedRealtimeMs =
                        10_000L,
                )

        assertEquals(
            3_580_000L,
            second,
        )
    }
}
