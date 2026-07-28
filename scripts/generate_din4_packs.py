#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 4th Grade Din Kültürü ve Ahlak Bilgisi question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: ayet/hadis yorumlama, senaryo tabanlı değer, kavram çıkarımı.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/din4"

TOPICS = [
    "allah_sevgisi",
    "islamin_sartlari",
    "ibadetler_onemi",
    "paylasma_yardimlasma",
    "guzel_ahlak",
    "peygamberleri_taniyalim",
    "hz_muhammed_hayati",
    "kurani_kerim_taniyalim",
    "dua_anlami",
    "temel_dini_kavramlar",
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
    # Allah Sevgisi
    ("\"Allah'ı sevmek, O'nun emirlerine uymak ve yarattıklarına merhametle bakmaktır\" metninde Allah sevgisi nasıl tanımlanıyor?",
     "allah_sevgisi", ["Allah'ın emirlerine uymak ve yaratıklarına merhamet göstermek", "Sadece Allah'ı anmak", "Sadece camiye gitmek", "Allah sevgisi sadece duadadır"]),
    ("\"Allah rahmandır, rahimdir; merhamet sahibidir\" ifadesi neyi vurgular?",
     "allah_sevgisi", ["Allah'ın merhamet eden ve bağışlayan oluşu", "Allah sadece cezalandırır", "Merhamet sadece insana aittir", "Rahman ve Rahim aynı anlamdadır"]),
    ("Senaryo: Zeynep her sabah \"Bismillah\" diyerek başlıyor. Bu davranış neyi gösterir?",
     "allah_sevgisi", ["Allah'ı hatırlama ve O'nun adıyla işe başlama", "Sadece bir alışkanlık", "Zorunlu değildir", "Sadece yemekte söylenir"]),
    ("\"Allah'ı seven, O'nun yarattıklarını da sever\" ifadesinden ne çıkarılabilir?",
     "allah_sevgisi", ["Allah sevgisi yaratıklara şefkat göstermeyle bağlantılıdır", "Sadece insanları sevmek yeterlidir", "Hayvanlar sevilmez", "Allah sevgisi sadece ibadettedir"]),
    # İslam'ın Şartları
    ("Metinde İslam'ın beş şartı sayılıyor: Kelime-i Şehadet, namaz, oruç, zekat, hac. Buna göre kelime-i şehadet neyi ifade eder?",
     "islamin_sartlari", ["Allah'ın birliğine ve Hz. Muhammed'in peygamberliğine şehadet", "Sadece namaza başlama", "Sadece bir söz", "Sadece camide söylenir"]),
    ("\"İslam'ın şartları Müslümanın temel görevlerini gösterir\" cümlesine göre bu şartların amacı nedir?",
     "islamin_sartlari", ["Müslümanın inancını ve pratik hayatını düzenlemek", "Sadece bilmek yeterlidir", "Uygulamak zorunlu değildir", "Sadece Ramazan'da geçerlidir"]),
    ("\"Namaz kılmak İslam'ın ikinci şartıdır. Günde beş vakit kılınır\" bilgisine göre namazın yeri nedir?",
     "islamin_sartlari", ["Farz ibadetlerden biri olması", "Sadece sünnettir", "Sadece Cuma günü kılınır", "Zorunlu değildir"]),
    ("İslam'ın şartları tablosunda oruç üçüncü sırada yer alıyor. Oruç ne zaman farz kılınmıştır?",
     "islamin_sartlari", ["Ramazan ayında tutulur; farz bir ibadettir", "Sadece nafile", "Sadece yaz aylarında", "Zorunlu değildir"]),
    # İbadetler ve Önemi
    ("\"İbadet, Allah'a kulluk ve saygı göstermektir. Namaz, oruç, zekat gibi ibadetler vardır\" metninde ibadetin amacı nedir?",
     "ibadetler_onemi", ["Allah'a kulluk ve yakınlık", "Sadece fiziksel hareket", "İbadet zorunlu değildir", "Sadece Ramazan'da ibadet edilir"]),
    ("\"İbadetler insanı iyi davranışlara yönlendirir\" ifadesi neyi anlatır?",
     "ibadetler_onemi", ["İbadetin ahlaki gelişime katkısı", "İbadet sadece beden hareketidir", "İbadet ahlakı etkilemez", "Sadece namaz ibadettir"]),
    ("Senaryo: Ali \"Namaz kılmak bana huzur veriyor\" diyor. Bu sözle ne ifade edilmektedir?",
     "ibadetler_onemi", ["İbadetin manevi huzur ve tatmin sağladığı", "Sadece fiziksel rahatlık", "Namaz sadece zorunluluktur", "Huzur namazdan gelmez"]),
    ("\"İbadet niyetle başlar. Gösteriş için yapılan ibadet makbul değildir\" ifadesi neyi vurgular?",
     "ibadetler_onemi", ["İbadetin samimiyet ve niyetle değer kazandığı", "Niyet önemsizdir", "Gösteriş de olabilir", "İbadet sadece beden hareketidir"]),
    # Paylaşma ve Yardımlaşma
    ("\"Paylaşmak ve yardımlaşmak İslam'da teşvik edilir. Komşu, akraba ve ihtiyaç sahiplerine destek olmak önemlidir\" paragrafından ne anlaşılmalıdır?",
     "paylasma_yardimlasma", ["İslam paylaşma ve yardımlaşmayı değerli görür", "Sadece aileye yardım edilir", "Yardım zorunlu değildir", "Sadece Ramazan'da paylaşılır"]),
    ("\"Kim bir Müslümanın sıkıntısını giderirse, Allah da onun sıkıntısını giderir\" hadisinin mesajı nedir?",
     "paylasma_yardimlasma", ["Yardım etmenin Allah katında değerli oluşu", "Sadece Müslümanlara yardım edilir", "Yardımın karşılığı yoktur", "Sıkıntı gidermek zorunlu değildir"]),
    ("Senaryo: Mehmet harçlığından bir kısmını yardım kutusuna attı. Bu davranış hangi değerle ilişkilidir?",
     "paylasma_yardimlasma", ["Paylaşma ve cömertlik", "Sadece zekat", "Cömertlik sadece zenginler içindir", "Yardım kutusu zorunlu değildir"]),
    ("\"Veren el alan elden üstündür\" hadisi neyi teşvik eder?",
     "paylasma_yardimlasma", ["Vermeyi ve yardım etmeyi", "Almayı", "Cimriliği", "Sadece zekatı"]),
    # Güzel Ahlak
    ("\"Doğruluk, dürüstlük, yardımseverlik ve saygı güzel ahlaktandır\" cümlesine göre güzel ahlak ne demektir?",
     "guzel_ahlak", ["İyi davranışlar ve erdemli olmak", "Sadece namaz kılmak", "Ahlak sadece camidedir", "Güzel ahlak zorunlu değildir"]),
    ("\"Temizlik imandandır\" hadisi neyi ifade eder?",
     "guzel_ahlak", ["Fiziksel ve manevi temizliğin dinin parçası oluşu", "Sadece abdest yeterlidir", "Temizlik sadece camide önemlidir", "Temizlik zorunlu değildir"]),
    ("Senaryo: Ayşe yanlışlıkla arkadaşının kalemini kırdı. Hemen özür diledi ve yenisini aldı. Bu davranış hangi ahlaki değeri yansıtır?",
     "guzel_ahlak", ["Dürüstlük, özür dileme ve sorumluluk", "Sadece maddi tazmin", "Özür zorunlu değildir", "Kaza olunca sorumluluk yoktur"]),
    ("\"Yalan söylemek münafıklık alametidir\" hadisi hangi ahlaki ilkeyi vurgular?",
     "guzel_ahlak", ["Dürüstlük ve doğruluk", "Cömertlik", "Sabır", "Temizlik"]),
    # Peygamberleri Tanıyalım
    ("\"Peygamberler Allah'ın insanlara gönderdiği elçilerdir. İnsanlara doğru yolu gösterirler\" metninde peygamberin görevi nasıl tanımlanıyor?",
     "peygamberleri_taniyalim", ["Allah'tan aldığı mesajı insanlara iletmek ve doğru yolu göstermek", "Sadece mucize göstermek", "Sadece kitap getirmek", "Peygamberler insan değildir"]),
    ("\"Her peygamber kendi toplumuna gönderilmiştir. Son peygamber Hz. Muhammed tüm insanlığa gönderilmiştir\" ifadesinden ne çıkarılabilir?",
     "peygamberleri_taniyalim", ["Hz. Muhammed'in mesajı evrenseldir", "Önceki peygamberler önemsizdir", "Peygamberler sadece Arap toplumuna gelmiştir", "Hz. Muhammed sadece Mekke'ye gönderilmiştir"]),
    ("Metinde \"Peygamberler güvenilir, doğru sözlü ve akıllıdır\" ifadesi geçiyor. Bu özellikler neyi gösterir?",
     "peygamberleri_taniyalim", ["Peygamberlerin örnek kişilikte oluşu", "Peygamberler sadece normal insanlardır", "Bu özellikler önemsizdir", "Peygamberler hata yapmaz, masumdur (sadece bu cümleden çıkarılamaz)"]),
    ("\"İbrahim, Musa, İsa ve Muhammed peygamberler olarak anılır\" cümlesine göre bu isimler neyi temsil eder?",
     "peygamberleri_taniyalim", ["Allah'ın farklı dönemlerde gönderdiği elçiler", "Sadece tarihi kişiler", "Hepsi aynı dönemde yaşamıştır", "Sadece Hz. Muhammed peygamberdir"]),
    # Hz. Muhammed'in Hayatı
    ("\"Hz. Muhammed Mekke'de doğdu. Çocukluğundan itibaren dürüstlüğü ile tanındı; 'el-Emin' (güvenilir) denirdi\" cümlesinden ne çıkarılabilir?",
     "hz_muhammed_hayati", ["Peygamberimizin güvenilir ve dürüst kişiliği", "Sadece yetişkinken güvenilirdi", "El-Emin sadece bir lakaptır", "Mekke'de herkes güvenilirdi"]),
    ("\"Peygamberimiz çocuklara sevgi gösterir, onların başını okşardı\" ifadesi Hz. Muhammed'in hangi özelliğini gösterir?",
     "hz_muhammed_hayati", ["Merhametli ve şefkatli oluşu", "Sadece çocuklara ilgisi vardı", "Çocuklar önemsizdir", "Bu bir efsanedir"]),
    ("\"Peygamberimiz herkese adil davranırdı; zengin fakir ayrımı yapmazdı\" metni neyi vurgular?",
     "hz_muhammed_hayati", ["Adalet ve eşit muamele", "Sadece zenginlere ilgi", "Adalet sadece mahkemede geçerlidir", "Fakirlere farklı davranılırdı"]),
    ("\"Peygamberimiz hicret sırasında mağarada saklanmış, Allah'ın koruması altında Medine'ye ulaşmıştır\" metni neyi anlatır?",
     "hz_muhammed_hayati", ["Allah'ın peygambere yardımı ve güven", "Mağara her zaman güvenlidir", "Hicret önemsizdir", "Medine'ye yürüyerek gidilmiştir"]),
    # Kur'an-ı Kerim'i Tanıyalım
    ("\"Kur'an-ı Kerim Allah'ın sözüdür. Hz. Muhammed'e Cebrail aracılığıyla indirilmiştir\" cümlesine göre Kur'an'ın kaynağı nedir?",
     "kurani_kerim_taniyalim", ["Kur'an Allah'tan gelen vahiydir", "Kur'an Hz. Muhammed tarafından yazılmıştır", "Kur'an insanların topladığı bir kitaptır", "Cebrail Kur'an'ı yazmıştır"]),
    ("\"Kur'an-ı Kerim değiştirilmeden günümüze ulaşmıştır\" ifadesi neyi vurgular?",
     "kurani_kerim_taniyalim", ["Kur'an'ın lafzen ve manen korunmuş oluşu", "Kur'an sonradan yazılmıştır", "Bazı ayetler değiştirilmiştir", "Koruma önemsizdir"]),
    ("\"Kur'an insanlara doğru yolu gösterir; iyiyi ve kötüyü bildirir\" metninde Kur'an'ın işlevi nasıl anlatılmaktadır?",
     "kurani_kerim_taniyalim", ["Rehberlik ve hidayet kaynağı oluşu", "Sadece tarih kitabıdır", "Kur'an sadece Arapça bilenlere hitap eder", "Kur'an'ın pratik faydası yoktur"]),
    ("Kur'an'ın \"mushaf\" olarak yazılı hali metinde anlatılıyor. Mushaf ne demektir?",
     "kurani_kerim_taniyalim", ["Kur'an'ın kitap halindeki yazılı nüshası", "Kur'an'ın sadece ezberlenmiş hali", "Tefsir kitabı", "Hadis kitabı"]),
    # Dua ve Anlamı
    ("\"Dua kulun Allah ile konuşmasıdır. Her zaman her yerde yapılabilir\" metninde duanın özelliği nedir?",
     "dua_anlami", ["Her zaman her yerde yapılabilir; Allah duaları işitir", "Sadece camide edilir", "Dua sadece namazda okunur", "Dua zorunlu değildir"]),
    ("\"Dua ibadetin özüdür\" hadisi neyi vurgular?",
     "dua_anlami", ["Duanın ibadetler arasında merkezi yeri", "Dua tek başına yeterlidir, diğer ibadetler gerekmez", "Sadece namaz duadır", "Dua önemsizdir"]),
    ("Senaryo: Fatma sınava girmeden önce \"Allah'ım bana yardım et\" diye dua etti. Bu davranış neyi gösterir?",
     "dua_anlami", ["Allah'a güven ve yardım isteme", "Sadece bir alışkanlık", "Dua sadece camide edilir", "Sınavda dua etmek yasaktır"]),
    ("\"Dua ederken samimi olmak, Allah'tan isterken başkalarına zarar vermemek önemlidir\" ifadesi neyi vurgular?",
     "dua_anlami", ["Duanın samimiyet ve iyi niyetle yapılması gerektiği", "Dua sadece sözledir", "Kötü dualar kabul olmaz", "Dua her zaman kabul edilir"]),
    # Temel Dini Kavramlar
    ("\"İman\" kavramı \"Allah'a, peygamberlere ve kitaplara inanmak\" olarak tanımlanıyor. İman sahibi nasıl davranır?",
     "temel_dini_kavramlar", ["İnandığı değerlere uygun yaşar", "İman sadece sözledir", "Davranış önemli değildir", "İman sadece namazda gösterilir"]),
    ("\"Şükür\" kavramı metinde nasıl açıklanıyor?",
     "temel_dini_kavramlar", ["Allah'ın nimetlerine karşı minnet duymak", "Sadece sözle teşekkür", "Şükür sadece namazda yapılır", "Şükür zorunlu değildir"]),
    ("\"Sabır\" kavramı İslam'da nasıl tanımlanır?",
     "temel_dini_kavramlar", ["Zorluklara karşı dayanma ve metanet", "Sadece oruçta sabretmek", "Sessiz kalmak", "Hiçbir şey yapmamak"]),
    ("\"Takva\" kavramı \"Allah'tan sakınmak, günahlardan kaçınmak\" olarak tanımlanıyor. Takva sahibi nasıl davranır?",
     "temel_dini_kavramlar", ["İyilik yapar, kötülükten kaçınır", "Sadece ibadet eder", "Takva sadece alimlerde olur", "Takva zorunlu değildir"]),
    # Extra templates for balance
    ("\"Allah gökleri ve yeri, geceyi ve gündüzü yaratmıştır\" ayeti neyi vurgular?",
     "allah_sevgisi", ["Allah'ın yaratıcı oluşu ve kâinatın O'nun eseri olması", "Sadece gökleri yaratmıştır", "Gece ve gündüz kendiliğinden oluşmuştur", "Yaratma bir kere olmuştur"]),
    ("\"İftar\" ve \"sahur\" kavramları metinde geçiyor. Bu kavramlar neyi ifade eder?",
     "ibadetler_onemi", ["İftar orucu açmak, sahur oruca başlamadan önce yenen yemek", "İkisi aynı şeydir", "Sadece iftar vardır", "Sahur oruçtan sonra yenir"]),
    ("\"Komşusu açken tok yatan bizden değildir\" hadisi neyi vurgular?",
     "paylasma_yardimlasma", ["Komşuya karşı sorumluluk ve yardım", "Sadece yemek vermek", "Komşu sadece yan dairedekindir", "Aç komşu yoktur"]),
    ("\"Gıybet, birinin ardından hoşuna gitmeyecek söz söylemektir\" tanımına göre gıybet neden sakınılmalıdır?",
     "guzel_ahlak", ["Kişiye gıyabında zarar verir, güveni sarsar", "Gıybet zararsızdır", "Doğruysa söylenebilir", "Gıybet ibadettir"]),
    ("\"Namazı kıl, zekatı ver\" ayetinde hangi iki ibadet birlikte emredilmektedir?",
     "islamin_sartlari", ["Namaz ve zekat", "Oruç ve hac", "Namaz ve oruç", "Zekat ve kurban"]),
    ("\"Müminler kardeştir\" ayetinin mesajı nedir?",
     "peygamberleri_taniyalim", ["Müslümanlar birbirine kardeşçe davranmalıdır", "Sadece kan kardeşleri kastedilir", "Kardeşlik sadece camidedir", "Bu ayet geçersizdir"]),
    ("\"Anne ve babaya iyilik edin\" ayeti neyi emretmektedir?",
     "guzel_ahlak", ["Anne ve babaya saygı ve iyilik", "Sadece maddi yardım", "Sadece anne önemlidir", "Büyükler her zaman haklıdır"]),
    ("\"Kim bir hayır işlerse ona kat kat karşılık vardır\" ayetinin mesajı nedir?",
     "dua_anlami", ["İyiliğin Allah katında karşılığı olduğu", "Sadece maddi karşılık", "İyilik zorunlu değildir", "Karşılık sadece dünyadadır"]),
    ("\"Fitre\" kavramı Ramazan'da sıkça geçer. Fitre ne demektir?",
     "temel_dini_kavramlar", ["Ramazan'da verilen sadaka/yardım", "Oruç tutmamak", "İftar yemeği", "Teravih namazı"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"din4_{idx:04d}",
        "stem": stem,
        "options": options,
        "answerIndex": answer_index,
        "difficulty": difficulty,
        "questionType": qtype,
        "topic": topic,
        "skills": skills,
        "explanation": explanation,
        "source": "edumio",
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
                expl, f"din4_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"din4_{idx:04d}"
        qq["id"] = f"din4_{idx:04d}"

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
            "publisher": "edumio",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_din4_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("DIN4 Question Bank Report")
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
    random.seed(51)
    main()
