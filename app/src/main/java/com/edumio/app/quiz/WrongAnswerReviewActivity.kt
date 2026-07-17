package com.edumio.app.quiz

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.edumio.app.R
import com.edumio.app.ads.RewardedAdManager
import com.edumio.app.core.AnalyticsStore
import com.edumio.app.core.WrongReviewAccessManager
import com.edumio.app.core.WrongReviewAnalytics
import com.edumio.app.databinding.ActivityWrongAnswerReviewBinding

/**
 * Wrong-answer review screen with production-grade gating.
 *
 * Architecture:
 * - [WrongReviewAccessManager] is the single source of truth for quota.
 * - NO UI code directly mutates quota — only [WrongReviewAccessManager.consumeUnlock].
 * - Ad flow uses explicit state machine: IDLE → LOADING → SHOWING → COMPLETED/FAILED.
 * - Locked state hides question text + options. Only user's wrong answer visible.
 * - Premium/parent → all content visible, no gates.
 * - Free → rewarded ad per question, max 3/day, then [PremiumPaywallSheet].
 */
class WrongAnswerReviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WRONG_IDS = "wrong_ids"
        const val EXTRA_SESSION_JSON = "session_json"
        const val EXTRA_IS_PARENT_REVIEW = "is_parent_review"
        const val EXTRA_GATE_FAIL_REVIEW = "extra_gate_fail_review"
        private const val STATE_REVEALED = "state_revealed_indices"
    }

    // ── Ad flow state machine ────────────────────────────────────
    private enum class AdState { IDLE, LOADING, SHOWING }

    private lateinit var b: ActivityWrongAnswerReviewBinding
    private lateinit var repo: QuestionRepository
    private lateinit var analyticsStore: AnalyticsStore
    private lateinit var accessManager: WrongReviewAccessManager

    private var questions: List<Question> = emptyList()
    private var index = 0
    private val retryAnswers = mutableMapOf<String, Int>()
    private var sessionAnswers: Map<String, Int> = emptyMap()
    private var inRetryMode = false
    private var isParentReview = false
    private val revealedIndices = mutableSetOf<Int>()
    private var adState = AdState.IDLE

    private val hasFullAccess: Boolean
        get() = accessManager.isPremium() || isParentReview

    // ── Lifecycle ────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWrongAnswerReviewBinding.inflate(layoutInflater)
        setContentView(b.root)

        isParentReview = intent.getBooleanExtra(EXTRA_IS_PARENT_REVIEW, false)

        repo = QuestionRepository(this)
        analyticsStore = AnalyticsStore(this)
        accessManager = WrongReviewAccessManager(this)
        accessManager.handleDailyReset()
        RewardedAdManager.preload(this)

        val wrongIds = intent.getStringArrayListExtra(EXTRA_WRONG_IDS) ?: arrayListOf()
        val sessionJson = intent.getStringExtra(EXTRA_SESSION_JSON)
        val session = QuizResultActivity.decodeSession(sessionJson)
        sessionAnswers = session?.answers ?: emptyMap()

        val orderedWrongIds = if (wrongIds.isNotEmpty()) {
            wrongIds
        } else {
            emptyList()
        }

        val allMap = repo.loadAllQuestions().associateBy { it.id }
        questions = orderedWrongIds.mapNotNull { allMap[it] }

        savedInstanceState?.getIntegerArrayList(STATE_REVEALED)?.let {
            revealedIndices.addAll(it)
        }

        b.btnBack.setOnClickListener { finish() }

        if (questions.isEmpty()) {
            showEmpty()
        } else {
            WrongReviewAnalytics.logOpen()
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        if (questions.isNotEmpty() && ::accessManager.isInitialized) {
            accessManager.handleDailyReset()
            if (!inRetryMode && adState == AdState.IDLE) render()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (revealedIndices.isNotEmpty()) {
            outState.putIntegerArrayList(STATE_REVEALED, ArrayList(revealedIndices))
        }
    }

    private fun showEmpty() {
        b.questionText.text = getString(R.string.wrong_review_empty)
        b.questionText.visibility = View.VISIBLE
        b.nextBtn.isEnabled = false
        b.summarySection.visibility = View.GONE
        b.gateSection.visibility = View.GONE
        b.tvQuotaBadge.visibility = View.GONE
        b.tvQuotaDots.visibility = View.GONE
    }

    // ── Main render (no side effects) ────────────────────────────

    private fun render() {
        val q = questions[index]

        // Reset all sections
        b.summarySection.visibility = View.GONE
        b.gateSection.visibility = View.GONE
        b.optionsGroup.visibility = View.GONE
        b.feedbackText.visibility = View.GONE
        b.nextBtn.visibility = View.VISIBLE
        b.questionImage.visibility = View.GONE

        // Header
        b.progressText.text = "${index + 1}/${questions.size}"
        b.subjectChip.text = "${q.subject.tr} \u2022 (\u0130nceleme)"

        updateQuotaDisplay()

        val isRevealed = hasFullAccess || index in revealedIndices

        when {
            inRetryMode -> renderRetry(q)
            isRevealed -> renderUnlocked(q)
            else -> renderLocked(q)
        }
    }

    private fun updateQuotaDisplay() {
        if (hasFullAccess) {
            b.tvQuotaDots.visibility = View.GONE
            b.tvQuotaBadge.visibility = View.VISIBLE
            b.tvQuotaBadge.text = getString(R.string.wrong_review_unlimited)
        } else {
            val used = accessManager.getUsedToday()
            val max = WrongReviewAccessManager.FREE_PER_DAY
            b.tvQuotaDots.visibility = View.VISIBLE
            b.tvQuotaDots.text = buildQuotaDots(used, max)
            b.tvQuotaBadge.visibility = View.VISIBLE
            b.tvQuotaBadge.text = getString(R.string.wrong_review_remaining, max - used)
        }
    }

    private fun buildQuotaDots(used: Int, max: Int): CharSequence {
        val sb = StringBuilder()
        for (i in 0 until max) {
            if (i > 0) sb.append(" ")
            sb.append(if (i < used) "\u25CF" else "\u25CB") // ● vs ○
        }
        return sb
    }

    // ── LOCKED: question hidden, only user's wrong answer shown ──

    private fun renderLocked(q: Question) {
        // Hide question content
        b.questionText.text = getString(R.string.wrong_review_locked_question)
        b.questionText.visibility = View.VISIBLE

        val displayChoices = QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices
        val userSel = sessionAnswers[q.id] ?: -1
        val userChoice = if (userSel in 0..3) displayChoices.getOrNull(userSel) ?: "?" else "-"

        b.gateSection.visibility = View.VISIBLE
        b.tvGateUserAnswer.visibility = View.VISIBLE
        b.tvGateUserAnswer.text = getString(R.string.wrong_review_gate_your_answer, userChoice)

        if (accessManager.canUnlock()) {
            b.tvGateIcon.text = "\uD83D\uDD12"
            b.tvGateMessage.text = getString(R.string.wrong_review_gate_ad_prompt)
            b.btnGateWatchAd.visibility = View.VISIBLE
            b.btnGateWatchAd.text = getString(R.string.wrong_review_gate_btn_ad)
            b.btnGateWatchAd.isEnabled = adState == AdState.IDLE
            b.btnGateWatchAd.setOnClickListener { onWatchAdClicked() }
            b.btnGatePremium.visibility = View.VISIBLE
            b.btnGatePremium.text = getString(R.string.wrong_review_btn_premium)
            b.btnGatePremium.setOnClickListener { showPaywall() }
        } else {
            WrongReviewAnalytics.logLimitHit()
            b.tvGateIcon.text = "\u23F3"
            b.tvGateMessage.text = getString(R.string.wrong_review_gate_limit_title) +
                "\n" + getString(R.string.wrong_review_gate_limit_body)
            b.btnGateWatchAd.visibility = View.GONE
            b.btnGatePremium.visibility = View.VISIBLE
            b.btnGatePremium.text = getString(R.string.wrong_review_gate_premium_cta)
            b.btnGatePremium.setOnClickListener { showPaywall() }
        }

        b.nextBtn.text = if (index < questions.size - 1) getString(R.string.wrong_review_btn_skip) else getString(R.string.wrong_review_btn_finish)
        b.nextBtn.setOnClickListener { advanceToNext() }
    }

    // ── UNLOCKED: full content visible ───────────────────────────

    private fun renderUnlocked(q: Question) {
        // Show full question
        b.questionText.text = q.stem
        b.questionText.visibility = View.VISIBLE

        // Show image if available
        if (!q.imageAsset.isNullOrBlank()) {
            try {
                assets.open(q.imageAsset!!.trim()).use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        b.questionImage.setImageBitmap(bmp)
                        b.questionImage.visibility = View.VISIBLE
                    }
                }
            } catch (_: Exception) { /* skip */ }
        }

        val displayChoices = QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices
        val userSel = sessionAnswers[q.id] ?: -1
        val userChoice = if (userSel in 0..3) displayChoices.getOrNull(userSel) ?: "?" else "-"
        val correctChoice = displayChoices.getOrNull(q.correctIndex) ?: "?"

        b.summarySection.visibility = View.VISIBLE
        b.tvUserChoiceSummary.text = getString(R.string.wrong_review_gate_your_answer, userChoice)
        b.tvCorrectSummary.visibility = View.VISIBLE
        b.tvCorrectSummary.text = "\u2713 Do\u011fru: $correctChoice"
        b.tvHintSummary.apply {
            if (!q.hint.isNullOrBlank()) {
                visibility = View.VISIBLE
                text = "\uD83D\uDCA1 ${q.hint}"
            } else {
                visibility = View.GONE
            }
        }
        b.btnRetryQuestion.visibility = View.VISIBLE
        b.btnRetryQuestion.setOnClickListener { enterRetryMode() }

        b.nextBtn.text = if (index < questions.size - 1) "Sonraki" else "Bitir"
        b.nextBtn.setOnClickListener { advanceToNext() }
    }

    // ── RETRY mode ───────────────────────────────────────────────

    private fun renderRetry(q: Question) {
        b.questionText.text = q.stem
        b.questionText.visibility = View.VISIBLE

        val displayChoices = QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices
        b.optA.text = displayChoices.getOrNull(0) ?: "-"
        b.optB.text = displayChoices.getOrNull(1) ?: "-"
        b.optC.text = displayChoices.getOrNull(2) ?: "-"
        b.optD.text = displayChoices.getOrNull(3) ?: "-"

        b.optionsGroup.visibility = View.VISIBLE
        val saved = retryAnswers[q.id] ?: -1
        b.optionsGroup.setOnCheckedChangeListener(null)
        when (saved) {
            0 -> b.optA.isChecked = true
            1 -> b.optB.isChecked = true
            2 -> b.optC.isChecked = true
            3 -> b.optD.isChecked = true
            else -> b.optionsGroup.clearCheck()
        }
        b.optionsGroup.setOnCheckedChangeListener { _, checkedId ->
            val sel = when (checkedId) {
                b.optA.id -> 0; b.optB.id -> 1; b.optC.id -> 2; b.optD.id -> 3; else -> -1
            }
            if (sel >= 0) retryAnswers[q.id] = sel
        }

        b.nextBtn.text = if (index < questions.size - 1) "Sonraki \u2192" else "Bitir"
        b.nextBtn.setOnClickListener { submitRetry() }
    }

    // ── Rewarded ad state machine ────────────────────────────────

    private fun onWatchAdClicked() {
        if (adState != AdState.IDLE) return // debounce

        if (!RewardedAdManager.isLoaded()) {
            adState = AdState.LOADING
            b.btnGateWatchAd.isEnabled = false
            b.btnGateWatchAd.text = getString(R.string.wrong_review_ad_loading_short)
            RewardedAdManager.preload(this)
            // Poll for load with a delayed check
            b.btnGateWatchAd.postDelayed({
                if (adState == AdState.LOADING) {
                    adState = AdState.IDLE
                    b.btnGateWatchAd.isEnabled = true
                    b.btnGateWatchAd.text = getString(R.string.wrong_review_gate_btn_ad)
                    if (!RewardedAdManager.isLoaded()) {
                        Toast.makeText(this, getString(R.string.wrong_review_ad_not_ready), Toast.LENGTH_SHORT).show()
                    } else {
                        onWatchAdClicked() // retry now that it's loaded
                    }
                }
            }, 5000)
            return
        }

        adState = AdState.SHOWING
        b.btnGateWatchAd.isEnabled = false

        RewardedAdManager.show(
            activity = this,
            onReward = {
                WrongReviewAnalytics.logAdShown()
                WrongReviewAnalytics.logAdRewarded()
                if (accessManager.consumeUnlock()) {
                    WrongReviewAnalytics.logItemReveal()
                    revealedIndices.add(index)
                }
                adState = AdState.IDLE
                render()
            },
            onFail = { msg ->
                adState = AdState.IDLE
                b.btnGateWatchAd.isEnabled = true
                val detail = RewardedAdManager.lastLoadError?.let { "$msg ($it)" } ?: msg
                Toast.makeText(this, getString(R.string.wrong_review_ad_fail, detail), Toast.LENGTH_LONG).show()
            }
        )
    }

    // ── Premium paywall ──────────────────────────────────────────

    private fun showPaywall() {
        PremiumPaywallSheet().show(supportFragmentManager, PremiumPaywallSheet.TAG)
    }

    // ── Actions ──────────────────────────────────────────────────

    private fun enterRetryMode() {
        inRetryMode = true
        render()
    }

    private fun submitRetry() {
        val q = questions.getOrNull(index) ?: return
        val sel = when (b.optionsGroup.checkedRadioButtonId) {
            b.optA.id -> 0; b.optB.id -> 1; b.optC.id -> 2; b.optD.id -> 3; else -> -1
        }
        if (sel >= 0) retryAnswers[q.id] = sel

        val correct = sel == q.correctIndex
        val fbChoices = QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices

        b.feedbackText.visibility = View.VISIBLE
        b.feedbackText.setTextColor(getColor(if (correct) R.color.edu_turquoise else R.color.edu_error))
        b.feedbackText.text = if (correct) {
            "\u2713 Do\u011fru!"
        } else {
            "\u2717 Yanl\u0131\u015f. Do\u011fru: ${fbChoices.getOrNull(q.correctIndex) ?: "?"}"
        }

        if (correct) {
            repo.recordAnswers(
                listOf(AnswerRecord(q.id, sel, q.correctIndex)),
                questions.associateBy { it.id }
            )
            WrongQuestionScheduler(applicationContext).markCorrect(q.id)
            analyticsStore.recordReviewCorrection()
        }

        b.nextBtn.text = if (index < questions.size - 1) "Sonraki \u2192" else "Bitir"
        b.nextBtn.setOnClickListener { advanceToNext() }
    }

    private fun advanceToNext() {
        if (index < questions.size - 1) {
            index++
            inRetryMode = false
            render()
        } else {
            finish()
        }
    }
}
