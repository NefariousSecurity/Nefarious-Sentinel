package com.localruntime.core.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsTriggerDecisionTest {

    private val validConfig =
        SmsKillConfig(
            enabled = true,
            senderNumber =
                "+61412345678",
            keyword =
                "CorrectHorseBattery",
        )

    @Test
    fun disabledConfigurationStopsEvaluation() {
        val result =
            SmsTriggerDecision.evaluate(
                config =
                    validConfig.copy(
                        enabled = false,
                    ),
                sender =
                    "+61412345678",
                body =
                    "CorrectHorseBattery",
                remainingLockoutMs =
                    0L,
            )

        assertEquals(
            SmsTriggerDecision
                .Result.DISABLED,
            result,
        )
    }

    @Test
    fun invalidConfigurationStopsEvaluation() {
        val result =
            SmsTriggerDecision.evaluate(
                config =
                    validConfig.copy(
                        keyword = "short",
                    ),
                sender =
                    "+61412345678",
                body =
                    "short",
                remainingLockoutMs =
                    0L,
            )

        assertEquals(
            SmsTriggerDecision
                .Result.CONFIG_INVALID,
            result,
        )
    }

    @Test
    fun wrongSenderIsRejectedBeforeAttemptHandling() {
        val result =
            SmsTriggerDecision.evaluate(
                config =
                    validConfig,
                sender =
                    "+61499999999",
                body =
                    "WrongSecretValue",
                /*
                 * Even with an active lockout, sender mismatch wins because
                 * unrelated senders must not participate in attempt state.
                 */
                remainingLockoutMs =
                    900_000L,
            )

        assertEquals(
            SmsTriggerDecision
                .Result.SENDER_MISMATCH,
            result,
        )
    }

    @Test
    fun authorisedSenderIsBlockedByActiveLockout() {
        val result =
            SmsTriggerDecision.evaluate(
                config =
                    validConfig,
                sender =
                    "+61412345678",
                body =
                    "CorrectHorseBattery",
                remainingLockoutMs =
                    30_000L,
            )

        assertEquals(
            SmsTriggerDecision
                .Result.LOCKED_OUT,
            result,
        )
    }

    @Test
    fun authorisedSenderWrongSecretRequestsFailureRecording() {
        val result =
            SmsTriggerDecision.evaluate(
                config =
                    validConfig,
                sender =
                    "+61412345678",
                body =
                    "IncorrectHorseBattery",
                remainingLockoutMs =
                    0L,
            )

        assertEquals(
            SmsTriggerDecision
                .Result.RECORD_FAILURE,
            result,
        )
    }

    @Test
    fun authorisedSenderCorrectSecretIsAccepted() {
        val result =
            SmsTriggerDecision.evaluate(
                config =
                    validConfig,
                sender =
                    "+61412345678",
                body =
                    "CorrectHorseBattery",
                remainingLockoutMs =
                    0L,
            )

        assertEquals(
            SmsTriggerDecision
                .Result.ACCEPT,
            result,
        )
    }

    @Test
    fun oldSubstringBehaviourDoesNotReachAccept() {
        val result =
            SmsTriggerDecision.evaluate(
                config =
                    validConfig,
                sender =
                    "+61412345678",
                body =
                    "please CorrectHorseBattery now",
                remainingLockoutMs =
                    0L,
            )

        assertEquals(
            SmsTriggerDecision
                .Result.RECORD_FAILURE,
            result,
        )
    }

    @Test
    fun oldCaseInsensitiveBehaviourDoesNotReachAccept() {
        val result =
            SmsTriggerDecision.evaluate(
                config =
                    validConfig,
                sender =
                    "+61412345678",
                body =
                    "correcthorsebattery",
                remainingLockoutMs =
                    0L,
            )

        assertEquals(
            SmsTriggerDecision
                .Result.RECORD_FAILURE,
            result,
        )
    }
}
