package com.brainbuddy.app.db

import java.security.MessageDigest
import java.nio.charset.Charset
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
     * Hash for uniqueness/dedup that includes answer/options so variants don't collide.
     * Still uses normalized stem to collapse exact template copies.
     */
    fun stemHash(stem: String, options: List<String>, answerIndex: Int): String {
        val normalizedStem = normalizeStem(stem)
        val normalizedOptions = options.joinToString("|") { opt ->
            opt.lowercase(Locale("tr"))
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        val payload = normalizedStem + "||" + normalizedOptions + "||" + answerIndex.toString()
        val bytes = payload.toByteArray(Charset.forName("UTF-8"))
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
