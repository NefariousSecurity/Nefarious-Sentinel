package com.localruntime.core.management

import android.os.SystemClock
import android.util.Base64
import com.runtimebridge.api.ManagementProtocol
import java.security.SecureRandom

/**
 * Process-memory-only authorization bridge for short system-UI setup flows.
 *
 * A handoff exists only to let an already authenticated Controller survive
 * one specific Core-owned Android permission dialog without keeping the
 * normal authenticated management session alive in the background.
 *
 * Security properties:
 *
 * - tokens are generated from 256 bits of SecureRandom;
 * - each token is bound to the authenticated Controller UID;
 * - each token is bound to one exact setup action;
 * - the ordinary authenticated session is destroyed before the handoff is
 *   returned to the Controller;
 * - the Core-owned setup Activity must mark the handoff completed;
 * - redemption is one-use and time-limited;
 * - nothing is persisted, so Core process death or reboot invalidates every
 *   outstanding handoff.
 */
internal object SetupHandoffStore {

    private val secureRandom =
        SecureRandom()

    private val entries =
        mutableMapOf<String, Entry>()

    @Synchronized
    fun create(
        callerUid: Int,
        action: String,
    ): String {
        pruneExpired()

        entries
            .filterValues {
                it.callerUid ==
                    callerUid
            }
            .keys
            .toList()
            .forEach {
                entries.remove(
                    it,
                )
            }

        val randomBytes =
            ByteArray(
                TOKEN_BYTES,
            )

        secureRandom.nextBytes(
            randomBytes,
        )

        val token =
            Base64.encodeToString(
                randomBytes,
                Base64.URL_SAFE or
                    Base64.NO_WRAP or
                    Base64.NO_PADDING,
            )

        entries[token] =
            Entry(
                callerUid =
                    callerUid,
                action =
                    action,
                expiresAtElapsedRealtimeMs =
                    SystemClock.elapsedRealtime() +
                        ManagementProtocol
                            .SETUP_HANDOFF_DURATION_MS,
                completed =
                    false,
            )

        return token
    }

    @Synchronized
    fun isPending(
        token: String?,
        action: String,
    ): Boolean {
        if (token.isNullOrBlank()) {
            return false
        }

        pruneExpired()

        val entry =
            entries[token]
                ?: return false

        return entry.action ==
            action
    }

    @Synchronized
    fun complete(
        token: String?,
        action: String,
    ): Boolean {
        if (token.isNullOrBlank()) {
            return false
        }

        pruneExpired()

        val entry =
            entries[token]
                ?: return false

        if (
            entry.action !=
            action
        ) {
            return false
        }

        entries[token] =
            entry.copy(
                completed =
                    true,
            )

        return true
    }

    @Synchronized
    fun consume(
        token: String,
        callerUid: Int,
        action: String,
    ): Boolean {
        pruneExpired()

        val entry =
            entries.remove(
                token,
            ) ?: return false

        return entry.callerUid ==
            callerUid &&
            entry.action ==
            action &&
            entry.completed
    }

    private fun pruneExpired() {
        val now =
            SystemClock.elapsedRealtime()

        entries
            .filterValues {
                now >=
                    it.expiresAtElapsedRealtimeMs
            }
            .keys
            .toList()
            .forEach {
                entries.remove(
                    it,
                )
            }
    }

    private data class Entry(
        val callerUid: Int,
        val action: String,
        val expiresAtElapsedRealtimeMs: Long,
        val completed: Boolean,
    )

    private const val TOKEN_BYTES =
        32
}
