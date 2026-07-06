package com.mioacademy.app.content

/**
 * Remotely-managed content types. These are the objects a future Admin Panel / CMS
 * publishes so they can change without an app update. Kept as plain, transport-shaped
 * models decoupled from the app's existing domain classes, so a CMS adapter can map
 * onto them freely.
 */

data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val startsAtMs: Long,
    val endsAtMs: Long,
    val ctaLabel: String? = null,
    val ctaUrl: String? = null
)

data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val publishedAtMs: Long,
    val imageUrl: String? = null
)

data class PremiumBanner(
    val id: String,
    val headline: String,
    val subtext: String,
    val variant: String
)

data class ScholarshipInfo(
    val id: String,
    val name: String,
    val provider: String,
    val country: String,
    val amount: String,
    val deadline: String,
    val url: String? = null
)
