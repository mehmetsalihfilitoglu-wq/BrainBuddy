package com.edumio.app.content

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
    // Marketing / informational
    suspend fun announcements(): List<Announcement>
    suspend fun news(): List<NewsArticle>
    suspend fun blogArticles(): List<BlogArticle>
    suspend fun guides(examCode: String): List<Guide>
    suspend fun premiumBanners(): List<PremiumBanner>
    suspend fun scholarships(): List<ScholarshipInfo>

    // Onboarding + microcopy (server-editable copy with a local default fallback)
    suspend fun onboardingSlides(): List<OnboardingSlide>
    /** Returns CMS microcopy for [key], or [default] when the CMS has none (never hardcode-only). */
    suspend fun microcopy(key: String, default: String): String

    // Push + email campaigns/templates
    suspend fun notificationCampaigns(): List<NotificationCampaign>
    suspend fun emailReportTemplate(type: String): EmailReportTemplate?

    // Exam taxonomy (sections / topics / subtopics) + question & university delivery
    suspend fun examSections(examCode: String): List<ExamSectionContent>
    suspend fun topics(examCode: String): List<TopicContent>
    /** Pulls the latest question / university content from the CMS. False = unchanged / unavailable. */
    suspend fun refreshQuestions(studyAreaCode: String): Boolean
    suspend fun refreshUniversities(): Boolean
}

/** No CMS yet: return empty (never hardcode content) / the given defaults, and report unchanged. */
class LocalContentGateway : ContentGateway {
    override suspend fun announcements(): List<Announcement> = emptyList()
    override suspend fun news(): List<NewsArticle> = emptyList()
    override suspend fun blogArticles(): List<BlogArticle> = emptyList()
    override suspend fun guides(examCode: String): List<Guide> = emptyList()
    override suspend fun premiumBanners(): List<PremiumBanner> = emptyList()
    override suspend fun scholarships(): List<ScholarshipInfo> = emptyList()
    override suspend fun onboardingSlides(): List<OnboardingSlide> = emptyList()
    override suspend fun microcopy(key: String, default: String): String = default
    override suspend fun notificationCampaigns(): List<NotificationCampaign> = emptyList()
    override suspend fun emailReportTemplate(type: String): EmailReportTemplate? = null
    override suspend fun examSections(examCode: String): List<ExamSectionContent> = emptyList()
    override suspend fun topics(examCode: String): List<TopicContent> = emptyList()
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
