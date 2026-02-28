package com.brainbuddy.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.databinding.ActivityParentBinding
import com.brainbuddy.app.security.ProtectedNav
import java.util.concurrent.TimeUnit

class ParentActivity : ComponentActivity() {

    private lateinit var b: ActivityParentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.openSettings.setOnClickListener {
            ProtectedNav.open(this, SettingsActivity::class.java, "Ebeveyn PIN’i gir")
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