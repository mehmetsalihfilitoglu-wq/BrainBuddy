package com.edumio.app.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Premium state mapping must fail safe to Free for every non-active state. */
class EntitlementStateTest {

    private val now = 1_000_000L

    @Test fun activeAndGraceArePremium() {
        assertTrue(EntitlementState.isPremium("ACTIVE", null, now))
        assertTrue(EntitlementState.isPremium("IN_GRACE_PERIOD", null, now))
    }

    @Test fun canceledIsPremiumOnlyUntilExpiry() {
        assertTrue(EntitlementState.isPremium("CANCELED", now + 1, now))
        assertFalse(EntitlementState.isPremium("CANCELED", now - 1, now))
        assertFalse(EntitlementState.isPremium("CANCELED", null, now))
    }

    @Test fun nonActiveStatesAreFree() {
        for (s in listOf("ON_HOLD", "PAUSED", "EXPIRED", "REVOKED", "PENDING", "GARBAGE", null)) {
            assertFalse("$s must be Free", EntitlementState.isPremium(s, now + 10_000, now))
        }
    }

    @Test fun caseInsensitive() {
        assertTrue(EntitlementState.isPremium("active", null, now))
    }
}
