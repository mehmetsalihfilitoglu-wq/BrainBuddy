package com.edumio.app.solutions

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.edumio.app.R
import com.edumio.app.core.ExamType
import com.edumio.app.core.StudyAreaManager
import com.edumio.app.dailychallenge.DailyChallengeAnalyticsProvider
import com.edumio.app.dailychallenge.DailyChallengeDatabase
import com.edumio.app.dailychallenge.DailyChallengeEntitlement
import com.edumio.app.dailychallenge.DailyChallengeOptions
import com.edumio.app.dailychallenge.DailyChallengeReviewActivity
import com.edumio.app.dailychallenge.DailyChallengeUser
import com.edumio.app.dailychallenge.DcEvents
import com.edumio.app.db.DatabaseProvider
import com.edumio.app.quiz.PremiumPaywallSheet
import com.edumio.app.ui.onTap
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * The ONLY screen that renders a full verified solution body.
 *
 * Access is enforced HERE, at entry, through the entitlement seam ([SolutionAccessPolicy] — fail
 * closed): a Free/unknown entitlement gets the locked state and NO solution text is ever bound into
 * the view tree, so deep links, crafted intents, accessibility traversal, or window previews cannot
 * leak content. Viewing a solution performs NO learning-state writes — a wrong question stays in the
 * active pool until the student actually answers it correctly (see wrong_question_pool_spec.md).
 */
class SolutionActivity : AppCompatActivity() {

