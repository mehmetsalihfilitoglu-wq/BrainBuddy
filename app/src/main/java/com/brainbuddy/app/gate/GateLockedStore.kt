package com.brainbuddy.app.gate

import android.content.Context
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ProfileScopedPrefs

/**
 * Per-package gate lock: when user fails gate quiz for a blocked app,
 * that package stays gate-required until they pass. Does NOT affect BrainBuddy.
 */
class GateLockedStore(context: Context) {
    private val prefs = ProfileScopedPrefs.gateLockedPackages(context)

    fun addGateLocked(pkg: String) {
        if (pkg.isBlank()) return
        val current = getGateLockedPackages().toMutableSet()
        current.add(pkg.trim())
        prefs.edit().putStringSet(KEY_GATE_LOCKED, current).apply()
    }

    fun removeGateLocked(pkg: String) {
        if (pkg.isBlank()) return
        val current = getGateLockedPackages().toMutableSet()
        current.remove(pkg.trim())
        prefs.edit().putStringSet(KEY_GATE_LOCKED, current).apply()
    }

    fun isGateLocked(pkg: String): Boolean {
        val trimmed = pkg.trim()
        if (trimmed.isEmpty()) return false
        return getGateLockedPackages().contains(trimmed)
    }

    fun getGateLockedPackages(): Set<String> =
        prefs.getStringSet(KEY_GATE_LOCKED, emptySet())
            ?.filter { it.isNotBlank() && it.length <= 256 }
            ?.toSet()
            ?: emptySet()

    /** Remove packages no longer in blocked list (cleanup). */
    fun pruneNonBlocked(context: Context) {
        val blocked = BlockedAppsStore(context).getBlockedPackages()
        val locked = getGateLockedPackages().filter { it in blocked }.toSet()
        if (locked.size != getGateLockedPackages().size) {
            prefs.edit().putStringSet(KEY_GATE_LOCKED, locked).apply()
        }
    }

    companion object {
        private const val KEY_GATE_LOCKED = "gate_locked_packages"
    }
}
