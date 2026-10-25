package com.localruntime.core.deadman

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.localruntime.core.log.SecLog

/**
 * Exact-alarm scheduler for the Direct-Boot Dead Man's Switch.
 *
 * Dead Man alarms are scheduled against elapsedRealtime(), not wall-clock time.
 * Manual changes to Android date/time therefore cannot postpone an alarm.
 */
object DeadmanAlarmScheduler {

    private const val TAG =
        "DeadmanAlarmScheduler"

    private const val REQUEST_CODE =
        0xDEAD_D00D.toInt()

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
            context.getSystemService(
                AlarmManager::class.java,
            ) ?: return false

        return alarmManager
            .canScheduleExactAlarms()
    }

    /**
     * Arm an absolute elapsedRealtime() deadline.
     */
    fun arm(
        context: Context,
        expiryElapsedRealtimeMs: Long,
        nowElapsedRealtimeMs: Long =
            SystemClock.elapsedRealtime(),
    ) {
        require(
            expiryElapsedRealtimeMs >
                nowElapsedRealtimeMs,
        ) {
            "Dead Man alarm deadline must be in the future"
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
            expiryElapsedRealtimeMs,
            buildPendingIntent(
                appContext,
            ),
        )

        SecLog.d(
            TAG,
            "Armed exact monotonic Dead Man alarm " +
                "expiryElapsedRealtimeMs=$expiryElapsedRealtimeMs",
        )
    }

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

        SecLog.d(
            TAG,
            "Cancelled Dead Man alarm",
        )
    }

    private fun buildPendingIntent(
        context: Context,
    ): PendingIntent {
        val intent =
            Intent(ACTION_FIRE).apply {
                setClassName(
                    context.packageName,
                    DEADMAN_ALARM_RECEIVER_CLASS,
                )
                setPackage(
                    context.packageName,
                )
            }

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val ACTION_FIRE =
        "com.runtimebridge.action.ALARM_B_V1"

    private const val DEADMAN_ALARM_RECEIVER_CLASS =
        "com.localruntime.core.deadman.DeadmanAlarmReceiver"
}
