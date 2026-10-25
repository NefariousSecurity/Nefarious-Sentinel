package com.localruntime.core.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.localruntime.core.beta.BetaLifecycleGuard
import com.localruntime.core.log.SecLog

/**
 * Device-policy helper for the persistent local Core.
 *
 * Sentinel can operate with two device-policy authority levels:
 *
 * - Hardened mode:
 *   this Core package is Device Owner.
 *
 * - Standard mode:
 *   this Core package is an active legacy Device Admin.
 *
 * Lock and wipe may use either authority where Android permits it.
 * Reboot deliberately remains Device Owner-only.
 */
class DeviceAdminManager(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val dpm =
        appContext.getSystemService(
            DevicePolicyManager::class.java,
        )

    private val adminComponent =
        ComponentName(
            appContext,
            DeviceAdminReceiver::class.java,
        )

    /**
     * Return true when this exact Core package is Device Owner.
     */
    fun isDeviceOwner(): Boolean =
        dpm?.isDeviceOwnerApp(
            appContext.packageName,
        ) == true

    /**
     * Return true when the Core policy receiver is an active Device Admin.
     *
     * A Device Owner also owns an active policy receiver, but callers should
     * continue using isDeviceOwner() when they specifically require hardened
     * Device Owner authority.
     */
    fun isAdminActive(): Boolean =
        dpm?.isAdminActive(
            adminComponent,
        ) == true

    /**
     * Return true when the Core currently has policy authority sufficient for
     * the lock path.
     */
    fun canLock(): Boolean =
        isDeviceOwner() || isAdminActive()

    /**
     * Return true when the Core currently has policy authority sufficient for
     * Sentinel's wipe path.
     *
     * Standard-mode wipe also depends on Android's target-SDK behaviour and
     * the <wipe-data /> policy declared by this DeviceAdminReceiver.
     */
    fun canWipe(): Boolean =
        isDeviceOwner() || isAdminActive()

    /**
     * Lock the device using the Core's current device-policy authority.
     *
     * Device Owner and active Device Admin are both accepted. Android performs
     * the final policy check when lockNow() is called.
     */
    fun lockNow(): Boolean {
        val manager =
            dpm ?: return false

        if (!canLock()) {
            return false
        }

        manager.lockNow()

        return true
    }

    /**
     * Reboot the device using Device Owner authority.
     *
     * Reboot intentionally has no Standard-mode Device Admin fallback.
     */
    fun rebootNow(): Boolean {
        val manager =
            dpm ?: return false

        if (!isDeviceOwner()) {
            return false
        }

        manager.reboot(
            adminComponent,
        )

        return true
    }

    /**
     * Request deliberate removal of Standard-mode Device Admin authority.
     *
     * This operation is intentionally unavailable in Hardened / Device Owner
     * mode. The caller is responsible for authenticating the management
     * session before reaching this helper.
     *
     * A short-lived one-use authorization is persisted before
     * removeActiveAdmin() is called. DeviceAdminReceiver consumes that
     * authorization only if Android delivers onDisableRequested() for this
     * deliberate removal. Unexpected Settings-based removal therefore retains
     * the emergency-wipe behaviour.
     */
    fun removeStandardAdminSafely():
        StandardAdminRemovalResult {
        val manager =
            dpm
                ?: return StandardAdminRemovalResult.FAILED

        if (isDeviceOwner()) {
            return StandardAdminRemovalResult.DEVICE_OWNER
        }

        if (
            !manager.isAdminActive(
                adminComponent,
            )
        ) {
            return StandardAdminRemovalResult.ADMIN_INACTIVE
        }

        val authorization =
            SafeAdminRemovalStore(
                appContext,
            )

        if (!authorization.authorize()) {
            SecLog.e(
                TAG,
                "STANDARD_ADMIN_SAFE_REMOVAL authorization_failed=true",
            )

            return StandardAdminRemovalResult.AUTHORIZATION_FAILED
        }

        SecLog.w(
            TAG,
            "STANDARD_ADMIN_SAFE_REMOVAL requested=true",
        )

        return try {
            manager.removeActiveAdmin(
                adminComponent,
            )

            SecLog.w(
                TAG,
                "STANDARD_ADMIN_SAFE_REMOVAL removeActiveAdmin_returned=true",
            )

            StandardAdminRemovalResult.REQUESTED
        } catch (
            throwable: Throwable,
        ) {
            authorization.clear()

            SecLog.e(
                TAG,
                "STANDARD_ADMIN_SAFE_REMOVAL failed=true",
                throwable,
            )

            StandardAdminRemovalResult.FAILED
        }
    }

    /**
     * Defensive migration for Beta builds.
     *
     * Sentinel no longer exposes any feature that configures Android's native
     * failed-password wipe threshold. Clear any value that may have been left
     * behind by an earlier development build so Android cannot perform a wipe
     * independently of Sentinel's Beta lifecycle gate.
     */
    fun clearNativeFailedPasswordWipePolicy():
        Boolean {
        val manager =
            dpm
                ?: return false

        if (
            !isDeviceOwner() &&
            !isAdminActive()
        ) {
            return true
        }

        return try {
            manager
                .setMaximumFailedPasswordsForWipe(
                    adminComponent,
                    0,
                )

            SecLog.d(
                TAG,
                "NATIVE_FAILED_PASSWORD_WIPE_POLICY cleared=true",
            )

            true
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "NATIVE_FAILED_PASSWORD_WIPE_POLICY clear_failed=true",
                throwable,
            )

            false
        }
    }

    /**
     * Perform Sentinel's destructive wipe operation.
     *
     * Hardened mode preserves the existing WIPE_SILENTLY behaviour.
     *
     * Standard Device Admin mode deliberately passes flags == 0. Android's
     * legacy Device Admin wipe contract does not permit Sentinel to assume
     * Device Owner-only wipe options are available.
     */
    fun wipeData():
        WipeExecutionResult {
        val lifecycle =
            BetaLifecycleGuard(
                appContext,
            ).snapshot()

        if (lifecycle.destructiveSuppressed) {
            SecLog.e(
                TAG,
                "DESTRUCTIVE_WIPE_SUPPRESSED " +
                    "reason=${lifecycle.reason} " +
                    "expired=${lifecycle.currentBuildExpired} " +
                    "rearmRequired=${lifecycle.rearmRequired} " +
                    "stateHealthy=${lifecycle.stateHealthy}",
            )

            return WipeExecutionResult
                .BETA_SUPPRESSED
        }

        val manager =
            dpm
                ?: throw IllegalStateException(
                    "DevicePolicyManager unavailable",
                )

        if (!canWipe()) {
            throw SecurityException(
                "Core has no active device-policy wipe authority",
            )
        }

        if (
            isDeviceOwner() &&
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            SecLog.w(
                TAG,
                "WHOLE_DEVICE_WIPE api=wipeDevice",
            )

            manager.wipeDevice(
                DevicePolicyManager.WIPE_SILENTLY,
            )
        } else {
            val flags =
                if (
                    isDeviceOwner() &&
                    Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                ) {
                    DevicePolicyManager.WIPE_SILENTLY
                } else {
                    0
                }

            SecLog.w(
                TAG,
                "WHOLE_DEVICE_WIPE api=wipeData flags=$flags",
            )

            manager.wipeData(
                flags,
            )
        }

        return WipeExecutionResult.CALLED
    }

    enum class WipeExecutionResult {
        CALLED,
        BETA_SUPPRESSED,
    }

    enum class StandardAdminRemovalResult {
        REQUESTED,
        DEVICE_OWNER,
        ADMIN_INACTIVE,
        AUTHORIZATION_FAILED,
        FAILED,
    }

    private companion object {
        private const val TAG =
            "DeviceAdminManager"
    }
}
