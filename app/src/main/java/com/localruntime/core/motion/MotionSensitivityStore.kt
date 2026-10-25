package com.localruntime.core.motion

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToLong

/**
 * Direct-Boot-safe persistent configuration for Motion Protection.
 *
 * Motion Protection has three independent pieces of persistent configuration:
 *
 * - enabled:
 *   Whether Motion Protection should be active.
 *
 * - sensitivity:
 *   User-facing sensitivity from 0 to 100.
 *
 * - action:
 *   The non-destructive action performed when a snatch candidate is detected.
 *   Supported actions are Lock and Reboot.
 *
 * The sensitivity scale is:
 *
 *     0   = Low
 *     50  = Medium
 *     100 = High
 *
 * Medium deliberately preserves the current hardware-proven detector:
 *
 *     high-acceleration threshold = 20.0 m/s²
 *     continuous duration         = 160 ms
 *
 * Low, Medium, and High profiles have been hardware validated on the
 * target device.
 *
 * This store contains configuration only. It does not start or stop Motion
 * Protection, classify motion, lock the device, reboot, or wipe data.
 *
 * Device Protected Storage is used so the same configuration is available
 * before the first user unlock after reboot.
 */
class MotionSensitivityStore(context: Context) {

    private val deviceContext: Context =
        context.applicationContext.createDeviceProtectedStorageContext()

    private val prefs: SharedPreferences =
        deviceContext.getSharedPreferences(
            FILE_NAME,
            Context.MODE_PRIVATE,
        )

    enum class Preset(
        val sensitivity: Int,
    ) {
        LOW(LOW_SENSITIVITY),
        MEDIUM(MEDIUM_SENSITIVITY),
        HIGH(HIGH_SENSITIVITY),
    }

    /**
     * Action performed after a snatch candidate is detected.
     *
     * LOCK preserves the existing Motion Protection behaviour.
     *
     * REBOOT is persisted here for the selectable-action feature. The store
     * itself does not perform the reboot.
     */
    enum class Action {
        LOCK,
        REBOOT,
    }

    data class State(
        val enabled: Boolean,
        val sensitivity: Int,
        val action: Action,
    ) {
        val profile: Profile
            get() =
                profileForSensitivity(sensitivity)

        val exactPreset: Preset?
            get() =
                Preset.entries.firstOrNull {
                    it.sensitivity == sensitivity
                }
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

    /**
     * Read the authoritative persistent Motion Protection configuration.
     *
     * Unknown or invalid stored action values fail closed to LOCK.
     */
    @Synchronized
    fun load(): State {
        val storedSensitivity =
            prefs.getInt(
                KEY_SENSITIVITY,
                DEFAULT_SENSITIVITY,
            )

        val storedAction =
            prefs.getString(
                KEY_ACTION,
                null,
            )

        val action =
            Action.entries.firstOrNull {
                it.name == storedAction
            } ?: DEFAULT_ACTION

        return State(
            enabled =
                prefs.getBoolean(
                    KEY_ENABLED,
                    DEFAULT_ENABLED,
                ),
            sensitivity =
                storedSensitivity.coerceIn(
                    MIN_SENSITIVITY,
                    MAX_SENSITIVITY,
                ),
            action =
                action,
        )
    }

    /**
     * Persist whether Motion Protection is enabled.
     *
     * This method changes configuration only. Service lifecycle is handled
     * separately by the feature controller.
     */
    @Synchronized
    fun setEnabled(
        enabled: Boolean,
    ): State {
        val committed =
            prefs.edit()
                .putBoolean(
                    KEY_ENABLED,
                    enabled,
                )
                .commit()

        check(committed) {
            "Failed to commit Direct Boot motion enabled state"
        }

        return load()
    }

    /**
     * Persist an arbitrary slider position from 0 through 100.
     */
    @Synchronized
    fun setSensitivity(
        sensitivity: Int,
    ): State {
        val validatedSensitivity =
            sensitivity.coerceIn(
                MIN_SENSITIVITY,
                MAX_SENSITIVITY,
            )

        val committed =
            prefs.edit()
                .putInt(
                    KEY_SENSITIVITY,
                    validatedSensitivity,
                )
                .commit()

        check(committed) {
            "Failed to commit Direct Boot motion sensitivity state"
        }

        return load()
    }

    /**
     * Persist one of the user-facing Low, Medium, or High presets.
     */
    @Synchronized
    fun setPreset(
        preset: Preset,
    ): State =
        setSensitivity(
            preset.sensitivity,
        )

    /**
     * Persist the action performed when a snatch candidate is detected.
     */
    @Synchronized
    fun setAction(
        action: Action,
    ): State {
        val committed =
            prefs.edit()
                .putString(
                    KEY_ACTION,
                    action.name,
                )
                .commit()

        check(committed) {
            "Failed to commit Direct Boot motion action state"
        }

        return load()
    }

    companion object {
        private const val FILE_NAME =
            "runtime_state_motion"

        private const val KEY_ENABLED =
            "motion.enabled"

        private const val KEY_SENSITIVITY =
            "motion.sensitivity"

        private const val KEY_ACTION =
            "motion.action"

        private const val DEFAULT_ENABLED =
            false

        val DEFAULT_ACTION =
            Action.LOCK

        const val MIN_SENSITIVITY =
            0

        const val LOW_SENSITIVITY =
            0

        const val MEDIUM_SENSITIVITY =
            50

        const val HIGH_SENSITIVITY =
            100

        const val MAX_SENSITIVITY =
            100

        const val DEFAULT_SENSITIVITY =
            MEDIUM_SENSITIVITY

        /*
         * Hardware-validated sensitivity endpoints.
         *
         * Medium remains the established baseline.
         *
         * Low is intentionally harder to trigger than Medium:
         * higher acceleration threshold at the same duration.
         *
         * High is intentionally easier to trigger than Medium:
         * lower acceleration threshold and shorter duration.
         */
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

        /**
         * Convert the 0-100 user-facing sensitivity scale into detector
         * parameters.
         *
         * Interpolation is piecewise:
         *
         *     Low -> Medium
         *     Medium -> High
         *
         * This guarantees that sensitivity 50 maps exactly to the currently
         * hardware-proven Medium profile.
         */
        fun profileForSensitivity(
            sensitivity: Int,
        ): Profile {
            val clamped =
                sensitivity.coerceIn(
                    MIN_SENSITIVITY,
                    MAX_SENSITIVITY,
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
