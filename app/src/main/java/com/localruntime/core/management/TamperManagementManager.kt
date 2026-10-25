package com.localruntime.core.management

import android.content.Context
import android.provider.Settings
import com.localruntime.core.admin.HardenedPolicyManager
import com.localruntime.core.tamper.TamperProtectionStore
import com.runtimebridge.api.ManagementProtocol

/**
 * Authenticated management facade for debugging tamper protections.
 *
 * The authoritative configuration lives in the persistent Core's
 * Device Protected Storage.
 *
 * Both policies default to OFF.
 *
 * A protection cannot be armed while the corresponding debugging interface
 * is already enabled. This prevents an administrative configuration action
 * from itself becoming a destructive tamper event.
 *
 * Once armed, however, subsequently enabling the protected debugging
 * interface is handled by DebuggingTamperGuard as an emergency tamper event.
 */
class TamperManagementManager(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val resolver =
        appContext.contentResolver

    private val store =
        TamperProtectionStore(
            appContext,
        )

    fun isUsbDebuggingProtectionEnabled(): Boolean =
        store.isUsbDebuggingProtectionEnabled()

    fun isWirelessDebuggingProtectionEnabled(): Boolean =
        store.isWirelessDebuggingProtectionEnabled()

    fun setUsbDebuggingProtectionEnabled(
        enabled: Boolean,
    ): Int {
        if (
            enabled &&
            isUsbDebuggingEnabled()
        ) {
            return ManagementProtocol
                .TAMPER_RESULT_USB_DEBUGGING_ACTIVE
        }

        val previous =
            store.isUsbDebuggingProtectionEnabled()

        if (
            !store.setUsbDebuggingProtectionEnabled(
                enabled,
            )
        ) {
            return ManagementProtocol
                .TAMPER_RESULT_FAILED
        }

        if (
            !HardenedPolicyManager(
                appContext,
            ).applyDebuggingRestrictionPolicy()
        ) {
            /*
             * Keep persisted UI state and effective Device Owner policy
             * transactional. Restore the previous switch and then best-effort
             * restore the previous restriction state.
             */
            store.setUsbDebuggingProtectionEnabled(
                previous,
            )

            runCatching {
                HardenedPolicyManager(
                    appContext,
                ).applyDebuggingRestrictionPolicy()
            }

            return ManagementProtocol
                .TAMPER_RESULT_FAILED
        }

        return ManagementProtocol
            .TAMPER_RESULT_OK
    }

    fun setWirelessDebuggingProtectionEnabled(
        enabled: Boolean,
    ): Int {
        if (
            enabled &&
            isWirelessDebuggingEnabled()
        ) {
            return ManagementProtocol
                .TAMPER_RESULT_WIRELESS_DEBUGGING_ACTIVE
        }

        val previous =
            store.isWirelessDebuggingProtectionEnabled()

        if (
            !store.setWirelessDebuggingProtectionEnabled(
                enabled,
            )
        ) {
            return ManagementProtocol
                .TAMPER_RESULT_FAILED
        }

        if (
            !HardenedPolicyManager(
                appContext,
            ).applyDebuggingRestrictionPolicy()
        ) {
            store.setWirelessDebuggingProtectionEnabled(
                previous,
            )

            runCatching {
                HardenedPolicyManager(
                    appContext,
                ).applyDebuggingRestrictionPolicy()
            }

            return ManagementProtocol
                .TAMPER_RESULT_FAILED
        }

        return ManagementProtocol
            .TAMPER_RESULT_OK
    }

    private fun isUsbDebuggingEnabled(): Boolean =
        try {
            Settings.Global.getInt(
                resolver,
                Settings.Global.ADB_ENABLED,
                0,
            ) == 1
        } catch (
            throwable: Throwable,
        ) {
            /*
             * Fail closed for administrative arming.
             *
             * If Sentinel cannot reliably determine whether debugging is
             * currently active, do not permit a new destructive policy to
             * become armed.
             */
            true
        }

    private fun isWirelessDebuggingEnabled(): Boolean =
        try {
            Settings.Global.getInt(
                resolver,
                WIRELESS_ADB_SETTING,
                0,
            ) == 1
        } catch (
            throwable: Throwable,
        ) {
            true
        }

    companion object {

        private const val WIRELESS_ADB_SETTING =
            "adb_wifi_enabled"
    }
}
