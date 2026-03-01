package com.brainbuddy.app.core

import android.app.Application
import android.os.Bundle
import java.util.concurrent.TimeUnit

/**
 * Role-based access: StudentMode (default) vs ParentMode.
 * ParentMode is session-based: 5 minutes or until app goes to background.
 */
enum class AppMode {
    StudentMode,
    ParentMode
}

object AppModeManager {

    private val SESSION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3)

    @Volatile
    private var currentMode: AppMode = AppMode.StudentMode

    @Volatile
    private var parentModeEnteredAt: Long = 0L

    fun currentMode(): AppMode = currentMode

    fun isParentMode(): Boolean {
        if (currentMode != AppMode.ParentMode) return false
        if (System.currentTimeMillis() - parentModeEnteredAt > SESSION_TIMEOUT_MS) {
            currentMode = AppMode.StudentMode
            return false
        }
        return true
    }

    fun isStudentMode(): Boolean = !isParentMode()

    fun enterParentMode() {
        currentMode = AppMode.ParentMode
        parentModeEnteredAt = System.currentTimeMillis()
    }

    fun exitParentMode() {
        currentMode = AppMode.StudentMode
    }

    fun registerLifecycle(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var startedCount = 0

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {
                startedCount++
            }
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {
                startedCount--
                if (startedCount == 0) {
                    exitParentMode()
                }
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }
}
