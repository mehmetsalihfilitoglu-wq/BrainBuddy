package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.databinding.ActivityParentBinding
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ParentHubActivity : ComponentActivity() {

    private lateinit var b: ActivityParentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, ParentHubActivity::class.java)) return

        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        b.cardScreenTime.setOnClickListener {
            startActivity(Intent(this, ScreenTimeActivity::class.java))
        }
        b.cardBlockedApps.setOnClickListener {
            startActivity(Intent(this, BlockedAppsActivity::class.java))
        }
        b.cardTests.setOnClickListener {
            startActivity(Intent(this, TestSettingsActivity::class.java))
        }
        b.cardReports.setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        b.cardSecurity.setOnClickListener {
            startActivity(Intent(this, SecurityActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderSummary()
    }

    private fun renderSummary() {
        val analytics = AnalyticsStore(this)
        val blockedStore = BlockedAppsStore(this)

        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val perfsToday = analytics.getTestPerformances()
            .filter { it.tsMs >= todayStart && it.tsMs <= now }

        val todayTests = perfsToday.size

        // Ekran süresi için basit tahmini bir değer: test başına 5 dk
        val estimatedMinutes = todayTests * 5

        b.tvTodayTests.text = todayTests.toString()
        b.tvTodayScreenTime.text = getString(R.string.parent_today_screen_time_minutes, estimatedMinutes)
        b.tvBlockedAppsCount.text = blockedStore.getBlockedPackages().size.toString()
    }
}
