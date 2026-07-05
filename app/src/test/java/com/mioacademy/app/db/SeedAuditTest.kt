package com.mioacademy.app.db

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

        // Run both:
        // - LEGACY: simulates the pre-fix behavior (template stemHash dedup + index-based fallback IDs)
        // - CURRENT: mirrors the active production seeder behavior (content dedup key + hash-based fallback IDs)
        val legacy = runAudit(assetsRoot, Mode.LEGACY)
        val current = runAudit(assetsRoot, Mode.CURRENT)
        println("SEED_AUDIT_BEFORE rawTotal=${legacy.totalRaw} insertedTotal=${legacy.totalInserted}")
        println("SEED_AUDIT_AFTER  rawTotal=${current.totalRaw} insertedTotal=${current.totalInserted}")
    }

    private enum class Mode { LEGACY, CURRENT }

    private data class AuditResult(
        val mode: Mode,
        val totalRaw: Int,
        val totalParsed: Int,
        val totalValid: Int,
        val totalDeduped: Int,
        val totalInMemDedupDrops: Int,
        val totalDbConflicts: Int,
        val totalInserted: Int,
        val lgs: SourceStats,
        val gradeBased: SourceStats,
        val packs: SourceStats
    )

    private fun runAudit(assetsRoot: File, mode: Mode): AuditResult {
        // IMPORTANT: source order must mirror DbSeeder.loadFromAssets:
        // root files -> packs -> grade_based -> lgs_exam -> synthetic
        // This order determines which item "wins" during in-memory distinctBy().
        val packsRoot = File(assetsRoot, "packs")
        val packs = auditPacks(packsRoot, mode)
        val gradeBased = auditGradeBased(File(assetsRoot, "grade_based"), mode)
        val lgs = auditLgsExam(File(assetsRoot, "lgs_exam"), mode)

        // Merge and compute dedup + simulated DB conflicts (by id).
        val all = mutableListOf<ParsedQuestion>()
        all += packs.second
        all += gradeBased.second
        all += lgs.second

        val totalRaw = lgs.first.bucket.rawQuestions + gradeBased.first.bucket.rawQuestions + packs.first.bucket.rawQuestions
        val totalParsed = lgs.first.bucket.parsedQuestions + gradeBased.first.bucket.parsedQuestions + packs.first.bucket.parsedQuestions
        val totalValid = lgs.first.bucket.validQuestions + gradeBased.first.bucket.validQuestions + packs.first.bucket.validQuestions

        val key = when (mode) {
            Mode.CURRENT -> {
                { q: ParsedQuestion ->
                    val exactStem = QuestionStemHash.normalizeStemExact(q.questionText)
                    val payload = exactStem + "\n" + q.optionsJson + "\n" + q.answerIndex
                    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
                    val contentHash = digest.joinToString("") { "%02x".format(it) }
                    "${q.grade}|${q.subject}|$contentHash"
                }
            }
            Mode.LEGACY -> {
                { q: ParsedQuestion ->
                    // pre-fix: template-normalized stem hash only
                    val h = QuestionStemHash.stemHash(q.questionText)
                    "${q.grade}|${q.subject}|$h"
                }
            }
        }

        // In-memory dedup (mirrors DbSeeder.questions.distinctBy(dedupKey)).
        val deduped = all.distinctBy(key)

        // Compute per-source in-memory dedup drops as: valid - keptAfterDedup.
        val keptAfterDedupBySource = deduped.groupingBy { it.source }.eachCount()
        fun kept(sourceName: String): Int = keptAfterDedupBySource[sourceName] ?: 0
        val keptPacks = kept("packs")
        val keptGradeBased = keptAfterDedupBySource.entries
            .filter { it.key.startsWith("grade_based") }
            .sumOf { it.value }
        val keptLgs = kept("lgs_exam")
        packs.first.bucket.inMemoryDedupDrops = packs.second.size - keptPacks
        gradeBased.first.bucket.inMemoryDedupDrops = gradeBased.second.size - keptGradeBased
        lgs.first.bucket.inMemoryDedupDrops = lgs.second.size - keptLgs

        // DB PK conflicts (Room INSERT IGNORE): only the FIRST occurrence of each id is inserted.
        val inserted = mutableListOf<ParsedQuestion>()
        val seenIds = HashSet<String>(deduped.size)
        for (q in deduped) {
            if (seenIds.add(q.id)) inserted += q
        }
        val dbConflicts = deduped.size - inserted.size

        // Per-source DB conflict ignored counts: keptAfterDedup - insertedFromSource.
        val insertedBySource = inserted.groupingBy { it.source }.eachCount()
        val insertedPacks = insertedBySource["packs"] ?: 0
        val insertedGradeBased = insertedBySource.entries
            .filter { it.key.startsWith("grade_based") }
            .sumOf { it.value }
        val insertedLgs = insertedBySource["lgs_exam"] ?: 0
        packs.first.bucket.dbConflictIgnored = keptPacks - insertedPacks
        gradeBased.first.bucket.dbConflictIgnored = keptGradeBased - insertedGradeBased
        lgs.first.bucket.dbConflictIgnored = keptLgs - insertedLgs

        // Final per-source inserted contribution.
        packs.first.bucket.finalInserted = insertedPacks
        gradeBased.first.bucket.finalInserted = insertedGradeBased
        lgs.first.bucket.finalInserted = insertedLgs

        val finalInserted = inserted.size

        val sumSourceInserted = packs.first.bucket.finalInserted + gradeBased.first.bucket.finalInserted + lgs.first.bucket.finalInserted
        val sumSourceDedupDrops = packs.first.bucket.inMemoryDedupDrops + gradeBased.first.bucket.inMemoryDedupDrops + lgs.first.bucket.inMemoryDedupDrops
        val sumSourceDbConflicts = packs.first.bucket.dbConflictIgnored + gradeBased.first.bucket.dbConflictIgnored + lgs.first.bucket.dbConflictIgnored

        // --- Mathematical consistency checks (hard fail if any mismatch) ---
        require(totalValid == deduped.size + sumSourceDedupDrops) {
            "Invariant failed: totalValid($totalValid) != deduped(${deduped.size}) + totalDedupDrops($sumSourceDedupDrops)"
        }
        require(deduped.size == finalInserted + dbConflicts) {
            "Invariant failed: deduped(${deduped.size}) != inserted($finalInserted) + dbConflicts($dbConflicts)"
        }
        require(finalInserted == sumSourceInserted) {
            "Invariant failed: inserted($finalInserted) != sumSourceInserted($sumSourceInserted)"
        }
        require(dbConflicts == sumSourceDbConflicts) {
            "Invariant failed: dbConflicts($dbConflicts) != sumSourceDbConflicts($sumSourceDbConflicts)"
        }
        require(packs.first.bucket.finalInserted <= packs.first.bucket.validQuestions)
        require(gradeBased.first.bucket.finalInserted <= gradeBased.first.bucket.validQuestions)
        require(lgs.first.bucket.finalInserted <= lgs.first.bucket.validQuestions)

        // Totals are printed in a mathematically consistent way:
        // totalValid == deduped + totalDedupDrops
        // deduped == finalInserted + dbConflicts
        // finalInserted == sum(source.finalInserted)
        println(
            "SEED_AUDIT_TOTAL mode=$mode " +
                "raw=$totalRaw parsed=$totalParsed valid=$totalValid " +
                "deduped=${deduped.size} inMemDedupDrops=$sumSourceDedupDrops " +
                "dbConflicts=$dbConflicts finalInserted=$finalInserted sumSourceInserted=$sumSourceInserted sumSourceDbConflicts=$sumSourceDbConflicts"
        )
        if (mode == Mode.CURRENT) {
            println("SEED_AUDIT_PACKS_ROOT ${packsRoot.path} exists=${packsRoot.exists()} filesOnDisk=${packsRoot.walkTopDown().count { it.isFile && it.extension.equals("json", true) }}")
            println("SEED_AUDIT_PACKS_DISCOVERED count=${packs.third.size}")
            packs.third.forEach { line -> println("SEED_AUDIT_PACKS_FILE $line") }
        }
        printSource(lgs.first)
        printSource(gradeBased.first)
        printSource(packs.first)
        return AuditResult(
            mode = mode,
            totalRaw = totalRaw,
            totalParsed = totalParsed,
            totalValid = totalValid,
            totalDeduped = deduped.size,
            totalInMemDedupDrops = sumSourceDedupDrops,
            totalDbConflicts = dbConflicts,
            totalInserted = finalInserted,
            lgs = lgs.first,
            gradeBased = gradeBased.first,
            packs = packs.first
        )
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

    private fun auditLgsExam(root: File, mode: Mode): Pair<SourceStats, List<ParsedQuestion>> {
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
                            sourceTag = "lgs_exam",
                            mode = mode
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

    private fun auditGradeBased(root: File, mode: Mode): Pair<SourceStats, List<ParsedQuestion>> {
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
                                sourceTag = "grade_based/${dir.name}",
                                mode = mode
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

    private fun auditPacks(root: File, mode: Mode): Triple<SourceStats, List<ParsedQuestion>, List<String>> {
        val stats = SourceStats("packs")
        val out = mutableListOf<ParsedQuestion>()
        val discoveredEvidence = mutableListOf<String>()
        if (!root.exists()) return Triple(stats, out, discoveredEvidence)

        // Mirror DbSeeder PACK_FILE_REGEX exactly.
        val packFileRegex = Regex("(?i)^grade(1|2|3|4|5|6|7)_(mat|turkce|fen|sosyal|ing)\\.json$")
        val discovered = root.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) && packFileRegex.matches(it.name) }
            .map { it.relativeTo(root.parentFile ?: root).invariantSeparatorsPath } // "packs/..."
            .sorted()
            .toList()
        discoveredEvidence += discovered.map { "path=$it" }

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
                // Evidence: per-file question count as parsed by production shape.
                if (mode == Mode.CURRENT && f.parentFile?.name.equals("packs", ignoreCase = true)) {
                    val shape = if (trimmed.startsWith("[")) "array" else "object"
                    discoveredEvidence += "file=${f.name} shape=$shape questionsLen=${arr.length()}"
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
                            sourceTag = "packs",
                            mode = mode
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
        return Triple(stats, out, discoveredEvidence)
    }

    private fun parseQuestionLikeSeeder(
        obj: JSONObject,
        index: Int,
        forcedGrade: Int?,
        forcedSubject: String?,
        sourceTag: String,
        mode: Mode
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
        val id = when (mode) {
            Mode.CURRENT -> explicitId ?: "${grade}_${subject}_${stemHash.take(16)}_${(index + 1).toString().padStart(4, '0')}"
            Mode.LEGACY -> explicitId ?: "${grade}_${subject}_${(index + 1).toString().padStart(6, '0')}"
        }
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

