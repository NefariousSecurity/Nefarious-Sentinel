package com.localruntime.core.wipe

import android.content.Context

/**
 * Authoritative persistent triggered-wipe configuration.
 *
 * eSIM erasure deliberately defaults to OFF.
 */
class WipeBehaviourStore(
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

    fun isEsimEraseEnabled(): Boolean =
        preferences.getBoolean(
            KEY_ESIM_ERASE_ENABLED,
            false,
        )

    fun setEsimEraseEnabled(
        enabled: Boolean,
    ): Boolean =
        preferences
            .edit()
            .putBoolean(
                KEY_ESIM_ERASE_ENABLED,
                enabled,
            )
            .commit()

    companion object {

        private const val PREFERENCES_NAME =
            "runtime_policy_wipe_behaviour"

        private const val KEY_ESIM_ERASE_ENABLED =
            "wipe.erase_esims_enabled"
    }
}
