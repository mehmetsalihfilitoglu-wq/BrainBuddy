package com.edumio.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.edumio.app.core.OnboardingPrefs
import com.edumio.app.core.StudyAreaManager

/**
 * Launcher activity. Routes to onboarding on first launch, home screen thereafter.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One-time, idempotent: stamp the legacy single career onto the active
        // study-area profile so pre-multi-area users keep their data as area #1.
        StudyAreaManager.ensureMigrated(this)
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
