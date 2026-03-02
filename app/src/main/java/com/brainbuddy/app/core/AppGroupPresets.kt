package com.brainbuddy.app.core

import android.content.Context
import android.content.pm.PackageManager

/**
 * Preset app groups: Social Media, Games, Browsers.
 * Uses installed apps list only - no hardcoded package names.
 * Categories return empty when no preset matching is available.
 */
object AppGroupPresets {

    /** Installed packages matching "social" - none without hardcoded list. Use getInstalledApps. */
    val socialPackages: Set<String> get() = emptySet()

    /** Installed packages matching "games" - none without hardcoded list. Use getInstalledApps. */
    val gamesPackages: Set<String> get() = emptySet()

    /** Installed packages matching "browsers" - none without hardcoded list. Use getInstalledApps. */
    val browsersPackages: Set<String> get() = emptySet()

    fun getInstalledFromGroup(context: Context, group: String): Set<String> {
        val pm = context.packageManager
        val packages = when (group) {
            "social" -> socialPackages
            "games" -> gamesPackages
            "browsers" -> browsersPackages
            else -> emptySet()
        }
        return packages.filter { pkg ->
            InstalledAppsHelper.isInstalledWithLauncher(pm, pkg)
        }.toSet()
    }

    fun selectGroup(packages: Set<String>, group: String): Set<String> {
        val groupSet = when (group) {
            "social" -> socialPackages
            "games" -> gamesPackages
            "browsers" -> browsersPackages
            else -> emptySet()
        }
        return packages.union(groupSet)
    }
}
