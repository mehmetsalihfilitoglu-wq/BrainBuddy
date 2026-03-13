package com.brainbuddy.app.quiz

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.databinding.ActivityQuizBinding

class QuizActivityRetryWrong : AppCompatActivity() {

    companion object {
        const val EXTRA_WRONG_IDS = "wrong_ids"
    }

    private lateinit var b: ActivityQuizBinding
    private lateinit var repo: QuestionRepository

    private var questions: List<Question> = emptyList()
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = QuestionRepository(this)
        val wrongIds = intent.getStringArrayListExtra(EXTRA_WRONG_IDS) ?: arrayListOf()

        val all = repo.loadAllQuestions().associateBy { it.id }
        questions = wrongIds.mapNotNull { all[it] }

        b.submitBtn.setOnClickListener { onSubmit() }
        b.nextBtn.setOnClickListener { goNext() }
        b.submitBtn.visibility = View.VISIBLE
        b.hintBtn.visibility = View.GONE
        b.hintTimer.visibility = View.GONE
        b.hintText.visibility = View.GONE

        b.nextBtn.isEnabled = false

        if (questions.isEmpty()) {
            b.subjectChip.text = "Yanlış soru yok"
            b.questionText.text = "Tekrar çözüm için yanlış soru bulunamadı."
            b.submitBtn.isEnabled = false
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
        b.subjectChip.text = "${q.subject.tr} • ${q.gradeTag} • (Yanlışları tekrar)"
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

        b.optionsGroup.clearCheck()
        b.optA.text = q.choices.getOrNull(0) ?: "-"
        b.optB.text = q.choices.getOrNull(1) ?: "-"
        b.optC.text = q.choices.getOrNull(2) ?: "-"
        b.optD.text = q.choices.getOrNull(3) ?: "-"

        b.submitBtn.isEnabled = true
        b.nextBtn.isEnabled = false
    }

    private fun onSubmit() {
        val selectedIndex = when (b.optionsGroup.checkedRadioButtonId) {
            b.optA.id -> 0
            b.optB.id -> 1
            b.optC.id -> 2
            b.optD.id -> 3
            else -> -1
        }

        if (selectedIndex == -1) {
            b.feedbackText.text = "Lütfen bir şık seç."
            b.feedbackText.visibility = View.VISIBLE
            return
        }

        b.feedbackText.text = "Cevabın kaydedildi."
        b.feedbackText.visibility = View.VISIBLE

        b.submitBtn.isEnabled = false
        b.nextBtn.isEnabled = true
    }

    private fun goNext() {
        b.feedbackText.visibility = View.GONE

        if (index < questions.size - 1) {
            index++
            render()
        } else {
            finish()
        }
    }
}
