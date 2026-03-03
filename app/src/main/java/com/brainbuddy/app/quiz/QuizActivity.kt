package com.brainbuddy.app.quiz

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.LastTestUnlockStore
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.RetryUnlockStore
import com.brainbuddy.app.core.QuizRetryPolicy
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
        const val EXTRA_REPLAY_FROM_LAST_TEST = "replay_from_last_test"
        const val EXTRA_UNLOCK_TOKEN = "unlock_token"
        const val EXTRA_QUESTION_IDS_FOR_REPLAY = "question_ids_for_replay"
        const val EXTRA_RETRY_AFTER_AD = "retry_after_ad"
        const val EXTRA_RETRY_UNLOCK_TOKEN = "retry_unlock_token"
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
    private var isFinishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)
        try {
            initQuiz(savedInstanceState)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("QuizActivity", "OOM", e)
            android.widget.Toast.makeText(this, "Bellek yetersiz. Uygulamayı yeniden başlatın.", android.widget.Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            android.util.Log.e("QuizActivity", "init error", e)
            finish()
        }
    }


    private fun initQuiz(savedInstanceState: Bundle?) {
        repo = QuestionRepository(this)
        quizPrefs = QuizPrefs(this)
        val protectionPrefs = ProtectionPrefs(this)
        val isReplayFromLastTest = intent.getBooleanExtra(EXTRA_REPLAY_FROM_LAST_TEST, false)
        if (isReplayFromLastTest) {
            val replayQuizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: ""
            val token = intent.getStringExtra(EXTRA_UNLOCK_TOKEN) ?: ""
            if (!PremiumStore(this).isPremium()) {
                val unlockStore = LastTestUnlockStore(this)
                val ids = unlockStore.consumeUnlock(token, replayQuizId)
                if (ids == null || ids.isEmpty()) {
                    startActivity(Intent(this, com.brainbuddy.app.ui.AdLimitReachedActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(com.brainbuddy.app.ui.AdLimitReachedActivity.EXTRA_TITLE, getString(com.brainbuddy.app.R.string.ad_limit_reached_bypass_title))
                        putExtra(com.brainbuddy.app.ui.AdLimitReachedActivity.EXTRA_MESSAGE, getString(com.brainbuddy.app.R.string.ad_limit_reached_bypass_message))
                    })
                    finish()
                    return
                }
            }
        }
        retryWrongMode = intent.getBooleanExtra(EXTRA_RETRY_WRONG, false)
        isRetryOfLockedQuiz = intent.getBooleanExtra(EXTRA_IS_RETRY, false)
        val retryAfterAd = intent.getBooleanExtra(EXTRA_RETRY_AFTER_AD, false)
        val retryUnlockToken = intent.getStringExtra(EXTRA_RETRY_UNLOCK_TOKEN)

        val blockedPkgForRetry = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.trim().orEmpty()
        if (isRetryOfLockedQuiz && !retryAfterAd && protectionPrefs.userLocked()) {
            val policy = QuizRetryPolicy(this)
            when (policy.getStartMode()) {
                QuizRetryPolicy.StartMode.REQUIRE_AD -> {
                    val qId = protectionPrefs.lastFailedQuizId()
                    val qIds = protectionPrefs.lastFailedQuestionIds()
                    if (qIds.size >= QuestionRepository.MIN_QUESTIONS_PER_TEST) {
                        startActivity(Intent(this, QuizRetryAdActivity::class.java).apply {
                            putExtra(QuizRetryAdActivity.EXTRA_QUIZ_ID, qId)
                            putStringArrayListExtra(QuizRetryAdActivity.EXTRA_QUESTION_IDS, java.util.ArrayList(qIds))
                            putExtra(QuizRetryAdActivity.EXTRA_BLOCKED_PACKAGE, blockedPkgForRetry)
                        })
                    }
                    finish()
                    return
                }
                QuizRetryPolicy.StartMode.WAIT_COOLDOWN -> {
                    val qId = protectionPrefs.lastFailedQuizId()
                    val qIds = protectionPrefs.lastFailedQuestionIds()
                    if (qIds.size >= QuestionRepository.MIN_QUESTIONS_PER_TEST) {
                        startActivity(Intent(this, QuizCooldownActivity::class.java).apply {
                            putExtra(QuizCooldownActivity.EXTRA_QUIZ_ID, qId)
                            putStringArrayListExtra(QuizCooldownActivity.EXTRA_QUESTION_IDS, java.util.ArrayList(qIds))
                            putExtra(QuizCooldownActivity.EXTRA_BLOCKED_PACKAGE, blockedPkgForRetry)
                        })
                    }
                    finish()
                    return
                }
                else -> { }
            }
        }

        // Bypass prevention: non-premium retry requires valid one-shot token (from ad or cooldown)
        if (isRetryOfLockedQuiz && protectionPrefs.userLocked() && !PremiumStore(this).isPremium()) {
            if (retryUnlockToken.isNullOrBlank()) {
                startActivity(Intent(this, com.brainbuddy.app.LockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
                return
            }
            val retryStore = RetryUnlockStore(this)
            val quizIdForRetry = protectionPrefs.lastFailedQuizId()
            val consumed = retryStore.consumeRetryToken(retryUnlockToken, quizIdForRetry)
            if (consumed == null || consumed.isEmpty()) {
                startActivity(Intent(this, com.brainbuddy.app.LockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
                return
            }
        }

        val isRemedial = intent.getBooleanExtra(EXTRA_REMEDIAL, false)
        quizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: UUID.randomUUID().toString()
        val wrongIds = intent.getStringArrayListExtra(EXTRA_WRONG_IDS)
        val replayQuestionIds = intent.getStringArrayListExtra(EXTRA_QUESTION_IDS_FOR_REPLAY)

        val levelGroup = repo.getLevelGroupFromPrefs()
        val bossLevel = intent.getIntExtra(EXTRA_BOSS_LEVEL, -1)
        val isGateMode = intent.getBooleanExtra(EXTRA_GATE_MODE, false)
        var remedialFallbackWarning = false
        val targetCount = QuestionRepository.MIN_QUESTIONS_PER_TEST
        questions = when {
            isReplayFromLastTest && replayQuestionIds != null && replayQuestionIds.size >= targetCount -> {
                val all = repo.loadAllQuestions().associateBy { it.id }
                replayQuestionIds.mapNotNull { all[it] }
            }
            bossLevel > 0 -> repo.pickBossQuestions(levelGroup, targetCount)
            isGateMode && isRetryOfLockedQuiz -> {
                val ids = protectionPrefs.lastFailedQuestionIds()
                if (ids.size >= targetCount) {
                    val all = repo.loadAllQuestions().associateBy { it.id }
                    ids.mapNotNull { all[it] }
                } else {
                    repo.pickGateQuestions(levelGroup, targetCount)
                }
            }
            isGateMode -> repo.pickGateQuestions(levelGroup, targetCount)
            isRemedial -> {
                val (q, usedFallback) = repo.pickRemedialQuestions(levelGroup, targetCount, protectionPrefs.lastFailedWrongIds())
                remedialFallbackWarning = usedFallback
                q
            }
            wrongIds != null && wrongIds.isNotEmpty() -> {
                val all = repo.loadAllQuestions().associateBy { it.id }
                val found = wrongIds.mapNotNull { all[it] }
                if (found.size < targetCount) {
                    repo.pickQuizQuestions(levelGroup, targetCount, quizPrefs.difficulty(), quizPrefs.selectedCategories(), quizId)
                } else found.shuffled().take(targetCount)
            }
            retryWrongMode -> {
                val wrong = repo.pickRetryWrongQuestions(levelGroup)
                if (wrong.size < targetCount) repo.pickQuizQuestions(levelGroup, targetCount, quizPrefs.difficulty(), quizPrefs.selectedCategories(), quizId)
                else wrong.shuffled().take(targetCount)
            }
            else -> repo.pickQuizQuestions(levelGroup, targetCount, quizPrefs.difficulty(), quizPrefs.selectedCategories(), quizId)
        }

        b.submitBtn.visibility = View.GONE
        b.nextBtn.setOnClickListener { goNext() }

        if (questions.isEmpty() || questions.size < QuestionRepository.MIN_QUESTIONS_PER_TEST) {
            b.subjectChip.text = "Soru havuzu yetersiz"
            val msg = if (questions.isEmpty()) {
                if (retryWrongMode) "Yanlış cevaplanan soru yok. Önce bir test çöz!"
                else "Soru havuzu yetersiz (en az ${QuestionRepository.MIN_QUESTIONS_PER_TEST} soru gerekli). Veli: Soru paketi ekleyin veya içe aktarın."
            } else "Soru havuzu yetersiz (${questions.size} soru mevcut, en az ${QuestionRepository.MIN_QUESTIONS_PER_TEST} gerekli)."
            b.questionText.text = msg
            b.nextBtn.isEnabled = false
            b.nextBtn.text = "Ana Sayfaya Dön"
            b.nextBtn.setOnClickListener {
                if (intent.getBooleanExtra(EXTRA_GATE_MODE, false) && ProtectionPrefs(this).userLocked()) {
                    startActivity(Intent(this, com.brainbuddy.app.LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    startActivity(Intent(this, com.brainbuddy.app.HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                finish()
            }
        } else {
            if (remedialFallbackWarning) {
                android.widget.Toast.makeText(
                    this,
                    "Soru havuzu sınırlı. Veli: Daha fazla soru paketi ekleyin.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            render()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("quiz_index", index)
        outState.putString("quiz_id", quizId)
        outState.putLong("quiz_started", startedAt)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun render() {
        if (questions.isEmpty()) return
        index = index.coerceIn(0, questions.size - 1)
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
                    // Auto-submit on last question - no Finish Test button
                    b.nextBtn.postDelayed({ if (!isFinishing) finishTest() }, 600)
                }
            }
        }

        b.feedbackText.visibility = View.GONE
        listOf(b.optA, b.optB, b.optC, b.optD).forEach {
            it.alpha = 1f
            it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        b.nextBtn.isEnabled = true
        b.nextBtn.text = if (index < questions.size - 1) "Sonraki Soru →" else "Gönder"
        b.nextBtn.visibility = View.VISIBLE
    }

    private fun goNext() {
        saveCurrentSelection()
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

        val questionsMap = questions.associateBy { it.id }
        repo.recordAnswers(answerRecords, questionsMap, quizId)
        repo.onQuizCompleted(questions.map { it.id })
        val bySubject = questions.groupBy { it.subject.tr }.mapValues { (_, qs) -> qs.size }
        val breakdown = bySubject.entries.joinToString(", ") { "${it.key}: ${it.value}" }.takeIf { it.isNotBlank() }
        repo.insertSnapshot(quizId, correctCount, questions.size, questions.map { it.id }, answers.toMap(), wrongIds, breakdown)

        val isGateMode = intent.getBooleanExtra(EXTRA_GATE_MODE, false)
        val isRemedial = intent.getBooleanExtra(EXTRA_REMEDIAL, false)
        val total = questions.size
        val accuracy = if (total > 0) correctCount.toFloat() / total else 0f
        val passed = when {
            isGateMode || isRetryOfLockedQuiz || isRemedial -> wrongCount < 4
            else -> accuracy >= 0.6f
        }
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
        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.trim().orEmpty()
        if (passed && wrongCount < 4 && (isGateMode || isRetryOfLockedQuiz || isRemedial)) {
            com.brainbuddy.app.gate.GateManager.onGatePassed(this, blockedPkg)
            com.brainbuddy.app.core.QuizRetryPolicy(this).onPass()
        }
        val passedBossLevel = intent.getIntExtra(EXTRA_BOSS_LEVEL, -1)
        if (passed && passedBossLevel > 0) {
            BossTestStore(this).markBossPassed(passedBossLevel)
        }
        if (!passed && wrongCount >= 4 && (isGateMode || isRetryOfLockedQuiz) && !isRemedial) {
            com.brainbuddy.app.core.ReportStore(this).recordLockEvent()
            com.brainbuddy.app.gate.GateManager.onGateFailed(this, blockedPkg)
            protectionPrefs.setLastFailedWrongIds(wrongIds)
            protectionPrefs.setLastFailedQuizId(quizId)
            protectionPrefs.setLastFailedQuestionIds(questions.map { it.id })
            protectionPrefs.setLastFailedSessionJson(QuizResultActivity.encodeSession(session))
            protectionPrefs.setLastFailedQuestionsJson(QuizResultActivity.encodeQuestions(questions))
            com.brainbuddy.app.core.QuizRetryPolicy(this).onFail(this, com.brainbuddy.app.core.QuizRetryPolicy.SameTestToken(quizId, questions.map { it.id }))
        }

        startActivity(Intent(this, QuizResultActivity::class.java).apply {
            putExtra(QuizResultActivity.EXTRA_SESSION, QuizResultActivity.encodeSession(session))
            putExtra(QuizResultActivity.EXTRA_QUESTIONS_JSON, QuizResultActivity.encodeQuestions(questions))
            putExtra(QuizResultActivity.EXTRA_IS_RETRY, isRetryOfLockedQuiz)
            putExtra(QuizResultActivity.EXTRA_IS_GATE_MODE, isGateMode)
            putExtra(QuizResultActivity.EXTRA_BLOCKED_PACKAGE, blockedPkg)
        })
        finish()
    }
}
