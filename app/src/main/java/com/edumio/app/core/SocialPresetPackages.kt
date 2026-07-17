package com.edumio.app.core

import android.content.Context
import android.content.pm.PackageManager

/**
 * Social media preset for one-tap block.
 * Uses installed apps only - no hardcoded package names. Never crashes.
 */
object SocialPresetPackages {

    /** No hardcoded package names. Use getInstalledSocialPackages for dynamic list. */
    val packageNames: Set<String> get() = emptySet()

    /**
     * Returns installed apps for social preset.
     * No hardcoded package names - returns empty. User selects from full app list via chip "All".
     */
    fun getInstalledSocialPackages(@Suppress("UNUSED_PARAMETER") context: Context): Set<String> {
        return emptySet()
    }

    /** Get app label for display; returns package name on error. Never crashes. */
    fun getAppLabel(context: Context, pkg: String): String {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        } catch (_: Exception) {
            pkg
        }
    }
}
