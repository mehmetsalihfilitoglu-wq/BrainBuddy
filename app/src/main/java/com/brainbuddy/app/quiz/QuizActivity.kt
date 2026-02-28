package com.brainbuddy.app.quiz

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.databinding.ActivityQuizBinding
import com.brainbuddy.app.R

class QuizActivity : AppCompatActivity() {

    private lateinit var b: ActivityQuizBinding

    private lateinit var repo: QuestionRepository
    private var questions: List<Question> = emptyList()
    private var index = 0

    private val answers = ArrayList<AnswerRecord>()
    private var hintTimer: CountDownTimer? = null
    private var hintAvailable = false

    private val currentLevel: LevelGroup = LevelGroup.GRADE_5_8

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = QuestionRepository(this)
        questions = repo.pickQuizQuestions(currentLevel, 20)

        b.submitBtn.setOnClickListener { onSubmit() }
        b.nextBtn.setOnClickListener { goNext() }
        b.hintBtn.setOnClickListener { showHintIfAllowed() }

        b.nextBtn.isEnabled = false

        if (questions.isEmpty()) {
            b.subjectChip.text = "Soru bulunamadı"
            b.questionText.text = "assets/questions_tr.json içine soru ekleyin."
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
        b.nextBtn.isEnabled = false

        hintAvailable = false
        b.hintBtn.isEnabled = false
        b.hintBtn.alpha = 0.5f
        b.hintText.visibility = View.GONE
        startHintCountdown(40)
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
                b.hintTimer.text = "İpucu hazır"
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
            b.feedbackText.text = "Lütfen bir şık seç."
            b.feedbackText.visibility = View.VISIBLE
            return
        }

        answers.add(AnswerRecord(q.id, selected, q.correctIndex))

        b.feedbackText.text = "Cevabın kaydedildi."
        b.feedbackText.visibility = View.VISIBLE

        b.submitBtn.isEnabled = false
        b.nextBtn.isEnabled = true
    }

    private fun goNext() {
        hintTimer?.cancel()

        if (index < questions.size - 1) {
            index++
            render()
        } else {
            val intent = Intent(this, QuizResultActivity::class.java).apply {
                putExtra(QuizResultActivity.EXTRA_ANSWERS_JSON,
                    QuizResultActivity.encodeAnswers(answers))
                putExtra(QuizResultActivity.EXTRA_LEVEL, currentLevel.name)
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