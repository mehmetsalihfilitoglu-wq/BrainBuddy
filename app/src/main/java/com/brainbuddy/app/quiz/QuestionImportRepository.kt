package com.brainbuddy.app.quiz

import android.content.Context
import android.util.Log
import com.brainbuddy.app.core.GradePrefs
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset

/**
 * Import pipeline for question packs.
 *
 * Hedef havuz mantığı:
 * - Her sınıf (1–7) × her ders (Matematik, Türkçe, Fen Bilimleri, Sosyal Bilgiler, İngilizce)
 *   kombinasyonu için en az 500 soru (grade+subject bazında).
 * - Yani 1 sınıf için toplam ≈ 5 × 500 = 2.500 soru,
 *   1–7 arası tüm sınıflar için ≈ 7 × 5 × 500 = 17.500 soru (1. sınıf = Junior).
 *
 * `TARGET_QUESTIONS_PER_SUBJECT` bu minimum hedefi temsil eder; gerçek implementasyon
 * JSON tarafında her (grade, subject) kombinasyonu için en az 500 soru olacak şekilde
 * içerik üretmeyi bekler.
 *
 * Loads from assets JSON; supports multiple files for scalability.
 */
class QuestionImportRepository(private val context: Context) {

    companion object {
        private const val TAG = "QuestionImport"
        const val TARGET_QUESTIONS_PER_SUBJECT = 500
        private val ASSET_FILES = listOf("questions_tr.json")
    }

    data class ImportResult(
        val totalLoaded: Int,
        val byExamType: Map<ExamType, Int>,
        val bySubject: Map<Subject, Int>,
        val parseErrors: Int
    )

    fun loadAndIndex(): ImportResult {
        val byExam = mutableMapOf<ExamType, Int>()
        val bySubj = mutableMapOf<Subject, Int>()
        var total = 0
        var errors = 0

        for (asset in ASSET_FILES) {
            try {
                context.assets.open(asset).use { input ->
                    val json = input.readBytes().toString(Charset.forName("UTF-8"))
                    val arr = JSONArray(json)
                    for (i in 0 until arr.length()) {
                        try {
                            val o = arr.getJSONObject(i)
                            val q = parseQuestion(o, asset, i)
                            byExam[q.examType] = (byExam[q.examType] ?: 0) + 1
                            bySubj[q.subject] = (bySubj[q.subject] ?: 0) + 1
                            total++
                        } catch (e: Exception) {
                            errors++
                            Log.w(TAG, "Parse error at $asset[$i]: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $asset: ${e.message}")
            }
        }

        return ImportResult(total, byExam, bySubj, errors)
    }

    private fun parseQuestion(o: JSONObject, source: String, index: Int): Question {
        val choicesArr = o.optJSONArray("choices") ?: throw IllegalArgumentException("Missing choices")
        val raw = (0 until choicesArr.length()).map { choicesArr.optString(it, "") }.filter { it.isNotBlank() }
        val choices = if (raw.size >= 4) raw.take(4) else raw + List(4 - raw.size) { "-" }
        val diff = try { QuizDifficulty.valueOf(o.optString("difficulty", "MEDIUM")) } catch (_: Exception) { QuizDifficulty.MEDIUM }
        val level = try { LevelGroup.valueOf(o.optString("levelGroup", "GRADE_5_8")) } catch (_: Exception) { LevelGroup.GRADE_5_8 }
        val subjStr = o.optString("subject", "MAT").let { if (it == "INGILIZCE") "ING" else it }
        val subject = try { Subject.valueOf(subjStr) } catch (_: Exception) { Subject.MAT }
        val examType = try { ExamType.valueOf(o.optString("examType", "GENERAL")) } catch (_: Exception) { ExamType.GENERAL }
        val gradeFromDto = o.optInt("grade", 0)
        val grade = when {
            gradeFromDto in 1..7 -> gradeFromDto
            examType == ExamType.LGS -> 8
            else -> GradePrefs(context).getSelectedGrade().coerceIn(1, 7)
        }
        val type = o.optString("type", "").takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: "UNKNOWN"
        val skill = o.optString("skill", "").takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: "UNKNOWN"
        return Question(
            id = o.optString("id", "q_${source}_${index}"),
            levelGroup = level,
            subject = subject,
            gradeTag = o.optString("gradeTag", ""),
            grade = grade,
            stem = o.optString("stem", "?"),
            choices = choices.ifEmpty { listOf("A", "B", "C", "D") },
            correctIndex = o.optInt("correctIndex", 0).coerceIn(0, 3),
            hint = o.optString("hint", "").takeIf { it.isNotEmpty() },
            imageAsset = o.optString("imageAsset", "").takeIf { it.isNotEmpty() },
            difficulty = diff,
            examType = examType,
            topic = o.optString("topic", "").takeIf { it.isNotEmpty() },
            type = type,
            skill = skill
        )
    }
}
