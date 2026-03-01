package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.BadgeDisplayHelper
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.TestPerformance
import com.brainbuddy.app.databinding.ActivityParentBinding
import com.brainbuddy.app.quiz.WrongAnswerReviewActivity
import com.google.android.material.chip.Chip
import java.util.concurrent.TimeUnit

class ParentActivity : ComponentActivity() {

    private lateinit var b: ActivityParentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, ParentActivity::class.java)) return

        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)

        val protectionPrefs = ProtectionPrefs(this)
        val wrongIds = protectionPrefs.lastFailedWrongIds()

        b.controlReviewWrong.root.visibility = if (wrongIds.isNotEmpty()) View.VISIBLE else View.GONE
        b.controlReviewWrong.root.setOnClickListener {
            if (wrongIds.isNotEmpty()) {
                val sessionJson = protectionPrefs.lastFailedSessionJson()
                startActivity(Intent(this, WrongAnswerReviewActivity::class.java).apply {
                    putStringArrayListExtra(WrongAnswerReviewActivity.EXTRA_WRONG_IDS, ArrayList(wrongIds))
                    if (sessionJson.isNotEmpty()) putExtra(WrongAnswerReviewActivity.EXTRA_SESSION_JSON, sessionJson)
                    putExtra(WrongAnswerReviewActivity.EXTRA_IS_PARENT_REVIEW, true)
                })
            }
        }

        setupControlRow(b.controlBlockedApps.root, getString(R.string.parent_blocked_apps), getString(R.string.parent_blocked_apps_sub)) {
            startActivity(Intent(this, BlockedAppsActivity::class.java))
        }
        setupControlRow(b.controlTimeSettings.root, getString(R.string.parent_time_settings), getString(R.string.parent_time_settings_sub)) {
            startActivity(Intent(this, TimeLimitsActivity::class.java))
        }
        setupControlRow(b.controlQuestionPacks.root, getString(R.string.parent_question_packs), getString(R.string.parent_question_packs_sub)) {
            startActivity(Intent(this, com.brainbuddy.app.ui.ExamPackActivity::class.java))
        }
        setupControlRow(b.controlAvatarShop.root, getString(R.string.parent_avatar_shop), getString(R.string.parent_avatar_shop_sub)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        setupControlRow(b.controlPermissions.root, getString(R.string.parent_permissions), getString(R.string.parent_permissions_sub)) {
            startActivity(Intent(this, PermissionsChecklistActivity::class.java))
        }
        setupControlRow(b.controlSettings.root, getString(R.string.parent_settings), getString(R.string.parent_settings_sub)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        setupControlRow(b.controlChangePin.root, getString(R.string.parent_change_pin), null) {
            startActivity(Intent(this, PinLockActivity::class.java).apply {
                putExtra(PinLockActivity.EXTRA_MODE, "change")
                putExtra(PinLockActivity.EXTRA_TARGET, "ParentActivity")
            })
        }

        setupControlRow(b.controlReviewWrong.root, getString(R.string.parent_review_wrong), getString(R.string.parent_review_wrong_sub))
    }

    private fun setupControlRow(
        root: View,
        title: String,
        subtitle: String?,
        onClick: (() -> Unit)? = null
    ) {
        root.findViewById<TextView>(R.id.title)?.text = title
        val subTv = root.findViewById<TextView>(R.id.subtitle)
        if (subtitle != null) {
            subTv?.text = subtitle
            subTv?.visibility = View.VISIBLE
        } else {
            subTv?.visibility = View.GONE
        }
        if (onClick != null) {
            root.setOnClickListener { onClick() }
        }
    }

    override fun onResume() {
        super.onResume()
        renderDashboard()
    }

    private fun renderDashboard() {
        val gam = GamificationStore(this)
        val analytics = AnalyticsStore(this)

        b.tvLevelNum.text = gam.level().toString()
        b.tvXpNum.text = gam.xp().toString()
        b.tvStreakNum.text = gam.streakDays().toString()

        val (xpInto, xpPer) = gam.xpProgress()
        val pct = if (xpPer > 0) (100 * xpInto / xpPer) else 0
        b.progressToNextLevel.max = 100
        b.progressToNextLevel.progress = pct

        val badges = gam.badges().sorted()
        val milestoneBadges = gam.milestoneBadges()
        val allBadges = (badges + milestoneBadges).distinct()
        b.badgeChips.removeAllViews()
        if (allBadges.isEmpty()) {
            val chip = Chip(this).apply {
                text = "Henüz rozet yok"
                isClickable = false
            }
            b.badgeChips.addView(chip)
        } else {
            allBadges.forEach { id ->
                val displayName = BadgeDisplayHelper.getDisplayName(id)
                val emoji = BadgeDisplayHelper.getBadgeEmoji(id)
                val chip = Chip(this).apply {
                    text = "$emoji $displayName"
                    isClickable = false
                }
                b.badgeChips.addView(chip)
            }
        }

        val now = System.currentTimeMillis()
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val sessions = analytics.getSessions().filter { it.tsMs >= weekAgo }

        val totalQuizzes = sessions.size
        val totalQuestions = sessions.sumOf { it.total }
        val totalCorrect = sessions.sumOf { it.correct }
        val totalWrong = (totalQuestions - totalCorrect).coerceAtLeast(0)
        val totalPoints = sessions.sumOf { it.pointsEarned }
        val accuracy = if (totalQuestions > 0) (100.0 * totalCorrect / totalQuestions) else 0.0

        b.donutChart.progress = accuracy.toFloat()
        b.miniBarChart.correct = totalCorrect
        b.miniBarChart.wrong = totalWrong
        b.miniBarChart.blank = 0

        b.tvWeeklyTests.text = totalQuizzes.toString()
        b.tvWeeklyQuestions.text = totalQuestions.toString()
        b.tvWeeklyCorrect.text = totalCorrect.toString()
        b.tvWeeklyAccuracy.text = "%.0f%%".format(accuracy)
        b.tvWeeklyXp.text = "$totalPoints XP"

        val topicCounts = analytics.getTopicMasteryWithCounts()
        val barData = topicCounts.map { (topic, tc) ->
            com.brainbuddy.app.ui.BarChartView.BarData(topic, tc.correct, tc.total)
        }.take(6)
        b.barChart.data = barData

        val strongest = analytics.getStrongestTopicsWithCounts(3)
        b.chipStrong.removeAllViews()
        if (strongest.isEmpty()) {
            val chip = Chip(this).apply { text = "-"; isClickable = false }
            b.chipStrong.addView(chip)
        } else {
            strongest.forEach { (topic, _) ->
                val chip = Chip(this).apply {
                    text = topic
                    isClickable = false
                }
                b.chipStrong.addView(chip)
            }
        }

        val weakest = analytics.getWeakestTopicsWithCounts(3)
        b.chipWeak.removeAllViews()
        if (weakest.isEmpty()) {
            val chip = Chip(this).apply { text = "-"; isClickable = false }
            b.chipWeak.addView(chip)
        } else {
            weakest.forEach { (topic, _) ->
                val chip = Chip(this).apply {
                    text = topic
                    isClickable = false
                }
                b.chipWeak.addView(chip)
            }
        }

        val perfs = analytics.getTestPerformances()
        val passed = perfs.count { it.passed }
        val gateTotal = perfs.size
        val gatePassPct = if (gateTotal > 0) (100.0 * passed / gateTotal) else 100.0
        b.pillGatePass.visibility = View.VISIBLE
        b.pillGatePass.text = "Geçiş: %.0f%%".format(gatePassPct)
    }
}
