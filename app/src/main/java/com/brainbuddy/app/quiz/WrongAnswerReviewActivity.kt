package com.brainbuddy.app.quiz

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.ads.RewardedAdManager
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.WrongReviewAnalytics
import com.brainbuddy.app.core.WrongReviewQuotaStore
import com.brainbuddy.app.databinding.ActivityWrongAnswerReviewBinding
import com.brainbuddy.app.ui.TestSettingsActivity

/**
 * Wrong-answer review screen (post-test "Yanlışları İncele").
 *
 * Access rules:
 * - Premium / Parent mode → all wrong questions visible, no gates.
 * - Free → each question requires a rewarded ad; max [WrongReviewQuotaStore.FREE_PER_DAY] reviews
 *   per day. After daily limit, premium upsell is shown instead of ad option.
 *
 * Daily quota is tracked by [WrongReviewQuotaStore] and persists across app restarts.
 * Quota resets automatically when the calendar day changes.
 */
class WrongAnswerReviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WRONG_IDS = "wrong_ids"
        const val EXTRA_SESSION_JSON = "session_json"
        const val EXTRA_IS_PARENT_REVIEW = "is_parent_review"
        const val EXTRA_GATE_FAIL_REVIEW = "extra_gate_fail_review"
        private const val STATE_REVEALED = "state_revealed_indices"
    }

    private lateinit var b: ActivityWrongAnswerReviewBinding
    private lateinit var repo: QuestionRepository
    private lateinit var analyticsStore: AnalyticsStore
    private lateinit var quotaStore: WrongReviewQuotaStore
    private lateinit var premiumStore: PremiumStore
    private var questions: List<Question> = emptyList()
    private var index = 0
    private val retryAnswers = mutableMapOf<String, Int>()
    private var sessionAnswers: Map<String, Int> = emptyMap()
    private var inRetryMode = false
    private var isParentReview = false
    /** Indices of questions revealed via ad in this session. */
    private val revealedIndices = mutableSetOf<Int>()

    /** Premium or parent → full access, no ad gate. */
    private val hasFullAccess: Boolean
        get() = premiumStore.isPremium() || isParentReview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWrongAnswerReviewBinding.inflate(layoutInflater)
        setContentView(b.root)

        val requestedParent = intent.getBooleanExtra(EXTRA_IS_PARENT_REVIEW, false)
        if (requestedParent && !AppModeManager.isParentMode()) {
            com.brainbuddy.app.core.ParentAccessGuard.checkAndRedirect(
                this, WrongAnswerReviewActivity::class.java
            )
            return
        }
        isParentReview = requestedParent

        repo = QuestionRepository(this)
        analyticsStore = AnalyticsStore(this)
        quotaStore = WrongReviewQuotaStore(this)
        premiumStore = PremiumStore(this)
        quotaStore.ensureDailyReset()
        RewardedAdManager.preload(this)

        val wrongIds = intent.getStringArrayListExtra(EXTRA_WRONG_IDS) ?: arrayListOf()
        val sessionJson = intent.getStringExtra(EXTRA_SESSION_JSON)
        val session = QuizResultActivity.decodeSession(sessionJson)
        sessionAnswers = session?.answers ?: emptyMap()

        val orderedWrongIds = if (wrongIds.isNotEmpty()) {
            wrongIds
        } else if (intent.getBooleanExtra(EXTRA_GATE_FAIL_REVIEW, false)) {
            ProtectionPrefs(this).gateFailReviewWrongIdsOrdered()
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
        // Re-check premium status (user may have upgraded in settings)
        if (questions.isNotEmpty() && ::premiumStore.isInitialized) {
            quotaStore.ensureDailyReset()
            if (!inRetryMode) render()
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
        b.nextBtn.isEnabled = false
        b.summarySection.visibility = View.GONE
        b.gateSection.visibility = View.GONE
        b.tvQuotaBadge.visibility = View.GONE
        b.tvHintWatchAd.visibility = View.GONE
    }

    // ── Main render ────────────────────────────────────────────────

    private fun render() {
        val q = questions[index]

        // Reset all dynamic sections
        b.summarySection.visibility = View.GONE
        b.gateSection.visibility = View.GONE
        b.optionsGroup.visibility = View.GONE
        b.feedbackText.visibility = View.GONE
        b.tvHintWatchAd.visibility = View.GONE
        b.nextBtn.visibility = View.VISIBLE

        // Always-visible: header + question stem
        b.progressText.text = "${index + 1}/${questions.size}"
        b.subjectChip.text = "${q.subject.tr} \u2022 (\u0130nceleme)"
        b.questionText.text = q.stem

        // Question image
        if (!q.imageAsset.isNullOrBlank()) {
            try {
                assets.open(q.imageAsset!!.trim()).use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        b.questionImage.setImageBitmap(bmp)
                        b.questionImage.visibility = View.VISIBLE
                    } else {
                        b.questionImage.visibility = View.GONE
                    }
                }
            } catch (_: Exception) {
                b.questionImage.visibility = View.GONE
            }
        } else {
            b.questionImage.visibility = View.GONE
        }

        updateQuotaBadge()

        val isRevealed = hasFullAccess || index in revealedIndices

        when {
            inRetryMode -> renderRetry(q)
            isRevealed -> renderUnlocked(q)
            else -> renderLocked(q)
        }
    }

    private fun updateQuotaBadge() {
        b.tvQuotaBadge.visibility = View.VISIBLE
        if (hasFullAccess) {
            b.tvQuotaBadge.text = getString(R.string.wrong_review_unlimited)
        } else {
            b.tvQuotaBadge.text = getString(
                R.string.wrong_review_remaining, quotaStore.getRemaining()
            )
        }
    }

    // ── Locked state: question visible, correct answer behind ad gate ──

    private fun renderLocked(q: Question) {
        val displayChoices =
            QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices
        val userSel = sessionAnswers[q.id] ?: -1
        val userChoice =
            if (userSel in 0..3) displayChoices.getOrNull(userSel) ?: "?" else "-"

        b.gateSection.visibility = View.VISIBLE
        b.tvGateUserAnswer.visibility = View.VISIBLE
        b.tvGateUserAnswer.text = getString(R.string.wrong_review_gate_your_answer, userChoice)

        val remaining = quotaStore.getRemaining()
        if (remaining > 0) {
            // Quota available → offer rewarded ad
            b.tvGateIcon.text = "\uD83D\uDD12"
            b.tvGateMessage.text = getString(R.string.wrong_review_gate_ad_prompt)
            b.tvGateQuota.visibility = View.VISIBLE
            b.tvGateQuota.text = getString(
                R.string.wrong_review_gate_quota_info,
                remaining,
                WrongReviewQuotaStore.FREE_PER_DAY
            )
            b.btnGateWatchAd.visibility = View.VISIBLE
            b.btnGateWatchAd.text = getString(R.string.wrong_review_gate_btn_ad)
            b.btnGateWatchAd.setOnClickListener { watchAdToReveal() }
            b.btnGatePremium.visibility = View.VISIBLE
            b.btnGatePremium.text = getString(R.string.wrong_review_btn_premium)
            b.btnGatePremium.setOnClickListener { navigateToPremium() }
        } else {
            // Daily limit exhausted → premium upsell only
            WrongReviewAnalytics.logLimitHit()
            b.tvGateIcon.text = "\u23F3"
            b.tvGateMessage.text = getString(R.string.wrong_review_gate_limit_title)
            b.tvGateQuota.visibility = View.VISIBLE
            b.tvGateQuota.text = getString(R.string.wrong_review_gate_limit_body)
            b.btnGateWatchAd.visibility = View.GONE
            b.btnGatePremium.visibility = View.VISIBLE
            b.btnGatePremium.text = getString(R.string.wrong_review_gate_premium_cta)
            b.btnGatePremium.setOnClickListener { navigateToPremium() }
        }

        // Allow skipping locked questions
        b.nextBtn.text =
            if (index < questions.size - 1) getString(R.string.wrong_review_btn_skip)
            else getString(R.string.wrong_review_btn_finish)
        b.nextBtn.setOnClickListener { advanceToNext() }
    }

    // ── Unlocked state: full details + retry ─────────────────────

    private fun renderUnlocked(q: Question) {
        val displayChoices =
            QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices
        val userSel = sessionAnswers[q.id] ?: -1
        val userChoice =
            if (userSel in 0..3) displayChoices.getOrNull(userSel) ?: "?" else "-"
        val correctChoice = displayChoices.getOrNull(q.correctIndex) ?: "?"

        b.summarySection.visibility = View.VISIBLE
        b.tvUserChoiceSummary.text = "Senin cevab\u0131n: $userChoice"
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

    // ── Retry mode: re-answer the question ───────────────────────

    private fun renderRetry(q: Question) {
        val displayChoices =
            QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices
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
                b.optA.id -> 0; b.optB.id -> 1; b.optC.id -> 2; b.optD.id -> 3
                else -> -1
            }
            if (sel >= 0) retryAnswers[q.id] = sel
        }

        b.nextBtn.text = if (index < questions.size - 1) "Sonraki \u2192" else "Bitir"
        b.nextBtn.setOnClickListener { submitRetry() }
    }

    // ── Rewarded ad flow ─────────────────────────────────────────

    private fun watchAdToReveal() {
        if (!RewardedAdManager.isLoaded()) {
            Toast.makeText(
                this,
                getString(R.string.wrong_review_ad_not_ready),
                Toast.LENGTH_SHORT
            ).show()
            RewardedAdManager.preload(this)
            return
        }

        RewardedAdManager.show(
            activity = this,
            onReward = {
                WrongReviewAnalytics.logAdShown()
                WrongReviewAnalytics.logAdRewarded()
                if (quotaStore.consumeOne()) {
                    WrongReviewAnalytics.logItemReveal()
                    revealedIndices.add(index)
                    render()
                }
            },
            onFail = { msg ->
                val detail =
                    RewardedAdManager.lastLoadError?.let { "$msg ($it)" } ?: msg
                Toast.makeText(
                    this,
                    getString(R.string.wrong_review_ad_fail, detail),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun navigateToPremium() {
        WrongReviewAnalytics.logPremiumClick()
        startActivity(Intent(this, TestSettingsActivity::class.java))
    }

    // ── User actions ─────────────────────────────────────────────

    private fun enterRetryMode() {
        inRetryMode = true
        render()
    }

    private fun submitRetry() {
        val q = questions.getOrNull(index) ?: return
        val sel = when (b.optionsGroup.checkedRadioButtonId) {
            b.optA.id -> 0; b.optB.id -> 1; b.optC.id -> 2; b.optD.id -> 3
            else -> -1
        }
        if (sel >= 0) retryAnswers[q.id] = sel

        val correct = sel == q.correctIndex
        val fbChoices =
            QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices

        b.feedbackText.visibility = View.VISIBLE
        b.feedbackText.setTextColor(
            getColor(if (correct) R.color.bb_turquoise else R.color.bb_error)
        )
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
