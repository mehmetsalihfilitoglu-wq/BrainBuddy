package com.brainbuddy.app.quiz

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizPrefs
import com.brainbuddy.app.databinding.ActivityQuizBinding
import java.util.UUID

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RETRY_WRONG = "retry_wrong"
        const val EXTRA_WRONG_IDS = "wrong_ids"
        const val EXTRA_QUIZ_ID = "quiz_id"
        const val EXTRA_IS_RETRY = "is_retry"
        const val EXTRA_QUESTIONS_JSON = "questions_json"
        const val EXTRA_GATE_MODE = "gate_mode"
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_REMEDIAL = "remedial"
        const val EXTRA_BOSS_LEVEL = "boss_level"
    }

    private lateinit var b: ActivityQuizBinding
    private lateinit var repo: QuestionRepository
    private lateinit var quizPrefs: QuizPrefs

    private var questions: List<Question> = emptyList()
    private var index = 0
    private var retryWrongMode = false
    private var isRetryOfLockedQuiz = false
    private var quizId: String = ""
    private var startedAt: Long = 0L

    private val answers = mutableMapOf<String, Int>()
    private var hintTimer: CountDownTimer? = null
    private var hintAvailable = false
    private var isFinishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = QuestionRepository(this)
        quizPrefs = QuizPrefs(this)
        retryWrongMode = intent.getBooleanExtra(EXTRA_RETRY_WRONG, false)
        isRetryOfLockedQuiz = intent.getBooleanExtra(EXTRA_IS_RETRY, false)
        val isRemedial = intent.getBooleanExtra(EXTRA_REMEDIAL, false)
        quizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: UUID.randomUUID().toString()
        val wrongIds = intent.getStringArrayListExtra(EXTRA_WRONG_IDS)

        val levelGroup = repo.getLevelGroupFromPrefs()
        val protectionPrefs = ProtectionPrefs(this)
        val bossLevel = intent.getIntExtra(EXTRA_BOSS_LEVEL, -1)
        val isGateMode = intent.getBooleanExtra(EXTRA_GATE_MODE, false)
        questions = when {
            bossLevel > 0 -> repo.pickBossQuestions(levelGroup, 15)
            isGateMode -> repo.pickGateQuestions(levelGroup, 10)
            isRemedial -> repo.pickRemedialQuestions(levelGroup, 10, protectionPrefs.lastFailedWrongIds())
            wrongIds != null && wrongIds.isNotEmpty() -> {
                val all = repo.loadAllQuestions().associateBy { it.id }
                val found = wrongIds.mapNotNull { all[it] }
                if (found.isEmpty()) {
                    val count = quizPrefs.questionsPerSession()
                    repo.pickQuizQuestions(levelGroup, count, quizPrefs.difficulty(), quizPrefs.selectedCategories())
                } else found.shuffled()
            }
            retryWrongMode -> repo.pickRetryWrongQuestions(levelGroup).shuffled()
            else -> {
                val count = quizPrefs.questionsPerSession()
                val diff = quizPrefs.difficulty()
                val cats = quizPrefs.selectedCategories()
                repo.pickQuizQuestions(levelGroup, count, diff, cats)
            }
        }

        b.submitBtn.visibility = View.GONE
        b.nextBtn.setOnClickListener { goNext() }
        b.hintBtn.setOnClickListener { showHintIfAllowed() }

        if (questions.isEmpty()) {
            b.subjectChip.text = "Soru bulunamadı"
            b.questionText.text = if (retryWrongMode) "Yanlış cevaplanan soru yok. Önce bir test çöz!" else "Soru havuzunda soru yok. Lütfen soru ekleyin veya içe aktarın."
            b.hintBtn.isEnabled = false
            b.nextBtn.isEnabled = false
        } else {
            startedAt = System.currentTimeMillis()
            render()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun render() {
        val q = questions[index]

        b.progressText.text = "${index + 1}/${questions.size}"
        b.subjectChip.text = "${q.subject.tr} • ${q.gradeTag}"
        b.questionText.text = q.stem

        if (!q.imageAsset.isNullOrBlank()) {
            try {
                assets.open(q.imageAsset!!.trim()).use {
                    b.questionImage.setImageBitmap(BitmapFactory.decodeStream(it))
                    b.questionImage.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                b.questionImage.visibility = View.GONE
            }
        } else b.questionImage.visibility = View.GONE

        b.optA.text = q.choices.getOrNull(0) ?: "-"
        b.optB.text = q.choices.getOrNull(1) ?: "-"
        b.optC.text = q.choices.getOrNull(2) ?: "-"
        b.optD.text = q.choices.getOrNull(3) ?: "-"

        b.optionsGroup.setOnCheckedChangeListener(null)
        val saved = answers[q.id] ?: -1
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
            if (sel >= 0) {
                answers[q.id] = sel
                if (index == questions.size - 1) {
                    b.nextBtn.postDelayed({ if (!isFinishing) finishTest() }, 800)
                }
            }
        }

        b.feedbackText.visibility = View.GONE
        listOf(b.optA, b.optB, b.optC, b.optD).forEach {
            it.alpha = 1f
            it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        b.nextBtn.isEnabled = true
        b.nextBtn.text = if (index < questions.size - 1) "Sonraki Soru →" else "Bitir"

        hintAvailable = false
        b.hintBtn.isEnabled = false
        b.hintBtn.alpha = 0.5f
        b.hintText.visibility = View.GONE
        startHintCountdown(30)
    }

    private fun startHintCountdown(seconds: Int) {
        hintTimer?.cancel()
        b.hintTimer.text = "İpucu: ${seconds}s"
        hintTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(ms: Long) { b.hintTimer.text = "İpucu: ${(ms / 1000).toInt()}s" }
            override fun onFinish() {
                hintAvailable = true
                b.hintTimer.text = "İpucu hazır ✨"
                b.hintBtn.isEnabled = true
                b.hintBtn.alpha = 1f
            }
        }.start()
    }

    private fun showHintIfAllowed() {
        if (!hintAvailable) return
        b.hintText.text = questions[index].hint ?: "Bu soru için ipucu yok."
        b.hintText.visibility = View.VISIBLE
        b.hintBtn.isEnabled = false
        b.hintBtn.alpha = 0.5f
    }

    private fun goNext() {
        saveCurrentSelection()
        hintTimer?.cancel()
        if (index < questions.size - 1) {
            index++
            render()
        } else {
            finishTest()
        }
    }

    private fun saveCurrentSelection() {
        val q = questions.getOrNull(index) ?: return
        val sel = when (b.optionsGroup.checkedRadioButtonId) {
            b.optA.id -> 0
            b.optB.id -> 1
            b.optC.id -> 2
            b.optD.id -> 3
            else -> -1
        }
        if (sel >= 0) answers[q.id] = sel
    }

    private fun finishTest() {
        if (isFinishing) return
        isFinishing = true
        saveCurrentSelection()
        hintTimer?.cancel()

        var correctCount = 0
        var wrongCount = 0
        val wrongIds = mutableListOf<String>()
        val answerRecords = mutableListOf<AnswerRecord>()

        for (q in questions) {
            val sel = answers[q.id] ?: -1
            when {
                sel < 0 -> { }
                sel == q.correctIndex -> {
                    correctCount++
                    answerRecords.add(AnswerRecord(q.id, sel, q.correctIndex))
                }
                else -> {
                    wrongCount++
                    wrongIds.add(q.id)
                    answerRecords.add(AnswerRecord(q.id, sel, q.correctIndex))
                }
            }
        }
        val blankCount = questions.size - correctCount - wrongCount

        repo.recordAnswers(answerRecords)

        val passed = wrongCount <= 3
        val completedAt = System.currentTimeMillis()
        val session = QuizSession(
            quizId = quizId,
            startedAt = if (startedAt > 0L) startedAt else completedAt - 60000,
            questionIds = questions.map { it.id },
            answers = answers.toMap(),
            completedAt = completedAt,
            correctCount = correctCount,
            wrongCount = wrongCount,
            blankCount = blankCount,
            passed = passed,
            wrongQuestionIds = wrongIds
        )

        val protectionPrefs = ProtectionPrefs(this)
        val isGateMode = intent.getBooleanExtra(EXTRA_GATE_MODE, false)
        val isRemedial = intent.getBooleanExtra(EXTRA_REMEDIAL, false)
        if (passed && (isGateMode || isRetryOfLockedQuiz || isRemedial)) {
            com.brainbuddy.app.gate.GateManager.onGatePassed(this)
        }
        val passedBossLevel = intent.getIntExtra(EXTRA_BOSS_LEVEL, -1)
        if (passed && passedBossLevel > 0) {
            BossTestStore(this).markBossPassed(passedBossLevel)
        }
        if (!passed && !isRetryOfLockedQuiz && !isRemedial) {
            com.brainbuddy.app.core.ReportStore(this).recordLockEvent()
            com.brainbuddy.app.gate.GateManager.onGateFailed(this)
            protectionPrefs.setLastFailedWrongIds(wrongIds)
            protectionPrefs.setLastFailedQuizId(quizId)
            protectionPrefs.setLastFailedQuestionIds(questions.map { it.id })
            protectionPrefs.setLastFailedSessionJson(QuizResultActivity.encodeSession(session))
        }

        startActivity(Intent(this, QuizResultActivity::class.java).apply {
            putExtra(QuizResultActivity.EXTRA_SESSION, QuizResultActivity.encodeSession(session))
            putExtra(QuizResultActivity.EXTRA_QUESTIONS_JSON, QuizResultActivity.encodeQuestions(questions))
            putExtra(QuizResultActivity.EXTRA_IS_RETRY, isRetryOfLockedQuiz)
            putExtra(QuizResultActivity.EXTRA_IS_GATE_MODE, isGateMode)
        })
        finish()
    }
}
