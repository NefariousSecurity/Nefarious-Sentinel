package com.localruntime.core.sms

/**
 * Pure decision layer for an incoming SMS Wipe candidate.
 *
 * This class performs no persistence and no destructive action.
 *
 * The caller remains responsible for:
 *
 * - recording a keyword failure when RECORD_FAILURE is returned;
 * - clearing attempt state and invoking wipe when ACCEPT is returned.
 */
object SmsTriggerDecision {

    enum class Result {
        DISABLED,
        CONFIG_INVALID,
        SENDER_MISMATCH,
        LOCKED_OUT,
        RECORD_FAILURE,
        ACCEPT,
    }

    fun evaluate(
        config: SmsKillConfig,
        sender: String,
        body: String,
        remainingLockoutMs: Long,
    ): Result {
        if (!config.enabled) {
            return Result.DISABLED
        }

        if (
            !SmsMatchRules.isValidSender(
                config.senderNumber,
            ) ||
            config.keyword.length !in
                SmsKillConfig.MIN_KEYWORD_LENGTH..
                    SmsKillConfig.MAX_KEYWORD_LENGTH
        ) {
            return Result.CONFIG_INVALID
        }

        /*
         * Sender filtering deliberately occurs before lockout state is
         * considered. Messages from unrelated senders therefore cannot consume
         * attempts or interfere with the authorised sender.
         */
        if (
            !SmsMatchRules.senderMatches(
                sender,
                config.senderNumber,
            )
        ) {
            return Result.SENDER_MISMATCH
        }

        if (remainingLockoutMs > 0L) {
            return Result.LOCKED_OUT
        }

        if (
            !SmsMatchRules.keywordMatches(
                body,
                config.keyword,
            )
        ) {
            return Result.RECORD_FAILURE
        }

        return Result.ACCEPT
    }
}
