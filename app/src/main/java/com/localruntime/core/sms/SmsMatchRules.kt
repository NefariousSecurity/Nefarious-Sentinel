package com.localruntime.core.sms

/**
 * Authoritative Core-owned SMS matching rules.
 *
 * The isolated SMS Helper deliberately does not know the configured sender or
 * trigger keyword. It extracts sender/body only and reports those raw values
 * to Core for evaluation here.
 *
 * Security rules:
 *
 * - sender matching is exact after conservative normalization;
 * - configured senders must use international E.164 form;
 * - no suffix matching is permitted;
 * - trigger keywords are compared against the complete trimmed SMS body;
 * - keyword comparison is case-sensitive.
 *
 * Exact sender comparison reduces ambiguity but does not make SMS caller ID a
 * cryptographically authenticated identity. The keyword remains the secret
 * authentication factor.
 */
object SmsMatchRules {

    /**
     * Remove common visual formatting without changing the actual telephone
     * number.
     *
     * An international 00 prefix is canonicalized to +.
     */
    fun normalizeSender(
        number: String,
    ): String {
        val compact =
            number
                .trim()
                .replace(
                    Regex("[\\s\\-().]"),
                    "",
                )

        return if (
            compact.startsWith("00")
        ) {
            "+" +
                compact.drop(2)
        } else {
            compact
        }
    }

    /**
     * Require international E.164-style input:
     *
     * - leading +
     * - first digit 1-9
     * - total national/international number length within the accepted
     *   E.164-sized range
     *
     * Sentinel deliberately does not guess a country code from a local number.
     */
    fun isValidSender(
        number: String,
    ): Boolean {
        val normalized =
            normalizeSender(
                number,
            )

        return E164_REGEX.matches(
            normalized,
        )
    }

    /**
     * Exact sender comparison only.
     *
     * The historical last-nine-digits fallback is intentionally removed.
     */
    fun senderMatches(
        received: String,
        configured: String,
    ): Boolean {
        val normalizedReceived =
            normalizeSender(
                received,
            )

        val normalizedConfigured =
            normalizeSender(
                configured,
            )

        if (
            !isValidSender(
                normalizedReceived,
            ) ||
            !isValidSender(
                normalizedConfigured,
            )
        ) {
            return false
        }

        return normalizedReceived ==
            normalizedConfigured
    }

    /**
     * Match the whole logical SMS body against the configured secret.
     *
     * Leading/trailing transport whitespace is ignored, but substring and
     * case-insensitive matching are deliberately not permitted.
     */
    fun keywordMatches(
        body: String,
        keyword: String,
    ): Boolean =
        body.trim() ==
            keyword

    private val E164_REGEX =
        Regex(
            "^\\+[1-9][0-9]{7,14}$",
        )
}
