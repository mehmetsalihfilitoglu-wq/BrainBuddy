package com.mioacademy.app.core

enum class CareerPath(
    val displayNameTr: String,
    val emoji: String,
    val examType: ExamType,
    val italianDegreeName: String
) {
    MEDICINE("Tıp", "🩺", ExamType.IMAT, "Medicina e Chirurgia"),
    DENTISTRY("Diş Hekimliği", "🦷", ExamType.IMAT, "Odontoiatria"),
    ENGINEERING("Mühendislik", "⚙️", ExamType.TIL_I, "Ingegneria"),
    COMPUTER_SCIENCE("Bilgisayar Müh.", "💻", ExamType.TIL_I, "Informatica"),
    ARCHITECTURE("Mimarlık", "🏛️", ExamType.TIL_A, "Architettura"),
    ECONOMICS("Ekonomi", "📊", ExamType.CENT_S, "Economia"),
    LAW("Hukuk", "⚖️", ExamType.TOLC_SU, "Giurisprudenza"),
    PHARMACY("Eczacılık", "💊", ExamType.CENT_S, "Farmacia"),
    BIOLOGY("Biyoloji", "🧬", ExamType.CENT_S, "Biologia"),
    PSYCHOLOGY("Psikoloji", "🧠", ExamType.TOLC_PSI, "Psicologia"),
    VETERINARY("Veterinerlik", "🐾", ExamType.CENT_S, "Medicina Veterinaria"),
    MATHEMATICS("Matematik", "📐", ExamType.TIL_I, "Matematica"),
    DESIGN("Tasarım", "✏️", ExamType.TIL_A, "Design"),
    OTHER("Diğer", "🎓", ExamType.ITALIAN_LANG, "Altro")
}
