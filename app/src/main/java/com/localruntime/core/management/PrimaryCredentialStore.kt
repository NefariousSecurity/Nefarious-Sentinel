package com.localruntime.core.management

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Credential-Encrypted storage for Sentinel's primary authentication factor.
 *
 * Only the primary factor lives here. Future optional second factors should use
 * their own independent storage and verification component, allowing Core's
 * session gate to require additional factors without weakening or redesigning
 * the PIN verifier.
 *
 * Security properties:
 *
 * - credential material is stored in normal Credential Encrypted app storage,
 *   not Device Protected Storage;
 * - the persisted verifier is not the raw PBKDF2 result;
 * - the PBKDF2 result is authenticated with a random non-exportable
 *   Android Keystore HMAC key before persistence;
 * - copying the preferences file alone therefore does not provide a
 *   self-contained verifier for offline PIN guessing.
 */
class PrimaryCredentialStore(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    /*
     * appContext's normal data directory is Credential Encrypted storage on the
     * Android versions supported by Sentinel.
     *
     * Do not replace this with createDeviceProtectedStorageContext().
     */
    private val credentialPrefs =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    /**
     * True when a complete hardened primary credential exists.
     *
     * Partial persisted state fails closed rather than being treated as a
     * legitimate opportunity to re-enrol a new PIN.
     */
    @Synchronized
    fun isEnrolled(): Boolean {
        val hasSalt =
            credentialPrefs.contains(
                KEY_PIN_SALT,
            )

        val hasVerifier =
            credentialPrefs.contains(
                KEY_PROTECTED_VERIFIER,
            )

        if (hasSalt != hasVerifier) {
            error(
                "Primary credential storage is incomplete",
            )
        }

        return hasSalt &&
            hasVerifier
    }

    /**
     * Persist a newly enrolled primary factor.
     *
     * [rawVerifier] is the deliberately expensive PBKDF2 result calculated by
     * CoreAuthManager. It is never written directly to disk.
     */
    @Synchronized
    fun enroll(
        salt: ByteArray,
        rawVerifier: ByteArray,
    ) {
        check(
            !isEnrolled(),
        ) {
            "Primary credential is already enrolled"
        }

        writeProtectedCredential(
            salt = salt,
            rawVerifier = rawVerifier,
        )
    }

    /**
     * Import the historical DPS PBKDF2 verifier into the hardened CE/Keystore
     * representation.
     *
     * This does not require the user's plaintext PIN because the historical
     * verifier is already the PBKDF2 result that new authentication attempts
     * will reproduce.
     *
     * If CE data was written before a previous migration was interrupted but
     * the Keystore key is unexpectedly absent, the still-present legacy
     * verifier permits safe recovery by re-wrapping it with a fresh key before
     * the caller removes the legacy material.
     */
    @Synchronized
    fun migrateLegacy(
        salt: ByteArray,
        rawVerifier: ByteArray,
    ) {
        if (isEnrolled()) {
            if (getExistingKey() != null) {
                return
            }

            /*
             * Recovery is possible only because the caller still possesses the
             * legacy verifier during migration.
             */
            check(
                credentialPrefs.edit()
                    .clear()
                    .commit(),
            ) {
                "Failed to clear unusable primary credential state"
            }
        }

        writeProtectedCredential(
            salt = salt,
            rawVerifier = rawVerifier,
        )
    }

    @Synchronized
    fun loadSalt(): ByteArray? {
        if (!isEnrolled()) {
            return null
        }

        val encoded =
            credentialPrefs.getString(
                KEY_PIN_SALT,
                null,
            ) ?: return null

        return decode(
            encoded,
        )
    }

    /**
     * Compare one freshly derived PBKDF2 verifier with the protected persisted
     * verifier.
     *
     * The Keystore key must already exist. We deliberately do not silently
     * create a replacement key here because doing so would make every valid PIN
     * fail while disguising the underlying key-loss condition.
     */
    @Synchronized
    fun matches(
        rawVerifier: ByteArray,
    ): Boolean {
        if (!isEnrolled()) {
            return false
        }

        val expected =
            decode(
                credentialPrefs.getString(
                    KEY_PROTECTED_VERIFIER,
                    null,
                ) ?: return false,
            )

        val key =
            getExistingKey()
                ?: error(
                    "Primary credential Keystore key is unavailable",
                )

        val candidate =
            protect(
                key = key,
                rawVerifier = rawVerifier,
            )

        return try {
            MessageDigest.isEqual(
                expected,
                candidate,
            )
        } finally {
            expected.fill(0)
            candidate.fill(0)
        }
    }

    private fun writeProtectedCredential(
        salt: ByteArray,
        rawVerifier: ByteArray,
    ) {
        val key =
            getOrCreateKey()

        val protectedVerifier =
            protect(
                key = key,
                rawVerifier = rawVerifier,
            )

        try {
            check(
                credentialPrefs.edit()
                    .putInt(
                        KEY_SCHEMA_VERSION,
                        SCHEMA_VERSION,
                    )
                    .putString(
                        KEY_PIN_SALT,
                        encode(
                            salt,
                        ),
                    )
                    .putString(
                        KEY_PROTECTED_VERIFIER,
                        encode(
                            protectedVerifier,
                        ),
                    )
                    .commit(),
            ) {
                "Failed to persist hardened primary credential"
            }
        } finally {
            protectedVerifier.fill(0)
        }
    }

    private fun protect(
        key: SecretKey,
        rawVerifier: ByteArray,
    ): ByteArray {
        val mac =
            Mac.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            )

        mac.init(
            key,
        )

        return mac.doFinal(
            rawVerifier,
        )
    }

    private fun getExistingKey():
        SecretKey? {
        val keyStore =
            KeyStore.getInstance(
                ANDROID_KEYSTORE,
            )

        keyStore.load(
            null,
        )

        return keyStore.getKey(
            KEY_ALIAS,
            null,
        ) as? SecretKey
    }

    private fun getOrCreateKey():
        SecretKey {
        getExistingKey()
            ?.let {
                return it
            }

        val generator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                ANDROID_KEYSTORE,
            )

        val specification =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or
                    KeyProperties.PURPOSE_VERIFY,
            )
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                )
                /*
                 * Sentinel's independent management PIN remains the user
                 * authentication factor. Requiring Android device credentials
                 * here would couple Sentinel authentication to the phone PIN.
                 */
                .setUserAuthenticationRequired(
                    false,
                )
                .build()

        generator.init(
            specification,
        )

        return generator.generateKey()
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
        private const val PREFS_NAME =
            "core_primary_credential_v2"

        private const val KEY_SCHEMA_VERSION =
            "schema_version"

        private const val KEY_PIN_SALT =
            "pin_salt"

        private const val KEY_PROTECTED_VERIFIER =
            "protected_verifier"

        private const val SCHEMA_VERSION =
            1

        /*
         * Deliberately neutral alias. The key is scoped to Core's Android
         * application UID and is non-exportable through Android Keystore.
         */
        private const val KEY_ALIAS =
            "runtime.primary.credential.hmac.v1"

        private const val ANDROID_KEYSTORE =
            "AndroidKeyStore"
    }
}
