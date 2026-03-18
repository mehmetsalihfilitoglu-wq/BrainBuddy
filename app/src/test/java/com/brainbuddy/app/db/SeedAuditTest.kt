package com.brainbuddy.app.db

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Offline seed audit that reads the real assets folder from the repo (no Android Context needed).
 *
 * It mirrors the active seeding rules:
 * - lgs_exam: wrapped { questions: [...] } (treated as LGS)
 * - grade_based: wrapped { questions: [...] } (treated as GENERAL with grade derived from folder suffix 1..7)
 * - packs: optional (array or wrapped), but current repo packs are empty placeholders
 *
 * This test prints mandatory accounting buckets so we can see where questions are lost.
 */
class SeedAuditTest {

    private data class Bucket(
        var rawQuestions: Int = 0,
        var parsedQuestions: Int = 0,
        var validQuestions: Int = 0,
        var droppedInvalidGrade: Int = 0,
        var droppedInvalidSubject: Int = 0,
        var droppedMissingFields: Int = 0,
        var parseErrors: Int = 0,
        var unsupportedFormat: Int = 0,
        var inMemoryDedupDrops: Int = 0,
        var dbConflictIgnored: Int = 0,
        var finalInserted: Int = 0
    )

    private data class SourceStats(
        val name: String,
        val bucket: Bucket = Bucket()
    )

    private data class ParsedQuestion(
        val source: String,
        val id: String,
        val grade: Int,
        val subject: String,
        val questionText: String,
        val optionsJson: String,
        val answerIndex: Int
    )

    private val subjectNormalize: (String) -> String? = { raw ->
        when (raw.trim().lowercase()) {
            "mat", "matematik", "math" -> "mat"
            "turkce", "türkçe", "tr" -> "turkce"
            "fen", "fen bilimleri" -> "fen"
            "sosyal", "sosyal bilgiler" -> "sosyal"
            "ing", "ingilizce", "ingilizce dersi", "english", "eng" -> "ing"
            "din", "din kültürü", "din kültürü ve ahlak bilgisi" -> "din"
            "inkilap", "inkılap", "tc inkılap tarihi" -> "inkilap"
            "hayat", "hayat bilgisi" -> "hayat"
            else -> null
        }
    }

    @Test
    fun printSeedAudit() {
        val assetsRoot = resolveAssetsRoot()
        require(assetsRoot.exists()) { "assets root not found: ${assetsRoot.absolutePath}" }

        val lgs = auditLgsExam(File(assetsRoot, "lgs_exam"))
        val gradeBased = auditGradeBased(File(assetsRoot, "grade_based"))
        val packs = auditPacks(File(assetsRoot, "packs"))

        // Merge and compute dedup + simulated DB conflicts (by id).
        val all = mutableListOf<ParsedQuestion>()
        all += lgs.second
        all += gradeBased.second
        all += packs.second

        val totalRaw = lgs.first.bucket.rawQuestions + gradeBased.first.bucket.rawQuestions + packs.first.bucket.rawQuestions
        val totalParsed = lgs.first.bucket.parsedQuestions + gradeBased.first.bucket.parsedQuestions + packs.first.bucket.parsedQuestions
        val totalValid = lgs.first.bucket.validQuestions + gradeBased.first.bucket.validQuestions + packs.first.bucket.validQuestions

        val deduped = run {
            val key = { q: ParsedQuestion ->
                val exactStem = QuestionStemHash.normalizeStemExact(q.questionText)
                val payload = exactStem + "\n" + q.optionsJson + "\n" + q.answerIndex
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
                val contentHash = digest.joinToString("") { "%02x".format(it) }
                "${q.grade}|${q.subject}|$contentHash"
            }
            val distinct = all.distinctBy(key)
            // Attribute in-memory dedup drops per source (distinctBy keeps the first occurrence).
            val keptLgs = distinct.count { it.source == "lgs_exam" }
            val keptGradeBased = distinct.count { it.source.startsWith("grade_based") }
            val keptPacks = distinct.count { it.source == "packs" }
            lgs.first.bucket.inMemoryDedupDrops = lgs.second.size - keptLgs
            gradeBased.first.bucket.inMemoryDedupDrops = gradeBased.second.size - keptGradeBased
            packs.first.bucket.inMemoryDedupDrops = packs.second.size - keptPacks
            distinct
        }

        // DB conflicts (Room INSERT IGNORE): primary key collisions by id.
        val idCounts = deduped.groupingBy { it.id }.eachCount()
        val idConflicts = idCounts.values.sumOf { (it - 1).coerceAtLeast(0) }

        fun conflictsFor(source: List<ParsedQuestion>): Int {
            return source.count { (idCounts[it.id] ?: 0) > 1 }
        }

        lgs.first.bucket.dbConflictIgnored = conflictsFor(lgs.second)
        gradeBased.first.bucket.dbConflictIgnored = conflictsFor(gradeBased.second)
        packs.first.bucket.dbConflictIgnored = conflictsFor(packs.second)

        val finalInserted = deduped.size - idConflicts
        lgs.first.bucket.finalInserted = lgs.second.size - lgs.first.bucket.dbConflictIgnored
        gradeBased.first.bucket.finalInserted = gradeBased.second.size - gradeBased.first.bucket.dbConflictIgnored
        packs.first.bucket.finalInserted = packs.second.size - packs.first.bucket.dbConflictIgnored

        println("SEED_AUDIT_TOTAL raw=$totalRaw parsed=$totalParsed valid=$totalValid deduped=${deduped.size} dbConflicts=$idConflicts finalInserted=$finalInserted")
        printSource(lgs.first)
        printSource(gradeBased.first)
        printSource(packs.first)
    }

