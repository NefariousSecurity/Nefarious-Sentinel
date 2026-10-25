package com.localruntime.core.management

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.UserManager
import com.localruntime.core.admin.HardenedPolicyManager
import com.localruntime.core.tamper.DebuggingTamperGuard
import com.localruntime.core.usb.UsbDataProbe
import com.localruntime.core.usb.UsbDataProbeService
import com.localruntime.core.usb.UsbMaintenanceAlarmScheduler
import com.localruntime.core.usb.UsbMaintenanceStore
import com.localruntime.core.usb.UsbProtectionStore
import com.localruntime.core.wipe.WipeGateway
import com.runtimebridge.api.ManagementProtocol

/**
 * Core-owned USB configuration interface used by the temporary Controller.
 *
 * This class deliberately uses the same authoritative Device Protected Storage,
 * USB classifier and exact-alarm scheduler as the existing accepted USB UI.
 *
 * It does not perform destructive USB classification itself. UsbDataProbe
 * remains solely responsible for detection and wipe enforcement.
 */
class UsbManagementManager(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val protectionStore =
        UsbProtectionStore(
            appContext,
        )

    private val maintenanceStore =
        UsbMaintenanceStore(
            appContext,
        )

    private val wipeGateway =
        WipeGateway(
            appContext,
        )

    fun isProtectionEnabled(): Boolean =
        protectionStore
            .load()
            .enabled

    fun isStrictModeEnabled(): Boolean =
        protectionStore
            .load()
            .strictMode

    fun getConnectionState(): Int =
        when (
            UsbDataProbe
                .connectionState
                .value
        ) {
            UsbDataProbe.ConnectionState.DISCONNECTED ->
                ManagementProtocol
                    .USB_CONNECTION_DISCONNECTED

            UsbDataProbe.ConnectionState.CONNECTED_PENDING ->
                ManagementProtocol
                    .USB_CONNECTION_PENDING

            UsbDataProbe.ConnectionState.CHARGE_ONLY ->
                ManagementProtocol
                    .USB_CONNECTION_CHARGE_ONLY

            UsbDataProbe.ConnectionState.DATA_CAPABLE ->
                ManagementProtocol
                    .USB_CONNECTION_DATA_CAPABLE
        }

    fun isMaintenanceActive(): Boolean =
        maintenanceStore
            .load()
            .active

    fun getMaintenanceRemainingMs(): Long =
        maintenanceStore
            .load()
            .remainingMs

    fun getMaintenanceExtendsUsed(): Int =
        maintenanceStore
            .load()
            .extendsUsed

    fun getMaintenanceMaxExtends(): Int =
        UsbMaintenanceStore.MAX_EXTENDS

    /**
     * Persistently enable or disable USB Protection.
     *
     * Enabling is fail-safe at the policy transition boundary:
     *
     * - active wipe authority is required;
     * - a currently proven USB data-host candidate prevents arming;
     * - an unresolved wired classification prevents arming until it settles;
     * - charge-only and wireless charging remain permitted;
     * - disabling clears Strict Mode through UsbProtectionStore;
     * - disabling also terminates Maintenance Mode.
     *
     * The connection guard runs before the authoritative policy store is
     * modified. This prevents an already-connected data host from becoming
     * destructive merely because the user arms USB Protection.
     */
    fun setProtectionEnabled(
        enabled: Boolean,
    ): Int {
        return try {
            if (
                enabled &&
                !wipeGateway.hasWipeAuthority()
            ) {
                return ManagementProtocol
                    .USB_RESULT_ADMIN_INACTIVE
            }

            if (
                enabled &&
                isUsbDataHostPresentOrPending()
            ) {
                return ManagementProtocol
                    .USB_RESULT_WIRED_CONNECTION
            }

            protectionStore.setEnabled(
                enabled,
            )

            if (!enabled) {
                cancelMaintenanceInternal()
            }

            ManagementProtocol
                .USB_RESULT_OK
        } catch (
            ignored: Throwable,
        ) {
            ManagementProtocol
                .USB_RESULT_FAILED
        }
    }

    /**
     * Persistently enable or disable Strict USB Mode.
     *
     * The wired-connection guard mirrors the existing accepted configuration
     * path. Wireless charging is deliberately permitted.
     */
    fun setStrictModeEnabled(
        enabled: Boolean,
    ): Int {
        return try {
            if (!enabled) {
                protectionStore.setStrictMode(
                    false,
                )

                return ManagementProtocol
                    .USB_RESULT_OK
            }

            val protectionState =
                protectionStore.load()

            if (!protectionState.enabled) {
                return ManagementProtocol
                    .USB_RESULT_PROTECTION_DISABLED
            }

            if (!wipeGateway.hasWipeAuthority()) {
                return ManagementProtocol
                    .USB_RESULT_ADMIN_INACTIVE
            }

            if (isWiredConnectionPresent()) {
                return ManagementProtocol
                    .USB_RESULT_WIRED_CONNECTION
            }

            protectionStore.setStrictMode(
                true,
            )

            ManagementProtocol
                .USB_RESULT_OK
        } catch (
            ignored: Throwable,
        ) {
            ManagementProtocol
                .USB_RESULT_FAILED
        }
    }

    /**
     * Start the standard 30-minute Maintenance Mode window.
     *
     * The authoritative state is persisted first and the exact alarm is then
     * armed for precisely that stored monotonic deadline.
     *
     * Failure to guarantee expiry is fail-closed: Maintenance Mode is removed.
     */
    fun startMaintenance(): Int {
        return try {
            val protectionState =
                protectionStore.load()

            if (!protectionState.enabled) {
                cancelMaintenanceInternal()

                return ManagementProtocol
                    .USB_RESULT_PROTECTION_DISABLED
            }

            if (!wipeGateway.hasWipeAuthority()) {
                cancelMaintenanceInternal()

                return ManagementProtocol
                    .USB_RESULT_ADMIN_INACTIVE
            }

            if (!isUserUnlocked()) {
                cancelMaintenanceInternal()

                return ManagementProtocol
                    .USB_RESULT_USER_LOCKED
            }

            if (
                !UsbMaintenanceAlarmScheduler
                    .canScheduleExactAlarms(
                        appContext,
                    )
            ) {
                cancelMaintenanceInternal()

                return ManagementProtocol
                    .USB_RESULT_EXACT_ALARM_UNAVAILABLE
            }

            val state =
                maintenanceStore.start()

            /*
             * Maintenance is now authoritative. Clear the Device Owner
             * debugging restriction before the user performs legitimate ADB
             * maintenance.
             */
            if (
                !HardenedPolicyManager(
                    appContext,
                ).applyDebuggingRestrictionPolicy()
            ) {
                cancelMaintenanceInternal()

                return ManagementProtocol
                    .USB_RESULT_FAILED
            }

            try {
                UsbMaintenanceAlarmScheduler.arm(
                    context = appContext,
                    expiresAtElapsedRealtimeMs =
                        state
                            .expiresAtElapsedRealtimeMs,
                )
            } catch (
                throwable: Throwable,
            ) {
                cancelMaintenanceInternal()

                return ManagementProtocol
                    .USB_RESULT_FAILED
            }

            ManagementProtocol
                .USB_RESULT_OK
        } catch (
            ignored: Throwable,
        ) {
            cancelMaintenanceInternal()

            ManagementProtocol
                .USB_RESULT_FAILED
        }
    }

    /**
     * Extend active Maintenance Mode by another 30 minutes.
     *
     * The existing hard limit of three extensions remains authoritative.
     */
    fun extendMaintenance(): Int {
        return try {
            val protectionState =
                protectionStore.load()

            if (!protectionState.enabled) {
                cancelMaintenanceInternal()

                return ManagementProtocol
                    .USB_RESULT_PROTECTION_DISABLED
            }

            if (!wipeGateway.hasWipeAuthority()) {
                cancelMaintenanceInternal()

                return ManagementProtocol
                    .USB_RESULT_ADMIN_INACTIVE
            }

            if (!isUserUnlocked()) {
                cancelMaintenanceInternal()

                return ManagementProtocol
                    .USB_RESULT_USER_LOCKED
            }

            if (
                !UsbMaintenanceAlarmScheduler
                    .canScheduleExactAlarms(
                        appContext,
                    )
            ) {
                cancelMaintenanceInternal()

                return ManagementProtocol
                    .USB_RESULT_EXACT_ALARM_UNAVAILABLE
            }

            val current =
                maintenanceStore.load()

            if (!current.active) {
                return ManagementProtocol
                    .USB_RESULT_MAINTENANCE_INACTIVE
            }

            if (
                current.extendsUsed >=
                UsbMaintenanceStore.MAX_EXTENDS
            ) {
                return ManagementProtocol
                    .USB_RESULT_EXTENSION_LIMIT
            }

            val extended =
                maintenanceStore.extend()

            if (
                extended
                    .expiresAtElapsedRealtimeMs !=
                current
                    .expiresAtElapsedRealtimeMs
            ) {
                try {
                    UsbMaintenanceAlarmScheduler.arm(
                        context = appContext,
                        expiresAtElapsedRealtimeMs =
                            extended
                                .expiresAtElapsedRealtimeMs,
                    )
                } catch (
                    throwable: Throwable,
                ) {
                    cancelMaintenanceInternal()

                    return ManagementProtocol
                        .USB_RESULT_FAILED
                }
            }

            ManagementProtocol
                .USB_RESULT_OK
        } catch (
            ignored: Throwable,
        ) {
            cancelMaintenanceInternal()

            ManagementProtocol
                .USB_RESULT_FAILED
        }
    }

    /**
     * End Maintenance Mode immediately.
     *
     * Authoritative suppression state is cleared before the stale exact alarm
     * is cancelled.
     */
    fun cancelMaintenance(): Int {
        return try {
            cancelMaintenanceInternal()

            ManagementProtocol
                .USB_RESULT_OK
        } catch (
            ignored: Throwable,
        ) {
            /*
             * If only alarm cancellation failed, authoritative Maintenance
             * state has already been cleared and suppression cannot continue.
             */
            ManagementProtocol
                .USB_RESULT_FAILED
        }
    }

    /**
     * Return true when normal USB Protection must not be armed because a
     * hardware-proven USB data-host candidate is already present, or because
     * the current wired connection has not yet been classified safely.
     *
     * BATTERY_PLUGGED_USB is the same trigger used by UsbDataProbe for normal
     * USB Protection. Charge-only AC and wireless charging are deliberately
     * permitted.
     *
     * If the sticky battery query is unavailable, fall back to UsbDataProbe's
     * published connection state. CONNECTED_PENDING is treated as unsafe to arm
     * until the detector can classify it.
     */
    private fun isUsbDataHostPresentOrPending(): Boolean {
        val batteryIntent =
            try {
                appContext.registerReceiver(
                    null,
                    IntentFilter(
                        Intent.ACTION_BATTERY_CHANGED,
                    ),
                )
            } catch (
                ignored: Throwable,
            ) {
                null
            }

        if (batteryIntent != null) {
            val plugged =
                batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_PLUGGED,
                    -1,
                )

            if (plugged >= 0) {
                return (
                    plugged ==
                        BatteryManager.BATTERY_PLUGGED_USB
                    )
            }
        }

        return when (
            UsbDataProbe
                .connectionState
                .value
        ) {
            UsbDataProbe.ConnectionState.DATA_CAPABLE,
            UsbDataProbe.ConnectionState.CONNECTED_PENDING ->
                true

            UsbDataProbe.ConnectionState.CHARGE_ONLY,
            UsbDataProbe.ConnectionState.DISCONNECTED ->
                false
        }
    }

    /**
     * Return true when an external wired power connection is present.
     *
     * This mirrors the hardware-tested Strict Mode configuration guard.
     */
    private fun isWiredConnectionPresent(): Boolean {
        val batteryIntent =
            try {
                appContext.registerReceiver(
                    null,
                    IntentFilter(
                        Intent.ACTION_BATTERY_CHANGED,
                    ),
                )
            } catch (
                ignored: Throwable,
            ) {
                null
            }

        if (batteryIntent != null) {
            val plugged =
                batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_PLUGGED,
                    -1,
                )

            if (plugged >= 0) {
                return (
                    plugged != 0 &&
                        plugged !=
                        BatteryManager
                            .BATTERY_PLUGGED_WIRELESS
                    )
            }
        }

        return (
            UsbDataProbe
                .connectionState
                .value !=
                UsbDataProbe.ConnectionState.DISCONNECTED
            )
    }

    private fun isUserUnlocked(): Boolean {
        val userManager =
            appContext.getSystemService(
                UserManager::class.java,
            )

        return userManager
            ?.isUserUnlocked
            ?: false
    }

    /**
     * Clear authoritative Maintenance state first, then best-effort cancel the
     * stale exact alarm.
     *
     * Once Maintenance is inactive, restart the persistent transport monitor
     * so USB / Wireless Debugging are re-evaluated immediately against the
     * user's unchanged Tamper Proofing configuration.
     *
     * Alarm cancellation failure cannot prolong suppression because the
     * authoritative Maintenance state has already been cleared.
     */
    private fun cancelMaintenanceInternal() {
        /*
         * Clear the authoritative suppression state first.
         */
        maintenanceStore.cancel()

        /*
         * Preserve the existing destructive boundary:
         *
         * if USB or Wireless debugging was left enabled during Maintenance,
         * check it synchronously BEFORE the Device Owner restriction can hide
         * that state by disabling debugging.
         */
        DebuggingTamperGuard(
            appContext,
        ).checkNow(
            reason =
                "MAINTENANCE_ENDED",
        )

        /*
         * Maintenance no longer suppresses physical USB enforcement.
         *
         * Re-evaluate any candidate already latched while Maintenance was
         * active. Without this explicit transition check, duplicate USB
         * broadcasts remain suppressed until physical disconnect and an
         * already-connected destructive host can survive Maintenance ending.
         */
        UsbDataProbe.reevaluateActiveCandidate(
            context = appContext,
            reason = "MAINTENANCE_ENDED",
        )

        /*
         * Both tamper protections armed + Maintenance inactive must now restore
         * DISALLOW_DEBUGGING_FEATURES. Failure is security-significant and is
         * allowed to propagate to the caller's existing failure handling.
         */
        check(
            HardenedPolicyManager(
                appContext,
            ).applyDebuggingRestrictionPolicy()
        ) {
            "Failed to restore Hardened debugging restriction"
        }

        runCatching {
            UsbMaintenanceAlarmScheduler.cancel(
                appContext,
            )
        }

        runCatching {
            UsbDataProbeService.start(
                appContext,
            )
        }
    }
}
