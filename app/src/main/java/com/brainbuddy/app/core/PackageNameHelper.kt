package com.brainbuddy.app.core

/**
 * Maps package names to friendly display names for blocked app attempt reports.
 * No raw package IDs shown to parents.
 */
object PackageNameHelper {

    private val knownPackages = mapOf(
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.google.android.youtube" to "YouTube",
        "com.snapchat.android" to "Snapchat",
        "com.twitter.android" to "X (Twitter)",
        "org.telegram.messenger" to "Telegram",
        "com.whatsapp" to "WhatsApp"
    )

    fun getFriendlyName(pkg: String): String = knownPackages[pkg] ?: pkg.takeLast(20)
}
