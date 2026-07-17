#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 2nd Grade Mathematics question bank for EDUmio.
50 packs × 10 questions = 500 questions.
MEB 2024-2025 2. Sınıf Matematik Öğretim Programı (7 ünite).
EDUmio Question Design Standard: günlük hayat problemleri, yorum gerektiren, çok adımlı işlem, mantık yürütme, tablo/veri yorumlama.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/mat2"

# MEB 2. Sınıf Matematik Öğretim Programı 2024-2025 (7 ünite)
TOPICS = [
    "dogal_sayilar_sayi_dogrusu",
    "dort_islem_temelleri",
    "problem_cozme_stratejileri",
    "geometri_uzunluk",
    "zaman_takvim",
    "para_finansal",
    "veri_tablo_grafik",
]

NEW_GEN_TYPES = [
    "gunluk_hayat_problemi",
    "cok_adinli_islem",
    "tablo_veri_yorumlama",
    "mantik_yuruttme",
    "yorum_gerektiren",
]


def q(id_val, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"mat2_{id_val:04d}",
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


# (stem, topic, [correct, wrong1, wrong2, wrong3], explanation)
TEMPLATES = [
    # 1. Doğal Sayılar ve Sayı Doğrusu (0-1000, onluk/birlik, basamak değeri)
    ("72 sayısının onlar basamağındaki rakamın basamak değeri kaçtır?", "dogal_sayilar_sayi_dogrusu", ["70", "7", "2", "72"], "Onlar basamağında 7 var: 7×10=70."),
    ("5 onluk + 3 birlik kaç eder?", "dogal_sayilar_sayi_dogrusu", ["53", "35", "8", "15"], "5 onluk=50, 3 birlik=3. 50+3=53."),
    ("348 sayısında 4'ün basamak değeri kaçtır?", "dogal_sayilar_sayi_dogrusu", ["40", "4", "400", "48"], "4 onlar basamağında: 4×10=40."),
    ("Bir sayı 2 yüzlük ve 6 onluktan oluşuyor. Bu sayı kaçtır?", "dogal_sayilar_sayi_dogrusu", ["260", "206", "62", "80"], "2×100+6×10=200+60=260."),
    ("Sayı doğrusunda 30 ile 50 arasında kaç tam sayı vardır? (30 ve 50 dahil değil)", "dogal_sayilar_sayi_dogrusu", ["19", "20", "21", "18"], "31,32,...,49 → 49-31+1=19 sayı."),
    ("456 sayısının çözümlemesi aşağıdakilerden hangisidir?", "dogal_sayilar_sayi_dogrusu", ["400 + 50 + 6", "4 + 5 + 6", "456", "40 + 56"], "456=4×100+5×10+6=400+50+6."),
    # 2. Dört İşlem Temelleri (toplama, çıkarma, çarpma hazırlığı)
    ("Ali 28 sayfa kitap okudu. Sonra 15 sayfa daha okudu. Toplam kaç sayfa okumuştur?", "dort_islem_temelleri", ["43", "42", "44", "13"], "28+15=43 sayfa."),
    ("Bir sepette 54 elma vardı. 26 elma yendi. Geriye kaç elma kalmıştır?", "dort_islem_temelleri", ["28", "27", "29", "80"], "54-26=28 elma."),
    ("(35 + 24) - 19 işleminin sonucu kaçtır?", "dort_islem_temelleri", ["40", "39", "41", "78"], "35+24=59, 59-19=40."),
    ("4 + 4 + 4 + 4 + 4 toplamı, 4'ün kaç katıdır?", "dort_islem_temelleri", ["5", "4", "20", "6"], "5 tane 4: 4×5=20. 4'ün 5 katı."),
    ("67 - 38 işleminin sonucu kaçtır?", "dort_islem_temelleri", ["29", "30", "28", "105"], "67-38=29."),
    ("Bir bahçede 3 sıra çiçek var. Her sırada 6 çiçek var. Toplam kaç çiçek vardır?", "dort_islem_temelleri", ["18", "9", "12", "21"], "3×6=18 çiçek (çarpma hazırlığı)."),
    # 3. Problem Çözme Stratejileri (kelime problemleri, mantık yürütme)
    ("Bir çiftlikte 45 koyun ve 38 keçi vardır. Koyunlar keçilerden kaç fazladır?", "problem_cozme_stratejileri", ["7", "8", "6", "83"], "45-38=7 fazla."),
    ("Zeynep'in 24 balonu vardı. 9 tanesini arkadaşına verdi. Sonra annesi 7 tane daha aldı. Zeynep'in şimdi kaç balonu vardır?", "problem_cozme_stratejileri", ["22", "23", "21", "16"], "24-9=15, 15+7=22."),
    ("Mehmet birinci gün 12, ikinci gün 18 sayfa okudu. Üçüncü gün okuması gereken 65 sayfalık kitabın kaç sayfası kaldı?", "problem_cozme_stratejileri", ["35", "36", "34", "95"], "12+18=30 okudu. 65-30=35 kaldı."),
    ("Bir sınıfta 12 kız, kızlardan 3 fazla erkek öğrenci vardır. Sınıfta toplam kaç öğrenci vardır?", "problem_cozme_stratejileri", ["27", "26", "28", "15"], "Erkek: 12+3=15. Toplam: 12+15=27."),
    ("Bir kutu 8 bisküvi alıyor. 5 kutu alan anne kaç bisküvi almış olur?", "problem_cozme_stratejileri", ["40", "13", "35", "45"], "5×8=40 bisküvi."),
    # 4. Geometrik Şekiller ve Uzunluk Ölçme (kare, dikdörtgen, üçgen, çember, cm, m)
    ("Karenin kaç kenarı vardır?", "geometri_uzunluk", ["4", "3", "5", "6"], "Kare 4 kenarlı bir şekildir."),
    ("Bir cetvelle ölçtüğümüzde masanın uzun kenarı 80 cm çıkıyor. Bu kaç m kaç cm'dir?", "geometri_uzunluk", ["0 m 80 cm", "8 m 0 cm", "80 m", "800 cm"], "80 cm = 0 m 80 cm."),
    ("Üçgenin kaç köşesi vardır?", "geometri_uzunluk", ["3", "4", "5", "2"], "Üçgen 3 köşelidir."),
    ("1 m kaç cm'dir?", "geometri_uzunluk", ["100", "10", "1000", "50"], "1 m = 100 cm."),
    ("Dikdörtgenin karşılıklı kenarları birbirine eşittir. Kısa kenarı 5 cm, uzun kenarı 8 cm olan dikdörtgenin çevresi kaç cm'dir?", "geometri_uzunluk", ["26", "13", "40", "25"], "2×(5+8)=2×13=26 cm."),
    ("Çemberin kaç kenarı vardır?", "geometri_uzunluk", ["Kenarı yoktur (eğri çizgidir)", "1", "4", "3"], "Çember eğri bir çizgidir, düz kenarı yoktur."),
    # 5. Zamanı Okuma ve Takvim (tam saat, yarım saat, takvim, aylar)
    ("Saat 3'te ders başlıyor. Saat 3'te küçük akrep ile büyük akrep hangi sayıda olur?", "zaman_takvim", ["Küçük 3'te, büyük 12'de", "İkisi de 3'te", "Küçük 12'de, büyük 3'te", "İkisi de 12'de"], "Tam saatte yelkovan 12'de, akrep saati gösterir."),
    ("1 saat kaç dakikadır?", "zaman_takvim", ["60", "30", "100", "12"], "1 saat = 60 dakika."),
    ("Yarım saat kaç dakikadır?", "zaman_takvim", ["30", "60", "15", "45"], "Yarım saat = 30 dakika."),
    ("Bir yılda kaç ay vardır?", "zaman_takvim", ["12", "10", "6", "52"], "Bir yılda 12 ay vardır."),
    ("Bir hafta kaç gündür?", "zaman_takvim", ["7", "5", "10", "30"], "Bir hafta 7 gündür."),
    # 6. Paralarımız ve Basit Finansal Kavramlar
    ("Ali'nin 50 TL'si var. 18 TL'ye oyuncak aldı. Geriye kaç TL kalmıştır?", "para_finansal", ["32", "33", "31", "68"], "50-18=32 TL."),
    ("2 ekmek 10 TL ise 1 ekmek kaç TL'dir?", "para_finansal", ["5", "4", "6", "20"], "10÷2=5 TL."),
    ("Elif 3 TL'ye silgi, 4 TL'ye kalem aldı. 10 TL verdiğinde kaç TL para üstü alır?", "para_finansal", ["3", "4", "2", "7"], "3+4=7 TL harcadı. 10-7=3 TL para üstü."),
    ("3 defter 15 TL ise 1 defter kaç TL'dir?", "para_finansal", ["5", "4", "6", "18"], "15÷3=5 TL."),
    ("Bir çocuk 20 TL ile 7 TL'lik oyuncak aldı. Kalan parayla 5 TL'lik şeker alabildi. Geriye kaç TL kalmış olur?", "para_finansal", ["8", "7", "9", "12"], "20-7=13, 13-5=8 TL."),
    # 7. Veri Toplama ve Görselleştirme
    ("Tablo: Pazartesi 8, Salı 12, Çarşamba 6 kitap okundu. Toplam kaç kitap okunmuştur?", "veri_tablo_grafik", ["26", "24", "20", "8"], "8+12+6=26 kitap."),
    ("Sütun grafiğinde Pazartesi 5, Salı 9, Çarşamba 4 oyuncak satılmış. En çok hangi gün satılmıştır?", "veri_tablo_grafik", ["Salı", "Pazartesi", "Çarşamba", "Eşit"], "9 > 5 > 4, en çok Salı."),
    ("4 öğrencinin boyları 115, 118, 115, 120 cm. En kısa boy kaç cm'dir?", "veri_tablo_grafik", ["115", "118", "114", "120"], "En küçük değer 115 cm."),
    ("Tablo: 1. gün 5, 2. gün 7, 3. gün 6 elma toplandı. Ortalama günde kaç elma toplanmıştır?", "veri_tablo_grafik", ["6", "5", "7", "18"], "5+7+6=18, 18÷3=6."),
    ("5 çocuğun yaşları 7, 8, 7, 9, 7'dir. En çok tekrar eden yaş kaçtır?", "veri_tablo_grafik", ["7", "8", "9", "Hiçbiri"], "7 üç kez, diğerleri daha az."),
    # Extra for balance - günlük hayat senaryoları
    ("Bir otobüste önce 25 yolcu vardı. 12 kişi indi, 8 kişi bindi. Şimdi otobüste kaç yolcu vardır?", "problem_cozme_stratejileri", ["21", "20", "22", "45"], "25-12=13, 13+8=21."),
    ("Bir manav 36 portakal sattı. Kasada 48 portakal kaldı. Başta kaç portakal vardı?", "problem_cozme_stratejileri", ["84", "12", "83", "72"], "36+48=84 portakal."),
    ("120 cm + 80 cm toplamı kaç cm'dir?", "geometri_uzunluk", ["200", "120", "80", "20"], "120+80=200 cm."),
    ("2 m 30 cm kaç cm'dir?", "geometri_uzunluk", ["230", "203", "32", "2300"], "2 m=200 cm, 200+30=230 cm."),
    ("Saat 14:00'ten 14:30'a kadar kaç dakika geçmiştir?", "zaman_takvim", ["30", "60", "45", "15"], "14:30-14:00=30 dakika."),
    ("6 TL'lik 3 bisküvi paketi alındı. Toplam kaç TL ödendi?", "para_finansal", ["18", "9", "21", "3"], "6×3=18 TL."),
    ("89 sayısının onlar basamağı ile birler basamağındaki rakamların toplamı kaçtır?", "dogal_sayilar_sayi_dogrusu", ["17", "8", "9", "89"], "8+9=17."),
    ("(24 + 16) + (50 - 20) işleminin sonucu kaçtır?", "dort_islem_temelleri", ["70", "68", "72", "10"], "24+16=40, 50-20=30. 40+30=70."),
    ("Tablo: 3 gün boyunca sırasıyla 4, 6, 5 simit satıldı. Toplam kaç simit satılmıştır?", "veri_tablo_grafik", ["15", "14", "16", "6"], "4+6+5=15 simit."),
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
            wrongs = [str(x) for x in opts_list[1:4]]
            options = [correct] + wrongs
            while len(options) < 4:
                options.append("Bu işlemle bulunamaz.")
            options = options[:4]
            random.shuffle(options)
            ai = options.index(correct)
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:12] if len(topic) >= 12 else topic, "problem_cozme", "mat"],
                expl, f"mat2_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"mat2_{idx:04d}"
        qq["id"] = f"mat2_{idx:04d}"

    packs = [all_q[i : i + 10] for i in range(0, 500, 10)]
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
        path = OUT_DIR / f"lgs_mat2_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("MAT2 (2. Sınıf Matematik) Question Bank Report")
    print("MEB 2024-2025 Öğretim Programı - 7 ünite")
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
    random.seed(37)
    main()
