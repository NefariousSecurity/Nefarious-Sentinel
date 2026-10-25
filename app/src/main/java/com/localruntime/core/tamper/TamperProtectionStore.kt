package com.localruntime.core.tamper

import android.content.Context

/**
 * Authoritative persistent debugging-tamper configuration.
 *
 * Both protections deliberately default to OFF.
 */
class TamperProtectionStore(
    context: Context,
) {

    private val deviceProtectedContext =
        context
            .applicationContext
            .createDeviceProtectedStorageContext()

    private val preferences =
        deviceProtectedContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun isUsbDebuggingProtectionEnabled(): Boolean =
        preferences.getBoolean(
            KEY_USB_DEBUGGING_PROTECTION_ENABLED,
            false,
        )

    fun isWirelessDebuggingProtectionEnabled(): Boolean =
        preferences.getBoolean(
            KEY_WIRELESS_DEBUGGING_PROTECTION_ENABLED,
            false,
        )

    fun setUsbDebuggingProtectionEnabled(
        enabled: Boolean,
    ): Boolean =
        preferences
            .edit()
            .putBoolean(
                KEY_USB_DEBUGGING_PROTECTION_ENABLED,
                enabled,
            )
            .commit()

    fun setWirelessDebuggingProtectionEnabled(
        enabled: Boolean,
    ): Boolean =
        preferences
            .edit()
            .putBoolean(
                KEY_WIRELESS_DEBUGGING_PROTECTION_ENABLED,
                enabled,
            )
            .commit()

    companion object {

        private const val PREFERENCES_NAME =
            "runtime_policy_tamper"

        private const val KEY_USB_DEBUGGING_PROTECTION_ENABLED =
            "tamper.usb_debugging_protection_enabled"

        private const val KEY_WIRELESS_DEBUGGING_PROTECTION_ENABLED =
            "tamper.wireless_debugging_protection_enabled"
    }
}
