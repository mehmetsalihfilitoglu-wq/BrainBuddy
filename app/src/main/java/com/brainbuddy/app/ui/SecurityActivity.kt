package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.accessibility.AccessibilityUtils
import com.brainbuddy.app.accessibility.ForegroundAppBlockerService
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.security.PinManager

class SecurityActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, SecurityActivity::class.java)) return

        setContentView(R.layout.activity_security)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Güvenlik & PIN"

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnChangePin).setOnClickListener {
            startActivity(Intent(this, PinLockActivity::class.java).apply {
                putExtra(PinLockActivity.EXTRA_MODE, "change")
                putExtra(PinLockActivity.EXTRA_TARGET, "SecurityActivity")
            })
        }

        updateAccessibilityStatus()
        updateUsageStatus()

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAccessibility).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {}
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUsage).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {}
        }

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updateUsageStatus()
    }

    private fun updateAccessibilityStatus() {
        val enabled = runCatching {
            AccessibilityUtils.isServiceEnabled(this, ForegroundAppBlockerService::class.java)
        }.getOrElse { false }
        findViewById<android.widget.TextView>(R.id.tvAccessibilityStatus).text =
            if (enabled) getString(R.string.perm_status_ok) else getString(R.string.perm_status_missing)
    }

    private fun updateUsageStatus() {
        val enabled = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val appOps = getSystemService(android.app.AppOpsManager::class.java)
                appOps.unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), packageName) == 0
            } else true
        }.getOrElse { false }
        findViewById<android.widget.TextView>(R.id.tvUsageStatus).text =
            if (enabled) getString(R.string.perm_status_ok) else getString(R.string.perm_status_missing)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
