package com.localruntime.core.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.localruntime.core.admin.HardenedPolicyManager
import com.localruntime.core.tamper.DebuggingTamperGuard

/**
 * Lifecycle recovery receiver for the Direct-Boot-aware USB data detector.
 *
 * Behaviour differs deliberately by lifecycle event:
 *
 * - LOCKED_BOOT_COMPLETED:
 *     A reboot is a hard boundary for temporary USB Maintenance Mode.
 *     Maintenance state is cleared before the detector starts.
 *
 * - BOOT_COMPLETED:
 *     The detector is started without clearing Maintenance Mode because the
 *     user may already have started a maintenance session after first unlock.
 *
 * - MY_PACKAGE_REPLACED:
 *     The detector is restored after this Core package is updated. Maintenance
 *     Mode is preserved because package replacement is not a reboot boundary.
 */
class UsbDataProbeBootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val appContext =
            context.applicationContext

        val action =
            intent.action

        Log.i(
            TAG,
            "Lifecycle receiver action=$action",
        )

        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                /*
                 * Reboot is a hard security boundary for Maintenance Mode.
                 *
                 * Clear temporary suppression before the USB detector is
                 * allowed to start.
                 */
                UsbMaintenanceStore(
                    appContext,
                ).clearForBoot()

                Log.i(
                    TAG,
                    "USB Maintenance Mode cleared for Direct Boot",
                )

                /*
                 * Maintenance is now inactive. Evaluate any debugging state
                 * left across the reboot boundary before the preventive
                 * restriction is restored.
                 */
                DebuggingTamperGuard(
                    appContext,
                ).checkNow(
                    reason =
                        "LOCKED_BOOT_COMPLETED",
                )

                val debuggingRestrictionHealthy =
                    HardenedPolicyManager(
                        appContext,
                    ).applyDebuggingRestrictionPolicy()

                if (!debuggingRestrictionHealthy) {
                    Log.e(
                        TAG,
                        "Failed to restore Hardened debugging restriction " +
                            "at Direct Boot boundary",
                    )
                }

                UsbDataProbeService.start(
                    appContext,
                )
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                /*
                 * Do not clear Maintenance Mode here.
                 *
                 * BOOT_COMPLETED may occur after first unlock and must not
                 * unexpectedly terminate a maintenance session started during
                 * the current boot.
                 */
                Log.i(
                    TAG,
                    "BOOT_COMPLETED: preserving USB Maintenance Mode",
                )

                UsbDataProbeService.start(
                    appContext,
                )
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                /*
                 * Package replacement kills the old Core process and therefore
                 * the USB detector service.
                 *
                 * This is not a reboot boundary. Preserve all authoritative
                 * USB policy and Maintenance Mode state exactly as stored, and
                 * restart only the detector runtime.
                 */
                Log.i(
                    TAG,
                    "MY_PACKAGE_REPLACED: preserving USB state and " +
                        "restarting detector",
                )

                UsbDataProbeService.start(
                    appContext,
                )
            }

            else -> {
                Log.w(
                    TAG,
                    "Ignoring unexpected action=$action",
                )
            }
        }
    }

    companion object {
        private const val TAG =
            "UsbDataProbe"
    }
}
