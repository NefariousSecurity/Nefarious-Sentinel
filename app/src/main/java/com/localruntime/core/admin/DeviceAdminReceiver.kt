package com.localruntime.core.admin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.localruntime.core.log.SecLog
import com.localruntime.core.usb.UsbDataProbeService

/**
 * Device-policy receiver for the persistent local Core.
 *
 * The same receiver supports both authority modes:
 *
 * - Hardened mode: the Core package is Device Owner.
 * - Standard mode: the user explicitly activates this receiver as Device Admin.
 *
 * Standard mode treats an unexpected Device Admin disable request from Android
 * Settings as a tamper event. The wipe request is made from
 * onDisableRequested(), while Device Admin authority is still active.
 *
 * Hardened mode does not use this admin-disable tamper path because Device
 * Owner already provides stronger removal protection.
 *
 * Device Admin activation also starts the persistent transport-security
 * monitor. That monitor checks Wireless Debugging immediately and continues
 * observing it for later activation.
 */
class DeviceAdminReceiver :
    android.app.admin.DeviceAdminReceiver() {

    /**
     * Start the persistent transport-security monitor as soon as Android grants
     * administration.
     *
     * If Wireless Debugging is already enabled, the service's immediate state
     * check treats that condition as a tamper event and invokes the configured
     * wipe path.
     */
    override fun onEnabled(
        context: Context,
        intent: Intent,
    ) {
        super.onEnabled(
            context,
            intent,
        )

        SafeAdminRemovalStore(
            context,
        ).clear()

        HardenedPolicyManager(
            context,
        ).applyRequiredPolicies()

        SecLog.d(
            TAG,
            "ADMIN_ENABLED starting_transport_monitor=true",
        )

        try {
            UsbDataProbeService.start(
                context.applicationContext,
            )
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "ADMIN_ENABLED transport_monitor_start_failed",
                throwable,
            )
        }
    }

    /**
     * Android calls this before ordinary Device Admin authority is removed
     * through the system Device Admin interface.
     *
     * In Standard mode this is the last reliable point at which the Core still
     * holds the wipe-data Device Admin policy, so an unexpected removal request
     * is treated as a tamper event.
     *
     * Device Owner installations deliberately do not use this path.
     */
    override fun onDisableRequested(
        context: Context,
        intent: Intent,
    ): CharSequence {
        val admin =
            DeviceAdminManager(
                context,
            )

        if (admin.isDeviceOwner()) {
            SecLog.w(
                TAG,
                "ADMIN_DISABLE_REQUESTED authority=DEVICE_OWNER action=NONE",
            )

            return DEVICE_OWNER_MESSAGE
        }

        val safeRemoval =
            SafeAdminRemovalStore(
                context,
            )

        if (safeRemoval.consumeIfAuthorized()) {
            SecLog.w(
                TAG,
                "STANDARD_ADMIN_DISABLE_REQUESTED action=SAFE_REMOVAL_AUTHORIZED",
            )

            return STANDARD_SAFE_REMOVAL_MESSAGE
        }

        SecLog.e(
            TAG,
            "STANDARD_ADMIN_DISABLE_REQUESTED action=EMERGENCY_WIPE",
        )

        try {
            when (
                admin.wipeData()
            ) {
                DeviceAdminManager
                    .WipeExecutionResult
                    .CALLED -> {
                    /*
                     * A successful wipe normally destroys device state before
                     * this method can meaningfully continue.
                     */
                    SecLog.e(
                        TAG,
                        "STANDARD_ADMIN_DISABLE_WIPE_RETURNED",
                    )
                }

                DeviceAdminManager
                    .WipeExecutionResult
                    .BETA_SUPPRESSED -> {
                    SecLog.e(
                        TAG,
                        "STANDARD_ADMIN_DISABLE_WIPE_SUPPRESSED " +
                            "reason=BETA_LIFECYCLE",
                    )
                }
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "STANDARD_ADMIN_DISABLE_WIPE_FAILED",
                throwable,
            )
        }

        return STANDARD_TAMPER_MESSAGE
    }

    /**
     * This callback should only become observable if Device Admin removal
     * actually completes. If the Standard-mode tamper wipe succeeds, the
     * device reset should occur instead.
     */
    override fun onDisabled(
        context: Context,
        intent: Intent,
    ) {
        SafeAdminRemovalStore(
            context,
        ).clear()

        SecLog.e(
            TAG,
            "ADMIN_DISABLED removal_completed=true",
        )

        super.onDisabled(
            context,
            intent,
        )
    }

    companion object {

        private const val TAG =
            "DeviceAdminReceiver"

        private const val STANDARD_TAMPER_MESSAGE =
            "Device administration is protected by the configured security policy."

        private const val STANDARD_SAFE_REMOVAL_MESSAGE =
            "Device administration removal was authorised by the management interface."

        private const val DEVICE_OWNER_MESSAGE =
            "Device administration is managed by the system."

        /**
         * Return the exact policy receiver component used by DevicePolicyManager.
         */
        fun getComponentName(
            context: Context,
        ): ComponentName =
            ComponentName(
                context,
                DeviceAdminReceiver::class.java,
            )
    }
}
