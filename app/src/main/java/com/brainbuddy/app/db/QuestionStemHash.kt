package com.brainbuddy.app.db

import java.security.MessageDigest
import java.nio.charset.Charset
import java.util.Locale

/**
 * Normalize and hash question stems for duplicate detection (büyük soru havuzu).
 *
 * stemNormalized: lowerCase(tr), noktalama/çoklu boşluk temizle,
 * sayıları <n>, kişi/şehir isimlerini <name> ile normalize eder.
 */
object QuestionStemHash {

    /**
     * Normalized stem: lowercase (TR), collapse whitespace, replace numbers with <n>,
     * person/city names with <name>.
     */
    fun normalizeStem(stem: String): String {
        var text = stem
            .replace(Regex("[\\p{Punct}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        // Kişi/şehir isimleri: büyük harfle başlayan kelimeler (Ayşe, Ali, Ankara)
        val nameRegex = Regex("\\b[\\p{Lu}][\\p{Ll}]{2,}\\b")
        text = nameRegex.replace(text) { "<name>" }
        // Sayıları normalize et (1, 2, 3 -> <n>)
        text = text.replace(Regex("\\d+"), "<n>")
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
