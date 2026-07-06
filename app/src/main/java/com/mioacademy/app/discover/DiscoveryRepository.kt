package com.mioacademy.app.discover

import com.mioacademy.app.core.CareerPath
import com.mioacademy.app.core.ExamType

/**
 * Source of Keşfet content. Local static today; the interface lets a remote
 * implementation (API / Firebase / Remote Config) drop in later with no changes
 * to the UI. Access via [Discovery.repository] so the source is swappable in one
 * place.
 */
interface DiscoveryRepository {
    fun getGuide(examType: ExamType): ExamGuide?
    fun getUniversities(examType: ExamType): List<University>
    fun getBachelorPrograms(examType: ExamType): List<BachelorProgram>
    fun getBachelorProgram(id: String): BachelorProgram?
    fun hasContent(examType: ExamType): Boolean
    fun getUniversity(id: String): University?
}

/** Single accessor — swap this to a remote repository in one line later. */
object Discovery {
    val repository: DiscoveryRepository = LocalDiscoveryRepository
}

/**
 * Local, static seed dataset. Values that change year to year (dates, fees,
 * quotas, exam centres) are given as guidance text with an explicit
 * "check the official notice" caveat — never as authoritative hard data.
 *
 * Currently seeded for IMAT. TIL-I, TIL-A, CEnT-S, SAT and TOLC-PSI extend by
 * adding a guide to [guides] and universities to [universities] with the right
 * [ExamType] — no UI change required.
 */
object LocalDiscoveryRepository : DiscoveryRepository {

    override fun getGuide(examType: ExamType): ExamGuide? = guides[examType]

    override fun getUniversities(examType: ExamType): List<University> =
        universities.filter { it.examType == examType }

    override fun getBachelorPrograms(examType: ExamType): List<BachelorProgram> =
        BachelorDataset.programs.filter { it.primaryStudyArea == examType }

    override fun getBachelorProgram(id: String): BachelorProgram? =
        BachelorDataset.programs.firstOrNull { it.id == id }

    override fun hasContent(examType: ExamType): Boolean =
        guides.containsKey(examType) ||
            universities.any { it.examType == examType } ||
            BachelorDataset.programs.any { it.primaryStudyArea == examType }

    override fun getUniversity(id: String): University? = universities.firstOrNull { it.id == id }

    // ── Guides ──────────────────────────────────────────────────────────────

    private val guides: Map<ExamType, ExamGuide> = mapOf(
        ExamType.IMAT to ExamGuide(
            examType = ExamType.IMAT,
            title = "IMAT — İtalya İngilizce Tıp Sınavı",
            subtitle = "İngilizce tıp ve bazı diş hekimliği programları için giriş sınavı",
            overview = "IMAT (International Medical Admissions Test), İtalya'da İngilizce verilen tıp ve " +
                "bazı diş hekimliği programlarına giriş için kullanılan sınavdır. Sınav tamamen " +
                "İngilizcedir ve devlet üniversitelerindeki İngilizce tıp kontenjanları büyük ölçüde " +
                "bu sınav sonucuna göre dağıtılır.",
            examStructure = listOf(
                GuideFact("Soru sayısı", "60"),
                GuideFact("Süre", "100 dakika"),
                GuideFact("Toplam puan", "90 üzerinden"),
                GuideFact("Sınav dili", "İngilizce")
            ),
            scoring = listOf(
                GuideFact("Doğru cevap", "+1,5"),
                GuideFact("Yanlış cevap", "-0,4"),
                GuideFact("Boş", "0")
            ),
            subjects = listOf(
                "Mantık (Logical Reasoning)",
                "Genel Kültür",
                "Biyoloji",
                "Kimya",
                "Matematik / Fizik"
            ),
            timeline = "Genellikle yılda bir kez, çoğunlukla Eylül ayında yapılır. Başvuru tarihleri " +
                "her yıl değişir; kesin takvim resmi duyuruda açıklanır.",
            applicationNotes = "Başvurular Universitaly portalı üzerinden yapılır. Sınav ücreti yaklaşık " +
                "130€ olabilir; kesin ücret resmi duyuruda açıklanır. Türkiye'de bazı yıllarda " +
                "İstanbul ve Ankara gibi merkezlerde yapılabilir; güncel sınav merkezi bilgisi resmi " +
                "duyuruya göre değişir. Minimum puan ve kontenjanlar her yıl değişebilir.",
            requiredDocuments = listOf(
                "Geçerli pasaport veya kimlik",
                "Lise diploması / denklik belgesi (gerekebilir)",
                "Universitaly kaydı",
                "Sınav ücreti ödeme dekontu"
            ),
            faq = listOf(
                FaqItem(
                    "IMAT'a kimler girebilir?",
                    "İngilizce tıp veya diş hekimliği okumak isteyen lise mezunları ve son sınıf " +
                        "öğrencileri başvurabilir. Uyruk sınırı yoktur; AB dışı öğrenciler için ayrı " +
                        "kontenjanlar bulunur."
                ),
                FaqItem(
                    "Sınav hangi dilde?",
                    "Sınav tamamen İngilizcedir."
                ),
                FaqItem(
                    "IMAT ile hangi bölümlere gidebilirim?",
                    "Başta İngilizce tıp (Medicine and Surgery) olmak üzere, bazı üniversitelerde " +
                        "İngilizce diş hekimliği (Dentistry) programlarına da IMAT ile başvurulabilir."
                ),
                FaqItem(
                    "Özel üniversiteler de IMAT istiyor mu?",
                    "Her zaman değil. Humanitas ve San Raffaele gibi bazı özel üniversitelerin kendi " +
                        "giriş sınavları vardır; bu okullar IMAT dışında değerlendirilir."
                ),
                FaqItem(
                    "Yanlış cevap puanımı düşürür mü?",
                    "Evet. Her yanlış -0,4 puan götürür, boş bırakmak 0'dır. Emin olmadığın soruda " +
                        "risk ve puan dengesini düşünmelisin."
                )
            ),
            officialNoticeNote = "Tarih, ücret, sınav merkezi, minimum puan ve kontenjanlar her yıl " +
                "değişebilir. Kesin bilgi için her zaman resmi duyuruyu (Universitaly / MUR / üniversite " +
                "siteleri) kontrol et."
        )
    )

