package com.localruntime.core.management

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runtimebridge.api.ManagementProtocol
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the hardened Sentinel primary credential.
 *
 * These tests intentionally exercise Android Keystore and Android's separate
 * Credential Encrypted / Device Protected storage domains, so they belong in
 * androidTest rather than ordinary host unit tests.
 */
@RunWith(AndroidJUnit4::class)
class CoreAuthManagerInstrumentedTest {

    @Test
    fun freshEnrollmentStoresOnlyKeystoreProtectedVerifierInCe() {
        withCleanAuthenticationState {
            context,
            legacyPrefs,
            credentialPrefs,
            deviceProtectedCredentialPrefs ->

            val manager =
                CoreAuthManager(
                    context,
                )

            assertEquals(
                ManagementProtocol.RESULT_OK,
                manager.enroll(
                    TEST_PIN,
                ),
            )

            assertTrue(
                credentialPrefs.contains(
                    KEY_PIN_SALT,
                ),
            )

            assertTrue(
                credentialPrefs.contains(
                    KEY_PROTECTED_VERIFIER,
                ),
            )

            /*
             * The historical raw-verifier key must not exist in CE.
             */
            assertFalse(
                credentialPrefs.contains(
                    LEGACY_KEY_PIN_VERIFIER,
                ),
            )

            /*
             * Historical credential material must no longer be present in DPS.
             */
            assertFalse(
                legacyPrefs.contains(
                    LEGACY_KEY_PIN_SALT,
                ),
            )

            assertFalse(
                legacyPrefs.contains(
                    LEGACY_KEY_PIN_VERIFIER,
                ),
            )

            /*
             * The hardened primary credential file must not accidentally exist
             * in Device Protected Storage.
             */
            assertFalse(
                deviceProtectedCredentialPrefs.contains(
                    KEY_PIN_SALT,
                ),
            )

            assertFalse(
                deviceProtectedCredentialPrefs.contains(
                    KEY_PROTECTED_VERIFIER,
                ),
            )

            val keyStore =
                loadKeyStore()

            assertTrue(
                keyStore.containsAlias(
                    KEY_ALIAS,
                ),
            )

            val key =
                keyStore.getKey(
                    KEY_ALIAS,
                    null,
                ) as? SecretKey

            assertNotNull(
                key,
            )

            /*
             * Android Keystore symmetric keys are non-exportable.
             */
            assertNull(
                key?.encoded,
            )

            val persistedSalt =
                decode(
                    credentialPrefs.getString(
                        KEY_PIN_SALT,
                        null,
                    ) ?: error(
                        "Persisted PIN salt missing",
                    ),
                )

            val protectedVerifier =
                decode(
                    credentialPrefs.getString(
                        KEY_PROTECTED_VERIFIER,
                        null,
                    ) ?: error(
                        "Protected verifier missing",
                    ),
                )

            val rawVerifier =
                deriveVerifier(
                    pin = TEST_PIN,
                    salt = persistedSalt,
                )

            try {
                /*
                 * The persisted verifier must not simply be the PBKDF2 result.
                 */
                assertFalse(
                    MessageDigest.isEqual(
                        rawVerifier,
                        protectedVerifier,
                    ),
                )
            } finally {
                rawVerifier.fill(
                    0,
                )

                protectedVerifier.fill(
                    0,
                )
            }

            assertEquals(
                ManagementProtocol.RESULT_OK,
                manager.authenticate(
                    TEST_PIN,
                ),
            )

            assertEquals(
                ManagementProtocol.RESULT_INVALID_PIN,
                manager.authenticate(
                    WRONG_PIN,
                ),
            )
        }
    }

    @Test
    fun alpha1DpsVerifierMigratesAndExistingPinStillAuthenticates() {
        withCleanAuthenticationState {
            context,
            legacyPrefs,
            credentialPrefs,
            _ ->

            val salt =
                ByteArray(
                    SALT_BYTES,
                ) {
                    index ->
                    (
                        index +
                            1
                        ).toByte()
                }

            val repeatSalt =
                ByteArray(
                    REPEAT_SALT_BYTES,
                ) {
                    index ->
                    (
                        index +
                            33
                        ).toByte()
                }

            val rawVerifier =
                deriveVerifier(
                    pin = TEST_PIN,
                    salt = salt,
                )

            try {
                check(
                    legacyPrefs.edit()
                        .putString(
                            LEGACY_KEY_PIN_SALT,
                            encode(
                                salt,
                            ),
                        )
                        .putString(
                            LEGACY_KEY_PIN_VERIFIER,
                            encode(
                                rawVerifier,
                            ),
                        )
                        .putString(
                            KEY_REPEAT_SALT,
                            encode(
                                repeatSalt,
                            ),
                        )
                        .commit(),
                )

                /*
                 * Construction performs the one-way Alpha 1 migration.
                 */
                val manager =
                    CoreAuthManager(
                        context,
                    )

                assertFalse(
                    legacyPrefs.contains(
                        LEGACY_KEY_PIN_SALT,
                    ),
                )

                assertFalse(
                    legacyPrefs.contains(
                        LEGACY_KEY_PIN_VERIFIER,
                    ),
                )

                /*
                 * Non-secret online throttling support remains in DPS.
                 */
                assertTrue(
                    legacyPrefs.contains(
                        KEY_REPEAT_SALT,
                    ),
                )

                assertTrue(
                    credentialPrefs.contains(
                        KEY_PIN_SALT,
                    ),
                )

                assertTrue(
                    credentialPrefs.contains(
                        KEY_PROTECTED_VERIFIER,
                    ),
                )

                val migratedProtectedVerifier =
                    decode(
                        credentialPrefs.getString(
                            KEY_PROTECTED_VERIFIER,
                            null,
                        ) ?: error(
                            "Migrated protected verifier missing",
                        ),
                    )

                try {
                    /*
                     * Migration must not simply copy the historical PBKDF2
                     * verifier into Credential Encrypted storage.
                     */
                    assertFalse(
                        MessageDigest.isEqual(
                            rawVerifier,
                            migratedProtectedVerifier,
                        ),
                    )
                } finally {
                    migratedProtectedVerifier.fill(
                        0,
                    )
                }

                assertEquals(
                    ManagementProtocol.PIN_STATE_READY,
                    manager.getPinState(),
                )

                /*
                 * The existing six-digit Sentinel PIN remains valid.
                 */
                assertEquals(
                    ManagementProtocol.RESULT_OK,
                    manager.authenticate(
                        TEST_PIN,
                    ),
                )
            } finally {
                rawVerifier.fill(
                    0,
                )

                repeatSalt.fill(
                    0,
                )
            }
        }
    }

