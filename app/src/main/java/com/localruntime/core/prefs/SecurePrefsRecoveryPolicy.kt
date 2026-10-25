package com.localruntime.core.prefs

import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Pure classification for SecurePrefs open failures.
 *
 * Only encrypted-storage, Keystore, or I/O failures are considered recoverable.
 * Unexpected programming or runtime failures must propagate instead of being
 * mislabeled as preference corruption.
 */
object SecurePrefsRecoveryPolicy {

    enum class Decision {
        RETRY,
        PROPAGATE,
    }

    fun classify(
        throwable: Throwable,
    ): Decision =
        when (throwable) {
            is GeneralSecurityException ->
                Decision.RETRY

            is IOException ->
                Decision.RETRY

            else ->
                Decision.PROPAGATE
        }
}
