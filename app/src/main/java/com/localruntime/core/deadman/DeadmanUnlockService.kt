package com.localruntime.core.deadman

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.TimeUnit

/**
 * Direct-Boot-capable foreground service that observes successful device
 * unlocks and refreshes the Dead Man deadline.
 *
 * It also periodically checkpoints the monotonic countdown so that reboot
 * recovery does not need to depend solely on an old wall-clock anchor.
 */
class DeadmanUnlockService : Service() {

    private lateinit var store:
        DeadmanDirectBootStore

    private val handler =
        Handler(
            Looper.getMainLooper(),
        )

    private val checkpointRunnable =
        object : Runnable {
            override fun run() {
                checkpoint()

                handler.postDelayed(
                    this,
                    CHECKPOINT_INTERVAL_MS,
                )
            }
        }

    private val userPresentReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (
                    intent.action !=
                    Intent.ACTION_USER_PRESENT
                ) {
                    return
                }

                handleUserPresent()
            }
        }

    override fun onCreate() {
        super.onCreate()

        Log.i(
            TAG,
            "Dead Man unlock service onCreate()",
        )

        store =
            DeadmanDirectBootStore(
                applicationContext,
            )

        ensureNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
        )

        registerReceiver(
            userPresentReceiver,
            IntentFilter(
                Intent.ACTION_USER_PRESENT,
            ),
        )

        handler.postDelayed(
            checkpointRunnable,
            CHECKPOINT_INTERVAL_MS,
        )

        Log.i(
            TAG,
            "ACTION_USER_PRESENT receiver registered",
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.i(
            TAG,
            "onStartCommand() startId=$startId " +
                "intent=${intent?.action}",
        )

        return START_STICKY
    }

    override fun onBind(
        intent: Intent?,
    ): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(
            checkpointRunnable,
        )

        runCatching {
            unregisterReceiver(
                userPresentReceiver,
            )
        }

        Log.i(
            TAG,
            "Dead Man unlock service onDestroy()",
        )

        super.onDestroy()
    }

    private fun checkpoint() {
        try {
            val nowElapsedRealtimeMs =
                SystemClock.elapsedRealtime()

            val state =
                store.checkpoint(
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                    nowWallClockMs =
                        System.currentTimeMillis(),
                    currentBootCount =
                        store.currentBootCount(),
                )

            if (state.enabled) {
                Log.d(
                    TAG,
                    "Dead Man recovery checkpoint " +
                        "expiryElapsedRealtimeMs=" +
                        state.expiryElapsedRealtimeMs,
                )
            }
        } catch (
            throwable: Throwable,
        ) {
            Log.e(
                TAG,
                "Dead Man recovery checkpoint failed",
                throwable,
            )
        }
    }

    private fun handleUserPresent() {
        val keyguardManager =
            getSystemService(
                KeyguardManager::class.java,
            )

        if (keyguardManager == null) {
            Log.e(
                TAG,
                "KeyguardManager unavailable; " +
                    "ignoring USER_PRESENT",
            )

            return
        }

        val deviceLocked =
            keyguardManager.isDeviceLocked

        Log.i(
            TAG,
            "USER_PRESENT received: " +
                "isDeviceLocked=$deviceLocked",
        )

        if (deviceLocked) {
            Log.w(
                TAG,
                "Ignoring USER_PRESENT because " +
                    "device is still reported locked",
            )

            return
        }

        try {
            val nowElapsedRealtimeMs =
                SystemClock.elapsedRealtime()

            val nowWallClockMs =
                System.currentTimeMillis()

            val currentBootCount =
                store.currentBootCount()

            val before =
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
                    state = before,
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                    currentBootCount =
                        currentBootCount,
                )

            Log.d(
                TAG,
                "Pre-check-in state " +
                    "enabled=${before.enabled} " +
                    "ready=${before.isReady()} " +
                    "classification=$classification " +
                    "expiryElapsedRealtimeMs=" +
                    before.expiryElapsedRealtimeMs,
            )

            when (classification) {
                DeadmanStateEvaluator
                    .Classification.Disabled -> {
                    Log.d(
                        TAG,
                        "Dead Man disabled; unlock " +
                            "does not refresh deadline",
                    )

                    return
                }

                DeadmanStateEvaluator
                    .Classification.Invalid -> {
                    Log.e(
                        TAG,
                        "Dead Man enabled but state " +
                            "is not ready; refusing refresh",
                    )

                    return
                }

                DeadmanStateEvaluator
                    .Classification.Expired -> {
                    Log.e(
                        TAG,
                        "Dead Man already expired; " +
                            "refusing unlock refresh",
                    )

                    return
                }

                DeadmanStateEvaluator
                    .Classification.Armed -> {
                    val after =
                        store.checkIn(
                            nowElapsedRealtimeMs =
                                nowElapsedRealtimeMs,
                            nowWallClockMs =
                                nowWallClockMs,
                            currentBootCount =
                                currentBootCount,
                        )

                    Log.i(
                        TAG,
                        "Successful device unlock " +
                            "refreshed Dead Man deadline " +
                            "oldExpiryElapsedRealtimeMs=" +
                            before.expiryElapsedRealtimeMs +
                            " newExpiryElapsedRealtimeMs=" +
                            after.expiryElapsedRealtimeMs,
                    )

                    DeadmanAlarmScheduler.arm(
                        context =
                            applicationContext,
                        expiryElapsedRealtimeMs =
                            after
                                .expiryElapsedRealtimeMs,
                        nowElapsedRealtimeMs =
                            nowElapsedRealtimeMs,
                    )
                }
            }
        } catch (
            throwable: Throwable,
        ) {
            Log.e(
                TAG,
                "Failed to refresh Dead Man " +
                    "deadline after USER_PRESENT",
                throwable,
            )
        }
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
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }

        notificationManager
            .createNotificationChannel(
                channel,
            )
    }

    private fun buildNotification() =
        NotificationCompat
            .Builder(
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
            "DeadmanUnlockService"

        private const val CHANNEL_ID =
            "runtime.channel.monitor"

        private const val NOTIFICATION_ID =
            4292

        private val CHECKPOINT_INTERVAL_MS =
            TimeUnit.MINUTES.toMillis(5)

        fun start(
            context: Context,
        ) {
            val intent =
                Intent(
                    context,
                    DeadmanUnlockService::class.java,
                )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                context.startForegroundService(
                    intent,
                )
            } else {
                context.startService(
                    intent,
                )
            }
        }

        fun stop(
            context: Context,
        ) {
            context.stopService(
                Intent(
                    context,
                    DeadmanUnlockService::class.java,
                ),
            )
        }
    }
}
