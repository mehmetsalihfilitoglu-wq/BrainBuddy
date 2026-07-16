package com.mioacademy.app.dailychallenge

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mioacademy.app.R
import com.mioacademy.app.core.ExamType
import com.mioacademy.app.core.StudyAreaManager
import com.mioacademy.app.db.QuestionEntity
import com.mioacademy.app.ui.onTap
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * The Daily Challenge flow: exactly five NEW questions, one at a time, driven entirely by the tested
 * [DailyChallengeController]/engine. Each answer is persisted immediately, so a restart/process death
 * resumes at the same question ([DailyChallengeHomePresenter.resumeIndex]) and a completed challenge
 * jumps straight to its result — a sixth new question can never appear.
 */
class DailyChallengeActivity : AppCompatActivity() {

    private val controller by lazy { DailyChallengeController(this) }
    private lateinit var userId: String
    private lateinit var exam: ExamType
    private lateinit var localDate: String

    private var questions: List<QuestionEntity> = emptyList()
    private var index = 0
    private var currentOrder: List<Int> = emptyList()
    private var shownAt = 0L
    private var busy = false

    private lateinit var examLabel: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var image: ImageView
    private lateinit var stem: TextView
    private lateinit var group: RadioGroup
    private lateinit var options: List<RadioButton>
    private lateinit var nextBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_challenge)
        examLabel = findViewById(R.id.dcExamLabel)
        progressText = findViewById(R.id.dcProgressText)
        progressBar = findViewById(R.id.dcProgressBar)
        image = findViewById(R.id.dcQuestionImage)
        stem = findViewById(R.id.dcQuestionText)
        group = findViewById(R.id.dcOptionsGroup)
        options = listOf(
            findViewById(R.id.dcOptA), findViewById(R.id.dcOptB), findViewById(R.id.dcOptC),
            findViewById(R.id.dcOptD), findViewById(R.id.dcOptE),
        )
        nextBtn = findViewById(R.id.dcNextBtn)

        userId = DailyChallengeUser.resolve(this)
        exam = StudyAreaManager.getActiveArea(this).career.examType
        localDate = controller.todayLocalDate()

        group.setOnCheckedChangeListener { _, _ ->
            nextBtn.isEnabled = group.checkedRadioButtonId != -1
        }
        nextBtn.onTap { onNext() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val result = try {
                controller.loadToday(userId, exam)
            } catch (t: Throwable) {
                null
            }
            if (result == null) {
                Toast.makeText(this@DailyChallengeActivity, R.string.dc_empty_today, Toast.LENGTH_LONG).show()
                finish(); return@launch
            }
            questions = result.questions
            if (result.completed || questions.isEmpty()) {
                openResult(); return@launch
            }
            index = DailyChallengeHomePresenter.resumeIndex(result.answered, result.total, result.completed)
            if (index >= questions.size) { openResult(); return@launch }
            examLabel.text = getString(R.string.dc_title)
            render()
        }
    }

    private fun render() {
        val q = questions[index]
        progressText.text = DailyChallengeHomePresenter.progressText(index + 1, questions.size)
        progressBar.max = questions.size
        progressBar.progress = index + 1

        if (!q.imageAsset.isNullOrBlank()) {
            try {
                assets.open(q.imageAsset!!.trim()).use { image.setImageBitmap(BitmapFactory.decodeStream(it)) }
                image.visibility = View.VISIBLE
                image.contentDescription = getString(R.string.dc_cd_question_figure)
            } catch (_: Throwable) {
                image.visibility = View.GONE
            }
        } else {
            image.visibility = View.GONE
        }

        stem.text = q.questionText
        val choices = parseChoices(q.optionsJson)
        currentOrder = DailyChallengeOptions.displayOrder(q.id, choices.size)
        group.setOnCheckedChangeListener(null)
        group.clearCheck()
        options.forEachIndexed { pos, btn ->
            if (pos < currentOrder.size) {
                val letter = ('A' + pos)
                btn.text = getString(R.string.dc_option_fmt, letter, choices[currentOrder[pos]])
                btn.visibility = View.VISIBLE
                btn.isEnabled = true
            } else {
                btn.visibility = View.GONE
            }
        }
        group.setOnCheckedChangeListener { _, _ -> nextBtn.isEnabled = group.checkedRadioButtonId != -1 }
        nextBtn.isEnabled = false
        nextBtn.setText(if (index == questions.size - 1) R.string.dc_finish else R.string.dc_next)
        shownAt = SystemClock.elapsedRealtime()
    }

    private fun onNext() {
        if (busy) return
        val checkedPos = options.indexOfFirst { it.visibility == View.VISIBLE && it.isChecked }
        if (checkedPos < 0 || checkedPos >= currentOrder.size) return
        val q = questions[index]
        val originalIndex = currentOrder[checkedPos]
        val isCorrect = originalIndex == q.answerIndex
        val timeMs = SystemClock.elapsedRealtime() - shownAt
        busy = true
        nextBtn.isEnabled = false
        lifecycleScope.launch {
            try {
                controller.submit(userId, exam, localDate, q.id, originalIndex, isCorrect, timeMs)
            } catch (_: Throwable) {
                Toast.makeText(this@DailyChallengeActivity, R.string.dc_error_generic, Toast.LENGTH_SHORT).show()
                busy = false; nextBtn.isEnabled = true
                return@launch
            }
            busy = false
            index += 1
            if (index >= questions.size) openResult() else render()
        }
    }

    private fun openResult() {
        startActivity(DailyChallengeResultActivity.intent(this, localDate))
        finish()
    }

    private fun parseChoices(optionsJson: String?): List<String> {
        if (optionsJson.isNullOrBlank()) return listOf("A", "B", "C", "D")
        return try {
            val arr = JSONArray(optionsJson)
            (0 until arr.length()).map { arr.optString(it) }
        } catch (_: Throwable) {
            listOf("A", "B", "C", "D")
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, DailyChallengeActivity::class.java)
    }
}