    private fun resolveAssetsRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        // Gradle unit tests may run with user.dir = repo root OR module dir (".../app").
        val candidates = listOf(
            File(cwd, "app/src/main/assets"),
            File(cwd, "src/main/assets"),
            File(cwd.parentFile ?: cwd, "app/src/main/assets"),
            File(cwd.parentFile ?: cwd, "src/main/assets")
        )
        return candidates.firstOrNull { it.exists() }
            ?: candidates.first() // fallback for error message path
    }

    private fun printSource(s: SourceStats) {
        val b = s.bucket
        println(
            "SEED_AUDIT_SOURCE ${s.name} " +
                "raw=${b.rawQuestions} parsed=${b.parsedQuestions} valid=${b.validQuestions} " +
                "dropInvalidGrade=${b.droppedInvalidGrade} dropInvalidSubject=${b.droppedInvalidSubject} dropMissingFields=${b.droppedMissingFields} " +
                "parseErrors=${b.parseErrors} unsupportedFormat=${b.unsupportedFormat} inMemDedupDrops=${b.inMemoryDedupDrops} " +
                "dbConflictIgnored=${b.dbConflictIgnored} finalInserted=${b.finalInserted}"
        )
    }

    private fun auditLgsExam(root: File): Pair<SourceStats, List<ParsedQuestion>> {
        val stats = SourceStats("lgs_exam")
        val out = mutableListOf<ParsedQuestion>()
        if (!root.exists()) return stats to out

        val files = root.walkTopDown().filter { it.isFile && it.extension.equals("json", ignoreCase = true) }.toList()
        for (f in files) {
            val raw = f.readText(StandardCharsets.UTF_8)
            val trimmed = raw.trimStart()
            try {
                val arr = when {
                    trimmed.startsWith("{") -> JSONObject(raw).optJSONArray("questions")
                    trimmed.startsWith("[") -> JSONArray(raw)
                    else -> null
                }
                if (arr == null) {
                    stats.bucket.unsupportedFormat++
                    continue
                }
                stats.bucket.rawQuestions += arr.length()
                for (i in 0 until arr.length()) {
                    try {
                        val o = arr.getJSONObject(i)
                        stats.bucket.parsedQuestions++
                        val parsed = parseQuestionLikeSeeder(
                            obj = o,
                            index = i,
                            forcedGrade = 7, // seeder placeholder for LGS root
                            forcedSubject = null,
                            sourceTag = "lgs_exam"
                        )
                        if (parsed != null) {
                            stats.bucket.validQuestions++
                            out += parsed
                        }
                    } catch (_: org.json.JSONException) {
                        stats.bucket.parseErrors++
                    }
                }
            } catch (_: Exception) {
                stats.bucket.parseErrors++
            }
        }
        return stats to out
    }

    private fun auditGradeBased(root: File): Pair<SourceStats, List<ParsedQuestion>> {
        val stats = SourceStats("grade_based")
        val out = mutableListOf<ParsedQuestion>()
        if (!root.exists()) return stats to out

        val dirRegex = Regex("(?i)^(hayat|mat|fen|turkce|english|din|sosyal|inkilap)([1-7])$")
        val gradeDirs = root.listFiles()?.filter { it.isDirectory && dirRegex.matches(it.name) }.orEmpty()
        for (dir in gradeDirs) {
            val m = dirRegex.find(dir.name) ?: continue
            val subjectPart = m.groupValues[1].lowercase()
            val grade = m.groupValues[2].toInt()
            val subject = when (subjectPart) {
                "english" -> "ing"
                else -> subjectPart
            }
            if (grade !in 1..7) {
                stats.bucket.droppedInvalidGrade++
                continue
            }

            val files = dir.listFiles()?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }.orEmpty()
            for (f in files) {
                val raw = f.readText(StandardCharsets.UTF_8)
                val trimmed = raw.trimStart()
                try {
                    val arr = when {
                        trimmed.startsWith("{") -> JSONObject(raw).optJSONArray("questions")
                        trimmed.startsWith("[") -> JSONArray(raw)
                        else -> null
                    }
                    if (arr == null) {
                        stats.bucket.unsupportedFormat++
                        continue
                    }
                    stats.bucket.rawQuestions += arr.length()
                    for (i in 0 until arr.length()) {
                        try {
                            val o = arr.getJSONObject(i)
                            stats.bucket.parsedQuestions++
                            val parsed = parseQuestionLikeSeeder(
                                obj = o,
                                index = i,
                                forcedGrade = grade,
                                forcedSubject = subject,
                                sourceTag = "grade_based/${dir.name}"
                            )
                            if (parsed != null) {
                                stats.bucket.validQuestions++
                                out += parsed
                            }
                        } catch (_: org.json.JSONException) {
                            stats.bucket.parseErrors++
                        }
                    }
                } catch (_: Exception) {
                    stats.bucket.parseErrors++
                }
            }
        }
        return stats to out
    }

    private fun auditPacks(root: File): Pair<SourceStats, List<ParsedQuestion>> {
        val stats = SourceStats("packs")
        val out = mutableListOf<ParsedQuestion>()
        if (!root.exists()) return stats to out

        val files = root.walkTopDown().filter { it.isFile && it.extension.equals("json", ignoreCase = true) }.toList()
        for (f in files) {
            val raw = f.readText(StandardCharsets.UTF_8)
            val trimmed = raw.trimStart()
            try {
                val arr = when {
                    trimmed.startsWith("{") -> JSONObject(raw).optJSONArray("questions")
                    trimmed.startsWith("[") -> JSONArray(raw)
                    else -> null
                }
                if (arr == null) {
                    stats.bucket.unsupportedFormat++
                    continue
                }
                stats.bucket.rawQuestions += arr.length()
                for (i in 0 until arr.length()) {
                    try {
                        val o = arr.getJSONObject(i)
                        stats.bucket.parsedQuestions++
                        val parsed = parseQuestionLikeSeeder(
                            obj = o,
                            index = i,
                            forcedGrade = null,
                            forcedSubject = null,
                            sourceTag = "packs"
                        )
                        if (parsed != null) {
                            stats.bucket.validQuestions++
                            out += parsed
                        }
                    } catch (_: org.json.JSONException) {
                        stats.bucket.parseErrors++
                    }
                }
            } catch (_: Exception) {
                stats.bucket.parseErrors++
            }
        }
        return stats to out
    }

    private fun parseQuestionLikeSeeder(
        obj: JSONObject,
        index: Int,
        forcedGrade: Int?,
        forcedSubject: String?,
        sourceTag: String
    ): ParsedQuestion? {
        // grade (force when provided, else mimic DbSeeder's "8 -> 6, else default 6")
        val gradeFromJson = when {
            obj.has("grade") -> obj.optInt("grade", 0)
            obj.has("grade_level") -> obj.optInt("grade_level", 0)
            else -> 0
        }
        val grade = forcedGrade ?: when {
            gradeFromJson in 1..7 -> gradeFromJson
            gradeFromJson == 8 -> 6
            else -> 6
        }
        if (grade !in 1..7) return null

        val subjRaw = forcedSubject ?: obj.optString("subject", "")
        val subject = subjectNormalize(subjRaw) ?: return null

        val questionText = obj.optString("stem", "").ifBlank { obj.optString("questionText", "") }
            .ifBlank { obj.optString("question", "") }
            .ifBlank { return null }

        val optionsArray = when {
            obj.has("options") -> obj.optJSONArray("options")
            else -> obj.optJSONArray("choices")
        } ?: return null
        val rawOptions = (0 until optionsArray.length())
            .map { idx ->
                optionsArray.optString(idx, "").ifEmpty { optionsArray.opt(idx)?.toString() ?: "" }.trim()
            }
            .filter { it.isNotBlank() }
        if (rawOptions.size < 2) return null

        val padded = if (rawOptions.size >= 4) rawOptions.take(4) else rawOptions + List(4 - rawOptions.size) { "-" }
        val optionsJson = JSONArray(padded).toString()

        val rawAnswerIndex = if (obj.has("answerIndex")) obj.optInt("answerIndex", 0) else obj.optInt("correctIndex", 0)
        val answerIndex = rawAnswerIndex.coerceIn(0, padded.size - 1)

        val stemHash = QuestionStemHash.stemHash(questionText)

        val explicitId = obj.optString("id", "").takeIf { it.isNotBlank() }
        val id = explicitId ?: "${grade}_${subject}_${stemHash.take(16)}_${(index + 1).toString().padStart(4, '0')}"
        return ParsedQuestion(
            source = sourceTag,
            id = id,
            grade = grade,
            subject = subject,
            questionText = questionText,
            optionsJson = optionsJson,
            answerIndex = answerIndex
        )
    }
}

