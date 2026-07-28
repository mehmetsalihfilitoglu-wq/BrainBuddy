package com.edumio.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.edumio.app.core.AppRouter
import com.edumio.app.core.OnboardingPrefs
import com.edumio.app.core.StudyAreaManager

/**
 * Launcher / splash activity. Shows the EDUmio mascot + wordmark briefly (over the Android 12
 * system splash), then routes to onboarding on first launch or home thereafter.
 */
class MainActivity : AppCompatActivity() {

    private var routed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One-time, idempotent: stamp the legacy single career onto the active
        // study-area profile so pre-multi-area users keep their data as area #1.
        StudyAreaManager.ensureMigrated(this)
        setContentView(R.layout.activity_splash)
        Handler(Looper.getMainLooper()).postDelayed({ route() }, SPLASH_MS)
    }

    private fun route() {
        if (routed || isFinishing) return
        routed = true
        val onboardingDone = OnboardingPrefs.isDone(this)
        // v1 is account-free: route purely on locally-persisted onboarding state. A fresh install runs the
        // onboarding wizard (welcome → exam → name → Home); a returning user opens straight into Home with
        // no network and no sign-in. See [AppRouter].
        val target = when (AppRouter.decide(onboardingDone = onboardingDone)) {
            AppRouter.Destination.HOME -> HomeActivity::class.java
            AppRouter.Destination.ONBOARDING -> OnboardingWizardActivity::class.java
        }
        Log.d("MainActivity", "onboardingDone=$onboardingDone startScreen=${target.simpleName}")
        // NOTE: NO_HISTORY was applied to the LAUNCHED screen (Home), which made the OS finish Home the
        // moment the user opened any sub-screen — so system BACK from a sub-screen found an empty task and
        // closed the app. Removed. The splash still doesn't linger because we finish() below + CLEAR_TASK.
        startActivity(
            Intent(this, target).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        )
        finish()
    }

    private companion object { const val SPLASH_MS = 750L }
}
