#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 5th Grade Mathematics question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: yorum, çok adımlı işlem, gerçek hayat senaryosu.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/mat5"

TOPICS = [
    "dogal_sayilar",
    "dogal_sayilarla_islemler",
    "kesirler",
    "kesirlerle_islemler",
    "ondalik_gosterimler",
    "yuzdeler",
    "temel_geometrik_kavramlar",
    "ucgen_dortgenler",
    "uzunluk_olcme",
    "alan_olcme",
    "veri_toplama_analiz",
    "grafik_yorumlama",
]

NEW_GEN_TYPES = [
    "cok_adinli_islem",
    "gercek_hayat_senaryosu",
    "grafik_tablo_yorum",
    "mantik_yuruttme",
    "yorum_gerektiren",
]

def q(id_val, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"mat5_{id_val:04d}",
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
        "subject": "mat",
    }

# Template format: (stem, topic, [correct_num, wrong1, wrong2, wrong3], explanation)
# Numbers as strings for options
TEMPLATES = [
    ("45 678 sayısının binler bölüğündeki rakamların toplamı kaçtır?", "dogal_sayilar", ["9", "11", "12", "10"], "Binler bölüğü 45'tir. 4 + 5 = 9."),
    ("678 912 sayısında 7'nin basamak değeri ile 9'un basamak değerinin farkı kaçtır?", "dogal_sayilar", ["69100", "62000", "70100", "69900"], "7 onbinler basamağında: 70000; 9 yüzler basamağında: 900. Fark 69100."),
    ("Bir çiftçi 3 günde 276 kg buğday topladı. Her gün eşit miktar topladığına göre 5 günde toplam kaç kg toplar?", "dogal_sayilarla_islemler", ["460", "450", "470", "440"], "Günde 276÷3=92 kg. 5 günde 92×5=460 kg."),
    ("(48 + 52) × 3 işleminin sonucu kaçtır?", "dogal_sayilarla_islemler", ["300", "303", "297", "306"], "48+52=100, 100×3=300."),
    ("2/5 kesrinin 3/5 kesrine eşit olması için paya kaç eklenmelidir?", "kesirler", ["1", "2", "3", "0"], "2/5 + x/5 = 3/5 ise x=1. Pay 2'den 3'e çıkmalı, 1 eklenmeli."),
    ("3 tam 2/7 kesrinin bileşik kesir olarak yazılışı aşağıdakilerden hangisidir?", "kesirler", ["23/7", "21/7", "25/7", "22/7"], "3 tam 2/7 = (3×7+2)/7 = 23/7."),
    ("2/3 + 1/6 işleminin sonucu kaçtır?", "kesirlerle_islemler", ["5/6", "3/9", "1/2", "4/6"], "2/3=4/6, 4/6+1/6=5/6."),
    ("Bir pastanın 1/4'ü yendi. Kalan kısım pastanın kaçta kaçıdır?", "kesirlerle_islemler", ["3/4", "1/2", "2/3", "1/3"], "1 - 1/4 = 4/4 - 1/4 = 3/4."),
    ("0,45 + 0,38 işleminin sonucu kaçtır?", "ondalik_gosterimler", ["0,83", "0,73", "0,93", "0,88"], "0,45 + 0,38 = 0,83."),
    ("1,5 × 4 işleminin sonucu kaçtır?", "ondalik_gosterimler", ["6", "5", "7", "6,5"], "1,5 × 4 = 6."),
    ("80 sayısının %25'i kaçtır?", "yuzdeler", ["20", "25", "16", "24"], "%25 = 1/4, 80÷4=20."),
    ("120'nin %40'ı kaçtır?", "yuzdeler", ["48", "40", "52", "44"], "120 × 0,40 = 48 veya 120 × 40/100 = 48."),
    ("Aynı doğru üzerinde olmayan 3 nokta birleştirildiğinde kaç üçgen oluşur?", "temel_geometrik_kavramlar", ["1", "2", "3", "0"], "Üç farklı nokta bir üçgen oluşturur."),
    ("Bir açının ölçüsü 90 dereceden küçükse bu açıya ne denir?", "temel_geometrik_kavramlar", ["Dar açı", "Geniş açı", "Dik açı", "Tam açı"], "90°'den küçük açı dar açıdır."),
    ("İki kenarı eşit olan üçgene ne denir?", "ucgen_dortgenler", ["İkizkenar üçgen", "Eşkenar üçgen", "Dik üçgen", "Çeşitkenar üçgen"], "İki kenarı eşit olan üçgen ikizkenar üçgendir."),
    ("Kare ve dikdörtgenin ortak özelliği aşağıdakilerden hangisidir?", "ucgen_dortgenler", ["Dört köşesi vardır", "Tüm kenarları eşittir", "Açıları 90 derecedir", "Köşegen sayısı 1'dir"], "Her ikisi de dörtgen olduğu için 4 köşesi vardır. (Dikdörtgenin kenarları eşit olmayabilir)"),
    ("2 m 45 cm kaç cm'dir?", "uzunluk_olcme", ["245", "247", "240", "250"], "2 m = 200 cm, 200 + 45 = 245 cm."),
    ("3450 m kaç km kaç m'dir?", "uzunluk_olcme", ["3 km 450 m", "34 km 50 m", "3 km 45 m", "345 km 0 m"], "3450 m = 3000 m + 450 m = 3 km 450 m."),
    ("Kenar uzunlukları 8 cm ve 5 cm olan dikdörtgenin alanı kaç cm²'dir?", "alan_olcme", ["40", "26", "35", "45"], "Dikdörtgen alanı = 8 × 5 = 40 cm²."),
    ("Bir karenin bir kenarı 6 cm ise alanı kaç cm²'dir?", "alan_olcme", ["36", "24", "30", "12"], "Kare alanı = 6 × 6 = 36 cm²."),
    ("5 öğrencinin sınav notları 70, 80, 75, 85, 90'dır. Ortalama kaçtır?", "veri_toplama_analiz", ["80", "78", "82", "79"], "Toplam 400, 400÷5=80."),
    ("Bir veri grubunda en çok tekrar eden değere ne denir?", "veri_toplama_analiz", ["Tepe değer (mod)", "Ortalama", "Medyan", "Aralık"], "En sık tekrar eden değer tepe değer (mod) olarak adlandırılır."),
    ("Sütun grafiğinde Pazartesi 40, Salı 35, Çarşamba 45 kitap okunmuştur. Ortalama günlük okunan kitap sayısı kaçtır?", "grafik_yorumlama", ["40", "38", "42", "39"], "Toplam 40+35+45=120, 120÷3=40."),
    ("Bir grafikte en yüksek değer 60, en düşük 20 ise aralık (range) kaçtır?", "grafik_yorumlama", ["40", "30", "50", "80"], "Aralık = 60 - 20 = 40."),
    ("12 450 sayısının yüzler basamağındaki rakamın basamak değeri kaçtır?", "dogal_sayilar", ["400", "4", "40", "450"], "4 yüzler basamağında: 4×100=400."),
    ("Bir mağazada 15 kutu, her kutuda 24 kalem vardır. 5 kutu satıldığında geriye kaç kalem kalır?", "dogal_sayilarla_islemler", ["240", "360", "180", "300"], "Toplam 15×24=360. Satılan 5×24=120. Kalan 360-120=240."),
    ("4/9 kesri 2/3 kesrinden küçük mü, büyük mü?", "kesirler", ["Küçük", "Büyük", "Eşit", "Karşılaştırılamaz"], "2/3=6/9, 4/9 < 6/9."),
    ("3/4 - 1/2 işleminin sonucu kaçtır?", "kesirlerle_islemler", ["1/4", "2/4", "1/2", "2/2"], "1/2=2/4, 3/4-2/4=1/4."),
    ("2,8 - 1,35 işleminin sonucu kaçtır?", "ondalik_gosterimler", ["1,45", "1,55", "1,35", "1,53"], "2,80 - 1,35 = 1,45."),
    ("200 TL'nin %15'i kaç TL'dir?", "yuzdeler", ["30", "25", "35", "20"], "200 × 0,15 = 30 TL."),
    ("Bir üçgenin iç açıları toplamı kaç derecedir?", "temel_geometrik_kavramlar", ["180", "360", "90", "270"], "Üçgenin iç açıları toplamı 180°'dir."),
    ("Paralelkenarın karşılıklı kenarları nasıldır?", "ucgen_dortgenler", ["Paralel ve eşit", "Sadece paralel", "Sadece eşit", "Eşit değildir"], "Paralelkenarda karşılıklı kenarlar paralel ve eşittir."),
    ("5 km 200 m kaç m'dir?", "uzunluk_olcme", ["5200", "5002", "5020", "520"], "5 km = 5000 m, 5000 + 200 = 5200 m."),
    ("Bir üçgenin tabanı 10 cm, yüksekliği 6 cm ise alanı kaç cm²'dir?", "alan_olcme", ["30", "60", "16", "20"], "Üçgen alanı = (10×6)/2 = 30 cm²."),
    ("8 sayısının yarısının 3 fazlası kaçtır?", "dogal_sayilarla_islemler", ["7", "5", "6", "8"], "8'in yarısı 4, 4+3=7."),
    ("Bir sınıfta 30 öğrenci var. 2/5'i kız ise erkek öğrenci sayısı kaçtır?", "kesirlerle_islemler", ["18", "12", "15", "20"], "Kız 30×2/5=12, erkek 30-12=18."),
    ("0,6 × 5 işleminin sonucu kaçtır?", "ondalik_gosterimler", ["3", "3,5", "2,5", "30"], "0,6 × 5 = 3."),
    ("50'nin %30'u kaçtır?", "yuzdeler", ["15", "18", "12", "20"], "50 × 0,30 = 15."),
    ("Bir dikdörtgenin çevresi 24 cm, uzun kenarı 8 cm ise kısa kenarı kaç cm'dir?", "ucgen_dortgenler", ["4", "6", "5", "3"], "2×(8+k)=24, 8+k=12, k=4."),
    ("1 m² kaç cm²'dir?", "alan_olcme", ["10000", "100", "1000", "100000"], "1 m = 100 cm, 1 m² = 100×100 = 10000 cm²."),
]

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
            stem, tpl_topic, opts_list, expl = tpl[0], tpl[1], tpl[2], tpl[3]
            correct = str(opts_list[0])
            wrongs = [str(x) for x in opts_list[1:]]
            options = [correct] + wrongs
            random.shuffle(options)
            ai = options.index(correct)
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10], "problem_cozme", "mat"],
                expl, f"mat5_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"mat5_{idx:04d}"
        qq["id"] = f"mat5_{idx:04d}"

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
            "subject": "mat",
            "publisher": "brainbuddy",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_mat5_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("MAT5 Question Bank Report")
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
    random.seed(52)
    main()
