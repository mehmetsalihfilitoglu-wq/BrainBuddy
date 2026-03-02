package com.brainbuddy.app.core

import android.content.Context
import android.content.pm.PackageManager

/**
 * Social media preset for one-tap block. Uses PackageManager to resolve only installed apps.
 * Never crashes if package not found.
 */
object SocialPresetPackages {

    /** Instagram, TikTok, YouTube, Facebook + optional Snapchat, X/Twitter */
    val packageNames: Set<String> = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.snapchat.android",
        "com.twitter.android",
        "com.x.android" // X (Twitter rebrand on some devices)
    )

    /**
     * Returns only installed packages from the social preset, with launcher intent.
     * Safe to call; never crashes. Small set (~7) so main-thread is fine.
     */
    fun getInstalledSocialPackages(context: Context): Set<String> {
        return runCatching {
            val pm = context.packageManager ?: return emptySet()
            val ownPkg = context.packageName
            packageNames.filter { pkg ->
                pkg != ownPkg && isInstalledWithLauncher(pm, pkg)
            }.toSet()
        }.getOrElse { emptySet() }
    }

    private fun isInstalledWithLauncher(pm: PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0)
            pm.getLaunchIntentForPackage(pkg) != null
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /** Get app label for display; returns package name on error. */
    fun getAppLabel(context: Context, pkg: String): String {
        return try {
            val ai = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }
    }
}
