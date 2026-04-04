package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.CrashRecoveryPrefs
import com.brainbuddy.app.core.LockModeMonitor
import com.brainbuddy.app.core.OnboardingPrefs
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs

/**
 * Launcher activity. Always routes to appropriate screen on app (re)launch.
 * Gate/Result must not stay stuck - clear task ensures fresh navigation.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashRecoveryPrefs.recordStableRun(this)

        val prefs = ProtectionPrefs(this)
        // A) Accessibility OFF: immediately ParentLockActivity (PinLockActivity)
        if (LockModeMonitor.checkAndSetLockIfNeeded(this) || LockModeMonitor.isLockModeActive(this)) {
            LockModeMonitor.launchParentLockActivity(this)
            return
        }
        val onboardingDone = OnboardingPrefs.isDone(this)
        val target = when {
            prefs.userLocked() || prefs.isPermissionLocked() -> LockScreenActivity::class.java
            CrashRecoveryPrefs.isProtectionDisabledByCrash(this) -> CrashRecoveryWarningActivity::class.java
            !onboardingDone -> OnboardingWizardActivity::class.java
            ProfileStore(this).getProfiles().size > 1 -> com.brainbuddy.app.ui.ProfileSelectionActivity::class.java
            else -> HomeActivity::class.java
        }
        Log.d("MainActivity", "onboardingDone=$onboardingDone startScreen=${target.simpleName}")
        startActivity(Intent(this, target).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        ))
    }
}
