package com.localruntime.core.deadman

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.localruntime.core.log.SecLog
import com.localruntime.core.wipe.WipeGateway

/**
 * Direct-Boot-safe receiver for the Dead Man alarm.
 *
 * Security decisions use elapsedRealtime() only.
 *
 * On delivery the receiver re-reads authoritative persisted state rather than
 * trusting the alarm itself.
 */
class DeadmanAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action =
            intent.action

        SecLog.d(
            TAG,
            "onReceive action=$action",
        )

        if (
            action !=
            DeadmanAlarmScheduler.ACTION_FIRE
        ) {
            SecLog.w(
                TAG,
                "Ignoring unexpected action=$action",
            )

            return
        }

        try {
            val appContext =
                context.applicationContext

            val store =
                DeadmanDirectBootStore(
                    appContext,
                )

            val nowElapsedRealtimeMs =
                SystemClock.elapsedRealtime()

            val nowWallClockMs =
                System.currentTimeMillis()

            val currentBootCount =
                store.currentBootCount()

            val state =
                store.loadCurrent(
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                    nowWallClockMs =
                        nowWallClockMs,
                    currentBootCount =
                        currentBootCount,
                )

            val classification =
                DeadmanStateEvaluator.classify(
                    state = state,
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                    currentBootCount =
                        currentBootCount,
                )

            SecLog.d(
                TAG,
                "Alarm state enabled=${state.enabled} " +
                    "ready=${state.isReady()} " +
                    "classification=$classification " +
                    "bootCount=${state.bootCount} " +
                    "currentBootCount=$currentBootCount " +
                    "expiryElapsedRealtimeMs=" +
                    "${state.expiryElapsedRealtimeMs} " +
                    "nowElapsedRealtimeMs=" +
                    "$nowElapsedRealtimeMs " +
                    "remainingMs=" +
                    state.remainingSameBootMs(
                        nowElapsedRealtimeMs =
                            nowElapsedRealtimeMs,
                        currentBootCount =
                            currentBootCount,
                    ),
            )

            when (classification) {
                DeadmanStateEvaluator
                    .Classification.Disabled -> {
                    SecLog.d(
                        TAG,
                        "Dead Man disabled; ignoring alarm",
                    )

                    return
                }

                DeadmanStateEvaluator
                    .Classification.Invalid -> {
                    SecLog.e(
                        TAG,
                        "Dead Man state invalid or incomplete; " +
                            "alarm not armed and no destructive " +
                            "action taken",
                    )

                    return
                }

                DeadmanStateEvaluator
                    .Classification.Armed -> {
                    SecLog.w(
                        TAG,
                        "Dead Man alarm arrived early; " +
                            "re-arming same monotonic deadline " +
                            state.expiryElapsedRealtimeMs,
                    )

                    DeadmanAlarmScheduler.arm(
                        context = appContext,
                        expiryElapsedRealtimeMs =
                            state
                                .expiryElapsedRealtimeMs,
                        nowElapsedRealtimeMs =
                            nowElapsedRealtimeMs,
                    )

                    return
                }

                DeadmanStateEvaluator
                    .Classification.Expired -> {
                    SecLog.e(
                        TAG,
                        "DEADMAN_EXPIRED_WIPE " +
                            "expiryElapsedRealtimeMs=" +
                            "${state.expiryElapsedRealtimeMs} " +
                            "nowElapsedRealtimeMs=" +
                            "$nowElapsedRealtimeMs",
                    )

                    val wipeGateway =
                        WipeGateway(
                            appContext,
                        )

                    val wipeResult =
                        wipeGateway.wipeNow()

                    SecLog.e(
                        TAG,
                        "Dead Man wipe call returned " +
                            "result=$wipeResult",
                    )

                    return
                }
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Dead Man alarm handling threw",
                throwable,
            )
        }
    }

    companion object {
        private const val TAG =
            "DeadmanAlarmReceiver"
    }
}
