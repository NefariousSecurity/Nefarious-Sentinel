package com.localruntime.core.sms

/**
 * Pure throttling policy for incorrect SMS Wipe secrets received from the
 * exact configured sender.
 *
 * Wrong senders are not counted. This avoids allowing arbitrary unrelated SMS
 * traffic to lock out the legitimate trigger.
 *
 * Backoff is deliberately bounded. SMS Wipe is an emergency feature, so an
 * attacker should not be able to cause a multi-day denial simply by sending
 * bad guesses.
 */
object SmsAttemptPolicy {

    fun delayForFailure(
        failureCount: Int,
    ): Long =
        when {
            failureCount <= 0 ->
                0L

            failureCount <= 2 ->
                0L

            failureCount == 3 ->
                30_000L

            failureCount == 4 ->
                120_000L

            failureCount == 5 ->
                300_000L

            else ->
                900_000L
        }

    const val MAX_STORED_FAILURE_COUNT =
        6
}