    // ── Universities ────────────────────────────────────────────────────────

    private fun publicMed(
        id: String, name: String, city: String, region: String,
        website: String, careers: List<CareerPath> = listOf(CareerPath.MEDICINE),
        program: String = "Medicine and Surgery (İngilizce)",
        degree: String = "Tıp Lisansı (6 yıl)",
        desc: String
    ) = University(
        id = id, name = name, city = city, region = region, country = "İtalya",
        examType = ExamType.IMAT, relatedCareers = careers,
        programName = program, degreeType = degree,
        institutionType = InstitutionType.PUBLIC, language = "İngilizce",
        shortDescription = desc,
        highlights = listOf("Devlet üniversitesi", "İngilizce program", "IMAT ile başvuru"),
        tuitionNote = "Devlet üniversitesi; harç, gelire dayalı (ISEE) hesaplanır. Güncel tutar için resmi duyuru kontrol edilmeli.",
        scholarshipNote = "DSU / bölgesel burs ve harç muafiyeti imkanları olabilir; başvuru koşulları her yıl değişir.",
        admissionExam = "IMAT",
        websiteUrl = website,
        tags = listOf("Devlet", "IMAT", "İngilizce")
    )

    private fun privateMed(
        id: String, name: String, city: String, region: String,
        website: String, admission: String, desc: String
    ) = University(
        id = id, name = name, city = city, region = region, country = "İtalya",
        examType = ExamType.IMAT, relatedCareers = listOf(CareerPath.MEDICINE),
        programName = "Medicine and Surgery (İngilizce)", degreeType = "Tıp Lisansı (6 yıl)",
        institutionType = InstitutionType.PRIVATE, language = "İngilizce",
        shortDescription = desc,
        highlights = listOf("Özel üniversite", "İngilizce program", "Kendi giriş süreci"),
        tuitionNote = "Özel üniversite; yıllık ücret yüksektir. Güncel ücret için resmi duyuru kontrol edilmeli.",
        scholarshipNote = "Kuruma özel burs / başarı indirimi imkanları olabilir; resmi siteden kontrol edilmeli.",
        admissionExam = admission,
        websiteUrl = website,
        tags = listOf("Özel", "İngilizce")
    )

