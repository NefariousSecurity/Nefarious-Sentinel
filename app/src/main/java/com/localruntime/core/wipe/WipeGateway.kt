package com.localruntime.core.wipe

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.localruntime.core.admin.DeviceAdminManager
import com.localruntime.core.admin.DeviceAdminReceiver
import com.localruntime.core.log.SecLog

/**
 * Central gateway for destructive wipe operations and related device-policy
 * state.
 *
 * Sentinel supports two authority levels:
 *
 * - Hardened mode: Core is Device Owner.
 * - Standard mode: Core is an active Device Admin.
 *
 * Destructive feature logic continues to route through this single gateway.
 */
class WipeGateway(
    context: Context,
) {
    private val appCtx =
        context.applicationContext

    private val admin =
        DeviceAdminManager(
            appCtx,
        )

    /**
     * Return true only when this exact Core package is Device Owner.
     */
    fun isDeviceOwner(): Boolean =
        admin.isDeviceOwner()

    /**
     * Return true when the Core policy receiver is currently active as a
     * Device Admin.
     */
    fun isAdminActive(): Boolean =
        admin.isAdminActive()

    /**
     * Return true when Sentinel currently has sufficient device-policy
     * authority to attempt its configured wipe path.
     */
    fun hasWipeAuthority(): Boolean =
        admin.canWipe()

    /**
     * Complete DeviceAdminReceiver component used by device-policy APIs.
     */
    fun adminComponentName(): ComponentName =
        ComponentName(
            appCtx,
            DeviceAdminReceiver::class.java,
        )

    /**
     * Execute Sentinel's destructive wipe path.
     *
     * Device Owner or active Device Admin authority is verified immediately
     * before the wipe request.
     */
    fun wipeNow(): WipeResult {
        val dpm =
            appCtx.getSystemService(
                DevicePolicyManager::class.java,
            )

        if (dpm == null) {
            SecLog.e(
                TAG,
                "DevicePolicyManager service is null",
            )

            return WipeResult.NoDpm
        }

        val component =
            adminComponentName()

        val deviceOwner =
            dpm.isDeviceOwnerApp(
                appCtx.packageName,
            )

        val activeAdmin =
            dpm.isAdminActive(
                component,
            )

        SecLog.d(
            TAG,
            "wipeNow() component=$component " +
                "deviceOwner=$deviceOwner activeAdmin=$activeAdmin",
        )

        if (!deviceOwner && !activeAdmin) {
            return WipeResult.NotAdmin(
                component.flattenToShortString(),
            )
        }

        return try {
            val authority =
                if (deviceOwner) {
                    "DEVICE_OWNER"
                } else {
                    "DEVICE_ADMIN"
                }

            SecLog.d(
                TAG,
                "Calling wipeData() NOW path=$authority",
            )

            when (
                admin.wipeData()
            ) {
                DeviceAdminManager
                    .WipeExecutionResult
                    .CALLED -> {
                    /*
                     * A successful wipe normally destroys this process/device
                     * state before this return can be meaningfully consumed.
                     */
                    SecLog.d(
                        TAG,
                        "wipeData() returned without throwing path=$authority",
                    )

                    WipeResult.Called
                }

                DeviceAdminManager
                    .WipeExecutionResult
                    .BETA_SUPPRESSED -> {
                    SecLog.e(
                        TAG,
                        "wipeData() suppressed by Beta lifecycle path=$authority",
                    )

                    WipeResult.BetaSuppressed
                }
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "wipeData() threw: " +
                    "${throwable.javaClass.simpleName}: " +
                    "${throwable.message}",
                throwable,
            )

            WipeResult.Error(
                throwable.javaClass.simpleName,
                throwable.message
                    ?: "(no message)",
            )
        }
    }

    sealed class WipeResult {
        object NoDpm : WipeResult()

        object Called : WipeResult()

        object BetaSuppressed : WipeResult()

        /**
         * Historical result name retained to avoid changing existing callers.
         *
         * It now means that the Core has neither Device Owner authority nor an
         * active Device Admin receiver.
         */
        data class NotAdmin(
            val expected: String,
        ) : WipeResult()

        data class Error(
            val type: String,
            val message: String,
        ) : WipeResult()
    }

    private companion object {
        private const val TAG =
            "WipeGateway"
    }
}
