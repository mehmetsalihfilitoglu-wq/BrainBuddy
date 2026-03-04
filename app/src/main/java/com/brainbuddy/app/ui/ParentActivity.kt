package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.databinding.ActivityParentBinding
import java.util.Calendar

class ParentHubActivity : ComponentActivity() {

    private lateinit var b: ActivityParentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, ParentHubActivity::class.java)) return

        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val categories = listOf(
            ParentCategoryItem(
                R.string.parent_card_screen_time,
                R.string.parent_card_screen_time_sub,
                R.drawable.ic_timer
            ) { startActivity(Intent(this, ScreenTimeActivity::class.java)) },
            ParentCategoryItem(
                R.string.parent_card_blocked_apps,
                R.string.parent_card_blocked_apps_sub,
                R.drawable.ic_block
            ) { startActivity(Intent(this, BlockedAppsActivity::class.java)) },
            ParentCategoryItem(
                R.string.parent_card_tests,
                R.string.parent_card_tests_sub,
                R.drawable.ic_quiz
            ) { startActivity(Intent(this, TestSettingsActivity::class.java)) },
            ParentCategoryItem(
                R.string.parent_card_reports,
                R.string.parent_card_reports_sub,
                R.drawable.ic_review
            ) { startActivity(Intent(this, ReportsActivity::class.java)) },
            ParentCategoryItem(
                R.string.email_reports_setup_title,
                R.string.email_reports_setup_subtitle,
                R.drawable.ic_review
            ) { startActivity(Intent(this, EmailReportsSetupActivity::class.java)) },
            ParentCategoryItem(
                R.string.parent_card_security,
                R.string.parent_card_security_sub,
                R.drawable.ic_lock
            ) { startActivity(Intent(this, SecurityActivity::class.java)) },
            ParentCategoryItem(
                R.string.parent_settings,
                R.string.parent_settings_sub,
                R.drawable.ic_settings
            ) { startActivity(Intent(this, SettingsActivity::class.java)) }
        )

        b.gridParentCategories.layoutManager = GridLayoutManager(this, 2)
        b.gridParentCategories.adapter = ParentCategoryAdapter(categories)
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
        b.tvTodayScreenTime.text = getString(R.string.parent_summary_minutes_value, estimatedMinutes)
        b.tvBlockedAppsCount.text = blockedStore.getBlockedPackages().size.toString()
    }
}
