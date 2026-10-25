package com.localruntime.core.deadman

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.localruntime.core.log.SecLog
import com.localruntime.core.wipe.WipeGateway

/**
 * Direct-Boot-aware lifecycle recovery for the Dead Man's Switch.
 *
 * Within a boot the authoritative clock is elapsedRealtime().
 *
 * Because elapsedRealtime() resets at reboot, DeadmanDirectBootStore performs
 * a conservative one-time rebase onto the new boot. That recovery may reduce
 * remaining time, but must never increase the previously stored allowance.
 *
 * Package replacement is not a legitimate check-in and never creates a fresh
 * interval.
 */
class DeadmanBootReceiver : BroadcastReceiver() {

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
            Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action !=
            Intent.ACTION_BOOT_COMPLETED &&
            action !=
            Intent.ACTION_MY_PACKAGE_REPLACED
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
                "Recovery state action=$action " +
                    "enabled=${state.enabled} " +
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
                        "Dead Man disabled; nothing to restore",
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
                    .Classification.Expired -> {
                    when (action) {
                        Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
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
                        }

                        Intent.ACTION_BOOT_COMPLETED -> {
                            SecLog.e(
                                TAG,
                                "DEADMAN_EXPIRED_WOULD_WIPE_" +
                                    "BOOT_COMPLETED " +
                                    "expiryElapsedRealtimeMs=" +
                                    state.expiryElapsedRealtimeMs,
                            )
                        }

                        Intent.ACTION_MY_PACKAGE_REPLACED -> {
                            SecLog.e(
                                TAG,
                                "DEADMAN_EXPIRED_WOULD_WIPE_" +
                                    "PACKAGE_REPLACED " +
                                    "expiryElapsedRealtimeMs=" +
                                    state.expiryElapsedRealtimeMs,
                            )
                        }
                    }

                    return
                }

                DeadmanStateEvaluator
                    .Classification.Armed -> {
                    SecLog.d(
                        TAG,
                        "Re-arming monotonic Dead Man deadline " +
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

                    SecLog.d(
                        TAG,
                        "Starting Dead Man unlock monitor",
                    )

                    DeadmanUnlockService.start(
                        appContext,
                    )
                }
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Dead Man lifecycle recovery failed",
                throwable,
            )
        }
    }

    companion object {
        private const val TAG =
            "DeadmanBootReceiver"
    }
}
