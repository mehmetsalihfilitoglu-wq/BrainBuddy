package com.brainbuddy.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.accessibility.AccessibilityUtils
import com.brainbuddy.app.accessibility.ForegroundAppBlockerService
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.SystemHealthStore
import com.brainbuddy.app.gate.GateActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SystemHealthActivity : AppCompatActivity() {

    private lateinit var healthStore: SystemHealthStore
    private lateinit var blockedStore: BlockedAppsStore

    private val gateTestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val ok = result.resultCode == RESULT_OK
        updateGateStatus(ok)
        val accOk = checkAccessibility()
        val usageOk = checkUsageStats()
        val batteryOk = checkBattery()
        val blockListOk = blockedStore.getBlockedPackages().isNotEmpty()
        healthStore.saveResults(accOk, usageOk, batteryOk, ok, blockListOk, System.currentTimeMillis())
        updateLastChecked()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, SystemHealthActivity::class.java)) return

        setContentView(R.layout.activity_system_health)
        healthStore = SystemHealthStore(this)
        blockedStore = BlockedAppsStore(this)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        refreshAll()
        setupFixButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun refreshAll() {
        val accOk = checkAccessibility()
        val usageOk = checkUsageStats()
        val batteryOk = checkBattery()
        val blockListOk = blockedStore.getBlockedPackages().isNotEmpty()

        updateAccessibilityStatus(accOk)
        updateUsageStatus(usageOk)
        updateBatteryStatus(batteryOk)
        updateBlockListStatus(blockListOk)

        val gateOk = healthStore.getLastCheckedMs().let { ts ->
            ts > 0 && (System.currentTimeMillis() - ts) < GATE_CACHE_MS
        }
        updateGateStatus(gateOk)
        updateLastChecked()
    }

    private fun checkAccessibility(): Boolean = runCatching {
        AccessibilityUtils.isServiceEnabled(this, ForegroundAppBlockerService::class.java)
    }.getOrElse { false }

    private fun checkUsageStats(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val appOps = getSystemService(android.app.AppOpsManager::class.java)
            appOps.unsafeCheckOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), packageName) == 0
        } else true
    }.getOrElse { false }

    private fun checkBattery(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(packageName) == true
        } else true
    }.getOrElse { false }

    private fun updateAccessibilityStatus(ok: Boolean) {
        findViewById<android.widget.TextView>(R.id.healthAccessibilityIcon).text = if (ok) "✅" else "❌"
        findViewById<android.widget.TextView>(R.id.healthAccessibilityStatus).text =
            if (ok) getString(R.string.health_status_ok) else getString(R.string.health_status_missing)
    }

    private fun updateUsageStatus(ok: Boolean) {
        findViewById<android.widget.TextView>(R.id.healthUsageIcon).text = if (ok) "✅" else "❌"
        findViewById<android.widget.TextView>(R.id.healthUsageStatus).text =
            if (ok) getString(R.string.health_status_ok) else getString(R.string.health_status_missing)
    }

    private fun updateBatteryStatus(ok: Boolean) {
        findViewById<android.widget.TextView>(R.id.healthBatteryIcon).text = if (ok) "✅" else "❌"
        findViewById<android.widget.TextView>(R.id.healthBatteryStatus).text =
            if (ok) getString(R.string.health_status_ok) else getString(R.string.health_status_missing)
    }

    private fun updateGateStatus(ok: Boolean) {
        findViewById<android.widget.TextView>(R.id.healthGateIcon).text = if (ok) "✅" else "❌"
        findViewById<android.widget.TextView>(R.id.healthGateStatus).text =
            if (ok) getString(R.string.health_status_ok) else getString(R.string.health_status_missing)
    }

    private fun updateBlockListStatus(ok: Boolean) {
        findViewById<android.widget.TextView>(R.id.healthBlockListIcon).text = if (ok) "✅" else "❌"
        findViewById<android.widget.TextView>(R.id.healthBlockListStatus).text =
            if (ok) getString(R.string.health_status_ok) else getString(R.string.health_status_missing)
    }

    private fun updateLastChecked() {
        val ms = healthStore.getLastCheckedMs()
        val str = if (ms > 0) {
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale("tr")).format(Date(ms))
        } else "-"
        findViewById<android.widget.TextView>(R.id.tvLastChecked).text =
            getString(R.string.health_last_checked, str)
    }

    private fun persistHealth() {
        val accOk = checkAccessibility()
        val usageOk = checkUsageStats()
        val batteryOk = checkBattery()
        val blockListOk = blockedStore.getBlockedPackages().isNotEmpty()
        val gateOk = healthStore.getLastCheckedMs().let { ts ->
            ts > 0 && (System.currentTimeMillis() - ts) < GATE_CACHE_MS
        }
        healthStore.saveResults(accOk, usageOk, batteryOk, gateOk, blockListOk, System.currentTimeMillis())
        updateLastChecked()
    }

    private fun setupFixButtons() {
        findViewById<android.widget.Button>(R.id.btnFixAccessibility).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                showFallback()
            }
        }
        findViewById<android.widget.Button>(R.id.btnFixUsage).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) {
                showFallback()
            }
        }
        findViewById<android.widget.Button>(R.id.btnFixBattery).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                showFallback()
            }
        }
        findViewById<android.widget.Button>(R.id.btnGateTest).setOnClickListener { runGateTest() }
        findViewById<android.widget.Button>(R.id.btnFixBlockList).setOnClickListener {
            try {
                startActivity(Intent(this, BlockedAppsActivity::class.java))
            } catch (e: Exception) {
                showFallback()
            }
        }
    }

    private fun runGateTest() {
        try {
            val intent = Intent(this, GateActivity::class.java).apply {
                putExtra(GateActivity.EXTRA_BLOCKED_PACKAGE, "com.brainbuddy.test.gate")
                putExtra(GateActivity.EXTRA_TEST_MODE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            gateTestLauncher.launch(intent)
        } catch (e: Exception) {
            updateGateStatus(false)
            android.util.Log.e("SystemHealth", "Gate test failed", e)
        }
    }

    private fun showFallback() {
        Toast.makeText(this, getString(R.string.health_settings_fallback), Toast.LENGTH_LONG).show()
        findViewById<android.widget.TextView>(R.id.healthSettingsFallback).visibility = android.view.View.VISIBLE
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val GATE_CACHE_MS = 5 * 60 * 1000L // 5 min
    }
}
