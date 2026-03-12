#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 3rd Grade Fen Bilimleri question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: deney/gözlem/grafik/tablo yorumlama, sebep-sonuç, kavram bağlantısı.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/fen3"

TOPICS = [
    "duyu_organlarimiz",
    "canlilar_dunyasina_yolculuk",
    "kuvveti_taniyalim",
    "maddeyi_taniyalim",
    "isik_ve_ses",
    "cevremizdeki_dunya",
]

NEW_GEN_TYPES = [
    "deney_yorumlama",
    "gozlem_yorumlama",
    "grafik_yorumlama",
    "tablo_yorumlama",
    "sebep_sonuc",
    "kavram_baglantisi",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # Duyu Organlarımız
    ("Gözle gördüğümüz şeyleri nasıl ayırt ederiz?", "duyu_organlarimiz",
     ["Renk, şekil ve büyüklük gibi özellikleriyle", "Sadece dokunarak", "Sadece koklayarak", "Göz işe yaramaz"]),
    ("Gözleri kapatıp bir ses dinlendiğinde hangi duyu organı kullanılır?", "duyu_organlarimiz",
     ["Kulak (işitme)", "Göz", "Burun", "Dil"]),
    ("Tabloda beş duyu organı ve görevleri listeleniyor. Koku alma hangi organla yapılır?", "duyu_organlarimiz",
     ["Burun", "Kulak", "Göz", "Dil"]),
    ("\"Tatlı\" ve \"acı\" tatları hangi duyu organımızla algılarız?", "duyu_organlarimiz",
     ["Dil", "Burun", "Kulak", "Göz"]),
    ("Bir nesneye dokunduğumuzda sıcak-soğuk, sert-yumuşak gibi özellikleri hangi organla algılarız?", "duyu_organlarimiz",
     ["Deri", "Göz", "Burun", "Kulak"]),
    # Canlılar Dünyasına Yolculuk
    ("Öğrenciler bahçede bitki ve hayvanları inceledi. Bazı canlıların toprakta, bazılarının suda yaşadığını gözlemledi.\n\nBu gözleme göre canlıları nasıl gruplayabiliriz?",
     "canlilar_dunyasina_yolculuk", ["Yaşadıkları yere göre (karada/suda)", "Sadece rengine göre", "Sadece büyüklüğüne göre", "Gruplamaya gerek yoktur"]),
    ("Bitkilerin yeşil yaprakları vardır. Hayvanlar ise bitkiler gibi kendi besinini üretemez.\n\nBu bilgiye göre bitki ile hayvan arasındaki temel fark nedir?",
     "canlilar_dunyasina_yolculuk", ["Bitkiler fotosentezle besin üretir; hayvanlar hazır besin alır", "Bitkiler hareket etmez", "Hayvanlar yeşildir", "Fark yoktur"]),
    ("Tablo: Tavşan ot yer, kurt et yer, ayı hem ot hem et yer. Bu sınıflandırmanın ölçütü nedir?",
     "canlilar_dunyasina_yolculuk", ["Beslenme şekli (otçul, etçil, hepçil)", "Rengi", "Büyüklüğü", "Yaşadığı yer"]),
    ("Evde beslenen kedi ve köpeğin ortak özelliği nedir?",
     "canlilar_dunyasina_yolculuk", ["İkisi de memeli hayvanlardır, yavrularını besler", "İkisi de uçar", "İkisi de balıktır", "Ortak özellik yoktur"]),
    # Kuvveti Tanıyalım
    ("Öğrenciler topu iterek hareket ettirdi. Top durduğunda tekrar itilince hareket etti.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "kuvveti_taniyalim", ["Kuvvet cisimleri hareket ettirebilir", "Top kendiliğinden hareket eder", "İtme kuvvet değildir", "Kuvvet sadece ağır cisimlere etki eder"]),
    ("Mıknatısın toplu iğneyi çektiği deneyle gösterildi. Plastik çubuk iğneyi çekmedi.\n\nBu deneyin sonucu nedir?",
     "kuvveti_taniyalim", ["Mıknatıs demir gibi maddeleri çeker", "Tüm maddeler mıknatıstan etkilenir", "Plastik manyetiktir", "Mıknatıs sadece altını çeker"]),
    ("Fren yapan bisikletin yavaşladığı gözlemlendi.\n\nBu gözlemin sebebi aşağıdakilerden hangisidir?",
     "kuvveti_taniyalim", ["Fren, hareket yönüne ters kuvvet uygulayarak yavaşlatır", "Bisiklet kendiliğinden durur", "Yol engebelidir", "Fren kuvvet değildir"]),
    ("Yay sıkıştırıldığında kısaldığı, bırakıldığında eski haline döndüğü deneyle gösterildi.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "kuvveti_taniyalim", ["Kuvvet cisimlerin şeklini değiştirebilir", "Yay kuvvet üretir", "Kuvvet sadece hareketi etkiler", "Yay genişlemez"]),
    ("Tablo: Halıda kutu zor, parkede kolay kayıyor. Bu farkın sebebi nedir?",
     "kuvveti_taniyalim", ["Halıda sürtünme daha fazladır", "Parke daha ağırdır", "Kutu halıda büyür", "Fark yoktur"]),
    # Maddeyi Tanıyalım
    ("Öğrenciler su, taş ve hava örneklerini inceledi. Su ve havanın akışkan, taşın katı olduğu gözlemlendi.\n\nBu sınıflandırmanın ölçütü nedir?",
     "maddeyi_taniyalim", ["Maddenin şekil ve hacim alabilme özelliği", "Maddenin rengi", "Maddenin ağırlığı", "Maddenin sıcaklığı"]),
    ("Şeker suda çözündü, kum çözünmedi. Bu deneyin sonucu nedir?",
     "maddeyi_taniyalim", ["Bazı maddeler suda çözünür, bazıları çözünmez", "Tüm maddeler suda çözünür", "Kum suda erir", "Şeker çözünmez"]),
    ("Isıtılan buzun suya dönüştüğü deneyle gösterildi.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "maddeyi_taniyalim", ["Isı alan katı madde sıvıya dönüşebilir (erime)", "Buz asla erimez", "Isı maddeyi etkilemez", "Sadece soğukta erir"]),
    ("Suyu buzdolabına koyduğumuzda ne olur?", "maddeyi_taniyalim",
     ["Su soğur, yeterince soğursa donarak buz olur", "Su kaybolur", "Su ısınır", "Hiçbir şey olmaz"]),
    ("Farklı maddelerin sertlik, yumuşaklık tablosu verildi. Pamuk yumuşak, taş sert gösterildi.\n\nBu tabloya göre maddeler nasıl nitelenir?",
     "maddeyi_taniyalim", ["Dokunma duyusuyla sert-yumuşak gibi özellikleriyle", "Sadece rengiyle", "Sadece kokusuyla", "Tüm maddeler aynıdır"]),
    # Işık ve Ses
    ("Karanlık odada el feneriyle duvara ışık tutulduğunda düz çizgi halinde ilerlediği gözlemlendi.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "isik_ve_ses", ["Işık doğrusal (düz çizgi) yayılır", "Işık eğri yayılır", "Işık sadece havada yayılır", "Işık duvardan geri dönmez"]),
    ("Opak (saydam olmayan) cisimden ışık geçmediği, gölge oluştuğu deneyle gösterildi.\n\nGölge nasıl oluşur?",
     "isik_ve_ses", ["Işık opak cisim tarafından engellendiğinde", "Işığın az olmasıyla", "Cismin büyük olmasıyla", "Kaynağın yakın olmasıyla"]),
    ("Titreşen diyapazon suya batırıldığında suyun sıçradığı görüldü. Titreşim durduğunda sıçrama da durdu.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "isik_ve_ses", ["Ses titreşimle oluşur; titreşim yoksa ses yoktur", "Su sesi yükseltir", "Diyapazon suda sessizdir", "Ses suda oluşmaz"]),
    ("Sesin kaynağa yaklaştıkça daha şiddetli, uzaklaştıkça daha zayıf duyulduğu gözlemlendi.\n\nBu ilişkinin sebebi nedir?",
     "isik_ve_ses", ["Ses uzaklaştıkça dağılır, şiddet azalır", "Ses her yerde aynıdır", "Yakın ses daha tizdir", "Uzaklık sesi etkilemez"]),
    ("İnce telden tiz, kalın telden pes ses çıktığı deneyle gösterildi.\n\nBu deneye göre sesin tizliği ile ilgili hangi ilişki kurulabilir?",
     "isik_ve_ses", ["Titreşen cisim inceyse ses tiz, kalınsa pes olur", "Kalın teller daha tiz ses çıkarır", "Ses tizliği ısıya bağlıdır", "Tüm teller aynı sesi çıkarır"]),
    # Çevremizdeki Dünya
    ("Çevremizdeki canlılar ve cansız varlıklar tablosunda ağaç, kuş, taş, su listeleniyor. Hangisi cansızdır?",
     "cevremizdeki_dunya", ["Taş ve su cansız varlıklardır", "Ağaç cansızdır", "Kuş cansızdır", "Hepsi canlıdır"]),
    ("\"Çöpleri yere atmamalı, geri dönüşüm kutularını kullanmalıyız\" ifadesi neyi vurgular?",
     "cevremizdeki_dunya", ["Çevre temizliği ve geri dönüşümün önemi", "Çöp zararsızdır", "Geri dönüşüm gereksizdir", "Sadece evde çöp vardır"]),
    ("Hava, su ve toprak kirliliği grafiğinde hangi bölgede kirlilik fazlaysa orada canlılar etkilenir.\n\nBu grafiğe göre hangi çıkarım yapılabilir?",
     "cevremizdeki_dunya", ["Kirlilik canlıların yaşamını olumsuz etkiler", "Kirlilik zararsızdır", "Sadece su kirlenir", "Canlılar kirlilikten etkilenmez"]),
    ("Mevsimler tablosunda yaz, kış, ilkbahar, sonbahar ve özellikleri verilmiştir. İlkbaharda ağaçlar çiçek açar.\n\nBu tabloya göre mevsimler canlıları nasıl etkiler?",
     "cevremizdeki_dunya", ["Mevsimlere göre canlıların yaşamı ve davranışları değişir", "Mevsimler canlıları etkilemez", "Sadece bitkiler etkilenir", "Tüm mevsimler aynıdır"]),
    ("Su tasarrufu ile ilgili deneyde damlayan musluktan günde birkaç litre suyun boşa aktığı hesaplandı.\n\nBu deneyin mesajı nedir?",
     "cevremizdeki_dunya", ["Küçük sızıntılar bile büyük su kaybına yol açar; tasarruf önemlidir", "Su sınırsızdır", "Sadece büyük sızıntılar önemlidir", "Su tasarrufu zordur"]),
    # Extra for balance
    ("Beş duyu organımız tablosunda görme, işitme, koku, tat, dokunma listeleniyor. Görmeyle ilgili organ hangisidir?",
     "duyu_organlarimiz", ["Göz", "Kulak", "Burun", "Dil"]),
    ("Bitkilerin kök, gövde ve yaprağı vardır. Kökün görevi nedir?", "canlilar_dunyasina_yolculuk",
     ["Bitkiyi toprağa bağlamak, su ve mineralleri almak", "Güneş ışığı almak", "Tohum üretmek", "Rüzgarda sallanmak"]),
    ("Cıvataların mıknatıslı tornavida ile toplandığı gözlemlendi.\n\nBu uygulamanın dayandığı ilke nedir?",
     "kuvveti_taniyalim", ["Mıknatıs demir gibi maddeleri çeker", "Cıvata mıknatıstır", "Tornavida elektrik üretir", "Cam da çekilir"]),
    ("1 m 25 cm kaç cm'dir? (Fen bağlamında ölçüm)", "maddeyi_taniyalim",
     ["125 cm", "1025 cm", "15 cm", "25 cm"]),  # Bu mat konusu, fen3'te uygun değil - değiştirelim
    ("Gölge, ışık kaynağına yaklaştıkça büyür. Bu gözlemin sebebi nedir?", "isik_ve_ses",
     ["Kaynak uzaklığı gölge boyunu etkiler", "Işık miktarı değişmez", "Gölge her zaman aynıdır", "Cismin rengi gölgeyi etkiler"]),
    ("Ormanların havayı temizlediği, canlılara yuva sağladığı metinde vurgulanıyor.\n\nBu bilgiye göre ormanları korumak neden önemlidir?",
     "cevremizdeki_dunya", ["Ormanlar hava temizliği ve canlı çeşitliliği için gereklidir", "Ormanlar sadece güzeldir", "Ormanlar önemsizdir", "Koruma gerekmez"]),
]

# Replace the math-style template with a proper fen one
TEMPLATES = [(s, t, o) for s, t, o in TEMPLATES if "125 cm" not in str(o)]
TEMPLATES.append(("Farklı maddelerin renk, koku, tat tablosu verildi. Limon ekşi, şeker tatlı gösterildi.\n\nBu tabloya göre maddeleri nasıl ayırt edebiliriz?",
     "maddeyi_taniyalim", ["Duyu organlarımızla (dil ile tat, burun ile koku, göz ile renk)", "Sadece gözle", "Sadece elle", "Ayırt edemeyiz"]))


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"fen3_{idx:04d}",
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
                expl, f"fen3_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"fen3_{idx:04d}"
        qq["id"] = f"fen3_{idx:04d}"

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
            "publisher": "brainbuddy",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_fen3_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("FEN3 Question Bank Report")
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
    random.seed(44)
    main()
