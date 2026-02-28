package com.brainbuddy.app.quiz

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class QuestionRepository(private val context: Context) {

    private val historyStore = QuestionHistoryStore(context)

    fun loadAllQuestions(): List<Question> {
        return try {
            val json = context.assets.open("questions_tr.json").use { input ->
                input.readBytes().toString(Charset.forName("UTF-8"))
            }
            val arr = JSONArray(json)
            val out = ArrayList<Question>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(parseQuestion(o))
            }
            out
        } catch (e: Exception) {
            showFallbackToast()
            getFallbackQuestions()
        }
    }

    private fun showFallbackToast() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context.applicationContext,
                "Soru dosyası bulunamadı, varsayılan sorular yüklendi.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** In-code fallback so quiz never crashes when asset is missing. */
    private fun getFallbackQuestions(): List<Question> = listOf(
        Question(
            id = "fb1",
            levelGroup = LevelGroup.GRADE_5_8,
            subject = Subject.MAT,
            gradeTag = "6. sınıf",
            stem = "12 × 15 işleminin sonucu kaçtır?",
            choices = listOf("160", "170", "180", "190"),
            correctIndex = 2,
            hint = "12×10=120, 12×5=60",
            imageAsset = null,
            difficulty = QuizDifficulty.EASY
        ),
        Question(
            id = "fb2",
            levelGroup = LevelGroup.GRADE_5_8,
            subject = Subject.TURKCE,
            gradeTag = "6. sınıf",
            stem = "Türkiye'nin başkenti neresidir?",
            choices = listOf("İstanbul", "İzmir", "Ankara", "Bursa"),
            correctIndex = 2,
            hint = "Mustafa Kemal Atatürk'ün kararıyla.",
            imageAsset = null,
            difficulty = QuizDifficulty.EASY
        ),
        Question(
            id = "fb3",
            levelGroup = LevelGroup.GRADE_5_8,
            subject = Subject.FEN,
            gradeTag = "6. sınıf",
            stem = "Güneş sisteminde Dünya'dan sonra gelen gezegen hangisidir?",
            choices = listOf("Venüs", "Mars", "Jüpiter", "Satürn"),
            correctIndex = 1,
            hint = "Merkür, Venüs, Dünya, Mars...",
            imageAsset = null,
            difficulty = QuizDifficulty.EASY
        ),
        Question(
            id = "fb4",
            levelGroup = LevelGroup.GRADE_5_8,
            subject = Subject.ING,
            gradeTag = "6. sınıf",
            stem = "\"Hello\" kelimesinin Türkçe karşılığı nedir?",
            choices = listOf("Hoşça kal", "Merhaba", "Teşekkürler", "Evet"),
            correctIndex = 1,
            hint = "Selamlama sözcüğü.",
            imageAsset = null,
            difficulty = QuizDifficulty.EASY
        ),
        Question(
            id = "fb5",
            levelGroup = LevelGroup.GRADE_5_8,
            subject = Subject.SOSYAL,
            gradeTag = "7. sınıf",
            stem = "Türkiye Cumhuriyeti hangi yıl kurulmuştur?",
            choices = listOf("1920", "1922", "1923", "1924"),
            correctIndex = 2,
            hint = "Lozan Antlaşması sonrası.",
            imageAsset = null,
            difficulty = QuizDifficulty.EASY
        )
    )

    private fun parseQuestion(o: JSONObject): Question {
        val choicesArr = o.getJSONArray("choices")
        val choices = (0 until choicesArr.length()).map { idx -> choicesArr.getString(idx) }
        val diffStr = o.optString("difficulty", "MEDIUM")
        val difficulty = try {
            QuizDifficulty.valueOf(diffStr)
        } catch (_: Exception) {
            QuizDifficulty.MEDIUM
        }
        return Question(
            id = o.getString("id"),
            levelGroup = LevelGroup.valueOf(o.getString("levelGroup")),
            subject = Subject.valueOf(o.getString("subject")),
            gradeTag = o.optString("gradeTag", ""),
            stem = o.getString("stem"),
            choices = choices,
            correctIndex = o.getInt("correctIndex"),
            hint = o.optString("hint", "").takeIf { it.isNotEmpty() },
            imageAsset = o.optString("imageAsset", "").takeIf { it.isNotEmpty() },
            difficulty = difficulty
        )
    }

    fun recordAnswers(answers: List<AnswerRecord>) {
        answers.forEach { a ->
            historyStore.recordAnswer(a.questionId, a.isCorrect)
        }
    }

    /**
     * Smart selection for quiz session:
     * 1) Prioritize wrong (within 7 days)
     * 2) Avoid recent correct (cooldown ~2 days)
     * 3) Variety: least recently seen
     * 4) No duplicates in session
     */
    fun pickQuizQuestions(
        levelGroup: LevelGroup,
        count: Int,
        difficulty: QuizDifficulty,
        categories: Set<String> = emptySet()
    ): List<Question> {
        var pool = loadAllQuestions().filter { it.levelGroup == levelGroup && it.difficulty == difficulty }
        if (categories.isNotEmpty()) {
            pool = pool.filter { it.subject.name in categories }
        }
        if (pool.isEmpty()) {
            pool = loadAllQuestions().filter { it.levelGroup == levelGroup }
        }
        if (pool.isEmpty()) return emptyList()

        val wrongIds = historyStore.getWrongQuestionIds(7)
        val now = System.currentTimeMillis()
        val cooldownMs = TimeUnit.DAYS.toMillis(2)

        val wrongPool = pool.filter { it.id in wrongIds }
        val cooldownExcluded = pool.filter { q ->
            val h = historyStore.getHistory(q.id) ?: return@filter true
            if (h.lastResult != "correct") return@filter true
            (now - h.lastSeenAt) < cooldownMs
        }
        val available = pool.filter { it !in cooldownExcluded }

        val selected = mutableSetOf<String>()
        val result = ArrayList<Question>()

        // 1) Add wrong questions first (up to count)
        wrongPool.shuffled().forEach { q ->
            if (result.size >= count) return@forEach
            if (q.id !in selected) {
                result.add(q)
                selected.add(q.id)
            }
        }

        // 2) Fill remaining with least-recently-seen
        val rest = available.filter { it.id !in selected }
            .sortedBy { historyStore.getHistory(it.id)?.lastSeenAt ?: 0L }
        rest.forEach { q ->
            if (result.size >= count) return@forEach
            result.add(q)
            selected.add(q.id)
        }

        return result.shuffled()
    }

    fun pickRetryWrongQuestions(levelGroup: LevelGroup): List<Question> {
        val wrongIds = historyStore.getAllWrongIds()
        if (wrongIds.isEmpty()) return emptyList()
        val all = loadAllQuestions().filter { it.levelGroup == levelGroup }.associateBy { it.id }
        return wrongIds.mapNotNull { all[it] }
    }

    fun getLevelGroupFromPrefs(): LevelGroup {
        return when (ProtectionPrefs(context).studentLevel()) {
            com.brainbuddy.app.core.StudentLevel.AGE_3_5 -> LevelGroup.AGE_3_5
            com.brainbuddy.app.core.StudentLevel.GRADES_1_4 -> LevelGroup.GRADE_1_4
            com.brainbuddy.app.core.StudentLevel.GRADES_5_8 -> LevelGroup.GRADE_5_8
            com.brainbuddy.app.core.StudentLevel.GRADES_9_12 -> LevelGroup.GRADE_9_12
        }
    }
}
