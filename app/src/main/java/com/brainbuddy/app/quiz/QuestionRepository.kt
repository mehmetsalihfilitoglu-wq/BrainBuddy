package com.brainbuddy.app.quiz

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class QuestionRepository(private val context: Context) {

    companion object {
        private const val TAG = "QuestionRepository"
    }

    private val historyStore = QuestionHistoryStore(context)

    /** @return Pair(questions, parseStats) - stats used for debug toast */
    fun loadAllQuestions(): List<Question> = loadAllQuestionsWithStats().first

    fun loadAllQuestionsWithStats(): Pair<List<Question>, LoadStats> {
        return try {
            val json = context.assets.open("questions_tr.json").use { input ->
                input.readBytes().toString(Charset.forName("UTF-8"))
            }
            val arr = JSONArray(json)
            val out = ArrayList<Question>(arr.length())
            var parseFailCount = 0
            for (i in 0 until arr.length()) {
                try {
                    val o = arr.getJSONObject(i)
                    out.add(parseQuestion(o))
                } catch (e: Exception) {
                    parseFailCount++
                    Log.w(TAG, "Parse failed for question index $i: ${e.message}", e)
                }
            }
            val stats = LoadStats(
                fileFound = true,
                totalInJson = arr.length(),
                parsedTotal = out.size,
                parseFailed = parseFailCount
            )
            Log.i(TAG, "questions_tr.json: found=true, totalInJson=${arr.length()}, parsed=$out.size, failed=$parseFailCount")
            if (out.isEmpty()) {
                showFallbackToast()
                Pair(getFallbackQuestions(), stats)
            } else {
                Pair(out, stats)
            }
        } catch (e: Exception) {
            Log.e(TAG, "questions_tr.json: found=false, error=${e.message}", e)
            val stats = LoadStats(fileFound = false, totalInJson = 0, parsedTotal = 0, parseFailed = 0)
            showFallbackToast()
            Pair(getFallbackQuestions(), stats)
        }
    }

    data class LoadStats(
        val fileFound: Boolean,
        val totalInJson: Int,
        val parsedTotal: Int,
        val parseFailed: Int
    )

    private fun showFallbackToast() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context.applicationContext,
                "Soru dosyası bulunamadı, varsayılan sorular yüklendi.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** In-code fallback so quiz never crashes when asset is missing or pool is empty. */
    private fun getFallbackQuestions(): List<Question> = listOf(
        Question(id = "fb1", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", stem = "12 × 15 işleminin sonucu kaçtır?", choices = listOf("160", "170", "180", "190"), correctIndex = 2, hint = "12×10=120, 12×5=60", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb2", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", stem = "Türkiye'nin başkenti neresidir?", choices = listOf("İstanbul", "İzmir", "Ankara", "Bursa"), correctIndex = 2, hint = "Mustafa Kemal Atatürk'ün kararıyla.", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb3", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", stem = "Güneş sisteminde Dünya'dan sonra gelen gezegen hangisidir?", choices = listOf("Venüs", "Mars", "Jüpiter", "Satürn"), correctIndex = 1, hint = "Merkür, Venüs, Dünya, Mars...", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb4", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.ING, gradeTag = "6", stem = "\"Hello\" kelimesinin Türkçe karşılığı nedir?", choices = listOf("Hoşça kal", "Merhaba", "Teşekkürler", "Evet"), correctIndex = 1, hint = "Selamlama sözcüğü.", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb5", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.SOSYAL, gradeTag = "7", stem = "Türkiye Cumhuriyeti hangi yıl kurulmuştur?", choices = listOf("1920", "1922", "1923", "1924"), correctIndex = 2, hint = "Lozan Antlaşması sonrası.", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb6", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", stem = "2x + 5 = 15 denkleminde x kaçtır?", choices = listOf("3", "4", "5", "6"), correctIndex = 2, hint = "Önce 5'i karşı tarafa at.", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb7", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", stem = "\"Koşmak\" fiilinin geniş zaman 1. tekil şahıs çekimi hangisidir?", choices = listOf("koşarım", "koşuyorum", "koşar", "koşarsın"), correctIndex = 0, hint = "Geniş zaman -ar/-er eki alır.", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb8", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", stem = "Fotosentez olayında hangi gaz üretilir?", choices = listOf("Karbondioksit", "Azot", "Oksijen", "Hidrojen"), correctIndex = 2, hint = "Bitkiler ışıkta ne üretir?", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb9", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "8", stem = "√64 işleminin sonucu kaçtır?", choices = listOf("6", "7", "8", "9"), correctIndex = 2, hint = "8×8=64", imageAsset = null, difficulty = QuizDifficulty.HARD),
        Question(id = "fb10", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.SOSYAL, gradeTag = "8", stem = "TBMM'nin açılış tarihi nedir?", choices = listOf("19 Mayıs 1919", "23 Nisan 1920", "30 Ağustos 1922", "29 Ekim 1923"), correctIndex = 1, hint = "Ulusal Egemenlik ve Çocuk Bayramı.", imageAsset = null, difficulty = QuizDifficulty.HARD)
    )

    private fun parseQuestion(o: JSONObject): Question {
        val choicesArr = o.optJSONArray("choices")
            ?: throw IllegalArgumentException("Missing 'choices' array")
        val raw = (0 until choicesArr.length()).map { idx ->
            choicesArr.optString(idx, "").ifEmpty { choicesArr.opt(idx)?.toString() ?: "-" }
        }.filter { it.isNotBlank() }
        if (raw.isEmpty()) throw IllegalArgumentException("Choices array empty")
        val choices = if (raw.size >= 4) raw.take(4) else raw + List(4 - raw.size) { "-" }
        val diffStr = o.optString("difficulty", "MEDIUM")
        val difficulty = try {
            QuizDifficulty.valueOf(diffStr)
        } catch (_: Exception) {
            QuizDifficulty.MEDIUM
        }
        val levelStr = o.optString("levelGroup", "GRADE_5_8")
        val levelGroup = try {
            LevelGroup.valueOf(levelStr)
        } catch (_: Exception) {
            LevelGroup.GRADE_5_8
        }
        val subjStr = o.optString("subject", "MAT").let { s ->
            if (s == "INGILIZCE") "ING" else s
        }
        val subject = try {
            Subject.valueOf(subjStr)
        } catch (_: Exception) {
            Subject.MAT
        }
        return Question(
            id = o.optString("id", "q_${System.currentTimeMillis()}"),
            levelGroup = levelGroup,
            subject = subject,
            gradeTag = o.optString("gradeTag", ""),
            stem = o.optString("stem", "?"),
            choices = choices.ifEmpty { listOf("A", "B", "C", "D") },
            correctIndex = o.optInt("correctIndex", 0).coerceIn(0, 3),
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
     * 1) Build pool with progressive filter relaxation (category → difficulty → levelGroup)
     * 2) Prioritize wrong (within 7 days)
     * 3) Avoid recent correct (cooldown ~2 days)
     * 4) Variety: least recently seen
     * 5) Never return empty: fallback to built-in questions if needed
     */
    fun pickQuizQuestions(
        levelGroup: LevelGroup,
        count: Int,
        difficulty: QuizDifficulty,
        categories: Set<String> = emptySet()
    ): List<Question> {
        val (all, loadStats) = loadAllQuestionsWithStats()
        val (pool, filterStats) = buildPoolWithFallback(all, levelGroup, difficulty, categories)
        val finalPool = if (pool.isEmpty()) {
            Log.w(TAG, "Pool empty after filters, using fallback questions")
            getFallbackQuestions()
        } else pool

        showDebugToast(loadStats, filterStats, finalPool.size, count)

        val wrongIds = historyStore.getWrongQuestionIds(7)
        val now = System.currentTimeMillis()
        val cooldownMs = TimeUnit.DAYS.toMillis(2)

        val wrongPool = finalPool.filter { it.id in wrongIds }
        val cooldownExcluded = finalPool.filter { q ->
            val h = historyStore.getHistory(q.id) ?: return@filter true
            if (h.lastResult != "correct") return@filter true
            (now - h.lastSeenAt) < cooldownMs
        }
        val available = finalPool.filter { it !in cooldownExcluded }

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

        // 3) If still empty (e.g. all in cooldown, no wrong), ignore cooldown and use pool
        if (result.isEmpty() && finalPool.isNotEmpty()) {
            Log.i(TAG, "Result empty after selection, using pool (ignoring cooldown)")
            return finalPool.shuffled().take(count)
        }

        return result.shuffled()
    }

    private fun showDebugToast(load: LoadStats, filter: FilterStats, poolSize: Int, count: Int) {
        val msg = buildString {
            append("Quiz Debug: ")
            append("JSON=${if (load.fileFound) "OK" else "MISSING"}, ")
            append("parsed=${load.parsedTotal}/${load.totalInJson}")
            if (load.parseFailed > 0) append(" (${load.parseFailed} failed)")
            append(" | after filters: ")
            append("diff=${filter.afterDifficulty}, cat=${filter.afterCategory}, ")
            append("grade=${filter.afterGrade}, pool=$poolSize, count=$count")
        }
        Log.i(TAG, msg)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    data class FilterStats(
        val afterDifficulty: Int,
        val afterCategory: Int,
        val afterGrade: Int
    )

    /** Progressive fallback: relax category first, then difficulty, then try alternate level groups. */
    private fun buildPoolWithFallback(
        all: List<Question>,
        levelGroup: LevelGroup,
        difficulty: QuizDifficulty,
        categories: Set<String>
    ): Pair<List<Question>, FilterStats> {
        val afterDiff = all.filter { it.levelGroup == levelGroup && it.difficulty == difficulty }
        val afterCat = if (categories.isNotEmpty()) afterDiff.filter { it.subject.name in categories } else afterDiff

        // 1) Full filter: levelGroup + difficulty + category
        if (afterCat.isNotEmpty()) {
            Log.d(TAG, "Filter: afterDifficulty=${afterDiff.size}, afterCategory=${afterCat.size}, afterGrade=${afterCat.size}")
            return Pair(afterCat, FilterStats(afterDiff.size, afterCat.size, afterCat.size))
        }

        // 2) Relax category: levelGroup + difficulty only
        if (afterDiff.isNotEmpty()) {
            Log.d(TAG, "Filter (relaxed category): afterDifficulty=${afterDiff.size}")
            return Pair(afterDiff, FilterStats(afterDiff.size, 0, afterDiff.size))
        }

        // 3) Relax difficulty: levelGroup only
        val afterGrade = all.filter { it.levelGroup == levelGroup }
        if (afterGrade.isNotEmpty()) {
            Log.d(TAG, "Filter (relaxed difficulty): afterGrade=${afterGrade.size}")
            return Pair(afterGrade, FilterStats(0, 0, afterGrade.size))
        }

        // 4) For AGE_3_5 or GRADE_1_4, fallback to GRADE_5_8 questions (closest match)
        val fallbackGroups = when (levelGroup) {
            LevelGroup.AGE_3_5, LevelGroup.GRADE_1_4 -> listOf(LevelGroup.GRADE_5_8, LevelGroup.GRADE_9_12)
            LevelGroup.GRADE_9_12 -> listOf(LevelGroup.GRADE_5_8)
            LevelGroup.GRADE_5_8 -> listOf(LevelGroup.GRADE_9_12)
        }
        for (alt in fallbackGroups) {
            val pool = all.filter { it.levelGroup == alt }
            if (pool.isNotEmpty()) {
                Log.d(TAG, "Filter (fallback levelGroup=$alt): pool=${pool.size}")
                return Pair(pool, FilterStats(0, 0, pool.size))
            }
        }
        Log.w(TAG, "Filter: no questions after all fallbacks")
        return Pair(emptyList(), FilterStats(0, 0, 0))
    }

    fun pickRetryWrongQuestions(levelGroup: LevelGroup): List<Question> {
        val wrongIds = historyStore.getAllWrongIds()
        if (wrongIds.isEmpty()) return emptyList()
        val allByLevel = loadAllQuestions().groupBy { it.levelGroup }
        val allMap = (allByLevel[levelGroup] ?: emptyList()).associateBy { it.id }
        var result = wrongIds.mapNotNull { allMap[it] }
        if (result.isEmpty()) {
            val fallbackGroups = when (levelGroup) {
                LevelGroup.AGE_3_5, LevelGroup.GRADE_1_4 -> listOf(LevelGroup.GRADE_5_8, LevelGroup.GRADE_9_12)
                LevelGroup.GRADE_9_12 -> listOf(LevelGroup.GRADE_5_8)
                LevelGroup.GRADE_5_8 -> listOf(LevelGroup.GRADE_9_12)
            }
            for (alt in fallbackGroups) {
                val altMap = (allByLevel[alt] ?: emptyList()).associateBy { it.id }
                result = wrongIds.mapNotNull { altMap[it] }
                if (result.isNotEmpty()) break
            }
        }
        return result
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
