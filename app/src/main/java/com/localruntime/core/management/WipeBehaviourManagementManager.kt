package com.localruntime.core.management

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.localruntime.core.wipe.WipeBehaviourStore
import com.runtimebridge.api.ManagementProtocol

/**
 * Authenticated management facade for triggered-wipe behaviour.
 *
 * The preference is Core-owned and stored in Device Protected Storage.
 *
 * eSIM erasure:
 * - defaults to OFF;
 * - may only be enabled while the Core is Device Owner;
 * - is reported OFF whenever Device Owner authority is absent.
 *
 * This manager only owns configuration. The actual destructive wipe path is
 * deliberately not changed here.
 */
class WipeBehaviourManagementManager(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val devicePolicyManager =
        appContext.getSystemService(
            DevicePolicyManager::class.java,
        )

    private val store =
        WipeBehaviourStore(
            appContext,
        )

    fun isEsimEraseAvailable(): Boolean =
        devicePolicyManager
            ?.isDeviceOwnerApp(
                appContext.packageName,
            ) == true

    fun isEsimEraseEnabled(): Boolean {
        if (!isEsimEraseAvailable()) {
            /*
             * Do not allow an old Device Owner preference to become active
             * again later simply because authority changed.
             */
            if (store.isEsimEraseEnabled()) {
                store.setEsimEraseEnabled(
                    false,
                )
            }

            return false
        }

        return store.isEsimEraseEnabled()
    }

    fun setEsimEraseEnabled(
        enabled: Boolean,
    ): Int {
        if (
            enabled &&
            !isEsimEraseAvailable()
        ) {
            return ManagementProtocol
                .WIPE_BEHAVIOUR_RESULT_DEVICE_OWNER_REQUIRED
        }

        if (
            !store.setEsimEraseEnabled(
                enabled,
            )
        ) {
            return ManagementProtocol
                .WIPE_BEHAVIOUR_RESULT_FAILED
        }

        return ManagementProtocol
            .WIPE_BEHAVIOUR_RESULT_OK
    }
}
