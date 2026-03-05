package com.brainbuddy.app.quiz

import android.content.Context
import android.util.Log
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.QuestionEntity
import com.brainbuddy.app.db.QuestionStemHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Import soru paketi JSON formatı:
 * {
 *   "version": 1,
 *   "publisher": "...",
 *   "year": 2026,
 *   "grade": 6,
 *   "subject": "mat",
 *   "questions": [
 *     { "stem": "...", "options": ["A","B","C","D"], "answerIndex": 2, "difficulty": 2,
 *       "questionType": "PROBLEM", "skills": ["oran_oranti"], "sourceRef": "pdf:xxx p.12" }
 *   ]
 * }
 *
 * Akış: normalize → dedup → DB'ye yaz → sonuç raporu (inserted / skippedDuplicate / deactivatedTooBasic)
 */
object QuestionPackImporter {
    private const val TAG = "QuestionPackImporter"

    data class ImportResult(
        val inserted: Int,
        val skippedDuplicate: Int,
        val deactivatedTooBasic: Int,
        val parseErrors: Int = 0
    ) {
        val summary: String
            get() = buildString {
                append("Ekle: $inserted")
                if (skippedDuplicate > 0) append(" | Tekrar atlandı: $skippedDuplicate")
                if (deactivatedTooBasic > 0) append(" | Kalite düşük (pasif): $deactivatedTooBasic")
                if (parseErrors > 0) append(" | Parse hatası: $parseErrors")
            }
    }

    /** Raw JSON string alır; wrapped veya raw array destekler. */
    fun importFromJson(context: Context, json: String): ImportResult {
        val root = try {
            when {
                json.trimStart().startsWith("{") -> JSONObject(json)
                else -> return importRawArray(context, JSONArray(json))
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed", e)
            return ImportResult(0, 0, 0, 1)
        }

        val questionsArr = root.optJSONArray("questions")
            ?: return ImportResult(0, 0, 0, 1)

        val packGrade = root.optInt("grade", 6).coerceIn(2, 8)
        val packSubject = normalizeSubject(root.optString("subject", "mat"))
        val publisher = root.optString("publisher", "").takeIf { it.isNotBlank() }
        val year = root.optInt("year", 0).takeIf { it > 0 }
        val sourcePack = "import_${packGrade}_${packSubject}"

        return runBlocking(Dispatchers.IO) {
            runImport(
                context = context,
                questionsArr = questionsArr,
                defaultGrade = packGrade,
                defaultSubject = packSubject,
                publisher = publisher,
                year = year,
                sourcePack = sourcePack
            )
        }
    }

    /** Eski format: doğrudan soru array'i. */
    fun importRawArray(context: Context, arr: JSONArray): ImportResult = runBlocking(Dispatchers.IO) {
        runImport(
            context = context,
            questionsArr = arr,
            defaultGrade = 6,
            defaultSubject = "mat",
            publisher = null,
            year = null,
            sourcePack = "import_legacy"
        )
    }

    private suspend fun runImport(
        context: Context,
        questionsArr: JSONArray,
        defaultGrade: Int,
        defaultSubject: String,
        publisher: String?,
        year: Int?,
        sourcePack: String
    ): ImportResult = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(context)
        val questionDao = db.questionDao()

        val existingStemKeys = questionDao.getAllQuestions().mapTo(mutableSetOf()) { e ->
            val h = e.stemHash.substringBefore(":dup:")
            "${e.grade}|${e.subject}|$h"
        }

        var inserted = 0
        var skippedDuplicate = 0
        var deactivatedTooBasic = 0
        var parseErrors = 0
        val batchSeenStemKeys = mutableSetOf<String>()

        val toInsert = mutableListOf<QuestionEntity>()

        forLoop@ for (i in 0 until questionsArr.length()) {
            val qObj = questionsArr.optJSONObject(i)
            if (qObj == null) {
                parseErrors++
                continue@forLoop
            }
            val parsed = parseAndNormalize(qObj, i, defaultGrade, defaultSubject)
            if (parsed == null) {
                parseErrors++
                continue@forLoop
            }

            val entity = parsed.entity
            val gate = parsed.gate
            val stemKey = "${entity.grade}|${entity.subject}|${entity.stemHash}"

            if (stemKey in batchSeenStemKeys || stemKey in existingStemKeys) {
                skippedDuplicate++
                continue
            }

            batchSeenStemKeys.add(stemKey)
            existingStemKeys.add(stemKey)

            if (!gate.isActive) {
                deactivatedTooBasic++
            }

            val finalEntity = entity.copy(
                isActive = gate.isActive,
                questionType = gate.questionType,
                skillsJson = parsed.skillsJson,
                deactivationReason = gate.deactivationReason,
                publisher = publisher,
                year = year,
                sourcePack = sourcePack
            )
            toInsert.add(finalEntity)
        }

        if (toInsert.isNotEmpty()) {
            db.questionDao().insertAll(toInsert)
            inserted = toInsert.size
        }

        Log.i(TAG, "Import result: inserted=$inserted, skippedDuplicate=$skippedDuplicate, deactivatedTooBasic=$deactivatedTooBasic, parseErrors=$parseErrors")
        ImportResult(inserted, skippedDuplicate, deactivatedTooBasic, parseErrors)
    }

