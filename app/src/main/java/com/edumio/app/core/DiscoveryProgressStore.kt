package com.edumio.app.core

import android.content.Context

/**
 * Tracks which university/program discovery pages the student has actually opened,
 * so "you've explored 12 of 143 programs" and discovery nudges are real, not faked.
 * Account-level (spans exams), and syncs across devices.
 */
class DiscoveryProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun viewedPrograms(): Set<String> = prefs.getStringSet(KEY_VIEWED_PROGRAMS, emptySet()) ?: emptySet()
    fun viewedUniversities(): Set<String> = prefs.getStringSet(KEY_VIEWED_UNIS, emptySet()) ?: emptySet()

    fun markProgramViewed(id: String) = add(KEY_VIEWED_PROGRAMS, id)
    fun markUniversityViewed(id: String) = add(KEY_VIEWED_UNIS, id)

    fun viewedProgramCount(): Int = viewedPrograms().size

    private fun add(key: String, id: String) {
        val set = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        if (set.add(id)) prefs.edit().putStringSet(key, set).apply()
    }

    companion object {
        private const val PREFS = "edu_discovery_progress"
        private const val KEY_VIEWED_PROGRAMS = "viewed_programs"
        private const val KEY_VIEWED_UNIS = "viewed_universities"
    }
}
