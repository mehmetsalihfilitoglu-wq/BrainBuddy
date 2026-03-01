package com.brainbuddy.app.core

import android.app.Application
import android.content.Intent
import com.brainbuddy.app.LockScreenActivity

object PermissionMonitorLauncher {

    fun scheduleCheck(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var resumedCount = 0

            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityResumed(a: android.app.Activity) {
                resumedCount++
                if (resumedCount == 1) {
                    if (PermissionMonitor.checkAndLockIfDisabled(a)) {
                        val intent = Intent(a, LockScreenActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY)
                        }
                        a.startActivity(intent)
                        a.finishAffinity()
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
