package com.localruntime.core.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsMatchRulesTest {

    @Test
    fun exactE164SenderMatches() {
        assertTrue(
            SmsMatchRules.senderMatches(
                "+61412345678",
                "+61412345678",
            ),
        )
    }

    @Test
    fun visualFormattingNormalizesBeforeExactMatch() {
        assertTrue(
            SmsMatchRules.senderMatches(
                "+61 412 345 678",
                "+61412345678",
            ),
        )
    }

    @Test
    fun international00PrefixNormalizesToPlus() {
        assertTrue(
            SmsMatchRules.senderMatches(
                "0061412345678",
                "+61412345678",
            ),
        )
    }

    @Test
    fun oldNineDigitSuffixRuleIsRejected() {
        assertFalse(
            SmsMatchRules.senderMatches(
                "+61412345678",
                "+99412345678",
            ),
        )
    }

    @Test
    fun localNumberIsNotAcceptedAsConfiguredIdentity() {
        assertFalse(
            SmsMatchRules.isValidSender(
                "0412345678",
            ),
        )
    }

    @Test
    fun completeCaseSensitiveKeywordMatches() {
        assertTrue(
            SmsMatchRules.keywordMatches(
                "CorrectHorseBattery",
                "CorrectHorseBattery",
            ),
        )
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertTrue(
            SmsMatchRules.keywordMatches(
                "  CorrectHorseBattery  ",
                "CorrectHorseBattery",
            ),
        )
    }

    @Test
    fun keywordSubstringDoesNotMatch() {
        assertFalse(
            SmsMatchRules.keywordMatches(
                "please CorrectHorseBattery now",
                "CorrectHorseBattery",
            ),
        )
    }

    @Test
    fun keywordCaseDifferenceDoesNotMatch() {
        assertFalse(
            SmsMatchRules.keywordMatches(
                "correcthorsebattery",
                "CorrectHorseBattery",
            ),
        )
    }
}
