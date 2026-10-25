package com.localruntime.core.usb

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * Exact-alarm scheduler for bounded USB Maintenance Mode.
 *
 * The deadline supplied to [arm] is the absolute elapsed-realtime timestamp
 * already persisted by UsbMaintenanceStore.
 *
 * This scheduler:
 *
 * - never creates or extends a Maintenance Mode deadline;
 * - uses ELAPSED_REALTIME_WAKEUP so wall-clock changes cannot extend the
 *   maintenance window;
 * - uses setExactAndAllowWhileIdle so expiry can be delivered through Doze;
 * - deliberately does not restore alarms after reboot because Maintenance
 *   Mode must never survive reboot.
 */
object UsbMaintenanceAlarmScheduler {

    private const val TAG =
        "UsbMaintenanceAlarm"

    /*
     * Keep this request code distinct from other alarm schedulers.
     */
    private const val REQUEST_CODE =
        0x554D_4149

    /**
     * Returns whether Android currently permits this app to schedule exact
     * alarms.
     */
    fun canScheduleExactAlarms(
        context: Context,
    ): Boolean {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {
            return true
        }

        val alarmManager =
            context.applicationContext.getSystemService(
                AlarmManager::class.java,
            ) ?: return false

        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * Arm the already-persisted monotonic Maintenance Mode expiry.
     *
     * @throws IllegalArgumentException if the supplied expiry is not in the
     * future.
     * @throws IllegalStateException if AlarmManager or exact-alarm capability
     * is unavailable.
     */
    fun arm(
        context: Context,
        expiresAtElapsedRealtimeMs: Long,
        nowElapsedRealtimeMs: Long =
            SystemClock.elapsedRealtime(),
    ) {
        require(
            expiresAtElapsedRealtimeMs >
                nowElapsedRealtimeMs,
        ) {
            "USB Maintenance expiry must be in the future"
        }

        val appContext =
            context.applicationContext

        val alarmManager =
            appContext.getSystemService(
                AlarmManager::class.java,
            ) ?: throw IllegalStateException(
                "AlarmManager unavailable",
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            throw IllegalStateException(
                "Exact alarm permission unavailable",
            )
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            expiresAtElapsedRealtimeMs,
            buildPendingIntent(
                appContext,
            ),
        )

        Log.i(
            TAG,
            "Armed USB Maintenance alarm " +
                "expiryElapsedRealtimeMs=" +
                "$expiresAtElapsedRealtimeMs",
        )
    }

    /**
     * Cancel the currently armed Maintenance Mode expiry alarm, if any.
     */
    fun cancel(
        context: Context,
    ) {
        val appContext =
            context.applicationContext

        val alarmManager =
            appContext.getSystemService(
                AlarmManager::class.java,
            ) ?: throw IllegalStateException(
                "AlarmManager unavailable",
            )

        alarmManager.cancel(
            buildPendingIntent(
                appContext,
            ),
        )

        Log.i(
            TAG,
            "Cancelled USB Maintenance alarm",
        )
    }

    private fun buildPendingIntent(
        context: Context,
    ): PendingIntent {
        val intent =
            Intent(
                ACTION_FIRE,
            ).apply {
                setClassName(
                    context.packageName,
                    RECEIVER_CLASS,
                )

                setPackage(
                    context.packageName,
                )
            }

        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flags,
        )
    }

    const val ACTION_FIRE =
        "com.runtimebridge.action.ALARM_A_V1"

    private const val RECEIVER_CLASS =
        "com.localruntime.core.usb.UsbMaintenanceAlarmReceiver"
}
