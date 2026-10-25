package com.localruntime.core.usb

import android.content.Context
import android.content.SharedPreferences

/**
 * Direct-Boot-safe persistent policy for USB Protection.
 *
 * This store is deliberately separate from the legacy USB protection
 * configuration. The new USB data-host classifier remains running
 * independently; this store controls whether a qualifying USB event is
 * permitted to reach the destructive wipe path.
 *
 * Device Protected Storage is required because the policy must be readable
 * after LOCKED_BOOT_COMPLETED, before the user's first unlock after boot.
 *
 * USB Protection is fail-safe on initial installation:
 *
 *     enabled = false
 *
 * A destructive USB wipe must therefore be explicitly armed by the user.
 *
 * Future Backup / Maintenance Mode state will be implemented separately from
 * this persistent preference. Temporary maintenance must never overwrite the
 * user's normal USB Protection preference.
 */
class UsbProtectionStore(context: Context) {

    private val deviceContext: Context =
        context.applicationContext.createDeviceProtectedStorageContext()

    private val prefs: SharedPreferences =
        deviceContext.getSharedPreferences(
            FILE_NAME,
            Context.MODE_PRIVATE,
        )

    data class State(
        val enabled: Boolean,
        val strictMode: Boolean,
    )

    /**
     * Read the authoritative persistent USB Protection state.
     *
     * This operation is Direct-Boot safe.
     */
    @Synchronized
    fun load(): State =
        State(
            enabled = prefs.getBoolean(
                KEY_ENABLED,
                DEFAULT_ENABLED,
            ),
            strictMode = prefs.getBoolean(
                KEY_STRICT_MODE,
                DEFAULT_STRICT_MODE,
            ),
        )

    /**
     * Persistently enable or disable USB Protection.
     *
     * This changes only the policy governing whether a qualifying USB
     * data-host event may invoke the wipe path. It does not start or stop the
     * USB detector service.
     */
    @Synchronized
    fun setEnabled(enabled: Boolean): State {
        val editor =
            prefs.edit()
                .putBoolean(
                    KEY_ENABLED,
                    enabled,
                )

        /*
         * Strict Mode must never survive USB Protection being disarmed.
         *
         * This guarantees that re-enabling USB Protection cannot silently
         * restore the more destructive "any wired connection" policy without
         * the user explicitly confirming Strict Mode again.
         */
        if (!enabled) {
            editor.putBoolean(
                KEY_STRICT_MODE,
                false,
            )
        }

        val committed =
            editor.commit()

        check(committed) {
            "Failed to commit Direct Boot USB Protection state"
        }

        return load()
    }

    /**
     * Persistently enable or disable Strict USB Mode.
     *
     * Strict Mode is subordinate to USB Protection itself. It may only be
     * stored as enabled while persistent USB Protection is armed.
     *
     * Connection-safety checks belong to the ViewModel and destructive
     * enforcement belongs to UsbDataProbe.
     */
    @Synchronized
    fun setStrictMode(enabled: Boolean): State {
        val current =
            load()

        if (
            enabled &&
            !current.enabled
        ) {
            return current
        }

        val committed =
            prefs.edit()
                .putBoolean(
                    KEY_STRICT_MODE,
                    enabled,
                )
                .commit()

        check(committed) {
            "Failed to commit Direct Boot Strict USB Mode state"
        }

        return load()
    }

    companion object {
        private const val FILE_NAME =
            "runtime_state_policy"

        private const val KEY_ENABLED =
            "usb_protection.enabled"

        private const val KEY_STRICT_MODE =
            "usb_protection.strict_mode"

        const val DEFAULT_ENABLED: Boolean =
            false

        const val DEFAULT_STRICT_MODE: Boolean =
            false
    }
}
