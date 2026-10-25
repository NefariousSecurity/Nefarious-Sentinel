package com.localruntime.core.management

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import com.runtimebridge.api.ManagementProtocol
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Core-owned management PIN verifier and online rate limiter.
 *
 * Primary credential design:
 *
 * - the management PIN remains independent of the Android lock-screen PIN;
 * - the plaintext PIN is never persisted;
 * - PBKDF2-HMAC-SHA256 remains deliberately expensive;
 * - the PBKDF2 verifier is handed to PrimaryCredentialStore, which stores only
 *   a Keystore-HMAC-protected representation in Credential Encrypted storage;
 * - online failure and lockout state remains in Device Protected Storage so
 *   reboot cannot trivially reset throttling.
 *
 * Primary authentication is deliberately isolated from session creation and
 * future optional second-factor storage. A later second factor can therefore
 * be inserted between successful primary authentication and Core session
 * creation without changing how the primary PIN itself is protected.
 */
class CoreAuthManager(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    /*
     * Only rate-limit / failure state remains Direct-Boot accessible.
     *
     * Historical Alpha builds also stored the PBKDF2 salt and verifier here.
     * migrateLegacyCredentialIfNeeded() removes those credential values after
     * successfully importing them into PrimaryCredentialStore.
     */
    private val storageContext =
        appContext.createDeviceProtectedStorageContext()

    private val statePrefs =
        storageContext.getSharedPreferences(
            LEGACY_STATE_PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    private val credentialStore =
        PrimaryCredentialStore(
            appContext,
        )

    init {
        migrateLegacyCredentialIfNeeded()
    }

    @Synchronized
    fun getPinState(): Int {
        if (!isEnrolled()) {
            return ManagementProtocol
                .PIN_STATE_NOT_ENROLLED
        }

        if (isTerminal()) {
            return ManagementProtocol
                .PIN_STATE_TERMINAL
        }

        if (getRemainingLockoutMs() > 0L) {
            return ManagementProtocol
                .PIN_STATE_LOCKED
        }

        return ManagementProtocol
            .PIN_STATE_READY
    }

    @Synchronized
    fun enroll(
        pin: String,
    ): Int {
        if (isEnrolled()) {
            return ManagementProtocol
                .RESULT_ALREADY_ENROLLED
        }

        if (!isValidPinFormat(pin)) {
            return ManagementProtocol
                .RESULT_INVALID_FORMAT
        }

        val salt =
            ByteArray(
                SALT_BYTES,
            ).also {
                secureRandom.nextBytes(
                    it,
                )
            }

        val repeatSalt =
            ByteArray(
                REPEAT_SALT_BYTES,
            ).also {
                secureRandom.nextBytes(
                    it,
                )
            }

        val rawVerifier =
            deriveVerifier(
                pin = pin,
                salt = salt,
            )

        try {
            /*
             * Persist the non-verifier repeat-guess salt first. If hardened
             * credential persistence subsequently fails, the installation is
             * still considered unenrolled and a later retry can safely replace
             * this value.
             */
            check(
                statePrefs.edit()
                    .putString(
                        KEY_REPEAT_SALT,
                        encode(
                            repeatSalt,
                        ),
                    )
                    .commit(),
            ) {
                "Failed to persist management authentication state"
            }

            credentialStore.enroll(
                salt = salt,
                rawVerifier =
                    rawVerifier,
            )

            clearFailureState()

            return ManagementProtocol
                .RESULT_OK
        } finally {
            rawVerifier.fill(0)
            repeatSalt.fill(0)
        }
    }

    @Synchronized
    fun authenticate(
        pin: String,
    ): Int {
        if (!isEnrolled()) {
            return ManagementProtocol
                .RESULT_NOT_ENROLLED
        }

        if (isTerminal()) {
            return ManagementProtocol
                .RESULT_TERMINAL
        }

        if (getRemainingLockoutMs() > 0L) {
            return ManagementProtocol
                .RESULT_LOCKED
        }

        if (!isValidPinFormat(pin)) {
            return ManagementProtocol
                .RESULT_INVALID_FORMAT
        }

        val salt =
            credentialStore
                .loadSalt()
                ?: return ManagementProtocol
                    .RESULT_NOT_ENROLLED

        val candidate =
            deriveVerifier(
                pin = pin,
                salt = salt,
            )

        val matched =
            try {
                credentialStore.matches(
                    candidate,
                )
            } finally {
                candidate.fill(0)
            }

        if (matched) {
            clearFailureState()

            return ManagementProtocol
                .RESULT_OK
        }

        val fingerprint =
            fingerprintWrongPin(
                pin,
            )

        val recent =
            getRecentWrongFingerprints()

        /*
         * Repeating one of the recent incorrect credentials does not consume
         * another distinct guess.
         */
        if (
            fingerprint in
            recent
        ) {
            return ManagementProtocol
                .RESULT_REPEAT_FAILURE
        }

        addRecentWrongFingerprint(
            fingerprint =
                fingerprint,
            existing =
                recent,
        )

        val attempts =
            statePrefs.getInt(
                KEY_FAILED_ATTEMPTS,
                0,
            ) + 1

        check(
            statePrefs.edit()
                .putInt(
                    KEY_FAILED_ATTEMPTS,
                    attempts,
                )
                .commit(),
        ) {
            "Failed to persist management authentication failure"
        }

        if (
            attempts >=
            MAX_DISTINCT_FAILURES
        ) {
            check(
                statePrefs.edit()
                    .putBoolean(
                        KEY_TERMINAL,
                        true,
                    )
                    .remove(
                        KEY_LOCKOUT_DURATION_MS,
                    )
                    .remove(
                        KEY_LOCKOUT_START_ELAPSED_MS,
                    )
                    .remove(
                        KEY_LOCKOUT_BOOT_COUNT,
                    )
                    .commit(),
            ) {
                "Failed to persist terminal authentication state"
            }

            return ManagementProtocol
                .RESULT_TERMINAL
        }

        val delayMs =
            delayForAttempt(
                attempts,
            )

        if (delayMs > 0L) {
            startLockout(
                delayMs,
            )

            return ManagementProtocol
                .RESULT_LOCKED
        }

        return ManagementProtocol
            .RESULT_INVALID_PIN
    }

    /**
     * Returns the remaining Core-enforced lockout.
     *
     * Lockouts use elapsedRealtime() within one boot. If the device reboots
     * during a lockout, Core restarts the complete applicable interval rather
     * than allowing reboot to shorten the delay.
     */
    @Synchronized
    fun getRemainingLockoutMs():
        Long {
        if (isTerminal()) {
            return Long.MAX_VALUE
        }

        val duration =
            statePrefs.getLong(
                KEY_LOCKOUT_DURATION_MS,
                0L,
            )

        if (duration <= 0L) {
            return 0L
        }

        val currentBoot =
            currentBootCount()

        val storedBoot =
            statePrefs.getInt(
                KEY_LOCKOUT_BOOT_COUNT,
                currentBoot,
            )

        val now =
            SystemClock.elapsedRealtime()

        if (
            currentBoot !=
            storedBoot
        ) {
            /*
             * Reboot during lockout: restart the complete interval.
             */
            check(
                statePrefs.edit()
                    .putInt(
                        KEY_LOCKOUT_BOOT_COUNT,
                        currentBoot,
                    )
                    .putLong(
                        KEY_LOCKOUT_START_ELAPSED_MS,
                        now,
                    )
                    .commit(),
            ) {
                "Failed to persist rebooted management lockout"
            }

            return duration
        }

        val started =
            statePrefs.getLong(
                KEY_LOCKOUT_START_ELAPSED_MS,
                now,
            )

        val remaining =
            duration -
                (
                    now -
                        started
                    )

        if (remaining <= 0L) {
            clearLockout()

            return 0L
        }

        return remaining
    }

    /**
     * Upgrade an existing Alpha installation.
     *
     * Historical Core stored pin_salt + pin_verifier directly in DPS. The
     * verifier was already the expensive PBKDF2 output, so it can be safely
     * imported without knowing the plaintext PIN:
     *
     *     legacy PBKDF2 verifier
     *              |
     *              v
     *     Keystore HMAC protection
     *              |
     *              v
     *     Credential Encrypted store
     *
     * Only after the hardened CE write succeeds do we delete the historical
     * verifier and salt from DPS.
     */
    private fun migrateLegacyCredentialIfNeeded() {
        val hasLegacySalt =
            statePrefs.contains(
                LEGACY_KEY_PIN_SALT,
            )

        val hasLegacyVerifier =
            statePrefs.contains(
                LEGACY_KEY_PIN_VERIFIER,
            )

        if (
            !hasLegacySalt &&
            !hasLegacyVerifier
        ) {
            return
        }

        check(
            hasLegacySalt &&
                hasLegacyVerifier,
        ) {
            "Legacy management credential storage is incomplete"
        }

        val salt =
            decode(
                statePrefs.getString(
                    LEGACY_KEY_PIN_SALT,
                    null,
                ) ?: error(
                    "Legacy management PIN salt is missing",
                ),
            )

        val verifier =
            decode(
                statePrefs.getString(
                    LEGACY_KEY_PIN_VERIFIER,
                    null,
                ) ?: error(
                    "Legacy management PIN verifier is missing",
                ),
            )

        try {
            credentialStore.migrateLegacy(
                salt = salt,
                rawVerifier =
                    verifier,
            )

            check(
                statePrefs.edit()
                    .remove(
                        LEGACY_KEY_PIN_SALT,
                    )
                    .remove(
                        LEGACY_KEY_PIN_VERIFIER,
                    )
                    .commit(),
            ) {
                "Failed to remove legacy DPS management verifier"
            }
        } finally {
            verifier.fill(0)
        }
    }

    private fun isEnrolled():
        Boolean =
        credentialStore
            .isEnrolled()

    private fun isTerminal():
        Boolean =
        statePrefs.getBoolean(
            KEY_TERMINAL,
            false,
        )

    private fun isValidPinFormat(
        pin: String,
    ): Boolean =
        pin.length in
            ManagementProtocol.PIN_MIN_LENGTH..
                ManagementProtocol.PIN_MAX_LENGTH &&
            pin.all {
                it.isDigit()
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

    private fun fingerprintWrongPin(
        pin: String,
    ): String {
        val repeatSalt =
            decode(
                statePrefs.getString(
                    KEY_REPEAT_SALT,
                    null,
                ) ?: error(
                    "Repeat-fingerprint salt missing",
                ),
            )

        return try {
            val digest =
                MessageDigest.getInstance(
                    "SHA-256",
                )

            digest.update(
                repeatSalt,
            )

            val result =
                digest.digest(
                    pin.toByteArray(
                        StandardCharsets.UTF_8,
                    ),
                )

            encode(
                result,
            )
        } finally {
            repeatSalt.fill(0)
        }
    }

    private fun getRecentWrongFingerprints():
        List<String> {
        val currentBoot =
            currentBootCount()

        val storedBoot =
            statePrefs.getInt(
                KEY_RECENT_WRONG_BOOT_COUNT,
                currentBoot,
            )

        /*
         * Recent wrong credentials are tracked only for the current boot.
         * The persistent failed-attempt counter is deliberately not cleared.
         */
        if (
            storedBoot !=
            currentBoot
        ) {
            check(
                statePrefs.edit()
                    .remove(
                        KEY_RECENT_WRONG,
                    )
                    .putInt(
                        KEY_RECENT_WRONG_BOOT_COUNT,
                        currentBoot,
                    )
                    .commit(),
            ) {
                "Failed to reset recent wrong-PIN state after reboot"
            }

            return emptyList()
        }

        val encoded =
            statePrefs.getString(
                KEY_RECENT_WRONG,
                "",
            ).orEmpty()

        if (encoded.isBlank()) {
            return emptyList()
        }

        return encoded
            .split(
                RECENT_SEPARATOR,
            )
            .filter {
                it.isNotBlank()
            }
    }

    private fun addRecentWrongFingerprint(
        fingerprint: String,
        existing: List<String>,
    ) {
        val updated =
            (
                existing +
                    fingerprint
                )
                .distinct()
                .takeLast(
                    RECENT_WRONG_LIMIT,
                )

        check(
            statePrefs.edit()
                .putString(
                    KEY_RECENT_WRONG,
                    updated.joinToString(
                        RECENT_SEPARATOR,
                    ),
                )
                .putInt(
                    KEY_RECENT_WRONG_BOOT_COUNT,
                    currentBootCount(),
                )
                .commit(),
        ) {
            "Failed to persist recent wrong-PIN state"
        }
    }

    private fun startLockout(
        durationMs: Long,
    ) {
        check(
            statePrefs.edit()
                .putLong(
                    KEY_LOCKOUT_DURATION_MS,
                    durationMs,
                )
                .putLong(
                    KEY_LOCKOUT_START_ELAPSED_MS,
                    SystemClock.elapsedRealtime(),
                )
                .putInt(
                    KEY_LOCKOUT_BOOT_COUNT,
                    currentBootCount(),
                )
                .commit(),
        ) {
            "Failed to persist management lockout"
        }
    }

    private fun clearLockout() {
        check(
            statePrefs.edit()
                .remove(
                    KEY_LOCKOUT_DURATION_MS,
                )
                .remove(
                    KEY_LOCKOUT_START_ELAPSED_MS,
                )
                .remove(
                    KEY_LOCKOUT_BOOT_COUNT,
                )
                .commit(),
        ) {
            "Failed to clear management lockout"
        }
    }

    private fun clearFailureState() {
        check(
            statePrefs.edit()
                .remove(
                    KEY_FAILED_ATTEMPTS,
                )
                .remove(
                    KEY_RECENT_WRONG,
                )
                .remove(
                    KEY_RECENT_WRONG_BOOT_COUNT,
                )
                .remove(
                    KEY_TERMINAL,
                )
                .remove(
                    KEY_LOCKOUT_DURATION_MS,
                )
                .remove(
                    KEY_LOCKOUT_START_ELAPSED_MS,
                )
                .remove(
                    KEY_LOCKOUT_BOOT_COUNT,
                )
                .commit(),
        ) {
            "Failed to clear management authentication failure state"
        }
    }

    private fun currentBootCount():
        Int =
        runCatching {
            Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.BOOT_COUNT,
                0,
            )
        }.getOrDefault(
            0,
        )

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

    /**
     * Delay applied after each distinct incorrect management PIN.
     *
     * Attempts 1-4 have no delay. Attempt 20 permanently closes PIN
     * authentication for this Core installation.
     */
    private fun delayForAttempt(
        attempt: Int,
    ): Long =
        when (attempt) {
            in 1..4 ->
                0L

            5 ->
                60_000L

            6 ->
                300_000L

            7 ->
                900_000L

            8 ->
                1_800_000L

            9 ->
                5_400_000L

            10 ->
                14_400_000L

            11 ->
                43_200_000L

            12 ->
                129_600_000L

            13 ->
                345_600_000L

            14 ->
                1_123_200_000L

            15 ->
                3_542_400_000L

            16 ->
                10_627_200_000L

            17 ->
                31_536_000_000L

            18 ->
                94_608_000_000L

            19 ->
                283_824_000_000L

            else ->
                0L
        }

    companion object {
        /*
         * Preserve the historical DPS filename so existing online lockout state
         * survives this migration.
         */
        private const val LEGACY_STATE_PREFS_NAME =
            "core_management_auth_v1"

        /*
         * Historical credential keys. These are migration-only and are removed
         * from DPS immediately after successful CE + Keystore import.
         */
        private const val LEGACY_KEY_PIN_SALT =
            "pin_salt"

        private const val LEGACY_KEY_PIN_VERIFIER =
            "pin_verifier"

        private const val KEY_REPEAT_SALT =
            "repeat_salt"

        private const val KEY_FAILED_ATTEMPTS =
            "failed_attempts"

        private const val KEY_RECENT_WRONG =
            "recent_wrong"

        private const val KEY_RECENT_WRONG_BOOT_COUNT =
            "recent_wrong_boot_count"

        private const val KEY_TERMINAL =
            "terminal"

        private const val KEY_LOCKOUT_DURATION_MS =
            "lockout_duration_ms"

        private const val KEY_LOCKOUT_START_ELAPSED_MS =
            "lockout_start_elapsed_ms"

        private const val KEY_LOCKOUT_BOOT_COUNT =
            "lockout_boot_count"

        private const val RECENT_SEPARATOR =
            "|"

        private const val RECENT_WRONG_LIMIT =
            5

        private const val MAX_DISTINCT_FAILURES =
            20

        private const val SALT_BYTES =
            32

        private const val REPEAT_SALT_BYTES =
            32

        private const val VERIFIER_BITS =
            256

        /*
         * Deliberately expensive primary verifier.
         *
         * The result is no longer persisted directly. PrimaryCredentialStore
         * binds it to non-exportable Android Keystore material before storage.
         */
        private const val PBKDF2_ITERATIONS =
            600_000

        private val secureRandom =
            SecureRandom()
    }
}
