package com.brainbuddy.app.quiz

import android.content.Context
import org.json.JSONArray
import java.io.FileNotFoundException
import java.nio.charset.Charset

class QuestionRepository(private val context: Context) {

    /**
     * assets/questions_tr.json okur.
     * Dosya yoksa veya okunamazsa boş liste döner.
     */
    fun loadAllQuestions(): List<Question> {
        val json = try {
            context.assets.open("questions_tr.json").use { input ->
                input.readBytes().toString(Charset.forName("UTF-8"))
            }
        } catch (e: FileNotFoundException) {
            return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        val arr = JSONArray(json)
        val out = ArrayList<Question>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)

            val choicesArr = o.getJSONArray("choices")
            val choices = (0 until choicesArr.length()).map { idx -> choicesArr.getString(idx) }

            val q = Question(
                id = o.getString("id"),
                levelGroup = LevelGroup.valueOf(o.getString("levelGroup")),
                subject = Subject.valueOf(o.getString("subject")),
                gradeTag = o.optString("gradeTag", ""),
                stem = o.getString("stem"),
                choices = choices,
                correctIndex = o.getInt("correctIndex"),
                hint = o.optString("hint", null),
                imageAsset = o.optString("imageAsset", null)
            )
            out.add(q)
        }
        return out
    }

    fun pickQuizQuestions(levelGroup: LevelGroup, count: Int = 20): List<Question> {
        val all = loadAllQuestions().filter { it.levelGroup == levelGroup }
        if (all.isEmpty()) return emptyList()
        return all.shuffled().take(count.coerceAtMost(all.size))
    }
}