    private data class ParsedQuestion(
        val entity: QuestionEntity,
        val gate: QuestionQualityGate.Result,
        val skillsJson: String
    )

    private fun parseAndNormalize(o: JSONObject, index: Int, defaultGrade: Int, defaultSubject: String): ParsedQuestion? {
        val stem = o.optString("stem", "").ifBlank {
            o.optString("questionText", "")
        }.trim()
        if (stem.isBlank()) return null

        val optionsRaw = o.optJSONArray("options") ?: o.optJSONArray("choices") ?: return null
        val rawOpts = (0 until optionsRaw.length())
            .map { optionsRaw.optString(it, "").ifEmpty { optionsRaw.opt(it)?.toString() ?: "" } }
            .filter { it.isNotBlank() }
        if (rawOpts.size < 2) return null
        val options = if (rawOpts.size >= 4) rawOpts.take(4) else rawOpts + List(4 - rawOpts.size) { "-" }

        val answerIndex = (o.optInt("answerIndex", 0).takeIf { o.has("answerIndex") }
            ?: o.optInt("correctIndex", 0)).coerceIn(0, options.size - 1)

        val grade = (o.optInt("grade", 0).takeIf { it in 2..8 } ?: defaultGrade).coerceIn(2, 8)
        val subject = o.optString("subject", "").let { s ->
            if (s.isBlank()) defaultSubject else normalizeSubject(s)
        }

        val difficulty = when (val d = o.opt("difficulty")) {
            is Int -> d.coerceIn(0, 2)
            is String -> when (d.uppercase()) {
                "EASY" -> 0
                "HARD", "VERY_HARD" -> 2
                else -> 1
            }
            else -> 1
        }

        val questionType = o.optString("questionType", "").takeIf { it.isNotBlank() } ?: "UNKNOWN"
        val skillsArr = o.optJSONArray("skills")
        val skillsJson = if (skillsArr != null && skillsArr.length() > 0) {
            (0 until skillsArr.length()).map { skillsArr.optString(it, "") }.filter { it.isNotBlank() }
                .let { JSONArray(it).toString() }
        } else "[]"

        val sourceRef = o.optString("sourceRef", "").takeIf { it.isNotBlank() }

        val subjectEnum = when (subject) {
            "mat" -> Subject.MAT
            "turkce" -> Subject.TURKCE
            "fen" -> Subject.FEN
            "sosyal" -> Subject.SOSYAL
            "ing" -> Subject.ING
            else -> Subject.MAT
        }

        val gate = QuestionQualityGate.evaluate(subjectEnum, grade, stem, options)
        val stemNorm = QuestionStemHash.normalizeStem(stem)
        val hash = QuestionStemHash.stemHash(stem)

        val id = o.optString("id", "").takeIf { it.isNotBlank() }
            ?: "imp_${grade}_${subject}_${index}_${hash.take(8)}"

        val entity = QuestionEntity(
            id = id,
            grade = grade,
            subject = subject,
            difficulty = difficulty,
            questionText = stem,
            optionsJson = JSONArray(options).toString(),
            answerIndex = answerIndex,
            explanation = null,
            isActive = true,
            questionType = questionType,
            skillsJson = skillsJson,
            deactivationReason = null,
            version = 1,
            examType = "GENERAL",
            imageAsset = null,
            type = questionType,
            skill = skillsArr?.optString(0, "")?.takeIf { it.isNotBlank() } ?: "UNKNOWN",
            stemNormalized = stemNorm,
            stemHash = hash,
            sourcePack = null,
            sourceRef = sourceRef,
            publisher = null,
            year = null,
            topic = null
        )
        return ParsedQuestion(entity, gate, skillsJson)
    }

    private fun normalizeSubject(s: String): String = when (s.trim().lowercase()) {
        "mat", "matematik", "math" -> "mat"
        "turkce", "türkçe", "tr" -> "turkce"
        "fen", "fen bilimleri" -> "fen"
        "sosyal", "sosyal bilgiler" -> "sosyal"
        "ing", "ingilizce", "english", "eng" -> "ing"
        else -> "mat"
    }
}
