package com.brainbuddy.app.quiz

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import org.json.JSONArray
import org.json.JSONObject

class QuizResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ANSWERS_JSON = "extra_answers_json"
        const val EXTRA_LEVEL = "extra_level"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_result)

        val tvLevel = findViewById<TextView>(R.id.tvLevel)
        val tvScore = findViewById<TextView>(R.id.tvScore)

        val level = intent.getStringExtra(EXTRA_LEVEL) ?: "-"
        val answers = decodeAnswers(intent.getStringExtra(EXTRA_ANSWERS_JSON))

        val correct = answers.count { it.selectedIndex == it.correctIndex }

        tvLevel.text = "Seviye: $level"
        tvScore.text = "Skor: $correct / ${answers.size}"
    }
}