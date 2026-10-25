package com.localruntime.core.deadman

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Direct-Boot-safe persistent state for the Dead Man's Switch.
 *
 * Enforcement uses SystemClock.elapsedRealtime() while the device is running.
 * Wall-clock time is retained only as a conservative reboot-recovery anchor.
 *
 * Changing the Android date or time must never extend an armed deadline.
 *
 * AlarmManager alarms do not survive reboot and elapsedRealtime() resets at
 * reboot. To recover safely across that boundary, the store persists:
 *
 *  - the boot count that owns the elapsed-time deadline;
 *  - the absolute elapsedRealtime() deadline for that boot;
 *  - an elapsed-time checkpoint;
 *  - a wall-clock checkpoint used only to estimate time that passed across
 *    the reboot boundary.
 *
 * Reboot recovery is deliberately conservative. It may reduce the remaining
 * interval, but it must never increase the amount of time previously stored.
 */
class DeadmanDirectBootStore(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val deviceContext =
        appContext.createDeviceProtectedStorageContext()

    private val prefs: SharedPreferences =
        deviceContext.getSharedPreferences(
            FILE_NAME,
            Context.MODE_PRIVATE,
        )

    data class State(
        val enabled: Boolean,
        val intervalMs: Long,
        val bootCount: Int,
        val expiryElapsedRealtimeMs: Long,
        val anchorElapsedRealtimeMs: Long,
        val anchorWallClockMs: Long,
    ) {
        fun isReady(): Boolean =
            enabled &&
                intervalMs in MIN_INTERVAL_MS..MAX_INTERVAL_MS &&
                bootCount >= 0 &&
                expiryElapsedRealtimeMs > 0L &&
                anchorElapsedRealtimeMs >= 0L &&
                anchorWallClockMs > 0L &&
                expiryElapsedRealtimeMs >=
                    anchorElapsedRealtimeMs

        fun remainingSameBootMs(
            nowElapsedRealtimeMs: Long,
            currentBootCount: Int,
        ): Long {
            if (!isReady()) {
                return 0L
            }

            if (bootCount != currentBootCount) {
                return 0L
            }

            return expiryElapsedRealtimeMs -
                nowElapsedRealtimeMs
        }

        fun isExpiredSameBoot(
            nowElapsedRealtimeMs: Long,
            currentBootCount: Int,
        ): Boolean =
            isReady() &&
                bootCount == currentBootCount &&
                expiryElapsedRealtimeMs <=
                    nowElapsedRealtimeMs

        fun remainingAtAnchorMs(): Long =
            if (!isReady()) {
                0L
            } else {
                expiryElapsedRealtimeMs -
                    anchorElapsedRealtimeMs
            }
    }

    /**
     * Load persisted state without crossing a boot boundary.
     *
     * Most enforcement callers should use [loadCurrent] instead.
     */
    @Synchronized
    fun load(): State {
        val enabled =
            prefs.getBoolean(
                KEY_ENABLED,
                false,
            )

        val storedIntervalMs =
            prefs.getLong(
                KEY_INTERVAL_MS,
                DEFAULT_INTERVAL_MS,
            )

        val intervalMs =
            if (enabled) {
                /*
                 * Never silently repair malformed active state.
                 */
                storedIntervalMs
            } else {
                storedIntervalMs.coerceIn(
                    MIN_INTERVAL_MS,
                    MAX_INTERVAL_MS,
                )
            }

        return State(
            enabled = enabled,
            intervalMs = intervalMs,
            bootCount =
                prefs.getInt(
                    KEY_BOOT_COUNT,
                    -1,
                ),
            expiryElapsedRealtimeMs =
                prefs.getLong(
                    KEY_EXPIRY_ELAPSED_REALTIME_MS,
                    0L,
                ),
            anchorElapsedRealtimeMs =
                prefs.getLong(
                    KEY_ANCHOR_ELAPSED_REALTIME_MS,
                    0L,
                ),
            anchorWallClockMs =
                prefs.getLong(
                    KEY_ANCHOR_WALL_CLOCK_MS,
                    0L,
                ),
        )
    }

    /**
     * Return state rebased onto the current boot when necessary.
     *
     * This method also performs the one-time migration from the Alpha 1
     * wall-clock-only representation.
     */
    @Synchronized
    fun loadCurrent(
        nowElapsedRealtimeMs: Long =
            SystemClock.elapsedRealtime(),
        nowWallClockMs: Long =
            System.currentTimeMillis(),
        currentBootCount: Int =
            currentBootCount(),
    ): State {
        migrateLegacyIfNeeded(
            nowElapsedRealtimeMs =
                nowElapsedRealtimeMs,
            nowWallClockMs =
                nowWallClockMs,
            currentBootCount =
                currentBootCount,
        )

        val current =
            load()

        if (!current.enabled) {
            return current
        }

        if (!current.isReady()) {
            return current
        }

        if (current.bootCount == currentBootCount) {
            return current
        }

        return rebaseAcrossBoot(
            current = current,
            nowElapsedRealtimeMs =
                nowElapsedRealtimeMs,
            nowWallClockMs =
                nowWallClockMs,
            currentBootCount =
                currentBootCount,
        )
    }

    /**
     * Enable or disable the Dead Man's Switch.
     *
     * Enabling creates a fresh monotonic deadline.
     * Disabling clears all active timing state.
     */
    @Synchronized
    fun setEnabled(
        enabled: Boolean,
        nowElapsedRealtimeMs: Long =
            SystemClock.elapsedRealtime(),
        nowWallClockMs: Long =
            System.currentTimeMillis(),
        currentBootCount: Int =
            currentBootCount(),
    ): State {
        val current =
            loadCurrent(
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                nowWallClockMs =
                    nowWallClockMs,
                currentBootCount =
                    currentBootCount,
            )

        if (!enabled) {
            persist(
                enabled = false,
                intervalMs = current.intervalMs,
                bootCount = -1,
                expiryElapsedRealtimeMs = 0L,
                anchorElapsedRealtimeMs = 0L,
                anchorWallClockMs = 0L,
            )

            return load()
        }

        require(
            current.intervalMs in
                MIN_INTERVAL_MS..MAX_INTERVAL_MS,
        ) {
            "Dead Man interval outside permitted range"
        }

        val deadline =
            checkedDeadline(
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                intervalMs =
                    current.intervalMs,
            )

        persist(
            enabled = true,
            intervalMs = current.intervalMs,
            bootCount = currentBootCount,
            expiryElapsedRealtimeMs = deadline,
            anchorElapsedRealtimeMs =
                nowElapsedRealtimeMs,
            anchorWallClockMs =
                nowWallClockMs,
        )

        return load()
    }

    /**
     * Change the configured interval.
     *
     * An active valid state receives a fresh monotonic deadline only after the
     * existing deadline has been confirmed not to have expired.
     */
    @Synchronized
    fun setIntervalMs(
        intervalMs: Long,
        nowElapsedRealtimeMs: Long =
            SystemClock.elapsedRealtime(),
        nowWallClockMs: Long =
            System.currentTimeMillis(),
        currentBootCount: Int =
            currentBootCount(),
    ): State {
        val current =
            loadCurrent(
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                nowWallClockMs =
                    nowWallClockMs,
                currentBootCount =
                    currentBootCount,
            )

        val validatedIntervalMs =
            intervalMs.coerceIn(
                MIN_INTERVAL_MS,
                MAX_INTERVAL_MS,
            )

        if (current.enabled) {
            check(current.isReady()) {
                "Dead Man state is not ready for interval change"
            }

            check(
                !current.isExpiredSameBoot(
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                    currentBootCount =
                        currentBootCount,
                ),
            ) {
                "Cannot change Dead Man interval after expiry"
            }
        }

        if (!current.enabled) {
            persist(
                enabled = false,
                intervalMs = validatedIntervalMs,
                bootCount = -1,
                expiryElapsedRealtimeMs = 0L,
                anchorElapsedRealtimeMs = 0L,
                anchorWallClockMs = 0L,
            )

            return load()
        }

        val deadline =
            checkedDeadline(
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                intervalMs =
                    validatedIntervalMs,
            )

        persist(
            enabled = true,
            intervalMs = validatedIntervalMs,
            bootCount = currentBootCount,
            expiryElapsedRealtimeMs = deadline,
            anchorElapsedRealtimeMs =
                nowElapsedRealtimeMs,
            anchorWallClockMs =
                nowWallClockMs,
        )

        return load()
    }

    /**
     * Refresh the deadline after a legitimate owner-presence event.
     */
    @Synchronized
    fun checkIn(
        nowElapsedRealtimeMs: Long =
            SystemClock.elapsedRealtime(),
        nowWallClockMs: Long =
            System.currentTimeMillis(),
        currentBootCount: Int =
            currentBootCount(),
    ): State {
        val current =
            loadCurrent(
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                nowWallClockMs =
                    nowWallClockMs,
                currentBootCount =
                    currentBootCount,
            )

        if (!current.enabled) {
            return current
        }

        check(current.isReady()) {
            "Dead Man state is not ready for check-in"
        }

        check(
            !current.isExpiredSameBoot(
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                currentBootCount =
                    currentBootCount,
            ),
        ) {
            "Cannot check in after Dead Man expiry"
        }

        val deadline =
            checkedDeadline(
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                intervalMs =
                    current.intervalMs,
            )

        persist(
            enabled = true,
            intervalMs = current.intervalMs,
            bootCount = currentBootCount,
            expiryElapsedRealtimeMs = deadline,
            anchorElapsedRealtimeMs =
                nowElapsedRealtimeMs,
            anchorWallClockMs =
                nowWallClockMs,
        )

        return load()
    }

    /**
     * Persist a more recent recovery checkpoint without changing the deadline.
     *
     * The wall-clock anchor is never allowed to move backwards. This prevents a
     * manual clock rollback from being persisted as if it were legitimate time.
     */
    @Synchronized
    fun checkpoint(
        nowElapsedRealtimeMs: Long =
            SystemClock.elapsedRealtime(),
        nowWallClockMs: Long =
            System.currentTimeMillis(),
        currentBootCount: Int =
            currentBootCount(),
    ): State {
        val current =
            loadCurrent(
                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
                nowWallClockMs =
                    nowWallClockMs,
                currentBootCount =
                    currentBootCount,
            )

        if (!current.enabled || !current.isReady()) {
            return current
        }

        if (current.bootCount != currentBootCount) {
            return current
        }

        persist(
            enabled = true,
            intervalMs = current.intervalMs,
            bootCount = current.bootCount,
            expiryElapsedRealtimeMs =
                current.expiryElapsedRealtimeMs,
            anchorElapsedRealtimeMs =
                nowElapsedRealtimeMs,
            anchorWallClockMs =
                max(
                    current.anchorWallClockMs,
                    nowWallClockMs,
                ),
        )

        return load()
    }

    fun currentBootCount(): Int =
        runCatching {
            Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.BOOT_COUNT,
                0,
            )
        }.getOrDefault(0)

    private fun rebaseAcrossBoot(
        current: State,
        nowElapsedRealtimeMs: Long,
        nowWallClockMs: Long,
        currentBootCount: Int,
    ): State {
        val remainingAtAnchor =
            current.remainingAtAnchorMs()

        val recoveredRemaining =
            DeadmanRecoveryMath
                .recoverRemainingMs(
                    remainingAtAnchorMs =
                        remainingAtAnchor,
                    anchorWallClockMs =
                        current.anchorWallClockMs,
                    nowWallClockMs =
                        nowWallClockMs,
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                )

        val rebasedDeadline =
            if (recoveredRemaining <= 0L) {
                nowElapsedRealtimeMs
            } else {
                checkedDeadline(
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                    intervalMs =
                        recoveredRemaining,
                    enforceConfiguredRange = false,
                )
            }

        persist(
            enabled = true,
            intervalMs = current.intervalMs,
            bootCount = currentBootCount,
            expiryElapsedRealtimeMs =
                rebasedDeadline,
            anchorElapsedRealtimeMs =
                nowElapsedRealtimeMs,
            anchorWallClockMs =
                max(
                    current.anchorWallClockMs,
                    nowWallClockMs,
                ),
        )

        return load()
    }

    /**
     * Migrate Alpha 1's wall-clock-only deadline into the new dual-clock state.
     */
    private fun migrateLegacyIfNeeded(
        nowElapsedRealtimeMs: Long,
        nowWallClockMs: Long,
        currentBootCount: Int,
    ) {
        if (
            prefs.contains(
                KEY_EXPIRY_ELAPSED_REALTIME_MS,
            )
        ) {
            return
        }

        if (
            !prefs.contains(
                LEGACY_KEY_EXPIRY_WALL_CLOCK_MS,
            )
        ) {
            return
        }

        val enabled =
            prefs.getBoolean(
                KEY_ENABLED,
                false,
            )

        val intervalMs =
            prefs.getLong(
                KEY_INTERVAL_MS,
                DEFAULT_INTERVAL_MS,
            )

        val legacyExpiry =
            prefs.getLong(
                LEGACY_KEY_EXPIRY_WALL_CLOCK_MS,
                0L,
            )

        if (!enabled || legacyExpiry <= 0L) {
            prefs.edit()
                .remove(
                    LEGACY_KEY_EXPIRY_WALL_CLOCK_MS,
                )
                .commit()

            return
        }

        val wallRemaining =
            legacyExpiry -
                nowWallClockMs

        val boundedRemaining =
            wallRemaining.coerceAtMost(
                intervalMs.coerceIn(
                    MIN_INTERVAL_MS,
                    MAX_INTERVAL_MS,
                ),
            )

        val deadline =
            if (boundedRemaining <= 0L) {
                nowElapsedRealtimeMs
            } else {
                checkedDeadline(
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                    intervalMs =
                        boundedRemaining,
                    enforceConfiguredRange = false,
                )
            }

        val committed =
            prefs.edit()
                .putInt(
                    KEY_BOOT_COUNT,
                    currentBootCount,
                )
                .putLong(
                    KEY_EXPIRY_ELAPSED_REALTIME_MS,
                    deadline,
                )
                .putLong(
                    KEY_ANCHOR_ELAPSED_REALTIME_MS,
                    nowElapsedRealtimeMs,
                )
                .putLong(
                    KEY_ANCHOR_WALL_CLOCK_MS,
                    nowWallClockMs,
                )
                .remove(
                    LEGACY_KEY_EXPIRY_WALL_CLOCK_MS,
                )
                .commit()

        check(committed) {
            "Failed to migrate legacy Dead Man state"
        }
    }

    private fun persist(
        enabled: Boolean,
        intervalMs: Long,
        bootCount: Int,
        expiryElapsedRealtimeMs: Long,
        anchorElapsedRealtimeMs: Long,
        anchorWallClockMs: Long,
    ) {
        val committed =
            prefs.edit()
                .putBoolean(
                    KEY_ENABLED,
                    enabled,
                )
                .putLong(
                    KEY_INTERVAL_MS,
                    intervalMs,
                )
                .putInt(
                    KEY_BOOT_COUNT,
                    bootCount,
                )
                .putLong(
                    KEY_EXPIRY_ELAPSED_REALTIME_MS,
                    expiryElapsedRealtimeMs,
                )
                .putLong(
                    KEY_ANCHOR_ELAPSED_REALTIME_MS,
                    anchorElapsedRealtimeMs,
                )
                .putLong(
                    KEY_ANCHOR_WALL_CLOCK_MS,
                    anchorWallClockMs,
                )
                .remove(
                    LEGACY_KEY_EXPIRY_WALL_CLOCK_MS,
                )
                .commit()

        check(committed) {
            "Failed to commit Direct Boot Dead Man state"
        }
    }

    private fun checkedDeadline(
        nowElapsedRealtimeMs: Long,
        intervalMs: Long,
        enforceConfiguredRange: Boolean = true,
    ): Long {
        require(nowElapsedRealtimeMs >= 0L) {
            "Invalid elapsed realtime"
        }

        if (enforceConfiguredRange) {
            require(
                intervalMs in
                    MIN_INTERVAL_MS..MAX_INTERVAL_MS,
            ) {
                "Dead Man interval outside permitted range"
            }
        } else {
            require(intervalMs > 0L) {
                "Dead Man remaining interval must be positive"
            }
        }

        check(
            nowElapsedRealtimeMs <=
                Long.MAX_VALUE -
                intervalMs,
        ) {
            "Dead Man deadline overflow"
        }

        return nowElapsedRealtimeMs +
            intervalMs
    }

    companion object {
        private const val FILE_NAME =
            "runtime_state_monitor"

        private const val KEY_ENABLED =
            "deadman.enabled"

        private const val KEY_INTERVAL_MS =
            "deadman.interval_ms"

        private const val KEY_BOOT_COUNT =
            "deadman.boot_count"

        private const val KEY_EXPIRY_ELAPSED_REALTIME_MS =
            "deadman.expiry_elapsed_realtime_ms"

        private const val KEY_ANCHOR_ELAPSED_REALTIME_MS =
            "deadman.anchor_elapsed_realtime_ms"

        private const val KEY_ANCHOR_WALL_CLOCK_MS =
            "deadman.anchor_wall_clock_ms"

        private const val LEGACY_KEY_EXPIRY_WALL_CLOCK_MS =
            "deadman.expiry_wall_clock_ms"

        val MIN_INTERVAL_MS: Long =
            TimeUnit.HOURS.toMillis(1)

        val MAX_INTERVAL_MS: Long =
            TimeUnit.DAYS.toMillis(60)

        val DEFAULT_INTERVAL_MS: Long =
            TimeUnit.HOURS.toMillis(48)
    }
}
