package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.OnboardingPrefs
import com.brainbuddy.app.core.ProtectionPrefs

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = when {
            ProtectionPrefs(this).userLocked() -> LockScreenActivity::class.java
            OnboardingPrefs.isDone(this) -> HomeActivity::class.java
            else -> OnboardingActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
    }
}
