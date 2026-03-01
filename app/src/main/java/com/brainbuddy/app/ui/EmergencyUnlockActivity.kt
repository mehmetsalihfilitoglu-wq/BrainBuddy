package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.PermissionMonitor
import com.brainbuddy.app.security.EmergencyCodeManager

/** Emergency unlock: enter Emergency Code to reach Parent area when gate/lock is stuck. */
class EmergencyUnlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_unlock)

        val emergencyManager = EmergencyCodeManager(this)
        if (!emergencyManager.isEmergencyCodeSet()) {
            finish()
            return
        }

        val input = findViewById<android.widget.EditText>(R.id.emergencyInput)
        val error = findViewById<android.widget.TextView>(R.id.emergencyError)
        val btnUnlock = findViewById<android.widget.Button>(R.id.btnEmergencyUnlock)
        val btnBack = findViewById<View>(R.id.btnEmergencyBack)

        btnUnlock.setOnClickListener {
            error.visibility = View.GONE
            val code = input.text.toString().toCharArray()
            if (code.size < 6) {
                error.text = getString(R.string.emergency_code_too_short)
                error.visibility = View.VISIBLE
                code.fill('\u0000')
                return@setOnClickListener
            }
            if (emergencyManager.verifyEmergencyCode(code)) {
                code.fill('\u0000')
                ProtectionPrefs(this).setPermissionDisabledLockReason("")
                ProtectionPrefs(this).setUserLocked(false)
                PermissionMonitor.cancelProtectionOffNotification(this)
                AppModeManager.enterParentMode()
                startActivity(Intent(this, ParentActivity::class.java))
                finish()
            } else {
                code.fill('\u0000')
                error.text = getString(R.string.emergency_code_incorrect)
                error.visibility = View.VISIBLE
            }
        }

        btnBack?.setOnClickListener { finish() }
    }
}