    private var lastAccess: SolutionAccessPolicy.Access? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_solution)
        bindForCurrentEntitlement()
    }

    override fun onResume() {
        super.onResume()
        // Re-check after possibly returning from the paywall: a fresh purchase unlocks in place.
        val nowPremium = DailyChallengeEntitlement.isPremiumForSolutions(this)
        val access = SolutionAccessPolicy.access(nowPremium)
        if (lastAccess != null && access != lastAccess) {
            if (access == SolutionAccessPolicy.Access.FULL_SOLUTION) {
                DailyChallengeAnalyticsProvider.get(this).track(
                    DcEvents.SOL_CTA_CONVERTED, mapOf(DcEvents.P_QUESTION_ID to questionId()),
                )
            }
            bindForCurrentEntitlement()
        }
    }

    private fun questionId(): String = intent.getStringExtra(EXTRA_QUESTION_ID) ?: ""

    private fun exam(): ExamType =
        intent.getStringExtra(EXTRA_EXAM)?.let { runCatching { ExamType.valueOf(it) }.getOrNull() }
            ?: StudyAreaManager.getActiveArea(this).career.examType

    private fun bindForCurrentEntitlement() {
        val premium = DailyChallengeEntitlement.isPremiumForSolutions(this)
        val access = SolutionAccessPolicy.access(premium)
        lastAccess = access
        val content = findViewById<LinearLayout>(R.id.solContent)
        val locked = findViewById<LinearLayout>(R.id.solLocked)
        if (!SolutionAccessPolicy.mayBindSolutionText(access)) {
            content.visibility = View.GONE
            locked.visibility = View.VISIBLE
            DailyChallengeAnalyticsProvider.get(this).track(
                DcEvents.SOL_CTA_SHOWN, mapOf(DcEvents.P_QUESTION_ID to questionId()),
            )
            findViewById<Button>(R.id.solLockedCta).onTap {
                PremiumPaywallSheet().show(supportFragmentManager, PremiumPaywallSheet.TAG)
            }
            return
        }
        locked.visibility = View.GONE
        content.visibility = View.VISIBLE
        loadFull()
    }

    private fun loadFull() {
        val qid = questionId()
        if (qid.isBlank()) { finish(); return }
        val exam = exam()
        val userId = DailyChallengeUser.resolve(this)
        lifecycleScope.launch {
            val q = try {
                DatabaseProvider.get(this@SolutionActivity).questionDao().getQuestionsByIds(listOf(qid)).firstOrNull()
            } catch (_: Throwable) { null }
            if (q == null) { finish(); return@launch }
            val solution = SolutionStore.solutionFor(this@SolutionActivity, exam, qid)
            val chosen = intent.getIntExtra(EXTRA_CHOSEN_INDEX, -1).takeIf { it >= 0 }
                ?: try {
                    DailyChallengeDatabase.get(this@SolutionActivity).dailyChallengeDao()
                        .getLatestAnswerForQuestion(userId, qid)?.chosenIndex
                } catch (_: Throwable) { null }

            // figure
            val figure = findViewById<ImageView>(R.id.solFigure)
            com.edumio.app.quiz.QuestionImageBinder.bind(figure, q.imageAsset)
            findViewById<TextView>(R.id.solStem).text = q.questionText

            // options in the SAME stable display order as the flow screens
            val choices = parseChoices(q.optionsJson)
            val lettered = com.edumio.app.quiz.OptionLabels.isLetterOptions(choices)
            val order = DailyChallengeOptions.displayOrder(q.id, choices.size, letterOptions = lettered)
            val optBox = findViewById<LinearLayout>(R.id.solOptions)
            optBox.removeAllViews()
            val green = ContextCompat.getColor(this@SolutionActivity, R.color.brand_primary_dark)
            val red = ContextCompat.getColor(this@SolutionActivity, R.color.color_error)
            val dark = ContextCompat.getColor(this@SolutionActivity, R.color.edu_text_dark)
            order.forEachIndexed { pos, orig ->
                val marker = when {
                    orig == q.answerIndex -> "  ✓"
                    chosen != null && orig == chosen && chosen != q.answerIndex -> "  ✗"
                    else -> ""
                }
                val d = resources.displayMetrics.density
                val optText = choices[orig]
                optBox.addView(TextView(this@SolutionActivity).apply {
                    text = (if (lettered) optText else getString(R.string.dc_option_fmt, ('A' + pos), optText)) + marker
                    textSize = 15f
                    setLineSpacing(0f, 1.25f)
                    setTextColor(when {
                        orig == q.answerIndex -> green
                        chosen != null && orig == chosen -> red
                        else -> dark
                    })
                    setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
                })
            }

            val summary = StringBuilder()
            if (chosen != null && chosen in choices.indices) {
                summary.append(getString(R.string.sol_your_answer)).append(": ").append(choices[chosen]).append('\n')
            }
            if (q.answerIndex in choices.indices) {
                summary.append(getString(R.string.sol_correct_answer)).append(": ").append(choices[q.answerIndex])
            }
            findViewById<TextView>(R.id.solAnswerSummary).text = summary.toString()

            bindSolutionBody(solution)

            findViewById<Button>(R.id.solRetryBtn).onTap {
                startActivity(DailyChallengeReviewActivity.retryIntent(this@SolutionActivity, exam, qid))
                finish()
            }
            DailyChallengeAnalyticsProvider.get(this@SolutionActivity).track(
                DcEvents.SOL_OPENED, mapOf(DcEvents.P_QUESTION_ID to qid, DcEvents.P_EXAM to exam.name),
            )
        }
    }

    private fun bindSolutionBody(s: Solution?) {
        val body = findViewById<LinearLayout>(R.id.solBody)
        body.removeAllViews()
        if (s == null) {
            body.addView(sectionBody(getString(R.string.sol_missing)))
            return
        }
        fun section(titleRes: Int, text: String) {
            body.addView(sectionTitle(getString(titleRes)))
            body.addView(sectionBody(text))
        }
        if (s.shortExplanation.isNotBlank()) section(R.string.sol_short_title, s.shortExplanation)
        if (s.solutionSteps.isNotEmpty()) {
            body.addView(sectionTitle(getString(R.string.sol_steps_title)))
            body.addView(sectionBody(s.solutionSteps.mapIndexed { i, st -> "${i + 1}. $st" }.joinToString("\n\n")))
        }
        s.figureExplanation?.let { section(R.string.sol_figure_title, it) }
        s.formulaNotes?.let { section(R.string.sol_formula_title, it) }
        if (s.keyConcept.isNotBlank()) section(R.string.sol_key_concept, s.keyConcept)
        if (s.commonMistake.isNotBlank()) section(R.string.sol_common_mistake, s.commonMistake)
        if (s.optionExplanations.isNotEmpty()) {
            body.addView(sectionTitle(getString(R.string.sol_options_title)))
            body.addView(sectionBody(s.optionExplanations.entries.joinToString("\n\n") { "• ${it.value}" }))
        }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ContextCompat.getColor(this@SolutionActivity, R.color.edu_text_muted))
        isAllCaps = true
        letterSpacing = 0.06f
        val d = resources.displayMetrics.density
        // Clear vertical space before each section heading so sections don't run together (raw px → dp).
        setPadding(0, (22 * d).toInt(), 0, (6 * d).toInt())
    }

    private fun sectionBody(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setLineSpacing(0f, 1.35f)
        setTextColor(ContextCompat.getColor(this@SolutionActivity, R.color.edu_text_dark))
        // Bottom breathing room so consecutive section bodies don't butt against the next heading.
        val d = resources.displayMetrics.density
        setPadding(0, 0, 0, (6 * d).toInt())
    }

    private fun parseChoices(optionsJson: String?): List<String> {
        if (optionsJson.isNullOrBlank()) return listOf("A", "B", "C", "D")
        return try {
            val arr = JSONArray(optionsJson)
            (0 until arr.length()).map { arr.optString(it) }
        } catch (_: Throwable) { listOf("A", "B", "C", "D") }
    }

    companion object {
        private const val EXTRA_QUESTION_ID = "sol_question_id"
        private const val EXTRA_EXAM = "sol_exam"
        private const val EXTRA_CHOSEN_INDEX = "sol_chosen_index"

        fun intent(context: Context, exam: ExamType, questionId: String, chosenIndex: Int? = null): Intent =
            Intent(context, SolutionActivity::class.java)
                .putExtra(EXTRA_QUESTION_ID, questionId)
                .putExtra(EXTRA_EXAM, exam.name)
                .apply { if (chosenIndex != null) putExtra(EXTRA_CHOSEN_INDEX, chosenIndex) }
    }
}
