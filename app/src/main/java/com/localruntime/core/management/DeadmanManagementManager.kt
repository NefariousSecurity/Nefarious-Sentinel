package com.localruntime.core.management

import android.content.Context
import android.os.SystemClock
import com.localruntime.core.deadman.DeadmanAlarmScheduler
import com.localruntime.core.deadman.DeadmanDirectBootStore
import com.localruntime.core.deadman.DeadmanStateEvaluator
import com.localruntime.core.deadman.DeadmanUnlockService
import com.localruntime.core.log.SecLog
import com.localruntime.core.wipe.WipeGateway
import com.runtimebridge.api.ManagementProtocol
import kotlin.math.max

/**
 * Core-owned Dead Man configuration interface.
 *
 * Security enforcement uses elapsedRealtime().
 *
 * getExpiryWallClockMs() is retained only for Controller protocol
 * compatibility. Its result is a projected display timestamp derived from the
 * current monotonic remaining interval. It is never used for enforcement.
 */
class DeadmanManagementManager(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val store =
        DeadmanDirectBootStore(
            appContext,
        )

    private val wipeGateway =
        WipeGateway(
            appContext,
        )

    fun getState(): Int {
        val snapshot =
            snapshot()

        return when (
            snapshot.classification
        ) {
            DeadmanStateEvaluator
                .Classification.Disabled ->
                ManagementProtocol
                    .DEADMAN_STATE_DISABLED

            DeadmanStateEvaluator
                .Classification.Invalid ->
                ManagementProtocol
                    .DEADMAN_STATE_INVALID

            DeadmanStateEvaluator
                .Classification.Expired ->
                ManagementProtocol
                    .DEADMAN_STATE_EXPIRED

            DeadmanStateEvaluator
                .Classification.Armed ->
                ManagementProtocol
                    .DEADMAN_STATE_ARMED
        }
    }

    fun isEnabled(): Boolean =
        store
            .loadCurrent()
            .enabled

    fun getIntervalMs(): Long =
        store
            .loadCurrent()
            .intervalMs

    /**
     * Display-only compatibility value.
     *
     * This projected wall-clock timestamp is not persisted and is not used by
     * any security decision.
     */
    fun getExpiryWallClockMs(): Long {
        val remaining =
            getRemainingMs()

        if (remaining <= 0L) {
            return 0L
        }

        val nowWallClockMs =
            System.currentTimeMillis()

        if (
            nowWallClockMs >
            Long.MAX_VALUE -
                remaining
        ) {
            return Long.MAX_VALUE
        }

        return nowWallClockMs +
            remaining
    }

    fun getRemainingMs(): Long {
        val snapshot =
            snapshot()

        if (
            snapshot.classification ==
            DeadmanStateEvaluator
                .Classification.Disabled ||
            snapshot.classification ==
            DeadmanStateEvaluator
                .Classification.Invalid
        ) {
            return 0L
        }

        return max(
            0L,
            snapshot.state
                .remainingSameBootMs(
                    nowElapsedRealtimeMs =
                        snapshot
                            .nowElapsedRealtimeMs,
                    currentBootCount =
                        snapshot
                            .currentBootCount,
                ),
        )
    }

    fun getMinIntervalMs(): Long =
        DeadmanDirectBootStore
            .MIN_INTERVAL_MS

    fun getMaxIntervalMs(): Long =
        DeadmanDirectBootStore
            .MAX_INTERVAL_MS

    fun setEnabled(
        enabled: Boolean,
    ): Int {
        return try {
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
                "Dead Man setEnabled($enabled) failed",
                throwable,
            )

            ManagementProtocol
                .DEADMAN_RESULT_FAILED
        }
    }

    fun setIntervalMs(
        intervalMs: Long,
    ): Int {
        if (
            intervalMs <
            DeadmanDirectBootStore.MIN_INTERVAL_MS ||
            intervalMs >
            DeadmanDirectBootStore.MAX_INTERVAL_MS
        ) {
            return ManagementProtocol
                .DEADMAN_RESULT_INVALID_INTERVAL
        }

        return try {
            val beforeSnapshot =
                snapshot()

            val before =
                beforeSnapshot.state

            if (!before.enabled) {
                store.setIntervalMs(
                    intervalMs =
                        intervalMs,
                )

                return ManagementProtocol
                    .DEADMAN_RESULT_OK
            }

            when (
                beforeSnapshot.classification
            ) {
                DeadmanStateEvaluator
                    .Classification.Disabled ->
                    return ManagementProtocol
                        .DEADMAN_RESULT_DISABLED

                DeadmanStateEvaluator
                    .Classification.Invalid ->
                    return ManagementProtocol
                        .DEADMAN_RESULT_INVALID_STATE

                DeadmanStateEvaluator
                    .Classification.Expired ->
                    return ManagementProtocol
                        .DEADMAN_RESULT_EXPIRED

                DeadmanStateEvaluator
                    .Classification.Armed -> {
                    // Continue.
                }
            }

            val nowElapsedRealtimeMs =
                beforeSnapshot
                    .nowElapsedRealtimeMs

            check(
                nowElapsedRealtimeMs <=
                    Long.MAX_VALUE -
                    intervalMs,
            ) {
                "Dead Man deadline overflow"
            }

            val proposedExpiry =
                nowElapsedRealtimeMs +
                    intervalMs

            if (
                proposedExpiry <
                before.expiryElapsedRealtimeMs
            ) {
                /*
                 * Moving enforcement earlier:
                 * arm the earlier deadline first.
                 */
                DeadmanAlarmScheduler.arm(
                    context = appContext,
                    expiryElapsedRealtimeMs =
                        proposedExpiry,
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                )

                store.setIntervalMs(
                    intervalMs =
                        intervalMs,
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                    nowWallClockMs =
                        beforeSnapshot
                            .nowWallClockMs,
                    currentBootCount =
                        beforeSnapshot
                            .currentBootCount,
                )
            } else {
                /*
                 * Moving enforcement later:
                 * persist first, then move the alarm.
                 */
                val persisted =
                    store.setIntervalMs(
                        intervalMs =
                            intervalMs,
                        nowElapsedRealtimeMs =
                            nowElapsedRealtimeMs,
                        nowWallClockMs =
                            beforeSnapshot
                                .nowWallClockMs,
                        currentBootCount =
                            beforeSnapshot
                                .currentBootCount,
                    )

                DeadmanAlarmScheduler.arm(
                    context = appContext,
                    expiryElapsedRealtimeMs =
                        persisted
                            .expiryElapsedRealtimeMs,
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                )
            }

            ManagementProtocol
                .DEADMAN_RESULT_OK
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Dead Man interval change failed",
                throwable,
            )

            ManagementProtocol
                .DEADMAN_RESULT_FAILED
        }
    }

    fun checkIn(): Int {
        return try {
            val beforeSnapshot =
                snapshot()

            when (
                beforeSnapshot.classification
            ) {
                DeadmanStateEvaluator
                    .Classification.Disabled ->
                    return ManagementProtocol
                        .DEADMAN_RESULT_DISABLED

                DeadmanStateEvaluator
                    .Classification.Invalid ->
                    return ManagementProtocol
                        .DEADMAN_RESULT_INVALID_STATE

                DeadmanStateEvaluator
                    .Classification.Expired ->
                    return ManagementProtocol
                        .DEADMAN_RESULT_EXPIRED

                DeadmanStateEvaluator
                    .Classification.Armed -> {
                    // Continue.
                }
            }

            val after =
                store.checkIn(
                    nowElapsedRealtimeMs =
                        beforeSnapshot
                            .nowElapsedRealtimeMs,
                    nowWallClockMs =
                        beforeSnapshot
                            .nowWallClockMs,
                    currentBootCount =
                        beforeSnapshot
                            .currentBootCount,
                )

            DeadmanAlarmScheduler.arm(
                context = appContext,
                expiryElapsedRealtimeMs =
                    after
                        .expiryElapsedRealtimeMs,
                nowElapsedRealtimeMs =
                    beforeSnapshot
                        .nowElapsedRealtimeMs,
            )

            ManagementProtocol
                .DEADMAN_RESULT_OK
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Dead Man manual check-in failed",
                throwable,
            )

            ManagementProtocol
                .DEADMAN_RESULT_FAILED
        }
    }

    private fun enable(): Int {
        if (!wipeGateway.hasWipeAuthority()) {
            return ManagementProtocol
                .DEADMAN_RESULT_ADMIN_INACTIVE
        }

        val currentSnapshot =
            snapshot()

        val current =
            currentSnapshot.state

        if (current.enabled) {
            return when (
                currentSnapshot.classification
            ) {
                DeadmanStateEvaluator
                    .Classification.Armed ->
                    ManagementProtocol
                        .DEADMAN_RESULT_OK

                DeadmanStateEvaluator
                    .Classification.Expired ->
                    ManagementProtocol
                        .DEADMAN_RESULT_EXPIRED

                DeadmanStateEvaluator
                    .Classification.Invalid,
                DeadmanStateEvaluator
                    .Classification.Disabled ->
                    ManagementProtocol
                        .DEADMAN_RESULT_INVALID_STATE
            }
        }

        val nowElapsedRealtimeMs =
            currentSnapshot
                .nowElapsedRealtimeMs

        check(
            current.intervalMs in
                DeadmanDirectBootStore.MIN_INTERVAL_MS..
                DeadmanDirectBootStore.MAX_INTERVAL_MS,
        ) {
            "Dead Man interval outside permitted range"
        }

        check(
            nowElapsedRealtimeMs <=
                Long.MAX_VALUE -
                current.intervalMs,
        ) {
            "Dead Man deadline overflow"
        }

        val proposedExpiry =
            nowElapsedRealtimeMs +
                current.intervalMs

        /*
         * Alarm first, enabled state second.
         */
        DeadmanAlarmScheduler.arm(
            context = appContext,
            expiryElapsedRealtimeMs =
                proposedExpiry,
            nowElapsedRealtimeMs =
                nowElapsedRealtimeMs,
        )

        val enabledState =
            try {
                store.setEnabled(
                    enabled = true,
                    nowElapsedRealtimeMs =
                        nowElapsedRealtimeMs,
                    nowWallClockMs =
                        currentSnapshot
                            .nowWallClockMs,
                    currentBootCount =
                        currentSnapshot
                            .currentBootCount,
                )
            } catch (
                throwable: Throwable,
            ) {
                runCatching {
                    DeadmanAlarmScheduler.cancel(
                        appContext,
                    )
                }

                throw throwable
            }

        check(
            enabledState
                .expiryElapsedRealtimeMs ==
                proposedExpiry,
        ) {
            "Persisted Dead Man expiry differs " +
                "from armed expiry"
        }

        try {
            DeadmanUnlockService.start(
                appContext,
            )
        } catch (
            throwable: Throwable,
        ) {
            store.setEnabled(
                false,
            )

            runCatching {
                DeadmanAlarmScheduler.cancel(
                    appContext,
                )
            }

            throw throwable
        }

        return ManagementProtocol
            .DEADMAN_RESULT_OK
    }

    private fun disable(): Int {
        val currentSnapshot =
            snapshot()

        if (
            currentSnapshot.classification ==
            DeadmanStateEvaluator
                .Classification.Expired
        ) {
            return ManagementProtocol
                .DEADMAN_RESULT_EXPIRED
        }

        /*
         * Persist disabled state before cancelling the stale alarm.
         */
        store.setEnabled(
            false,
        )

        runCatching {
            DeadmanAlarmScheduler.cancel(
                appContext,
            )
        }.onFailure {
            SecLog.e(
                TAG,
                "Failed to cancel Dead Man " +
                    "alarm after disabling",
                it,
            )
        }

        runCatching {
            DeadmanUnlockService.stop(
                appContext,
            )
        }.onFailure {
            SecLog.e(
                TAG,
                "Failed to stop Dead Man " +
                    "unlock monitor after disabling",
                it,
            )
        }

        return ManagementProtocol
            .DEADMAN_RESULT_OK
    }

    private fun snapshot(): Snapshot {
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

        return Snapshot(
            state = state,
            classification =
                classification,
            nowElapsedRealtimeMs =
                nowElapsedRealtimeMs,
            nowWallClockMs =
                nowWallClockMs,
            currentBootCount =
                currentBootCount,
        )
    }

    private data class Snapshot(
        val state:
            DeadmanDirectBootStore.State,
        val classification:
            DeadmanStateEvaluator.Classification,
        val nowElapsedRealtimeMs: Long,
        val nowWallClockMs: Long,
        val currentBootCount: Int,
    )

    companion object {
        private const val TAG =
            "DeadmanManagement"
    }
}
