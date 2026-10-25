package com.localruntime.core.deadman

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeadmanDirectBootStoreInstrumentedTest {

    @Test
    fun enabledDeadlineUsesElapsedRealtimeNotWallClock() {
        withCleanStore { store, _ ->
            val bootCount = 100
            val elapsedNow = 10_000L
            val wallNow = 1_800_000_000_000L

            val enabled =
                store.setEnabled(
                    enabled = true,
                    nowElapsedRealtimeMs =
                        elapsedNow,
                    nowWallClockMs =
                        wallNow,
                    currentBootCount =
                        bootCount,
                )

            assertTrue(
                enabled.enabled,
            )

            assertEquals(
                elapsedNow +
                    DeadmanDirectBootStore
                        .DEFAULT_INTERVAL_MS,
                enabled.expiryElapsedRealtimeMs,
            )

            /*
             * Roll wall clock backwards by 30 days while elapsed time advances
             * normally. Remaining monotonic time must be unchanged except for
             * the elapsed-time progression.
             */
            val laterElapsed =
                elapsedNow + 60_000L

            val rolledBackWall =
                wallNow -
                    30L * 24L * 60L * 60L * 1000L

            val afterRollback =
                store.loadCurrent(
                    nowElapsedRealtimeMs =
                        laterElapsed,
                    nowWallClockMs =
                        rolledBackWall,
                    currentBootCount =
                        bootCount,
                )

            val expectedRemaining =
                DeadmanDirectBootStore
                    .DEFAULT_INTERVAL_MS -
                    60_000L

            assertEquals(
                expectedRemaining,
                afterRollback
                    .remainingSameBootMs(
                        nowElapsedRealtimeMs =
                            laterElapsed,
                        currentBootCount =
                            bootCount,
                    ),
            )
        }
    }

    @Test
    fun forwardWallClockDoesNotExpireSameBootDeadlineEarly() {
        withCleanStore { store, _ ->
            val bootCount = 101
            val elapsedNow = 20_000L
            val wallNow = 1_800_000_000_000L

            store.setEnabled(
                enabled = true,
                nowElapsedRealtimeMs =
                    elapsedNow,
                nowWallClockMs =
                    wallNow,
                currentBootCount =
                    bootCount,
            )

            val laterElapsed =
                elapsedNow + 60_000L

            val farFutureWall =
                wallNow +
                    365L * 24L * 60L * 60L * 1000L

            val state =
                store.loadCurrent(
                    nowElapsedRealtimeMs =
                        laterElapsed,
                    nowWallClockMs =
                        farFutureWall,
                    currentBootCount =
                        bootCount,
                )

            assertFalse(
                state.isExpiredSameBoot(
                    nowElapsedRealtimeMs =
                        laterElapsed,
                    currentBootCount =
                        bootCount,
                ),
            )
        }
    }

    @Test
    fun rebootRecoveryNeverIncreasesRemainingTime() {
        withCleanStore { store, _ ->
            val originalBoot = 200
            val newBoot = 201

            val elapsedNow = 50_000L
            val wallNow = 1_800_000_000_000L

            val enabled =
                store.setEnabled(
                    enabled = true,
                    nowElapsedRealtimeMs =
                        elapsedNow,
                    nowWallClockMs =
                        wallNow,
                    currentBootCount =
                        originalBoot,
                )

            val remainingBeforeReboot =
                enabled.remainingSameBootMs(
                    nowElapsedRealtimeMs =
                        elapsedNow,
                    currentBootCount =
                        originalBoot,
                )

            /*
             * New boot has a small elapsedRealtime value.
             */
            val newBootElapsed =
                5_000L

            val afterReboot =
                store.loadCurrent(
                    nowElapsedRealtimeMs =
                        newBootElapsed,
                    nowWallClockMs =
                        wallNow + 120_000L,
                    currentBootCount =
                        newBoot,
                )

            val remainingAfterReboot =
                afterReboot
                    .remainingSameBootMs(
                        nowElapsedRealtimeMs =
                            newBootElapsed,
                        currentBootCount =
                            newBoot,
                    )

            assertTrue(
                remainingAfterReboot <=
                    remainingBeforeReboot,
            )
        }
    }

    @Test
    fun backwardWallClockAcrossRebootDoesNotGrantExtraTime() {
        withCleanStore { store, _ ->
            val originalBoot = 300
            val newBoot = 301

            val elapsedNow = 80_000L
            val wallNow = 1_800_000_000_000L

            val enabled =
                store.setEnabled(
                    enabled = true,
                    nowElapsedRealtimeMs =
                        elapsedNow,
                    nowWallClockMs =
                        wallNow,
                    currentBootCount =
                        originalBoot,
                )

            val remainingBeforeReboot =
                enabled.remainingAtAnchorMs()

            val newBootElapsed =
                10_000L

            /*
             * Simulate a large backwards date change before/through reboot.
             */
            val rolledBackWall =
                wallNow -
                    90L * 24L * 60L * 60L * 1000L

            val recovered =
                store.loadCurrent(
                    nowElapsedRealtimeMs =
                        newBootElapsed,
                    nowWallClockMs =
                        rolledBackWall,
                    currentBootCount =
                        newBoot,
                )

            val remainingAfterReboot =
                recovered
                    .remainingSameBootMs(
                        nowElapsedRealtimeMs =
                            newBootElapsed,
                        currentBootCount =
                            newBoot,
                    )

            assertTrue(
                remainingAfterReboot <
                    remainingBeforeReboot,
            )
        }
    }

    @Test
    fun repeatedRebootDoesNotIncreaseRemainingTime() {
        withCleanStore { store, _ ->
            val wallNow = 1_800_000_000_000L

            val first =
                store.setEnabled(
                    enabled = true,
                    nowElapsedRealtimeMs = 10_000L,
                    nowWallClockMs = wallNow,
                    currentBootCount = 400,
                )

            val remaining1 =
                first.remainingAtAnchorMs()

            val second =
                store.loadCurrent(
                    nowElapsedRealtimeMs = 5_000L,
                    nowWallClockMs =
                        wallNow + 30_000L,
                    currentBootCount = 401,
                )

            val remaining2 =
                second.remainingAtAnchorMs()

            val third =
                store.loadCurrent(
                    nowElapsedRealtimeMs = 5_000L,
                    nowWallClockMs =
                        wallNow + 60_000L,
                    currentBootCount = 402,
                )

            val remaining3 =
                third.remainingAtAnchorMs()

            assertTrue(
                remaining2 <= remaining1,
            )

            assertTrue(
                remaining3 <= remaining2,
            )
        }
    }

    @Test
    fun alpha1WallClockStateMigratesToMonotonicState() {
        withCleanStore { store, prefs ->
            val bootCount = 500
            val elapsedNow = 15_000L
            val wallNow = 1_800_000_000_000L

            val legacyExpiry =
                wallNow +
                    DeadmanDirectBootStore
                        .MIN_INTERVAL_MS

            val committed =
                prefs.edit()
                    .putBoolean(
                        "deadman.enabled",
                        true,
                    )
                    .putLong(
                        "deadman.interval_ms",
                        DeadmanDirectBootStore
                            .MIN_INTERVAL_MS,
                    )
                    .putLong(
                        "deadman.expiry_wall_clock_ms",
                        legacyExpiry,
                    )
                    .commit()

            check(committed)

            val migrated =
                store.loadCurrent(
                    nowElapsedRealtimeMs =
                        elapsedNow,
                    nowWallClockMs =
                        wallNow,
                    currentBootCount =
                        bootCount,
                )

            assertTrue(
                migrated.enabled,
            )

            assertTrue(
                migrated.isReady(),
            )

            assertEquals(
                bootCount,
                migrated.bootCount,
            )

            assertEquals(
                elapsedNow +
                    DeadmanDirectBootStore
                        .MIN_INTERVAL_MS,
                migrated.expiryElapsedRealtimeMs,
            )

            assertFalse(
                prefs.contains(
                    "deadman.expiry_wall_clock_ms",
                ),
            )
        }
    }

    @Test
    fun malformedOrExpiredActiveStateCannotBeRevivedByIntervalChange() {
        withCleanStore { store, prefs ->
            val bootCount = 600
            val elapsedNow = 100_000L
            val wallNow = 1_800_000_000_000L

            val malformedCommitted =
                prefs.edit()
                    .putBoolean(
                        "deadman.enabled",
                        true,
                    )
                    .putLong(
                        "deadman.interval_ms",
                        1L,
                    )
                    .putInt(
                        "deadman.boot_count",
                        bootCount,
                    )
                    .putLong(
                        "deadman.expiry_elapsed_realtime_ms",
                        elapsedNow +
                            DeadmanDirectBootStore
                                .MIN_INTERVAL_MS,
                    )
                    .putLong(
                        "deadman.anchor_elapsed_realtime_ms",
                        elapsedNow,
                    )
                    .putLong(
                        "deadman.anchor_wall_clock_ms",
                        wallNow,
                    )
                    .commit()

            check(malformedCommitted)

            val malformed =
                store.load()

            assertFalse(
                malformed.isReady(),
            )

            try {
                store.setIntervalMs(
                    intervalMs =
                        DeadmanDirectBootStore
                            .DEFAULT_INTERVAL_MS,
                    nowElapsedRealtimeMs =
                        elapsedNow,
                    nowWallClockMs =
                        wallNow,
                    currentBootCount =
                        bootCount,
                )

                fail(
                    "Malformed enabled state was revived",
                )
            } catch (_: IllegalStateException) {
                // Expected.
            }

            val expiredCommitted =
                prefs.edit()
                    .putBoolean(
                        "deadman.enabled",
                        true,
                    )
                    .putLong(
                        "deadman.interval_ms",
                        DeadmanDirectBootStore
                            .MIN_INTERVAL_MS,
                    )
                    .putInt(
                        "deadman.boot_count",
                        bootCount,
                    )
                    .putLong(
                        "deadman.expiry_elapsed_realtime_ms",
                        elapsedNow - 1L,
                    )
                    .putLong(
                        "deadman.anchor_elapsed_realtime_ms",
                        elapsedNow - 2L,
                    )
                    .putLong(
                        "deadman.anchor_wall_clock_ms",
                        wallNow,
                    )
                    .commit()

            check(expiredCommitted)

            val expired =
                store.loadCurrent(
                    nowElapsedRealtimeMs =
                        elapsedNow,
                    nowWallClockMs =
                        wallNow,
                    currentBootCount =
                        bootCount,
                )

            assertTrue(
                expired.isExpiredSameBoot(
                    nowElapsedRealtimeMs =
                        elapsedNow,
                    currentBootCount =
                        bootCount,
                ),
            )

            try {
                store.setIntervalMs(
                    intervalMs =
                        DeadmanDirectBootStore
                            .DEFAULT_INTERVAL_MS,
                    nowElapsedRealtimeMs =
                        elapsedNow,
                    nowWallClockMs =
                        wallNow,
                    currentBootCount =
                        bootCount,
                )

                fail(
                    "Expired enabled state was revived",
                )
            } catch (_: IllegalStateException) {
                // Expected.
            }
        }
    }

    private fun withCleanStore(
        block: (
            DeadmanDirectBootStore,
            android.content.SharedPreferences,
        ) -> Unit,
    ) {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()
                .createDeviceProtectedStorageContext()

        val prefs =
            context.getSharedPreferences(
                "runtime_state_monitor",
                Context.MODE_PRIVATE,
            )

        val original =
            prefs.all.toMap()

        try {
            check(
                prefs.edit()
                    .clear()
                    .commit(),
            )

            block(
                DeadmanDirectBootStore(
                    context,
                ),
                prefs,
            )
        } finally {
            val editor =
                prefs.edit()
                    .clear()

            for (
                (key, value) in original
            ) {
                when (value) {
                    is Boolean ->
                        editor.putBoolean(
                            key,
                            value,
                        )

                    is Int ->
                        editor.putInt(
                            key,
                            value,
                        )

                    is Long ->
                        editor.putLong(
                            key,
                            value,
                        )

                    is Float ->
                        editor.putFloat(
                            key,
                            value,
                        )

                    is String ->
                        editor.putString(
                            key,
                            value,
                        )

                    is Set<*> ->
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(
                            key,
                            value as Set<String>,
                        )
                }
            }

            check(
                editor.commit(),
            )
        }
    }
}
