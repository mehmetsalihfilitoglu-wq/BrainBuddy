#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 4th Grade Fen Bilimleri question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: deney/gözlem/grafik/tablo yorumlama, sebep-sonuç, kavram bağlantısı.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/fen4"

# 4th grade curriculum units
TOPICS = [
    "yer_kabugu_dunyanin_hareketleri",
    "besinlerimiz",
    "kuvvet_ve_hareket",
    "madde_ve_ozellikleri",
    "isigin_yayilmasi",
    "ses_ve_ozellikleri",
    "canlilar_ve_yasam",
]

NEW_GEN_TYPES = [
    "deney_yorumlama",
    "gozlem_yorumlama",
    "grafik_yorumlama",
    "tablo_yorumlama",
    "sebep_sonuc",
    "kavram_baglantisi",
    "cok_adimli_dusunme",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # Yer Kabuğu ve Dünya'nın Hareketleri
    ("Öğrenciler yer kabuğu modelini inceledi. Kabuk, manto ve çekirdek katmanlarını gözlemlediler.\n\nBu modele göre yer kabuğunun özelliği aşağıdakilerden hangisidir?",
     "yer_kabugu_dunyanin_hareketleri", ["En dış katmandır; kayaçlar burada bulunur", "Sadece su içerir", "Katman yoktur", "İç çekirdek en dıştadır"]),
    ("Dünya'nın kendi etrafında 24 saatte döndüğü modelde gösterildi.\n\nBu dönüşün sonucu nedir?",
     "yer_kabugu_dunyanin_hareketleri", ["Gece ve gündüzün oluşması", "Mevsimlerin oluşması", "Ay evrelerinin değişmesi", "Depremlerin oluşması"]),
    ("Farklı kayaç türleri tablosunda granit, kireçtaşı, mermer örnek olarak verildi. Mermerin kireçtaşından oluştuğu belirtildi.\n\nBu tabloya göre kayaçlar hakkında hangi çıkarım yapılabilir?",
     "yer_kabugu_dunyanin_hareketleri", ["Kayaçlar zamanla değişebilir ve dönüşebilir", "Kayaçlar hiç değişmez", "Tüm kayaçlar aynıdır", "Mermer kayaç değildir"]),
    ("Fosil örnekleri incelendi. Fosillerin milyonlarca yıl önce yaşamış canlıların kalıntıları olduğu öğrenildi.\n\nFosillerin incelenmesinin önemi nedir?",
     "yer_kabugu_dunyanin_hareketleri", ["Geçmişte yaşamış canlılar ve ortamlar hakkında bilgi verir", "Fosiller önemsizdir", "Sadece büyük hayvanlar fosilleşir", "Fosiller canlıdır"]),
    ("Dünya'nın Güneş etrafında 365 günde dolandığı modelde gösterildi.\n\nBu dolanma hareketinin sonucu aşağıdakilerden hangisidir?",
     "yer_kabugu_dunyanin_hareketleri", ["Bir yılın (mevsimlerin) oluşması", "Gece ve gündüzün oluşması", "Gel-git olayı", "Depremler"]),
    # Besinlerimiz
    ("Öğrenciler besin içerikleri tablosunu inceledi. Karbonhidrat, protein, yağ, vitamin ve mineraller listelendi.\n\nBu tabloya göre dengeli beslenme için ne gereklidir?",
     "besinlerimiz", ["Farklı besin gruplarından yeterli ve dengeli almak", "Sadece protein tüketmek", "Sadece karbonhidrat tüketmek", "Tek bir besinle yetinmek"]),
    ("Lugol çözeltisi deneyinde ekmek ve patatesin mavi-mor renge dönüştüğü gözlemlendi.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "besinlerimiz", ["Ekmek ve patateste nişasta (karbonhidrat) vardır", "Lugol her besini mavi yapar", "Ekmek protein içerir", "Patates yağ içerir"]),
    ("Bilinçsiz beslenmenin obeziteye yol açabileceği metinde vurgulandı.\n\nBu bilgiye göre obeziteyi önlemek için ne yapılmalıdır?",
     "besinlerimiz", ["Dengeli beslenme ve düzenli hareket", "Hiç yemek yememek", "Sadece tatlı tüketmek", "Sadece fast food yemek"]),
    ("Besin israfı grafiğinde evlerde atılan yiyecek miktarının yüksek olduğu görüldü.\n\nBu grafiğe göre hangi önlem alınmalıdır?",
     "besinlerimiz", ["İhtiyaca göre alışveriş, artan yemekleri değerlendirme", "Daha fazla atık üretmek", "Besin almayı durdurmak", "Grafik yanıltıcıdır"]),
    ("Sigara ve alkolün sağlığa zararlı olduğu deney ve metinlerle anlatıldı.\n\nBu bilgiye dayanarak hangi çıkarım yapılabilir?",
     "besinlerimiz", ["Sigara ve alkolden uzak durulmalıdır", "Az miktarda zararsızdır", "Sadece yetişkinler etkilenir", "Sağlığa faydalıdır"]),
    # Kuvvet ve Hareket
    ("Öğrenciler topu iterek hareket ettirdi. Top durduğunda tekrar itilince hareket etti.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "kuvvet_ve_hareket", ["Kuvvet cisimleri hareket ettirebilir veya hızlandırabilir", "Top kendiliğinden hareket eder", "İtme kuvvet değildir", "Kuvvet sadece ağır cisimlere etki eder"]),
    ("Fren yapan bisikletin yavaşladığı gözlemlendi.\n\nBu gözlemin sebebi aşağıdakilerden hangisidir?",
     "kuvvet_ve_hareket", ["Fren, hareket yönüne ters kuvvet uygulayarak yavaşlatır", "Bisiklet kendiliğinden durur", "Yol engebelidir", "Fren kuvvet değildir"]),
    ("Mıknatısın toplu iğneyi çektiği deneyle gösterildi. Plastik çubuk iğneyi çekmedi.\n\nBu deneyin sonucu nedir?",
     "kuvvet_ve_hareket", ["Mıknatıs manyetik maddelere kuvvet uygular", "Tüm maddeler mıknatıstan etkilenir", "Plastik manyetiktir", "Mıknatıs sadece demiri çeker"]),
    ("Tablo: Farklı kuvvetlerin topun hızına etkisi. İtme kuvveti artınca hız arttı.\n\nBu tabloya göre kuvvet ile hız arasındaki ilişki nasıldır?",
     "kuvvet_ve_hareket", ["Aynı yöndeki kuvvet arttıkça hız artabilir", "Kuvvet hızı etkilemez", "Kuvvet arttıkça hız azalır", "İlişki yoktur"]),
    ("Cıvataların mıknatıslı tornavida ile toplandığı gözlemlendi.\n\nBu uygulamanın dayandığı bilimsel ilke nedir?",
     "kuvvet_ve_hareket", ["Mıknatıs demir gibi maddeleri çeker", "Cıvata mıknatıstır", "Tornavida elektrik üretir", "Manyetizma camda da vardır"]),
    # Madde ve Özellikleri
    ("Öğrenciler su, taş ve hava örneklerini inceledi. Su ve havanın akışkan, taşın katı olduğu gözlemlendi.\n\nMaddenin halleri tablosuna göre bu sınıflandırmanın ölçütü nedir?",
     "madde_ve_ozellikleri", ["Maddenin şekil ve hacim alabilme özelliği", "Maddenin rengi", "Maddenin ağırlığı", "Maddenin sıcaklığı"]),
    ("Isıtılan buzun suya dönüştüğü deneyle gösterildi.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "madde_ve_ozellikleri", ["Isı alan katı madde sıvıya dönüşebilir (erime)", "Buz asla erimez", "Isı maddeyi etkilemez", "Sadece soğukta erir"]),
    ("Şeker ve tuzun suda çözündüğü, kumun çözünmediği deneyle gösterildi.\n\nBu deneyin sonucu aşağıdakilerden hangisidir?",
     "madde_ve_ozellikleri", ["Bazı maddeler suda çözünür, bazıları çözünmez", "Tüm maddeler suda çözünür", "Sadece katılar çözünür", "Kum suda erir"]),
    ("Saf madde ve karışım tablosunda altın saf, tuzlu su karışım olarak işaretlendi.\n\nBu sınıflandırmanın temel ölçütü nedir?",
     "madde_ve_ozellikleri", ["Tek tür tanecikten mi yoksa birden fazla maddeden mi oluştuğu", "Maddenin rengi", "Maddenin sıcaklığı", "Maddenin ağırlığı"]),
    ("Termometre ile ölçülen su sıcaklığı ısıtıldıkça arttı. Kaynama sırasında sıcaklık sabit kaldı.\n\nBu durumun nedeni nedir?",
     "madde_ve_ozellikleri", ["Verilen ısı buharlaşmada kullanılır, sıcaklık değişmez", "Termometre bozulmuştur", "Isı bitmiştir", "Su ısı almaz"]),
    # Işığın Yayılması
    ("Karanlık odada el feneriyle duvara ışık tutulduğunda düz çizgi halinde ilerlediği gözlemlendi.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "isigin_yayilmasi", ["Işık doğrusal (düz çizgi) yayılır", "Işık eğri yayılır", "Işık sadece havada yayılır", "Işık duvardan geri dönmez"]),
    ("Opak cisimden ışık geçmediği, gölge oluştuğu deneyle gösterildi.\n\nGölge oluşumunun koşulu nedir?",
     "isigin_yayilmasi", ["Işığın opak cisim tarafından engellenmesi", "Işığın az olması", "Cismin büyük olması", "Kaynağın yakın olması"]),
    ("Işık kaynağına yaklaştıkça gölgenin büyüdüğü deneyle gösterildi.\n\nBu gözlemin sebebi aşağıdakilerden hangisidir?",
     "isigin_yayilmasi", ["Kaynak uzaklığı gölge boyunu etkiler", "Işık miktarı değişmez", "Gölge her zaman aynı büyüklüktedir", "Cismin rengi gölgeyi etkiler"]),
    ("Işık ayna yüzeyinden yansıdığında gelen ve yansıyan açıların eşit olduğu gözlemlendi.\n\nBu deneyin yorumu nedir?",
     "isigin_yayilmasi", ["Düzgün yansımada gelme açısı = yansıma açısı", "Işık kırılır", "Ayna ışığı soğurur", "Açılar farklıdır"]),
    ("Beyaz ışık prizmadan geçirildiğinde gökkuşağı renklerine ayrıldığı deneyle gösterildi.\n\nBu deneyin sonucu nedir?",
     "isigin_yayilmasi", ["Beyaz ışık farklı renklerin birleşimidir", "Prizma renk üretir", "Işık tek renktir", "Renkler ışıktan bağımsızdır"]),
    # Ses ve Özellikleri
    ("Titreşen diyapazon suya batırıldığında suyun sıçradığı görüldü. Titreşim durduğunda sıçrama da durdu.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "ses_ve_ozellikleri", ["Ses titreşimle oluşur; titreşim yoksa ses yoktur", "Su sesi yükseltir", "Diyapazon suda sessizdir", "Ses suda oluşmaz"]),
    ("Sesin duvara çarpıp geri dönmesi (eko) deneyinde uzak duvarda gecikme daha fazlaydı.\n\nBu gözlemin açıklaması nedir?",
     "ses_ve_ozellikleri", ["Ses yansır; mesafe arttıkça gidip dönme süresi artar", "Ses duvardan geçer", "Eko sadece dağda olur", "Uzaklık sesi etkilemez"]),
    ("İnce telden tiz, kalın telden pes ses çıktığı deneyle gösterildi.\n\nBu deneye göre sesin tizliği (perdesi) ile ilgili hangi ilişki kurulabilir?",
     "ses_ve_ozellikleri", ["Titreşen cisim inceyse ses tiz, kalınsa pes olur", "Kalın teller daha tiz ses çıkarır", "Ses tizliği ısıya bağlıdır", "Tüm teller aynı sesi çıkarır"]),
    ("Havası alınmış fanustaki zil sesinin duyulmadığı deneyle gösterildi.\n\nBu deneyin sonucu neyi gösterir?",
     "ses_ve_ozellikleri", ["Ses maddesel ortamda (taneciklerle) yayılır; boşlukta yayılmaz", "Ses boşlukta da yayılır", "Fanus sesi engeller", "Zil sessizdir"]),
    ("Gürültü kirliliğinin işitme kaybına yol açabileceği metinde vurgulandı.\n\nGürültüyü azaltmak için ne yapılmalıdır?",
     "ses_ve_ozellikleri", ["Ses yalıtımı, sessiz alanlar, gürültü kaynaklarının azaltılması", "Kulaklık takmak yeterlidir", "Gürültü zararsızdır", "Ses kirliliği önlenemez"]),
    # Canlılar ve Yaşam
    ("Öğrenciler doğada bitki ve hayvanları inceledi. Bazı canlıların toprakta, bazılarının suda yaşadığını gözlemledi.\n\nBu gözleme göre canlıların sınıflandırılmasında hangi özellik kullanılabilir?",
     "canlilar_ve_yasam", ["Yaşam alanı ve yapısal özellikler", "Sadece rengi", "Sadece büyüklüğü", "Sadece hızı"]),
    ("Bitkilerin fotosentez yaparak besin ürettiği, güneş ışığı olmadan yapılamadığı deneyle gösterildi.\n\nFotosentez için gerekli olan aşağıdakilerden hangisidir?",
     "canlilar_ve_yasam", ["Güneş ışığı, su ve karbondioksit", "Sadece su", "Sadece toprak", "Sadece oksijen"]),
    ("Hayvanların beslenme şekillerine göre otçul, etçil ve hepçil olarak ayrıldığı tablo verildi.\n\nBu sınıflandırmanın temel ölçütü nedir?",
     "canlilar_ve_yasam", ["Yedikleri besin türü", "Yaşadıkları yer", "Büyüklükleri", "Renkleri"]),
    ("Canlıların yaşadıkları ortama uyum sağladığı örneklerle anlatıldı. Kutup ayısının kalın kürkü vardır.\n\nBu uyumun nedeni nedir?",
     "canlilar_ve_yasam", ["Soğuk ortamda ısı kaybını azaltmak", "Avlanmayı kolaylaştırmak", "Daha güzel görünmek", "Diğer hayvanlardan farklı olmak"]),
    ("Besin zinciri tablosunda bitki → otçul → etçil sıralaması verildi. Enerji aktarımında kayıp olduğu belirtildi.\n\nBu tabloya göre besin zincirinde enerji nasıl aktarılır?",
     "canlilar_ve_yasam", ["Her basamakta enerjinin bir kısmı ısı olarak kaybedilir", "Enerji tam aktarılır", "Sadece bitkiler enerji üretir", "Enerji aktarımı yoktur"]),
    # Additional templates for variety
    ("Depremlerin yer kabuğundaki kırılmalarla oluştuğu modelde gösterildi.\n\nDeprem sırasında alınacak önlemler arasında hangisi doğrudur?",
     "yer_kabugu_dunyanin_hareketleri", ["Sağlam masanın altına çökmek, başı korumak", "Pencereye yaklaşmak", "Asansör kullanmak", "Hemen dışarı çıkmak"]),
    ("Vitamin ve mineral eksikliğinin hastalıklara yol açabileceği tabloda gösterildi. D vitamini eksikliğinde kemikler zayıflar.\n\nBu tabloya göre hangi çıkarım yapılabilir?",
     "besinlerimiz", ["Vitamin ve mineraller sağlık için gereklidir", "Vitaminler önemsizdir", "Sadece protein önemlidir", "Mineral eksikliği zararsızdır"]),
    ("Yay sıkıştırıldığında kısaldığı, bırakıldığında eski haline döndüğü deneyle gösterildi.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "kuvvet_ve_hareket", ["Kuvvet cisimlerin şeklini değiştirebilir", "Yay kuvvet üretir", "Kuvvet sadece hareketi etkiler", "Yay genişlemez"]),
    ("Yoğunluk deneyinde tahta suda yüzerken demir battı. Bu farkın sebebi nedir?",
     "madde_ve_ozellikleri", ["Yoğunluk farkı; yoğunluğu sudan az olan yüzer", "Demir daha hafiftir", "Tahta suyu iter", "Yoğunluk önemsizdir"]),
    ("Işık kirliliğinin gece gökyüzünü aydınlatarak yıldızların görünmesini engellediği belirtildi.\n\nIşık kirliliğini azaltmak için ne yapılmalıdır?",
     "isigin_yayilmasi", ["Gereksiz dış aydınlatmayı azaltmak, ışığı yere yönlendirmek", "Tüm ışıkları kapatmak", "Sadece ev içinde ışık kullanmak", "Işık kirliliği önlenemez"]),
    ("Sesin kaynağa yaklaştıkça daha şiddetli, uzaklaştıkça daha zayıf duyulduğu gözlemlendi.\n\nBu ilişkinin sebebi nedir?",
     "ses_ve_ozellikleri", ["Ses enerjisi uzaklaştıkça dağılır, şiddet azalır", "Ses her yerde aynıdır", "Yakın ses daha tizdir", "Uzaklık sesi etkilemez"]),
    ("Ekosistemde üreticiler, tüketiciler ve ayrıştırıcıların birbirine bağımlı olduğu tablo verildi.\n\nBu ilişkinin anlamı nedir?",
     "canlilar_ve_yasam", ["Canlılar besin ve enerji zinciriyle birbirine bağlıdır", "Her canlı bağımsız yaşar", "Sadece bitkiler önemlidir", "Ayrıştırıcılar gereksizdir"]),
    ("Grafikte kayaç türlerine göre sertlik değerleri karşılaştırıldı. Elmas en sert gösterildi.\n\nBu grafiğe göre hangi çıkarım yapılabilir?",
     "yer_kabugu_dunyanin_hareketleri", ["Kayaçların sertliği birbirinden farklıdır", "Tüm kayaçlar aynı sertliktedir", "Elmas yumuşaktır", "Grafik yanıltıcıdır"]),
    ("Sütün protein içerdiğini göstermek için yapılan deneyde süt ısıtıldığında kesilme (pıhtılaşma) gözlemlendi.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "besinlerimiz", ["Sütte protein vardır; ısı protein pıhtılaşmasına yol açar", "Süt ısıtılınca bozulur", "Süt sadece karbonhidrat içerir", "Protein ısıya dayanıklıdır"]),
    ("Eğik düzlem deneyinde rampa uzunluğu artırıldığında aynı yükseklik için daha az kuvvet yeterli oldu.\n\nBu sonucun nedeni nedir?",
     "kuvvet_ve_hareket", ["Uzun rampa eğimi azaltır; aynı yükseklik daha az kuvvetle aşılabilir", "Kısa rampa daha hafiftir", "Kuvvet rampa uzunluğundan bağımsızdır", "Yükseklik kuvveti etkilemez"]),
    ("Filtre kağıdı ile yapılan deneyde kum suda ayrıldı. Su filtreden geçti, kum kaldı.\n\nBu yöntemin adı ve dayandığı ilke nedir?",
     "madde_ve_ozellikleri", ["Süzme; tanecik boyutu farkı", "Buharlaştırma; yoğunluk farkı", "Damıtma; kaynama farkı", "Mıknatıslama; manyetik fark"]),
    ("Kalem su dolu bardakta kırık göründü. Bu gözlemin nedeni nedir?",
     "isigin_yayilmasi", ["Işığın farklı ortamlarda kırılması", "Suyun rengi değişmiştir", "Kalem kırılmıştır", "Işık kırılmaz"]),
    ("Mikroskopla incelenen su damlasında hareket eden tek hücreli canlılar görüldü.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "canlilar_ve_yasam", ["Tek hücreli canlılar da beslenir ve hareket eder", "Sadece çok hücreliler canlıdır", "Su canlı üretir", "Mikroskop canlı yaratır"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"fen4_{idx:04d}",
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
        "subject": "fen",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = ["Deney/gözlem bu sonucu desteklemez.", "Bu çıkarım yapılamaz.", "Sebep-sonuç ilişkisi yanlış kurulmuştur.", "Kavram karışıklığı vardır."]
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
            expl = f"Deney/gözlem sonuçlarına ve bilimsel kavramlara göre doğru cevap \"{correct}\" seçeneğidir. Diğer seçenekler sebep-sonuç ilişkisini yanlış kurmakta, deneyi yanlış yorumlamakta veya kavram karışıklığı içermektedir."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10] if len(topic) >= 10 else topic, "deney_yorum", "veri_analiz"],
                expl, f"fen4_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"fen4_{idx:04d}"
        qq["id"] = f"fen4_{idx:04d}"

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
            "subject": "fen",
            "publisher": "edumio",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_fen4_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("FEN4 Question Bank Report")
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
    random.seed(48)
    main()
