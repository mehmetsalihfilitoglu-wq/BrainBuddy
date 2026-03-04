package com.brainbuddy.app.db

import android.content.Context
import android.util.Log
import com.brainbuddy.app.quiz.QuestionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset

/**
 * Seeds Room DB from JSON on first install.
 * Idempotent: app_meta["db_seeded"] == "true" skips.
 */
object DbSeeder {
    private const val TAG = "DbSeeder"
    private const val KEY_DB_SEEDED = "db_seeded"

    suspend fun seedIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(context)
        val meta = db.appMetaDao()
        if (meta.get(KEY_DB_SEEDED) == "true") {
            Log.d(TAG, "Already seeded, skip")
            return@withContext false
        }
        val questions = mutableListOf<QuestionEntity>()
        try {
            questions.addAll(loadFromAssets(context))
            val imported = loadFromImported(context)
            val existingIds = questions.map { it.id }.toSet()
            imported.filter { it.id !in existingIds }.forEach { questions.add(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Seed load error", e)
        }
        if (questions.isEmpty()) {
            questions.addAll(getFallbackEntities())
        }
        db.questionDao().insertAll(questions)
        meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
        Log.i(TAG, "Seeded ${questions.size} questions")
        true
    }

    private fun loadFromAssets(context: Context): List<QuestionEntity> {
        return try {
            val json = context.assets.open("questions_tr.json").use { input ->
                input.readBytes().toString(Charset.forName("UTF-8"))
            }
            parseJsonArray(JSONArray(json))
        } catch (e: Exception) {
            Log.e(TAG, "questions_tr.json error", e)
            emptyList()
        }
    }

    private fun loadFromImported(context: Context): List<QuestionEntity> {
        val file = java.io.File(context.filesDir, "imported_questions.json")
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText(Charsets.UTF_8)
            parseJsonArray(JSONArray(json))
        } catch (e: Exception) {
            Log.e(TAG, "imported load error", e)
            emptyList()
        }
    }

    private fun parseJsonArray(arr: JSONArray): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val choicesArr = o.optJSONArray("choices") ?: throw IllegalArgumentException("Missing choices")
                val raw = (0 until choicesArr.length()).map { idx ->
                    choicesArr.optString(idx, "").ifEmpty { choicesArr.opt(idx)?.toString() ?: "-" }
                }.filter { it.isNotBlank() }
                val choices = if (raw.size >= 4) raw.take(4) else raw + List(4 - raw.size) { "-" }
                val stem = o.optString("stem", "?")
                val correctIdx = o.optInt("correctIndex", 0).coerceIn(0, choices.size - 1)
                val correctAnswer = choices.getOrNull(correctIdx) ?: ""
                val subjStr = o.optString("subject", "MAT").let { if (it == "INGILIZCE") "ING" else it }
                val subject = subjStr.lowercase().let {
                    when (it) {
                        "mat" -> "math"
                        "turkce" -> "tr"
                        "ing" -> "en"
                        "fen" -> "fen"
                        "sosyal" -> "sosyal"
                        else -> it
                    }
                }
                val gradeTag = o.optString("gradeTag", "").takeIf { it.isNotEmpty() }
                val grade = o.optInt("grade", 0).let { g ->
                    if (g in 2..8) g else gradeTag?.toIntOrNull()?.coerceIn(2, 8) ?: 6
                }
                val subjShort = when (subject) {
                    "math" -> "MAT"
                    "tr" -> "TURKCE"
                    "en" -> "ING"
                    "fen" -> "FEN"
                    "sosyal" -> "SOSYAL"
                    else -> "MAT"
                }
                val rawId = o.optString("id", "")
                val id = if (rawId.isNotBlank()) rawId else "${grade}_${subjShort}_${(i + 1).toString().padStart(6, '0')}"
                val diffStr = o.optString("difficulty", "MEDIUM")
                val difficulty = when (diffStr) {
                    "EASY" -> 0
                    "HARD" -> 2
                    "VERY_HARD" -> 3
                    else -> 1
                }
                val optionsJson = org.json.JSONArray(choices).toString()
                val topic = o.optString("topic", "").takeIf { it.isNotEmpty() }
                val tagsJson = topic?.let { org.json.JSONArray(listOf(it)).toString() }
                val levelGroup = o.optString("levelGroup", "GRADE_5_8")
                val hint = o.optString("hint", "").takeIf { it.isNotEmpty() }
                val imageAsset = o.optString("imageAsset", "").takeIf { it.isNotEmpty() }
                val examType = o.optString("examType", "GENERAL")
                val now = System.currentTimeMillis()
                out.add(
                    QuestionEntity(
                        id = id,
                        subject = subject,
                        difficulty = difficulty,
                        grade = grade,
                        text = stem,
                        optionsJson = optionsJson,
                        correctIndex = correctIdx,
                        tagsJson = tagsJson,
                        isActive = true,
                        version = 1,
                        updatedAt = now,
                        levelGroup = levelGroup,
                        gradeTag = gradeTag,
                        hint = hint,
                        imageAsset = imageAsset,
                        examType = examType
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Parse failed index $i: ${e.message}")
            }
        }
        return out
    }

    private fun getFallbackEntities(): List<QuestionEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            QuestionEntity("fb1", "math", 0, 6, "12 × 15 işleminin sonucu kaçtır?", "[\"160\",\"170\",\"180\",\"190\"]", 2, null, true, 1, now, "GRADE_5_8", "6", "12×10=120, 12×5=60", null, "GENERAL"),
            QuestionEntity("fb2", "tr", 0, 6, "Türkiye'nin başkenti neresidir?", "[\"İstanbul\",\"İzmir\",\"Ankara\",\"Bursa\"]", 2, null, true, 1, now, "GRADE_5_8", "6", "Mustafa Kemal Atatürk'ün kararıyla.", null, "GENERAL"),
            QuestionEntity("fb3", "fen", 0, 6, "Güneş sisteminde Dünya'dan sonra gelen gezegen hangisidir?", "[\"Venüs\",\"Mars\",\"Jüpiter\",\"Satürn\"]", 1, null, true, 1, now, "GRADE_5_8", "6", "Merkür, Venüs, Dünya, Mars...", null, "GENERAL"),
            QuestionEntity("fb4", "en", 0, 6, "\"Hello\" kelimesinin Türkçe karşılığı nedir?", "[\"Hoşça kal\",\"Merhaba\",\"Teşekkürler\",\"Evet\"]", 1, null, true, 1, now, "GRADE_5_8", "6", "Selamlama sözcüğü.", null, "GENERAL")
        )
    }
}