    private val universities: List<University> = listOf(
        // ── Public (devlet) — IMAT ──
        publicMed("pavia", "University of Pavia", "Pavia", "Lombardia", "https://web-en.unipv.it",
            desc = "İngilizce tıp programında köklü geçmişe sahip, uluslararası öğrencilerin yoğun tercih ettiği bir devlet üniversitesi."),
        publicMed("milan_statale", "University of Milan", "Milano", "Lombardia", "https://www.unimi.it/en",
            desc = "Milano'nun merkezî devlet üniversitesi; güçlü klinik ağı ve araştırma imkanları sunar."),
        publicMed("milan_bicocca", "University of Milan-Bicocca", "Monza", "Lombardia", "https://en.unimib.it",
            desc = "Tıp eğitimi Monza kampüsünde yürütülür; modern hastane iş birlikleriyle öne çıkar."),
        publicMed("turin", "University of Turin", "Torino", "Piemonte", "https://en.unito.it",
            desc = "Kuzey İtalya'nın büyük devlet üniversitelerinden; İngilizce tıp kontenjanı bulunur."),
        publicMed("bologna", "University of Bologna", "Bologna", "Emilia-Romagna", "https://www.unibo.it/en",
            desc = "Dünyanın en eski üniversitesi; güçlü akademik gelenek ve canlı öğrenci şehri."),
        publicMed("sapienza", "Sapienza University of Rome", "Roma", "Lazio", "https://www.uniroma1.it/en",
            desc = "Roma'nın en büyük devlet üniversitesi; geniş İngilizce tıp kontenjanı ve klinik olanaklar."),
        publicMed("tor_vergata", "University of Rome Tor Vergata", "Roma", "Lazio", "https://web.uniroma2.it/en",
            desc = "Roma'da modern kampüs ve araştırma hastanesiyle bilinen devlet üniversitesi."),
        publicMed("federico_ii", "University of Naples Federico II", "Napoli", "Campania", "https://www.international.unina.it",
            desc = "Güney İtalya'nın köklü devlet üniversitesi; İngilizce tıp programı sunar."),
        publicMed("padua", "University of Padua", "Padova", "Veneto", "https://www.unipd.it/en",
            desc = "Tarihî tıp fakültesiyle ünlü; araştırma odaklı güçlü bir devlet üniversitesi."),
        publicMed("bari", "University of Bari Aldo Moro", "Bari", "Puglia", "https://www.uniba.it",
            desc = "Puglia bölgesinin merkezî devlet üniversitesi; İngilizce tıp kontenjanı bulunur."),
        publicMed("messina", "University of Messina", "Messina", "Sicilia", "https://www.unime.it",
            desc = "Sicilya'da yer alan devlet üniversitesi; uygun yaşam maliyetiyle öne çıkar."),
        publicMed("parma", "University of Parma", "Parma", "Emilia-Romagna", "https://www.unipr.it/en",
            desc = "Öğrenci dostu bir şehirde, güçlü sağlık bilimleri geleneğine sahip devlet üniversitesi."),
        publicMed("cagliari", "University of Cagliari", "Cagliari", "Sardegna", "https://www.unica.it/unica/en",
            desc = "Sardinya'nın devlet üniversitesi; sakin ada yaşamı ve uygun maliyet sunar."),
        publicMed("catania", "University of Catania", "Catania", "Sicilia", "https://www.unict.it",
            desc = "Sicilya'nın büyük devlet üniversitelerinden; İngilizce tıp programı bulunur."),
        publicMed("marche", "Marche Polytechnic University", "Ancona", "Marche", "https://www.univpm.it",
            desc = "Ancona merkezli; sağlık bilimlerinde uygulamalı eğitime önem veren devlet üniversitesi."),
        publicMed("siena", "University of Siena", "Siena", "Toscana", "https://www.unisi.it/en",
            careers = listOf(CareerPath.DENTISTRY),
            program = "Dentistry (İngilizce)", degree = "Diş Hekimliği Lisansı",
            desc = "Toskana'nın tarihî şehrinde, İngilizce diş hekimliği programıyla öne çıkan devlet üniversitesi."),
        publicMed("vanvitelli", "University of Campania Luigi Vanvitelli", "Caserta", "Campania", "https://international.unicampania.it",
            desc = "Napoli/Caserta bölgesinde İngilizce tıp kontenjanı sunan devlet üniversitesi."),

        // ── Private (özel) — IMAT dışı kendi süreçleri ──
        privateMed("humanitas", "Humanitas University", "Milano", "Lombardia", "https://www.hunimed.eu",
            admission = "HUMAT (kendi giriş sınavı)",
            desc = "Araştırma hastanesiyle iç içe, İngilizce tıp eğitimi veren seçkin bir özel üniversite."),
        privateMed("san_raffaele", "Vita-Salute San Raffaele University", "Milano", "Lombardia", "https://www.unisr.it/en",
            admission = "Kendi giriş sınavı",
            desc = "San Raffaele hastanesine bağlı; rekabetçi kontenjanlı prestijli bir özel üniversite."),
        privateMed("cattolica", "Università Cattolica del Sacro Cuore", "Roma", "Lazio", "https://www.unicatt.it",
            admission = "Kendi giriş sınavı",
            desc = "Gemelli hastanesiyle bağlantılı, İngilizce tıp programı olan köklü bir özel üniversite."),
        privateMed("unicamillus", "UniCamillus", "Roma", "Lazio", "https://www.unicamillus.org/en",
            admission = "Kendi giriş süreci (bazı yıllar IMAT kabul edebilir)",
            desc = "Uluslararası sağlık bilimlerine odaklı, İngilizce tıp veren bir özel üniversite."),
        privateMed("campus_biomedico", "Campus Bio-Medico University", "Roma", "Lazio", "https://www.unicampus.it/en",
            admission = "Kendi giriş sınavı",
            desc = "Roma'da sağlık bilimlerine odaklanmış, kendi hastanesi olan bir özel üniversite.")
    )
}
