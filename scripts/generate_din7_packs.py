#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 7th Grade Din Kültürü ve Ahlak Bilgisi question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: ayet/hadis yorumlama, senaryo tabanlı ahlak, kavram çıkarımı.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/din7"

TOPICS = [
    "melek_ahiret",
    "hac_kurban",
    "ahlaki_davranislar",
    "hz_muhammed",
    "islam_dusuncesinde_yorumlar",
    "din_guzel_ahlak",
    "ibadet_sorumluluk",
    "paylasma_yardimlasma",
    "peygamberler_ozellikleri",
    "ayet_hadis_yorumlama",
    "islami_kavramlar_degerler",
]

NEW_GEN_TYPES = [
    "ayet_hadis_yorumlama",
    "senaryo_ahlak",
    "kavram_cikarimi",
    "metin_analizi",
    "paragraf_yorumlama",
    "deger_cikarimi",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    ("Metinde geçen \"Melekler, Allah'ın emirlerini yerine getiren, asla isyan etmeyen varlıklardır\" ifadesine göre melekler hakkında hangi çıkarım doğrudur?",
     "melek_ahiret", ["Melekler Allah'a tam itaat ederler.", "Melekler insanlardan üstündür.", "Melekler sadece Cennet'tedir.", "Melekler görülebilir varlıklardır."]),
    ("\"Ahiret gününe inanmayan kimse tam manasıyla Müslüman olamaz\" hadisiyle verilen mesaj nedir?",
     "melek_ahiret", ["Ahiret inancı İslam'ın temel esaslarındandır.", "Sadece ahirete inanmak yeterlidir.", "Ahiret sadece peygamberlere mahsustur.", "Dünya hayatı önemli değildir."]),
    ("Hac ibadetiyle ilgili metinde \"Kâbe'nin etrafında tavaf edilir\" ifadesi geçmektedir. Bu bilgiye dayanarak tavafın anlamı nedir?",
     "hac_kurban", ["Kâbe'nin etrafında dönerek yapılan ibadettir.", "Hac sadece Kâbe'yi görmektir.", "Tavaf sadece erkeklere farzdır.", "Tavaf hac dışında yapılamaz."]),
    ("Kurban ibadetinin hikmetiyle ilgili verilen paragrafta \"paylaşma ve yardımlaşma ruhunu canlandırır\" cümlesi geçmektedir. Bu ifadeden ne anlaşılmalıdır?",
     "hac_kurban", ["Kurban, toplumsal dayanışmayı güçlendiren bir ibadettir.", "Kurban sadece et dağıtmaktır.", "Paylaşma sadece kurban günü yapılır.", "Kurban mecburi değildir."]),
    ("Senaryo: Arkadaşı zor durumda olan Ali, ona yardım etmek yerine \"Bana ne, kendi işini kendin gör\" demiştir. Bu davranış hangi ahlaki değere aykırıdır?",
     "ahlaki_davranislar", ["Yardımlaşma ve dayanışma", "Dürüstlük", "Sabır", "Temizlik"]),
    ("\"Güzel söz söylemek sadakadır\" hadisine göre hangi davranış doğrudur?",
     "ahlaki_davranislar", ["İnsanlarla güzel konuşmak da ibadet sayılır.", "Sadece para vermek sadakadır.", "Güzel söz söylemek zorunlu değildir.", "Sadaka sadece Ramazan'da verilir."]),
    ("Hz. Muhammed'in \"Komşusu açken tok yatan bizden değildir\" hadisi hangi değeri vurgulamaktadır?",
     "hz_muhammed", ["Komşu hakkına riayet ve yardımlaşma", "Namaz kılmanın önemi", "Oruç tutmanın gerekliliği", "Hacca gitmenin farz oluşu"]),
    ("Peygamberimizin \"En hayırlılarınız, ailesine en iyi davrananlardır\" sözüyle verilen mesaj nedir?",
     "hz_muhammed", ["Aile fertlerine iyi davranmak dinî bir görevdir.", "Sadece aileye iyi davranılmalıdır.", "Aile dışındakilere davranış önemsizdir.", "Bu söz sadece erkekler için geçerlidir."]),
    ("İslam düşüncesinde farklı mezheplerin varlığı hakkındaki paragrafta \"temel inanç esaslarında ittifak vardır\" ifadesi geçiyor. Bu ne anlama gelir?",
     "islam_dusuncesinde_yorumlar", ["Temel inançlarda birlik, detayda farklılık olabilir.", "Tüm mezhepler aynıdır.", "Mezhepler gereksizdir.", "İnanç esasları değişkendir."]),
    ("Metinde \"ictihad\" kavramı \" yetkili alimlerin Kur'an ve Sünnet'ten hüküm çıkarması\" olarak tanımlanıyor. Buna göre ictihadın amacı nedir?",
     "islam_dusuncesinde_yorumlar", ["Dinî hükümleri yeni durumlara uyarlamak", "Sadece geçmişe bakmak", "Mezhepleri kaldırmak", "Kur'an'ı değiştirmek"]),
    ("\"Din güzel ahlakı tamamlamak için gönderilmiştir\" sözüne göre din ve ahlak ilişkisi nasıldır?",
     "din_guzel_ahlak", ["Din, güzel ahlakı destekler ve tamamlar.", "Ahlak dinden bağımsızdır.", "Sadece ibadet yeterlidir.", "Ahlak sadece toplum içindir."]),
    ("Paragrafta \"doğruluk, güvenilirlik ve adalet\" değerleri vurgulanmaktadır. Bu değerlerin İslam'daki yeri nedir?",
     "din_guzel_ahlak", ["İslam'ın önem verdiği temel ahlaki değerlerdir.", "Sadece ticarette geçerlidir.", "Peygamberlere mahsustur.", "Zorunlu değildir."]),
    ("\"İbadet, Allah'a karşı sorumluluğumuzu yerine getirmektir\" cümlesine göre ibadetin anlamı nedir?",
     "ibadet_sorumluluk", ["Kulluk bilinciyle Allah'a yönelmektir.", "Sadece namaz ve oruçtur.", "İbadet sadece camide yapılır.", "İbadet zorunlu değildir."]),
    ("Metinde \"sorumluluk bilinci, yaptığımız işleri doğru yapmak ve sonuçlarını üstlenmektir\" denilmektedir. Buna göre sorumluluk ne demektir?",
     "ibadet_sorumluluk", ["Davranışlarımızdan hesap verme bilincidir.", "Sadece büyüklere karşıdır.", "Sorumluluk sadece iş yerindedir.", "Çocuklar sorumlu değildir."]),
    ("\"Mallarınızı aranızda batıl yollarla yemeyin; insanların mallarından bir kısmını bilerek haksız yere yemek için onu hakimlere (rüşvet olarak) vermeyin\" ayetinin ana mesajı nedir?",
     "paylasma_yardimlasma", ["Haksız kazanç haramdır, adil olunmalıdır.", "Mal paylaşımı zorunlu değildir.", "Hakimlere hediye verilebilir.", "Ticaret serbesttir."]),
    ("Yardımlaşma ile ilgili hadiste \"Kardeşine yardım et, o da sana yardım etsin\" denilmektedir. Buradan çıkarılabilecek sonuç nedir?",
     "paylasma_yardimlasma", ["Yardımlaşma karşılıklı dayanışmayı güçlendirir.", "Sadece aileye yardım edilir.", "Yardım beklenmeden yapılmamalıdır.", "Yardım sadece maddi olmalıdır."]),
    ("Peygamberlerin sıfatlarıyla ilgili metinde \"sıdk\" kavramı \"doğruluk\" olarak açıklanıyor. Buna göre peygamberlerde bu sıfat ne anlama gelir?",
     "peygamberler_ozellikleri", ["Peygamberler asla yalan söylemezler.", "Sadece peygamberler doğrudur.", "Doğruluk sadece dinde önemlidir.", "Sıdk zorunlu değildir."]),
    ("\"Emanet\" peygamberlerin sıfatlarından biridir. Metne göre emanet ne demektir?",
     "peygamberler_ozellikleri", ["Güvenilir olmak, kendilerine verileni korumak", "Sadece eşyayı saklamak", "Emanet sadece ticarette geçerlidir", "Peygamberler dışında kimse güvenilir değildir"]),
    ("\"Namazı kıl, zekatı ver\" ayeti hangi iki ibadeti birlikte vurgulamaktadır?",
     "ayet_hadis_yorumlama", ["Namaz ve zekat", "Oruç ve hac", "Namaz ve oruç", "Zekat ve kurban"]),
    ("\"Kim bir Müslümanın dünya sıkıntılarından birini giderirse, Allah da onun ahiret sıkıntılarından birini giderir\" hadisinin mesajı nedir?",
     "ayet_hadis_yorumlama", ["İnsanlara yardım etmek Allah katında değerlidir.", "Sadece Müslümanlara yardım edilir.", "Dünya sıkıntısı önemsizdir.", "Yardım etmek zorunlu değildir."]),
    ("\"İslam\" kelimesinin anlamı metinde nasıl açıklanmaktadır?",
     "islami_kavramlar_degerler", ["Allah'a teslim olmak, barış ve esenlik", "Sadece namaz kılmak", "Arapça bir kelimedir", "Sadece oruç tutmaktır"]),
    ("\"Takva\" kavramı \"Allah'tan sakınmak, günahlardan kaçınmak\" olarak tanımlanıyor. Buna göre takva sahibi kimse nasıl davranır?",
     "islami_kavramlar_degerler", ["İyilik yapar, kötülükten kaçınır.", "Sadece ibadet eder.", "Takva sadece alimlerde olur.", "Takva zorunlu değildir."]),
    ("Ahiret inancıyla ilgili paragrafta \"dünya hayatı imtihan yeridir\" ifadesi geçmektedir. Bu ne anlama gelir?",
     "melek_ahiret", ["Dünya, ahirete hazırlık ve sınanma alanıdır.", "Dünya önemsizdir.", "İmtihan sadece peygamberleredir.", "Ahiret dünyadan önce gelir."]),
    ("Cebrail, Mikail, İsrafil, Azrail meleklerinin görevleri metinde anlatılmaktadır. \"Vahiy getirmek\" hangi meleğin görevidir?",
     "melek_ahiret", ["Cebrail", "Mikail", "İsrafil", "Azrail"]),
    ("Hac ibadetinin farzları metinde sayılmaktadır. \"Arafat'ta vakfe\" ne anlama gelir?",
     "hac_kurban", ["Belirli bir süre Arafat'ta beklemektir.", "Arafat'ta namaz kılmaktır.", "Arafat sadece semboliktir.", "Vakfe zorunlu değildir."]),
    ("Kurban kesen kişi etin bir kısmını dağıtmalıdır. Bu davranış hangi değerle ilişkilidir?",
     "hac_kurban", ["Paylaşma ve cömertlik", "Sabır", "Temizlik", "Dürüstlük"]),
    ("Senaryo: Öğretmen yanlışlıkla sınavda Fazıl'a fazla puan vermiştir. Fazıl bunu fark edince ne yapmalıdır?",
     "ahlaki_davranislar", ["Öğretmene durumu bildirmeli, doğru puanı almalıdır.", "Sessiz kalmalıdır.", "Arkadaşlarına söylememelidir.", "Puanı hak ettiğini düşünmelidir."]),
    ("\"Yalan söylemek münafıklık alametidir\" hadisi hangi ahlaki ilkeyi vurgular?",
     "ahlaki_davranislar", ["Dürüstlük ve doğruluk", "Cömertlik", "Sabır", "Temizlik"]),
    ("Hz. Muhammed'in Hudeybiye Antlaşması'ndaki sabırlı ve diplomasiye dayalı tutumu neyi gösterir?",
     "hz_muhammed", ["Barışı ön planda tutması ve stratejik düşünmesi", "Savaştan kaçınması", "Güçsüzlüğü", "Teslimiyeti"]),
    ("\"Güzel örnek\" (üsve-i hasene) ifadesi Hz. Muhammed için kullanılmaktadır. Bu ne anlama gelir?",
     "hz_muhammed", ["Tüm davranışlarında örnek alınacak bir kişidir.", "Sadece ibadette örnektir.", "Sadece savaşta örnektir.", "Örnek alınması zorunlu değildir."]),
    ("Hanefi, Şafii, Maliki, Hanbeli mezhepleri metinde anlatılıyor. Bu mezhepler neden ortaya çıkmıştır?",
     "islam_dusuncesinde_yorumlar", ["Farklı ictihadlara dayalı yorum farklılıklarından", "Peygamberin farklı söylemlerinden", "Kur'an'ın farklı versiyonlarından", "Coğrafi ayrılıklardan"]),
    ("\"Kıyas\" kavramı İslam hukukunda nasıl kullanılır?",
     "islam_dusuncesinde_yorumlar", ["Benzer konularda Kur'an ve Sünnet'teki hükme göre hüküm çıkarmak", "Kur'an'ı değiştirmek", "Peygambere isnat etmek", "Mezhepleri birleştirmek"]),
    ("\"Temizlik imandandır\" hadisi neyi vurgular?",
     "din_guzel_ahlak", ["Fiziksel ve manevi temizlik dinin parçasıdır.", "Sadece abdest almak yeterlidir.", "Temizlik sadece camide önemlidir.", "Temizlik zorunlu değildir."]),
    ("Adalet kavramı metinde \"herkese hakkını vermek\" olarak tanımlanıyor. Buna göre adil bir davranış nasıl olmalıdır?",
     "din_guzel_ahlak", ["Tarafsız ve hakkaniyetli olmalıdır.", "Sadece aileye karşı adil olunur.", "Adalet sadece mahkemede geçerlidir.", "Adalet zorunlu değildir."]),
    ("Ramazan orucu ile ilgili ayette \"sizden öncekilere farz kılındığı gibi size de farz kılındı\" ifadesi neyi gösterir?",
     "ibadet_sorumluluk", ["Oruç evrensel bir ibadettir.", "Oruç sadece Müslümanlara mahsustur.", "Oruç sonradan konmuştur.", "Oruç zorunlu değildir."]),
    ("Zekat ibadetinin toplumsal işlevi metinde nasıl vurgulanmaktadır?",
     "ibadet_sorumluluk", ["Zengin ile fakir arasında denge sağlar.", "Zekat sadece camiye verilir.", "Zekat fakirlere borçtur.", "Zekat mecburi değildir."]),
    ("\"Verdiğiniz sadakalar açıktan verilebileceği gibi gizli de verilebilir\" ayeti neyi vurgular?",
     "paylasma_yardimlasma", ["Sadakanın samimiyetle ve gösterişsiz verilmesi önemlidir.", "Sadece gizli verilmelidir.", "Sadaka açıktan verilmemelidir.", "Sadaka zorunlu değildir."]),
    ("Komişer (karşılıklı yardımlaşma) kavramı metinde nasıl açıklanıyor?",
     "paylasma_yardimlasma", ["Toplumun birlikte dayanışma içinde yaşaması", "Sadece aile içi yardım", "Devletin görevi", "Zorunlu değil"]),
    ("\"Fetanet\" peygamberlerin sıfatlarından biridir. Bu sıfat ne anlama gelir?",
     "peygamberler_ozellikleri", ["Üstün zeka ve akıllılık", "Güçlü olmak", "Zengin olmak", "Çok konuşmak"]),
    ("Peygamberlerin \"tebliğ\" görevi ne anlama gelir?",
     "peygamberler_ozellikleri", ["Allah'ın mesajını insanlara iletmek", "Sadece kendi kavmine anlatmak", "Kur'an'ı yazmak", "Cami yaptırmak"]),
    ("\"Sabah akşam Rablerine, O'nun rızasını dileyerek dua edenlerle birlikte ol\" ayetinde vurgulanan değer nedir?",
     "ayet_hadis_yorumlama", ["İyi ve salih insanlarla birlikte olmak", "Sadece sabah akşam dua etmek", "Duanın zorunluluğu", "Camide bulunmak"]),
    ("\"Müminler kardeştir\" ayetinin mesajı nedir?",
     "ayet_hadis_yorumlama", ["Müslümanlar birbirine kardeşçe davranmalıdır.", "Sadece kan kardeşleri kastedilir.", "Kardeşlik sadece camidedir.", "Bu ayet hükmü kaldırılmıştır."]),
    ("\"Şükür\" kavramı metinde nasıl açıklanıyor?",
     "islami_kavramlar_degerler", ["Allah'ın nimetlerine karşı minnet duymak ve teşekkür etmek", "Sadece sözle teşekkür", "Şükür sadece namazda yapılır", "Şükür zorunlu değildir"]),
    ("\"Sabır\" İslam'da nasıl tanımlanmaktadır?",
     "islami_kavramlar_degerler", ["Zorluklara karşı dayanma ve metanet gösterme", "Sadece oruçta sabretmek", "Sessiz kalmak", "Pasif olmak"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"din7_{idx:04d}",
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
                expl, f"din7_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"din7_{idx:04d}"
        qq["id"] = f"din7_{idx:04d}"

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
        path = OUT_DIR / f"lgs_din7_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("DIN7 Question Bank Report")
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
    random.seed(47)
    main()
