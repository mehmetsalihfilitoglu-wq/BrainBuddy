package com.brainbuddy.app.core

import android.content.Context

class BlockedAppsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getBlockedPackages(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED, emptySet())?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun setBlockedPackages(pkgs: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED, pkgs).apply()
    }

    fun isBlocked(pkg: String): Boolean =
        pkg.isNotBlank() && getBlockedPackages().contains(pkg.trim())

    companion object {
        private const val PREFS = "bb_blocked_apps"
        private const val KEY_BLOCKED = "blocked_packages"
    }
}