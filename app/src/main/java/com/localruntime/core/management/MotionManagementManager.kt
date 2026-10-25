package com.localruntime.core.management

import android.content.Context
import com.localruntime.core.admin.DeviceAdminManager
import com.localruntime.core.admin.HardenedPolicyManager
import com.localruntime.core.log.SecLog
import com.localruntime.core.motion.MotionSensitivityStore
import com.localruntime.core.motionbridge.MotionHelperClient
import com.localruntime.core.wipe.WipeGateway
import com.runtimebridge.api.ManagementProtocol
import com.runtimebridge.helper.HelperProtocol

/**
 * Core-owned Motion Protection configuration authority.
 *
 * Core persists the authoritative:
 *
 * - enabled state;
 * - sensitivity;
 * - Lock/Reboot action.
 *
 * Accelerometer access and motion classification run in the isolated Motion
 * Helper UID. The helper never receives or persists the privileged action.
 */
class MotionManagementManager(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val store =
        MotionSensitivityStore(
            appContext,
        )

    private val wipeGateway =
        WipeGateway(
            appContext,
        )

    private val helperClient =
        MotionHelperClient.get(
            appContext,
        )

    fun isEnabled(): Boolean =
        store
            .load()
            .enabled

    /**
     * Report the effective isolated-helper state while preserving Core's
     * configured state as authoritative.
     *
     * If the helper connection is still being established, re-submit the
     * newest Core configuration. MotionHelperClient collapses queued updates
     * so stale values are never replayed.
     */
    fun getOperationalState(): Int {
        val current =
            store.load()

        if (!current.enabled) {
            return ManagementProtocol
                .MOTION_OPERATIONAL_DISABLED
        }

        val helperState =
            helperClient
                .getOperationalState()

        if (helperState == null) {
            val dispatch =
                helperClient
                    .applyConfiguration(
                        enabled = true,
                        sensitivity =
                            current.sensitivity,
                    )

            return when (
                dispatch
            ) {
                MotionHelperClient
                    .ConfigurationDispatch
                    .APPLIED,
                MotionHelperClient
                    .ConfigurationDispatch
                    .QUEUED ->
                    ManagementProtocol
                        .MOTION_OPERATIONAL_STARTING

                else ->
                    ManagementProtocol
                        .MOTION_OPERATIONAL_FAILED
            }
        }

        return mapHelperOperationalState(
            helperState,
        )
    }

    fun getSensitivity(): Int =
        store
            .load()
            .sensitivity

    fun getMinSensitivity(): Int =
        MotionSensitivityStore
            .MIN_SENSITIVITY

    fun getMaxSensitivity(): Int =
        MotionSensitivityStore
            .MAX_SENSITIVITY

    fun getAction(): Int =
        when (
            store
                .load()
                .action
        ) {
            MotionSensitivityStore.Action.LOCK ->
                ManagementProtocol
                    .MOTION_ACTION_LOCK

            MotionSensitivityStore.Action.REBOOT ->
                ManagementProtocol
                    .MOTION_ACTION_REBOOT
        }

    fun isRebootAvailable(): Boolean =
        DeviceAdminManager(
            appContext,
        ).isDeviceOwner()

    fun setEnabled(
        enabled: Boolean,
    ): Int =
        try {
            if (enabled) {
                enable()
            } else {
                disable()
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Motion Protection setEnabled($enabled) failed",
                throwable,
            )

            ManagementProtocol
                .MOTION_RESULT_FAILED
        }

    /**
     * Persist a new Core-authoritative sensitivity and synchronize the isolated
     * helper when Motion Protection is active.
     *
     * If synchronization cannot even be queued, Core restores the previous
     * sensitivity and makes a best-effort attempt to restore that configuration
     * in the helper as well.
     */
    fun setSensitivity(
        sensitivity: Int,
    ): Int {
        if (
            sensitivity <
            MotionSensitivityStore.MIN_SENSITIVITY ||
            sensitivity >
            MotionSensitivityStore.MAX_SENSITIVITY
        ) {
            return ManagementProtocol
                .MOTION_RESULT_INVALID_SENSITIVITY
        }

        return try {
            val previous =
                store.load()

            val updated =
                store.setSensitivity(
                    sensitivity,
                )

            if (!updated.enabled) {
                return ManagementProtocol
                    .MOTION_RESULT_OK
            }

            when (
                helperClient
                    .applyConfiguration(
                        enabled = true,
                        sensitivity =
                            updated.sensitivity,
                    )
            ) {
                MotionHelperClient
                    .ConfigurationDispatch
                    .APPLIED,
                MotionHelperClient
                    .ConfigurationDispatch
                    .QUEUED ->
                    ManagementProtocol
                        .MOTION_RESULT_OK

                else -> {
                    val rolledBack =
                        store.setSensitivity(
                            previous.sensitivity,
                        )

                    /*
                     * Best effort only. Core's rolled-back value is
                     * authoritative even if the helper is temporarily absent.
                     */
                    helperClient
                        .applyConfiguration(
                            enabled =
                                rolledBack.enabled,
                            sensitivity =
                                rolledBack.sensitivity,
                        )

                    ManagementProtocol
                        .MOTION_RESULT_FAILED
                }
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Motion Protection sensitivity change failed",
                throwable,
            )

            ManagementProtocol
                .MOTION_RESULT_FAILED
        }
    }

    /**
     * Persist the privileged response action in Core only.
     *
     * The helper deliberately does not receive this setting. When it reports a
     * candidate, MotionCandidateHandler re-reads this authoritative Core store
     * immediately before Lock/Reboot execution.
     */
    fun setAction(
        action: Int,
    ): Int {
        if (
            action ==
            ManagementProtocol.MOTION_ACTION_REBOOT &&
            !isRebootAvailable()
        ) {
            return ManagementProtocol
                .MOTION_RESULT_REBOOT_REQUIRES_DEVICE_OWNER
        }

        val mappedAction =
            when (
                action
            ) {
                ManagementProtocol
                    .MOTION_ACTION_LOCK ->
                    MotionSensitivityStore
                        .Action
                        .LOCK

                ManagementProtocol
                    .MOTION_ACTION_REBOOT ->
                    MotionSensitivityStore
                        .Action
                        .REBOOT

                else ->
                    return ManagementProtocol
                        .MOTION_RESULT_INVALID_ACTION
            }

        return try {
            store.setAction(
                mappedAction,
            )

            ManagementProtocol
                .MOTION_RESULT_OK
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Motion Protection action change failed",
                throwable,
            )

            ManagementProtocol
                .MOTION_RESULT_FAILED
        }
    }

    private fun enable(): Int {
        /*
         * Lock remains the safe baseline response, so Motion Protection still
         * requires active device-administration authority.
         */
        if (!wipeGateway.isAdminActive()) {
            return ManagementProtocol
                .MOTION_RESULT_ADMIN_INACTIVE
        }

        val current =
            store.load()

        /*
         * An already-enabled Core state is also a synchronization request.
         * This is useful after helper replacement or process recovery.
         */
        if (current.enabled) {
            HardenedPolicyManager(
                appContext,
            ).applyRequiredPolicies()

            return when (
                helperClient
                    .applyConfiguration(
                        enabled = true,
                        sensitivity =
                            current.sensitivity,
                    )
            ) {
                MotionHelperClient
                    .ConfigurationDispatch
                    .APPLIED,
                MotionHelperClient
                    .ConfigurationDispatch
                    .QUEUED ->
                    ManagementProtocol
                        .MOTION_RESULT_OK

                else ->
                    ManagementProtocol
                        .MOTION_RESULT_FAILED
            }
        }

        /*
         * Core becomes authoritative first. If the helper cannot accept or
         * queue this state, roll Core back to disabled.
         */
        val enabledState =
            store.setEnabled(
                true,
            )

        HardenedPolicyManager(
            appContext,
        ).applyRequiredPolicies()

        val dispatch =
            helperClient
                .applyConfiguration(
                    enabled = true,
                    sensitivity =
                        enabledState.sensitivity,
                )

        if (
            dispatch ==
            MotionHelperClient
                .ConfigurationDispatch
                .APPLIED ||
            dispatch ==
            MotionHelperClient
                .ConfigurationDispatch
                .QUEUED
        ) {
            return ManagementProtocol
                .MOTION_RESULT_OK
        }

        val rolledBack =
            store.setEnabled(
                false,
            )

        /*
         * A race may have delivered the enable immediately before an IPC
         * failure became visible. Explicitly queue disabled as a best effort.
         */
        helperClient
            .applyConfiguration(
                enabled = false,
                sensitivity =
                    rolledBack.sensitivity,
            )

        HardenedPolicyManager(
            appContext,
        ).applyRequiredPolicies()

        return ManagementProtocol
            .MOTION_RESULT_FAILED
    }

    private fun disable(): Int {
        val current =
            store.load()

        /*
         * Persist disabled first. From this point onward Core rejects every
         * helper candidate even if the helper process has not stopped yet.
         */
        val disabledState =
            if (current.enabled) {
                store.setEnabled(
                    false,
                )
            } else {
                current
            }

        HardenedPolicyManager(
            appContext,
        ).applyRequiredPolicies()

        /*
         * Disabling is fail-safe from Core's perspective. Even if the helper is
         * temporarily unreachable, MotionCandidateHandler will reject any
         * later candidate because Core is authoritative and now disabled.
         */
        val dispatch =
            helperClient
                .applyConfiguration(
                    enabled = false,
                    sensitivity =
                        disabledState.sensitivity,
                )

        if (
            dispatch !=
            MotionHelperClient
                .ConfigurationDispatch
                .APPLIED &&
            dispatch !=
            MotionHelperClient
                .ConfigurationDispatch
                .QUEUED
        ) {
            SecLog.w(
                TAG,
                "Motion Helper disable synchronization deferred: $dispatch",
            )
        }

        return ManagementProtocol
            .MOTION_RESULT_OK
    }

    private fun mapHelperOperationalState(
        helperState: Int,
    ): Int =
        when (
            helperState
        ) {
            HelperProtocol
                .MOTION_STATE_DISABLED ->
                ManagementProtocol
                    .MOTION_OPERATIONAL_DISABLED

            HelperProtocol
                .MOTION_STATE_STARTING ->
                ManagementProtocol
                    .MOTION_OPERATIONAL_STARTING

            HelperProtocol
                .MOTION_STATE_ACTIVE ->
                ManagementProtocol
                    .MOTION_OPERATIONAL_ACTIVE

            HelperProtocol
                .MOTION_STATE_SENSOR_PERMISSION_REQUIRED ->
                ManagementProtocol
                    .MOTION_OPERATIONAL_SENSOR_PERMISSION_REQUIRED

            HelperProtocol
                .MOTION_STATE_ACCELEROMETER_UNAVAILABLE ->
                ManagementProtocol
                    .MOTION_OPERATIONAL_ACCELEROMETER_UNAVAILABLE

            HelperProtocol
                .MOTION_STATE_FAILED ->
                ManagementProtocol
                    .MOTION_OPERATIONAL_FAILED

            else ->
                ManagementProtocol
                    .MOTION_OPERATIONAL_FAILED
        }

    companion object {
        private const val TAG =
            "MotionManagement"
    }
}
