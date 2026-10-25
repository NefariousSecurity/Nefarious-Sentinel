package com.runtimebridge.motionhelper

import android.content.Context
import com.runtimebridge.helper.HelperProtocol
import kotlin.math.roundToLong

/**
 * Minimal Direct-Boot-safe Motion Helper configuration cache.
 *
 * Core remains authoritative. The helper persists only:
 *
 * - enabled
 * - sensitivity
 *
 * The selected Lock/Reboot action never enters this package.
 */
class MotionHelperConfigStore(
    context: Context,
) {

    private val deviceContext =
        context.applicationContext
            .createDeviceProtectedStorageContext()

    private val prefs =
        deviceContext.getSharedPreferences(
            FILE_NAME,
            Context.MODE_PRIVATE,
        )

    data class State(
        val enabled: Boolean,
        val sensitivity: Int,
    ) {
        val profile: Profile
            get() =
                profileForSensitivity(
                    sensitivity,
                )
    }

    data class Profile(
        val highAccelerationThreshold: Float,
        val continuousDurationMs: Long,
    ) {
        val continuousDurationNs: Long
            get() =
                continuousDurationMs *
                    NANOSECONDS_PER_MILLISECOND
    }

    @Synchronized
    fun load(): State =
        State(
            enabled =
                prefs.getBoolean(
                    KEY_ENABLED,
                    false,
                ),
            sensitivity =
                prefs.getInt(
                    KEY_SENSITIVITY,
                    DEFAULT_SENSITIVITY,
                ).coerceIn(
                    HelperProtocol.MOTION_MIN_SENSITIVITY,
                    HelperProtocol.MOTION_MAX_SENSITIVITY,
                ),
        )

    @Synchronized
    fun apply(
        enabled: Boolean,
        sensitivity: Int,
    ): Boolean {
        if (
            sensitivity !in
            HelperProtocol.MOTION_MIN_SENSITIVITY..
                HelperProtocol.MOTION_MAX_SENSITIVITY
        ) {
            return false
        }

        return prefs.edit()
            .putBoolean(
                KEY_ENABLED,
                enabled,
            )
            .putInt(
                KEY_SENSITIVITY,
                sensitivity,
            )
            .commit()
    }

    companion object {
        private const val FILE_NAME =
            "runtime_state_motion_helper"

        private const val KEY_ENABLED =
            "motion.enabled"

        private const val KEY_SENSITIVITY =
            "motion.sensitivity"

        private const val DEFAULT_SENSITIVITY =
            50

        private const val LOW_SENSITIVITY =
            0

        private const val MEDIUM_SENSITIVITY =
            50

        private const val HIGH_SENSITIVITY =
            100

        private const val LOW_ACCELERATION_THRESHOLD =
            24.0f

        private const val MEDIUM_ACCELERATION_THRESHOLD =
            20.0f

        private const val HIGH_ACCELERATION_THRESHOLD =
            18.0f

        private const val LOW_DURATION_MS =
            160L

        private const val MEDIUM_DURATION_MS =
            160L

        private const val HIGH_DURATION_MS =
            140L

        private const val NANOSECONDS_PER_MILLISECOND =
            1_000_000L

        fun profileForSensitivity(
            sensitivity: Int,
        ): Profile {
            val clamped =
                sensitivity.coerceIn(
                    HelperProtocol.MOTION_MIN_SENSITIVITY,
                    HelperProtocol.MOTION_MAX_SENSITIVITY,
                )

            return if (
                clamped <= MEDIUM_SENSITIVITY
            ) {
                interpolate(
                    sensitivity = clamped,
                    rangeStart = LOW_SENSITIVITY,
                    rangeEnd = MEDIUM_SENSITIVITY,
                    startAcceleration =
                        LOW_ACCELERATION_THRESHOLD,
                    endAcceleration =
                        MEDIUM_ACCELERATION_THRESHOLD,
                    startDurationMs =
                        LOW_DURATION_MS,
                    endDurationMs =
                        MEDIUM_DURATION_MS,
                )
            } else {
                interpolate(
                    sensitivity = clamped,
                    rangeStart = MEDIUM_SENSITIVITY,
                    rangeEnd = HIGH_SENSITIVITY,
                    startAcceleration =
                        MEDIUM_ACCELERATION_THRESHOLD,
                    endAcceleration =
                        HIGH_ACCELERATION_THRESHOLD,
                    startDurationMs =
                        MEDIUM_DURATION_MS,
                    endDurationMs =
                        HIGH_DURATION_MS,
                )
            }
        }

        private fun interpolate(
            sensitivity: Int,
            rangeStart: Int,
            rangeEnd: Int,
            startAcceleration: Float,
            endAcceleration: Float,
            startDurationMs: Long,
            endDurationMs: Long,
        ): Profile {
            val fraction =
                (
                    sensitivity - rangeStart
                    ).toFloat() /
                    (
                        rangeEnd - rangeStart
                        ).toFloat()

            val acceleration =
                startAcceleration +
                    (
                        endAcceleration -
                            startAcceleration
                        ) *
                    fraction

            val durationMs =
                (
                    startDurationMs +
                        (
                            endDurationMs -
                                startDurationMs
                            ) *
                        fraction
                    ).roundToLong()

            return Profile(
                highAccelerationThreshold =
                    acceleration,
                continuousDurationMs =
                    durationMs,
            )
        }
    }
}
