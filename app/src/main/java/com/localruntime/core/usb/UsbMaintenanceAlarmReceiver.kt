package com.localruntime.core.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.localruntime.core.admin.HardenedPolicyManager
import com.localruntime.core.tamper.DebuggingTamperGuard

/**
 * Exact-alarm receiver for USB Maintenance Mode expiry.
 *
 * This receiver re-reads the authoritative Device Protected Storage state
 * rather than trusting alarm delivery alone.
 *
 * Rules:
 *
 * - unexpected action: ignore;
 * - inactive state: Maintenance is no longer active;
 * - early alarm: re-arm the same persisted monotonic expiry;
 * - expired state: UsbMaintenanceStore.load() clears it synchronously;
 * - after confirmed expiry, re-evaluate any still-active USB candidate
 *   through the normal USB Protection policy path.
 *
 * Maintenance Mode is never extended by this receiver.
 *
 * The receiver does not call WipeGateway directly. Any active candidate is
 * routed back through UsbDataProbe so persistent USB Protection and current
 * Maintenance state are re-read before enforcement can occur.
 */
class UsbMaintenanceAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (
            intent.action !=
            UsbMaintenanceAlarmScheduler.ACTION_FIRE
        ) {
            Log.w(
                TAG,
                "Ignoring unexpected action=${intent.action}",
            )

            return
        }

        val appContext =
            context.applicationContext

        try {
            val maintenanceState =
                UsbMaintenanceStore(
                    appContext,
                ).load()

            if (maintenanceState.active) {
                /*
                 * Alarm arrived before the authoritative monotonic deadline.
                 *
                 * Re-arm exactly the same persisted deadline. Do not create
                 * or extend a Maintenance Mode window.
                 */
                Log.w(
                    TAG,
                    "USB Maintenance alarm arrived early; " +
                        "re-arming persisted expiry " +
                        "remainingMs=${maintenanceState.remainingMs}",
                )

                UsbMaintenanceAlarmScheduler.arm(
                    context = appContext,
                    expiresAtElapsedRealtimeMs =
                        maintenanceState
                            .expiresAtElapsedRealtimeMs,
                )

                return
            }

            /*
             * load() synchronously removes an expired stored deadline.
             *
             * At this point temporary suppression is no longer active and
             * persistent USB Protection has returned to its normal policy.
             */
            Log.w(
                TAG,
                "USB Maintenance Mode expired; " +
                    "normal USB Protection policy restored",
            )

            /*
             * Preserve the existing debugging-tamper boundary before the
             * Device Owner restriction is restored. If debugging was left
             * enabled during Maintenance, the existing emergency-wipe path is
             * evaluated against the now-inactive Maintenance state.
             */
            DebuggingTamperGuard(
                appContext,
            ).checkNow(
                reason =
                    "MAINTENANCE_EXPIRED",
            )

            val debuggingRestrictionHealthy =
                HardenedPolicyManager(
                    appContext,
                ).applyDebuggingRestrictionPolicy()

            if (!debuggingRestrictionHealthy) {
                Log.e(
                    TAG,
                    "Failed to restore Hardened debugging restriction " +
                        "after Maintenance expiry",
                )
            }

            /*
             * Re-evaluate the existing candidate latch through the normal
             * USB Protection policy path.
             *
             * UsbDataProbe will:
             *
             * - do nothing if no candidate remains active;
             * - re-read persistent USB Protection;
             * - re-read Maintenance Mode;
             * - invoke the established wipe path only if policy allows it.
             */
            UsbDataProbe.reevaluateActiveCandidate(
                context = appContext,
                reason = "MAINTENANCE_EXPIRED",
            )

            /*
             * Maintenance also suppresses USB / Wireless Debugging tamper
             * reactions.
             *
             * Starting the persistent monitor again does not create a second
             * monitor when the service is already alive. Android delivers
             * onStartCommand(), which causes the existing DebuggingTamperGuard
             * to immediately re-read both debugging settings now that
             * Maintenance is inactive.
             *
             * If either corresponding Tamper Proofing switch is armed and its
             * debugging transport was left enabled, the normal emergency-wipe
             * path resumes here.
             */
            UsbDataProbeService.start(
                appContext,
            )
        } catch (
            throwable: Throwable
        ) {
            Log.e(
                TAG,
                "USB Maintenance alarm handling failed",
                throwable,
            )
        }
    }

    companion object {
        private const val TAG =
            "UsbMaintenanceAlarm"
    }
}
