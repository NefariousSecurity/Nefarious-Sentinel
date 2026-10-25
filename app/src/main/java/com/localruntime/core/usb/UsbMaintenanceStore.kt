package com.localruntime.core.usb

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock

/**
 * Temporary suppression state for legitimate USB backup / maintenance work.
 *
 * This store is deliberately separate from UsbProtectionStore.
 *
 * USB Protection is the user's persistent normal policy and survives reboot.
 * Maintenance Mode temporarily suppresses that policy for a bounded period.
 *
 * Maintenance state is stored in Device Protected Storage so it survives an
 * application process restart during a backup or maintenance session.
 *
 * The expiry uses SystemClock.elapsedRealtime() rather than wall-clock time.
 * This prevents changing the system clock from extending the maintenance
 * window.
 *
 * Maintenance Mode must never survive a device reboot. The USB Direct Boot
 * receiver clears this store on LOCKED_BOOT_COMPLETED before starting the USB
 * detector service.
 *
 * A hard limit of [MAX_EXTENDS] additional +30-minute extensions is enforced
 * so the total suppression window cannot grow indefinitely.
 */
class UsbMaintenanceStore(context: Context) {

    private val deviceContext: Context =
        context.applicationContext.createDeviceProtectedStorageContext()

    private val prefs: SharedPreferences =
        deviceContext.getSharedPreferences(
            FILE_NAME,
            Context.MODE_PRIVATE,
        )

    data class State(
        val active: Boolean,
        val expiresAtElapsedRealtimeMs: Long,
        val remainingMs: Long,
        val extendsUsed: Int,
    )

    /**
     * Read the authoritative Maintenance Mode state.
     *
     * Expired state is cleaned up synchronously when encountered.
     */
    @Synchronized
    fun load(): State {
        val expiresAt =
            prefs.getLong(
                KEY_EXPIRES_AT_ELAPSED_REALTIME_MS,
                0L,
            )

        val extendsUsed =
            prefs.getInt(
                KEY_EXTENDS_USED,
                0,
            ).coerceAtLeast(0)

        if (expiresAt <= 0L) {
            return inactiveState()
        }

        val now =
            SystemClock.elapsedRealtime()

        val remaining =
            expiresAt - now

        if (remaining <= 0L) {
            clearInternal()

            return inactiveState()
        }

        return State(
            active = true,
            expiresAtElapsedRealtimeMs = expiresAt,
            remainingMs = remaining,
            extendsUsed = extendsUsed,
        )
    }

    /**
     * Begin a new bounded Maintenance Mode window.
     *
     * Starting maintenance replaces any existing maintenance expiry and
     * resets the extension counter to zero.
     */
    @Synchronized
    fun start(
        durationMs: Long = DEFAULT_DURATION_MS,
    ): State {
        require(durationMs > 0L) {
            "Maintenance duration must be greater than zero"
        }

        val now =
            SystemClock.elapsedRealtime()

        val expiresAt =
            Math.addExact(
                now,
                durationMs,
            )

        val committed =
            prefs.edit()
                .putLong(
                    KEY_EXPIRES_AT_ELAPSED_REALTIME_MS,
                    expiresAt,
                )
                .putInt(
                    KEY_EXTENDS_USED,
                    0,
                )
                .commit()

        check(committed) {
            "Failed to commit USB Maintenance Mode state"
        }

        return load()
    }

    /**
     * Extend an already-active Maintenance Mode window by [extraMs],
     * subject to the hard limit of [MAX_EXTENDS] extensions.
     *
     * If Maintenance Mode is not active, or the extension limit has already
     * been reached, this is a no-op and returns the current state.
     */
    @Synchronized
    fun extend(
        extraMs: Long = DEFAULT_DURATION_MS,
    ): State {
        require(extraMs > 0L) {
            "Extension duration must be greater than zero"
        }

        val current = load()
        if (!current.active) {
            return current
        }

        if (current.extendsUsed >= MAX_EXTENDS) {
            // Hard limit reached — do not extend further
            return current
        }

        val now = SystemClock.elapsedRealtime()
        val newExpiresAt = Math.addExact(
            now,
            Math.addExact(current.remainingMs, extraMs),
        )

        val newExtendsUsed = current.extendsUsed + 1

        val committed = prefs.edit()
            .putLong(
                KEY_EXPIRES_AT_ELAPSED_REALTIME_MS,
                newExpiresAt,
            )
            .putInt(
                KEY_EXTENDS_USED,
                newExtendsUsed,
            )
            .commit()

        check(committed) {
            "Failed to commit USB Maintenance Mode extension"
        }

        return load()
    }

    /**
     * End Maintenance Mode immediately.
     */
    @Synchronized
    fun cancel(): State {
        clearInternal()

        return inactiveState()
    }

    /**
     * Clear Maintenance Mode at the device reboot boundary.
     *
     * UsbDataProbeBootReceiver calls this on LOCKED_BOOT_COMPLETED before the
     * Direct Boot USB detector service is started.
     */
    @Synchronized
    fun clearForBoot(): State {
        clearInternal()

        return inactiveState()
    }

    private fun clearInternal() {
        val committed =
            prefs.edit()
                .remove(
                    KEY_EXPIRES_AT_ELAPSED_REALTIME_MS,
                )
                .remove(
                    KEY_EXTENDS_USED,
                )
                .commit()

        check(committed) {
            "Failed to clear USB Maintenance Mode state"
        }
    }

    private fun inactiveState(): State =
        State(
            active = false,
            expiresAtElapsedRealtimeMs = 0L,
            remainingMs = 0L,
            extendsUsed = 0,
        )

    companion object {
        private const val FILE_NAME =
            "runtime_state_maintenance"

        private const val KEY_EXPIRES_AT_ELAPSED_REALTIME_MS =
            "usb_maintenance.expires_at_elapsed_realtime_ms"

        private const val KEY_EXTENDS_USED =
            "usb_maintenance.extends_used"

        const val DEFAULT_DURATION_MS: Long =
            30L * 60L * 1000L

        /** Maximum number of +30-minute extensions allowed per maintenance session. */
        const val MAX_EXTENDS: Int = 3
    }
}
