package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.security.PinManager

class PinLockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "pin_mode" // "verify" | "set" | "change"
        const val EXTRA_TARGET = "target_activity" // optional: class name to launch on success
    }

    private lateinit var pinManager: PinManager
    private var mode: String = "verify"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_lock)

        pinManager = PinManager(this)
        mode = intent.getStringExtra(EXTRA_MODE)
            ?: if (pinManager.isPinSet()) "verify" else "set"

        val title = findViewById<android.widget.TextView>(R.id.pinTitle)
        val modeLabel = findViewById<android.widget.TextView>(R.id.pinModeLabel)
        val lengthGroup = findViewById<android.widget.RadioGroup>(R.id.pinLengthGroup)
        val confirmRow = findViewById<View>(R.id.confirmRow)
        val pinInput = findViewById<android.widget.EditText>(R.id.pinInput)
        val pinConfirm = findViewById<android.widget.EditText>(R.id.pinConfirm)
        val errorText = findViewById<android.widget.TextView>(R.id.pinError)
        val actionBtn = findViewById<android.widget.Button>(R.id.pinActionBtn)

        when (mode) {
            "set", "change" -> {
                title.text = if (mode == "change") "PIN Değiştir" else "Veli PIN'i Ayarla"
                modeLabel.text = "4 veya 6 haneli bir PIN belirleyin."
                lengthGroup.visibility = View.VISIBLE
                confirmRow.visibility = View.VISIBLE
                actionBtn.text = "Kaydet"
            }
            else -> {
                title.text = "Veli Modu"
                modeLabel.text = "Devam etmek için PIN girin."
                lengthGroup.visibility = View.GONE
                confirmRow.visibility = View.GONE
                actionBtn.text = "Kilidi Aç"
            }
        }

        actionBtn.setOnClickListener {
            errorText.text = ""
            val pin = pinInput.text.toString().toCharArray()
            val confirm = pinConfirm.text.toString().toCharArray()

            when (mode) {
                "verify" -> {
                    if (pin.size != 4 && pin.size != 6) {
                        errorText.text = getString(R.string.pin_length_error_generic)
                        return@setOnClickListener
                    }
                    if (pinManager.verifyPin(pin)) {
                        pin.fill('\u0000')
                        val prefs = ProtectionPrefs(this)
                        if (prefs.isPermissionLocked()) {
                            prefs.setPermissionDisabledLockReason("")
                            prefs.setUserLocked(false)
                            com.brainbuddy.app.core.PermissionMonitor.cancelProtectionOffNotification(this)
                        }
                        AppModeManager.enterParentMode()
                        navigateToTarget()
                        finish()
                    } else {
                        errorText.text = getString(R.string.pin_incorrect)
                    }
                }
                "set", "change" -> {
                    val len = if (lengthGroup.checkedRadioButtonId == R.id.pinLen6) 6 else 4
                    if (pin.size != len || confirm.size != len) {
                        errorText.text = getString(R.string.pin_length_error_generic)
                        return@setOnClickListener
                    }
                    if (!pin.contentEquals(confirm)) {
                        errorText.text = getString(R.string.pin_mismatch)
                        return@setOnClickListener
                    }
                    pinManager.setPin(pin)
                    pin.fill('\u0000')
                    confirm.fill('\u0000')
                    Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show()
                    AppModeManager.enterParentMode()
                    navigateToTarget()
                    finish()
                }
            }
        }
    }

    private fun navigateToTarget() {
        val targetName = intent.getStringExtra(EXTRA_TARGET)
        val target = when (targetName) {
            "ParentActivity" -> ParentActivity::class.java
            "ClassroomJoin", "ClassroomLeave" -> com.brainbuddy.app.classroom.ClassroomActivity::class.java
            "SettingsActivity" -> SettingsActivity::class.java
            "BlockedAppsActivity" -> BlockedAppsActivity::class.java
            "TimeLimitsActivity" -> TimeLimitsActivity::class.java
            "QuizSettingsActivity" -> QuizSettingsActivity::class.java
            "ProtectionInactiveActivity" -> ProtectionInactiveActivity::class.java
            "PermissionsChecklistActivity" -> PermissionsChecklistActivity::class.java
            "ProfileManageActivity" -> ProfileManageActivity::class.java
            "RewardContractActivity" -> com.brainbuddy.app.reward.RewardContractActivity::class.java
            "JuniorSettingsActivity" -> com.brainbuddy.app.junior.JuniorSettingsActivity::class.java
            "JuniorReportActivity" -> com.brainbuddy.app.junior.JuniorReportActivity::class.java
            else -> ParentActivity::class.java
        }
        startActivity(Intent(this, target))
    }
}
