#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 5th Grade Din Kültürü ve Ahlak Bilgisi question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: ayet/hadis yorumlama, senaryo tabanlı ahlak, kavram çıkarımı.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/din5"

TOPICS = [
    "allah_inanci",
    "ibadet_onemi",
    "ramazan_oruc",
    "paylasma_yardimlasma",
    "hz_muhammed",
    "kur_an_ozellikleri",
    "guzel_ahlak",
    "temel_dini_kavramlar",
    "ayet_hadis_yorumlama",
    "islam_degerler",
]

NEW_GEN_TYPES = [
    "ayet_hadis_yorumlama",
    "senaryo_deger",
    "kavram_cikarimi",
    "metin_analizi",
    "paragraf_yorumlama",
    "deger_cikarimi",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # Allah İnancı
    ("Metinde \"Allah birdir, her şeyi yaratandır ve her şey O'na muhtaçtır\" ifadesi geçiyor. Buna göre Allah'ın birliği ne anlama gelir?",
     "allah_inanci", ["Allah tektir, ortağı yoktur; her şey O'nun yaratmasıyladır", "Allah birden fazladır", "Yaratma sadece başlangıçta olmuştur", "Allah muhtaçtır"]),
    ("\"Allah her şeyi bilir, her şeyi görür, her şeyi işitir\" cümlesinden Allah'ın hangi sıfatları çıkarılabilir?",
     "allah_inanci", ["İlim (bilme), basar (görme), semî (işitme) sıfatları", "Sadece yaratma sıfatı", "Allah sadece görür", "Bu sıfatlar insanda da vardır"]),
    ("\"Allah rahmandır, rahimdir; merhamet sahibidir\" ifadesi neyi vurgular?",
     "allah_inanci", ["Allah'ın merhamet eden ve bağışlayan oluşu", "Allah sadece cezalandırır", "Merhamet sadece insana aittir", "Rahman ve Rahim aynı anlama gelir, fark yoktur"]),
    ("\"O (Allah) her an her yerde hazırdır; gizli ve açık her şeyi bilir\" paragrafından çıkarılabilecek sonuç nedir?",
     "allah_inanci", ["Allah'ın her yerde hazır ve her şeyi bilen oluşu", "Allah sadece camidedir", "Gizli olanları bilmez", "Allah bazen habersiz kalır"]),
    # İbadet ve Önemi
    ("\"İbadet, Allah'a kulluk ve saygı göstermektir. Namaz, oruç, zekat gibi farz ibadetler vardır\" metninde ibadetin amacı nedir?",
     "ibadet_onemi", ["Allah'a kulluk ve yakınlık", "Sadece fiziksel hareket", "İbadet zorunlu değildir", "Sadece Ramazan'da ibadet edilir"]),
    ("\"İbadetler insanı iyi davranışlara yönlendirir\" ifadesi neyi anlatır?",
     "ibadet_onemi", ["İbadetin ahlaki gelişime katkısı", "İbadet sadece beden hareketidir", "İbadet ahlakı etkilemez", "Sadece namaz ibadettir"]),
    ("Senaryo: Ali \"Namaz kılmak bana huzur veriyor\" diyor. Bu sözle ne ifade edilmek istenmektedir?",
     "ibadet_onemi", ["İbadetin manevi huzur ve tatmin sağladığı", "Sadece fiziksel rahatlık", "Namaz sadece zorunluluktur", "Huzur namazdan gelmez"]),
    # Ramazan ve Oruç
    ("\"Ramazan ayında oruç tutulur. Oruç, tan yerinin ağarmasından güneş batana kadar yemek, içmek ve kötü sözlerden uzak durmaktır\" cümlesine göre orucun temel şartı nedir?",
     "ramazan_oruc", ["Belirli süre boyunca yemek, içmek ve kötü sözlerden sakınmak", "Sadece yemek yememek", "Oruç sadece gündüz tutulur, gece serbesttir", "Oruç zorunlu değildir"]),
    ("\"Ramazan ayı rahmet, mağfiret ve kurtuluş ayıdır\" hadisi neyi vurgular?",
     "ramazan_oruc", ["Ramazan'ın manevi bereket ve bağışlanma fırsatı oluşu", "Sadece aç kalma ayı", "Ramazan sadece oruçtür", "Bu ayda günah işlenmez, otomatik bağışlanır"]),
    ("Senaryo: Zeynep oruçluyken bir arkadaşı yemek teklif etti. Zeynep \"Teşekkürler, oruçluyum\" dedi. Bu davranış nasıl değerlendirilir?",
     "ramazan_oruc", ["Oruca saygı ve kararlılık göstergesi", "Kaba bir red", "Arkadaşlığa aykırı", "Oruçlu olduğunu söylemek uygunsuzdur"]),
    ("\"İftar\" ve \"sahur\" kavramları metinde geçiyor. Bu kavramlar neyi ifade eder?",
     "ramazan_oruc", ["İftar orucu açmak, sahur oruca başlamadan önce yenen yemek", "İkisi aynı şeydir", "Sadece iftar vardır", "Sahur oruçtan sonra yenir"]),
    # Paylaşma ve Yardımlaşma
    ("\"Paylaşmak ve yardımlaşmak İslam'da teşvik edilir. Komşu, akraba ve ihtiyaç sahiplerine destek olmak önemlidir\" paragrafından ne anlaşılmalıdır?",
     "paylasma_yardimlasma", ["İslam paylaşma ve yardımlaşmayı değerli görür", "Sadece aileye yardım edilir", "Yardım zorunlu değildir", "Sadece Ramazan'da paylaşılır"]),
    ("\"Kim bir Müslümanın sıkıntısını giderirse, Allah da onun sıkıntısını giderir\" hadisinin mesajı nedir?",
     "paylasma_yardimlasma", ["Yardım etmenin Allah katında değerli oluşu", "Sadece Müslümanlara yardım edilir", "Yardımın karşılığı yoktur", "Sıkıntı gidermek zorunlu değildir"]),
    ("Senaryo: Mehmet harçlığından bir kısmını yardım kutusuna attı. Bu davranış hangi değerle ilişkilidir?",
     "paylasma_yardimlasma", ["Paylaşma ve cömertlik", "Sadece zekat", "Cömertlik sadece zenginler içindir", "Yardım kutusu zorunlu değildir"]),
    # Hz. Muhammed'i Tanıyalım
    ("\"Hz. Muhammed Mekke'de doğdu. Çocukluğundan itibaren dürüstlüğü ile tanındı; 'el-Emin' (güvenilir) denirdi\" cümlesinden ne çıkarılabilir?",
     "hz_muhammed", ["Peygamberimizin güvenilir ve dürüst kişiliği", "Sadece yetişkinken güvenilirdi", "El-Emin sadece bir lakaptır", "Mekke'de herkes güvenilirdi"]),
    ("\"Peygamberimiz çocuklara sevgi gösterir, onların başını okşardı\" ifadesi Hz. Muhammed'in hangi özelliğini gösterir?",
     "hz_muhammed", ["Merhametli ve şefkatli oluşu", "Sadece çocuklara ilgisi vardı", "Çocuklar önemsizdir", "Bu bir efsanedir"]),
    ("\"Peygamberimiz herkese adil davranırdı; zengin fakir ayrımı yapmazdı\" metni neyi vurgular?",
     "hz_muhammed", ["Adalet ve eşit muamele", "Sadece zenginlere ilgi", "Adalet sadece mahkemede geçerlidir", "Fakirlere farklı davranılırdı"]),
    # Kur'an-ı Kerim ve Temel Özellikleri
    ("\"Kur'an-ı Kerim Allah'ın sözüdür. Hz. Muhammed'e Cebrail aracılığıyla indirilmiştir\" cümlesine göre Kur'an'ın kaynağı nedir?",
     "kur_an_ozellikleri", ["Kur'an Allah'tan gelen vahiydir", "Kur'an Hz. Muhammed tarafından yazılmıştır", "Kur'an insanların topladığı bir kitaptır", "Cebrail Kur'an'ı yazmıştır"]),
    ("\"Kur'an-ı Kerim değiştirilmeden günümüze ulaşmıştır\" ifadesi neyi vurgular?",
     "kur_an_ozellikleri", ["Kur'an'ın lafzen ve manen korunmuş oluşu", "Kur'an sonradan yazılmıştır", "Bazı ayetler değiştirilmiştir", "Koruma önemsizdir"]),
    ("\"Kur'an insanlara doğru yolu gösterir; iyiyi ve kötüyü bildirir\" metninde Kur'an'ın işlevi nasıl anlatılmaktadır?",
     "kur_an_ozellikleri", ["Rehberlik ve hidayet kaynağı oluşu", "Sadece tarih kitabıdır", "Kur'an sadece Arapça bilenlere hitap eder", "Kur'an'ın pratik faydası yoktur"]),
    # Güzel Ahlak
    ("\"Doğruluk, dürüstlük, yardımseverlik ve saygı güzel ahlaktandır\" cümlesine göre güzel ahlak ne demektir?",
     "guzel_ahlak", ["İyi davranışlar ve erdemli olmak", "Sadece namaz kılmak", "Ahlak sadece camidedir", "Güzel ahlak zorunlu değildir"]),
    ("\"Temizlik imandandır\" hadisi neyi ifade eder?",
     "guzel_ahlak", ["Fiziksel ve manevi temizliğin dinin parçası oluşu", "Sadece abdest yeterlidir", "Temizlik sadece camide önemlidir", "Temizlik zorunlu değildir"]),
    ("Senaryo: Ayşe yanlışlıkla arkadaşının kalemini kırdı. Hemen özür diledi ve yenisini aldı. Bu davranış hangi ahlaki değeri yansıtır?",
     "guzel_ahlak", ["Dürüstlük, özür dileme ve sorumluluk", "Sadece maddi tazmin", "Özür zorunlu değildir", "Kaza olunca sorumluluk yoktur"]),
    ("\"Yalan söylemek münafıklık alametidir\" hadisi hangi ahlaki ilkeyi vurgular?",
     "guzel_ahlak", ["Dürüstlük ve doğruluk", "Cömertlik", "Sabır", "Temizlik"]),
    # Temel Dini Kavramlar
    ("\"İman\" kavramı \"Allah'a, peygamberlere ve kitaplara inanmak\" olarak tanımlanıyor. İman sahibi nasıl davranır?",
     "temel_dini_kavramlar", ["İnandığı değerlere uygun yaşar", "İman sadece sözledir", "Davranış önemli değildir", "İman sadece namazda gösterilir"]),
    ("\"Şükür\" kavramı metinde nasıl açıklanıyor?",
     "temel_dini_kavramlar", ["Allah'ın nimetlerine karşı minnet duymak", "Sadece sözle teşekkür", "Şükür sadece namazda yapılır", "Şükür zorunlu değildir"]),
    ("\"Sabır\" kavramı İslam'da nasıl tanımlanır?",
     "temel_dini_kavramlar", ["Zorluklara karşı dayanma ve metanet", "Sadece oruçta sabretmek", "Sessiz kalmak", "Hiçbir şey yapmamak"]),
    ("\"Dua\" kulun Allah ile konuşmasıdır. Duanın özelliği nedir?",
     "temel_dini_kavramlar", ["Her zaman her yerde yapılabilir; Allah duaları işitir", "Sadece camide edilir", "Dua sadece namazda okunur", "Dua zorunlu değildir"]),
    # Ayet ve Hadis Yorumlama
    ("\"Namazı kıl, zekatı ver\" ayetinde hangi iki ibadet birlikte emredilmektedir?",
     "ayet_hadis_yorumlama", ["Namaz ve zekat", "Oruç ve hac", "Namaz ve oruç", "Zekat ve kurban"]),
    ("\"Müminler kardeştir\" ayetinin mesajı nedir?",
     "ayet_hadis_yorumlama", ["Müslümanlar birbirine kardeşçe davranmalıdır", "Sadece kan kardeşleri kastedilir", "Kardeşlik sadece camidedir", "Bu ayet geçersizdir"]),
    ("\"Anne ve babaya iyilik edin\" ayeti neyi emretmektedir?",
     "ayet_hadis_yorumlama", ["Anne ve babaya saygı ve iyilik", "Sadece maddi yardım", "Sadece anne önemlidir", "Büyükler her zaman haklıdır"]),
    ("\"Komşusu açken tok yatan bizden değildir\" hadisi neyi vurgular?",
     "ayet_hadis_yorumlama", ["Komşuya karşı sorumluluk ve yardım", "Sadece yemek vermek", "Komşu sadece yan dairedekindir", "Aç komşu yoktur"]),
    # İslam'da Değerler
    ("\"İslam'da adalet herkese hakkını vermektir\" ifadesi neyi vurgular?",
     "islam_degerler", ["Adaletin tarafsız ve hakkaniyetli olması", "Adalet sadece mahkemede geçerlidir", "Adalet zorunlu değildir", "Sadece Müslümanlara adil olunur"]),
    ("\"Büyüklere saygı, küçüklere sevgi\" değeri hangi ilişkiyi vurgular?",
     "islam_degerler", ["Nesiller arası karşılıklı saygı ve sevgi", "Sadece büyükler saygı gösterir", "Küçükler önemsizdir", "Bu değer eskimiştir"]),
    ("\"Doğruluk, dürüstlük, yardımlaşma ve saygı temel değerlerimizdendir\" cümlesine göre bu değerlerin toplumdaki yeri nedir?",
     "islam_degerler", ["Toplumsal düzen ve huzurun temelidir", "Sadece ailede geçerlidir", "Bu değerler eskimiştir", "Sadece dinde önemlidir"]),
    ("\"Hak\" kavramı metinde \"kişinin başkalarına karşı taleplerinin sınırları\" olarak açıklanıyor. Haklara saygı ne gerektirir?",
     "islam_degerler", ["Başkalarının hakkına saygı göstermek", "Sadece kendi hakkımızı korumak", "Haklar önemsizdir", "Hak sadece mahkemede geçerlidir"]),
    # Extra templates for balance
    ("\"Allah gökleri ve yeri, geceyi ve gündüzü yaratmıştır\" ayeti neyi vurgular?",
     "allah_inanci", ["Allah'ın yaratıcı oluşu ve kâinatın O'nun eseri olması", "Sadece gökleri yaratmıştır", "Gece ve gündüz kendiliğinden oluşmuştur", "Yaratma bir kere olmuştur"]),
    ("\"İbadet niyetle başlar. Gösteriş için yapılan ibadet makbul değildir\" ifadesi neyi vurgular?",
     "ibadet_onemi", ["İbadetin samimiyet ve niyetle değer kazandığı", "Niyet önemsizdir", "Gösteriş de olabilir", "İbadet sadece beden hareketidir"]),
    ("\"Fitre\" ve \"zekat\" kavramları Ramazan'da sıkça geçer. Fitre ne demektir?",
     "ramazan_oruc", ["Ramazan'da verilen sadaka/yardım", "Oruç tutmamak", "İftar yemeği", "Teravih namazı"]),
    ("\"Veren el alan elden üstündür\" hadisi neyi teşvik eder?",
     "paylasma_yardimlasma", ["Vermeyi ve yardım etmeyi", "Almayı", "Cimriliği", "Sadece zekatı"]),
    ("\"Peygamberimiz hicret sırasında mağarada saklanmış, Allah'ın koruması altında Medine'ye ulaşmıştır\" metni neyi anlatır?",
     "hz_muhammed", ["Allah'ın peygambere yardımı ve güven", "Mağara her zaman güvenlidir", "Hicret önemsizdir", "Medine'ye yürüyerek gidilmiştir"]),
    ("Kur'an'ın \"mushaf\" olarak yazılı hali metinde anlatılıyor. Mushaf ne demektir?",
     "kur_an_ozellikleri", ["Kur'an'ın kitap halindeki yazılı nüshası", "Kur'an'ın sadece ezberlenmiş hali", "Tefsir kitabı", "Hadis kitabı"]),
    ("\"Gıybet, birinin ardından hoşuna gitmeyecek söz söylemektir\" tanımına göre gıybet neden sakınılmalıdır?",
     "guzel_ahlak", ["Kişiye gıyabında zarar verir, güveni sarsar", "Gıybet zararsızdır", "Doğruysa söylenebilir", "Gıybet ibadettir"]),
    ("\"Takva\" kavramı \"Allah'tan sakınmak, günahlardan kaçınmak\" olarak tanımlanıyor. Takva sahibi nasıl davranır?",
     "temel_dini_kavramlar", ["İyilik yapar, kötülükten kaçınır", "Sadece ibadet eder", "Takva sadece alimlerde olur", "Takva zorunlu değildir"]),
    ("\"Kim bir hayır işlerse ona kat kat karşılık vardır\" ayetinin mesajı nedir?",
     "ayet_hadis_yorumlama", ["İyiliğin Allah katında karşılığı olduğu", "Sadece maddi karşılık", "İyilik zorunlu değildir", "Karşılık sadece dünyadadır"]),
    ("\"İslam\" kelimesi metinde nasıl açıklanıyor?",
     "islam_degerler", ["Allah'a teslim olmak, barış ve esenlik", "Sadece namaz kılmak", "Arapça bir kelime", "Sadece oruç tutmak"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"din5_{idx:04d}",
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
        "subject": "din",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = ["Metinde bu bilgi verilmemektedir.", "Bu çıkarım yapılamaz.", "Bu yanlış bir yorumdur.", "Paragrafta bu konu işlenmemektedir."]
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
            expl = f"Metindeki anlam ve bağlam doğrultusunda doğru cevap \"{correct}\" seçeneğidir. Diğer seçenekler metnin mesajını yanlış yorumlamakta, kavram karışıklığına düşmekte veya kısmen doğru ama eksik kalmaktadır."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10] if len(topic) >= 10 else topic, "yorumlama", "cikarim"],
                expl, f"din5_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"din5_{idx:04d}"
        qq["id"] = f"din5_{idx:04d}"

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
            "subject": "din",
            "publisher": "brainbuddy",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_din5_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("DIN5 Question Bank Report")
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
    random.seed(54)
    main()
