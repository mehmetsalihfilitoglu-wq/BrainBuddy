package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.OnboardingPrefs

class OnboardingWizardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_wizard)

        val btnFinish = findViewById<Button>(R.id.btnFinish)
        btnFinish?.setOnClickListener { completeOnboarding() }

        val btnNext = findViewById<Button>(R.id.btnNext)
        btnNext?.setOnClickListener { completeOnboarding() }
    }

    private fun completeOnboarding() {
        OnboardingPrefs.setDone(this, true)
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
