package com.mioacademy.app.discover

import com.mioacademy.app.core.CareerPath
import com.mioacademy.app.core.ExamType

/**
 * Domain models for the "Keşfet" (University & Program Discovery) section.
 *
 * These are plain immutable models with no persistence coupling, so the same
 * shapes can later be produced by a remote source (API / Firebase / Remote
 * Config) instead of the current local static dataset — see [DiscoveryRepository].
 *
 * Fields that change year to year (dates, fees, quotas) are expressed as free
 * text and always paired with an "official notice" caveat, never as hard data.
 */

enum class InstitutionType { PUBLIC, PRIVATE }

data class GeoPoint(val lat: Double, val lng: Double)

/** A labelled fact row used for exam structure / scoring breakdowns. */
data class GuideFact(val label: String, val value: String)

data class FaqItem(val question: String, val answer: String)

/** A university/program card, scalable toward a production dataset. */
data class University(
    val id: String,
    val name: String,
    val city: String,
    val region: String,
    val country: String,
    val examType: ExamType,
    val relatedCareers: List<CareerPath>,
    val programName: String,
    val degreeType: String,               // e.g. "Lisans (6 yıl)"
    val institutionType: InstitutionType,
    val language: String,                 // e.g. "İngilizce"
    val shortDescription: String,
    val highlights: List<String> = emptyList(),
    val tuitionNote: String = "",
    val scholarshipNote: String = "",
    val admissionExam: String = "",       // e.g. "IMAT" or "Kendi giriş sınavı"
    val websiteUrl: String = "",
    val tags: List<String> = emptyList(),
    val hasEnglishProgram: Boolean = true,
    val coordinates: GeoPoint? = null,    // optional, for a future map
    val heroImageUrl: String? = null      // optional, for a future photo
) {
    val isPublic: Boolean get() = institutionType == InstitutionType.PUBLIC
}

/** A per-exam study/admissions guide. */
data class ExamGuide(
    val examType: ExamType,
    val title: String,
    val subtitle: String,
    val overview: String,
    val examStructure: List<GuideFact>,
    val scoring: List<GuideFact>,
    val subjects: List<String>,
    val timeline: String,
    val applicationNotes: String,
    val requiredDocuments: List<String> = emptyList(),
    val faq: List<FaqItem> = emptyList(),
    val officialNoticeNote: String
)
