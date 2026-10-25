package com.localruntime.core.admin

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Device-Protected Storage token for deliberate Standard-mode admin removal.
 *
 * This is not a general "disable tamper protection" switch. The token can only
 * be created by the authenticated Core management path immediately before
 * DevicePolicyManager.removeActiveAdmin() is called.
 *
 * Properties:
 *
 * - one use;
 * - valid only for the current Android boot;
 * - valid for a very short elapsed-realtime window;
 * - synchronously persisted and synchronously consumed;
 * - invalid or stale state fails closed.
 */
class SafeAdminRemovalStore(
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

    /**
     * Arm one deliberate removal attempt.
     *
     * Returns false if a trustworthy current boot identifier cannot be read or
     * if the authorization cannot be synchronously persisted.
     */
    @Synchronized
    fun authorize(): Boolean {
        clear()

        val bootCount =
            currentBootCount()

        if (bootCount < 0) {
            return false
        }

        val expiresAtElapsedRealtimeMs =
            SystemClock.elapsedRealtime() +
                AUTHORIZATION_WINDOW_MS

        return prefs.edit()
            .putBoolean(
                KEY_ARMED,
                true,
            )
            .putInt(
                KEY_BOOT_COUNT,
                bootCount,
            )
            .putLong(
                KEY_EXPIRES_AT_ELAPSED_REALTIME_MS,
                expiresAtElapsedRealtimeMs,
            )
            .commit()
    }

    /**
     * Consume a valid authorization before allowing the non-destructive admin
     * removal path.
     *
     * The token is cleared synchronously before true is returned. If clearing
     * fails, authorization is rejected so DeviceAdminReceiver fails closed to
     * its existing tamper response.
     */
    @Synchronized
    fun consumeIfAuthorized(): Boolean {
        if (
            !prefs.getBoolean(
                KEY_ARMED,
                false,
            )
        ) {
            return false
        }

        val storedBootCount =
            prefs.getInt(
                KEY_BOOT_COUNT,
                -1,
            )

        val currentBootCount =
            currentBootCount()

        val expiresAt =
            prefs.getLong(
                KEY_EXPIRES_AT_ELAPSED_REALTIME_MS,
                -1L,
            )

        val now =
            SystemClock.elapsedRealtime()

        val valid =
            storedBootCount >= 0 &&
                currentBootCount >= 0 &&
                storedBootCount ==
                    currentBootCount &&
                expiresAt > 0L &&
                now <=
                    expiresAt

        if (!valid) {
            clear()

            return false
        }

        return clear()
    }

    /**
     * Clear any pending authorization.
     *
     * The dedicated preferences file contains only this removal token, so a
     * synchronous clear is safe and avoids leaving replayable state behind.
     */
    @Synchronized
    fun clear(): Boolean =
        prefs.edit()
            .clear()
            .commit()

    private fun currentBootCount(): Int =
        try {
            Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.BOOT_COUNT,
                -1,
            )
        } catch (
            ignored: Throwable,
        ) {
            -1
        }

    private companion object {

        private const val PREFS_NAME =
            "standard_admin_safe_removal"

        private const val KEY_ARMED =
            "armed"

        private const val KEY_BOOT_COUNT =
            "boot_count"

        private const val KEY_EXPIRES_AT_ELAPSED_REALTIME_MS =
            "expires_at_elapsed_realtime_ms"

        /*
         * Long enough for DevicePolicyManager to deliver an immediate
         * onDisableRequested() callback on a busy device, while keeping the
         * deliberate-removal exception extremely narrow.
         */
        private const val AUTHORIZATION_WINDOW_MS =
            5_000L
    }
}
