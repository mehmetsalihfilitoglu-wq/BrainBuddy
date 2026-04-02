package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.LockScreenActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.databinding.ActivityQuizBinding
import com.brainbuddy.app.gate.GateManager
import ui.MainActivity

/**
 * Legacy single-question screen. Gate unlock cannot be granted from a partial retry; always returns to lock.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)

        val questionId = intent.getStringExtra(EXTRA_QUESTION_ID) ?: ""
        session = QuizResultActivity.decodeSession(intent.getStringExtra(EXTRA_SESSION_JSON))
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
        val displayChoices = QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices
        b.optA.text = displayChoices.getOrNull(0) ?: "-"
        b.optB.text = displayChoices.getOrNull(1) ?: "-"
        b.optC.text = displayChoices.getOrNull(2) ?: "-"
        b.optD.text = displayChoices.getOrNull(3) ?: "-"
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

        QuestionRepository(this).recordAnswers(
            listOf(AnswerRecord(q.id, sel, q.correctIndex)),
            mapOf(q.id to q)
        )

        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.trim().orEmpty()
        GateManager.onGateFailed(this, blockedPkg)
        val protectionPrefs = ProtectionPrefs(this)
        protectionPrefs.setLastFailedWrongIds(session?.wrongQuestionIds ?: emptyList())
        protectionPrefs.setLastFailedQuizId(session?.quizId ?: "")
        protectionPrefs.setLastFailedQuestionIds(session?.questionIds ?: emptyList())
        session?.let {
            protectionPrefs.setLastFailedSessionJson(QuizResultActivity.encodeSession(it))
        }
        val qJson = intent.getStringExtra(EXTRA_QUESTIONS_JSON) ?: ""
        if (qJson.isNotEmpty()) protectionPrefs.setLastFailedQuestionsJson(qJson)
        startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
