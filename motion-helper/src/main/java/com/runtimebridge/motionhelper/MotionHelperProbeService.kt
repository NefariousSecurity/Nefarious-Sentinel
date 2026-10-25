package com.runtimebridge.motionhelper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.runtimebridge.helper.HelperProtocol
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Isolated Motion Protection sensor monitor.
 *
 * This process owns accelerometer access and classification only.
 * It has no Device Admin / Device Owner authority and cannot lock, reboot,
 * or wipe the device.
 */
class MotionHelperProbeService :
    Service(),
    SensorEventListener {

    private lateinit var sensorManager:
        SensorManager

    private var accelerometer:
        Sensor? =
        null

    private lateinit var configStore:
        MotionHelperConfigStore

    private lateinit var configState:
        MotionHelperConfigStore.State

    private lateinit var profile:
        MotionHelperConfigStore.Profile

    private lateinit var eventClient:
        MotionEventClient

    private var eventActive =
        false

    private var eventStartElapsedMs =
        0L

    private var eventSampleCount =
        0L

    private var minimumMagnitude =
        Float.MAX_VALUE

    private var peakMagnitude =
        0f

    private var previousMagnitude:
        Float? =
        null

    private var maximumMagnitudeDelta =
        0f

    private var highAccelerationSamples =
        0L

    private var currentHighAccelerationRun =
        0L

    private var longestHighAccelerationRun =
        0L

    private var currentHighAccelerationStartNs:
        Long? =
        null

    private var longestHighAccelerationDurationNs =
        0L

    private var nearFreefallSamples =
        0L

    private var currentFreefallRun =
        0L

    private var longestFreefallRun =
        0L

    private var currentFreefallStartNs:
        Long? =
        null

    private var longestFreefallDurationNs =
        0L

    private var snatchCandidateLogged =
        false

    private var sensorRegistered =
        false

    private val mainHandler =
        Handler(
            Looper.getMainLooper(),
        )

    private var sensorRecoveryRetryScheduled =
        false

    private val sensorRecoveryRunnable =
        object : Runnable {
            override fun run() {
                sensorRecoveryRetryScheduled =
                    false

                val currentState =
                    configStore.load()

                if (
                    !currentState.enabled ||
                    sensorRegistered
                ) {
                    return
                }

                attemptStartMonitoring(
                    "sensor_recovery_retry",
                )
            }
        }

    override fun onCreate() {
        super.onCreate()

        configStore =
            MotionHelperConfigStore(
                applicationContext,
            )

        configState =
            configStore.load()

        profile =
            configState.profile

        eventClient =
            MotionEventClient(
                applicationContext,
            )

        eventClient.connect()

        ensureNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
        )

        if (!configState.enabled) {
            setOperationalState(
                HelperProtocol.MOTION_STATE_DISABLED,
                "persistent_state_disabled",
            )

            stopSelf()
            return
        }

        setOperationalState(
            HelperProtocol.MOTION_STATE_STARTING,
            "service_create",
        )

        attemptStartMonitoring(
            "service_create",
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val currentState =
            configStore.load()

        if (!currentState.enabled) {
            configState =
                currentState

            profile =
                currentState.profile

            setOperationalState(
                HelperProtocol.MOTION_STATE_DISABLED,
                "persistent_state_disabled",
            )

            stopSelf(
                startId,
            )

            return START_NOT_STICKY
        }

        applyConfiguration(
            currentState,
            discardActiveEvent =
                intent?.action ==
                    ACTION_RELOAD_CONFIGURATION,
        )

        if (!sensorRegistered) {
            attemptStartMonitoring(
                intent?.action
                    ?: "start_command",
            )
        }

        return START_STICKY
    }

    override fun onSensorChanged(
        event: SensorEvent,
    ) {
        if (
            event.sensor.type !=
            Sensor.TYPE_ACCELEROMETER
        ) {
            return
        }

        val x =
            event.values[0]

        val y =
            event.values[1]

        val z =
            event.values[2]

        val magnitude =
            sqrt(
                x * x +
                    y * y +
                    z * z,
            )

        val nowElapsedMs =
            SystemClock.elapsedRealtime()

        val sensorTimestampNs =
            event.timestamp

        if (
            !eventActive &&
            magnitude >= EVENT_START_THRESHOLD
        ) {
            startEvent(
                nowElapsedMs,
            )

            log(
                "EVENT_START magnitude=${format(magnitude)}",
            )
        }

        if (!eventActive) {
            return
        }

        recordEventSample(
            magnitude,
            sensorTimestampNs,
        )

        if (
            nowElapsedMs -
                eventStartElapsedMs >=
            EVENT_OBSERVATION_MS
        ) {
            finishEvent(
                nowElapsedMs,
            )
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        log(
            "Sensor accuracy changed: " +
                "sensor=${sensor?.name} " +
                "accuracy=$accuracy",
        )
    }

    override fun onDestroy() {
        cancelSensorRecoveryRetry()

        stopSensorMonitoring(
            "service_destroyed",
        )

        eventClient.disconnect()

        setOperationalState(
            HelperProtocol.MOTION_STATE_DISABLED,
            "service_destroyed",
        )

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?,
    ): IBinder? =
        null

    private fun attemptStartMonitoring(
        reason: String,
    ) {
        val currentState =
            configStore.load()

        if (!currentState.enabled) {
            cancelSensorRecoveryRetry()

            setOperationalState(
                HelperProtocol.MOTION_STATE_DISABLED,
                "attempt_while_disabled",
            )

            return
        }

        cancelSensorRecoveryRetry()

        applyConfiguration(
            currentState,
            false,
        )

        if (sensorRegistered) {
            setOperationalState(
                HelperProtocol.MOTION_STATE_ACTIVE,
                "already_registered",
            )

            return
        }

        setOperationalState(
            HelperProtocol.MOTION_STATE_STARTING,
            reason,
        )

        if (
            !isSensorAccessAvailable(
                applicationContext,
            )
        ) {
            setOperationalState(
                HelperProtocol
                    .MOTION_STATE_SENSOR_PERMISSION_REQUIRED,
                "sensor_permission_missing",
            )

            scheduleSensorRecoveryRetry()
            return
        }

        if (!::sensorManager.isInitialized) {
            sensorManager =
                getSystemService(
                    SensorManager::class.java,
                )
                    ?: run {
                        setOperationalState(
                            HelperProtocol.MOTION_STATE_FAILED,
                            "sensor_manager_unavailable",
                        )

                        scheduleSensorRecoveryRetry()
                        return
                    }
        }

        if (accelerometer == null) {
            accelerometer =
                sensorManager
                    .getDefaultSensor(
                        Sensor.TYPE_ACCELEROMETER,
                    )
        }

        if (accelerometer == null) {
            setOperationalState(
                HelperProtocol
                    .MOTION_STATE_ACCELEROMETER_UNAVAILABLE,
                "accelerometer_unavailable",
            )

            return
        }

        val registered =
            try {
                sensorManager
                    .registerListener(
                        this,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_GAME,
                    )
            } catch (
                throwable: Throwable,
            ) {
                log(
                    "Accelerometer registration failed: " +
                        "${throwable.javaClass.simpleName}: " +
                        "${throwable.message}",
                )

                false
            }

        if (!registered) {
            if (
                !isSensorAccessAvailable(
                    applicationContext,
                )
            ) {
                setOperationalState(
                    HelperProtocol
                        .MOTION_STATE_SENSOR_PERMISSION_REQUIRED,
                    "register_listener_permission_denied",
                )
            } else {
                setOperationalState(
                    HelperProtocol.MOTION_STATE_FAILED,
                    "register_listener_failed",
                )
            }

            scheduleSensorRecoveryRetry()
            return
        }

        sensorRegistered =
            true

        setOperationalState(
            HelperProtocol.MOTION_STATE_ACTIVE,
            "accelerometer_registered",
        )

        log(
            "Motion protection started: " +
                "sensor=${accelerometer?.name} " +
                "vendor=${accelerometer?.vendor}",
        )

        logConfiguration()
    }

    private fun scheduleSensorRecoveryRetry() {
        if (
            sensorRecoveryRetryScheduled ||
            sensorRegistered ||
            !configStore.load().enabled
        ) {
            return
        }

        sensorRecoveryRetryScheduled =
            true

        mainHandler.postDelayed(
            sensorRecoveryRunnable,
            SENSOR_RECOVERY_RETRY_MS,
        )
    }

    private fun cancelSensorRecoveryRetry() {
        if (!sensorRecoveryRetryScheduled) {
            return
        }

        mainHandler.removeCallbacks(
            sensorRecoveryRunnable,
        )

        sensorRecoveryRetryScheduled =
            false
    }

    private fun stopSensorMonitoring(
        reason: String,
    ) {
        if (
            ::sensorManager.isInitialized &&
            sensorRegistered
        ) {
            runCatching {
                sensorManager
                    .unregisterListener(
                        this,
                    )
            }

            sensorRegistered =
                false
        }

        if (eventActive) {
            log(
                "Active event discarded: $reason",
            )

            resetEvent()
        }
    }

    private fun applyConfiguration(
        state: MotionHelperConfigStore.State,
        discardActiveEvent: Boolean,
    ) {
        val configurationChanged =
            state.sensitivity !=
                configState.sensitivity

        if (
            discardActiveEvent &&
            configurationChanged &&
            eventActive
        ) {
            resetEvent()
        }

        configState =
            state

        profile =
            state.profile
    }

    private fun startEvent(
        nowElapsedMs: Long,
    ) {
        eventActive =
            true

        eventStartElapsedMs =
            nowElapsedMs

        eventSampleCount =
            0L

        minimumMagnitude =
            Float.MAX_VALUE

        peakMagnitude =
            0f

        previousMagnitude =
            null

        maximumMagnitudeDelta =
            0f

        highAccelerationSamples =
            0L

        currentHighAccelerationRun =
            0L

        longestHighAccelerationRun =
            0L

        currentHighAccelerationStartNs =
            null

        longestHighAccelerationDurationNs =
            0L

        nearFreefallSamples =
            0L

        currentFreefallRun =
            0L

        longestFreefallRun =
            0L

        currentFreefallStartNs =
            null

        longestFreefallDurationNs =
            0L

        snatchCandidateLogged =
            false
    }

    private fun recordEventSample(
        magnitude: Float,
        sensorTimestampNs: Long,
    ) {
        eventSampleCount++

        if (
            magnitude <
            minimumMagnitude
        ) {
            minimumMagnitude =
                magnitude
        }

        if (
            magnitude >
            peakMagnitude
        ) {
            peakMagnitude =
                magnitude
        }

        previousMagnitude
            ?.let { previous ->
                val magnitudeDelta =
                    abs(
                        magnitude -
                            previous,
                    )

                if (
                    magnitudeDelta >
                    maximumMagnitudeDelta
                ) {
                    maximumMagnitudeDelta =
                        magnitudeDelta
                }
            }

        previousMagnitude =
            magnitude

        updateHighAccelerationRun(
            magnitude,
            sensorTimestampNs,
        )

        updateFreefallRun(
            magnitude,
            sensorTimestampNs,
        )
    }

    private fun updateHighAccelerationRun(
        magnitude: Float,
        sensorTimestampNs: Long,
    ) {
        if (
            magnitude >=
            profile.highAccelerationThreshold
        ) {
            highAccelerationSamples++
            currentHighAccelerationRun++

            if (
                currentHighAccelerationRun >
                longestHighAccelerationRun
            ) {
                longestHighAccelerationRun =
                    currentHighAccelerationRun
            }

            if (
                currentHighAccelerationStartNs ==
                null
            ) {
                currentHighAccelerationStartNs =
                    sensorTimestampNs
            }

            val durationNs =
                sensorTimestampNs -
                    (
                        currentHighAccelerationStartNs
                            ?: sensorTimestampNs
                        )

            if (
                durationNs >
                longestHighAccelerationDurationNs
            ) {
                longestHighAccelerationDurationNs =
                    durationNs
            }

            if (
                !snatchCandidateLogged &&
                durationNs >=
                profile.continuousDurationNs
            ) {
                snatchCandidateLogged =
                    true

                log(
                    "SNATCH_CANDIDATE " +
                        "sensitivity=${configState.sensitivity} " +
                        "continuousHighAccelerationMs=" +
                        nanosecondsToMilliseconds(
                            durationNs,
                        ) +
                        " magnitude=${format(magnitude)}",
                )

                eventClient.reportCandidate(
                    SystemClock.elapsedRealtime(),
                )
            }
        } else {
            currentHighAccelerationRun =
                0L

            currentHighAccelerationStartNs =
                null
        }
    }

    private fun updateFreefallRun(
        magnitude: Float,
        sensorTimestampNs: Long,
    ) {
        if (
            magnitude <=
            NEAR_FREEFALL_THRESHOLD
        ) {
            nearFreefallSamples++
            currentFreefallRun++

            if (
                currentFreefallRun >
                longestFreefallRun
            ) {
                longestFreefallRun =
                    currentFreefallRun
            }

            if (
                currentFreefallStartNs ==
                null
            ) {
                currentFreefallStartNs =
                    sensorTimestampNs
            }

            val durationNs =
                sensorTimestampNs -
                    (
                        currentFreefallStartNs
                            ?: sensorTimestampNs
                        )

            if (
                durationNs >
                longestFreefallDurationNs
            ) {
                longestFreefallDurationNs =
                    durationNs
            }
        } else {
            currentFreefallRun =
                0L

            currentFreefallStartNs =
                null
        }
    }

    private fun finishEvent(
        nowElapsedMs: Long,
    ) {
        val durationMs =
            nowElapsedMs -
                eventStartElapsedMs

        log(
            "EVENT_END " +
                "durationMs=$durationMs " +
                "samples=$eventSampleCount " +
                "minMagnitude=${format(minimumMagnitude)} " +
                "peakMagnitude=${format(peakMagnitude)} " +
                "maxDelta=${format(maximumMagnitudeDelta)} " +
                "highAccelSamples=$highAccelerationSamples " +
                "longestHighAccelRun=$longestHighAccelerationRun " +
                "longestHighAccelDurationMs=" +
                nanosecondsToMilliseconds(
                    longestHighAccelerationDurationNs,
                ) +
                " freefallSamples=$nearFreefallSamples " +
                "longestFreefallRun=$longestFreefallRun " +
                "longestFreefallDurationMs=" +
                nanosecondsToMilliseconds(
                    longestFreefallDurationNs,
                ) +
                " snatchCandidate=$snatchCandidateLogged",
        )

        resetEvent()
    }

    private fun resetEvent() {
        eventActive =
            false

        eventSampleCount =
            0L

        previousMagnitude =
            null

        currentHighAccelerationStartNs =
            null

        currentFreefallStartNs =
            null
    }

    private fun logConfiguration() {
        log(
            "Motion sensitivity loaded: " +
                "enabled=${configState.enabled} " +
                "sensitivity=${configState.sensitivity} " +
                "magnitudeThreshold=" +
                format(
                    profile.highAccelerationThreshold,
                ) +
                " continuousDurationMs=" +
                profile.continuousDurationMs,
        )
    }

    private fun setOperationalState(
        state: Int,
        reason: String,
    ) {
        val previous =
            operationalState

        operationalState =
            state

        if (previous != state) {
            log(
                "MOTION_HELPER_STATE " +
                    "from=$previous " +
                    "to=$state " +
                    "reason=$reason",
            )
        }
    }

    private fun nanosecondsToMilliseconds(
        nanoseconds: Long,
    ): Long =
        nanoseconds /
            NANOSECONDS_PER_MILLISECOND

    private fun format(
        value: Float,
    ): String =
        "%.3f".format(
            value,
        )

    private fun ensureNotificationChannel() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java,
            )
                ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(
                    false,
                )
                setSound(
                    null,
                    null,
                )
                enableVibration(
                    false,
                )
                enableLights(
                    false,
                )
            },
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
            .setOngoing(
                true,
            )
            .setSilent(
                true,
            )
            .setShowWhen(
                false,
            )
            .setPriority(
                NotificationCompat.PRIORITY_LOW,
            )
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE,
            )
            .build()

    private fun log(
        message: String,
    ) {
        Log.i(
            TAG,
            message,
        )
    }

    companion object {
        private const val TAG =
            "MotionHelperProbe"

        private const val CHANNEL_ID =
            "runtime.channel.motion"

        private const val NOTIFICATION_ID =
            5293

        private const val ACTION_RELOAD_CONFIGURATION =
            "com.runtimebridge.motionhelper.RELOAD_CONFIGURATION"

        private const val PERMISSION_OTHER_SENSORS =
            "android.permission.OTHER_SENSORS"

        private const val SENSOR_RECOVERY_RETRY_MS =
            5_000L

        private const val EVENT_START_THRESHOLD =
            15.0f

        private const val NEAR_FREEFALL_THRESHOLD =
            3.0f

        private const val EVENT_OBSERVATION_MS =
            750L

        private const val NANOSECONDS_PER_MILLISECOND =
            1_000_000L

        @Volatile
        private var operationalState =
            HelperProtocol.MOTION_STATE_DISABLED

        fun getOperationalState(): Int =
            operationalState

        fun start(
            context: Context,
        ) {
            val appContext =
                context.applicationContext

            val intent =
                Intent(
                    appContext,
                    MotionHelperProbeService::class.java,
                )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                appContext
                    .startForegroundService(
                        intent,
                    )
            } else {
                appContext
                    .startService(
                        intent,
                    )
            }
        }

        fun reloadConfiguration(
            context: Context,
        ) {
            val appContext =
                context.applicationContext

            val state =
                MotionHelperConfigStore(
                    appContext,
                ).load()

            if (!state.enabled) {
                return
            }

            val intent =
                Intent(
                    appContext,
                    MotionHelperProbeService::class.java,
                ).setAction(
                    ACTION_RELOAD_CONFIGURATION,
                )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                appContext
                    .startForegroundService(
                        intent,
                    )
            } else {
                appContext
                    .startService(
                        intent,
                    )
            }
        }

        fun stop(
            context: Context,
        ) {
            context.applicationContext
                .stopService(
                    Intent(
                        context.applicationContext,
                        MotionHelperProbeService::class.java,
                    ),
                )
        }

        fun isSensorAccessAvailable(
            context: Context,
        ): Boolean {
            val appContext =
                context.applicationContext

            val permissionDefined =
                try {
                    appContext.packageManager
                        .getPermissionInfo(
                            PERMISSION_OTHER_SENSORS,
                            0,
                        )

                    true
                } catch (
                    ignored:
                        PackageManager.NameNotFoundException,
                ) {
                    false
                }

            if (!permissionDefined) {
                return true
            }

            return appContext
                .checkSelfPermission(
                    PERMISSION_OTHER_SENSORS,
                ) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
