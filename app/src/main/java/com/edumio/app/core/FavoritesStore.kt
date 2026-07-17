package com.edumio.app.core

import android.content.Context

/**
 * Account-level favourites for university discovery (Keşfet): saved universities and
 * programs. Global (not study-area scoped) — a student's shortlist spans exams.
 *
 * A real, ready-to-wire store: the discovery screens toggle favourites here and the
 * set syncs across devices via [com.edumio.app.sync.SyncRegistry].
 */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun favoriteUniversities(): Set<String> = prefs.getStringSet(KEY_UNIS, emptySet()) ?: emptySet()
    fun favoritePrograms(): Set<String> = prefs.getStringSet(KEY_PROGRAMS, emptySet()) ?: emptySet()

    fun isFavoriteUniversity(id: String): Boolean = id in favoriteUniversities()
    fun isFavoriteProgram(id: String): Boolean = id in favoritePrograms()

    /** Toggles and returns the new state (true = now favourite). */
    fun toggleUniversity(id: String): Boolean = toggle(KEY_UNIS, id)
    fun toggleProgram(id: String): Boolean = toggle(KEY_PROGRAMS, id)

    private fun toggle(key: String, id: String): Boolean {
        val set = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        val nowFavorite = if (id in set) { set.remove(id); false } else { set.add(id); true }
        prefs.edit().putStringSet(key, set).apply()
        return nowFavorite
    }

    companion object {
        private const val PREFS = "bb_favorites"
        private const val KEY_UNIS = "favorite_universities"
        private const val KEY_PROGRAMS = "favorite_programs"
    }
}
