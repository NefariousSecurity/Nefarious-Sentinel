package com.localruntime.core.smsbridge

import android.content.Context
import com.localruntime.core.log.SecLog
import com.localruntime.core.prefs.SecurePrefs
import com.localruntime.core.sms.SmsAttemptGuard
import com.localruntime.core.sms.SmsKillConfigStore
import com.localruntime.core.sms.SmsTriggerDecision
import com.localruntime.core.wipe.WipeGateway
import com.runtimebridge.helper.HelperProtocol

/**
 * Core-owned decision point for SMS candidates delivered by the isolated
 * SMS Helper.
 *
 * The Helper owns RECEIVE_SMS but has no access to:
 *
 * - the configured authorised sender;
 * - the configured secret keyword;
 * - persistent attempt/backoff state;
 * - Device Admin / Device Owner wipe authority.
 *
 * Core therefore remains the only component able to authenticate and execute
 * the destructive SMS Wipe trigger.
 */
class SmsEventHandler(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val configStore =
        SmsKillConfigStore(
            SecurePrefs.create(
                appContext,
            ),
        )

    private val attemptGuard =
        SmsAttemptGuard(
            appContext,
        )

    private val wipeGateway =
        WipeGateway(
            appContext,
        )

    fun handleMessage(
        sender: String,
        body: String,
    ): Int {
        val config =
            try {
                configStore.load()
            } catch (
                throwable: Throwable,
            ) {
                SecLog.e(
                    TAG,
                    "Failed to load SMS configuration",
                    throwable,
                )

                return HelperProtocol
                    .EVENT_RESULT_UNAVAILABLE
            }

        /*
         * First evaluate without consulting mutable attempt state.
         *
         * This preserves an important ordering property:
         *
         * - disabled / invalid configuration stops immediately;
         * - unrelated senders are rejected before the attempt guard is read;
         * - only an otherwise eligible sender proceeds to lockout handling.
         */
        val preliminaryDecision =
            SmsTriggerDecision.evaluate(
                config = config,
                sender = sender,
                body = body,
                remainingLockoutMs = 0L,
            )

        when (preliminaryDecision) {
            SmsTriggerDecision
                .Result.DISABLED -> {
                SecLog.d(
                    TAG,
                    "SMS candidate ignored because SMS Wipe is disabled",
                )

                return HelperProtocol
                    .EVENT_RESULT_DISABLED
            }

            SmsTriggerDecision
                .Result.CONFIG_INVALID -> {
                SecLog.w(
                    TAG,
                    "SMS candidate ignored because SMS Wipe configuration is invalid",
                )

                return HelperProtocol
                    .EVENT_RESULT_UNAVAILABLE
            }

            SmsTriggerDecision
                .Result.SENDER_MISMATCH -> {
                SecLog.d(
                    TAG,
                    "SMS candidate rejected: sender mismatch",
                )

                return HelperProtocol
                    .EVENT_RESULT_REJECTED
            }

            SmsTriggerDecision
                .Result.LOCKED_OUT -> {
                /*
                 * Not possible with remainingLockoutMs == 0, but keep the
                 * branch explicit so future policy changes remain fail-safe.
                 */
                return HelperProtocol
                    .EVENT_RESULT_REJECTED
            }

            SmsTriggerDecision
                .Result.RECORD_FAILURE,
            SmsTriggerDecision
                .Result.ACCEPT -> {
                /*
                 * Sender and configuration are eligible.
                 * Continue to live policy checks.
                 */
            }
        }

        if (!wipeGateway.hasWipeAuthority()) {
            SecLog.d(
                TAG,
                "SMS candidate ignored because wipe authority is unavailable",
            )

            return HelperProtocol
                .EVENT_RESULT_UNAVAILABLE
        }

        val remainingLockoutMs =
            try {
                attemptGuard
                    .getRemainingLockoutMs()
            } catch (
                throwable: Throwable,
            ) {
                /*
                 * A failure in the persistent rate limiter must not silently
                 * disable throttling on a remotely reachable destructive path.
                 */
                SecLog.e(
                    TAG,
                    "Failed to read SMS attempt guard",
                    throwable,
                )

                return HelperProtocol
                    .EVENT_RESULT_UNAVAILABLE
            }

        val decision =
            SmsTriggerDecision.evaluate(
                config = config,
                sender = sender,
                body = body,
                remainingLockoutMs =
                    remainingLockoutMs,
            )

        return when (decision) {
            SmsTriggerDecision
                .Result.DISABLED -> {
                HelperProtocol
                    .EVENT_RESULT_DISABLED
            }

            SmsTriggerDecision
                .Result.CONFIG_INVALID -> {
                HelperProtocol
                    .EVENT_RESULT_UNAVAILABLE
            }

            SmsTriggerDecision
                .Result.SENDER_MISMATCH -> {
                /*
                 * Preliminary evaluation already rejected this case.
                 * Retain a safe response if state changes concurrently.
                 */
                HelperProtocol
                    .EVENT_RESULT_REJECTED
            }

            SmsTriggerDecision
                .Result.LOCKED_OUT -> {
                SecLog.w(
                    TAG,
                    "SMS candidate rejected because keyword backoff is active " +
                        "remainingMs=$remainingLockoutMs",
                )

                HelperProtocol
                    .EVENT_RESULT_REJECTED
            }

            SmsTriggerDecision
                .Result.RECORD_FAILURE -> {
                recordKeywordFailure()
            }

            SmsTriggerDecision
                .Result.ACCEPT -> {
                executeAuthenticatedTrigger()
            }
        }
    }

    private fun recordKeywordFailure(): Int {
        val delayMs =
            try {
                attemptGuard
                    .recordFailure()
            } catch (
                throwable: Throwable,
            ) {
                /*
                 * If the failure state cannot be persisted, refuse to continue
                 * processing the destructive remote trigger.
                 */
                SecLog.e(
                    TAG,
                    "Failed to persist SMS keyword failure",
                    throwable,
                )

                return HelperProtocol
                    .EVENT_RESULT_UNAVAILABLE
            }

        SecLog.w(
            TAG,
            "SMS candidate rejected: keyword mismatch " +
                "backoffMs=$delayMs",
        )

        return HelperProtocol
            .EVENT_RESULT_REJECTED
    }

    private fun executeAuthenticatedTrigger(): Int {
        /*
         * Authentication succeeded.
         *
         * Clear stale failure state before requesting the wipe. If Android
         * rejects or fails the wipe call, a correctly authenticated future
         * trigger must not remain locked out because of earlier bad guesses.
         */
        try {
            attemptGuard.clear()
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Failed to clear SMS attempt guard after valid trigger",
                throwable,
            )

            return HelperProtocol
                .EVENT_RESULT_UNAVAILABLE
        }

        SecLog.d(
            TAG,
            "SMS candidate matched authorised trigger -> wipe",
        )

        return try {
            val result =
                wipeGateway.wipeNow()

            SecLog.d(
                TAG,
                "SMS helper wipeNow() result=$result",
            )

            HelperProtocol
                .EVENT_RESULT_ACCEPTED
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "SMS wipe invocation failed",
                throwable,
            )

            HelperProtocol
                .EVENT_RESULT_UNAVAILABLE
        }
    }

    companion object {
        private const val TAG =
            "SmsEventHandler"
    }
}
