package com.localruntime.core.prefs

import java.io.IOException
import java.security.GeneralSecurityException
import org.junit.Assert.assertEquals
import org.junit.Test

class SecurePrefsRecoveryPolicyTest {

    @Test
    fun generalSecurityExceptionIsRecoverable() {
        assertEquals(
            SecurePrefsRecoveryPolicy.Decision.RETRY,
            SecurePrefsRecoveryPolicy.classify(
                GeneralSecurityException(
                    "test",
                ),
            ),
        )
    }

    @Test
    fun ioExceptionIsRecoverable() {
        assertEquals(
            SecurePrefsRecoveryPolicy.Decision.RETRY,
            SecurePrefsRecoveryPolicy.classify(
                IOException(
                    "test",
                ),
            ),
        )
    }

    @Test
    fun illegalStateExceptionPropagates() {
        assertEquals(
            SecurePrefsRecoveryPolicy.Decision.PROPAGATE,
            SecurePrefsRecoveryPolicy.classify(
                IllegalStateException(
                    "test",
                ),
            ),
        )
    }

    @Test
    fun nullPointerExceptionPropagates() {
        assertEquals(
            SecurePrefsRecoveryPolicy.Decision.PROPAGATE,
            SecurePrefsRecoveryPolicy.classify(
                NullPointerException(
                    "test",
                ),
            ),
        )
    }
}
