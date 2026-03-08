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

/** LGS import pack schema: { version, mode, subject, publisher, questions: [ { difficulty, topic, skills, questionType, stem, options, answerIndex, explanation, imageAsset, source, sourceRef } ] } */

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
        val parseErrors: Int = 0,
        val validationRejected: Int = 0
    ) {
        val summary: String
            get() = buildString {
                append("Ekle: $inserted")
                if (skippedDuplicate > 0) append(" | Tekrar atlandı: $skippedDuplicate")
                if (deactivatedTooBasic > 0) append(" | Kalite düşük (pasif): $deactivatedTooBasic")
                if (parseErrors > 0) append(" | Parse hatası: $parseErrors")
                if (validationRejected > 0) append(" | Doğrulama reddedildi: $validationRejected")
            }
    }

    /** Result of importing all LGS packs from assets/lgs_import/ */
    data class LgsImportSummary(
        val importedCount: Int,
        val skippedDuplicateCount: Int,
        val deactivatedLowQualityCount: Int,
        val parseErrorCount: Int,
        val totalLgsQuestionsAfter: Int,
        val perSubjectCounts: Map<String, Int>,
        val qualityDebug: LgsQualityDebugSummary?
    ) {
        val summaryText: String
            get() = buildString {
                appendLine("Import tamamlandı")
                appendLine("  Eklenen: $importedCount")
                appendLine("  Tekrar atlandı: $skippedDuplicateCount")
                if (deactivatedLowQualityCount > 0) appendLine("  Düşük kalite (pasif): $deactivatedLowQualityCount")
                appendLine("  Parse hatası: $parseErrorCount")
                appendLine("  Toplam LGS aktif: $totalLgsQuestionsAfter")
                appendLine("  Ders bazında:")
                perSubjectCounts.forEach { (subj, cnt) ->
                    appendLine("    $subj: $cnt")
                }
                qualityDebug?.let { qd ->
                    appendLine()
                    appendLine("LGS Kalite Özeti:")
                    appendLine("  Aktif: ${qd.activeCount}")
                    appendLine("  Pasif (düşük kalite): ${qd.inactiveLowQualityCount}")
                    val avgStr = qd.avgQualityScoreBySubject.entries.joinToString(", ") { e -> e.key + "=" + e.value }
                    appendLine("  Ort. qualityScore (ders): $avgStr")
                    val newGenStr = qd.newGenerationRatioBySubject.entries.joinToString(", ") { e -> e.key + "=" + String.format("%.0f", e.value * 100) + "%" }
                    appendLine("  Yeni nesil oranı (ders): $newGenStr")
                }
            }
    }

    /** LGS quality debug summary. */
    data class LgsQualityDebugSummary(
        val activeCount: Int,
        val inactiveLowQualityCount: Int,
        val avgQualityScoreBySubject: Map<String, Int>,
        val newGenerationRatioBySubject: Map<String, Float>
    )

    private const val LGS_GRADE = 8
    private const val LGS_MAT_IMPORT_DIR = "lgs_import/mat"
    private val LGS_IMPORT_FILES = listOf(
        "lgs_mat.json", "lgs_turkce.json", "lgs_fen.json",
        "lgs_inkilap.json", "lgs_din.json", "lgs_ing.json"
    )

    /** Import only math LGS packs from assets/lgs_import/mat/ (JSON files). Forces subject=mat, mode=LGS. */
    fun importMatLgsPacksFromAssets(context: Context): LgsMatImportSummary = runBlocking(Dispatchers.IO) {
        val matDir = LGS_MAT_IMPORT_DIR
        val jsonFiles = context.assets.list(matDir)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?.toList()
            ?: emptyList()
        if (jsonFiles.isEmpty()) {
            val db = DatabaseProvider.get(context)
            val dao = db.questionDao()
            val activeMat = dao.getLgsCountsBySubject().firstOrNull { it.subject == "mat" }?.count ?: 0
            val inactiveMat = dao.countLgsInactiveLowQualityBySubject("mat")
            val byDiff = dao.getLgsCountsByDifficultyForSubject("mat").associate { it.difficulty to it.count }
            val byType = dao.getLgsCountsByQuestionTypeForSubject("mat").associate { it.questionType to it.count }
            return@runBlocking LgsMatImportSummary(
                importedCount = 0,
                skippedDuplicateCount = 0,
                deactivatedLowQualityCount = 0,
                parseErrorCount = 0,
                validationRejectedCount = 0,
                activeMatCount = activeMat,
                inactiveLowQualityMatCount = inactiveMat,
                matCountsByDifficulty = byDiff,
                matCountsByQuestionType = byType
            )
        }
        var totalImported = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        var totalValidationRejected = 0
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$matDir/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "mat", packName = fileName)
            totalImported += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
            totalValidationRejected += result.validationRejected
        }
        val db = DatabaseProvider.get(context)
        val dao = db.questionDao()
        val activeMat = dao.getLgsCountsBySubject().firstOrNull { it.subject == "mat" }?.count ?: 0
        val inactiveMat = dao.countLgsInactiveLowQualityBySubject("mat")
        val byDiff = dao.getLgsCountsByDifficultyForSubject("mat").associate { it.difficulty to it.count }
        val byType = dao.getLgsCountsByQuestionTypeForSubject("mat").associate { it.questionType to it.count }
        LgsMatImportSummary(
            importedCount = totalImported,
            skippedDuplicateCount = totalSkippedDuplicate,
            deactivatedLowQualityCount = totalDeactivatedLowQuality,
            parseErrorCount = totalParseErrors,
            validationRejectedCount = totalValidationRejected,
            activeMatCount = activeMat,
            inactiveLowQualityMatCount = inactiveMat,
            matCountsByDifficulty = byDiff,
            matCountsByQuestionType = byType
        )
    }

    /** Import all LGS question packs from assets/lgs_import/. Returns summary. */
    fun importAllLgsPacksFromAssets(context: Context): LgsImportSummary = runBlocking(Dispatchers.IO) {
        var totalImported = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0

        for (fileName in LGS_IMPORT_FILES) {
            val json = readAsset(context, "lgs_import/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = null)
            totalImported += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }

        val db = DatabaseProvider.get(context)
        val dao = db.questionDao()
        val totalLgs = dao.countLgsActive()
        val perSubject = dao.getLgsCountsBySubject().associate { it.subject to it.count }
        val qualityDebug = buildLgsQualityDebugSummary(dao)

        LgsImportSummary(
            importedCount = totalImported,
            skippedDuplicateCount = totalSkippedDuplicate,
            deactivatedLowQualityCount = totalDeactivatedLowQuality,
            parseErrorCount = totalParseErrors,
            totalLgsQuestionsAfter = totalLgs,
            perSubjectCounts = perSubject,
            qualityDebug = qualityDebug
        )
    }

    private fun readAsset(context: Context, path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        Log.w(TAG, "Asset not found or unreadable: $path", e)
        null
    }

    /** Import a single LGS pack JSON. Applies LGS quality rules; low-quality items are deactivated. */
    fun importLgsPackFromJson(context: Context, json: String): ImportResult = runBlocking(Dispatchers.IO) {
        runLgsImport(context, json, forceSubject = null)
    }

    private suspend fun runLgsImport(context: Context, json: String, forceSubject: String? = null, packName: String? = null): ImportResult = withContext(Dispatchers.IO) {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            Log.e(TAG, "LGS JSON parse failed", e)
            return@withContext ImportResult(0, 0, 0, 1)
        }

        val questionsArr = root.optJSONArray("questions") ?: return@withContext ImportResult(0, 0, 0, 1)
        val packSubject = forceSubject ?: normalizeSubject(root.optString("subject", "mat"))
        val publisher = root.optString("publisher", "").takeIf { it.isNotBlank() }
        val sourcePack = "lgs_import_${packSubject}"

        var validationRejected = 0
        val validationRejectedIndices = mutableSetOf<Int>()
        if (packSubject == "mat") {
            val packValidation = MatQuestionValidator.validatePack(root, packName)
            val report = MatQuestionValidator.buildReport(packValidation, packName)
            Log.i(TAG, report.formatForLog())
            packValidation.questionResults
                .filter { !it.second.isValid }
                .forEach { (idx, r) ->
                    validationRejectedIndices.add(idx)
                    Log.w(TAG, "MAT validation rejected q$idx: ${r.summary}")
                }
        }

        val db = DatabaseProvider.get(context)
        val questionDao = db.questionDao()
        val existingStemKeys = questionDao.getAllQuestions().mapTo(mutableSetOf()) { e ->
            val h = e.stemHash.substringBefore(":dup:")
            "${e.grade}|${e.subject}|$h"
        }

        var inserted = 0
        var skippedDuplicate = 0
        var deactivatedLowQuality = 0
        var parseErrors = 0
        val batchSeenStemKeys = mutableSetOf<String>()
        val toInsert = mutableListOf<QuestionEntity>()

        for (i in 0 until questionsArr.length()) {
            if (packSubject == "mat" && i in validationRejectedIndices) {
                validationRejected++
                continue
            }
            val qObj = questionsArr.optJSONObject(i)
            if (qObj == null) {
                parseErrors++
                continue
            }
            val entity = parseLgsQuestion(qObj, i, packSubject)
            if (entity == null) {
                parseErrors++
                continue
            }

            val stemKey = "${entity.grade}|${entity.subject}|${entity.stemHash}"
            if (stemKey in batchSeenStemKeys || stemKey in existingStemKeys) {
                skippedDuplicate++
                continue
            }
            batchSeenStemKeys.add(stemKey)
            existingStemKeys.add(stemKey)

            val options = parseOptionsFromEntity(entity)
            val qualityResult = LgsQualityRules.evaluate(
                stem = entity.questionText,
                options = options,
                subjectKey = packSubject,
                hasImageAsset = entity.imageAsset.isNullOrBlank().not(),
                difficulty = entity.difficulty
            )
            if (!qualityResult.isActive) deactivatedLowQuality++

            val finalEntity = entity.copy(
                isActive = qualityResult.isActive,
                deactivationReason = qualityResult.deactivationReason,
                qualityScore = qualityResult.qualityScore,
                isNewGenerationLike = qualityResult.isNewGenerationLike,
                type = qualityResult.questionType,
                publisher = publisher,
                sourcePack = sourcePack
            )
            toInsert.add(finalEntity)
        }

        if (toInsert.isNotEmpty()) {
            questionDao.insertAll(toInsert)
            inserted = toInsert.size
        }

        Log.i(TAG, "LGS import $packSubject: inserted=$inserted, skippedDuplicate=$skippedDuplicate, deactivatedLowQuality=$deactivatedLowQuality, parseErrors=$parseErrors, validationRejected=$validationRejected")
        ImportResult(inserted, skippedDuplicate, deactivatedLowQuality, parseErrors, validationRejected)
    }

    private suspend fun buildLgsQualityDebugSummary(dao: com.brainbuddy.app.db.QuestionDao): LgsQualityDebugSummary {
        val activeCount = dao.countLgsActive()
        val inactiveLowQuality = dao.countLgsInactiveLowQuality()
        val avgBySubj = dao.getLgsAvgQualityBySubject().associate { it.subject to it.avgQualityScore.toInt() }
        val newGenBySubj = dao.getLgsNewGenCountBySubject().associate { row ->
            row.subject to (if (row.totalCount > 0) row.newGenCount.toFloat() / row.totalCount else 0f)
        }
        return LgsQualityDebugSummary(
            activeCount = activeCount,
            inactiveLowQualityCount = inactiveLowQuality,
            avgQualityScoreBySubject = avgBySubj,
            newGenerationRatioBySubject = newGenBySubj
        )
    }

    private fun parseOptionsFromEntity(e: QuestionEntity): List<String> = try {
        val arr = JSONArray(e.optionsJson)
        (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    private fun parseLgsQuestion(o: JSONObject, index: Int, defaultSubject: String): QuestionEntity? {
        val stem = o.optString("stem", "").ifBlank {
            o.optString("questionText", "").ifBlank {
                o.optString("question", "")
            }
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
        val topic = o.optString("topic", "").takeIf { it.isNotBlank() }
        val explanation = o.optString("explanation", "").takeIf { it.isNotBlank() }
        val imageAsset = o.optString("imageAsset", "").takeIf { it.isNotBlank() }
        val source = o.optString("source", "").takeIf { it.isNotBlank() }
        val sourceRef = o.optString("sourceRef", "").takeIf { it.isNotBlank() }

        val stemNorm = QuestionStemHash.normalizeStem(stem)
        val hash = QuestionStemHash.stemHash(stem)
        val id = o.optString("id", "").takeIf { it.isNotBlank() }
            ?: "lgs_${LGS_GRADE}_${subject}_${index}_${hash.take(8)}"

        return QuestionEntity(
            id = id,
            grade = LGS_GRADE,
            subject = subject,
            difficulty = difficulty,
            questionText = stem,
            optionsJson = JSONArray(options).toString(),
            answerIndex = answerIndex,
            explanation = explanation,
            isActive = true,
            questionType = questionType,
            skillsJson = skillsJson,
            deactivationReason = null,
            version = 1,
            examType = "LGS",
            imageAsset = imageAsset,
            type = questionType,
            skill = skillsArr?.optString(0, "")?.takeIf { it.isNotBlank() } ?: "UNKNOWN",
            stemNormalized = stemNorm,
            stemHash = hash,
            sourcePack = null,
            source = source,
            sourceRef = sourceRef,
            publisher = null,
            year = null,
            topic = topic
        )
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

        val packGrade = root.optInt("grade", 6).coerceIn(1, 7)
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

        val grade = (o.optInt("grade", 0).takeIf { it in 1..7 } ?: defaultGrade).coerceIn(1, 7)
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

        val gate = QuestionQualityGate.evaluate(subjectEnum, grade, stem, options, difficulty)
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
        "inkilap", "inkılap", "inkılap tarihi", "tc_inkilap" -> "inkilap"
        "din", "din kültürü", "din kültürü ve ahlak bilgisi" -> "din"
        else -> "mat"
    }
}
