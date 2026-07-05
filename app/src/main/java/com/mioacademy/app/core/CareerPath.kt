package com.mioacademy.app.core

enum class CareerPath(
    val displayNameTr: String,
    val emoji: String,
    val examType: ExamType,
    val italianDegreeName: String,
    val subjectSummary: String
) {
    MEDICINE(
        "Tıp", "🩺", ExamType.IMAT,
        "Medicina e Chirurgia",
        "Biyoloji · Kimya · Fizik · Mantık"
    ),
    DENTISTRY(
        "Diş Hekimliği", "🦷", ExamType.IMAT,
        "Odontoiatria",
        "Biyoloji · Kimya · Fizik · Mantık"
    ),
    ENGINEERING(
        "Mühendislik", "⚙️", ExamType.TOLC_I,
        "Ingegneria",
        "Matematik · Fizik · Kimya · Mantık"
    ),
    COMPUTER_SCIENCE(
        "Bilgisayar Müh.", "💻", ExamType.TOLC_I,
        "Informatica",
        "Matematik · Fizik · Mantık"
    ),
    ARCHITECTURE(
        "Mimarlık", "🏛️", ExamType.TOLC_A,
        "Architettura",
        "Matematik · Uzaysal Düşünme · Sanat Tarihi"
    ),
    ECONOMICS(
        "Ekonomi", "📊", ExamType.TOLC_E,
        "Economia",
        "Matematik · Mantık · İngilizce"
    ),
    LAW(
        "Hukuk", "⚖️", ExamType.TOLC_SU,
        "Giurisprudenza",
        "Mantık · Okuduğunu Anlama · Tarih"
    ),
    PHARMACY(
        "Eczacılık", "💊", ExamType.TOLC_B,
        "Farmacia",
        "Biyoloji · Kimya · Matematik"
    ),
    BIOLOGY(
        "Biyoloji", "🧬", ExamType.TOLC_B,
        "Biologia",
        "Biyoloji · Kimya · Matematik"
    ),
    PSYCHOLOGY(
        "Psikoloji", "🧠", ExamType.TOLC_SU,
        "Psicologia",
        "Mantık · Okuduğunu Anlama · Genel Kültür"
    ),
    VETERINARY(
        "Veterinerlik", "🐾", ExamType.TOLC_B,
        "Medicina Veterinaria",
        "Biyoloji · Kimya · Fizik"
    ),
    MATHEMATICS(
        "Matematik", "📐", ExamType.TOLC_I,
        "Matematica",
        "Matematik · Fizik · Mantık"
    ),
    DESIGN(
        "Tasarım", "✏️", ExamType.TOLC_A,
        "Design",
        "Matematik · Uzaysal Düşünme · Yaratıcılık"
    ),
    OTHER(
        "Diğer", "🎓", ExamType.ITALIAN_LANG,
        "Altro",
        "İtalyanca · Genel Hazırlık"
    )
}
