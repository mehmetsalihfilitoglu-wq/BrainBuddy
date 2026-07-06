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

/** Broad field a bachelor program belongs to (for grouping in Keşfet). */
enum class FieldCategory(val displayTr: String) {
    ENGINEERING("Mühendislik & Bilişim"),
    ARCHITECTURE_DESIGN("Mimarlık & Tasarım"),
    BUSINESS_ECONOMICS("İşletme & Ekonomi"),
    SCIENCE_BIOTECH("Bilim & Biyoteknoloji"),
    LAW_INTERNATIONAL("Hukuk & Uluslararası İlişkiler"),
    COMMUNICATION_MEDIA("İletişim & Medya"),
    HEALTH_SCIENCES("Sağlık Bilimleri"),
    PSYCHOLOGY_SOCIAL("Psikoloji & Sosyal Bilimler"),
    HUMANITIES("Beşeri Bilimler"),
    FOUNDATION("Hazırlık Programları"),
    OTHER("Diğer")
}

/**
 * A bachelor (undergraduate) program. Kept separate from [University] (which
 * currently seeds the IMAT single-cycle medicine schools) but shown together in
 * the same Keşfet experience.
 *
 * Intentionally lean: this phase is a fast "which programs, where, which exam"
 * browser — not a full advisory sheet. Volatile fields (fees, language certs,
 * opening/deadline dates, long notes, links) are deliberately NOT modelled, so
 * nothing here needs constant maintenance. [admissionExam] preserves the real
 * admission path (TIL-I, TEST-ARCHED, CEnT-S, SAT, TOLC-SU, university exam, …),
 * while [primaryStudyArea] is the reachable study area the program surfaces
 * under so filtering matches the student's active area. [note] is reserved for
 * short, stable notes only (e.g. TOLC-SU's English-section rule).
 */
data class BachelorProgram(
    val id: String,
    val universityId: String,
    val universityName: String,
    val city: String,
    val region: String,
    val programName: String,
    val fieldCategory: FieldCategory,
    val relatedCareers: List<CareerPath>,
    val primaryStudyArea: ExamType,
    val admissionExam: String,
    val admissionRoute: String = "",
    val note: String = "",
    val tags: List<String> = emptyList(),
    val isPublic: Boolean = true,
    val hasEnglishProgram: Boolean = true,
    val country: String = "İtalya"
)

/**
 * One program inside a [FeaturedUniversity] page. Richer than [BachelorProgram]
 * because featured pages are curated (campus, seats, admission are stable,
 * hand-checked values for that specific school).
 */
data class UniversityProgram(
    val programId: String,
    val programName: String,
    val degreeType: String,
    val language: String,
    val campus: String,
    val availableSeats: Int?,      // null = not published
    val admissionInfo: String,     // e.g. "TIL-I or SAT"
    val shortDescription: String = "",
    val tags: List<String> = emptyList()
)

/**
 * A curated, multi-program university page (e.g. Politecnico di Torino). Kept
 * separate from the flat [BachelorProgram] list so a flagship school can be
 * presented as its own page with hand-checked programs, without touching the
 * broad dataset or the global study-area architecture.
 *
 * [studyArea] is the reachable area this page surfaces under (TIL_I for Polito
 * engineering). Optional [coordinates] / [heroImage] / [gallery] are reserved
 * for future map/photo/experience blocks.
 */
data class FeaturedUniversity(
    val universityId: String,
    val universityName: String,
    val city: String,
    val region: String,
    val country: String,
    val institutionType: InstitutionType,
    val studyArea: ExamType,
    val shortDescription: String,
    val highlights: List<String> = emptyList(),
    val tuitionNote: String = "",
    val scholarshipNote: String = "",
    val websiteUrl: String = "",
    val tags: List<String> = emptyList(),
    val coordinates: GeoPoint? = null,
    val heroImage: String? = null,
    val gallery: List<String> = emptyList(),
    val programs: List<UniversityProgram> = emptyList()
) {
    val isPublic: Boolean get() = institutionType == InstitutionType.PUBLIC
}

/**
 * Standard at-a-glance exam summary, shown as icon cards near the top of a
 * guide. Every field is optional — the UI renders a card only for the ones that
 * are set, so the same structure works for any exam.
 */
data class ExamSummary(
    val whenHeld: String = "",        // 📅 Ne zaman yapılır?
    val attempts: String = "",        // 🔁 Kaç kez girilebilir?
    val resultValidity: String = "",  // 📊 Sonuç geçerliliği
    val importantNote: String = ""    // ℹ️ Önemli bilgiler
)

/** A per-exam study/admissions guide. */
data class ExamGuide(
    val examType: ExamType,
    val title: String,
    val subtitle: String,
    val overview: String,
    val summary: ExamSummary? = null,
    val examStructure: List<GuideFact> = emptyList(),
    val scoring: List<GuideFact> = emptyList(),
    val subjects: List<String> = emptyList(),
    val timeline: String = "",
    val applicationNotes: String = "",
    val requiredDocuments: List<String> = emptyList(),
    val faq: List<FaqItem> = emptyList(),
    val officialNoticeNote: String
)
