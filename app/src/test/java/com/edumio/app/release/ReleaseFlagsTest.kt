package com.edumio.app.release

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spark-safe mode must disable every backend-dependent feature and keep Analytics/Crashlytics on. */
class ReleaseFlagsTest {

    @Test fun sparkSafeDisablesAllBackendDependentFeatures() {
        assertFalse(ReleaseFlags.cloudAccountEnabled(sparkSafe = true))
        assertFalse(ReleaseFlags.cloudSyncEnabled(sparkSafe = true))
        assertFalse(ReleaseFlags.purchasesEnabled(sparkSafe = true))
        assertFalse(ReleaseFlags.serverEntitlementEnabled(sparkSafe = true))
    }

    @Test fun analyticsAndCrashlyticsStayOnInSparkSafe() {
        assertTrue(ReleaseFlags.analyticsEnabled(sparkSafe = true))
        assertTrue(ReleaseFlags.crashlyticsEnabled(sparkSafe = true))
    }

    @Test fun disablingSparkSafeReenablesEverything() {
        assertTrue(ReleaseFlags.cloudAccountEnabled(sparkSafe = false))
        assertTrue(ReleaseFlags.cloudSyncEnabled(sparkSafe = false))
        assertTrue(ReleaseFlags.purchasesEnabled(sparkSafe = false))
        assertTrue(ReleaseFlags.serverEntitlementEnabled(sparkSafe = false))
        assertTrue(ReleaseFlags.analyticsEnabled(sparkSafe = false))
        assertTrue(ReleaseFlags.crashlyticsEnabled(sparkSafe = false))
    }
}
