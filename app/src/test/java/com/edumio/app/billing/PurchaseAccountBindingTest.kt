package com.edumio.app.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A purchase must only ever grant Premium to the account it was verified for; doubt → Free. */
class PurchaseAccountBindingTest {

    @Test fun appliesToExactAccountOnly() {
        assertTrue(PurchaseAccountBinding.appliesTo("uidA", "uidA"))
        assertFalse(PurchaseAccountBinding.appliesTo("uidA", "uidB"))
        assertFalse(PurchaseAccountBinding.appliesTo(null, "uidA"))
        assertFalse(PurchaseAccountBinding.appliesTo("uidA", null))
        assertFalse(PurchaseAccountBinding.appliesTo("", ""))
    }

    @Test fun signedOutIsNeverPremium() {
        assertFalse(PurchaseAccountBinding.effectivePremium(
            currentUid = null, serverReachable = true, serverPremium = true,
            entitlementUid = "uidA", cachedPremium = true))
    }

    @Test fun serverReachable_trustsServerWhenBound() {
        assertTrue(PurchaseAccountBinding.effectivePremium("uidA", true, true, "uidA", false))
        assertFalse("server says premium but bound to another account",
            PurchaseAccountBinding.effectivePremium("uidA", true, true, "uidB", true))
        assertFalse("server says not premium",
            PurchaseAccountBinding.effectivePremium("uidA", true, false, "uidA", true))
    }

    @Test fun offline_trustsCacheOnlyForOwnAccount() {
        assertTrue(PurchaseAccountBinding.effectivePremium("uidA", false, false, "uidA", true))
        assertFalse("offline + cache belongs to another account",
            PurchaseAccountBinding.effectivePremium("uidA", false, false, "uidB", true))
        assertFalse("offline + no cache",
            PurchaseAccountBinding.effectivePremium("uidA", false, false, "uidA", false))
    }
}
