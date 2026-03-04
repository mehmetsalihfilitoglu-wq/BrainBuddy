package com.brainbuddy.app.core

import android.app.Application

/**
 * On app resume (foreground), check if AccessibilityService is disabled.
 * If so: launch ParentLockActivity (PinLockActivity) - PIN required until re-enabled.
 */
object PermissionMonitorLauncher {

    fun scheduleCheck(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var resumedCount = 0

            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityResumed(a: android.app.Activity) {
                resumedCount++
                if (resumedCount == 1) {
                    if (a is com.brainbuddy.app.ui.PinLockActivity) return
                    // A) Accessibility OFF → ParentLockActivity (PinLockActivity)
                    if (LockModeMonitor.checkAndSetLockIfNeeded(a) || LockModeMonitor.isLockModeActive(a)) {
                        LockModeMonitor.launchParentLockActivity(a)
                    }
                }
            }
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {
                resumedCount = (resumedCount - 1).coerceAtLeast(0)
            }
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
    }
}
