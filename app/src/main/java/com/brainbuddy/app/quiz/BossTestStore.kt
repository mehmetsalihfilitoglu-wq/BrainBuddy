package com.brainbuddy.app.quiz

import android.content.Context

/** Every 10 levels (10, 20, 30...) requires a Boss test. */
class BossTestStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getPassedBossLevels(): Set<Int> {
        val s = prefs.getStringSet(KEY_PASSED, emptySet()) ?: emptySet()
        return s.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun markBossPassed(level: Int) {
        val set = getPassedBossLevels().toMutableSet()
        set.add(level)
        prefs.edit().putStringSet(KEY_PASSED, set.map { it.toString() }.toSet()).apply()
    }

    fun isBossLevel(level: Int): Boolean = level > 0 && level % 10 == 0

    fun isBossPassed(level: Int): Boolean = level in getPassedBossLevels()

    fun getNextBossLevel(currentLevel: Int): Int? {
        val next = ((currentLevel / 10) + 1) * 10
        return if (isBossLevel(next)) next else null
    }

    fun isBlockedFromLevelUp(currentLevel: Int): Boolean {
        val bossLevel = (currentLevel / 10) * 10
        if (bossLevel == 0) return false
        return currentLevel >= bossLevel && !isBossPassed(bossLevel)
    }

    companion object {
        private const val PREFS = "bb_boss_test"
        private const val KEY_PASSED = "passed_boss_levels"
    }
}
