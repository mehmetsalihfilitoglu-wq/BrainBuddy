package com.brainbuddy.app.quiz

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.databinding.ActivityWrongAnswerReviewBinding

/**
 * Review wrong answers only. Shows question, user's wrong choice, correct answer, and optional hint.
 * "Retry question" lets user answer again in review mode. Does NOT change original test score.
 * Tracks reviewCorrectedCount separately via AnalyticsStore.
 */
class WrongAnswerReviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WRONG_IDS = "wrong_ids"
        const val EXTRA_SESSION_JSON = "session_json"
        /** When true (Parent mode), show correct answer and explanation. When false (Student), show only "Wrong". */
        const val EXTRA_IS_PARENT_REVIEW = "is_parent_review"
    }

    private lateinit var b: ActivityWrongAnswerReviewBinding
    private lateinit var repo: QuestionRepository
    private lateinit var analyticsStore: AnalyticsStore
    private var questions: List<Question> = emptyList()
    private var index = 0
    private val answers = mutableMapOf<String, Int>()
    private var sessionAnswers: Map<String, Int> = emptyMap()
    private var inRetryMode = false
    private var isParentReview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWrongAnswerReviewBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = QuestionRepository(this)
        analyticsStore = AnalyticsStore(this)
        val wrongIds = intent.getStringArrayListExtra(EXTRA_WRONG_IDS) ?: arrayListOf()
        val sessionJson = intent.getStringExtra(EXTRA_SESSION_JSON)
        val requestedParent = intent.getBooleanExtra(EXTRA_IS_PARENT_REVIEW, false)
        isParentReview = requestedParent && AppModeManager.isParentMode()
        val session = QuizResultActivity.decodeSession(sessionJson)
        sessionAnswers = session?.answers ?: emptyMap()

        val allMap = repo.loadAllQuestions().associateBy { it.id }
        questions = wrongIds.mapNotNull { allMap[it] }

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

        if (questions.isEmpty()) {
            b.questionText.text = "İncelenecek yanlış soru yok."
            b.nextBtn.isEnabled = false
            b.summarySection.visibility = View.GONE
        } else {
            render()
        }
    }

    private fun render() {
        val q = questions[index]
        b.progressText.text = "${index + 1}/${questions.size}"
        b.subjectChip.text = "${q.subject.tr} • (İnceleme)"
        b.questionText.text = q.stem

        b.optA.text = q.choices.getOrNull(0) ?: "-"
        b.optB.text = q.choices.getOrNull(1) ?: "-"
        b.optC.text = q.choices.getOrNull(2) ?: "-"
        b.optD.text = q.choices.getOrNull(3) ?: "-"

        val userSel = sessionAnswers[q.id] ?: -1
        val userChoice = if (userSel in 0..3) q.choices.getOrNull(userSel) ?: "?" else "-"
        val correctChoice = q.choices.getOrNull(q.correctIndex) ?: "?"

        if (sessionAnswers.isNotEmpty() && sessionAnswers.containsKey(q.id) && !inRetryMode) {
            b.summarySection.visibility = View.VISIBLE
            b.optionsGroup.visibility = View.GONE
            b.nextBtn.visibility = View.GONE
            b.feedbackText.visibility = View.GONE
            b.tvUserChoiceSummary.text = "Senin cevabın: $userChoice"
            if (isParentReview) {
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
        b.feedbackText.text = if (correct) "✓ Doğru!" else if (isParentReview) "✗ Yanlış. Doğru: ${q.choices.getOrNull(q.correctIndex) ?: "?"}" else "✗ Yanlış"
        if (correct) {
            repo.recordAnswers(listOf(AnswerRecord(q.id, sel, q.correctIndex)))
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
