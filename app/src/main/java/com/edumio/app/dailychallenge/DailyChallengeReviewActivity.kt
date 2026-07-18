package com.edumio.app.dailychallenge

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.edumio.app.R
import com.edumio.app.core.ExamType
import com.edumio.app.core.StudyAreaManager
import com.edumio.app.quiz.PremiumPaywallSheet
import com.edumio.app.ui.onTap
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * The review experience: re-surfaces the user's incorrect + due-revision questions one at a time
 * with the correct answer and explanation, and advances the spaced-repetition state on each attempt.
 *
 * Premium unlocks unlimited review depth; Free is capped by [ReviewEngine] (with a Premium upsell
 * when the cap is hit). Review draws ONLY from already-seen review states, so it can never introduce
 * a new Daily-Challenge question — the 5-new-per-day limit is untouched.
 */
class DailyChallengeReviewActivity : AppCompatActivity() {

    private val controller by lazy { DailyChallengeController(this) }
    private lateinit var userId: String
    private lateinit var exam: ExamType
    private var isPremium = false

    private var items: List<ReviewEngine.ReviewItem> = emptyList()
    private var totalEligible = 0
    private var pos = 0
    private var revealed = false
    private var currentOrder: List<Int> = emptyList()

    private lateinit var queueLabel: TextView
    private lateinit var progress: TextView
    private lateinit var image: ImageView
    private lateinit var stem: TextView
    private lateinit var group: RadioGroup
    private lateinit var options: List<RadioButton>
    private lateinit var explanationCard: View
    private lateinit var verdict: TextView
    private lateinit var explanation: TextView
    private lateinit var premiumHint: TextView
    private lateinit var primary: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_challenge_review)
        queueLabel = findViewById(R.id.dcrvQueueLabel)
        progress = findViewById(R.id.dcrvProgress)
        image = findViewById(R.id.dcrvImage)
        stem = findViewById(R.id.dcrvQuestionText)
        group = findViewById(R.id.dcrvOptionsGroup)
        options = listOf(
            findViewById(R.id.dcrvOptA), findViewById(R.id.dcrvOptB), findViewById(R.id.dcrvOptC),
            findViewById(R.id.dcrvOptD), findViewById(R.id.dcrvOptE),
        )
        explanationCard = findViewById(R.id.dcrvExplanationCard)
        verdict = findViewById(R.id.dcrvVerdict)
        explanation = findViewById(R.id.dcrvExplanation)
        premiumHint = findViewById(R.id.dcrvPremiumHint)
        primary = findViewById(R.id.dcrvPrimaryBtn)

        userId = DailyChallengeUser.resolve(this)
        // Scope the review queue to the exam passed by the caller (the challenge being reviewed), so it is
        // not empty after the user switches study areas. Falls back to the active area for direct entry.
        exam = intent.getStringExtra(EXTRA_EXAM)?.let { runCatching { ExamType.valueOf(it) }.getOrNull() }
            ?: StudyAreaManager.getActiveArea(this).career.examType
        // Review depth is gated by the entitlement seam (fails safe to Free). Never affects new-Q count.
        isPremium = DailyChallengeEntitlement.isPremiumForReview(this)

        group.setOnCheckedChangeListener { _, _ ->
            if (!revealed) primary.isEnabled = group.checkedRadioButtonId != -1
        }
        premiumHint.onTap { PremiumPaywallSheet().show(supportFragmentManager, PremiumPaywallSheet.TAG) }
        primary.onTap { onPrimary() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            items = try { controller.reviewQueue(userId, exam, isPremium) } catch (_: Throwable) { emptyList() }
            totalEligible = try { controller.reviewCount(userId, exam) } catch (_: Throwable) { items.size }
            if (items.isEmpty()) {
                // Empty review — show the sleeping mascot empty state instead of a bare toast.
                setContentView(R.layout.view_mascot_empty)
                findViewById<Button>(R.id.emptyClose).onTap { finish() }
                return@launch
            }
            pos = 0
            render()
        }
    }

    private fun render() {
        revealed = false
        val item = items[pos]
        val q = item.question
        queueLabel.setText(R.string.dc_review_title)
        progress.text = DailyChallengeReviewPresenter.progressText(pos, items.size)

        if (!q.imageAsset.isNullOrBlank()) {
            try {
                assets.open(q.imageAsset!!.trim()).use { image.setImageBitmap(BitmapFactory.decodeStream(it)) }
                image.visibility = View.VISIBLE
            } catch (_: Throwable) { image.visibility = View.GONE }
        } else image.visibility = View.GONE

        stem.text = q.questionText
        val choices = parseChoices(q.optionsJson)
        currentOrder = DailyChallengeOptions.displayOrder(q.id, choices.size)
        group.setOnCheckedChangeListener(null)
        group.clearCheck()
        val defaultColor = androidx.core.content.ContextCompat.getColor(this, R.color.edu_text_dark)
        options.forEachIndexed { p, btn ->
            if (p < currentOrder.size) {
                btn.text = getString(R.string.dc_option_fmt, ('A' + p), choices[currentOrder[p]])
                btn.setTextColor(defaultColor) // clear any correct/incorrect highlight from the previous item
                btn.visibility = View.VISIBLE
                btn.isEnabled = true
            } else btn.visibility = View.GONE
        }
        group.setOnCheckedChangeListener { _, _ -> if (!revealed) primary.isEnabled = group.checkedRadioButtonId != -1 }

        explanationCard.visibility = View.GONE
        premiumHint.visibility = View.GONE
        primary.isEnabled = false
        primary.setText(R.string.dc_review_check)
    }

    private fun onPrimary() {
        if (!revealed) reveal() else advance()
    }

    private fun reveal() {
        val checkedPos = options.indexOfFirst { it.visibility == View.VISIBLE && it.isChecked }
        if (checkedPos < 0 || checkedPos >= currentOrder.size) return
        val item = items[pos]
        val q = item.question
        val chosenOriginal = currentOrder[checkedPos]
        val isCorrect = chosenOriginal == q.answerIndex
        revealed = true
        options.forEach { it.isEnabled = false }

        // Mark WHICH option was correct (and the wrong pick, if any) — a review must teach the answer.
        // Uses an icon marker in addition to colour so it is not conveyed by colour alone.
        val correctColor = androidx.core.content.ContextCompat.getColor(this, R.color.brand_primary_dark)
        val wrongColor = androidx.core.content.ContextCompat.getColor(this, R.color.color_error)
        for (p in currentOrder.indices) {
            val btn = options[p]
            when {
                currentOrder[p] == q.answerIndex -> { btn.append("  ✓"); btn.setTextColor(correctColor) }
                p == checkedPos -> { btn.append("  ✗"); btn.setTextColor(wrongColor) }
            }
        }

        verdict.setText(if (isCorrect) R.string.dc_review_correct else R.string.dc_review_incorrect)
        explanation.text = q.explanation?.takeIf { it.isNotBlank() } ?: getString(R.string.dc_review_no_explanation)
        explanationCard.visibility = View.VISIBLE

        if (DailyChallengeReviewPresenter.isCapped(totalEligible, items.size, isPremium)) {
            premiumHint.visibility = View.VISIBLE
            premiumHint.text = getString(
                R.string.dc_review_premium_hint,
                DailyChallengeReviewPresenter.lockedCount(totalEligible, items.size, isPremium),
            )
        }

        primary.isEnabled = true
        primary.setText(
            if (DailyChallengeReviewPresenter.step(true, pos, items.size) == DailyChallengeReviewPresenter.Step.FINISH)
                R.string.dc_review_finish else R.string.dc_review_next
        )
        lifecycleScope.launch {
            try { controller.submitReview(userId, q.id, isCorrect) } catch (_: Throwable) { /* local write; ignore */ }
        }
    }

    private fun advance() {
        if (pos >= items.size - 1) {
            Toast.makeText(this, R.string.dc_review_done, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            pos += 1
            render()
        }
    }

    private fun parseChoices(optionsJson: String?): List<String> {
        if (optionsJson.isNullOrBlank()) return listOf("A", "B", "C", "D")
        return try {
            val arr = JSONArray(optionsJson)
            (0 until arr.length()).map { arr.optString(it) }
        } catch (_: Throwable) { listOf("A", "B", "C", "D") }
    }

    companion object {
        private const val EXTRA_EXAM = "dc_review_exam"

        /** [exam] scopes the review queue to a specific exam (the challenge being reviewed). */
        fun intent(context: Context, exam: ExamType? = null): Intent =
            Intent(context, DailyChallengeReviewActivity::class.java).apply {
                if (exam != null) putExtra(EXTRA_EXAM, exam.name)
            }
    }
}
