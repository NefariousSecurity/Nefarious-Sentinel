package com.localruntime.core.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.localruntime.core.log.SecLog
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Shared wrapper around [EncryptedSharedPreferences].
 *
 * The encrypted store contains Sentinel configuration that should remain
 * confidential at rest.
 *
 * Failure to open the encrypted preferences is deliberately non-destructive.
 * A Keystore, framework, storage, upgrade, or corruption failure is not, by
 * itself, sufficient evidence of hostile tampering and must never trigger a
 * device wipe.
 *
 * Recovery policy:
 *
 * 1. Attempt to open the existing encrypted preferences.
 * 2. If a recognized encrypted-storage failure occurs, retry once.
 * 3. If the retry also fails, delete only Sentinel's encrypted preference
 *    file and recreate an empty encrypted store.
 *
 * Features backed by this store therefore return to their safe defaults after
 * unrecoverable preference corruption. In particular, SMS Wipe defaults to
 * disabled until explicitly configured again.
 */
class SecurePrefs private constructor(
    val prefs: SharedPreferences,
) {

    companion object {
        private const val TAG =
            "SecurePrefs"

        private const val FILE_NAME =
            "runtime_state_secure"

        fun create(
            context: Context,
        ): SecurePrefs {
            val appContext =
                context.applicationContext

            val firstAttempt =
                tryOpen(
                    appContext,
                )

            if (firstAttempt != null) {
                return SecurePrefs(
                    firstAttempt,
                )
            }

            SecLog.w(
                TAG,
                "Encrypted preferences open failed; retrying once",
            )

            val retryAttempt =
                tryOpen(
                    appContext,
                )

            if (retryAttempt != null) {
                SecLog.w(
                    TAG,
                    "Encrypted preferences recovered on retry",
                )

                return SecurePrefs(
                    retryAttempt,
                )
            }

            /*
             * Opening the store failed twice.
             *
             * Do not infer malicious tampering from this condition. Android
             * Keystore availability, framework state, interrupted writes,
             * application upgrades, or genuine file corruption can all make
             * encrypted preferences temporarily or permanently unreadable.
             *
             * Reset only this application's encrypted configuration store.
             */
            SecLog.e(
                TAG,
                "Encrypted preferences remain unreadable after retry; " +
                    "resetting Sentinel encrypted configuration",
            )

            val deleted =
                appContext.deleteSharedPreferences(
                    FILE_NAME,
                )

            SecLog.w(
                TAG,
                "Encrypted preferences reset requested; deleted=$deleted",
            )

            val recreated =
                try {
                    buildEncrypted(
                        appContext,
                    )
                } catch (
                    throwable: Throwable,
                ) {
                    /*
                     * Recovery has already failed through both normal attempts
                     * and a store reset. There is no safe configuration store
                     * to return.
                     *
                     * Propagate the failure rather than silently constructing
                     * security state that cannot be persisted.
                     *
                     * This path still never performs a device wipe.
                     */
                    SecLog.e(
                        TAG,
                        "Encrypted preferences recreation failed",
                        throwable,
                    )

                    throw throwable
                }

            SecLog.w(
                TAG,
                "Encrypted preferences recreated with safe defaults",
            )

            return SecurePrefs(
                recreated,
            )
        }

        /**
         * Attempt to open the encrypted preferences.
         *
         * The catch is intentionally broad so that every failure reaches the
         * explicit classification policy. Only known security / storage
         * failures are converted into a recoverable null result.
         *
         * Unexpected programming and runtime failures are propagated.
         */
        private fun tryOpen(
            context: Context,
        ): SharedPreferences? {
            return try {
                buildEncrypted(
                    context,
                )
            } catch (
                throwable: Throwable,
            ) {
                when (
                    SecurePrefsRecoveryPolicy
                        .classify(
                            throwable,
                        )
                ) {
                    SecurePrefsRecoveryPolicy
                        .Decision.RETRY -> {
                        SecLog.e(
                            TAG,
                            "Encrypted preferences recoverable open failure",
                            throwable,
                        )

                        null
                    }

                    SecurePrefsRecoveryPolicy
                        .Decision.PROPAGATE -> {
                        SecLog.e(
                            TAG,
                            "Unexpected encrypted preferences failure",
                            throwable,
                        )

                        throw throwable
                    }
                }
            }
        }

        @Throws(
            GeneralSecurityException::class,
            IOException::class,
        )
        private fun buildEncrypted(
            context: Context,
        ): SharedPreferences {
            val masterKey =
                MasterKey.Builder(
                    context,
                )
                    .setKeyScheme(
                        MasterKey.KeyScheme.AES256_GCM,
                    )
                    .build()

            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences
                    .PrefKeyEncryptionScheme
                    .AES256_SIV,
                EncryptedSharedPreferences
                    .PrefValueEncryptionScheme
                    .AES256_GCM,
            )
        }
    }
}
