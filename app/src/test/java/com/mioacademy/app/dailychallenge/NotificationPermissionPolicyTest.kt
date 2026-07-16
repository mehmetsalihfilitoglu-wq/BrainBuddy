package com.mioacademy.app.dailychallenge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the notification-permission decision logic. */
class NotificationPermissionPolicyTest {

    private val P = NotificationPermissionPolicy

    @Test
    fun below33NeverNeedsRuntimePermission() {
        assertFalse(P.isRuntimePermissionRequired(24))
        assertFalse(P.isRuntimePermissionRequired(32))
        assertFalse("no prompt below 33", P.shouldAsk(sdkInt = 30, granted = false, alreadyAsked = false))
    }

    @Test
    fun asksOnceOnAndroid13WhenNotGranted() {
        assertTrue(P.isRuntimePermissionRequired(33))
        assertTrue("first time on 33, not granted", P.shouldAsk(sdkInt = 33, granted = false, alreadyAsked = false))
    }

    @Test
    fun neverAsksWhenAlreadyGranted() {
        assertFalse(P.shouldAsk(sdkInt = 34, granted = true, alreadyAsked = false))
    }

    @Test
    fun respectsPriorDenial_neverReasks() {
        assertFalse("denied once → don't nag", P.shouldAsk(sdkInt = 34, granted = false, alreadyAsked = true))
    }

    @Test
    fun offersSettingsPathOnlyAfterDenial() {
        // asked before, still not granted → offer the settings recovery path
        assertTrue(P.shouldOfferSettings(sdkInt = 34, granted = false, alreadyAsked = true))
        // not yet asked → we prompt instead of routing to settings
        assertFalse(P.shouldOfferSettings(sdkInt = 34, granted = false, alreadyAsked = false))
        // granted → nothing to offer
        assertFalse(P.shouldOfferSettings(sdkInt = 34, granted = true, alreadyAsked = true))
        // below 33 → not applicable
        assertFalse(P.shouldOfferSettings(sdkInt = 30, granted = false, alreadyAsked = true))
    }
}
