package com.brainbuddy.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.accessibility.AccessibilityUtils
import com.brainbuddy.app.accessibility.ForegroundAppBlockerService
import com.brainbuddy.app.databinding.ActivityPermissionsChecklistBinding

class PermissionsChecklistActivity : AppCompatActivity() {

    private lateinit var b: ActivityPermissionsChecklistBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        b = ActivityPermissionsChecklistBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        refreshStatus()
        setupClicks()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val accEnabled = AccessibilityUtils.isServiceEnabled(this, ForegroundAppBlockerService::class.java)
        if (accEnabled) com.brainbuddy.app.core.PermissionMonitor.cancelProtectionOffNotification(this)
        b.permAccessibilityStatus.text = if (accEnabled) getString(R.string.perm_status_ok) else getString(R.string.perm_status_missing)
        b.permAccessibilityStatus.setTextColor(if (accEnabled) getColor(R.color.bb_turquoise) else getColor(R.color.bb_error))
        b.btnAccessibility.setText(if (accEnabled) R.string.perm_recheck else R.string.perm_enable)

        val usageGranted = try {
            val appOps = getSystemService(android.app.AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), packageName)
            } else 0
            mode == 0
        } catch (_: Exception) { false }
        b.permUsageStatus.text = if (usageGranted) getString(R.string.perm_status_ok) else getString(R.string.perm_status_optional)
        b.permUsageStatus.setTextColor(getColor(android.R.color.darker_gray))

        b.permNotificationStatus.text = getString(R.string.perm_status_optional)
        b.permBatteryStatus.text = getString(R.string.perm_status_recommended)
    }

    private fun setupClicks() {
        b.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        b.btnUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        b.btnNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                })
            }
        }
        b.btnBattery.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
