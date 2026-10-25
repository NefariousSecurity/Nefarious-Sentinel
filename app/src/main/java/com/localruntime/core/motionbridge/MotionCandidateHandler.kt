package com.localruntime.core.motionbridge

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.localruntime.core.admin.DeviceAdminManager
import com.localruntime.core.motion.MotionSensitivityStore
import com.runtimebridge.helper.HelperProtocol

/**
 * Privileged Core-side response to a Motion Helper candidate.
 *
 * The helper is only a sensor/classification oracle. Core independently:
 *
 * - rejects stale candidates;
 * - rechecks that Motion Protection is still enabled;
 * - reads the authoritative Lock/Reboot action;
 * - performs the device-policy operation.
 */
class MotionCandidateHandler(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val store =
        MotionSensitivityStore(
            appContext,
        )

    private val admin =
        DeviceAdminManager(
            appContext,
        )

    fun handleCandidate(
        eventElapsedRealtimeMs: Long,
    ): Int {
        val now =
            SystemClock.elapsedRealtime()

        if (
            eventElapsedRealtimeMs <= 0L ||
            eventElapsedRealtimeMs >
                now + FUTURE_TOLERANCE_MS ||
            now - eventElapsedRealtimeMs >
                MAX_CANDIDATE_AGE_MS
        ) {
            Log.w(
                TAG,
                "MOTION_HELPER_EVENT_REJECTED reason=STALE_OR_INVALID",
            )

            return HelperProtocol
                .EVENT_RESULT_REJECTED
        }

        val state =
            store.load()

        if (!state.enabled) {
            Log.w(
                TAG,
                "MOTION_HELPER_EVENT_IGNORED reason=CORE_DISABLED",
            )

            return HelperProtocol
                .EVENT_RESULT_DISABLED
        }

        return when (
            state.action
        ) {
            MotionSensitivityStore.Action.LOCK ->
                performLock()

            MotionSensitivityStore.Action.REBOOT ->
                performRebootWithLockFallback()
        }
    }

    private fun performLock(): Int {
        val locked =
            try {
                admin.lockNow()
            } catch (
                exception: SecurityException,
            ) {
                Log.e(
                    TAG,
                    "MOTION_HELPER_LOCK_FAILED SecurityException",
                    exception,
                )

                false
            } catch (
                exception: RuntimeException,
            ) {
                Log.e(
                    TAG,
                    "MOTION_HELPER_LOCK_FAILED RuntimeException",
                    exception,
                )

                false
            }

        return if (locked) {
            Log.i(
                TAG,
                "MOTION_HELPER_LOCK_SUCCESS",
            )

            HelperProtocol.EVENT_RESULT_ACCEPTED
        } else {
            Log.w(
                TAG,
                "MOTION_HELPER_LOCK_UNAVAILABLE",
            )

            HelperProtocol.EVENT_RESULT_UNAVAILABLE
        }
    }

    private fun performRebootWithLockFallback():
        Int {
        val rebooted =
            try {
                admin.rebootNow()
            } catch (
                exception: SecurityException,
            ) {
                Log.e(
                    TAG,
                    "MOTION_HELPER_REBOOT_FAILED SecurityException",
                    exception,
                )

                false
            } catch (
                exception: RuntimeException,
            ) {
                Log.e(
                    TAG,
                    "MOTION_HELPER_REBOOT_FAILED RuntimeException",
                    exception,
                )

                false
            }

        if (rebooted) {
            Log.i(
                TAG,
                "MOTION_HELPER_REBOOT_REQUEST_ACCEPTED",
            )

            return HelperProtocol
                .EVENT_RESULT_ACCEPTED
        }

        Log.w(
            TAG,
            "MOTION_HELPER_REBOOT_UNAVAILABLE falling_back_to_lock",
        )

        return performLock()
    }

    companion object {
        private const val TAG =
            "MotionCandidateHandler"

        /*
         * A candidate is a live physical event, not a durable command.
         * Reject queued/replayed events after a short interruption.
         */
        private const val MAX_CANDIDATE_AGE_MS =
            5_000L

        private const val FUTURE_TOLERANCE_MS =
            1_000L
    }
}
