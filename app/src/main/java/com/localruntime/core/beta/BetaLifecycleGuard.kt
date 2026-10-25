package com.localruntime.core.beta

import android.content.Context
import com.localruntime.core.BuildConfig

/**
 * Authoritative Core-side Beta lifecycle state.
 *
 * State is stored in Device Protected Storage so expiry remains authoritative
 * before first unlock and across reboot.
 *
 * Once an expired Beta build is observed, destructive suppression is latched.
 * A later build must explicitly clear that latch through an authenticated
 * re-arm operation. Merely installing a newer APK must never restore a stale
 * destructive trigger automatically.
 */
class BetaLifecycleGuard(
    context: Context,
) {

    private val prefs =
        context.applicationContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )

    fun snapshot(
        nowWallClockMs: Long =
            System.currentTimeMillis(),
    ): Snapshot =
        synchronized(
            lock,
        ) {
            try {
                val storedMaxWallClockMs =
                    prefs.getLong(
                        KEY_MAX_OBSERVED_WALL_CLOCK_MS,
                        0L,
                    )

                val storedRearmRequired =
                    prefs.getBoolean(
                        KEY_REARM_REQUIRED,
                        false,
                    )

                /*
                 * Keep exact high-water state in process memory between
                 * persistent checkpoints. This prevents a one-second
                 * Controller refresh from causing a one-second flash write.
                 */
                val observedFloor =
                    maxOf(
                        storedMaxWallClockMs,
                        processMaxObservedWallClockMs,
                    )

                val evaluation =
                    BetaLifecyclePolicy.evaluate(
                        betaBuild =
                            BuildConfig.BETA_BUILD,
                        expiryEpochMs =
                            BuildConfig.BETA_EXPIRY_EPOCH_MS,
                        nowWallClockMs =
                            nowWallClockMs,
                        maxObservedWallClockMs =
                            observedFloor,
                        rearmRequired =
                            storedRearmRequired,
                    )

                processMaxObservedWallClockMs =
                    maxOf(
                        processMaxObservedWallClockMs,
                        evaluation.effectiveWallClockMs,
                    )

                val needsExpiryLatch =
                    evaluation.shouldLatchRearm

                val needsWallClockUpdate =
                    BetaLifecyclePolicy
                        .shouldPersistHighWater(
                            storedWallClockMs =
                                storedMaxWallClockMs,
                            effectiveWallClockMs =
                                evaluation
                                    .effectiveWallClockMs,
                            /*
                             * Crossing expiry must persist both the latch and
                             * the exact high-water observation immediately.
                             */
                            force =
                                needsExpiryLatch,
                        )

                var persistenceHealthy =
                    true

                if (
                    needsWallClockUpdate ||
                    needsExpiryLatch
                ) {
                    val editor =
                        prefs.edit()

                    if (needsWallClockUpdate) {
                        editor.putLong(
                            KEY_MAX_OBSERVED_WALL_CLOCK_MS,
                            evaluation
                                .effectiveWallClockMs,
                        )
                    }

                    if (needsExpiryLatch) {
                        editor.putBoolean(
                            KEY_REARM_REQUIRED,
                            true,
                        )

                        editor.putInt(
                            KEY_EXPIRED_VERSION_CODE,
                            BuildConfig.VERSION_CODE,
                        )
                    }

                    persistenceHealthy =
                        editor.commit()
                }

                if (!persistenceHealthy) {
                    return@synchronized Snapshot(
                        betaBuild =
                            BuildConfig.BETA_BUILD,
                        currentBuildExpired =
                            evaluation.currentBuildExpired,
                        rearmRequired =
                            true,
                        destructiveSuppressed =
                            true,
                        reason =
                            SuppressionReason.STATE_ERROR,
                        effectiveWallClockMs =
                            evaluation.effectiveWallClockMs,
                        remainingMs =
                            evaluation.remainingMs,
                        expiredVersionCode =
                            prefs.getInt(
                                KEY_EXPIRED_VERSION_CODE,
                                0,
                            ),
                        stateHealthy =
                            false,
                    )
                }

                val effectiveRearmRequired =
                    storedRearmRequired ||
                        needsExpiryLatch

                val reason =
                    when {
                        evaluation.currentBuildExpired ->
                            SuppressionReason
                                .CURRENT_BUILD_EXPIRED

                        effectiveRearmRequired ->
                            SuppressionReason
                                .REARM_REQUIRED

                        else ->
                            SuppressionReason.NONE
                    }

                Snapshot(
                    betaBuild =
                        BuildConfig.BETA_BUILD,
                    currentBuildExpired =
                        evaluation.currentBuildExpired,
                    rearmRequired =
                        effectiveRearmRequired,
                    destructiveSuppressed =
                        evaluation
                            .destructiveSuppressed,
                    reason =
                        reason,
                    effectiveWallClockMs =
                        evaluation
                            .effectiveWallClockMs,
                    remainingMs =
                        evaluation.remainingMs,
                    expiredVersionCode =
                        prefs.getInt(
                            KEY_EXPIRED_VERSION_CODE,
                            0,
                        ),
                    stateHealthy =
                        true,
                )
            } catch (
            throwable: Throwable,
            ) {
                /*
                 * Lifecycle-state failure is deliberately fail-safe against
                 * destructive action. A storage/read failure must never result
                 * in Sentinel assuming that destructive operations are allowed.
                 */
                Snapshot(
                    betaBuild =
                        BuildConfig.BETA_BUILD,
                    currentBuildExpired =
                        false,
                    rearmRequired =
                        true,
                    destructiveSuppressed =
                        true,
                    reason =
                        SuppressionReason.STATE_ERROR,
                    effectiveWallClockMs =
                        nowWallClockMs,
                    remainingMs =
                        0L,
                    expiredVersionCode =
                        0,
                    stateHealthy =
                        false,
                )
            }
        }

    fun rearmAfterUpgrade():
        RearmResult =
        synchronized(
            lock,
        ) {
            val current =
                snapshot()

            if (!current.stateHealthy) {
                return@synchronized RearmResult.STATE_ERROR
            }

            if (current.currentBuildExpired) {
                return@synchronized RearmResult.CURRENT_BUILD_EXPIRED
            }

            if (!current.rearmRequired) {
                return@synchronized RearmResult.NOT_REQUIRED
            }

            if (
                !BetaLifecyclePolicy.canRearm(
                    currentBuildExpired =
                        current.currentBuildExpired,
                    rearmRequired =
                        current.rearmRequired,
                    currentVersionCode =
                        BuildConfig.VERSION_CODE,
                    expiredVersionCode =
                        current.expiredVersionCode,
                    stateHealthy =
                        current.stateHealthy,
                )
            ) {
                return@synchronized RearmResult.VERSION_NOT_NEWER
            }

            val committed =
                runCatching {
                    prefs.edit()
                        .putBoolean(
                            KEY_REARM_REQUIRED,
                            false,
                        )
                        .remove(
                            KEY_EXPIRED_VERSION_CODE,
                        )
                        .commit()
                }.getOrDefault(
                    false,
                )

            if (!committed) {
                return@synchronized RearmResult.FAILED
            }

            /*
             * Verify the persisted state through the same authoritative path
             * used by destructive-action checks. A successful re-arm must
             * never be reported unless suppression has genuinely cleared.
             */
            val verified =
                snapshot()

            if (
                !verified.stateHealthy ||
                verified.currentBuildExpired ||
                verified.rearmRequired ||
                verified.destructiveSuppressed
            ) {
                return@synchronized RearmResult.FAILED
            }

            RearmResult.OK
        }

    enum class RearmResult {
        OK,
        CURRENT_BUILD_EXPIRED,
        NOT_REQUIRED,
        VERSION_NOT_NEWER,
        STATE_ERROR,
        FAILED,
    }

    data class Snapshot(
        val betaBuild: Boolean,
        val currentBuildExpired: Boolean,
        val rearmRequired: Boolean,
        val destructiveSuppressed: Boolean,
        val reason: SuppressionReason,
        val effectiveWallClockMs: Long,
        val remainingMs: Long,
        val expiredVersionCode: Int,
        val stateHealthy: Boolean,
    )

    enum class SuppressionReason {
        NONE,
        CURRENT_BUILD_EXPIRED,
        REARM_REQUIRED,
        STATE_ERROR,
    }

    private companion object {
        private const val PREFS_NAME =
            "sentinel_beta_lifecycle"

        private const val KEY_MAX_OBSERVED_WALL_CLOCK_MS =
            "max_observed_wall_clock_ms"

        private const val KEY_REARM_REQUIRED =
            "rearm_required"

        private const val KEY_EXPIRED_VERSION_CODE =
            "expired_version_code"

        private val lock =
            Any()

        private var processMaxObservedWallClockMs =
            0L
    }
}
