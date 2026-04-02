package com.brainbuddy.app.quiz

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.brainbuddy.app.ui.SettingsActivity
import com.brainbuddy.app.ui.TestSettingsActivity

/**
 * Review wrong answers only. Shows question, user's wrong choice, correct answer, and optional hint.
 * "Retry question" lets user answer again in review mode. Does NOT change original test score.
 * Tracks reviewCorrectedCount separately via AnalyticsStore.
 *
 * Quota (parent mode): Premium unlimited; free: 3/day + 1 per rewarded ad.
 * Each question reveal (summary with correct answer) consumes 1 view.
 */
class WrongAnswerReviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WRONG_IDS = "wrong_ids"
        const val EXTRA_SESSION_JSON = "session_json"
        /** When true (Parent mode), show correct answer and explanation. When false (Student), show only "Wrong". */
        const val EXTRA_IS_PARENT_REVIEW = "is_parent_review"
        /** Failed gate quiz: first 3 wrongs unlock with rewarded ads; rest premium-only. Does not affect quiz pass state. */
        const val EXTRA_GATE_FAIL_REVIEW = "extra_gate_fail_review"
        private const val STATE_REVEALED_GATE = "state_revealed_gate_indices"
    }

    private lateinit var b: ActivityWrongAnswerReviewBinding
    private lateinit var repo: QuestionRepository
    private lateinit var analyticsStore: AnalyticsStore
    private lateinit var quotaStore: WrongReviewQuotaStore
    private lateinit var premiumStore: PremiumStore
    private var questions: List<Question> = emptyList()
    private var index = 0
    private val answers = mutableMapOf<String, Int>()
    private var sessionAnswers: Map<String, Int> = emptyMap()
    private var inRetryMode = false
    private var isParentReview = false
    private var gateFailReview = false
    private val revealedGateIndices = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWrongAnswerReviewBinding.inflate(layoutInflater)
        setContentView(b.root)

        val requestedParent = intent.getBooleanExtra(EXTRA_IS_PARENT_REVIEW, false)
        if (requestedParent && !AppModeManager.isParentMode()) {
            com.brainbuddy.app.core.ParentAccessGuard.checkAndRedirect(this, WrongAnswerReviewActivity::class.java)
            return
        }
        isParentReview = requestedParent
        gateFailReview = intent.getBooleanExtra(EXTRA_GATE_FAIL_REVIEW, false)

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
        } else if (gateFailReview) {
            ProtectionPrefs(this).gateFailReviewWrongIdsOrdered()
        } else {
            emptyList()
        }

        val allMap = repo.loadAllQuestions().associateBy { it.id }
        questions = orderedWrongIds.mapNotNull { allMap[it] }

        b.btnBack.setOnClickListener { finish() }
        b.nextBtn.setOnClickListener { goNext() }
        b.btnRetryQuestion.setOnClickListener { enterRetryMode() }
        b.optionsGroup.setOnCheckedChangeListener { _, checkedId ->
            val q = questions.getOrNull(index) ?: return@setOnCheckedChangeListener
            val sel = when (checkedId) {
                b.optA.id -> 0
                b.optB.id -> 1
                b.optC.id -> 2
                b.optD.id -> 3
                else -> -1
            }
            if (sel >= 0) answers[q.id] = sel
        }

        savedInstanceState?.getIntegerArrayList(STATE_REVEALED_GATE)?.let { arr ->
            revealedGateIndices.clear()
            revealedGateIndices.addAll(arr)
        }

        if (questions.isEmpty()) {
            b.questionText.text = "İncelenecek yanlış soru yok."
            b.nextBtn.isEnabled = false
            b.summarySection.visibility = View.GONE
            b.tvQuotaBadge.visibility = View.GONE
            b.tvHintWatchAd.visibility = View.GONE
        } else {
            WrongReviewAnalytics.logOpen()
            render()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (gateFailReview && revealedGateIndices.isNotEmpty()) {
            outState.putIntegerArrayList(STATE_REVEALED_GATE, ArrayList(revealedGateIndices))
        }
    }

    private fun updateQuotaUi() {
        if (gateFailReview) {
            val pp = ProtectionPrefs(this)
            b.tvQuotaBadge.visibility = View.VISIBLE
            if (premiumStore.isPremium()) {
                b.tvQuotaBadge.text = getString(R.string.wrong_review_unlimited)
                b.tvHintWatchAd.visibility = View.GONE
            } else {
                b.tvQuotaBadge.text = getString(R.string.wrong_review_remaining, pp.gateFailReviewAdsRemaining())
                b.tvHintWatchAd.visibility = View.GONE
            }
            return
        }
        if (premiumStore.isPremium()) {
            b.tvQuotaBadge.text = getString(R.string.wrong_review_unlimited)
            b.tvQuotaBadge.visibility = View.VISIBLE
            b.tvHintWatchAd.visibility = View.GONE
        } else {
            val remaining = quotaStore.getRemaining()
            b.tvQuotaBadge.text = getString(R.string.wrong_review_remaining, remaining)
            b.tvQuotaBadge.visibility = View.VISIBLE
            b.tvHintWatchAd.visibility = if (remaining == 0) View.VISIBLE else View.GONE
        }
    }

    private fun render() {
        updateQuotaUi()
        val q = questions[index]

        if (gateFailReview && !premiumStore.isPremium() && index >= 3) {
            b.summarySection.visibility = View.VISIBLE
            b.tvUserChoiceSummary.text = getString(R.string.wrong_review_paywall_message)
            b.tvCorrectSummary.visibility = View.GONE
            b.tvHintSummary.visibility = View.GONE
            b.btnRetryQuestion.visibility = View.GONE
            b.optionsGroup.visibility = View.GONE
            b.nextBtn.visibility = View.VISIBLE
            b.nextBtn.text = if (index < questions.size - 1) "Sonraki" else "Bitir"
            b.nextBtn.setOnClickListener { advanceToNext() }
            b.feedbackText.visibility = View.GONE
            return
        }

        if (gateFailReview && !premiumStore.isPremium() && index < 3 && index !in revealedGateIndices) {
            b.summarySection.visibility = View.VISIBLE
            b.tvUserChoiceSummary.text = getString(R.string.wrong_detail_unlock_ad_message)
            b.tvCorrectSummary.visibility = View.GONE
            b.tvHintSummary.visibility = View.GONE
            b.btnRetryQuestion.visibility = View.GONE
            b.optionsGroup.visibility = View.GONE
            b.nextBtn.visibility = View.VISIBLE
            b.nextBtn.text = getString(R.string.wrong_review_btn_watch_ad)
            b.nextBtn.setOnClickListener { showGateFailAdForCurrentIndex() }
            b.feedbackText.visibility = View.GONE
            return
        }
        b.progressText.text = "${index + 1}/${questions.size}"
        b.subjectChip.text = "${q.subject.tr} • (İnceleme)"
        b.questionText.text = q.stem

        if (!q.imageAsset.isNullOrBlank()) {
            val path = q.imageAsset!!.trim()
            try {
                assets.open(path).use { input ->
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

        val displayChoices = QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices
        b.optA.text = displayChoices.getOrNull(0) ?: "-"
        b.optB.text = displayChoices.getOrNull(1) ?: "-"
        b.optC.text = displayChoices.getOrNull(2) ?: "-"
        b.optD.text = displayChoices.getOrNull(3) ?: "-"

        val userSel = sessionAnswers[q.id] ?: -1
        val userChoice = if (userSel in 0..3) displayChoices.getOrNull(userSel) ?: "?" else "-"
        val correctChoice = displayChoices.getOrNull(q.correctIndex) ?: "?"

        if (sessionAnswers.isNotEmpty() && sessionAnswers.containsKey(q.id) && !inRetryMode) {
            val explain = isParentReview || (gateFailReview && (premiumStore.isPremium() || index in revealedGateIndices))
            val canReveal = premiumStore.isPremium() || gateFailReview || quotaStore.getRemaining() > 0

            if (explain && !canReveal && !gateFailReview) {
                showSummaryBlocked(userChoice) { onRevealUnlocked ->
                    if (onRevealUnlocked && quotaStore.consumeOne()) {
                        WrongReviewAnalytics.logItemReveal()
                        showSummary(q, userChoice, correctChoice, explain)
                    }
                }
                return
            }

            if (explain && canReveal && !premiumStore.isPremium() && !gateFailReview) {
                if (quotaStore.consumeOne()) {
                    WrongReviewAnalytics.logItemReveal()
                }
            }
            showSummary(q, userChoice, correctChoice, explain)
        } else {
            b.summarySection.visibility = View.GONE
            b.optionsGroup.visibility = View.VISIBLE
            b.nextBtn.visibility = View.VISIBLE

            val saved = answers[q.id] ?: -1
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
                    b.optA.id -> 0
                    b.optB.id -> 1
                    b.optC.id -> 2
                    b.optD.id -> 3
                    else -> -1
                }
                if (sel >= 0) answers[q.id] = sel
            }

            b.feedbackText.visibility = View.GONE
            b.nextBtn.text = if (index < questions.size - 1) "Sonraki" else "Bitir"
        }
    }

    private fun showGateFailAdForCurrentIndex() {
        if (RewardedAdManager.isLoaded()) {
            RewardedAdManager.show(
                activity = this,
                onReward = {
                    WrongReviewAnalytics.logAdShown()
                    WrongReviewAnalytics.logAdRewarded()
                    val pp = ProtectionPrefs(this)
                    if (pp.consumeGateFailReviewAdSlot()) {
                        revealedGateIndices.add(index)
                        render()
                    } else {
                        Toast.makeText(this, getString(R.string.wrong_review_ad_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                onFail = { msg ->
                    val err = RewardedAdManager.lastLoadError?.let { "$msg ($it)" } ?: msg
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                }
            )
        } else {
            Toast.makeText(this, getString(R.string.wrong_review_ad_loading), Toast.LENGTH_SHORT).show()
            RewardedAdManager.preload(this)
        }
    }

    private fun showSummary(q: Question, userChoice: String, correctChoice: String, showFullExplain: Boolean = isParentReview) {
        b.summarySection.visibility = View.VISIBLE
        b.btnRetryQuestion.visibility = View.VISIBLE
        b.optionsGroup.visibility = View.GONE
        b.nextBtn.visibility = View.GONE
        b.feedbackText.visibility = View.GONE
        b.tvUserChoiceSummary.text = "Senin cevabın: $userChoice"
        if (showFullExplain) {
            b.tvCorrectSummary.visibility = View.VISIBLE
            b.tvCorrectSummary.text = "✓ Doğru: $correctChoice"
            b.tvHintSummary.apply {
                if (!q.hint.isNullOrBlank()) {
                    visibility = View.VISIBLE
                    text = "💡 ${q.hint}"
                } else visibility = View.GONE
            }
        } else {
            b.tvCorrectSummary.visibility = View.GONE
            b.tvHintSummary.visibility = View.GONE
            b.tvUserChoiceSummary.text = "Senin cevabın: $userChoice\n❌ Yanlış"
        }
    }

    private fun showSummaryBlocked(userChoice: String, onRevealUnlocked: (Boolean) -> Unit) {
        b.summarySection.visibility = View.VISIBLE
        b.tvUserChoiceSummary.text = "Senin cevabın: $userChoice\n❌ Yanlış"
        b.tvCorrectSummary.visibility = View.GONE
        b.tvHintSummary.visibility = View.GONE
        b.btnRetryQuestion.visibility = View.GONE
        b.optionsGroup.visibility = View.GONE
        b.nextBtn.visibility = View.VISIBLE
        b.nextBtn.text = if (index < questions.size - 1) "Sonraki" else "Bitir"
        b.nextBtn.setOnClickListener { advanceToNext() }
        b.feedbackText.visibility = View.GONE

        WrongReviewAnalytics.logPaywallOpened()
        showPaywall(onRevealUnlocked)
    }

    private fun showPaywall(onAdRewarded: (Boolean) -> Unit) {
        val builder = AlertDialog.Builder(this)
            .setMessage(getString(R.string.wrong_review_paywall_message))
            .setNegativeButton(getString(R.string.close)) { dialog, _ -> dialog.dismiss() }
            .setNeutralButton(getString(R.string.wrong_review_btn_premium)) { _, _ ->
                WrongReviewAnalytics.logPremiumClick()
                startActivity(Intent(this, TestSettingsActivity::class.java))
            }

        if (RewardedAdManager.isLoaded()) {
            builder.setPositiveButton(getString(R.string.wrong_review_btn_watch_ad)) { dialog, _ ->
                dialog.dismiss()
                RewardedAdManager.show(
                    activity = this,
                    onReward = {
                        WrongReviewAnalytics.logAdShown()
                        WrongReviewAnalytics.logAdRewarded()
                        quotaStore.addOneFromReward()
                        onAdRewarded(true)
                    },
                    onFail = { msg ->
                        val err = RewardedAdManager.lastLoadError?.let { "$msg ($it)" } ?: msg
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        } else {
            builder.setPositiveButton(getString(R.string.wrong_review_btn_watch_ad)) { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.wrong_review_ad_loading), Toast.LENGTH_SHORT).show()
                RewardedAdManager.preload(this)
            }
        }
        builder.create().show()
    }

    private fun enterRetryMode() {
        inRetryMode = true
        render()
    }

    private fun goNext() {
        val q = questions.getOrNull(index) ?: return
        val sel = when (b.optionsGroup.checkedRadioButtonId) {
            b.optA.id -> 0
            b.optB.id -> 1
            b.optC.id -> 2
            b.optD.id -> 3
            else -> -1
        }
        if (sel >= 0) answers[q.id] = sel

        val correct = sel == q.correctIndex
        b.feedbackText.visibility = View.VISIBLE
        b.feedbackText.setTextColor(getColor(if (correct) R.color.bb_turquoise else R.color.bb_error))
        val fbChoices = QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices
        b.feedbackText.text = if (correct) "✓ Doğru!" else if (isParentReview) "✗ Yanlış. Doğru: ${fbChoices.getOrNull(q.correctIndex) ?: "?"}" else "✗ Yanlış"
        if (correct) {
            repo.recordAnswers(
                listOf(AnswerRecord(q.id, sel, q.correctIndex)),
                questions.associateBy { it.id }
            )
            WrongQuestionScheduler(applicationContext).markCorrect(q.id)
            analyticsStore.recordReviewCorrection()
        }
        b.nextBtn.text = if (index < questions.size - 1) "Sonraki →" else "Bitir"
        b.nextBtn.setOnClickListener {
            advanceToNext()
        }
    }

    private fun advanceToNext() {
        b.nextBtn.setOnClickListener { goNext() }
        if (index < questions.size - 1) {
            index++
            inRetryMode = false
            render()
        } else {
            finish()
        }
    }
}
