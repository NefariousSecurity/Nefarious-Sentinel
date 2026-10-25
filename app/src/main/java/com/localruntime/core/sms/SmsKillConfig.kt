package com.localruntime.core.sms

/**
 * Configuration for the SMS Wipe trigger.
 *
 * SMS sender identity is an eligibility filter, not a cryptographic
 * authentication mechanism. The trigger keyword remains the secret
 * authentication factor.
 */
data class SmsKillConfig(
    val enabled: Boolean = false,
    val senderNumber: String = "",
    val keyword: String = "",
) {

    /**
     * Ready only when all locally verifiable configuration requirements are
     * satisfied.
     */
    fun isReady(): Boolean =
        enabled &&
            SmsMatchRules.isValidSender(
                senderNumber,
            ) &&
            keyword.length in
                MIN_KEYWORD_LENGTH..
                    MAX_KEYWORD_LENGTH

    companion object {
        /*
         * Four characters is too small for a remotely reachable destructive
         * trigger. Alpha remediation raises the minimum to twelve.
         */
        const val MIN_KEYWORD_LENGTH =
            12

        const val MAX_KEYWORD_LENGTH =
            50
    }
}
