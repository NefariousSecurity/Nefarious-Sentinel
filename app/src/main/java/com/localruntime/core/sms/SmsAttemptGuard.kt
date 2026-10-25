package com.localruntime.core.sms

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Persistent rate limiter for SMS Wipe keyword failures.
 *
 * State is stored in Device Protected Storage so process death or reboot cannot
 * trivially reset an active backoff.
 *
 * elapsedRealtime() is authoritative within one boot. If the device reboots
 * during a lockout, the complete stored lockout interval begins again. A reboot
 * can therefore never shorten an active SMS backoff.
 */
class SmsAttemptGuard(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val storageContext =
        appContext
            .createDeviceProtectedStorageContext()

    private val prefs =
        storageContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    @Synchronized
    fun getFailureCount(): Int =
        prefs.getInt(
            KEY_FAILURE_COUNT,
            0,
        ).coerceIn(
            0,
            SmsAttemptPolicy
                .MAX_STORED_FAILURE_COUNT,
        )

    /**
     * Remaining active backoff.
     *
     * Reboot while blocked restarts the complete applicable interval.
     */
    @Synchronized
    fun getRemainingLockoutMs(): Long {
        val duration =
            prefs.getLong(
                KEY_LOCKOUT_DURATION_MS,
                0L,
            )

        if (duration <= 0L) {
            return 0L
        }

        val currentBoot =
            currentBootCount()

        val storedBoot =
            prefs.getInt(
                KEY_LOCKOUT_BOOT_COUNT,
                currentBoot,
            )

        val now =
            SystemClock.elapsedRealtime()

        if (
            currentBoot !=
            storedBoot
        ) {
            val committed =
                prefs.edit()
                    .putInt(
                        KEY_LOCKOUT_BOOT_COUNT,
                        currentBoot,
                    )
                    .putLong(
                        KEY_LOCKOUT_START_ELAPSED_MS,
                        now,
                    )
                    .commit()

            check(committed) {
                "Failed to persist SMS lockout reboot recovery"
            }

            return duration
        }

        val started =
            prefs.getLong(
                KEY_LOCKOUT_START_ELAPSED_MS,
                now,
            )

        val remaining =
            duration -
                (
                    now -
                        started
                    )

        if (remaining <= 0L) {
            clearLockout()

            return 0L
        }

        return remaining
    }

    /**
     * Record one distinct incoming keyword failure after the sender has already
     * matched the exact configured sender.
     */
    @Synchronized
    fun recordFailure(): Long {
        val updatedCount =
            (
                getFailureCount() +
                    1
                ).coerceAtMost(
                SmsAttemptPolicy
                    .MAX_STORED_FAILURE_COUNT,
            )

        val delay =
            SmsAttemptPolicy
                .delayForFailure(
                    updatedCount,
                )

        val editor =
            prefs.edit()
                .putInt(
                    KEY_FAILURE_COUNT,
                    updatedCount,
                )

        if (delay > 0L) {
            editor
                .putLong(
                    KEY_LOCKOUT_DURATION_MS,
                    delay,
                )
                .putLong(
                    KEY_LOCKOUT_START_ELAPSED_MS,
                    SystemClock.elapsedRealtime(),
                )
                .putInt(
                    KEY_LOCKOUT_BOOT_COUNT,
                    currentBootCount(),
                )
        } else {
            editor
                .remove(
                    KEY_LOCKOUT_DURATION_MS,
                )
                .remove(
                    KEY_LOCKOUT_START_ELAPSED_MS,
                )
                .remove(
                    KEY_LOCKOUT_BOOT_COUNT,
                )
        }

        check(
            editor.commit(),
        ) {
            "Failed to persist SMS failure state"
        }

        return delay
    }

    /**
     * Clear all failure/backoff state.
     *
     * Called after a valid trigger and when the authenticated user changes SMS
     * Wipe configuration.
     */
    @Synchronized
    fun clear() {
        check(
            prefs.edit()
                .remove(
                    KEY_FAILURE_COUNT,
                )
                .remove(
                    KEY_LOCKOUT_DURATION_MS,
                )
                .remove(
                    KEY_LOCKOUT_START_ELAPSED_MS,
                )
                .remove(
                    KEY_LOCKOUT_BOOT_COUNT,
                )
                .commit(),
        ) {
            "Failed to clear SMS failure state"
        }
    }

    private fun clearLockout() {
        check(
            prefs.edit()
                .remove(
                    KEY_LOCKOUT_DURATION_MS,
                )
                .remove(
                    KEY_LOCKOUT_START_ELAPSED_MS,
                )
                .remove(
                    KEY_LOCKOUT_BOOT_COUNT,
                )
                .commit(),
        ) {
            "Failed to clear expired SMS lockout"
        }
    }

    private fun currentBootCount(): Int =
        runCatching {
            Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.BOOT_COUNT,
                0,
            )
        }.getOrDefault(
            0,
        )

    companion object {
        private const val PREFS_NAME =
            "sms_attempt_guard_v1"

        private const val KEY_FAILURE_COUNT =
            "failure_count"

        private const val KEY_LOCKOUT_DURATION_MS =
            "lockout_duration_ms"

        private const val KEY_LOCKOUT_START_ELAPSED_MS =
            "lockout_start_elapsed_ms"

        private const val KEY_LOCKOUT_BOOT_COUNT =
            "lockout_boot_count"
    }
}
