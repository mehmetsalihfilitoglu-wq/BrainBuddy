package com.brainbuddy.app.quiz

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brainbuddy.app.R
import com.brainbuddy.app.databinding.ActivityQuizBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Standalone mode: solve only questions in [WrongQuestionPoolStore] until the pool is empty or the user leaves.
 * Not gated; not normal test generation; no fixed test size.
 *
 * [getQuestionById] only returns **active** questions (DB `isActive = 1`); missing/inactive IDs are dropped from pool+queue.
 */
class WrongPoolActivity : AppCompatActivity() {

    private lateinit var b: ActivityQuizBinding
    private lateinit var repo: QuestionRepository
    private lateinit var poolStore: WrongQuestionPoolStore

    private val queue = mutableListOf<String>()
    private var currentQuestion: Question? = null
    private val answers = mutableMapOf<String, Int>()
    private var isFinishingAnswer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)

        poolStore = WrongQuestionPoolStore(this)
        repo = QuestionRepository(this)

        if (poolStore.isEmpty()) {
            Toast.makeText(this, R.string.wrong_pool_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (savedInstanceState != null) {
            queue.clear()
            savedInstanceState.getStringArrayList(STATE_QUEUE)?.let { queue.addAll(it) }
        }
        if (queue.isEmpty()) {
            queue.addAll(poolStore.getIds().shuffled())
        }
        pruneQueueToPool()

        if (queue.isEmpty()) {
            Toast.makeText(this, R.string.wrong_pool_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        b.nextBtn.setOnClickListener { onSubmitAnswer() }
        showCurrentQuestion()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_QUEUE, ArrayList(queue))
    }

    private fun pruneQueueToPool() {
        val pool = poolStore.getIds()
        queue.retainAll { it in pool }
        val missing = pool - queue.toSet()
        queue.addAll(missing.shuffled())
    }

    /**
     * Bounded iteration instead of recursion: stale / inactive / missing question IDs are stripped without stack overflow.
     */
    private fun showCurrentQuestion() {
        val maxPasses = (queue.size + poolStore.size()).coerceAtLeast(1) * 4 + 48
        var passes = 0
        while (passes < maxPasses) {
            passes++
            pruneQueueToPool()
            if (queue.isEmpty() || poolStore.isEmpty()) {
                Toast.makeText(this, R.string.wrong_pool_all_done, Toast.LENGTH_LONG).show()
                finish()
                return
            }

            val qid = queue[0]
            val q = repo.getQuestionById(qid)
            if (q == null) {
                poolStore.remove(qid)
                queue.remove(qid)
                continue
            }

            answers.clear()
            currentQuestion = q
            b.subjectChip.text = getString(R.string.wrong_pool_mode_chip)
            b.progressText.text = getString(R.string.wrong_pool_remaining, poolStore.size())
            b.questionText.text = q.stem

            if (!q.imageAsset.isNullOrBlank()) {
                val path = q.imageAsset!!.trim()
                try {
                    assets.open(path).use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            b.questionImage.setImageBitmap(bitmap)
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

            b.optA.text = q.choices.getOrNull(0) ?: "-"
            b.optB.text = q.choices.getOrNull(1) ?: "-"
            b.optC.text = q.choices.getOrNull(2) ?: "-"
            b.optD.text = q.choices.getOrNull(3) ?: "-"

            b.optionsGroup.clearCheck()
            b.optionsGroup.setOnCheckedChangeListener { _, checkedId ->
                val cq = currentQuestion ?: return@setOnCheckedChangeListener
                val sel = when (checkedId) {
                    b.optA.id -> 0
                    b.optB.id -> 1
                    b.optC.id -> 2
                    b.optD.id -> 3
                    else -> -1
                }
                if (sel >= 0) answers[cq.id] = sel
            }

            b.feedbackText.visibility = View.GONE
            b.nextBtn.isEnabled = true
            b.nextBtn.text = getString(R.string.wrong_pool_submit)
            b.optionsGroup.visibility = View.VISIBLE
            b.optA.visibility = View.VISIBLE
            b.optB.visibility = View.VISIBLE
            b.optC.visibility = View.VISIBLE
            b.optD.visibility = View.VISIBLE
            return
        }

        Toast.makeText(this, R.string.wrong_pool_empty, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun onSubmitAnswer() {
        if (isFinishingAnswer) return
        val q = currentQuestion ?: return
        val sel = answers[q.id] ?: when (b.optionsGroup.checkedRadioButtonId) {
            b.optA.id -> 0
            b.optB.id -> 1
            b.optC.id -> 2
            b.optD.id -> 3
            else -> -1
        }
        if (sel < 0) {
            Toast.makeText(this, R.string.wrong_pool_pick_option, Toast.LENGTH_SHORT).show()
            return
        }
        isFinishingAnswer = true
        val record = AnswerRecord(q.id, sel, q.correctIndex)
        val correct = record.isCorrect

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.recordAnswers(
                        listOf(record),
                        mapOf(q.id to q),
                        testId = "wrong_pool_${System.currentTimeMillis()}"
                    )
                }
            } finally {
                isFinishingAnswer = false
            }

            if (correct) {
                queue.removeAll { it == q.id }
            } else {
                if (queue.isNotEmpty() && queue[0] == q.id) {
                    val head = queue.removeAt(0)
                    queue.add(head)
                }
            }

            pruneQueueToPool()

            if (poolStore.isEmpty()) {
                Toast.makeText(this@WrongPoolActivity, R.string.wrong_pool_all_done, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }

            if (queue.isEmpty()) {
                queue.addAll(poolStore.getIds().shuffled())
            }

            showCurrentQuestion()
        }
    }

    companion object {
        private const val STATE_QUEUE = "wrong_pool_queue"
    }
}
