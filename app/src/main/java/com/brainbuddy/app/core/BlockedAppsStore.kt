package com.brainbuddy.app.core

import android.content.Context

class BlockedAppsStore(context: Context) {
    private val prefs = ProfileScopedPrefs.blockedApps(context)

    fun getBlockedPackages(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED, emptySet())?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun setBlockedPackages(pkgs: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED, pkgs).apply()
    }

    fun isBlocked(pkg: String): Boolean {
        val trimmed = pkg.trim()
        if (trimmed.isEmpty() || trimmed.length > 256) return false
        return getBlockedPackages().contains(trimmed)
    }

    companion object {
        private const val KEY_BLOCKED = "blocked_packages"
    }
}