package com.edumio.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release-critical fail-safe: the local premium cache trusts a value ONLY when it is `true` and its stored
 * signature matches the freshly-computed one. Every other state must resolve to Free. (§6/§9 Phase 0.)
 */
class PremiumIntegrityTest {

    private val SIG = "c2lnbmF0dXJl"     // some valid signature
    private val OTHER = "dGFtcGVyZWQ="    // a different signature

    @Test fun freeIsNeverPremium_evenWithAValidSignature() {
        assertFalse(PremiumIntegrity.isTrusted(value = false, storedSig = SIG, expectedSig = SIG))
    }

    @Test fun missingStoredSignatureFailsSafeToFree() {
        assertFalse(PremiumIntegrity.isTrusted(value = true, storedSig = null, expectedSig = SIG))
    }

    @Test fun missingExpectedSignatureFailsSafeToFree() {
        assertFalse(PremiumIntegrity.isTrusted(value = true, storedSig = SIG, expectedSig = null))
    }

    @Test fun tamperedSignatureFailsSafeToFree() {
        assertFalse("a flipped is_premium=true with a stale/forged sig must NOT unlock",
            PremiumIntegrity.isTrusted(value = true, storedSig = OTHER, expectedSig = SIG))
    }

    @Test fun differentLengthSignatureFailsSafeToFree() {
        assertFalse(PremiumIntegrity.isTrusted(value = true, storedSig = "short", expectedSig = SIG))
    }

    @Test fun validSignedPremiumIsTrusted() {
        assertTrue(PremiumIntegrity.isTrusted(value = true, storedSig = SIG, expectedSig = SIG))
    }

    @Test fun constantTimeEquals_matchesAndDiffers() {
        assertTrue(PremiumIntegrity.constantTimeEquals(SIG, SIG))
        assertFalse(PremiumIntegrity.constantTimeEquals(SIG, OTHER))
        assertFalse(PremiumIntegrity.constantTimeEquals("a", "ab"))
    }
}