    private fun withCleanAuthenticationState(
        block: (
            Context,
            android.content.SharedPreferences,
            android.content.SharedPreferences,
            android.content.SharedPreferences,
        ) -> Unit,
    ) {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        check(
            !context.isDeviceProtectedStorage,
        ) {
            "Test application context unexpectedly uses Device Protected Storage"
        }

        val deviceProtectedContext =
            context
                .createDeviceProtectedStorageContext()

        val legacyPrefs =
            deviceProtectedContext
                .getSharedPreferences(
                    LEGACY_PREFS_NAME,
                    Context.MODE_PRIVATE,
                )

        val credentialPrefs =
            context
                .getSharedPreferences(
                    PRIMARY_PREFS_NAME,
                    Context.MODE_PRIVATE,
                )

        val deviceProtectedCredentialPrefs =
            deviceProtectedContext
                .getSharedPreferences(
                    PRIMARY_PREFS_NAME,
                    Context.MODE_PRIVATE,
                )

        try {
            clearTestState(
                legacyPrefs =
                    legacyPrefs,
                credentialPrefs =
                    credentialPrefs,
                deviceProtectedCredentialPrefs =
                    deviceProtectedCredentialPrefs,
            )

            block(
                context,
                legacyPrefs,
                credentialPrefs,
                deviceProtectedCredentialPrefs,
            )
        } finally {
            clearTestState(
                legacyPrefs =
                    legacyPrefs,
                credentialPrefs =
                    credentialPrefs,
                deviceProtectedCredentialPrefs =
                    deviceProtectedCredentialPrefs,
            )
        }
    }

    private fun clearTestState(
        legacyPrefs:
            android.content.SharedPreferences,
        credentialPrefs:
            android.content.SharedPreferences,
        deviceProtectedCredentialPrefs:
            android.content.SharedPreferences,
    ) {
        check(
            legacyPrefs.edit()
                .clear()
                .commit(),
        )

        check(
            credentialPrefs.edit()
                .clear()
                .commit(),
        )

        check(
            deviceProtectedCredentialPrefs
                .edit()
                .clear()
                .commit(),
        )

        val keyStore =
            loadKeyStore()

        if (
            keyStore.containsAlias(
                KEY_ALIAS,
            )
        ) {
            keyStore.deleteEntry(
                KEY_ALIAS,
            )
        }
    }

    private fun loadKeyStore():
        KeyStore =
        KeyStore.getInstance(
            ANDROID_KEYSTORE,
        ).apply {
            load(
                null,
            )
        }

    private fun deriveVerifier(
        pin: String,
        salt: ByteArray,
    ): ByteArray {
        val spec =
            PBEKeySpec(
                pin.toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                VERIFIER_BITS,
            )

        return try {
            SecretKeyFactory
                .getInstance(
                    "PBKDF2WithHmacSHA256",
                )
                .generateSecret(
                    spec,
                )
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(
        value: ByteArray,
    ): String =
        Base64.encodeToString(
            value,
            Base64.NO_WRAP,
        )

    private fun decode(
        value: String,
    ): ByteArray =
        Base64.decode(
            value,
            Base64.NO_WRAP,
        )

    companion object {
        private const val TEST_PIN =
            "482193"

        private const val WRONG_PIN =
            "482194"

        private const val LEGACY_PREFS_NAME =
            "core_management_auth_v1"

        private const val PRIMARY_PREFS_NAME =
            "core_primary_credential_v2"

        private const val LEGACY_KEY_PIN_SALT =
            "pin_salt"

        private const val LEGACY_KEY_PIN_VERIFIER =
            "pin_verifier"

        private const val KEY_REPEAT_SALT =
            "repeat_salt"

        private const val KEY_PIN_SALT =
            "pin_salt"

        private const val KEY_PROTECTED_VERIFIER =
            "protected_verifier"

        private const val KEY_ALIAS =
            "runtime.primary.credential.hmac.v1"

        private const val ANDROID_KEYSTORE =
            "AndroidKeyStore"

        private const val SALT_BYTES =
            32

        private const val REPEAT_SALT_BYTES =
            32

        private const val VERIFIER_BITS =
            256

        private const val PBKDF2_ITERATIONS =
            600_000
    }
}
