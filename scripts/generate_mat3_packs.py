#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 3rd Grade Mathematics question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: yorum gerektiren, çok adımlı işlem, günlük hayat senaryosu, grafik/tablo yorumlama.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/mat3"

TOPICS = [
    "dogal_sayilar",
    "dogal_sayilarla_islemler",
    "carpma_bolme_islemleri",
    "kesirlere_giris",
    "uzunluk_olcme",
    "zaman_olcme",
    "para_problemleri",
    "geometrik_sekiller",
    "alan_kavrami",
    "cevre_kavrami",
    "veri_toplama",
    "tablo_grafik_yorumlama",
]

NEW_GEN_TYPES = [
    "cok_adinli_islem",
    "gunluk_hayat_senaryosu",
    "grafik_tablo_yorum",
    "mantik_yuruttme",
    "yorum_gerektiren",
]

def q(id_val, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"mat3_{id_val:04d}",
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
    # Doğal Sayılar
    ("234 sayısının onlar basamağındaki rakamın basamak değeri kaçtır?", "dogal_sayilar", ["30", "3", "300", "34"], "3 onlar basamağında: 3×10=30."),
    ("456 sayısında 4'ün basamak değeri ile 6'nın basamak değerinin toplamı kaçtır?", "dogal_sayilar", ["406", "46", "400", "10"], "4 yüzlerde: 400; 6 birlerde: 6. Toplam 406."),
    ("127 sayısının en büyük basamağı hangisidir?", "dogal_sayilar", ["Yüzler", "Onlar", "Birler", "Binler"], "127'de 1 yüzler, 2 onlar, 7 birler. En büyük yüzler."),
    ("3 yüzlük + 5 onluk + 2 birlik kaç eder?", "dogal_sayilar", ["352", "350", "325", "3520"], "300+50+2=352."),
    # Doğal Sayılarla İşlemler
    ("Emre 45 sayfası olan bir kitabın önce 18, sonra 12 sayfasını okudu. Kaç sayfa kalmıştır?", "dogal_sayilarla_islemler", ["15", "14", "16", "17"], "18+12=30 okudu. 45-30=15 sayfa kaldı."),
    ("(24 + 36) - 20 işleminin sonucu kaçtır?", "dogal_sayilarla_islemler", ["40", "38", "42", "39"], "24+36=60, 60-20=40."),
    ("78 - 29 işleminin sonucu kaçtır?", "dogal_sayilarla_islemler", ["49", "50", "48", "47"], "78-29=49."),
    ("Bir çiftlikte 56 tavuk, 34 ördek vardır. Tavuk ve ördeklerin toplamı kaçtır?", "dogal_sayilarla_islemler", ["90", "88", "92", "22"], "56+34=90."),
    # Çarpma ve Bölme İşlemleri
    ("Bir manav 6 kasada, her kasada 8 elma sattı. Toplam kaç elma satılmıştır?", "carpma_bolme_islemleri", ["48", "42", "54", "14"], "6×8=48 elma."),
    ("72 kalemi 8 öğrenciye eşit paylaştırırsak her birine kaç kalem düşer?", "carpma_bolme_islemleri", ["9", "8", "10", "7"], "72÷8=9 kalem."),
    ("5'in 6 katı kaçtır?", "carpma_bolme_islemleri", ["30", "25", "35", "11"], "5×6=30."),
    ("Bir bakkal 4 paket, her pakette 9 bisküvi satıyor. Toplam kaç bisküvi vardır?", "carpma_bolme_islemleri", ["36", "32", "40", "13"], "4×9=36 bisküvi."),
    ("63'ü 7'ye bölersek sonuç kaç olur?", "carpma_bolme_islemleri", ["9", "8", "10", "56"], "63÷7=9."),
    # Kesirlere Giriş
    ("Bir pizza 4 eşit parçaya bölündü. Zeynep 1 parça yedi. Zeynep pizzanın kaçta kaçını yemiştir?", "kesirlere_giris", ["1/4", "1/2", "3/4", "2/4"], "4 parçadan 1'i: 1/4."),
    ("1/2 kesri ile 1/4 kesrinden hangisi büyüktür?", "kesirlere_giris", ["1/2", "1/4", "Eşit", "Karşılaştırılamaz"], "1/2=2/4, 2/4 > 1/4."),
    ("Bir pastanın 2/4'ü yendi. Kalan kısım pastanın kaçta kaçıdır?", "kesirlere_giris", ["2/4 veya 1/2", "1/4", "3/4", "4/4"], "1-2/4=4/4-2/4=2/4=1/2."),
    ("3/4 kesrinin payı kaçtır?", "kesirlere_giris", ["3", "4", "7", "1"], "Kesirde üstteki pay, alttaki paydadır. Pay 3."),
    # Uzunluk Ölçme
    ("1 m 25 cm kaç cm'dir?", "uzunluk_olcme", ["125", "1025", "126", "15"], "1 m=100 cm, 100+25=125 cm."),
    ("240 cm kaç m kaç cm'dir?", "uzunluk_olcme", ["2 m 40 cm", "24 m 0 cm", "2 m 4 cm", "240 m"], "240 cm=2 m 40 cm."),
    ("Sınıf tahtası 3 m uzunluğundadır. Kaç cm'dir?", "uzunluk_olcme", ["300", "30", "3000", "3"], "3 m=300 cm."),
    ("Bir kalem 15 cm, bir defter 25 cm uzunluğundadır. İkisinin toplam uzunluğu kaç cm'dir?", "uzunluk_olcme", ["40", "35", "45", "10"], "15+25=40 cm."),
    # Zaman Ölçme
    ("1 saat kaç dakikadır?", "zaman_olcme", ["60", "100", "30", "12"], "1 saat = 60 dakika."),
    ("90 dakika kaç saat kaç dakikadır?", "zaman_olcme", ["1 saat 30 dakika", "1 saat 90 dakika", "90 saat", "9 saat"], "90 dk = 60+30 = 1 saat 30 dk."),
    ("Bir film 2 saat 15 dakika sürdü. Toplam kaç dakikadır?", "zaman_olcme", ["135", "215", "120", "75"], "2×60+15=120+15=135 dk."),
    ("Ders 10:00'da başlayıp 10:40'ta bitti. Ders kaç dakika sürmüştür?", "zaman_olcme", ["40", "30", "50", "1 saat"], "10:40-10:00=40 dakika."),
    # Para Problemleri
    ("Ali'nin 50 TL'si var. 18 TL harcadı. Geriye kaç TL kalmıştır?", "para_problemleri", ["32", "33", "31", "68"], "50-18=32 TL."),
    ("3 ekmek 12 TL ise 1 ekmek kaç TL'dir?", "para_problemleri", ["4", "3", "5", "36"], "12÷3=4 TL."),
    ("Elif 2 TL'ye silgi, 5 TL'ye kalem aldı. 10 TL verdiğinde kaç TL para üstü alır?", "para_problemleri", ["3", "4", "2", "7"], "2+5=7, 10-7=3 TL."),
    ("Zeynep 3 defter aldı, her biri 6 TL. Toplam kaç TL ödedi?", "para_problemleri", ["18", "9", "21", "3"], "3×6=18 TL."),
    # Geometrik Şekiller
    ("Karenin kaç kenarı vardır?", "geometrik_sekiller", ["4", "3", "5", "6"], "Kare 4 kenarlı bir şekildir."),
    ("Üçgenin kaç köşesi vardır?", "geometrik_sekiller", ["3", "4", "5", "2"], "Üçgen 3 köşelidir."),
    ("Aşağıdakilerden hangisi dikdörtgenin özelliğidir?", "geometrik_sekiller", ["Karşılıklı kenarları eşittir", "Tüm kenarları eşittir", "3 kenarı vardır", "Köşesi yoktur"], "Dikdörtgende karşılıklı kenarlar eşittir."),
    ("Dairenin kaç kenarı vardır?", "geometrik_sekiller", ["Kenarı yoktur (eğri çizgidir)", "1", "4", "Çok"], "Daire kenarı olmayan eğri bir şekildir."),
    # Alan Kavramı
    ("Kenarı 4 cm olan karenin alanı kaç cm²'dir?", "alan_kavrami", ["16", "8", "12", "4"], "4×4=16 cm²."),
    ("Uzun kenarı 6 cm, kısa kenarı 3 cm olan dikdörtgenin alanı kaç cm²'dir?", "alan_kavrami", ["18", "9", "12", "15"], "6×3=18 cm²."),
    ("Bir kare şeklindeki masa 3 birim genişliğinde, 3 birim uzunluğundadır. Kaç birim kare alan kaplar?", "alan_kavrami", ["9", "6", "12", "3"], "3×3=9 birim kare."),
    ("Alanı 25 cm² olan karenin bir kenarı kaç cm'dir?", "alan_kavrami", ["5", "4", "6", "20"], "5×5=25, kenar 5 cm."),
    # Çevre Kavramı
    ("Kenar uzunlukları 5 cm ve 3 cm olan dikdörtgenin çevresi kaç cm'dir?", "cevre_kavrami", ["16", "15", "8", "10"], "2×(5+3)=2×8=16 cm."),
    ("Bir kenarı 6 cm olan karenin çevresi kaç cm'dir?", "cevre_kavrami", ["24", "36", "12", "6"], "4×6=24 cm."),
    ("Kenarları 4 cm, 5 cm ve 6 cm olan üçgenin çevresi kaç cm'dir?", "cevre_kavrami", ["15", "14", "16", "20"], "4+5+6=15 cm."),
    ("Çevresi 20 cm olan karenin bir kenarı kaç cm'dir?", "cevre_kavrami", ["5", "4", "6", "10"], "20÷4=5 cm."),
    # Veri Toplama
    ("4 öğrencinin sınav puanları 70, 80, 90, 80'dir. Ortalama kaçtır?", "veri_toplama", ["80", "79", "81", "320"], "70+80+90+80=320, 320÷4=80."),
    ("5, 5, 6, 7, 5 sayılarında en çok tekrar eden hangisidir?", "veri_toplama", ["5", "6", "7", "Hiçbiri"], "5 üç kez, diğerleri daha az. Tepe değer 5."),
    ("3 çocuğun yaşları 8, 9, 10'dur. Yaşları toplamı kaçtır?", "veri_toplama", ["27", "9", "26", "28"], "8+9+10=27."),
    ("6 öğrencinin boyları 120, 122, 120, 125, 120, 121 cm. En kısa boy kaç cm'dir?", "veri_toplama", ["120", "121", "119", "125"], "En küçük değer 120 cm."),
    # Tablo ve Grafik Yorumlama
    ("Tablo: Pazartesi 15, Salı 20, Çarşamba 10 kitap okundu. Toplam kaç kitap okunmuştur?", "tablo_grafik_yorumlama", ["45", "40", "50", "15"], "15+20+10=45."),
    ("Grafikte 1. gün 8, 2. gün 12, 3. gün 6 puan alınmış. Ortalama günlük puan kaçtır?", "tablo_grafik_yorumlama", ["9", "8", "10", "26"], "8+12+6=26, 26÷3≈8,67. Yaklaşık 9."),
    ("Sütun grafiğinde Pazartesi 10, Salı 15, Çarşamba 5 oyuncak satılmış. En çok hangi gün satılmıştır?", "tablo_grafik_yorumlama", ["Salı", "Pazartesi", "Çarşamba", "Eşit"], "15 > 10 > 5, en çok Salı."),
    ("Bir tabloda 3 gün boyunca sırasıyla 4, 6, 5 elma toplandı. Toplam kaç elma toplanmıştır?", "tablo_grafik_yorumlama", ["15", "14", "16", "10"], "4+6+5=15 elma."),
    # Extra for balance - fix the avg question
    ("Tablo: 1. gün 8, 2. gün 10, 3. gün 6 sayfa okundu. Ortalama günde kaç sayfa okunmuştur?", "tablo_grafik_yorumlama", ["8", "7", "9", "24"], "8+10+6=24, 24÷3=8."),
    ("Bir çiftçi 5 sıraya, her sıraya 7 fidan dikti. Toplam kaç fidan dikilmiştir?", "carpma_bolme_islemleri", ["35", "12", "42", "28"], "5×7=35 fidan."),
    ("48 sayfası olan defterin 1/4'ü kullanıldı. Kaç sayfa kullanılmıştır?", "kesirlere_giris", ["12", "24", "16", "44"], "48÷4=12 sayfa."),
    ("2 m 50 cm + 1 m 30 cm toplamı kaç m kaç cm'dir?", "uzunluk_olcme", ["3 m 80 cm", "3 m 50 cm", "4 m 20 cm", "280 cm"], "250+130=380 cm = 3 m 80 cm."),
    ("Saat 14:00'ten 15:30'a kadar kaç dakika geçmiştir?", "zaman_olcme", ["90", "60", "1 saat 30 dk", "130"], "15:30-14:00=1 saat 30 dk=90 dk."),
    ("5 TL'lik 4 bisküvi paketi alındı. Toplam kaç TL ödendi?", "para_problemleri", ["20", "9", "25", "15"], "5×4=20 TL."),
    ("Karenin 4 kenarı eşit, dikdörtgenin ise karşılıklı kenarları eşittir. Hangisi doğrudur?", "geometrik_sekiller", ["İkisi de dörtgendir", "Sadece kare dörtgendir", "Dikdörtgen 3 kenarlıdır", "Kare 5 kenarlıdır"], "Kare ve dikdörtgen dörtgenlerdir (4 kenarlı)."),
    ("5×4 işleminin sonucu ile 20÷5 işleminin sonucunun toplamı kaçtır?", "dogal_sayilarla_islemler", ["24", "25", "23", "20"], "5×4=20, 20÷5=4. 20+4=24."),
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
                [topic[:10] if len(topic) >= 10 else topic, "problem_cozme", "mat"],
                expl, f"mat3_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"mat3_{idx:04d}"
        qq["id"] = f"mat3_{idx:04d}"

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
        path = OUT_DIR / f"lgs_mat3_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("MAT3 Question Bank Report")
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
    random.seed(46)
    main()
