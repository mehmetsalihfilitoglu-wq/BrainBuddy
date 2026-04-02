package com.brainbuddy.app.db

import java.security.MessageDigest
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Normalize and hash question stems for duplicate detection.
 * Catches template questions like "Ali'nin 3 kalemi vardır" vs "Ayşe'nin 3 kalemi vardır".
 *
 * normalizeStem: lowercase (TR), remove punctuation, collapse spaces,
 * replace numbers with "#", replace common Turkish names with "NAME",
 * replace years/dates with "#".
 */
object QuestionStemHash {

    /** Common Turkish first names – normalize to NAME to catch template duplicates. */
    private val TURKISH_NAMES = setOf(
        "ali", "ayşe", "mehmet", "ahmet", "zeynep", "fatma", "hasan", "mustafa",
        "emre", "elif", "ömer", "kaan", "derya", "selin", "burak", "cem",
        "oya", "can", "ece", "deniz", "merve", "berkay", "sude", "emir",
        "ipek", "yusuf", "irem", "arda", "aslı", "onur", "büşra", "kerem"
    )

    /**
     * Normalized stem for template duplicate detection.
     * 1) lowercase (Turkish locale safe)
     * 2) remove punctuation
     * 3) collapse multiple spaces
     * 4) replace numbers with "#"
     * 5) replace common Turkish names with "NAME"
     * 6) replace years (e.g. 2020, 1999) and date fragments with "#"
     */
    fun normalizeStem(stem: String): String {
        var text = stem
        text = text.lowercase(Locale("tr"))
        text = text.replace(Regex("[\\p{Punct}]"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()
        text = text.replace(Regex("\\d+"), "#")
        for (name in TURKISH_NAMES) {
            text = Regex("\\b${Regex.escape(name)}\\b").replace(text, "NAME")
        }
        text = text.replace(Regex("\\s+"), " ").trim()
        return text
    }

    /** SHA-256 hash of normalized stem. */
    fun stemHash(stem: String): String {
        val normalized = normalizeStem(stem)
        val bytes = normalized.toByteArray(Charset.forName("UTF-8"))
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Strict/exact normalization for true-duplicate detection.
     * Intentionally does NOT replace numbers/names; only collapses whitespace + lowercases.
     */
    fun normalizeStemExact(stem: String): String {
        return stem
            .lowercase(Locale("tr"))
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** SHA-256 hash of normalizeStemExact(stem). */
    fun stemHashExact(stem: String): String {
        val normalized = normalizeStemExact(stem)
        val bytes = normalized.toByteArray(Charset.forName("UTF-8"))
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Exact-content duplicate key: stem (exact norm) + full options JSON + answer index.
     * Used for general seed distinctBy and for pack import duplicate detection.
     */
    fun contentDedupKey(
        grade: Int,
        subject: String,
        questionText: String,
        optionsJson: String,
        answerIndex: Int
    ): String {
        val exactStem = normalizeStemExact(questionText)
        val payload = buildString {
            append(exactStem)
            append('\n')
            append(optionsJson)
            append('\n')
            append(answerIndex)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8))
        val contentHash = digest.joinToString("") { "%02x".format(it) }
        return "$grade|$subject|$contentHash"
    }

    fun contentDedupKey(entity: QuestionEntity): String =
        contentDedupKey(entity.grade, entity.subject, entity.questionText, entity.optionsJson, entity.answerIndex)

    /**
     * Lightweight content fingerprint for runtime repeat detection.
     * Uses stem + choices + correctIndex to catch duplicate content
     * even when question IDs differ.
     */
    fun contentFingerprint(stem: String, choices: List<String>, correctIndex: Int): String {
        val normalized = normalizeStemExact(stem)
        val payload = buildString {
            append(normalized)
            append('\n')
            choices.forEach { append(it.trim()); append('|') }
            append('\n')
            append(correctIndex)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
