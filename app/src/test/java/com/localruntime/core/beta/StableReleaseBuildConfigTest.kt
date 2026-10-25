package com.localruntime.core.beta

import com.localruntime.core.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compile-time release safeguards.
 *
 * Runs for debug and release unit-test variants on this stable candidate
 * branch. Debug builds may append "-debug" to VERSION_NAME.
 */
class StableReleaseBuildConfigTest {
    @Test
    fun stableCandidateHasNoExpiry() {
        assertEquals(
            "STABLE",
            BuildConfig.BUILD_CHANNEL,
        )

        assertFalse(
            BuildConfig.BETA_BUILD,
        )

        assertEquals(
            0L,
            BuildConfig.BETA_EXPIRY_EPOCH_MS,
        )

        assertTrue(
            BuildConfig.VERSION_NAME.startsWith(
                "1.0.1",
            ),
        )

        assertEquals(
            5,
            BuildConfig.VERSION_CODE,
        )
    }
}
