package com.brainbuddy.app.legal

import com.brainbuddy.app.BuildConfig
import com.brainbuddy.app.R

/**
 * Central configuration for all legal documents.
 * Single source of truth for asset paths, titles, and web URLs.
 */
object LegalConfig {

    // ── Asset paths (in-app HTML files) ──────────────────────────
    const val ASSET_PRIVACY_POLICY = "file:///android_asset/privacy_policy_tr.html"
    const val ASSET_TERMS_OF_USE = "file:///android_asset/terms_of_use_tr.html"
    const val ASSET_DATA_USAGE = "file:///android_asset/data_usage_tr.html"
    const val ASSET_AD_INFO = "file:///android_asset/ad_info_tr.html"
    const val ASSET_PARENT_INFO = "file:///android_asset/parent_info_tr.html"

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
        ),
        LegalDoc(
            R.string.legal_ad_info_title,
            R.string.legal_ad_info_sub,
            R.drawable.ic_review,
            ASSET_AD_INFO
        ),
        LegalDoc(
            R.string.legal_parent_info_title,
            R.string.legal_parent_info_sub,
            R.drawable.ic_permission,
            ASSET_PARENT_INFO
        )
    )
}
