package com.brainbuddy.app

import android.content.Intent
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.accessibility.AccessibilityUtils
import com.brainbuddy.app.accessibility.ForegroundAppBlockerService
import com.brainbuddy.app.core.AppGroupPresets
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.KillSwitchPrefs
import com.brainbuddy.app.core.OnboardingPrefs
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.ScheduleStore
import com.brainbuddy.app.security.EmergencyCodeManager
import com.brainbuddy.app.security.PinManager
import com.brainbuddy.app.ui.BlockedAppsActivity

class OnboardingWizardActivity : AppCompatActivity() {

    private var step = 0
    private val pinManager by lazy { PinManager(this) }
    private val emergencyManager by lazy { EmergencyCodeManager(this) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_wizard)

        showStep(0)
    }

    private fun showStep(s: Int) {
        step = s
        val container = findViewById<ViewGroup>(R.id.wizardContainer)
        container.removeAllViews()
        when (s) {
            0 -> addWelcomeStep()
            1 -> addPinStep()
            2 -> addEmergencyStep()
            3 -> addAccessibilityStep()
            4 -> addBlockedAppsStep()
            5 -> addIntervalStep()
            6 -> addFinishStep()
        }
    }

    private fun addWelcomeStep() {
        val container = findViewById<ViewGroup>(R.id.wizardContainer)
        val v = layoutInflater.inflate(R.layout.wizard_step_welcome, container, false)
        v.findViewById<Button>(R.id.btnNext).setOnClickListener { showStep(1) }
        container.addView(v)
    }

    private fun addPinStep() {
        val container = findViewById<ViewGroup>(R.id.wizardContainer)
        val v = layoutInflater.inflate(R.layout.wizard_step_pin, container, false)
        val pinInput = v.findViewById<EditText>(R.id.pinInput)
        val pinConfirm = v.findViewById<EditText>(R.id.pinConfirm)
        val error = v.findViewById<TextView>(R.id.pinError)
        v.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val p1 = pinInput.text.toString().toCharArray()
            val p2 = pinConfirm.text.toString().toCharArray()
            if (p1.size != 4 && p1.size != 6) {
                error.text = getString(R.string.pin_length_error_generic)
                return@setOnClickListener
            }
            if (!p1.contentEquals(p2)) {
                error.text = getString(R.string.pin_mismatch)
                return@setOnClickListener
            }
            pinManager.setPin(p1)
            p1.fill(' ')
            p2.fill(' ')
            error.text = ""
            com.brainbuddy.app.core.AppModeManager.enterParentMode()
            showStep(2)
        }
        container.addView(v)
    }

    private fun addEmergencyStep() {
        val container = findViewById<ViewGroup>(R.id.wizardContainer)
        val v = layoutInflater.inflate(R.layout.wizard_step_emergency, container, false)
        val input = v.findViewById<EditText>(R.id.emergencyInput)
        val confirm = v.findViewById<EditText>(R.id.emergencyConfirm)
        val error = v.findViewById<TextView>(R.id.emergencyError)
        v.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val code1 = input.text.toString().toCharArray()
            val code2 = confirm.text.toString().toCharArray()
            if (code1.size < 6) {
                error.text = getString(R.string.emergency_code_too_short)
                return@setOnClickListener
            }
            if (!code1.contentEquals(code2)) {
                error.text = "Kodlar eşleşmiyor"
                return@setOnClickListener
            }
            emergencyManager.setEmergencyCode(code1)
            showStep(3)
        }
        container.addView(v)
    }

    private fun addAccessibilityStep() {
        val container = findViewById<ViewGroup>(R.id.wizardContainer)
        val v = layoutInflater.inflate(R.layout.wizard_step_accessibility, container, false)
        v.findViewById<Button>(R.id.btnOpenAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        v.findViewById<Button>(R.id.btnNext).setOnClickListener {
            if (AccessibilityUtils.isServiceEnabled(this, ForegroundAppBlockerService::class.java)) {
                showStep(4)
            } else {
                Toast.makeText(this, "Lütfen önce BrainBuddy erişilebilirlik iznini açın", Toast.LENGTH_LONG).show()
            }
        }
        container.addView(v)
    }

    private fun addBlockedAppsStep() {
        val container = findViewById<ViewGroup>(R.id.wizardContainer)
        val v = layoutInflater.inflate(R.layout.wizard_step_blocked_apps, container, false)
        v.findViewById<Button>(R.id.btnSelectApps).setOnClickListener {
            startActivity(Intent(this, BlockedAppsActivity::class.java))
        }
        v.findViewById<Button>(R.id.btnPresetSocial).setOnClickListener {
            startActivity(Intent(this, BlockedAppsActivity::class.java).apply {
                putExtra(BlockedAppsActivity.EXTRA_OPEN_SOCIAL_PRESET, true)
            })
        }
        v.findViewById<Button>(R.id.btnNext).setOnClickListener { showStep(5) }
        container.addView(v)
    }

    private fun addIntervalStep() {
        val container = findViewById<ViewGroup>(R.id.wizardContainer)
        val v = layoutInflater.inflate(R.layout.wizard_step_interval, container, false)
        val rg = v.findViewById<RadioGroup>(R.id.intervalGroup)
        rg.check(R.id.interval30)
        v.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val mins = when (rg.checkedRadioButtonId) {
                R.id.interval45 -> 45
                R.id.interval60 -> 60
                else -> 30
            }
            ProtectionPrefs(this).setQuizIntervalMinutes(mins)
            ProtectionPrefs(this).setProtectionEnabled(true)
            showStep(6)
        }
        container.addView(v)
    }

    private fun addFinishStep() {
        val container = findViewById<ViewGroup>(R.id.wizardContainer)
        val v = layoutInflater.inflate(R.layout.wizard_step_finish, container, false)
        v.findViewById<Button>(R.id.btnFinish).setOnClickListener {
            OnboardingPrefs.setDone(this, true)
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        container.addView(v)
    }
}
