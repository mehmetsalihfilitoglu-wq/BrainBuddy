package com.brainbuddy.app.ui

import android.content.Intent
import com.brainbuddy.app.core.ParentAccessGuard
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.activity.ComponentActivity
import com.brainbuddy.app.R

class ProtectionInactiveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, ProtectionInactiveActivity::class.java)) return

        setContentView(R.layout.activity_protection_inactive)

        findViewById<Button>(R.id.openAccessibilitySettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}