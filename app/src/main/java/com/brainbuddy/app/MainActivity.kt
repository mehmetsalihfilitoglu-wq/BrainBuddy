package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.OnboardingPrefs
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = ProtectionPrefs(this)
        val target = when {
            prefs.userLocked() || prefs.isPermissionLocked() -> LockScreenActivity::class.java
            !OnboardingPrefs.isDone(this) -> OnboardingActivity::class.java
            ProfileStore(this).getProfiles().size > 1 -> com.brainbuddy.app.ui.ProfileSelectionActivity::class.java
            else -> HomeActivity::class.java
        }
        startActivity(Intent(this, target).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        ))
        finish()
    }
}
