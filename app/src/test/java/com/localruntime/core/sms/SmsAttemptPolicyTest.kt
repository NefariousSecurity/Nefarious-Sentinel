package com.localruntime.core.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsAttemptPolicyTest {

    @Test
    fun firstTwoFailuresHaveNoDelay() {
        assertEquals(
            0L,
            SmsAttemptPolicy
                .delayForFailure(
                    1,
                ),
        )

        assertEquals(
            0L,
            SmsAttemptPolicy
                .delayForFailure(
                    2,
                ),
        )
    }

    @Test
    fun thirdFailureStartsThirtySecondDelay() {
        assertEquals(
            30_000L,
            SmsAttemptPolicy
                .delayForFailure(
                    3,
                ),
        )
    }

    @Test
    fun fourthFailureStartsTwoMinuteDelay() {
        assertEquals(
            120_000L,
            SmsAttemptPolicy
                .delayForFailure(
                    4,
                ),
        )
    }

    @Test
    fun fifthFailureStartsFiveMinuteDelay() {
        assertEquals(
            300_000L,
            SmsAttemptPolicy
                .delayForFailure(
                    5,
                ),
        )
    }

    @Test
    fun sixthAndLaterFailuresAreBoundedAtFifteenMinutes() {
        assertEquals(
            900_000L,
            SmsAttemptPolicy
                .delayForFailure(
                    6,
                ),
        )

        assertEquals(
            900_000L,
            SmsAttemptPolicy
                .delayForFailure(
                    100,
                ),
        )
    }
}
