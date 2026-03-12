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
    private const val LGS_MAT2_IMPORT_DIR = "lgs_import/mat2"
    private const val LGS_MAT3_IMPORT_DIR = "lgs_import/mat3"
    private const val LGS_MAT4_IMPORT_DIR = "lgs_import/mat4"
    private const val LGS_MAT5_IMPORT_DIR = "lgs_import/mat5"
    private const val LGS_MAT7_IMPORT_DIR = "lgs_import/mat7"
    private const val LGS_FEN_IMPORT_DIR = "lgs_import/fen"
    private const val LGS_FEN3_IMPORT_DIR = "lgs_import/fen3"
    private const val LGS_FEN4_IMPORT_DIR = "lgs_import/fen4"
    private const val LGS_FEN5_IMPORT_DIR = "lgs_import/fen5"
    private const val LGS_FEN6_IMPORT_DIR = "lgs_import/fen6"
    private const val LGS_FEN7_IMPORT_DIR = "lgs_import/fen7"
    private const val LGS_INKILAP_IMPORT_DIR = "lgs_import/inkilap"
    private const val LGS_INKILAP7_IMPORT_DIR = "lgs_import/inkilap7"
    private const val LGS_TURKCE_IMPORT_DIR = "lgs_import/turkce"
    private const val LGS_TURKCE2_IMPORT_DIR = "lgs_import/turkce2"
    private const val LGS_TURKCE3_IMPORT_DIR = "lgs_import/turkce3"
    private const val LGS_TURKCE4_IMPORT_DIR = "lgs_import/turkce4"
    private const val LGS_TURKCE5_IMPORT_DIR = "lgs_import/turkce5"
    private const val LGS_TURKCE7_IMPORT_DIR = "lgs_import/turkce7"
    private const val LGS_DIN_IMPORT_DIR = "lgs_import/din"
    private const val LGS_DIN4_IMPORT_DIR = "lgs_import/din4"
    private const val LGS_DIN5_IMPORT_DIR = "lgs_import/din5"
    private const val LGS_DIN6_IMPORT_DIR = "lgs_import/din6"
    private const val LGS_DIN7_IMPORT_DIR = "lgs_import/din7"
    private const val LGS_ENGLISH_IMPORT_DIR = "lgs_import/english"
    private const val LGS_ENGLISH2_IMPORT_DIR = "lgs_import/english2"
    private const val LGS_ENGLISH3_IMPORT_DIR = "lgs_import/english3"
    private const val LGS_ENGLISH4_IMPORT_DIR = "lgs_import/english4"
    private const val LGS_ENGLISH5_IMPORT_DIR = "lgs_import/english5"
    private const val LGS_ENGLISH6_IMPORT_DIR = "lgs_import/english6"
    private const val LGS_ENGLISH7_IMPORT_DIR = "lgs_import/english7"
    private const val LGS_SOSYAL4_IMPORT_DIR = "lgs_import/sosyal4"
    private const val LGS_HAYAT2_IMPORT_DIR = "lgs_import/hayat2"
    private const val LGS_HAYAT3_IMPORT_DIR = "lgs_import/hayat3"
    private const val LGS_SOSYAL5_IMPORT_DIR = "lgs_import/sosyal5"
    private const val LGS_SOSYAL6_IMPORT_DIR = "lgs_import/sosyal6"
    private val LGS_IMPORT_FILES = listOf(
        "lgs_mat.json", "lgs_turkce.json", "lgs_fen.json",
        "lgs_inkilap.json", "lgs_din.json", "lgs_ing.json"
    )

    /** Import 2nd grade math packs from lgs_import/mat2/ (all JSON files). Forces subject=mat, grade=2. */
    fun importMat2LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runMat2LgsImportFromAssets(context)
    }

    /** Import 3rd grade math packs from lgs_import/mat3/ (all JSON files). Forces subject=mat, grade=3. */
    fun importMat3LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runMat3LgsImportFromAssets(context)
    }

    /** Import 4th grade math packs from lgs_import/mat4/ (all JSON files). Forces subject=mat, grade=4. */
    fun importMat4LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runMat4LgsImportFromAssets(context)
    }

    /** Import 5th grade math packs from lgs_import/mat5/ (all JSON files). Forces subject=mat, grade=5. */
    fun importMat5LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runMat5LgsImportFromAssets(context)
    }

    /** Import 7th grade math packs from assets/lgs_import/mat7/ (JSON files). Forces subject=mat, grade=7. */
    fun importMat7LgsPacksFromAssets(context: Context): LgsMatImportSummary = runBlocking(Dispatchers.IO) {
        val matDir = LGS_MAT7_IMPORT_DIR
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
                importedCount = 0, skippedDuplicateCount = 0, deactivatedLowQualityCount = 0,
                parseErrorCount = 0, validationRejectedCount = 0,
                activeMatCount = activeMat, inactiveLowQualityMatCount = inactiveMat,
                matCountsByDifficulty = byDiff, matCountsByQuestionType = byType
            )
        }
        var totalImported = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        var totalValidationRejected = 0
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$matDir/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "mat", packName = fileName, forceGrade = 7)
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
            importedCount = totalImported, skippedDuplicateCount = totalSkippedDuplicate,
            deactivatedLowQualityCount = totalDeactivatedLowQuality, parseErrorCount = totalParseErrors,
            validationRejectedCount = totalValidationRejected,
            activeMatCount = activeMat, inactiveLowQualityMatCount = inactiveMat,
            matCountsByDifficulty = byDiff, matCountsByQuestionType = byType
        )
    }

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

    /** Import FEN LGS packs from lgs_import/lgs_fen.json and lgs_import/fen/ (all JSON files). Forces subject=fen. */
    fun importFenLgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runFenLgsImportFromAssets(context)
    }

    /** Import 3rd grade Science packs from lgs_import/fen3/ (all JSON files). Forces subject=fen, grade=3. */
    fun importFen3LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runFen3LgsImportFromAssets(context)
    }

    /** Import 4th grade Science packs from lgs_import/fen4/ (all JSON files). Forces subject=fen, grade=4. */
    fun importFen4LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runFen4LgsImportFromAssets(context)
    }

    /** Import 5th grade Science packs from lgs_import/fen5/ (all JSON files). Forces subject=fen, grade=5. */
    fun importFen5LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runFen5LgsImportFromAssets(context)
    }

    /** Import 6th grade Science packs from lgs_import/fen6/ (all JSON files). Forces subject=fen, grade=6. */
    fun importFen6LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runFen6LgsImportFromAssets(context)
    }

    /** Import 4th grade Social Studies packs from lgs_import/sosyal4/ (all JSON files). Forces subject=sosyal, grade=4. */
    fun importSosyal4LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runSosyal4LgsImportFromAssets(context)
    }

    /** Import 2nd grade Life Studies (Hayat Bilgisi) packs from lgs_import/hayat2/ (all JSON files). Forces subject=hayat, grade=2. */
    fun importHayat2LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runHayat2LgsImportFromAssets(context)
    }

    /** Import 3rd grade Life Studies (Hayat Bilgisi) packs from lgs_import/hayat3/ (all JSON files). Forces subject=hayat, grade=3. */
    fun importHayat3LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runHayat3LgsImportFromAssets(context)
    }

    /** Import 5th grade Social Studies packs from lgs_import/sosyal5/ (all JSON files). Forces subject=sosyal, grade=5. */
    fun importSosyal5LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runSosyal5LgsImportFromAssets(context)
    }

    /** Import 6th grade Social Studies packs from lgs_import/sosyal6/ (all JSON files). Forces subject=sosyal, grade=6. */
    fun importSosyal6LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runSosyal6LgsImportFromAssets(context)
    }

    /** Import 7th grade Science packs from lgs_import/fen7/ (all JSON files). Forces subject=fen, grade=7. */
    fun importFen7LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runFen7LgsImportFromAssets(context)
    }

    /** Import İnkılap LGS packs from lgs_import/lgs_inkilap.json and lgs_import/inkilap/ (all JSON files). Forces subject=inkilap. */
    fun importInkilapLgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runInkilapLgsImportFromAssets(context)
    }

    /** Import 2nd grade English packs from lgs_import/english2/ (all JSON files). Forces subject=ing, grade=2. */
    fun importEnglish2LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runEnglish2LgsImportFromAssets(context)
    }

    /** Import 3rd grade English packs from lgs_import/english3/ (all JSON files). Forces subject=ing, grade=3. */
    fun importEnglish3LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runEnglish3LgsImportFromAssets(context)
    }

    /** Import 4th grade English packs from lgs_import/english4/ (all JSON files). Forces subject=ing, grade=4. */
    fun importEnglish4LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runEnglish4LgsImportFromAssets(context)
    }

    /** Import 5th grade English packs from lgs_import/english5/ (all JSON files). Forces subject=ing, grade=5. */
    fun importEnglish5LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runEnglish5LgsImportFromAssets(context)
    }

    /** Import 6th grade English packs from lgs_import/english6/ (all JSON files). Forces subject=ing, grade=6. */
    fun importEnglish6LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runEnglish6LgsImportFromAssets(context)
    }

    /** Import 7th grade English packs from lgs_import/english7/ (all JSON files). Forces subject=ing, grade=7. */
    fun importEnglish7LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runEnglish7LgsImportFromAssets(context)
    }

    /** Import 7th grade İnkılap packs from lgs_import/inkilap7/ (all JSON files). Forces subject=inkilap, grade=7. */
    fun importInkilap7LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runInkilap7LgsImportFromAssets(context)
    }

    /** Import Türkçe LGS packs from lgs_import/turkce/ (all JSON files). Forces subject=turkce. */
    fun importTurkceLgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runTurkceLgsImportFromAssets(context)
    }

    /** Import 2nd grade Turkish packs from lgs_import/turkce2/ (all JSON files). Forces subject=turkce, grade=2. */
    fun importTurkce2LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runTurkce2LgsImportFromAssets(context)
    }

    /** Import 3rd grade Turkish packs from lgs_import/turkce3/ (all JSON files). Forces subject=turkce, grade=3. */
    fun importTurkce3LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runTurkce3LgsImportFromAssets(context)
    }

    /** Import 4th grade Turkish packs from lgs_import/turkce4/ (all JSON files). Forces subject=turkce, grade=4. */
    fun importTurkce4LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runTurkce4LgsImportFromAssets(context)
    }

    /** Import 5th grade Turkish packs from lgs_import/turkce5/ (all JSON files). Forces subject=turkce, grade=5. */
    fun importTurkce5LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runTurkce5LgsImportFromAssets(context)
    }

    /** Import 7th grade Turkish packs from lgs_import/turkce7/ (all JSON files). Forces subject=turkce, grade=7. */
    fun importTurkce7LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runTurkce7LgsImportFromAssets(context)
    }

    /** Import Din Kültürü LGS packs from lgs_import/din/ (all JSON files). Forces subject=din. */
    fun importDinLgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runDinLgsImportFromAssets(context)
    }

    /** Import 4th grade Din Kültürü packs from lgs_import/din4/ (all JSON files). Forces subject=din, grade=4. */
    fun importDin4LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runDin4LgsImportFromAssets(context)
    }

    /** Import 5th grade Din Kültürü packs from lgs_import/din5/ (all JSON files). Forces subject=din, grade=5. */
    fun importDin5LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runDin5LgsImportFromAssets(context)
    }

    /** Import 6th grade Din Kültürü packs from lgs_import/din6/ (all JSON files). Forces subject=din, grade=6. */
    fun importDin6LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runDin6LgsImportFromAssets(context)
    }

    /** Import 7th grade Din Kültürü packs from lgs_import/din7/ (all JSON files). Forces subject=din, grade=7. */
    fun importDin7LgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runDin7LgsImportFromAssets(context)
    }

    /** Import English LGS packs from lgs_import/english/ (all JSON files). Forces subject=ing. */
    fun importEnglishLgsPacksFromAssets(context: Context): ImportResult = runBlocking(Dispatchers.IO) {
        runEnglishLgsImportFromAssets(context)
    }

    private suspend fun runInkilapLgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0

        val inkilapFiles = mutableListOf<Pair<String, String>>()
        readAsset(context, "lgs_import/lgs_inkilap.json")?.let { inkilapFiles.add("lgs_inkilap.json" to it) }
        context.assets.list(LGS_INKILAP_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?.forEach { fileName ->
                readAsset(context, "$LGS_INKILAP_IMPORT_DIR/$fileName")?.let { inkilapFiles.add(fileName to it) }
            }

        for ((packName, json) in inkilapFiles) {
            val result = runLgsImport(context, json, forceSubject = "inkilap", packName = packName)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runInkilap7LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_INKILAP7_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_INKILAP7_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "inkilap", packName = fileName, forceGrade = 7)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runEnglish2LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_ENGLISH2_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_ENGLISH2_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "ing", packName = fileName, forceGrade = 2)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runEnglish3LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_ENGLISH3_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_ENGLISH3_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "ing", packName = fileName, forceGrade = 3)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runEnglish4LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_ENGLISH4_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_ENGLISH4_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "ing", packName = fileName, forceGrade = 4)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runEnglish5LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_ENGLISH5_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_ENGLISH5_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "ing", packName = fileName, forceGrade = 5)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runEnglish6LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_ENGLISH6_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_ENGLISH6_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "ing", packName = fileName, forceGrade = 6)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runEnglish7LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_ENGLISH7_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_ENGLISH7_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "ing", packName = fileName, forceGrade = 7)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runFenLgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0

        val fenFiles = mutableListOf<Pair<String, String>>()
        readAsset(context, "lgs_import/lgs_fen.json")?.let { fenFiles.add("lgs_fen.json" to it) }
        context.assets.list(LGS_FEN_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?.forEach { fileName ->
                readAsset(context, "$LGS_FEN_IMPORT_DIR/$fileName")?.let { fenFiles.add(fileName to it) }
            }

        for ((packName, json) in fenFiles) {
            val result = runLgsImport(context, json, forceSubject = "fen", packName = packName)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runMat2LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_MAT2_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_MAT2_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "mat", packName = fileName, forceGrade = 2)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runMat3LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_MAT3_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_MAT3_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "mat", packName = fileName, forceGrade = 3)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runMat4LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_MAT4_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_MAT4_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "mat", packName = fileName, forceGrade = 4)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runMat5LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_MAT5_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_MAT5_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "mat", packName = fileName, forceGrade = 5)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runFen3LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_FEN3_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_FEN3_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "fen", packName = fileName, forceGrade = 3)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runFen4LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_FEN4_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_FEN4_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "fen", packName = fileName, forceGrade = 4)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runFen5LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_FEN5_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_FEN5_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "fen", packName = fileName, forceGrade = 5)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runFen6LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_FEN6_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_FEN6_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "fen", packName = fileName, forceGrade = 6)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runSosyal4LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_SOSYAL4_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_SOSYAL4_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "sosyal", packName = fileName, forceGrade = 4)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runHayat2LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_HAYAT2_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_HAYAT2_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "hayat", packName = fileName, forceGrade = 2)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runHayat3LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_HAYAT3_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_HAYAT3_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "hayat", packName = fileName, forceGrade = 3)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runSosyal5LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_SOSYAL5_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_SOSYAL5_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "sosyal", packName = fileName, forceGrade = 5)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runSosyal6LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_SOSYAL6_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_SOSYAL6_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "sosyal", packName = fileName, forceGrade = 6)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runFen7LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_FEN7_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_FEN7_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "fen", packName = fileName, forceGrade = 7)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runTurkceLgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val turkceFiles = mutableListOf<Pair<String, String>>()
        readAsset(context, "lgs_import/lgs_turkce.json")?.let { turkceFiles.add("lgs_turkce.json" to it) }
        context.assets.list(LGS_TURKCE_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?.forEach { fileName ->
                readAsset(context, "$LGS_TURKCE_IMPORT_DIR/$fileName")?.let { turkceFiles.add(fileName to it) }
            }
        for ((packName, json) in turkceFiles) {
            val result = runLgsImport(context, json, forceSubject = "turkce", packName = packName)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runTurkce2LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_TURKCE2_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_TURKCE2_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "turkce", packName = fileName, forceGrade = 2)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runTurkce3LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_TURKCE3_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_TURKCE3_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "turkce", packName = fileName, forceGrade = 3)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runTurkce4LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_TURKCE4_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_TURKCE4_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "turkce", packName = fileName, forceGrade = 4)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runTurkce5LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_TURKCE5_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_TURKCE5_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "turkce", packName = fileName, forceGrade = 5)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runTurkce7LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_TURKCE7_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_TURKCE7_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "turkce", packName = fileName, forceGrade = 7)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runDin4LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_DIN4_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_DIN4_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "din", packName = fileName, forceGrade = 4)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runDin5LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_DIN5_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_DIN5_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "din", packName = fileName, forceGrade = 5)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runDin6LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_DIN6_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_DIN6_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "din", packName = fileName, forceGrade = 6)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runDin7LgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val jsonFiles = context.assets.list(LGS_DIN7_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?: emptyList()
        for (fileName in jsonFiles) {
            val json = readAsset(context, "$LGS_DIN7_IMPORT_DIR/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = "din", packName = fileName, forceGrade = 7)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    /** Wraps legacy raw-array Din JSON (lgs_din_pack_*.json) into object format expected by runLgsImport. */
    private fun normalizeDinJsonForLgsImport(json: String): String {
        if (!json.trimStart().startsWith("[")) return json
        return try {
            val arr = JSONArray(json)
            JSONObject().apply {
                put("version", 1)
                put("mode", "LGS")
                put("subject", "din")
                put("questions", arr)
            }.toString()
        } catch (e: Exception) {
            Log.w(TAG, "normalizeDinJson: failed to wrap array, passing through", e)
            json
        }
    }

    private suspend fun runDinLgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val dinFiles = mutableListOf<Pair<String, String>>()
        readAsset(context, "lgs_import/lgs_din.json")?.let { dinFiles.add("lgs_din.json" to it) }
        context.assets.list(LGS_DIN_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?.forEach { fileName ->
                readAsset(context, "$LGS_DIN_IMPORT_DIR/$fileName")?.let { dinFiles.add(fileName to it) }
            }
        for ((packName, json) in dinFiles) {
            val normalizedJson = normalizeDinJsonForLgsImport(json)
            val result = runLgsImport(context, normalizedJson, forceSubject = "din", packName = packName)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    private suspend fun runEnglishLgsImportFromAssets(context: Context): ImportResult = withContext(Dispatchers.IO) {
        var totalInserted = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0
        val engFiles = mutableListOf<Pair<String, String>>()
        readAsset(context, "lgs_import/lgs_ing.json")?.let { engFiles.add("lgs_ing.json" to it) }
        context.assets.list(LGS_ENGLISH_IMPORT_DIR)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            ?.forEach { fileName ->
                readAsset(context, "$LGS_ENGLISH_IMPORT_DIR/$fileName")?.let { engFiles.add(fileName to it) }
            }
        for ((packName, json) in engFiles) {
            val result = runLgsImport(context, json, forceSubject = "ing", packName = packName)
            totalInserted += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }
        ImportResult(totalInserted, totalSkippedDuplicate, totalDeactivatedLowQuality, totalParseErrors)
    }

    /** Import all LGS question packs from assets/lgs_import/. Returns summary. */
    fun importAllLgsPacksFromAssets(context: Context): LgsImportSummary = runBlocking(Dispatchers.IO) {
        var totalImported = 0
        var totalSkippedDuplicate = 0
        var totalDeactivatedLowQuality = 0
        var totalParseErrors = 0

        for (fileName in LGS_IMPORT_FILES) {
            if (fileName == "lgs_fen.json") {
                val fenResult = runFenLgsImportFromAssets(context)
                totalImported += fenResult.inserted
                totalSkippedDuplicate += fenResult.skippedDuplicate
                totalDeactivatedLowQuality += fenResult.deactivatedTooBasic
                totalParseErrors += fenResult.parseErrors
                continue
            }
            if (fileName == "lgs_turkce.json") {
                val turkceResult = runTurkceLgsImportFromAssets(context)
                totalImported += turkceResult.inserted
                totalSkippedDuplicate += turkceResult.skippedDuplicate
                totalDeactivatedLowQuality += turkceResult.deactivatedTooBasic
                totalParseErrors += turkceResult.parseErrors
                continue
            }
            if (fileName == "lgs_din.json") {
                val dinResult = runDinLgsImportFromAssets(context)
                totalImported += dinResult.inserted
                totalSkippedDuplicate += dinResult.skippedDuplicate
                totalDeactivatedLowQuality += dinResult.deactivatedTooBasic
                totalParseErrors += dinResult.parseErrors
                continue
            }
            if (fileName == "lgs_inkilap.json") {
                val inkilapResult = runInkilapLgsImportFromAssets(context)
                totalImported += inkilapResult.inserted
                totalSkippedDuplicate += inkilapResult.skippedDuplicate
                totalDeactivatedLowQuality += inkilapResult.deactivatedTooBasic
                totalParseErrors += inkilapResult.parseErrors
                continue
            }
            if (fileName == "lgs_ing.json") {
                val engResult = runEnglishLgsImportFromAssets(context)
                totalImported += engResult.inserted
                totalSkippedDuplicate += engResult.skippedDuplicate
                totalDeactivatedLowQuality += engResult.deactivatedTooBasic
                totalParseErrors += engResult.parseErrors
                continue
            }
            val json = readAsset(context, "lgs_import/$fileName") ?: continue
            val result = runLgsImport(context, json, forceSubject = null)
            totalImported += result.inserted
            totalSkippedDuplicate += result.skippedDuplicate
            totalDeactivatedLowQuality += result.deactivatedTooBasic
            totalParseErrors += result.parseErrors
        }

        // Also import grade 2 math (mat2) packs
        val mat2Result = importMat2LgsPacksFromAssets(context)
        totalImported += mat2Result.inserted
        totalSkippedDuplicate += mat2Result.skippedDuplicate
        totalDeactivatedLowQuality += mat2Result.deactivatedTooBasic
        totalParseErrors += mat2Result.parseErrors

        // Also import grade 3 math (mat3) packs
        val mat3Result = importMat3LgsPacksFromAssets(context)
        totalImported += mat3Result.inserted
        totalSkippedDuplicate += mat3Result.skippedDuplicate
        totalDeactivatedLowQuality += mat3Result.deactivatedTooBasic
        totalParseErrors += mat3Result.parseErrors

        // Also import grade 4 math (mat4) packs
        val mat4Result = importMat4LgsPacksFromAssets(context)
        totalImported += mat4Result.inserted
        totalSkippedDuplicate += mat4Result.skippedDuplicate
        totalDeactivatedLowQuality += mat4Result.deactivatedTooBasic
        totalParseErrors += mat4Result.parseErrors

        // Also import grade 5 math (mat5) packs
        val mat5Result = importMat5LgsPacksFromAssets(context)
        totalImported += mat5Result.inserted
        totalSkippedDuplicate += mat5Result.skippedDuplicate
        totalDeactivatedLowQuality += mat5Result.deactivatedTooBasic
        totalParseErrors += mat5Result.parseErrors

        // Also import grade 7 math (mat7) packs
        val mat7Summary = importMat7LgsPacksFromAssets(context)
        totalImported += mat7Summary.importedCount
        totalSkippedDuplicate += mat7Summary.skippedDuplicateCount
        totalDeactivatedLowQuality += mat7Summary.deactivatedLowQualityCount
        totalParseErrors += mat7Summary.parseErrorCount

        // Also import grade 2 Turkish (turkce2) packs
        val turkce2Result = importTurkce2LgsPacksFromAssets(context)
        totalImported += turkce2Result.inserted
        totalSkippedDuplicate += turkce2Result.skippedDuplicate
        totalDeactivatedLowQuality += turkce2Result.deactivatedTooBasic
        totalParseErrors += turkce2Result.parseErrors

        // Also import grade 3 Turkish (turkce3) packs
        val turkce3Result = importTurkce3LgsPacksFromAssets(context)
        totalImported += turkce3Result.inserted
        totalSkippedDuplicate += turkce3Result.skippedDuplicate
        totalDeactivatedLowQuality += turkce3Result.deactivatedTooBasic
        totalParseErrors += turkce3Result.parseErrors

        // Also import grade 4 Turkish (turkce4) packs
        val turkce4Result = importTurkce4LgsPacksFromAssets(context)
        totalImported += turkce4Result.inserted
        totalSkippedDuplicate += turkce4Result.skippedDuplicate
        totalDeactivatedLowQuality += turkce4Result.deactivatedTooBasic
        totalParseErrors += turkce4Result.parseErrors

        // Also import grade 5 Turkish (turkce5) packs
        val turkce5Result = importTurkce5LgsPacksFromAssets(context)
        totalImported += turkce5Result.inserted
        totalSkippedDuplicate += turkce5Result.skippedDuplicate
        totalDeactivatedLowQuality += turkce5Result.deactivatedTooBasic
        totalParseErrors += turkce5Result.parseErrors

        // Also import grade 7 Turkish (turkce7) packs
        val turkce7Result = importTurkce7LgsPacksFromAssets(context)
        totalImported += turkce7Result.inserted
        totalSkippedDuplicate += turkce7Result.skippedDuplicate
        totalDeactivatedLowQuality += turkce7Result.deactivatedTooBasic
        totalParseErrors += turkce7Result.parseErrors

        // Also import grade 3 Science (fen3) packs
        val fen3Result = importFen3LgsPacksFromAssets(context)
        totalImported += fen3Result.inserted
        totalSkippedDuplicate += fen3Result.skippedDuplicate
        totalDeactivatedLowQuality += fen3Result.deactivatedTooBasic
        totalParseErrors += fen3Result.parseErrors

        // Also import grade 2 Life Studies (hayat2) packs
        val hayat2Result = importHayat2LgsPacksFromAssets(context)
        totalImported += hayat2Result.inserted
        totalSkippedDuplicate += hayat2Result.skippedDuplicate
        totalDeactivatedLowQuality += hayat2Result.deactivatedTooBasic
        totalParseErrors += hayat2Result.parseErrors

        // Also import grade 3 Life Studies (hayat3) packs
        val hayat3Result = importHayat3LgsPacksFromAssets(context)
        totalImported += hayat3Result.inserted
        totalSkippedDuplicate += hayat3Result.skippedDuplicate
        totalDeactivatedLowQuality += hayat3Result.deactivatedTooBasic
        totalParseErrors += hayat3Result.parseErrors

        // Also import grade 4 Science (fen4) packs
        val fen4Result = importFen4LgsPacksFromAssets(context)
        totalImported += fen4Result.inserted
        totalSkippedDuplicate += fen4Result.skippedDuplicate
        totalDeactivatedLowQuality += fen4Result.deactivatedTooBasic
        totalParseErrors += fen4Result.parseErrors

        // Also import grade 5 Science (fen5) packs
        val fen5Result = importFen5LgsPacksFromAssets(context)
        totalImported += fen5Result.inserted
        totalSkippedDuplicate += fen5Result.skippedDuplicate
        totalDeactivatedLowQuality += fen5Result.deactivatedTooBasic
        totalParseErrors += fen5Result.parseErrors

        // Also import grade 6 Science (fen6) packs
        val fen6Result = importFen6LgsPacksFromAssets(context)
        totalImported += fen6Result.inserted
        totalSkippedDuplicate += fen6Result.skippedDuplicate
        totalDeactivatedLowQuality += fen6Result.deactivatedTooBasic
        totalParseErrors += fen6Result.parseErrors

        // Also import grade 4 Social Studies (sosyal4) packs
        val sosyal4Result = importSosyal4LgsPacksFromAssets(context)
        totalImported += sosyal4Result.inserted
        totalSkippedDuplicate += sosyal4Result.skippedDuplicate
        totalDeactivatedLowQuality += sosyal4Result.deactivatedTooBasic
        totalParseErrors += sosyal4Result.parseErrors

        // Also import grade 5 Social Studies (sosyal5) packs
        val sosyal5Result = importSosyal5LgsPacksFromAssets(context)
        totalImported += sosyal5Result.inserted
        totalSkippedDuplicate += sosyal5Result.skippedDuplicate
        totalDeactivatedLowQuality += sosyal5Result.deactivatedTooBasic
        totalParseErrors += sosyal5Result.parseErrors

        // Also import grade 6 Social Studies (sosyal6) packs
        val sosyal6Result = importSosyal6LgsPacksFromAssets(context)
        totalImported += sosyal6Result.inserted
        totalSkippedDuplicate += sosyal6Result.skippedDuplicate
        totalDeactivatedLowQuality += sosyal6Result.deactivatedTooBasic
        totalParseErrors += sosyal6Result.parseErrors

        // Also import grade 7 Science (fen7) packs
        val fen7Result = importFen7LgsPacksFromAssets(context)
        totalImported += fen7Result.inserted
        totalSkippedDuplicate += fen7Result.skippedDuplicate
        totalDeactivatedLowQuality += fen7Result.deactivatedTooBasic
        totalParseErrors += fen7Result.parseErrors

        // Also import grade 7 İnkılap (inkilap7) packs
        val inkilap7Result = importInkilap7LgsPacksFromAssets(context)
        totalImported += inkilap7Result.inserted
        totalSkippedDuplicate += inkilap7Result.skippedDuplicate
        totalDeactivatedLowQuality += inkilap7Result.deactivatedTooBasic
        totalParseErrors += inkilap7Result.parseErrors

        // Also import grade 2 English (english2) packs
        val english2Result = importEnglish2LgsPacksFromAssets(context)
        totalImported += english2Result.inserted
        totalSkippedDuplicate += english2Result.skippedDuplicate
        totalDeactivatedLowQuality += english2Result.deactivatedTooBasic
        totalParseErrors += english2Result.parseErrors

        // Also import grade 3 English (english3) packs
        val english3Result = importEnglish3LgsPacksFromAssets(context)
        totalImported += english3Result.inserted
        totalSkippedDuplicate += english3Result.skippedDuplicate
        totalDeactivatedLowQuality += english3Result.deactivatedTooBasic
        totalParseErrors += english3Result.parseErrors

        // Also import grade 4 English (english4) packs
        val english4Result = importEnglish4LgsPacksFromAssets(context)
        totalImported += english4Result.inserted
        totalSkippedDuplicate += english4Result.skippedDuplicate
        totalDeactivatedLowQuality += english4Result.deactivatedTooBasic
        totalParseErrors += english4Result.parseErrors

        // Also import grade 5 English (english5) packs
        val english5Result = importEnglish5LgsPacksFromAssets(context)
        totalImported += english5Result.inserted
        totalSkippedDuplicate += english5Result.skippedDuplicate
        totalDeactivatedLowQuality += english5Result.deactivatedTooBasic
        totalParseErrors += english5Result.parseErrors

        // Also import grade 6 English (english6) packs
        val english6Result = importEnglish6LgsPacksFromAssets(context)
        totalImported += english6Result.inserted
        totalSkippedDuplicate += english6Result.skippedDuplicate
        totalDeactivatedLowQuality += english6Result.deactivatedTooBasic
        totalParseErrors += english6Result.parseErrors

        // Also import grade 7 English (english7) packs
        val english7Result = importEnglish7LgsPacksFromAssets(context)
        totalImported += english7Result.inserted
        totalSkippedDuplicate += english7Result.skippedDuplicate
        totalDeactivatedLowQuality += english7Result.deactivatedTooBasic
        totalParseErrors += english7Result.parseErrors

        // Also import grade 4 Din Kültürü (din4) packs
        val din4Result = importDin4LgsPacksFromAssets(context)
        totalImported += din4Result.inserted
        totalSkippedDuplicate += din4Result.skippedDuplicate
        totalDeactivatedLowQuality += din4Result.deactivatedTooBasic
        totalParseErrors += din4Result.parseErrors

        // Also import grade 5 Din Kültürü (din5) packs
        val din5Result = importDin5LgsPacksFromAssets(context)
        totalImported += din5Result.inserted
        totalSkippedDuplicate += din5Result.skippedDuplicate
        totalDeactivatedLowQuality += din5Result.deactivatedTooBasic
        totalParseErrors += din5Result.parseErrors

        // Also import grade 6 Din Kültürü (din6) packs
        val din6Result = importDin6LgsPacksFromAssets(context)
        totalImported += din6Result.inserted
        totalSkippedDuplicate += din6Result.skippedDuplicate
        totalDeactivatedLowQuality += din6Result.deactivatedTooBasic
        totalParseErrors += din6Result.parseErrors

        // Also import grade 7 Din Kültürü (din7) packs
        val din7Result = importDin7LgsPacksFromAssets(context)
        totalImported += din7Result.inserted
        totalSkippedDuplicate += din7Result.skippedDuplicate
        totalDeactivatedLowQuality += din7Result.deactivatedTooBasic
        totalParseErrors += din7Result.parseErrors

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

    private suspend fun runLgsImport(context: Context, json: String, forceSubject: String? = null, packName: String? = null, forceGrade: Int? = null): ImportResult = withContext(Dispatchers.IO) {
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
            val grade = forceGrade ?: LGS_GRADE
            val entity = parseLgsQuestion(qObj, i, packSubject, grade)
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

    /** Extracts image/visual asset path. Supports imageAsset, visualAsset, graphicAsset, tableAsset (first non-blank wins). */
    private fun optImageAsset(o: JSONObject): String? = sequenceOf("imageAsset", "visualAsset", "graphicAsset", "tableAsset")
        .mapNotNull { o.optString(it, "").takeIf { s -> s.isNotBlank() && s.lowercase() != "null" } }
        .firstOrNull()

    private fun parseLgsQuestion(o: JSONObject, index: Int, defaultSubject: String, defaultGrade: Int = LGS_GRADE): QuestionEntity? {
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
        val imageAsset = optImageAsset(o)
        val source = o.optString("source", "").takeIf { it.isNotBlank() }
        val sourceRef = o.optString("sourceRef", "").takeIf { it.isNotBlank() }

        val stemNorm = QuestionStemHash.normalizeStem(stem)
        val hash = QuestionStemHash.stemHash(stem)
        val id = o.optString("id", "").takeIf { it.isNotBlank() }
            ?: "lgs_${defaultGrade}_${subject}_${index}_${hash.take(8)}"

        return QuestionEntity(
            id = id,
            grade = defaultGrade,
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
        val imageAsset = optImageAsset(o)

        val subjectEnum = when (subject) {
            "mat" -> Subject.MAT
            "turkce" -> Subject.TURKCE
            "fen" -> Subject.FEN
            "sosyal" -> Subject.SOSYAL
            "hayat" -> Subject.HAYAT
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
            imageAsset = imageAsset,
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
        "hayat", "hayat bilgisi" -> "hayat"
        else -> "mat"
    }
}
