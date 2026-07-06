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

data class BlogArticle(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val publishedAtMs: Long,
    val tags: List<String> = emptyList(),
    val imageUrl: String? = null
)

/** An exam guide / how-to article, versioned via Remote Config `exam_guide_version`. */
data class Guide(
    val id: String,
    val examCode: String,
    val title: String,
    val body: String
)

/** A server-driven push campaign (title/body + optional deep link + schedule window). */
data class NotificationCampaign(
    val id: String,
    val title: String,
    val body: String,
    val deepLink: String? = null,
    val startsAtMs: Long,
    val endsAtMs: Long,
    val premiumOnly: Boolean = false
)

/** A remotely-editable email report template (subject + HTML skeleton with placeholders). */
data class EmailReportTemplate(
    val id: String,
    val type: String,       // "weekly" | "monthly"
    val subject: String,
    val htmlTemplate: String
)

data class OnboardingSlide(
    val id: String,
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val order: Int = 0
)

/** Exam section / topic taxonomy for a study area (drives blueprints + filters). */
data class ExamSectionContent(
    val examCode: String,
    val sectionId: String,
    val name: String,
    val questionCount: Int,
    val weight: Double
)

data class TopicContent(
    val examCode: String,
    val sectionId: String,
    val topicId: String,
    val name: String,
    val parentTopicId: String? = null   // null = top-level topic; else a subtopic
)
