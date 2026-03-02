package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.avatar.AvatarStore
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.KillSwitchPrefs
import com.brainbuddy.app.core.PackageNameHelper
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.ReportStore
import com.brainbuddy.app.core.SystemHealthStore
import com.brainbuddy.app.core.TopicCounts
import com.brainbuddy.app.databinding.ActivityParentBinding
import com.brainbuddy.app.quiz.Subject
import com.brainbuddy.app.quiz.WrongAnswerReviewActivity
import com.brainbuddy.app.quiz.WrongQuestionStore
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ParentActivity : ComponentActivity() {

    private lateinit var b: ActivityParentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, ParentActivity::class.java)) return

        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnPermissions.setOnClickListener {
            startActivity(Intent(this, PermissionsChecklistActivity::class.java))
        }
        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val protectionPrefs = ProtectionPrefs(this)
        val wrongIds = protectionPrefs.lastFailedWrongIds()

        val wrongStoreInit = WrongQuestionStore(this)
        val impByTopic = wrongStoreInit.getImprovementByTopic()
        val impLines = impByTopic.entries.filter { it.value.totalWrong > 0 }.take(3)
            .joinToString(" • ") { (t, i) -> "$t: ${i.totalWrong}→${i.fixedCount} düz." }
        val reviewSub = if (impLines.isNotBlank()) impLines else getString(R.string.parent_review_wrong_sub)
        setupControlRow(b.controlReviewWrong.root, R.drawable.ic_review, getString(R.string.parent_review_wrong), reviewSub) {
            if (wrongIds.isNotEmpty()) {
                val sessionJson = protectionPrefs.lastFailedSessionJson()
                startActivity(Intent(this, WrongAnswerReviewActivity::class.java).apply {
                    putStringArrayListExtra(WrongAnswerReviewActivity.EXTRA_WRONG_IDS, ArrayList(wrongIds))
                    if (sessionJson.isNotEmpty()) putExtra(WrongAnswerReviewActivity.EXTRA_SESSION_JSON, sessionJson)
                    putExtra(WrongAnswerReviewActivity.EXTRA_IS_PARENT_REVIEW, true)
                })
            } else {
                Toast.makeText(this, getString(R.string.parent_no_wrong_yet), Toast.LENGTH_SHORT).show()
            }
        }

        setupControlRow(b.controlBlockedApps.root, R.drawable.ic_block, getString(R.string.parent_blocked_apps), getString(R.string.parent_blocked_apps_sub)) {
            startActivity(Intent(this, BlockedAppsActivity::class.java))
        }
        setupControlRow(b.controlSystemHealth.root, R.drawable.ic_permission, getString(R.string.system_health_title), getString(R.string.system_health_subtitle)) {
            startActivity(Intent(this, SystemHealthActivity::class.java))
        }
        setupControlRow(b.controlTimeSettings.root, R.drawable.ic_timer, getString(R.string.parent_time_settings), getString(R.string.parent_time_settings_sub)) {
            startActivity(Intent(this, TimeLimitsActivity::class.java))
        }
        setupControlRow(b.controlQuestionPacks.root, R.drawable.ic_quiz, getString(R.string.parent_question_packs), getString(R.string.parent_question_packs_sub)) {
            startActivity(Intent(this, ExamPackActivity::class.java))
        }
        setupControlRow(b.controlAvatarShop.root, R.drawable.ic_avatar, getString(R.string.parent_avatar_shop), null) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        setupControlRow(b.controlChangePin.root, R.drawable.ic_lock, getString(R.string.parent_change_pin), getString(R.string.parent_change_pin_sub)) {
            startActivity(Intent(this, PinLockActivity::class.java).apply {
                putExtra(PinLockActivity.EXTRA_MODE, "change")
                putExtra(PinLockActivity.EXTRA_TARGET, "ParentActivity")
            })
        }

        val killSwitch = KillSwitchPrefs(this)
        b.switchKillSwitch.setOnCheckedChangeListener(null)
        b.switchKillSwitch.isChecked = killSwitch.isKillSwitchActive()
        b.killSwitchExpireGroup.visibility = if (killSwitch.isKillSwitchActive()) View.VISIBLE else View.GONE
        when (killSwitch.getExpireOption()) {
            KillSwitchPrefs.ExpireOption.MINUTES_15 -> b.killSwitchExpireGroup.check(R.id.killExpire15m)
            KillSwitchPrefs.ExpireOption.HOUR_1 -> b.killSwitchExpireGroup.check(R.id.killExpire1h)
            KillSwitchPrefs.ExpireOption.HOURS_24 -> b.killSwitchExpireGroup.check(R.id.killExpire24h)
            else -> b.killSwitchExpireGroup.check(R.id.killExpireManual)
        }
        b.switchKillSwitch.setOnCheckedChangeListener { _, isChecked ->
            val opt = when (b.killSwitchExpireGroup.checkedRadioButtonId) {
                R.id.killExpire15m -> KillSwitchPrefs.ExpireOption.MINUTES_15
                R.id.killExpire1h -> KillSwitchPrefs.ExpireOption.HOUR_1
                R.id.killExpire24h -> KillSwitchPrefs.ExpireOption.HOURS_24
                else -> KillSwitchPrefs.ExpireOption.MANUAL
            }
            killSwitch.setKillSwitchActive(isChecked, if (isChecked) opt else KillSwitchPrefs.ExpireOption.MANUAL)
            b.killSwitchExpireGroup.visibility = if (isChecked) View.VISIBLE else View.GONE
            b.tvKillSwitchBanner.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        b.killSwitchExpireGroup.setOnCheckedChangeListener { _, _ ->
            if (b.switchKillSwitch.isChecked) {
                val opt = when (b.killSwitchExpireGroup.checkedRadioButtonId) {
                    R.id.killExpire15m -> KillSwitchPrefs.ExpireOption.MINUTES_15
                    R.id.killExpire1h -> KillSwitchPrefs.ExpireOption.HOUR_1
                    R.id.killExpire24h -> KillSwitchPrefs.ExpireOption.HOURS_24
                    else -> KillSwitchPrefs.ExpireOption.MANUAL
                }
                killSwitch.setKillSwitchActive(true, opt)
            }
        }
        b.tvKillSwitchBanner.visibility = if (killSwitch.isKillSwitchActive()) View.VISIBLE else View.GONE
    }

    private fun setupControlRow(
        root: View,
        iconRes: Int,
        title: String,
        subtitle: String?,
        onClick: (() -> Unit)? = null
    ) {
        root.findViewById<android.widget.ImageView>(R.id.icon)?.setImageDrawable(
            ContextCompat.getDrawable(this, iconRes)
        )
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
        val reportStore = ReportStore(this)
        val avatarStore = AvatarStore(this)

        val dateFormat = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("tr"))
        b.tvLastUpdate.text = getString(R.string.parent_last_update, dateFormat.format(Date()))

        val now = System.currentTimeMillis()
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val todayStart = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

        val sessions = analytics.getSessions().filter { it.tsMs >= weekAgo }
        val perfs = analytics.getTestPerformances().filter { it.tsMs >= weekAgo }

        val completedTestsLast7Days = perfs.size.coerceAtLeast(sessions.size)
        val totalQuestions = sessions.sumOf { it.total }.let { if (it > 0) it else perfs.sumOf { p -> p.correctCount + p.wrongCount + p.blankCount } }
        val totalCorrect = sessions.sumOf { it.correct }.let { if (it > 0) it else perfs.sumOf { it.correctCount } }
        val totalWrong = (totalQuestions - totalCorrect).coerceAtLeast(0).let { w ->
            if (w > 0) w else perfs.sumOf { it.wrongCount }
        }
        val totalBlank = perfs.sumOf { it.blankCount }
        val accuracyPercentLast7Days = if (totalQuestions > 0) (100.0 * totalCorrect / totalQuestions).toInt() else 0

        val blockedToday = reportStore.getBlockedAttemptsSince(todayStart)
        val totalBlockedToday = blockedToday.values.sum()
        val topBlocked = blockedToday.entries.maxByOrNull { it.value }
        val denemeText = when {
            topBlocked != null && topBlocked.value > 0 -> "${PackageNameHelper.getFriendlyName(topBlocked.key)} ${topBlocked.value}${if (totalBlockedToday > topBlocked.value) " • Toplam $totalBlockedToday" else ""}"
            totalBlockedToday > 0 -> "Toplam $totalBlockedToday"
            else -> "0"
        }

        b.quickTestValue.text = completedTestsLast7Days.toString()
        b.quickAccuracyValue.text = accuracyPercentLast7Days.toString()
        b.quickDenemeValue.text = denemeText

        val level = gam.level()
        val xp = gam.xp()
        b.tvLevelNum.text = "Seviye $level"
        b.tvXpNum.text = "$xp XP"

        val (xpInto, xpPer) = gam.xpProgress()
        val remainingXp = (xpPer - xpInto).coerceAtLeast(0)
        val pct = if (xpPer > 0) (100 * xpInto / xpPer) else 0
        b.progressToNextLevel.max = 100
        b.progressToNextLevel.progress = pct
        b.tvXpToNext.text = "Seviye ${level + 1}'e $remainingXp XP kaldı"

        val streak = gam.streakDays()
        b.tvStreakNum.text = "🔥 Seri: $streak gün"

        val accuracy = if (totalQuestions > 0) (100.0 * totalCorrect / totalQuestions) else 0.0
        b.donutChart.progress = accuracy.toFloat()
        b.tvWeeklyCorrect.text = "Doğru: $totalCorrect"
        b.tvWeeklyWrong.text = "Yanlış: $totalWrong"
        b.tvWeeklyBlank.text = "Boş: $totalBlank"

        val wrongStoreRender = WrongQuestionStore(this)
        val impByTopicRender = wrongStoreRender.getImprovementByTopic()
        val impSub = impByTopicRender.entries.filter { it.value.totalWrong > 0 }.take(3)
            .joinToString(" • ") { (t, i) -> "$t: ${i.totalWrong} yanlış→${i.fixedCount} düz." }
        if (impSub.isNotBlank()) {
            b.controlReviewWrong.root.findViewById<TextView>(R.id.subtitle)?.apply {
                text = impSub
                visibility = View.VISIBLE
            }
        }

        val topicCounts = analytics.getTopicMasteryWithCounts()
        val subjectOrder = listOf(Subject.MAT, Subject.TURKCE, Subject.FEN, Subject.SOSYAL, Subject.ING)
        val barData = subjectOrder.map { subj ->
            val tc = topicCounts[subj.tr] ?: TopicCounts(0, 0, 0, 0)
            BarChartView.BarData(subj.tr, tc.correct, tc.total)
        }
        b.barChart.data = barData

        val strongest = analytics.getStrongestTopicsWithCounts(3)
        b.containerStrong.removeAllViews()
        if (strongest.isEmpty()) {
            addTopicRow(b.containerStrong, "-", 0, 0)
        } else {
            strongest.forEach { (topic, tc) ->
                addTopicRow(b.containerStrong, topic, tc.correct, tc.total)
            }
        }

        val weakest = analytics.getWeakestTopicsWithCounts(3)
        b.containerWeak.removeAllViews()
        if (weakest.isEmpty()) {
            addTopicRow(b.containerWeak, "-", 0, 0)
        } else {
            weakest.forEach { (topic, tc) ->
                addTopicRow(b.containerWeak, topic, tc.correct, tc.total)
            }
        }

        val avatarSub = if (avatarStore.isShopDisabledByParent()) "Kapalı" else "Açık"
        b.controlAvatarShop.root.findViewById<TextView>(R.id.subtitle)?.apply {
            text = avatarSub
            visibility = View.VISIBLE
        }

        val healthStore = SystemHealthStore(this)
        val healthSub = if (healthStore.isAllOk()) getString(R.string.health_all_ok) else getString(R.string.system_health_subtitle)
        b.controlSystemHealth.root.findViewById<TextView>(R.id.subtitle)?.apply {
            text = healthSub
            visibility = View.VISIBLE
        }
    }

    private fun addTopicRow(container: ViewGroup, topic: String, correct: Int, total: Int) {
        val chip = Chip(this).apply {
            text = if (total > 0) "$topic $correct/$total" else topic
            isClickable = false
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 4 }
        container.addView(chip, params)
    }
}
