package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.OnboardingPrefs

/**
 * Launcher activity. Routes to onboarding on first launch, home screen thereafter.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val onboardingDone = OnboardingPrefs.isDone(this)
        val target = if (!onboardingDone) OnboardingWizardActivity::class.java else HomeActivity::class.java
        Log.d("MainActivity", "onboardingDone=$onboardingDone startScreen=${target.simpleName}")
        startActivity(
            Intent(this, target).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY
            )
        )
        finish()
    }
}
