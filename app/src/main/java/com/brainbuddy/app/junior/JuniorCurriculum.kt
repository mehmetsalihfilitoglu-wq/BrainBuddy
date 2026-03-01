package com.brainbuddy.app.junior

import android.content.Context
import org.json.JSONObject
import java.io.InputStreamReader

/**
 * Turkish phonics curriculum (Ses Temelli). Configurable letter order and content.
 */
object JuniorCurriculum {

    private var cached: CurriculumData? = null

    fun load(context: Context): CurriculumData {
        cached?.let { return it }
        return try {
            context.assets.open("junior_curriculum.json").use { stream ->
                val json = InputStreamReader(stream).readText()
                val obj = JSONObject(json)
                val letterOrder = mutableListOf<String>()
                val arr = obj.getJSONArray("letterOrder")
                for (i in 0 until arr.length()) letterOrder.add(arr.getString(i))
                val letterNames = mutableMapOf<String, String>()
                val names = obj.optJSONObject("letterNames") ?: JSONObject()
                for (k in names.keys()) letterNames[k] = names.getString(k)
                val syllables = mutableListOf<Syllable>()
                val sylArr = obj.optJSONArray("syllables") ?: org.json.JSONArray()
                for (i in 0 until sylArr.length()) {
                    val a = sylArr.getJSONArray(i)
                    syllables.add(Syllable(a.getString(0), a.getString(1), a.getString(2)))
                }
                val words = mutableListOf<CurriculumWord>()
                val wordArr = obj.optJSONArray("words") ?: org.json.JSONArray()
                for (i in 0 until wordArr.length()) {
                    val w = wordArr.getJSONObject(i)
                    words.add(CurriculumWord(w.getString("word"), w.optString("meaning", w.getString("word"))))
                }
                CurriculumData(letterOrder, letterNames, syllables, words).also { cached = it }
            }
        } catch (e: Exception) {
            CurriculumData(DEFAULT_LETTERS, DEFAULT_NAMES, emptyList(), DEFAULT_WORDS)
        }
    }

    data class CurriculumData(
        val letterOrder: List<String>,
        val letterNames: Map<String, String>,
        val syllables: List<Syllable>,
        val words: List<CurriculumWord>
    )

    data class Syllable(val first: String, val second: String, val result: String)

    data class CurriculumWord(val word: String, val meaning: String)

    private val DEFAULT_LETTERS = listOf("E", "L", "A", "K", "İ", "N", "O", "M", "U", "T", "Ü", "Y", "Ö", "R", "I", "D", "S", "B", "Z", "Ç", "G", "Ş", "C", "P", "H", "V", "Ğ", "F", "J")

    private val DEFAULT_NAMES = mapOf(
        "E" to "e", "L" to "el", "A" to "a", "K" to "ke", "İ" to "i", "N" to "ne",
        "O" to "o", "M" to "me", "U" to "u", "T" to "te", "Ü" to "ü", "Y" to "ye",
        "Ö" to "ö", "R" to "re", "I" to "ı", "D" to "de", "S" to "se", "B" to "be"
    )

    private val DEFAULT_WORDS = listOf(
        CurriculumWord("EL", "el"), CurriculumWord("ELMA", "elma"), CurriculumWord("ANNE", "anne"),
        CurriculumWord("AL", "al"), CurriculumWord("EK", "ek"), CurriculumWord("KALE", "kale")
    )
}
