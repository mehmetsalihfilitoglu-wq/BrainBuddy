package com.brainbuddy.app.quiz

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.brainbuddy.app.R
import com.brainbuddy.app.core.QuizPrefs
import com.brainbuddy.app.databinding.ActivityQuizBinding

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RETRY_WRONG = "retry_wrong"
        const val EXTRA_WRONG_IDS = "wrong_ids"
    }

    private lateinit var b: ActivityQuizBinding
    private lateinit var repo: QuestionRepository
    private lateinit var quizPrefs: QuizPrefs

    private var questions: List<Question> = emptyList()
    private var index = 0
    private var retryWrongMode = false

    private val answers = ArrayList<AnswerRecord>()
    private var hintTimer: CountDownTimer? = null
    private var hintAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = QuestionRepository(this)
        quizPrefs = QuizPrefs(this)
        retryWrongMode = intent.getBooleanExtra(EXTRA_RETRY_WRONG, false)
        val wrongIds = intent.getStringArrayListExtra(EXTRA_WRONG_IDS)

        val levelGroup = repo.getLevelGroupFromPrefs()
        questions = when {
            wrongIds != null && wrongIds.isNotEmpty() -> {
                val all = repo.loadAllQuestions().associateBy { it.id }
                val found = wrongIds.mapNotNull { all[it] }
                if (found.isEmpty()) {
                    val count = quizPrefs.questionsPerSession()
                    repo.pickQuizQuestions(levelGroup, count, quizPrefs.difficulty(), quizPrefs.selectedCategories())
                } else found
            }
            retryWrongMode -> repo.pickRetryWrongQuestions(levelGroup)
            else -> {
                val count = quizPrefs.questionsPerSession()
                val diff = quizPrefs.difficulty()
                val cats = quizPrefs.selectedCategories()
                repo.pickQuizQuestions(levelGroup, count, diff, cats)
            }
        }

        b.submitBtn.setOnClickListener { onSubmit() }
        b.nextBtn.setOnClickListener { goNext() }
        b.hintBtn.setOnClickListener { showHintIfAllowed() }

        b.nextBtn.isEnabled = false

        // Only show "soru yok" for retry mode with no wrong questions; never for filter/JSON issues
        if (questions.isEmpty()) {
            b.subjectChip.text = "Soru bulunamadı"
            b.questionText.text = if (retryWrongMode) "Yanlış cevaplanan soru yok. Önce bir test çöz!" else "Soru havuzunda soru yok."
            b.submitBtn.isEnabled = false
            b.hintBtn.isEnabled = false
            b.nextBtn.isEnabled = false
        } else {
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
                assets.open(q.imageAsset!!).use {
                    b.questionImage.setImageBitmap(BitmapFactory.decodeStream(it))
                    b.questionImage.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                b.questionImage.visibility = View.GONE
            }
        } else b.questionImage.visibility = View.GONE

        b.optionsGroup.clearCheck()
        b.optA.text = q.choices.getOrNull(0) ?: "-"
        b.optB.text = q.choices.getOrNull(1) ?: "-"
        b.optC.text = q.choices.getOrNull(2) ?: "-"
        b.optD.text = q.choices.getOrNull(3) ?: "-"

        b.submitBtn.isEnabled = true
        b.submitBtn.text = "Cevabı Kontrol Et"
        b.nextBtn.isEnabled = false

        hintAvailable = false
        b.hintBtn.isEnabled = false
        b.hintBtn.alpha = 0.5f
        b.hintText.visibility = View.GONE
        b.feedbackText.visibility = View.GONE

        listOf(b.optA, b.optB, b.optC, b.optD).forEach {
            it.alpha = 1f
            it.scaleX = 1f
            it.scaleY = 1f
            it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        startHintCountdown(30)
    }

    private fun startHintCountdown(seconds: Int) {
        hintTimer?.cancel()
        b.hintTimer.text = "İpucu: ${seconds}s"

        hintTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(ms: Long) {
                b.hintTimer.text = "İpucu: ${(ms / 1000).toInt()}s"
            }

            override fun onFinish() {
                hintAvailable = true
                b.hintTimer.text = "İpucu hazır ✨"
                b.hintBtn.isEnabled = true
                b.hintBtn.alpha = 1f
            }
        }.start()
    }

    private fun showHintIfAllowed() {
        val hint = questions[index].hint
        if (!hintAvailable) return
        b.hintText.text = hint ?: "Bu soru için ipucu yok."
        b.hintText.visibility = View.VISIBLE
        b.hintBtn.isEnabled = false
        b.hintBtn.alpha = 0.5f
    }

    private fun onSubmit() {
        val q = questions[index]
        val selected = when (b.optionsGroup.checkedRadioButtonId) {
            b.optA.id -> 0
            b.optB.id -> 1
            b.optC.id -> 2
            b.optD.id -> 3
            else -> -1
        }

        if (selected == -1) {
            b.feedbackText.text = "Önce bir şık seç! 😊"
            b.feedbackText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            b.feedbackText.visibility = View.VISIBLE
            return
        }

        val correct = selected == q.correctIndex
        answers.add(AnswerRecord(q.id, selected, q.correctIndex))

        // Feedback
        b.feedbackText.visibility = View.VISIBLE
        if (correct) {
            b.feedbackText.text = "Harika! Doğru cevap! 🎉"
            b.feedbackText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            b.feedbackText.text = "Yanlış. Doğru cevap: ${q.choices.getOrNull(q.correctIndex) ?: "?"}"
            b.feedbackText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }

        // Visual feedback on choice buttons (subtle)
        val optViews = listOf(b.optA, b.optB, b.optC, b.optD)
        optViews.forEachIndexed { i, v ->
            val color = when {
                i == selected && correct -> ContextCompat.getColor(this, android.R.color.holo_green_light)
                i == selected -> ContextCompat.getColor(this, android.R.color.holo_red_light)
                i == q.correctIndex -> ContextCompat.getColor(this, android.R.color.holo_green_light)
                else -> android.graphics.Color.TRANSPARENT
            }
            v.setBackgroundColor(color)
        }

        // Simple scale animation
        b.feedbackText.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).withEndAction {
            b.feedbackText.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()

        b.submitBtn.isEnabled = false
        b.nextBtn.isEnabled = true
        b.nextBtn.text = if (index < questions.size - 1) "Sonraki Soru →" else "Sonuçları Gör"
        hintTimer?.cancel()
    }

    private fun goNext() {
        hintTimer?.cancel()

        if (index < questions.size - 1) {
            index++
            render()
        } else {
            repo.recordAnswers(answers)
            val intent = Intent(this, QuizResultActivity::class.java).apply {
                putExtra(QuizResultActivity.EXTRA_ANSWERS_JSON, QuizResultActivity.encodeAnswers(answers))
                putExtra(QuizResultActivity.EXTRA_LEVEL, repo.getLevelGroupFromPrefs().name)
                putExtra(QuizResultActivity.EXTRA_RETRY_WRONG, retryWrongMode)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        hintTimer?.cancel()
        super.onDestroy()
    }
}
