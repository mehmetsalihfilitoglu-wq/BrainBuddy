package com.edumio.app.dailychallenge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.edumio.app.R
import com.edumio.app.core.ExamType
import com.edumio.app.core.StudyAreaManager
import com.edumio.app.ui.onTap
import kotlinx.coroutines.launch

/**
 * Daily Challenge completion screen. Shows score/5, per-section distribution, concise learning
 * feedback, streak, and the next-unlock countdown. Wrong answers are already in the review queue
 * (the engine records them on submit). There is intentionally NO control offering another new
 * challenge today.
 */
class DailyChallengeResultActivity : AppCompatActivity() {

    private val controller by lazy { DailyChallengeController(this) }

    // Contextual POST_NOTIFICATIONS request — shown after a completion, never on launch.
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        NotificationPermission.markAsked(this)
        if (granted) DailyChallengeReminderScheduler.schedule(this) // (re)arm reminders now that we may post
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_challenge_result)
        val localDate = intent.getStringExtra(EXTRA_DATE) ?: controller.todayLocalDate()
        val userId = DailyChallengeUser.resolve(this)
        val exam: ExamType = StudyAreaManager.getActiveArea(this).career.examType

        findViewById<Button>(R.id.dcrHomeBtn).onTap { finish() }
        val reviewBtn = findViewById<Button>(R.id.dcrReviewBtn)

        lifecycleScope.launch {
            val c = controller.completion(userId, localDate)
            if (c == null) { finish(); return@launch }
            bind(c)
            // Review is scoped to the exam THIS challenge belongs to — not the currently-active area,
            // which may differ if the user switched study areas after completing it.
            val challengeExam = runCatching { ExamType.valueOf(c.examProfile) }.getOrDefault(exam)
            val premium = DailyChallengeEntitlement.isPremiumForSolutions(this@DailyChallengeResultActivity)
            bindWrongAnswers(c, challengeExam, premium)
            reviewBtn.onTap {
                // Premium goes to the wrong-question hub (two-path experience); Free keeps capped review.
                startActivity(
                    if (premium) WrongQuestionsActivity.intent(this@DailyChallengeResultActivity)
                    else DailyChallengeReviewActivity.intent(this@DailyChallengeResultActivity, challengeExam)
                )
            }
            // Offer review only when the queue actually has something to work through.
            val reviewCount = try { controller.reviewCount(userId, challengeExam) } catch (_: Throwable) { 0 }
            reviewBtn.visibility = if (reviewCount > 0) View.VISIBLE else View.GONE
        }

        // The contextual moment: the user just finished — ask (once) so tomorrow's reminders can fire.
        if (NotificationPermission.shouldAsk(this)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun bind(c: DailyChallengeEngine.Completion) {
        findViewById<android.widget.ImageView>(R.id.dcrMascot).setImageResource(
            com.edumio.app.ui.EduMascot.drawable(
                com.edumio.app.ui.EduMascot.forCompletion(c.score, c.total, c.streakCurrent)
            )
        )
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
                setTextColor(ContextCompat.getColor(this@DailyChallengeResultActivity, R.color.edu_text_dark))
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

    /**
     * One row per wrong answer with the two learning paths. Premium: "Retry" (answer again — the only
     * way out of the active pool) and "View solution". Free: a locked CTA — no solution text is ever
     * bound, so nothing can leak through accessibility, logs, or view state.
     */
    private fun bindWrongAnswers(c: DailyChallengeEngine.Completion, challengeExam: ExamType, premium: Boolean) {
        val title = findViewById<TextView>(R.id.dcrWrongTitle)
        val box = findViewById<LinearLayout>(R.id.dcrWrongContainer)
        box.removeAllViews()
        val wrong = c.reviews.filter { !it.isCorrect }
        if (wrong.isEmpty()) { title.visibility = View.GONE; return }
        title.visibility = View.VISIBLE
        if (!premium) {
            DailyChallengeAnalyticsProvider.get(this).track(DcEvents.SOL_CTA_SHOWN)
        }
        for (r in wrong) {
            val stemPreview = TextView(this).apply {
                text = r.stem
                textSize = 13f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@DailyChallengeResultActivity, R.color.edu_text_dark))
                setPadding(0, 18, 0, 4)
            }
            box.addView(stemPreview)
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (premium) {
                actions.addView(rowAction(getString(R.string.sol_retry)) {
                    startActivity(DailyChallengeReviewActivity.retryIntent(this, challengeExam, r.questionId))
                })
                actions.addView(rowAction(getString(R.string.sol_view)) {
                    startActivity(
                        com.edumio.app.solutions.SolutionActivity.intent(this, challengeExam, r.questionId, r.chosenIndex)
                    )
                })
            } else {
                actions.addView(rowAction(getString(R.string.sol_locked_cta)) {
                    com.edumio.app.quiz.PremiumPaywallSheet().show(supportFragmentManager, com.edumio.app.quiz.PremiumPaywallSheet.TAG)
                })
            }
            box.addView(actions)
        }
    }

    private fun rowAction(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(ContextCompat.getColor(this@DailyChallengeResultActivity, R.color.brand_primary_dark))
        minHeight = 44
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 6, 48, 6)
        onTap { onClick() }
    }

    companion object {
        private const val EXTRA_DATE = "dc_local_date"
        fun intent(context: Context, localDate: String): Intent =
            Intent(context, DailyChallengeResultActivity::class.java).putExtra(EXTRA_DATE, localDate)
    }
}
