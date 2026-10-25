package com.localruntime.core.usb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.localruntime.core.tamper.DebuggingTamperGuard

/**
 * Foreground service for persistent transport-security monitoring.
 *
 * Responsibilities:
 *
 * - keep UsbDataProbe registered for physical USB data-host detection;
 * - monitor Android's Wireless Debugging state;
 * - remain usable during Direct Boot / BFU;
 * - keep transport monitoring alive independently of the Controller UI.
 *
 * Maintenance Mode timing is deliberately not implemented here.
 *
 * The authoritative Maintenance Mode deadline is stored in
 * UsbMaintenanceStore and enforced by UsbMaintenanceAlarmScheduler /
 * UsbMaintenanceAlarmReceiver.
 *
 * This avoids relying on an in-process Handler timer, which may be delayed
 * while the device is asleep and disappears if the process is killed.
 */
class UsbDataProbeService : Service() {

    private lateinit var debuggingTamperGuard:
        DebuggingTamperGuard

    override fun onCreate() {
        super.onCreate()

        Log.i(
            TAG,
            "Probe service onCreate()",
        )

        ensureNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
        )

        UsbDataProbe.register(
            applicationContext,
        )

        debuggingTamperGuard =
            DebuggingTamperGuard(
                applicationContext,
            )

        debuggingTamperGuard.start()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.i(
            TAG,
            "Probe service onStartCommand() " +
                "startId=$startId intent=${intent?.action}",
        )

        /*
         * Re-check Wireless Debugging on every service start.
         *
         * DeviceAdminReceiver starts this service when administration becomes
         * active. If the service was already alive, onCreate() will not run
         * again, so this explicit check closes that activation-time gap.
         */
        if (
            ::debuggingTamperGuard
                .isInitialized
        ) {
            debuggingTamperGuard
                .checkNow(
                    reason =
                        "SERVICE_START",
                )
        }

        /*
         * START_STICKY exists to keep the transport monitors alive.
         *
         * Exact Maintenance Mode expiry is owned by AlarmManager and the
         * authoritative Device Protected Storage state.
         */
        return START_STICKY
    }

    override fun onBind(
        intent: Intent?,
    ): IBinder? = null

    override fun onDestroy() {
        Log.i(
            TAG,
            "Probe service onDestroy()",
        )

        if (
            ::debuggingTamperGuard
                .isInitialized
        ) {
            debuggingTamperGuard.stop()
        }

        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val notificationManager =
            getSystemService(
                NotificationManager::class.java,
            ) ?: return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description =
                    "Background service status"

                setShowBadge(false)

                setSound(
                    null,
                    null,
                )

                enableVibration(false)
                enableLights(false)
            }

        notificationManager.createNotificationChannel(
            channel,
        )
    }

    private fun buildNotification() =
        NotificationCompat.Builder(
            this,
            CHANNEL_ID,
        )
            .setSmallIcon(
                android.R.drawable.ic_lock_lock,
            )
            .setContentTitle(
                "Background Service",
            )
            .setContentText(
                "Service active",
            )
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(
                NotificationCompat.PRIORITY_LOW,
            )
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE,
            )
            .build()

    companion object {
        private const val TAG =
            "UsbDataProbeService"

        /*
         * Keep the existing channel ID so Android treats upgrades as the same
         * notification channel rather than creating another channel.
         */
        private const val CHANNEL_ID =
            "runtime.channel.logging"

        private const val NOTIFICATION_ID =
            4291

        fun start(
            context: Context,
        ) {
            val appContext =
                context.applicationContext

            val intent =
                Intent(
                    appContext,
                    UsbDataProbeService::class.java,
                )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                appContext.startForegroundService(
                    intent,
                )
            } else {
                appContext.startService(
                    intent,
                )
            }
        }

        fun stop(
            context: Context,
        ) {
            val appContext =
                context.applicationContext

            appContext.stopService(
                Intent(
                    appContext,
                    UsbDataProbeService::class.java,
                ),
            )
        }
    }
}
