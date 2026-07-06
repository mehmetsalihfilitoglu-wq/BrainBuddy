package com.mioacademy.app.content

/**
 * The CMS boundary. Everything a Head of Content should be able to change remotely —
 * announcements, news, premium banners, scholarships, and (later) questions and
 * universities — is read through this gateway rather than hardcoded.
 *
 * [LocalContentGateway] returns honest empty content today (nothing fabricated): the
 * surfaces simply stay empty until a CMS publishes. A `RemoteContentGateway` backed by
 * Firestore/CMS collections implements the same interface, so nothing in the UI changes.
 *
 * Questions and universities keep their existing bundled providers as the offline
 * fallback; [refreshQuestions]/[refreshUniversities] are the seam through which a CMS
 * delivers or overrides them without shipping an APK.
 */
interface ContentGateway {
    suspend fun announcements(): List<Announcement>
    suspend fun news(): List<NewsArticle>
    suspend fun premiumBanners(): List<PremiumBanner>
    suspend fun scholarships(): List<ScholarshipInfo>

    /** Pulls the latest question / university content from the CMS. False = unchanged / unavailable. */
    suspend fun refreshQuestions(studyAreaCode: String): Boolean
    suspend fun refreshUniversities(): Boolean
}

/** No CMS yet: return empty (never hardcode content) and report content unchanged. */
class LocalContentGateway : ContentGateway {
    override suspend fun announcements(): List<Announcement> = emptyList()
    override suspend fun news(): List<NewsArticle> = emptyList()
    override suspend fun premiumBanners(): List<PremiumBanner> = emptyList()
    override suspend fun scholarships(): List<ScholarshipInfo> = emptyList()
    override suspend fun refreshQuestions(studyAreaCode: String): Boolean = false
    override suspend fun refreshUniversities(): Boolean = false
}

/** Composition root for CMS content — returns the remote gateway once a CMS is live. */
object ContentProvider {
    @Volatile private var cached: ContentGateway? = null
    fun gateway(): ContentGateway = cached ?: synchronized(this) {
        cached ?: LocalContentGateway().also { cached = it }
    }
}
