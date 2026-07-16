package com.mioacademy.app.dailychallenge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mioacademy.app.R
import com.mioacademy.app.core.ExamType
import com.mioacademy.app.core.StudyAreaManager
import com.mioacademy.app.ui.onTap
import kotlinx.coroutines.launch

/**
 * Daily Challenge completion screen. Shows score/5, per-section distribution, concise learning
 * feedback, streak, and the next-unlock countdown. Wrong answers are already in the review queue
 * (the engine records them on submit). There is intentionally NO control offering another new
 * challenge today.
 */
class DailyChallengeResultActivity : AppCompatActivity() {

    private val controller by lazy { DailyChallengeController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_challenge_result)
        val localDate = intent.getStringExtra(EXTRA_DATE) ?: controller.todayLocalDate()
        val userId = DailyChallengeUser.resolve(this)
        val exam: ExamType = StudyAreaManager.getActiveArea(this).career.examType

        findViewById<Button>(R.id.dcrHomeBtn).onTap { finish() }
        val reviewBtn = findViewById<Button>(R.id.dcrReviewBtn)
        reviewBtn.onTap { startActivity(DailyChallengeReviewActivity.intent(this)) }

        lifecycleScope.launch {
            val c = controller.completion(userId, exam, localDate)
            if (c == null) { finish(); return@launch }
            bind(c)
            // Offer review only when the queue actually has something to work through.
            val reviewCount = try { controller.reviewCount(userId, exam) } catch (_: Throwable) { 0 }
            reviewBtn.visibility = if (reviewCount > 0) View.VISIBLE else View.GONE
        }
    }

    private fun bind(c: DailyChallengeEngine.Completion) {
        findViewById<TextView>(R.id.dcrScoreBig).text = "${c.score}/${c.total}"
        val bar = findViewById<ProgressBar>(R.id.dcrScoreBar)
        bar.max = c.total; bar.progress = c.score

        findViewById<TextView>(R.id.dcrFeedback).text = getString(
            when {
                c.score >= 4 -> R.string.dc_result_feedback_great
                c.score >= 2 -> R.string.dc_result_feedback_ok
                else -> R.string.dc_result_feedback_low
            }
        )

        val container = findViewById<LinearLayout>(R.id.dcrSectionContainer)
        container.removeAllViews()
        for ((section, total) in c.sectionTotal) {
            val correct = c.sectionCorrect[section] ?: 0
            val row = TextView(this).apply {
                text = getString(
                    R.string.dc_result_section_row,
                    DailyChallengeSectionLabels.label(section), correct, total,
                )
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@DailyChallengeResultActivity, R.color.bb_text_dark))
                gravity = Gravity.START
                setPadding(0, 12, 0, 0)
            }
            container.addView(row)
        }

        findViewById<TextView>(R.id.dcrStreak).text = getString(R.string.dc_result_streak, c.streakCurrent)
        val countdown = DailyChallengeHomePresenter.countdownText(c.nextUnlockAtMs, System.currentTimeMillis())
        findViewById<TextView>(R.id.dcrNextUnlock).text =
            if (countdown.isEmpty()) getString(R.string.dc_result_next_unlock_ready)
            else getString(R.string.dc_result_next_unlock, countdown)
    }

    companion object {
        private const val EXTRA_DATE = "dc_local_date"
        fun intent(context: Context, localDate: String): Intent =
            Intent(context, DailyChallengeResultActivity::class.java).putExtra(EXTRA_DATE, localDate)
    }
}
