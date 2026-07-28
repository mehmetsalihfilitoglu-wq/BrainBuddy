package com.edumio.app.quiz

import org.json.JSONArray
import org.json.JSONObject

/**
 * Quiz -> Result ekranına veri taşımak için ortak contract.
 */
object QuizContracts {
    const val EXTRA_ANSWERS_JSON = "extra_answers_json"
    const val EXTRA_LEVEL = "extra_level"
}

/**
 * Result ekranında göstereceğin cevap modeli.
 * questionId: soru id'si (String/Int fark etmez, burada String tuttum)
 * selectedIndex: kullanıcının işaretlediği şık (0..n-1), boşsa -1
 * correctIndex: doğru şık (0..n-1)
 */
data class AnswerResult(
    val questionId: String,
    val selectedIndex: Int,
    val correctIndex: Int
) {
    val isCorrect: Boolean get() = selectedIndex == correctIndex
}

/**
 * List<AnswerResult> -> JSON String
 */
fun encodeAnswers(list: List<AnswerResult>): String {
    val arr = JSONArray()
    list.forEach { a ->
        val o = JSONObject()
        o.put("questionId", a.questionId)
        o.put("selectedIndex", a.selectedIndex)
        o.put("correctIndex", a.correctIndex)
        arr.put(o)
    }
    return arr.toString()
}

/**
 * JSON String -> List<AnswerResult>
 */
fun decodeAnswers(json: String?): List<AnswerResult> {
    if (json.isNullOrBlank()) return emptyList()
    val arr = JSONArray(json)
    val out = ArrayList<AnswerResult>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out.add(
            AnswerResult(
                questionId = o.optString("questionId"),
                selectedIndex = o.optInt("selectedIndex", -1),
                correctIndex = o.optInt("correctIndex", -1)
            )
        )
    }
    return out
}