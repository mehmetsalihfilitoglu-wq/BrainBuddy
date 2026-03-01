package com.brainbuddy.app.core

import android.content.Context
import android.content.pm.PackageManager

/**
 * Preset app groups: Social Media, Games, Browsers.
 * Detects installed apps for each group.
 */
object AppGroupPresets {

    val socialPackages: Set<String> = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.zhiliaoapp.musically", // TikTok
        "com.google.android.youtube",
        "com.snapchat.android",
        "com.twitter.android",
        "org.telegram.messenger",
        "com.whatsapp"
    )

    val gamesPackages: Set<String> = setOf(
        "com.king.candycrushsaga",
        "com.supercell.clashofclans",
        "com.roblox.client",
        "com.mojang.minecraftpe",
        "com.epicgames.fortnite",
        "com.pubg.krmobile",
        "com.innersloth.spacemafia",
        "com.tencent.ig"
    )

    val browsersPackages: Set<String> = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "org.mozilla.fennec_fdroid",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser",
        "com.brave.browser"
    )

    fun getInstalledFromGroup(context: Context, group: String): Set<String> {
        val pm = context.packageManager
        val packages = when (group) {
            "social" -> socialPackages
            "games" -> gamesPackages
            "browsers" -> browsersPackages
            else -> emptySet()
        }
        return packages.filter { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                pm.getLaunchIntentForPackage(pkg) != null
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
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
