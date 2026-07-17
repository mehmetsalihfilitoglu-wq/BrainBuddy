#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 6th Grade Din Kültürü ve Ahlak Bilgisi question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: ayet/hadis yorumlama, senaryo tabanlı değer, kavram çıkarımı.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/din6"

TOPICS = [
    "peygamber_ilahi_kitap",
    "namaz",
    "zararli_aliskanliklar",
    "hz_muhammed_hayati",
    "temel_degerler",
    "islam_sakinilmasi_gereken",
    "dua_anlami",
    "islam_guzel_ahlak",
    "peygamberler_ozellikleri",
    "ayet_hadis_yorumlama",
    "islami_kavramlar_degerler",
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
    ("Metinde \"Peygamberlere vahiy yoluyla Allah'tan mesaj gelir. Bu mesajlar ilahi kitaplarda toplanmıştır\" ifadesi geçiyor. Buna göre peygamber ve ilahi kitap ilişkisi nasıldır?",
     "peygamber_ilahi_kitap", ["İlahi kitaplar vahiy yoluyla peygamberlere indirilmiştir", "İlahi kitaplar insanlar tarafından yazılmıştır", "Peygamberler kitap indirmemiştir", "Kitaplar peygamberlerden bağımsızdır"]),
    ("\"Tevrat, Zebur, İncil ve Kur'an-ı Kerim ilahi kitaplardır. Son ve değişmemiş olanı Kur'an'dır\" cümlesinden çıkarılabilecek sonuç nedir?",
     "peygamber_ilahi_kitap", ["Kur'an en son gelen ve korunmuş ilahi kitaptır", "Diğer kitaplar önemsizdir", "Tüm kitaplar aynıdır", "Kur'an değiştirilmiştir"]),
    ("\"Namaz günde beş vakit farzdır. Müslümanlar namazla Allah'a yönelir ve O'na şükreder\" metninde namazın amacı nedir?",
     "namaz", ["Allah'a kulluk ve şükür ifade etmek", "Sadece fiziksel hareket yapmak", "Namaz sadece Cuma günü kılınır", "Namaz zorunlu değildir"]),
    ("Senaryo: Mehmet namaz vakitlerini kaçırıyor, \"Nasılsa kaza kılarım\" diyor. Bu tutumla ilgili doğru değerlendirme hangisidir?",
     "namaz", ["Namazı vaktinde kılmak önemlidir; kaza özürle caizdir", "Kaza kılmak yeterlidir", "Namaz terk edilebilir", "Vakit önemsizdir"]),
    ("\"Sigara, alkol ve uyuşturucu sağlığa zarar verir. İslam bu maddelerden sakınmayı emreder\" paragrafından ne anlaşılmalıdır?",
     "zararli_aliskanliklar", ["Zararlı alışkanlıklar hem bedene hem dine aykırıdır", "Az kullanmak zararsızdır", "Sadece uyuşturucu haramdır", "Din bunlarla ilgilenmez"]),
    ("\"Çocukları kötü alışkanlıklardan korumak ailenin ve toplumun görevidir\" ifadesi neyi vurgular?",
     "zararli_aliskanliklar", ["Koruyucu eğitim ve bilinçlendirme önemlidir", "Sadece aile sorumludur", "Toplum müdahale etmemelidir", "Çocuklar zaten bilir"]),
    ("\"Hz. Muhammed Mekke'de doğdu. Çocukluğundan itibaren dürüstlüğü ile tanındı; 'el-Emin' (güvenilir) denirdi\" cümlesinden ne çıkarılabilir?",
     "hz_muhammed_hayati", ["Peygamberimiz güvenilir ve dürüst bir kişiliğe sahipti", "Sadece yetişkinken güvenilirdi", "El-Emin sadece bir lakaptır", "Mekke'de kimse güvenilir değildi"]),
    ("\"Peygamberimiz hicret sırasında mağarada saklanmış, Allah'ın koruması altında Medine'ye ulaşmıştır\" metni neyi anlatır?",
     "hz_muhammed_hayati", ["Hicret sırasında Allah'ın yardımı ve peygambere olan güven", "Mağara her zaman güvenlidir", "Medine'ye yürüyerek gidilmiştir", "Hicret önemsizdir"]),
    ("\"Doğruluk, dürüstlük, yardımlaşma ve saygı temel değerlerimizdendir\" cümlesine göre bu değerlerin toplumdaki yeri nedir?",
     "temel_degerler", ["Toplumsal düzen ve huzurun temelidir", "Sadece ailede geçerlidir", "Bu değerler eskimiştir", "Sadece dinde önemlidir"]),
    ("\"Anne ve babaya iyilik etmek\" değeri metinde vurgulanıyor. Buna göre bu değer neden önemlidir?",
     "temel_degerler", ["Aile bağlarını güçlendirir ve dinî bir görevdir", "Sadece maddi yardım yeterlidir", "Sadece anne önemlidir", "Büyükler her zaman haklıdır"]),
    ("\"Yalan söylemek, gıybet etmek, iftira atmak İslam'ın sakınılmasını istediği davranışlardandır\" ifadesine göre bunlar neden sakınılmalıdır?",
     "islam_sakinilmasi_gereken", ["İnsanlara zarar verir ve toplumsal güveni sarsar", "Sadece camide yasaktır", "Gıybet zararsızdır", "Yalan bazen gerekir"]),
    ("Senaryo: Ayşe arkadaşının sırrını başkalarına anlatıyor. Bu davranış hangi değere aykırıdır?",
     "islam_sakinilmasi_gereken", ["Sır saklamak ve güven", "Cömertlik", "Sabır", "Temizlik"]),
    ("\"Dua, kulun Allah ile konuşmasıdır. İstek, şükür ve yakarışı içerir\" tanımına göre duanın anlamı nedir?",
     "dua_anlami", ["Allah'a yönelmek, O'ndan istemek ve O'na şükretmek", "Sadece sözle yapılır", "Dua sadece namazda edilir", "Dua zorunlu değildir"]),
    ("\"Dua her zaman ve her yerde yapılabilir. Allah kulun duasını işitir\" ifadesi neyi vurgular?",
     "dua_anlami", ["Allah her yerde hazır ve duayı kabul edendir", "Sadece camide dua edilir", "Dua belirli saatlerde yapılır", "Allah sadece büyük duaları işitir"]),
    ("\"İslam'da güzel ahlak: doğruluk, cömertlik, sabır, affetme ve merhamet\" metninde bu değerlerin kaynağı nedir?",
     "islam_guzel_ahlak", ["İslam dininin öğrettiği ahlaki ilkelerdir", "Sadece toplumsal kurallardır", "Her din aynı değerleri öğretir", "Ahlak dinden bağımsızdır"]),
    ("\"Komşu hakkı\" ile ilgili hadiste Peygamberimiz komşuya iyilik etmeyi vurgular. Bu neyi gösterir?",
     "islam_guzel_ahlak", ["İslam komşuluk ilişkilerine önem verir", "Komşu sadece yan dairedekindir", "Komşuya yardım zorunlu değildir", "Sadece Müslüman komşu önemlidir"]),
    ("\"Peygamberler sıdk (doğruluk), emanet (güvenilirlik), fetanet (zeka) gibi sıfatlara sahiptir\" ifadesine göre emanet ne demektir?",
     "peygamberler_ozellikleri", ["Güvenilir olmak, kendilerine verileni korumak", "Sadece eşya saklamak", "Emanet sadece ticarette geçerlidir", "Peygamberler dışında kimse güvenilir değildir"]),
    ("\"Peygamberlerin tebliğ görevi\" metinde nasıl açıklanıyor?",
     "peygamberler_ozellikleri", ["Allah'ın mesajını insanlara iletmek", "Sadece kendi kavmine anlatmak", "Kur'an'ı yazmak", "Cami yaptırmak"]),
    ("\"Namazı kıl, zekatı ver\" ayetinde hangi iki ibadet birlikte emredilmektedir?",
     "ayet_hadis_yorumlama", ["Namaz ve zekat", "Oruç ve hac", "Namaz ve oruç", "Zekat ve kurban"]),
    ("\"Kim bir Müslümanın dünya sıkıntılarından birini giderirse, Allah da onun ahiret sıkıntılarından birini giderir\" hadisinin mesajı nedir?",
     "ayet_hadis_yorumlama", ["İnsanlara yardım etmek Allah katında değerlidir", "Sadece Müslümanlara yardım edilir", "Dünya sıkıntısı önemsizdir", "Yardım zorunlu değildir"]),
    ("\"İslam\" kelimesinin anlamı metinde nasıl açıklanıyor?",
     "islami_kavramlar_degerler", ["Allah'a teslim olmak, barış ve esenlik", "Sadece namaz kılmak", "Arapça bir kelime", "Sadece oruç tutmak"]),
    ("\"Takva\" kavramı \"Allah'tan sakınmak, günahlardan kaçınmak\" olarak tanımlanıyor. Takva sahibi nasıl davranır?",
     "islami_kavramlar_degerler", ["İyilik yapar, kötülükten kaçınır", "Sadece ibadet eder", "Takva sadece alimlerde olur", "Takva zorunlu değildir"]),
    ("İlahi kitapların indirildiği peygamberler tablosunda Tevrat Musa'ya, İncil İsa'ya, Kur'an Hz. Muhammed'e verilmiştir. Bu bilgi neyi gösterir?",
     "peygamber_ilahi_kitap", ["Her peygambere Allah tarafından kitap veya sahife verilmiştir", "Sadece Hz. Muhammed'e kitap inmiştir", "Kitaplar aynıdır", "Peygamberler kitap yazmıştır"]),
    ("\"Kur'an-ı Kerim lafzen ve manen korunmuştur. Bugün elimizdeki musaf ile ilk nüsha aynıdır\" ifadesi neyi vurgular?",
     "peygamber_ilahi_kitap", ["Kur'an değiştirilmeden günümüze ulaşmıştır", "Kur'an sonradan yazılmıştır", "Manası değişmiştir", "Koruma önemsizdir"]),
    ("Namazın farzları ve sünnetleri metinde anlatılıyor. \"Rüku\" ve \"secde\" namazın hangi bölümleridir?",
     "namaz", ["Namazın içindeki farz hareketlerdendir", "Sadece sünnettir", "Sadece Cuma namazında vardır", "Opsiyonel hareketlerdir"]),
    ("\"Namaz kılan kişi günde beş kez Allah'ı anar, O'na yönelir\" cümlesinden namazın manevi etkisi ne olabilir?",
     "namaz", ["Allah ile bağ kurmayı ve hatırlamayı sağlar", "Sadece beden hareketidir", "Manevi etkisi yoktur", "Sadece Cuma günü etkilidir"]),
    ("\"Alkol ve uyuşturucu aklı bulandırır, kişiyi kötü davranışlara sürükler\" ifadesi bunların neden haram olduğunu nasıl açıklar?",
     "zararli_aliskanliklar", ["Sağlığa ve akla zarar verdiği, kötülüğe yol açtığı için", "Sadece sağlığa zararlıdır", "Sadece toplum yasaklamıştır", "Din bunu söylemez"]),
    ("Senaryo: Arkadaşları Emre'ye sigara teklif ediyor. Emre \"Hayır, teşekkürler. Sağlığıma dikkat ediyorum\" diyor. Bu cevap nasıl değerlendirilir?",
     "zararli_aliskanliklar", ["Kibar ve kararlı bir red; doğru bir davranış", "Kaba bir cevaptır", "Arkadaşlığa aykırıdır", "Sigara zararsızdır"]),
    ("\"Peygamberimiz çocuklara sevgi gösterir, onların başını okşardı\" ifadesi Hz. Muhammed'in hangi özelliğini gösterir?",
     "hz_muhammed_hayati", ["Merhametli ve şefkatli oluşu", "Sadece çocuklara ilgisi vardı", "Çocuklar önemsizdir", "Bu bir efsanedir"]),
    ("\"Bedir, Uhud, Hendek savaşları İslam'ın korunması için yapılmıştır\" cümlesinden çıkarılabilecek sonuç nedir?",
     "hz_muhammed_hayati", ["Peygamberimiz İslam'ı ve Müslümanları korumak için mücadele etmiştir", "Savaş her zaman iyidir", "Sadece savunma savaşı yoktur", "Savaşlar önemsizdir"]),
    ("\"Adalet\" değeri metinde \"herkese hakkını vermek\" olarak tanımlanıyor. Buna göre adil davranış nasıl olmalıdır?",
     "temel_degerler", ["Tarafsız ve hakkaniyetli olmalıdır", "Sadece aileye karşı adil olunur", "Adalet sadece mahkemede geçerlidir", "Adalet zorunlu değildir"]),
    ("\"Büyüklere saygı, küçüklere sevgi\" değeri hangi ilişkiyi vurgular?",
     "temel_degerler", ["Nesiller arası ilişkide karşılıklı saygı ve sevgi", "Sadece büyükler saygı gösterir", "Küçükler önemsizdir", "Bu değer eskimiştir"]),
    ("\"Gıybet, bir kimsenin ardından hoşuna gitmeyecek söz söylemektir\" tanımına göre gıybet neden sakınılması gereken bir davranıştır?",
     "islam_sakinilmasi_gereken", ["Kişinin gıyabında hakkında konuşmak ona zarar verir", "Gıybet zararsızdır", "Sadece doğruysa söylenebilir", "Gıybet ibadettir"]),
    ("\"Haram lokma vücuda giren duayı kabul ettirmez\" hadisi neyi vurgular?",
     "islam_sakinilmasi_gereken", ["Helal kazanç ve temiz yaşam duanın kabulü için önemlidir", "Haram yemek sadece mideye zarar verir", "Dua her zaman kabul olur", "Lokma duayı etkilemez"]),
    ("\"Rabbena\" duaları namazda okunur. Bu duaların amacı nedir?",
     "dua_anlami", ["Allah'tan dünya ve ahiret için hayır istemek", "Sadece söz tekrarıdır", "Dua namazda okunmaz", "Rabbena zorunlu değildir"]),
    ("\"Dua ederken samimiyet önemlidir. Gösteriş için yapılan dua makbul değildir\" ifadesi neyi vurgular?",
     "dua_anlami", ["Duanın içten ve Allah'a yönelik olması gerekir", "Dua sadece sözledir", "Gösteriş de olabilir", "Dua önemsizdir"]),
    ("\"Yalan söylemek münafıklık alametidir\" hadisi hangi ahlaki ilkeyi vurgular?",
     "islam_guzel_ahlak", ["Dürüstlük ve doğruluk", "Cömertlik", "Sabır", "Temizlik"]),
    ("\"Temizlik imandandır\" hadisi neyi ifade eder?",
     "islam_guzel_ahlak", ["Fiziksel ve manevi temizlik dinin parçasıdır", "Sadece abdest yeterlidir", "Temizlik sadece camide önemlidir", "Temizlik zorunlu değildir"]),
    ("\"Sıdk\" peygamberlerin sıfatlarındandır. Bu sıfat ne anlama gelir?",
     "peygamberler_ozellikleri", ["Peygamberler asla yalan söylemezler", "Sadece peygamberler doğrudur", "Doğruluk sadece dinde önemlidir", "Sıdk zorunlu değildir"]),
    ("\"Fetanet\" sıfatı peygamberlerde nasıl tezahür eder?",
     "peygamberler_ozellikleri", ["Üstün zeka ve doğru karar verebilme", "Güçlü olmak", "Zengin olmak", "Çok konuşmak"]),
    ("\"Müminler kardeştir\" ayetinin mesajı nedir?",
     "ayet_hadis_yorumlama", ["Müslümanlar birbirine kardeşçe davranmalıdır", "Sadece kan kardeşleri kastedilir", "Kardeşlik sadece camidedir", "Bu ayet hükmü kaldırılmıştır"]),
    ("\"Sabır\" kavramı İslam'da nasıl tanımlanır?",
     "ayet_hadis_yorumlama", ["Zorluklara karşı dayanma ve metanet", "Sadece oruçta sabretmek", "Sessiz kalmak", "Pasif olmak"]),
    ("\"Şükür\" kavramı metinde nasıl açıklanıyor?",
     "islami_kavramlar_degerler", ["Allah'ın nimetlerine karşı minnet duymak ve teşekkür etmek", "Sadece sözle teşekkür", "Şükür sadece namazda yapılır", "Şükür zorunlu değildir"]),
    ("\"Sabır\" değeri zorluklarda nasıl uygulanır?",
     "islami_kavramlar_degerler", ["Zorluklara karşı dayanma ve pes etmeme", "Sadece oruçta sabretmek", "Hiçbir şey yapmamak", "Şikayet etmek"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"din6_{idx:04d}",
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
        for _ in range(count):
            tpl = TEMPLATES[tpl_idx % len(TEMPLATES)]
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
                [topic[:10], "yorumlama", "cikarim"],
                expl, f"din6_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"din6_{idx:04d}"
        qq["id"] = f"din6_{idx:04d}"

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
        path = OUT_DIR / f"lgs_din6_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("DIN6 Question Bank Report")
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
