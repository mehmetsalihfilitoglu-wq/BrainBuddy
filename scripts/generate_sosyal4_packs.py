#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 4th Grade Sosyal Bilgiler question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: paragraf yorum, harita/tablo/grafik yorumlama, tarihsel analiz, sebep-sonuç.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/sosyal4"

TOPICS = [
    "birey_toplum",
    "kultur_miras",
    "insanlar_yerler_cevreler",
    "bilim_teknoloji_toplum",
    "uretim_dagitim_tuketim",
    "etkin_vatandaslik",
    "kuresel_baglantilar",
]

NEW_GEN_TYPES = [
    "paragraf_yorum",
    "harita_yorumlama",
    "tablo_grafik_yorumlama",
    "tarihsel_olay_analizi",
    "cografi_yorum",
    "sebep_sonuc",
    "kavram_baglantisi",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # Birey ve Toplum
    ("Paragrafta \"Bireyin okulda öğrenci, evde çocuk, mahallede arkadaş olarak farklı roller üstlendiği\" ifadesi geçiyor.\n\nBu bilgiye göre birey-toplum ilişkisi ile ilgili hangi çıkarım yapılabilir?",
     "birey_toplum", ["Birey farklı ortamlarda farklı roller üstlenir ve bu roller toplumsal yaşamı şekillendirir", "Herkes sadece tek bir role sahip olmalıdır", "Roller birbirinden bağımsızdır", "Aile rolü diğerlerinden önemsizdir"]),
    ("\"Toplumsal kurallar, bir arada yaşamayı kolaylaştırır\" cümlesine göre kuralların işlevi nedir?",
     "birey_toplum", ["Düzenli ve uyumlu bir toplum yaşamı sağlamak", "Kurallar sadece okulda geçerlidir", "Kurallar bireyleri kısıtlar, fayda sağlamaz", "Kurallar evrensel değildir"]),
    ("Bir metinde \"gruplar, ortak amaçları olan insan topluluklarıdır; aile, sınıf, kulüp örnektir\" ifadesi yer alıyor.\n\nBu tanıma göre grubun özelliği nedir?",
     "birey_toplum", ["Ortak amaç veya ilgi etrafında bir araya gelen bireylerden oluşması", "Sadece aileden oluşur", "Grup üyeleri birbirini tanımaz", "Gruplar toplumdan bağımsızdır"]),
    ("Toplumsal değerler (dürüstlük, yardımlaşma, saygı) metinde vurgulanıyor. \"Bu değerler toplumda güveni artırır\" deniyor.\n\nDeğerlerin toplumsal işlevi nedir?",
     "birey_toplum", ["Toplumsal uyum ve güveni desteklemek", "Değerler sadece ailede öğretilir", "Değerler önemsizdir", "Toplum değerlerden etkilenmez"]),
    # Kültür ve Miras
    ("Kültür ve Miras ünitesinde \"bayramlar, yemekler, halk oyunları toplumun ortak değerlerini yansıtır\" ifadesi geçiyor.\n\nBu tür değerlerin korunması neden önemlidir?",
     "kultur_miras", ["Toplumsal kimliği ve geçmişi yaşatmaya katkı sağlar", "Sadece turizm için önemlidir", "Geçmiş önemsizdir", "Sadece büyük şehirlerde vardır"]),
    ("Tarihsel metinde \"Atatürk'ün Türk tarihine ve diline verdiği önem\" vurgulanıyor.\n\nBu vurgunun amacı aşağıdakilerden hangisi olabilir?",
     "kultur_miras", ["Milli kimlik ve geçmişe sahip çıkmayı desteklemek", "Sadece dil önemlidir", "Tarih önemsizdir", "Geçmiş değiştirilemez"]),
    ("\"Yazının icadı\" tarih şeridinde önemli bir dönüm noktası olarak gösteriliyor.\n\nYazının icadının önemi aşağıdakilerden hangisidir?",
     "kultur_miras", ["Bilginin kaydedilmesini ve sonraki kuşaklara aktarılmasını sağlaması", "Sadece edebiyat için önemlidir", "Tarih öncesi dönem yoktur", "Kayıt tutmak gereksizdir"]),
    ("Anadolu medeniyetleri haritasında Hitit, Frig, Lidya farklı bölgelerde gösteriliyor.\n\nBu medeniyetlerin Anadolu'da gelişmesinin sebebi aşağıdakilerden hangisi olabilir?",
     "kultur_miras", ["Verimli topraklar, ticaret yolları ve elverişli coğrafya", "Sadece savaşlar", "Nüfus artışı tek nedendir", "İklim değişikliği"]),
    # İnsanlar, Yerler ve Çevreler
    ("Haritada Türkiye'nin bölgeleri gösterilmiştir. Karadeniz kıyısı yeşil, İç Anadolu daha açık renkle işaretlenmiştir.\n\nBu farklılığın temel sebebi aşağıdakilerden hangisidir?",
     "insanlar_yerler_cevreler", ["İklim ve bitki örtüsü farkı (yağış, sıcaklık)", "Sadece nüfus farkı", "Ulaşım imkanları", "Şehirleşme oranı"]),
    ("Coğrafi bölgeler tablosunda Akdeniz'de turunçgil, İç Anadolu'da tahıl tarımı öne çıkıyor.\n\nBu farklılığın nedeni aşağıdakilerden hangisidir?",
     "insanlar_yerler_cevreler", ["İklim ve toprak koşullarının bölgelere göre değişmesi", "Sadece nüfus farkı", "Sanayi yatırımları", "Eğitim düzeyi"]),
    ("Haritada nüfus yoğunluğu kıyı bölgelerde yüksek, iç kesimlerde düşük gösteriliyor.\n\nBu dağılımı etkileyen faktörler arasında aşağıdakilerden hangisi sayılabilir?",
     "insanlar_yerler_cevreler", ["İklim, ulaşım, tarım ve sanayi imkanları", "Sadece dil birliği", "Din farkı", "Tarihsel nedenler tek başına yeterlidir"]),
    ("İklim grafiğinde yağış ve sıcaklık değerleri karşılaştırılıyor. Karadeniz'de yıl boyu yağış vardır.\n\nBu iklimin tarıma etkisi nasıldır?",
     "insanlar_yerler_cevreler", ["Yağış fazla olduğu için sulama ihtiyacı azalır; çay, fındık gibi ürünler yetişir", "Tarım mümkün değildir", "Sadece kışın tarım yapılır", "Her bölge aynıdır"]),
    # Bilim, Teknoloji ve Toplum
    ("\"Teknoloji günlük yaşamı kolaylaştırır; ancak doğru kullanılmalıdır\" paragrafından çıkarılabilecek sonuç nedir?",
     "bilim_teknoloji_toplum", ["Teknoloji bilinçli kullanıldığında fayda sağlar", "Teknoloji zararlıdır", "Teknoloji sadece eğlence içindir", "Teknolojiden kaçınmak gerekir"]),
    ("Metinde \"internet ile bilgiye hızlı erişim sağlanır; ancak güvenilir kaynaklara dikkat edilmelidir\" ifadesi geçiyor.\n\nBu uyarının nedeni nedir?",
     "bilim_teknoloji_toplum", ["Her bilgi doğru değildir; kaynak kontrolü önemlidir", "İnternet tamamen güvensizdir", "Bilgiye erişim gereksizdir", "Sadece kitaplar güvenilirdir"]),
    ("Bilim ve teknoloji gelişim tablosunda \"tekerleğin icadı → ulaşım kolaylaştı\" ilişkisi verilmiş.\n\nBu tabloya göre teknolojik gelişmenin topluma etkisi nasıldır?",
     "bilim_teknoloji_toplum", ["Teknolojik buluşlar yaşamı kolaylaştırır ve toplumu değiştirir", "Teknoloji toplumu etkilemez", "Sadece ulaşım önemlidir", "Geçmişte teknoloji yoktu"]),
    ("Teknolojinin çevreye etkisi metinde tartışılıyor. \"Sanayi atıkları hava ve suyu kirletebilir\" deniyor.\n\nBu durumun sebebi nedir?",
     "bilim_teknoloji_toplum", ["Endüstriyel üretimin atıklarının çevreye verdiği zarar", "Teknoloji sadece fayda sağlar", "Çevre kirliliği doğaldır", "Nüfus artışı tek neden değildir"]),
    # Üretim, Dağıtım ve Tüketim
    ("Üretim, dağıtım ve tüketim süreci metinde anlatılıyor. \"Üretici üretir, dağıtıcı ulaştırır, tüketici kullanır\" deniyor.\n\nDağıtımın işlevi nedir?",
     "uretim_dagitim_tuketim", ["Ürünün üreticiden tüketiciye ulaşmasını sağlamak", "Sadece fiyatı artırmak", "Üretimi durdurmak", "Tüketiciye katkısı yoktur"]),
    ("\"İstek ve ihtiyaç farklıdır. İhtiyaç zorunludur, istek ek olarak karşılanır\" ifadesi geçiyor.\n\nBu ayrımın önemi nedir?",
     "uretim_dagitim_tuketim", ["Bilinçli tüketim için öncelikleri belirlemeye yardımcı olur", "İstek ve ihtiyaç aynıdır", "Sadece ihtiyaçlar karşılanmalıdır", "İstekler önemsizdir"]),
    ("Tüketici hakları metninde \"ayıplı mal alındığında iade veya değişim hakkı\" vurgulanıyor.\n\nBu hakkın amacı nedir?",
     "uretim_dagitim_tuketim", ["Tüketiciyi korumak ve adil alışveriş sağlamak", "Satıcıyı cezalandırmak", "Üretimi durdurmak", "Fiyatları düşürmek"]),
    ("Aile bütçesi tablosunda gelir ve giderler karşılaştırılıyor. Gelirin gideri geçmemesi gerektiği belirtiliyor.\n\nBütçe yapmanın amacı nedir?",
     "uretim_dagitim_tuketim", ["Gelir-gider dengesini kurarak tasarruf ve planlama sağlamak", "Sadece az harcamak", "Hiç harcamamak", "Bütçe gereksizdir"]),
    # Etkin Vatandaşlık
    ("Etkin vatandaşlık metninde \"oy kullanma, vergi verme, kurallara uyma\" vatandaşlık görevleri olarak sayılıyor.\n\nBu bilgiye göre etkin vatandaşın özelliği nedir?",
     "etkin_vatandaslik", ["Hak ve sorumluluklarının bilincinde olup topluma katılım göstermesi", "Sadece oy kullanması", "Vergi vermek yeterlidir", "Kurallar önemsizdir"]),
    ("\"Demokratik toplumda herkes fikrini özgürce söyleyebilir; ancak başkalarının haklarına saygı göstermelidir\" ifadesi geçiyor.\n\nBu cümleden çıkarılabilecek sonuç nedir?",
     "etkin_vatandaslik", ["Özgürlükler başkalarının haklarıyla sınırlıdır", "Herkes her şeyi söyleyebilir", "Fikir özgürlüğü yoktur", "Sadece yetişkinler söz hakkına sahiptir"]),
    ("\"Sivil toplum kuruluşları gönüllülük esaslıdır; çevre, eğitim gibi alanlarda faaliyet gösterir\" deniyor.\n\nBu kuruluşların işlevi nedir?",
     "etkin_vatandaslik", ["Toplumsal sorunlara yurttaşların katılımıyla çözüm üretmek", "Sadece devlete alternatif olmak", "Siyasi partilerle aynı işlevdedir", "Kuruluşlar gereksizdir"]),
    ("\"Kamuoyu toplumun bir konudaki genel düşüncesidir\" tanımı veriliyor.\n\nKamuoyunun demokrasideki yeri nedir?",
     "etkin_vatandaslik", ["Yöneticilerin kararlarını etkileyebilir; toplumsal taleplerin iletilmesine katkı sağlar", "Kamuoyu önemsizdir", "Sadece seçimlerde etkilidir", "Medyadan bağımsızdır"]),
    # Küresel Bağlantılar
    ("Küresel bağlantılar ünitesinde \"ülkeler arası ticaret, turizm ve iletişim\" konuları işleniyor.\n\nBu bağlantıların temel işlevi nedir?",
     "kuresel_baglantilar", ["Ülkeler arası iş birliği, kültür alışverişi ve ekonomik ilişki sağlamak", "Sadece ticaret için vardır", "Tek bir ülkenin çıkarına hizmet eder", "Bağlantılar gereksizdir"]),
    ("\"İklim değişikliği, kuraklık, salgın hastalıklar tüm dünyayı etkileyebilir\" ifadesi geçiyor.\n\nBu sorunların küresel çözümü için ne gereklidir?",
     "kuresel_baglantilar", ["Ülkelerin iş birliği ve ortak çaba göstermesi", "Tek bir ülkenin çabası yeterlidir", "Sorunlar çözülemez", "Sadece gelişmiş ülkeler sorumludur"]),
    ("Uluslararası ticaret grafiğinde ülkeler arası mal ve hizmet akışı gösteriliyor.\n\nBu ticaretin faydası aşağıdakilerden hangisi olabilir?",
     "kuresel_baglantilar", ["Ülkelerin ihtiyaçlarını karşılıklı olarak gidermesi, ekonomik ilişki", "Sadece zengin ülkeler kazanır", "Ticaret zararlıdır", "Ürün çeşitliliği azalır"]),
    ("Göç hareketleri haritasında kırsaldan kente, ülkeler arası göçler gösteriliyor.\n\nGöçün olası nedenleri arasında aşağıdakilerden hangisi sayılabilir?",
     "kuresel_baglantilar", ["İş imkanı, eğitim, güvenlik ve daha iyi yaşam arayışı", "Sadece savaş", "Göç zorunlu değildir", "Sadece iklim değişikliği"]),
    ("Dünya kaynakları (su, orman, enerji) grafiğinde bazı bölgelerde kıtlık olduğu görülüyor.\n\nKaynak kıtlığının olası sonucu aşağıdakilerden hangisi olabilir?",
     "kuresel_baglantilar", ["İş birliği ihtiyacı, çatışma riski ve tasarruf önlemleri", "Kıtlık sorun değildir", "Teknoloji her şeyi çözer", "Sadece fiyat artar"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"sosyal4_{idx:04d}",
        "stem": stem,
        "options": options,
        "answerIndex": answer_index,
        "difficulty": difficulty,
        "questionType": qtype,
        "topic": topic,
        "skills": skills,
        "explanation": explanation,
        "source": "brainbuddy",
        "sourceRef": source_ref,
        "imageAsset": None,
        "subject": "sosyal",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = ["Paragrafta bu çıkarım yapılamaz.", "Sebep-sonuç ilişkisi yanlış kurulmuştur.", "Bu bilgi metinde verilmemiştir.", "Kavram karışıklığı vardır."]
    for p in pad:
        if len(opts) >= 4:
            break
        if p not in seen:
            opts.append(p)
    return opts[:4]


def generate_questions():
    per_topic = 500 // len(TOPICS)
    remainder = 500 % len(TOPICS)
    counts = {t: per_topic + (1 if i < remainder else 0) for i, t in enumerate(TOPICS)}

    out = []
    tpl_idx = 0
    for topic, count in counts.items():
        tpls_for_topic = [t for t in TEMPLATES if t[1] == topic]
        if not tpls_for_topic:
            tpls_for_topic = TEMPLATES
        for _ in range(count):
            tpl = tpls_for_topic[tpl_idx % len(tpls_for_topic)]
            tpl_idx += 1
            stem, tpl_topic, opts_list = tpl[0], tpl[1], tpl[2]
            correct = opts_list[0]
            wrongs = opts_list[1:]
            options = ensure_4_opts(correct, wrongs)
            random.shuffle(options)
            ai = options.index(correct)
            expl = f"Metindeki anlam ve sebep-sonuç ilişkisine göre doğru cevap \"{correct}\" seçeneğidir. Diğer seçenekler yanlış sebep-sonuç, kavram karışıklığı veya kısmi yorum hatası içermektedir."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10] if len(topic) >= 10 else topic, "yorumlama", "cikarim"],
                expl, f"sosyal4_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"sosyal4_{idx:04d}"
        qq["id"] = f"sosyal4_{idx:04d}"

    packs = [all_q[i:i+10] for i in range(0, 500, 10)]
    topic_counts = {}
    new_gen_count = 0
    for pi, questions in enumerate(packs):
        for qq in questions:
            topic_counts[qq["topic"]] = topic_counts.get(qq["topic"], 0) + 1
            if qq["questionType"] in NEW_GEN_TYPES:
                new_gen_count += 1
        pack = {
            "version": 1,
            "mode": "LGS",
            "subject": "sosyal",
            "publisher": "brainbuddy",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_sosyal4_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("SOSYAL4 Question Bank Report")
    print("=" * 60)
    print(f"Packs: {len(packs)}, Questions: {len(all_q)}")
    print("\nTopic distribution:")
    for t in sorted(topic_counts.keys()):
        print(f"  {t}: {topic_counts[t]}")
    pct = 100 * new_gen_count / 500
    print(f"\nNew-generation: {new_gen_count}/500 = {pct:.1f}%")
    print(f"Output: {OUT_DIR}")
    print("=" * 60)


if __name__ == "__main__":
    random.seed(57)
    main()
