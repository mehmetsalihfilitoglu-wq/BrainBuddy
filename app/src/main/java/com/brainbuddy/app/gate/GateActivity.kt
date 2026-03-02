package com.brainbuddy.app.gate

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.quiz.QuizActivity

/**
 * Fullscreen gate shown when user tries to open a blocked app.
 * No back escape; must pass quiz or stay.
 */
class GateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_TEST_MODE = "gate_test_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent?.getBooleanExtra(EXTRA_TEST_MODE, false) == true) {
            setResult(RESULT_OK)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gate)

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val blockedPkg = (intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "").trim().takeIf { it.isNotEmpty() } ?: ""

        findViewById<android.widget.TextView>(R.id.tvGateMessage).text =
            getString(R.string.gate_quiz_required)

        findViewById<android.widget.Button>(R.id.btnStartQuiz).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java).apply {
                putExtra(QuizActivity.EXTRA_GATE_MODE, true)
                putExtra(QuizActivity.EXTRA_BLOCKED_PACKAGE, blockedPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
        }

        val btnEmergency = findViewById<android.widget.TextView>(R.id.btnGateEmergency)
        if (btnEmergency != null && com.brainbuddy.app.security.EmergencyCodeManager(this).isEmergencyCodeSet()) {
            btnEmergency.visibility = android.view.View.VISIBLE
            btnEmergency.setText(R.string.btn_emergency_unlock)
            btnEmergency.setOnClickListener {
                startActivity(Intent(this, com.brainbuddy.app.ui.EmergencyUnlockActivity::class.java))
                finish()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Consume back - no escape
            }
        })
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // User left - accessibility will re-open if they try blocked app again
    }
}
