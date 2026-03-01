package com.brainbuddy.app.junior

import android.content.Context
import com.brainbuddy.app.core.ProfileStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-profile Junior progress: learned letters, syllable success, words, daily minutes.
 */
class JuniorProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val profileStore = ProfileStore(context)

    private fun key(suffix: String): String = "profile_${profileStore.getCurrentProfileId()}_$suffix"

    fun getLearnedLetters(): Set<String> {
        val raw = prefs.getString(key(KEY_LEARNED_LETTERS), "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    fun addLearnedLetter(letter: String) {
        val set = getLearnedLetters().toMutableSet()
        set.add(letter)
        prefs.edit().putString(key(KEY_LEARNED_LETTERS), JSONArray(set.toList()).toString()).apply()
    }

    fun recordSyllableAttempt(correct: Boolean) {
        val attempts = prefs.getInt(key(KEY_SYLLABLE_ATTEMPTS), 0) + 1
        val correctCount = prefs.getInt(key(KEY_SYLLABLE_CORRECT), 0) + if (correct) 1 else 0
        prefs.edit()
            .putInt(key(KEY_SYLLABLE_ATTEMPTS), attempts)
            .putInt(key(KEY_SYLLABLE_CORRECT), correctCount)
            .apply()
    }

    fun getSyllableSuccessRate(): Float {
        val attempts = prefs.getInt(key(KEY_SYLLABLE_ATTEMPTS), 0)
        if (attempts == 0) return 0f
        return prefs.getInt(key(KEY_SYLLABLE_CORRECT), 0).toFloat() / attempts
    }

    fun addWordLearned(word: String) {
        val list = getWordsLearned().toMutableList()
        if (!list.contains(word)) list.add(word)
        prefs.edit().putString(key(KEY_WORDS_LEARNED), JSONArray(list).toString()).apply()
    }

    fun getWordsLearned(): List<String> {
        val raw = prefs.getString(key(KEY_WORDS_LEARNED), "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    fun getTodayMinutesUsed(): Int = prefs.getInt(key(KEY_TODAY_MINUTES), 0)

    fun addSessionMinutes(minutes: Int) {
        val dayKey = "day_${System.currentTimeMillis() / (24 * 60 * 60 * 1000)}"
        val lastDay = prefs.getString(key(KEY_LAST_DAY), "") ?: ""
        val current = if (dayKey == lastDay) prefs.getInt(key(KEY_TODAY_MINUTES), 0) else 0
        prefs.edit()
            .putInt(key(KEY_TODAY_MINUTES), current + minutes)
            .putString(key(KEY_LAST_DAY), dayKey)
            .apply()
    }

    fun getTodayLearned(): List<String> {
        val raw = prefs.getString(key(KEY_TODAY_LEARNED), "[]") ?: "[]"
        return try {
            val dayKey = "day_${System.currentTimeMillis() / (24 * 60 * 60 * 1000)}"
            val storedDay = prefs.getString(key(KEY_TODAY_LEARNED_DAY), "")
            if (dayKey != storedDay) return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    fun addTodayLearned(item: String) {
        val dayKey = "day_${System.currentTimeMillis() / (24 * 60 * 60 * 1000)}"
        val storedDay = prefs.getString(key(KEY_TODAY_LEARNED_DAY), "")
        val list = if (dayKey == storedDay) getTodayLearned().toMutableList() else mutableListOf<String>()
        if (!list.contains(item)) list.add(item)
        prefs.edit()
            .putString(key(KEY_TODAY_LEARNED), JSONArray(list).toString())
            .putString(key(KEY_TODAY_LEARNED_DAY), dayKey)
            .apply()
    }

    fun getStarsEarnedToday(): Int = prefs.getInt(key(KEY_STARS_TODAY), 0)

    fun addStars(count: Int) {
        val dayKey = "day_${System.currentTimeMillis() / (24 * 60 * 60 * 1000)}"
        val lastDay = prefs.getString(key(KEY_STARS_DAY), "")
        val current = if (dayKey == lastDay) prefs.getInt(key(KEY_STARS_TODAY), 0) else 0
        prefs.edit()
            .putInt(key(KEY_STARS_TODAY), current + count)
            .putString(key(KEY_STARS_DAY), dayKey)
            .apply()
    }

    fun getReportForProfile(profileId: String): JuniorReport {
        val p = "profile_${profileId}_"
        val letters = try {
            val raw = prefs.getString("${p}$KEY_LEARNED_LETTERS", "[]") ?: "[]"
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) { emptySet() }
        val attempts = prefs.getInt("${p}$KEY_SYLLABLE_ATTEMPTS", 0)
        val correct = prefs.getInt("${p}$KEY_SYLLABLE_CORRECT", 0)
        val words = try {
            val raw = prefs.getString("${p}$KEY_WORDS_LEARNED", "[]") ?: "[]"
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
        val todayMin = prefs.getInt("${p}$KEY_TODAY_MINUTES", 0)
        return JuniorReport(
            learnedLetters = letters,
            syllableAttempts = attempts,
            syllableCorrect = correct,
            wordsLearned = words,
            todayMinutesUsed = todayMin
        )
    }

    data class JuniorReport(
        val learnedLetters: Set<String>,
        val syllableAttempts: Int,
        val syllableCorrect: Int,
        val wordsLearned: List<String>,
        val todayMinutesUsed: Int
    )

    companion object {
        private const val PREFS = "bb_junior_progress"
        private const val KEY_LEARNED_LETTERS = "learned_letters"
        private const val KEY_SYLLABLE_ATTEMPTS = "syl_attempts"
        private const val KEY_SYLLABLE_CORRECT = "syl_correct"
        private const val KEY_WORDS_LEARNED = "words_learned"
        private const val KEY_TODAY_MINUTES = "today_min"
        private const val KEY_LAST_DAY = "last_day"
        private const val KEY_TODAY_LEARNED = "today_learned"
        private const val KEY_TODAY_LEARNED_DAY = "today_learned_day"
        private const val KEY_STARS_TODAY = "stars_today"
        private const val KEY_STARS_DAY = "stars_day"
    }
}
