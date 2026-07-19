package com.edumio.app.legal

import com.edumio.app.BuildConfig
import com.edumio.app.R

/**
 * Central configuration for all legal documents.
 * Single source of truth for asset paths, titles, and web URLs.
 */
object LegalConfig {

    // ── Asset paths (in-app HTML files) ──────────────────────────
    const val ASSET_PRIVACY_POLICY = "file:///android_asset/privacy_policy_tr.html"
    const val ASSET_TERMS_OF_USE = "file:///android_asset/terms_of_use_tr.html"
    const val ASSET_DATA_USAGE = "file:///android_asset/data_usage_tr.html"
    // Removed (Phase 0): ad_info (falsely claimed the app uses Google AdMob — EDUmio has NO ads/ads SDK)
    // and parent_info (legacy parental-control era, already unused).

    // ── Web URLs (from BuildConfig, set in build.gradle.kts per build type) ──
    val WEB_PRIVACY_POLICY: String get() = BuildConfig.PRIVACY_POLICY_URL
    val WEB_TERMS_OF_USE: String get() = BuildConfig.TERMS_URL

    // ── Legal document definitions ──────────────────────────────
    data class LegalDoc(
        val titleRes: Int,
        val subtitleRes: Int,
        val iconRes: Int,
        val assetUrl: String
    )

    fun allDocuments(): List<LegalDoc> = listOf(
        LegalDoc(
            R.string.legal_privacy_policy_title,
            R.string.legal_privacy_policy_sub,
            R.drawable.ic_lock,
            ASSET_PRIVACY_POLICY
        ),
        LegalDoc(
            R.string.legal_terms_title,
            R.string.legal_terms_sub,
            R.drawable.ic_review,
            ASSET_TERMS_OF_USE
        ),
        LegalDoc(
            R.string.legal_data_usage_title,
            R.string.legal_data_usage_sub,
            R.drawable.ic_info_outline,
            ASSET_DATA_USAGE
        )
    )
}
