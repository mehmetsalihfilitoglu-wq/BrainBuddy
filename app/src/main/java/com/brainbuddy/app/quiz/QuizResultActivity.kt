package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.HomeActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.QuizSession
import org.json.JSONArray
import org.json.JSONObject

class QuizResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ANSWERS_JSON = "extra_answers_json"
        const val EXTRA_LEVEL = "extra_level"
        const val EXTRA_RETRY_WRONG = "extra_retry_wrong"

        fun encodeAnswers(list: List<AnswerRecord>): String {
            val arr = JSONArray()
            list.forEach {
                val o = JSONObject()
                o.put("questionId", it.questionId)
                o.put("selectedIndex", it.selectedIndex)
                o.put("correctIndex", it.correctIndex)
                arr.put(o)
            }
            return arr.toString()
        }

        fun decodeAnswers(json: String?): List<AnswerRecord> {
            if (json.isNullOrBlank()) return emptyList()
            val arr = JSONArray(json)
            val out = ArrayList<AnswerRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    AnswerRecord(
                        o.getString("questionId"),
                        o.getInt("selectedIndex"),
                        o.getInt("correctIndex")
                    )
                )
            }
            return out
        }
    }

    private lateinit var answers: List<AnswerRecord>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_result)

        answers = decodeAnswers(intent.getStringExtra(EXTRA_ANSWERS_JSON))
        val correct = answers.count { it.isCorrect }
        val total = answers.size

        // Points: 10 per correct
        val pointsEarned = correct * 10
        val gam = GamificationStore(this)
        val analytics = AnalyticsStore(this)

        gam.addPoints(pointsEarned)
        gam.recordQuizCompletion(System.currentTimeMillis())

        analytics.recordSession(
            QuizSession(
                System.currentTimeMillis(),
                correct,
                total,
                pointsEarned
            )
        )

        // UI
        findViewById<android.widget.TextView>(R.id.tvScoreBig).text = "$correct/$total"
        findViewById<android.widget.TextView>(R.id.tvScoreLabel).text = "Doğru cevap"
        findViewById<android.widget.TextView>(R.id.tvPoints).text = "+$pointsEarned puan"
        findViewById<android.widget.TextView>(R.id.tvStreak).text = "🔥 ${gam.streakDays()} gün seri"

        val progress = findViewById<android.widget.ProgressBar>(R.id.progressCircle)
        progress.max = 100
        progress.progress = if (total > 0) (100 * correct / total) else 0

        val wrongAnswers = answers.filter { !it.isCorrect }
        val wrongSection = findViewById<android.view.View>(R.id.wrongSection)
        val btnRetryWrong = findViewById<android.widget.Button>(R.id.btnRetryWrong)

        if (wrongAnswers.isEmpty()) {
            wrongSection.visibility = android.view.View.GONE
            btnRetryWrong.visibility = android.view.View.GONE
        } else {
            wrongSection.visibility = android.view.View.VISIBLE
            btnRetryWrong.visibility = android.view.View.VISIBLE
            showWrongAnswers(wrongAnswers)
        }

        btnRetryWrong.setOnClickListener {
            val wrongIds = wrongAnswers.map { it.questionId }
            startActivity(Intent(this, QuizActivity::class.java).apply {
                putExtra(QuizActivity.EXTRA_RETRY_WRONG, true)
                putStringArrayListExtra(QuizActivity.EXTRA_WRONG_IDS, ArrayList(wrongIds))
            })
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnPlayAgain).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java))
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finishAffinity()
        }
    }

    private fun showWrongAnswers(wrong: List<AnswerRecord>) {
        val repo = QuestionRepository(this)
        val allQuestions = repo.loadAllQuestions().associateBy { it.id }

        val recycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerWrong)
        val items = wrong.mapNotNull { a ->
            val q = allQuestions[a.questionId] ?: return@mapNotNull null
            WrongItem(q.stem, q.choices.getOrNull(a.correctIndex) ?: "?")
        }
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recycler.adapter = WrongAnswersAdapter(items)
    }

    data class WrongItem(val question: String, val correctAnswer: String)

    class WrongAnswersAdapter(private val items: List<WrongItem>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<WrongAnswersAdapter.VH>() {
        class VH(val view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_wrong_answer, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.view.findViewById<android.widget.TextView>(R.id.tvQuestion).text = item.question
            holder.view.findViewById<android.widget.TextView>(R.id.tvCorrect).text = "✓ ${item.correctAnswer}"
        }

        override fun getItemCount() = items.size
    }
}
