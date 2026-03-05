package com.brainbuddy.app.db

import java.security.MessageDigest
import java.nio.charset.Charset
import java.util.Locale

/**
 * Normalize and hash question stems for duplicate detection (büyük soru havuzu).
 */
object QuestionStemHash {

    /**
     * Normalized stem: lowercase (TR), collapse whitespace, replace numbers/some tokens.
     */
    fun normalizeStem(stem: String): String {
        var text = stem.replace(Regex("[\\p{Punct}]"), " ")
        val nameRegex = Regex("\\b[\\p{Lu}][\\p{Ll}]{2,}\\b")
        text = nameRegex.replace(text) { "@" }
        text = text.replace(Regex("\\d+"), "#")
        text = text.lowercase(Locale("tr"))
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
}
