package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.databinding.ActivityQuizBinding
import com.brainbuddy.app.gate.GateManager
import com.brainbuddy.app.LockScreenActivity
import ui.MainActivity

/**
 * Single-question retry after watching rewarded ad.
 * If correct: wrongCount-1; if wrongCount < 4, mark PASSED and unlock.
 * Student never sees correct answer.
 */
class GateRetrySingleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUIZ_ID = "quiz_id"
        const val EXTRA_QUESTION_ID = "question_id"
        const val EXTRA_SESSION_JSON = "session_json"
        const val EXTRA_QUESTIONS_JSON = "questions_json"
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    private lateinit var b: ActivityQuizBinding
    private var question: Question? = null
    private var session: QuizSession? = null
    private var wrongCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)

        val quizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: ""
        val questionId = intent.getStringExtra(EXTRA_QUESTION_ID) ?: ""
        val sessionJson = intent.getStringExtra(EXTRA_SESSION_JSON)
        session = QuizResultActivity.decodeSession(sessionJson)
        wrongCount = (session?.wrongCount ?: 4).coerceAtLeast(1)

        val questions = QuizResultActivity.decodeQuestions(intent.getStringExtra(EXTRA_QUESTIONS_JSON))
        question = questions.find { it.id == questionId }

        b.submitBtn.visibility = View.GONE
        b.hintBtn.visibility = View.GONE
        b.hintTimer.visibility = View.GONE
        b.hintText.visibility = View.GONE

        if (question == null) {
            b.questionText.text = "Soru bulunamadı."
            b.nextBtn.isEnabled = true
            b.nextBtn.text = "Ana Sayfaya Dön"
            b.nextBtn.setOnClickListener {
                val i = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(i)
                finish()
            }
            return
        }

        render(question!!)
        b.optionsGroup.setOnCheckedChangeListener { _, _ -> }

        b.nextBtn.text = "Gönder"
        b.nextBtn.setOnClickListener { onSubmit() }
    }

    private fun render(q: Question) {
        b.progressText.text = "1/1"
        b.subjectChip.text = "${q.subject.tr} • Tekrar"
        b.questionText.text = q.stem
        b.optA.text = q.choices.getOrNull(0) ?: "-"
        b.optB.text = q.choices.getOrNull(1) ?: "-"
        b.optC.text = q.choices.getOrNull(2) ?: "-"
        b.optD.text = q.choices.getOrNull(3) ?: "-"
        b.optionsGroup.clearCheck()
    }

    private fun onSubmit() {
        val q = question ?: return
        val sel = when (b.optionsGroup.checkedRadioButtonId) {
            b.optA.id -> 0
            b.optB.id -> 1
            b.optC.id -> 2
            b.optD.id -> 3
            else -> -1
        }
        if (sel < 0) return

        val correct = sel == q.correctIndex
        val newWrongCount = if (correct) (wrongCount - 1).coerceAtLeast(0) else wrongCount

        QuestionRepository(this).recordAnswers(
            listOf(AnswerRecord(q.id, sel, q.correctIndex)),
            mapOf(q.id to q)
        )

        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.trim().orEmpty()
        if (newWrongCount < 4) {
            GateManager.onGatePassed(this, blockedPkg)
            val s = session!!
            val updatedSession = s.copy(
                correctCount = s.correctCount + if (correct) 1 else 0,
                wrongCount = newWrongCount,
                passed = true,
                wrongQuestionIds = s.wrongQuestionIds - q.id
            )
            startActivity(Intent(this, QuizResultActivity::class.java).apply {
                putExtra(QuizResultActivity.EXTRA_SESSION, QuizResultActivity.encodeSession(updatedSession))
                putExtra(QuizResultActivity.EXTRA_QUESTIONS_JSON, intent.getStringExtra(EXTRA_QUESTIONS_JSON))
                putExtra(QuizResultActivity.EXTRA_IS_GATE_MODE, true)
            })
        } else {
            GateManager.onGateFailed(this, blockedPkg)
            val protectionPrefs = ProtectionPrefs(this)
            protectionPrefs.setLastFailedWrongIds(session?.wrongQuestionIds ?: emptyList())
            protectionPrefs.setLastFailedQuizId(session?.quizId ?: "")
            protectionPrefs.setLastFailedQuestionIds(session?.questionIds ?: emptyList())
            protectionPrefs.setLastFailedSessionJson(QuizResultActivity.encodeSession(session!!))
            val qJson = intent.getStringExtra(EXTRA_QUESTIONS_JSON) ?: ""
            if (qJson.isNotEmpty()) protectionPrefs.setLastFailedQuestionsJson(qJson)
            startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
