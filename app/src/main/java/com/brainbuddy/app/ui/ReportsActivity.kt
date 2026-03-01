package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.auth.AuthProvider
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.EmailReportPrefs
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.ReportStore
import com.brainbuddy.app.report.ReportScheduler
import java.util.concurrent.TimeUnit

class ReportsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        setContentView(R.layout.activity_reports)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val reportStore = ReportStore(this)
        val analytics = AnalyticsStore(this)

        val now = System.currentTimeMillis()
        val dayAgo = now - TimeUnit.DAYS.toMillis(1)
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)

        val dailyLockEvents = reportStore.getLockEventsSince(dayAgo)
        val weeklyLockEvents = reportStore.getLockEventsSince(weekAgo)
        val dailyAttempts = reportStore.getBlockedAttemptsSince(dayAgo)
        val weeklyAttempts = reportStore.getBlockedAttemptsSince(weekAgo)

        val dailySessions = analytics.getSessions().count { it.tsMs >= dayAgo }
        val weeklySessions = analytics.getSessions().count { it.tsMs >= weekAgo }
        val perfs = analytics.getTestPerformances().filter { it.tsMs >= weekAgo }
        val passRate = if (perfs.isNotEmpty()) perfs.count { it.passed }.toFloat() / perfs.size * 100 else 0f

        findViewById<android.widget.TextView>(R.id.tvDailyLock).text = "$dailyLockEvents"
        findViewById<android.widget.TextView>(R.id.tvWeeklyLock).text = "$weeklyLockEvents"
        findViewById<android.widget.TextView>(R.id.tvDailyAttempts).text =
            dailyAttempts.entries.sortedByDescending { it.value }.take(5).joinToString("\n") { "${it.key}: ${it.value}" }.ifEmpty { "-" }
        findViewById<android.widget.TextView>(R.id.tvWeeklyAttempts).text =
            weeklyAttempts.entries.sortedByDescending { it.value }.take(5).joinToString("\n") { "${it.key}: ${it.value}" }.ifEmpty { "-" }
        findViewById<android.widget.TextView>(R.id.tvDailyQuizzes).text = "$dailySessions"
        findViewById<android.widget.TextView>(R.id.tvWeeklyQuizzes).text = "$weeklySessions"
        findViewById<android.widget.TextView>(R.id.tvPassRate).text = "%.0f%%".format(passRate)

        val protectionPrefs = ProtectionPrefs(this)
        val lastWrongIds = protectionPrefs.lastFailedWrongIds()
        val lastSessionJson = protectionPrefs.lastFailedSessionJson()
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardReviewWrong)?.let { card ->
            card.visibility = if (lastWrongIds.isNotEmpty() && lastSessionJson.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            findViewById<android.widget.Button>(R.id.btnReviewWrongParent)?.setOnClickListener {
                startActivity(Intent(this, com.brainbuddy.app.quiz.WrongAnswerReviewActivity::class.java).apply {
                    putStringArrayListExtra(com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_WRONG_IDS, ArrayList(lastWrongIds))
                    putExtra(com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_SESSION_JSON, lastSessionJson)
                    putExtra(com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_IS_PARENT_REVIEW, true)
                })
            }
        }

        val emailPrefs = EmailReportPrefs(this)
        val auth = AuthProvider.get(this)
        val switchDaily = findViewById<android.widget.Switch>(R.id.switchDailyReport)
        val switchWeekly = findViewById<android.widget.Switch>(R.id.switchWeeklyReport)
        val btnLogin = findViewById<android.widget.TextView>(R.id.btnLoginForReports)

        switchDaily.isChecked = emailPrefs.isDailyReportEnabled()
        switchWeekly.isChecked = emailPrefs.isWeeklyReportEnabled()

        fun updateReportToggles() {
            val signedIn = auth.isSignedIn()
            if (!signedIn) {
                switchDaily.isChecked = false
                switchWeekly.isChecked = false
                emailPrefs.setDailyReportEnabled(false)
                emailPrefs.setWeeklyReportEnabled(false)
            }
            switchDaily.isEnabled = signedIn
            switchWeekly.isEnabled = signedIn
            btnLogin.visibility = if (signedIn) android.view.View.GONE else android.view.View.VISIBLE
        }
        updateReportToggles()

        btnLogin.setOnClickListener {
            startActivityForResult(Intent(this, LoginActivity::class.java), REQ_LOGIN)
        }

        switchDaily.setOnCheckedChangeListener { _, isChecked ->
            if (auth.isSignedIn()) {
                emailPrefs.setDailyReportEnabled(isChecked)
                emailPrefs.setReportEmail(auth.currentEmail() ?: "")
                ReportScheduler.schedule(this)
            }
        }
        switchWeekly.setOnCheckedChangeListener { _, isChecked ->
            if (auth.isSignedIn()) {
                emailPrefs.setWeeklyReportEnabled(isChecked)
                emailPrefs.setReportEmail(auth.currentEmail() ?: "")
                ReportScheduler.schedule(this)
            }
        }

        findViewById<android.widget.Button>(R.id.btnShareReport).setOnClickListener {
            val text = buildString {
                append("BrainBuddy Rapor\n")
                append("Günlük kilit: $dailyLockEvents, Haftalık: $weeklyLockEvents\n")
                append("Testler (gün/hafta): $dailySessions / $weeklySessions\n")
                append("Geçme oranı: %.0f%%\n".format(passRate))
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, getString(R.string.report_share)))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_LOGIN && resultCode == RESULT_OK) recreate()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val REQ_LOGIN = 1001
    }
}
