package com.localruntime.core.tamper

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.localruntime.core.admin.DeviceAdminManager
import com.localruntime.core.admin.DeviceAdminReceiver
import com.localruntime.core.log.SecLog
import com.localruntime.core.usb.UsbMaintenanceStore

/**
 * Monitors Android debugging state for protected tamper events.
 *
 * Historical class name is retained because the foreground USB monitor
 * service already owns this guard.
 *
 * Enforcement is explicitly opt-in:
 *
 * - USB Debugging Protection defaults OFF.
 * - Wireless Debugging Protection defaults OFF.
 *
 * Administrative authority alone never arms either protection.
 */
class DebuggingTamperGuard(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val resolver =
        appContext.contentResolver

    private val protectionStore =
        TamperProtectionStore(
            appContext,
        )

    private val maintenanceStore =
        UsbMaintenanceStore(
            appContext,
        )

    private val handler =
        Handler(
            Looper.getMainLooper(),
        )

    private var registered =
        false

    private var wipeTriggered =
        false

    private val wirelessObserver =
        object : ContentObserver(
            handler,
        ) {
            override fun onChange(
                selfChange: Boolean,
            ) {
                checkNow(
                    reason =
                        "WIRELESS_SETTING_CHANGED",
                )
            }
        }

    private val usbObserver =
        object : ContentObserver(
            handler,
        ) {
            override fun onChange(
                selfChange: Boolean,
            ) {
                checkNow(
                    reason =
                        "USB_SETTING_CHANGED",
                )
            }
        }

    /**
     * Register both debugging observers and immediately inspect current state.
     */
    fun start() {
        if (registered) {
            checkNow(
                reason =
                    "MONITOR_ALREADY_REGISTERED",
            )
            return
        }

        try {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(
                    WIRELESS_ADB_SETTING,
                ),
                false,
                wirelessObserver,
            )

            resolver.registerContentObserver(
                Settings.Global.getUriFor(
                    Settings.Global.ADB_ENABLED,
                ),
                false,
                usbObserver,
            )

            registered =
                true

            SecLog.d(
                TAG,
                "DEBUGGING_TAMPER_MONITOR_REGISTERED",
            )
        } catch (
            throwable: Throwable,
        ) {
            runCatching {
                resolver.unregisterContentObserver(
                    wirelessObserver,
                )
            }

            runCatching {
                resolver.unregisterContentObserver(
                    usbObserver,
                )
            }

            registered =
                false

            SecLog.e(
                TAG,
                "DEBUGGING_TAMPER_MONITOR_REGISTER_FAILED",
                throwable,
            )

            return
        }

        checkNow(
            reason =
                "INITIAL_CHECK",
        )
    }

    /**
     * Re-read both debugging settings and enforce only explicitly armed policy.
     */
    fun checkNow(
        reason: String,
    ) {
        if (wipeTriggered) {
            return
        }

        /*
         * USB Maintenance Mode is the single authorised temporary bypass for:
         *
         * - physical USB Protection;
         * - USB Debugging tamper reaction;
         * - Wireless Debugging tamper reaction.
         *
         * The user's persistent tamper switches are never changed here.
         * Maintenance suppresses enforcement only for the bounded authoritative
         * window stored in Device Protected Storage.
         *
         * Failure to read Maintenance state is fail-closed: enforcement
         * continues rather than assuming Maintenance is active.
         */
        val maintenanceState =
            try {
                maintenanceStore.load()
            } catch (
                throwable: Throwable,
            ) {
                SecLog.e(
                    TAG,
                    "DEBUGGING_TAMPER_MAINTENANCE_READ_FAILED reason=$reason",
                    throwable,
                )

                null
            }

        if (maintenanceState?.active == true) {
            SecLog.d(
                TAG,
                "DEBUGGING_TAMPER_SUPPRESSED " +
                    "reason=USB_MAINTENANCE_ACTIVE " +
                    "remainingMs=${maintenanceState.remainingMs} " +
                    "checkReason=$reason",
            )

            return
        }

        val wirelessEnabled =
            try {
                Settings.Global.getInt(
                    resolver,
                    WIRELESS_ADB_SETTING,
                    0,
                ) == 1
            } catch (
                throwable: Throwable,
            ) {
                SecLog.e(
                    TAG,
                    "WIRELESS_ADB_STATE_READ_FAILED reason=$reason",
                    throwable,
                )

                return
            }

        val usbEnabled =
            try {
                Settings.Global.getInt(
                    resolver,
                    Settings.Global.ADB_ENABLED,
                    0,
                ) == 1
            } catch (
                throwable: Throwable,
            ) {
                SecLog.e(
                    TAG,
                    "USB_ADB_STATE_READ_FAILED reason=$reason",
                    throwable,
                )

                return
            }

        val wirelessProtectionEnabled =
            protectionStore
                .isWirelessDebuggingProtectionEnabled()

        val usbProtectionEnabled =
            protectionStore
                .isUsbDebuggingProtectionEnabled()

        val authority =
            hasWipeAuthority()

        SecLog.d(
            TAG,
            "DEBUGGING_TAMPER_STATE " +
                "usbEnabled=$usbEnabled " +
                "usbProtection=$usbProtectionEnabled " +
                "wirelessEnabled=$wirelessEnabled " +
                "wirelessProtection=$wirelessProtectionEnabled " +
                "authority=$authority " +
                "reason=$reason",
        )

        val usbTriggered =
            usbProtectionEnabled &&
                usbEnabled

        val wirelessTriggered =
            wirelessProtectionEnabled &&
                wirelessEnabled

        if (
            !authority ||
            (
                !usbTriggered &&
                    !wirelessTriggered
            )
        ) {
            return
        }

        wipeTriggered =
            true

        val triggerName =
            when {
                usbTriggered &&
                    wirelessTriggered ->
                    "USB_AND_WIRELESS_ADB_TAMPER_TRIGGER"

                usbTriggered ->
                    "USB_ADB_TAMPER_TRIGGER"

                else ->
                    "WIRELESS_ADB_TAMPER_TRIGGER"
            }

        SecLog.e(
            TAG,
            "$triggerName " +
                "usbEnabled=$usbEnabled " +
                "wirelessEnabled=$wirelessEnabled " +
                "reason=$reason " +
                "action=EMERGENCY_WIPE",
        )

        try {
            when (
                DeviceAdminManager(
                    appContext,
                ).wipeData()
            ) {
                DeviceAdminManager
                    .WipeExecutionResult
                    .CALLED -> {
                    SecLog.e(
                        TAG,
                        "DEBUGGING_TAMPER_WIPE_RETURNED",
                    )
                }

                DeviceAdminManager
                    .WipeExecutionResult
                    .BETA_SUPPRESSED -> {
                    wipeTriggered =
                        false

                    SecLog.e(
                        TAG,
                        "DEBUGGING_TAMPER_WIPE_SUPPRESSED " +
                            "reason=BETA_LIFECYCLE",
                    )
                }
            }
        } catch (
            throwable: Throwable,
        ) {
            wipeTriggered =
                false

            SecLog.e(
                TAG,
                "DEBUGGING_TAMPER_WIPE_FAILED",
                throwable,
            )
        }
    }

    fun stop() {
        if (!registered) {
            return
        }

        runCatching {
            resolver.unregisterContentObserver(
                wirelessObserver,
            )
        }.onFailure {
            SecLog.e(
                TAG,
                "WIRELESS_ADB_MONITOR_UNREGISTER_FAILED",
                it,
            )
        }

        runCatching {
            resolver.unregisterContentObserver(
                usbObserver,
            )
        }.onFailure {
            SecLog.e(
                TAG,
                "USB_ADB_MONITOR_UNREGISTER_FAILED",
                it,
            )
        }

        registered =
            false
    }

    private fun hasWipeAuthority(): Boolean {
        val dpm =
            appContext.getSystemService(
                DevicePolicyManager::class.java,
            ) ?: return false

        if (
            dpm.isDeviceOwnerApp(
                appContext.packageName,
            )
        ) {
            return true
        }

        return dpm.isAdminActive(
            DeviceAdminReceiver.getComponentName(
                appContext,
            ),
        )
    }

    companion object {

        private const val TAG =
            "DebuggingTamper"

        private const val WIRELESS_ADB_SETTING =
            "adb_wifi_enabled"
    }
}
