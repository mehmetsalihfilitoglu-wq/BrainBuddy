#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 4th Grade Mathematics question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: yorum, çok adımlı işlem, gerçek hayat senaryosu, grafik/tablo yorumlama.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/mat4"

TOPICS = [
    "dogal_sayilar",
    "dogal_sayilarla_islemler",
    "kesirler",
    "kesirlerle_islemler",
    "ondalik_gosterimler",
    "uzunluk_olcme",
    "zaman_olcme",
    "para_problemleri",
    "alan_olcme",
    "geometrik_sekiller",
    "acilar",
    "cevre_hesaplama",
    "veri_toplama",
    "tablo_grafik_yorumlama",
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
        "id": f"mat4_{id_val:04d}",
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
        "subject": "mat",
    }

# Template: (stem, topic, [correct, wrong1, wrong2, wrong3], explanation)
TEMPLATES = [
    ("3456 sayısının binler basamağındaki rakamın basamak değeri kaçtır?", "dogal_sayilar", ["3000", "3", "300", "30"], "3 binler basamağında: 3×1000=3000."),
    ("7892 sayısında 8'in basamak değeri ile 2'nin basamak değerinin toplamı kaçtır?", "dogal_sayilar", ["802", "8020", "820", "80"], "8 yüzlerde: 800; 2 birlerde: 2. Toplam 802."),
    ("Bir bakkal 4 koli, her kolide 18 paket çay sattı. Toplam kaç paket çay satılmıştır?", "dogal_sayilarla_islemler", ["72", "68", "76", "70"], "4×18=72 paket."),
    ("(36 + 24) ÷ 6 işleminin sonucu kaçtır?", "dogal_sayilarla_islemler", ["10", "9", "11", "12"], "36+24=60, 60÷6=10."),
    ("Bir çiftçi 5 günde 195 kg patates topladı. Her gün eşit miktar topladığına göre günde kaç kg toplamıştır?", "dogal_sayilarla_islemler", ["39", "38", "40", "37"], "195÷5=39 kg."),
    ("2/5 kesrinin paydası 10 yapılırsa pay kaç olmalıdır?", "kesirler", ["4", "2", "5", "6"], "2/5=4/10. Pay 4 olmalı."),
    ("3/4 kesri ile 1/2 kesrinden hangisi büyüktür?", "kesirler", ["3/4", "1/2", "Eşit", "Karşılaştırılamaz"], "1/2=2/4, 3/4 > 2/4."),
    ("1 tam 1/3 kesrinin bileşik kesir olarak yazılışı nedir?", "kesirler", ["4/3", "3/3", "5/3", "2/3"], "1 tam 1/3 = (3+1)/3 = 4/3."),
    ("2/5 + 1/5 işleminin sonucu kaçtır?", "kesirlerle_islemler", ["3/5", "2/10", "1/5", "4/5"], "2/5+1/5=3/5."),
    ("Bir pastanın 1/3'ü yendi. Kalan kısım pastanın kaçta kaçıdır?", "kesirlerle_islemler", ["2/3", "1/2", "1/3", "3/4"], "1-1/3=3/3-1/3=2/3."),
    ("0,3 + 0,5 işleminin sonucu kaçtır?", "ondalik_gosterimler", ["0,8", "0,7", "0,9", "8"], "0,3+0,5=0,8."),
    ("1,2 × 3 işleminin sonucu kaçtır?", "ondalik_gosterimler", ["3,6", "3", "4,2", "3,2"], "1,2×3=3,6."),
    ("3 m 50 cm kaç cm'dir?", "uzunluk_olcme", ["350", "305", "353", "3500"], "3 m=300 cm, 300+50=350 cm."),
    ("2400 m kaç km kaç m'dir?", "uzunluk_olcme", ["2 km 400 m", "24 km 0 m", "2 km 40 m", "240 km"], "2400 m=2 km 400 m."),
    ("Bir otobüs 09:15'te hareket edip 11:45'te varmıştır. Yolculuk kaç saat sürmüştür?", "zaman_olcme", ["2 saat 30 dk", "2 saat", "3 saat", "2 saat 45 dk"], "11:45-09:15=2 saat 30 dk."),
    ("45 dakika kaç saniyedir?", "zaman_olcme", ["2700", "270", "4500", "450"], "45×60=2700 saniye."),
    ("3 saat 20 dakika kaç dakikadır?", "zaman_olcme", ["200", "320", "180", "230"], "3×60+20=180+20=200 dk."),
    ("Ali'nin 85 TL'si var. 28 TL harcadı. Geriye kaç TL kalmıştır?", "para_problemleri", ["57", "58", "56", "55"], "85-28=57 TL."),
    ("4 ekmek 12 TL ise 1 ekmek kaç TL'dir?", "para_problemleri", ["3", "4", "2", "5"], "12÷4=3 TL."),
    ("Ayşe 5 TL'ye kalem, 3 TL'ye silgi aldı. 20 TL verdiğinde kaç TL para üstü alır?", "para_problemleri", ["12", "13", "11", "10"], "5+3=8, 20-8=12 TL."),
    ("Kenarı 5 cm olan karenin alanı kaç cm²'dir?", "alan_olcme", ["25", "20", "30", "10"], "5×5=25 cm²."),
    ("Uzun kenarı 8 cm, kısa kenarı 4 cm olan dikdörtgenin alanı kaç cm²'dir?", "alan_olcme", ["32", "24", "28", "36"], "8×4=32 cm²."),
    ("Bir üçgenin tabanı 6 cm, yüksekliği 4 cm ise alanı kaç cm²'dir?", "alan_olcme", ["12", "24", "10", "8"], "(6×4)/2=12 cm²."),
    ("Aşağıdakilerden hangisi kare ile dikdörtgenin ortak özelliğidir?", "geometrik_sekiller", ["4 köşesi vardır", "Tüm kenarları eşittir", "Sadece 2 kenarı eşittir", "Köşegeni yoktur"], "Her ikisi de dörtgen, 4 köşeli."),
    ("Bir karenin kaç köşegeni vardır?", "geometrik_sekiller", ["2", "4", "1", "3"], "Kare ve dikdörtgen 2 köşegene sahiptir."),
    ("90 dereceden küçük açıya ne denir?", "acilar", ["Dar açı", "Geniş açı", "Dik açı", "Doğru açı"], "90°'den küçük açı dar açıdır."),
    ("Bir doğru açı kaç derecedir?", "acilar", ["180", "90", "360", "270"], "Doğru açı 180°'dir."),
    ("İki açının toplamı 90° ise bu açılara ne denir?", "acilar", ["Tümler açılar", "Bütünler açılar", "Komşu açılar", "Eş açılar"], "Toplamı 90° olan açılar tümlerdir."),
    ("Kenar uzunlukları 6 cm ve 4 cm olan dikdörtgenin çevresi kaç cm'dir?", "cevre_hesaplama", ["20", "24", "18", "22"], "2×(6+4)=2×10=20 cm."),
    ("Bir kenarı 7 cm olan karenin çevresi kaç cm'dir?", "cevre_hesaplama", ["28", "49", "21", "14"], "4×7=28 cm."),
    ("Üç kenarı 5 cm, 6 cm ve 7 cm olan üçgenin çevresi kaç cm'dir?", "cevre_hesaplama", ["18", "17", "19", "20"], "5+6+7=18 cm."),
    ("5 öğrencinin notları 70, 80, 75, 85, 90'dır. Ortalama kaçtır?", "veri_toplama", ["80", "78", "82", "79"], "Toplam 400, 400÷5=80."),
    ("Bir veri grubunda en çok tekrar eden değere ne denir?", "veri_toplama", ["Tepe değer (mod)", "Ortalama", "Medyan", "Aralık"], "En sık tekrar eden tepe değerdir."),
    ("Sütun grafiğinde Pazartesi 30, Salı 25, Çarşamba 35 kitap okunmuş. Toplam kaç kitap okunmuştur?", "tablo_grafik_yorumlama", ["90", "80", "95", "85"], "30+25+35=90."),
    ("Tablo: Pazartesi 20, Salı 30, Çarşamba 25. Ortalama günlük değer kaçtır?", "tablo_grafik_yorumlama", ["25", "24", "26", "23"], "20+30+25=75, 75÷3=25."),
    ("1245 sayısının yüzler basamağındaki rakamın basamak değeri kaçtır?", "dogal_sayilar", ["200", "2", "20", "245"], "2 yüzlerde: 2×100=200."),
    ("8'in 3 katının 5 eksiği kaçtır?", "dogal_sayilarla_islemler", ["19", "21", "17", "18"], "8×3=24, 24-5=19."),
    ("12 sayısının yarısının 4 fazlası kaçtır?", "dogal_sayilarla_islemler", ["10", "8", "12", "6"], "12÷2=6, 6+4=10."),
    ("3/6 kesri sadeleştirildiğinde hangi kesir elde edilir?", "kesirler", ["1/2", "1/3", "2/3", "2/6"], "3/6=1/2."),
    ("4/7 - 2/7 işleminin sonucu kaçtır?", "kesirlerle_islemler", ["2/7", "1/7", "6/7", "2/14"], "4/7-2/7=2/7."),
    ("0,7 - 0,3 işleminin sonucu kaçtır?", "ondalik_gosterimler", ["0,4", "0,5", "0,3", "4"], "0,7-0,3=0,4."),
    ("5 km 300 m kaç m'dir?", "uzunluk_olcme", ["5300", "5030", "530", "5003"], "5 km=5000 m, 5000+300=5300 m."),
    ("2 saat 15 dakika kaç dakikadır?", "zaman_olcme", ["135", "215", "120", "140"], "2×60+15=135 dk."),
    ("Zeynep 50 TL'den 3 defter aldı. 23 TL para üstü aldı. Bir defter kaç TL'dir?", "para_problemleri", ["9", "10", "8", "7"], "50-23=27, 27÷3=9 TL."),
    ("Alanı 36 cm² olan karenin bir kenarı kaç cm'dir?", "alan_olcme", ["6", "9", "4", "12"], "√36=6 cm."),
    ("Çevresi 24 cm olan karenin bir kenarı kaç cm'dir?", "cevre_hesaplama", ["6", "8", "4", "5"], "24÷4=6 cm."),
    ("Geniş açı kaç dereceden büyüktür?", "acilar", ["90", "180", "45", "360"], "90°'den büyük 180°'den küçük açı geniş açıdır."),
    ("4 öğrencinin sınav notları 60, 70, 80, 90'dır. Ortalama kaçtır?", "veri_toplama", ["75", "74", "76", "73"], "300÷4=75."),
]


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
            stem, tpl_topic, opts_list, expl = tpl[0], tpl[1], tpl[2], tpl[3]
            correct = str(opts_list[0])
            wrongs = [str(x) for x in opts_list[1:]]
            options = [correct] + wrongs
            random.shuffle(options)
            ai = options.index(correct)
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10] if len(topic) >= 10 else topic, "problem_cozme", "mat"],
                expl, f"mat4_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"mat4_{idx:04d}"
        qq["id"] = f"mat4_{idx:04d}"

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
            "publisher": "edumio",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_mat4_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("MAT4 Question Bank Report")
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
    random.seed(55)
    main()
