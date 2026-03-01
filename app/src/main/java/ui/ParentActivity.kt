package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.databinding.ActivityParentBinding
import com.brainbuddy.app.quiz.WrongAnswerReviewActivity
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
        val sessionJson = protectionPrefs.lastFailedSessionJson()
        b.btnReviewWrong.visibility = if (wrongIds.isNotEmpty()) View.VISIBLE else View.GONE
        b.btnReviewWrong.setOnClickListener {
            if (wrongIds.isNotEmpty()) {
                startActivity(Intent(this, WrongAnswerReviewActivity::class.java).apply {
                    putStringArrayListExtra(WrongAnswerReviewActivity.EXTRA_WRONG_IDS, ArrayList(wrongIds))
                    if (sessionJson.isNotEmpty()) putExtra(WrongAnswerReviewActivity.EXTRA_SESSION_JSON, sessionJson)
                    putExtra(WrongAnswerReviewActivity.EXTRA_IS_PARENT_REVIEW, true)
                })
            }
        }
        b.openSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.changePin.setOnClickListener {
            startActivity(Intent(this, PinLockActivity::class.java).apply {
                putExtra(PinLockActivity.EXTRA_MODE, "change")
                putExtra(PinLockActivity.EXTRA_TARGET, "ParentActivity")
            })
        }
    }

    override fun onResume() {
        super.onResume()
        renderSummariesTR()
    }

    private fun renderSummariesTR() {
        val gam = GamificationStore(this)
        val analytics = AnalyticsStore(this)

        val badges = gam.badges().sorted()
        b.gamificationSummary.text = buildString {
            append("Puan: ${gam.points()}\n")
            append("Seviye: ${gam.level()}\n")
            append("Seri: ${gam.streakDays()} gün\n")
            append("Rozetler: ${if (badges.isEmpty()) "Henüz yok" else badges.joinToString(", ")}\n")
        }

        val now = System.currentTimeMillis()
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val sessions = analytics.getSessions().filter { it.tsMs >= weekAgo }

        val totalQuizzes = sessions.size
        val totalQuestions = sessions.sumOf { it.total }
        val totalCorrect = sessions.sumOf { it.correct }
        val totalPoints = sessions.sumOf { it.pointsEarned }
        val accuracy = if (totalQuestions > 0) (100.0 * totalCorrect / totalQuestions) else 0.0

        b.weeklySummary.text = buildString {
            append("Son 7 gün\n")
            append("Tamamlanan test: $totalQuizzes\n")
            append("Toplam soru: $totalQuestions\n")
            append("Doğru: $totalCorrect\n")
            append("Başarı: ${"%.1f".format(accuracy)}%\n")
            append("Kazanılan puan: $totalPoints\n")
        }
    }
}
