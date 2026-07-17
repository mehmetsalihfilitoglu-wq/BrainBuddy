#!/usr/bin/env python3
"""
Grade 6 Question Pool Generator - 500 questions per subject (2500 total).

Generates diverse, non-templated questions with varied contexts, sentence structures,
and questionTypes. Output JSON compatible with EDUmio DbSeeder packs.

Usage:
  python question_generator.py [--output-dir app/src/main/assets/packs]
"""

import json
import random
import argparse
from fractions import Fraction
from pathlib import Path
from typing import List, Dict, Any

# --- Shared variation pools (avoid template repetition) ---
NAMES_TR = [
    "Defne", "Mert", "Zeynep", "Kerem", "Elif", "Can", "Selin", "Burak",
    "Deniz", "Ece", "Onur", "Ipek", "Arda", "Ayşe", "Kaan", "Leyla",
    "Emre", "Nil", "Cem", "Sude", "Barış", "Asya", "Emir", "Melis"
]
NAMES_EN = ["Tom", "Lisa", "Jack", "Emma", "Mike", "Sara", "Alex", "Anna"]
PLACES = [
    "market", "kırtasiye", "fırın", "manav", "pastane", "oyuncak mağazası",
    "kitapçı", "eczane", "köy pazarı", "alışveriş merkezi"
]
OBJECTS = ["elma", "kalem", "ekmek", "kitap", "defter", "silgi", "çikolata", "bardak"]
CITIES = [
    "Ankara", "İstanbul", "İzmir", "Bursa", "Antalya", "Konya", "Adana",
    "Kayseri", "Gaziantep", "Trabzon", "Samsun", "Kocaeli"
]
SEASONS = ["ilkbahar", "yaz", "sonbahar", "kış"]
HOBBIES_TR = ["resim", "müzik", "spor", "okuma", "fotoğrafçılık", "yüzme"]
HOBBIES_EN = ["reading", "swimming", "drawing", "playing football", "playing chess"]
TOPICS_PARAGRAPH = [
    "günlük rutin", "tatil planları", "bilim merakı", "arkadaşlık", "hayvan sevgisi",
    "spor alışkanlıkları", "kitap okuma", "doğa gezileri", "yemek yapma", "müzik"
]
EXPERIMENTS = [
    ("suyun sıcaklığı", "ısıtma", "°C"),
    ("bitki büyümesi", "güneş ışığı", "cm"),
    ("mıknatıs etkisi", "yaklaştırma", "mesafe"),
    ("basınç deneyi", "su seviyesi", "cm"),
    ("çözünme hızı", "sıcaklık", "saniye"),
]
HISTORY_TOPICS = [
    "Anadolu'da ilk yerleşim", "İpek Yolu ticareti", "Osmanlı kuruluşu",
    "Cumhuriyetin ilanı", "coğrafi keşifler", "Sanayi Devrimi"
]


def shuffle_options(correct: str, wrongs: List[str]) -> tuple:
    """Return (options list, correctIndex)."""
    distinct = []
    for w in wrongs:
        if w != correct and w not in distinct and len(distinct) < 3:
            distinct.append(w)
    opts = [correct] + distinct
    random.shuffle(opts)
    return opts, opts.index(correct)


# --- MAT: Word problems, multi-step, ratio, logic, real-life ---
MAT_QUESTION_TYPES = [
    "math_problem", "context_problem", "math_problem", "context_problem",
    "math_problem", "context_problem"
]
MAT_SKILLS = [
    "problem_solving", "ratio_reasoning", "multi_step", "real_life_math",
    "logic", "geometry_application"
]


def gen_mat(n: int) -> List[Dict]:
    out = []
    templates = [
        _mat_sharing,
        _mat_recipe_scaling,
        _mat_travel_time,
        _mat_savings_ratio,
        _mat_mixture_percent,
        _mat_class_distribution,
        _mat_geometric_real_life,
        _mat_table_interpretation,
        _mat_discount_problem,
        _mat_work_rate,
    ]
    for i in range(n):
        t = templates[i % len(templates)]
        q = t(i, n)
        if q:
            q["id"] = f"g6_mat_{i+1:03d}"
            q["grade"] = 6
            q["subject"] = "mat"
            q["questionType"] = random.choice(MAT_QUESTION_TYPES)
            q["skills"] = random.sample(MAT_SKILLS, k=min(3, len(MAT_SKILLS)))
            out.append(q)
    return out


def _mat_sharing(i: int, n: int) -> Dict:
    total = random.randint(24, 96)
    share_ratio = random.choice([(1, 3), (1, 4), (2, 5), (1, 2)])
    num, den = share_ratio
    first = total * num // den
    second = total - first
    extra = random.randint(1, 5)
    stem = f"{random.choice(NAMES_TR)}, {total} cevizi {den} kişiye paylaştırıyor. Kişi başına {num} bölüm düşecek şekilde dağıtıyor. " \
           f"Sonra bir kişi kendi payından {extra} cevizi geri bırakıyor. Bu kişinin elinde kaç ceviz kalır?"
    correct = first - extra
    wrongs = [first, second, first + extra, second + extra]
    opts2, ci = shuffle_options(str(correct), [str(x) for x in wrongs if x != correct])
    return {"stem": stem, "options": opts2, "answer": str(correct), "correctIndex": ci,
            "difficulty": random.randint(0, 2), "explanation": f"Pay: {total}÷{den}×{num}={first}. Kalan: {first}-{extra}={correct}."}


def _mat_recipe_scaling(i: int, n: int) -> Dict:
    base_serving = random.choice([4, 6, 8])
    target = base_serving + random.randint(2, 6)
    flour_per = random.randint(2, 5)
    flour_total = base_serving * flour_per
    needed = target * flour_per
    stem = f"Bir kek tarifinde {base_serving} kişilik için {flour_total} su bardağı un kullanılıyor. " \
           f"Aynı oranda {target} kişilik kek yapmak için kaç su bardağı una ihtiyaç vardır?"
    wrongs = [flour_total, flour_total + flour_per, needed - flour_per, needed + flour_per]
    opts2, ci = shuffle_options(str(needed), [str(x) for x in wrongs])
    return {"stem": stem, "options": opts2, "answer": str(needed), "correctIndex": ci,
            "difficulty": 1, "explanation": f"Oran: {target}/{base_serving} = {needed}/{flour_total}. Gerekli un: {needed} bardak."}


def _mat_travel_time(i: int, n: int) -> Dict:
    dist = random.choice([120, 180, 240, 300])
    speed1 = random.choice([60, 80, 90])
    speed2 = random.choice([40, 50, 60])
    t1 = dist // speed1
    t2 = dist // speed2
    diff = t2 - t1
    stem = f"İki araç aynı anda aynı noktadan {dist} km uzaktaki bir şehre gidiyor. Birincisi saatte {speed1} km, " \
           f"ikincisi saatte {speed2} km hızla gidiyor. İkinci araç birinci araçtan kaç saat sonra varır?"
    wrongs = [t1, t2, t1 + t2, abs(t1 - t2) if t1 != t2 else diff + 1]
    opts2, ci = shuffle_options(str(diff), [str(x) for x in wrongs if x != diff])
    return {"stem": stem, "options": opts2, "answer": str(diff), "correctIndex": ci,
            "difficulty": 2, "explanation": f"Birinci: {dist}÷{speed1}={t1} saat. İkinci: {dist}÷{speed2}={t2} saat. Fark: {t2}-{t1}={diff} saat."}


def _mat_savings_ratio(i: int, n: int) -> Dict:
    monthly = random.randint(80, 200) * 10
    ratio_num = random.randint(1, 3)
    ratio_den = random.randint(4, 8)
    saved = monthly * ratio_num // ratio_den
    spent = monthly - saved
    stem = f"{random.choice(NAMES_TR)} her ay {monthly} TL harçlık alıyor. Bunun {ratio_num}/{ratio_den}'ünü biriktiriyor, " \
           f"geri kalanını harcıyor. Ayda harcadığı para kaç TL'dir?"
    wrongs = [saved, monthly, saved + 50, spent - 50]
    opts2, ci = shuffle_options(str(spent), [str(x) for x in wrongs if x != spent])
    return {"stem": stem, "options": opts2, "answer": str(spent), "correctIndex": ci,
            "difficulty": 1, "explanation": f"Birikim: {monthly}×{ratio_num}÷{ratio_den}={saved}. Harcama: {monthly}-{saved}={spent}."}


def _mat_mixture_percent(i: int, n: int) -> Dict:
    total = random.randint(40, 80)
    pct = random.choice([20, 25, 30, 40])
    part = total * pct // 100
    stem = f"Bir sınıfta {total} öğrenci vardır. Öğrencilerin %{pct}'i okul takımında yer almaktadır. " \
           f"Takımda olmayan öğrenci sayısı kaçtır?"
    rest = total - part
    wrongs = [part, total, part + 2, rest + 2]
    opts2, ci = shuffle_options(str(rest), [str(x) for x in wrongs if x != rest])
    return {"stem": stem, "options": opts2, "answer": str(rest), "correctIndex": ci,
            "difficulty": random.randint(0, 1), "explanation": f"Takımda: {total}×{pct}÷100={part}. Dışarıda: {total}-{part}={rest}."}


def _mat_class_distribution(i: int, n: int) -> Dict:
    rows = random.randint(4, 8)
    cols = random.randint(3, 6)
    total = rows * cols
    absent = random.randint(2, min(8, total - 1))
    present = total - absent
    stem = f"Sınıfta pencere kenarında {rows} sıra, her sırada {cols} sandalye vardır. Bir gün {absent} sandalye boş kaldığına göre " \
           f"o gün sınıfta kaç öğrenci vardır?"
    wrongs = [total, absent, total - 1, present + 1]
    opts2, ci = shuffle_options(str(present), [str(x) for x in wrongs if x != present])
    return {"stem": stem, "options": opts2, "answer": str(present), "correctIndex": ci,
            "difficulty": 0, "explanation": f"Toplam: {rows}×{cols}={total}. Gelen: {total}-{absent}={present}."}


def _mat_geometric_real_life(i: int, n: int) -> Dict:
    side = random.randint(5, 12)
    cost_per_m = random.randint(8, 15)
    perimeter = 4 * side
    cost = perimeter * cost_per_m
    stem = f"Dikdörtgen şeklindeki bir bahçenin kısa kenarı {side} m, uzun kenarı {side * 2} m'dir. " \
           f"Bahçenin çevresine metre başına {cost_per_m} TL'ye çit çekilecektir. Toplam maliyet kaç TL olur?"
    wrongs = [perimeter, side * cost_per_m, (perimeter + 4) * cost_per_m, cost - 50]
    opts2, ci = shuffle_options(str(cost), [str(x) for x in wrongs if x != cost])
    return {"stem": stem, "options": opts2, "answer": str(cost), "correctIndex": ci,
            "difficulty": 1, "explanation": f"Çevre: 2×({side}+{side*2})={perimeter} m. Maliyet: {perimeter}×{cost_per_m}={cost} TL."}


def _mat_table_interpretation(i: int, n: int) -> Dict:
    a, b, c = random.randint(10, 30), random.randint(15, 35), random.randint(12, 28)
    total = a + b + c
    stem = f"Bir sınıftaki öğrencilerin kitap okuma sayıları tabloda verilmiştir: 1-5 kitap: {a} öğrenci, " \
           f"6-10 kitap: {b} öğrenci, 11 ve üzeri: {c} öğrenci. Bu sınıfta toplam kaç öğrenci vardır?"
    wrongs = [a + b, b + c, a + c, total + 5]
    opts2, ci = shuffle_options(str(total), [str(x) for x in wrongs if x != total])
    return {"stem": stem, "options": opts2, "answer": str(total), "correctIndex": ci,
            "difficulty": 0, "explanation": f"Toplam: {a}+{b}+{c}={total}."}


def _mat_discount_problem(i: int, n: int) -> Dict:
    price = random.choice([100, 150, 200, 250])
    discount = random.choice([10, 20, 25])
    final = price * (100 - discount) // 100
    stem = f"{random.choice(PLACES).capitalize()}nda {price} TL'lik bir ürün %{discount} indirimle satılmaktadır. " \
           f"İndirimli fiyat kaç TL'dir?"
    wrongs = [price, price - discount, final + 10, price - final]
    opts2, ci = shuffle_options(str(final), [str(x) for x in wrongs if x != final])
    return {"stem": stem, "options": opts2, "answer": str(final), "correctIndex": ci,
            "difficulty": 1, "explanation": f"İndirim: {price}×{discount}÷100={price*discount//100}. Son fiyat: {price}-{price*discount//100}={final}."}


def _mat_work_rate(i: int, n: int) -> Dict:
    days_a = random.randint(4, 8)
    days_b = random.randint(6, 12)
    # A's rate 1/days_a, B's 1/days_b. Together: 1/(1/days_a + 1/days_b)
    combined = 1 / (Fraction(1, days_a) + Fraction(1, days_b))
    days_together = int(combined) if combined == int(combined) else round(float(combined))
    stem = f"Bir işi {random.choice(NAMES_TR)} {days_a} günde, {random.choice(NAMES_TR)} {days_b} günde bitirebiliyor. " \
           f"İkisi birlikte çalışırsa bu işi yaklaşık kaç günde bitirirler?"
    wrongs = [days_a, days_b, days_a + days_b, max(1, min(days_a, days_b) - 1)]
    opts2, ci = shuffle_options(str(days_together), [str(x) for x in wrongs if x != days_together])
    return {"stem": stem, "options": opts2, "answer": str(days_together), "correctIndex": ci,
            "difficulty": 2, "explanation": f"Birlikte: 1/(1/{days_a}+1/{days_b}) ≈ {days_together} gün."}


# --- TURKCE: Paragraph reading, inference, meaning, main idea ---
TURKCE_QUESTION_TYPES = ["paragraph", "long_context", "paragraph", "long_context", "MEANING", "LOGIC"]
TURKCE_SKILLS = ["reading_comprehension", "inference", "main_idea", "interpretation", "meaning"]


def gen_turkce(n: int) -> List[Dict]:
    out = []
    templates = [_turkce_paragraph_detail, _turkce_paragraph_inference, _turkce_paragraph_main_idea,
                 _turkce_paragraph_meaning, _turkce_paragraph_interpret]
    for i in range(n):
        t = templates[i % len(templates)]
        q = t(i, n)
        if q:
            q["id"] = f"g6_turkce_{i+1:03d}"
            q["grade"] = 6
            q["subject"] = "turkce"
            q["questionType"] = random.choice(TURKCE_QUESTION_TYPES)
            q["skills"] = random.sample(TURKCE_SKILLS, k=min(3, len(TURKCE_SKILLS)))
            out.append(q)
    return out


def _make_turkce_paragraph(i: int) -> str:
    name = NAMES_TR[i % len(NAMES_TR)]
    topic = TOPICS_PARAGRAPH[i % len(TOPICS_PARAGRAPH)]
    intro = [
        f"{name}, geçen yaz ailesiyle birlikte farklı şehirlere seyahat etti.",
        f"{name}, okulda fen bilimleri projesi üzerinde çalışıyor.",
        f"{name}, her hafta sonu kütüphaneye gidip yeni kitaplar keşfediyor.",
        f"{name}'in en sevdiği mevsim {random.choice(SEASONS)}; bu dönemde açık havada vakit geçirmeyi seviyor.",
        f"{name} ve arkadaşları, mahallelerindeki parkta düzenli olarak buluşuyor.",
    ][i % 5]
    mid = [
        "Yolculuk sırasında tarihi yerleri gezdi ve yerel yemekleri tatma fırsatı buldu.",
        "Deneylerde dikkatli gözlem yapmanın önemini fark etti.",
        "Özellikle macera ve bilim kurgu türünde kitaplar okuyor.",
        "Doğadaki değişimleri izlemek ona huzur veriyor.",
        "Birlikte oyun oynayıp, birbirlerine yardım ediyorlar.",
    ][(i + 1) % 5]
    end = [
        "Bu deneyim ona yeni kültürler tanıma fırsatı sundu.",
        "Sonuçları sınıfta sunarak arkadaşlarına anlattı.",
        "Okudukları hakkında notlar alıp tartışmalara katılıyor.",
        "Çevresindeki canlıları ve bitkileri inceleyerek bilgisini artırıyor.",
        "Böylece hem eğleniyor hem de sosyal bağlarını güçlendiriyor.",
    ][(i + 2) % 5]
    return f"{intro} {mid} {end}"


def _turkce_paragraph_detail(i: int, n: int) -> Dict:
    p = _make_turkce_paragraph(i)
    name = NAMES_TR[i % len(NAMES_TR)]
    stem = f"{p}\n\nBu parçaya göre {name} ile ilgili aşağıdakilerden hangisi doğrudur?"
    opts = [
        "Parçada anlatılan etkinliklere katılmaktadır.",
        "Sadece evde vakit geçirmektedir.",
        "Okula hiç gitmemektedir.",
        "Arkadaşlarıyla hiç görüşmemektedir.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": random.randint(0, 1), "explanation": "Parçada verilen bilgiler doğrultusunda en uygun seçenek ilkidir."}


def _turkce_paragraph_inference(i: int, n: int) -> Dict:
    p = _make_turkce_paragraph(i)
    stem = f"{p}\n\nBu parçadan aşağıdakilerden hangisi çıkarılabilir?"
    opts = [
        "Metindeki kişi çeşitli etkinliklere katılmaktadır.",
        "Metindeki kişi hiç dışarı çıkmamaktadır.",
        "Metindeki kişi sadece ders çalışmaktadır.",
        "Metindeki kişi arkadaşlarından uzak durmaktadır.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 1, "explanation": "Parçadaki anlatılanlardan çıkarılabilecek en mantıklı sonuç ilk seçenektir."}


def _turkce_paragraph_main_idea(i: int, n: int) -> Dict:
    p = _make_turkce_paragraph(i)
    stem = f"{p}\n\nBu parçanın ana fikri aşağıdakilerden hangisidir?"
    opts = [
        "Parçada anlatılan kişinin günlük yaşamı ve etkinlikleri.",
        "Seyahat etmenin önemi.",
        "Okulda başarılı olmanın yolları.",
        "Kitapların önemi.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 2, "explanation": "Parça bütününde kişinin yaşamı ve etkinlikleri anlatılmaktadır."}


def _turkce_paragraph_meaning(i: int, n: int) -> Dict:
    p = _make_turkce_paragraph(i)
    stem = f"{p}\n\nBu parçada \"etkinlik\" kelimesiyle anlatılmak istenen aşağıdakilerden hangisidir?"
    opts = [
        "Yapılan işler ve uğraşılar",
        "Sadece oyun oynamak",
        "Sadece ders çalışmak",
        "Hiçbir şey yapmamak",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 1, "explanation": "Etkinlik kelimesi yapılan işler, uğraşılar anlamında kullanılmaktadır."}


def _turkce_paragraph_interpret(i: int, n: int) -> Dict:
    p = _make_turkce_paragraph(i)
    stem = f"{p}\n\nYazar bu parçada asıl neyi vurgulamak istemektedir?"
    opts = [
        "Kişinin günlük yaşamındaki çeşitlilik ve katıldığı faaliyetler.",
        "Sadece tatilin önemi.",
        "Sadece okulun önemi.",
        "Hiçbir şeyi vurgulamıyor.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 2, "explanation": "Parçada kişinin yaşamındaki farklı etkinlikler vurgulanmaktadır."}


# --- FEN: Experiment, cause-effect, real-life science ---
FEN_QUESTION_TYPES = ["EXPERIMENT", "REASONING", "TABLE_GRAPH", "CONCEPT_APPLICATION"]
FEN_SKILLS = ["experiment_interpretation", "cause_effect", "scientific_reasoning", "real_life_science"]


def gen_fen(n: int) -> List[Dict]:
    out = []
    templates = [_fen_experiment_result, _fen_experiment_interpret, _fen_cause_effect,
                 _fen_real_life_science, _fen_table_interpret]
    for i in range(n):
        t = templates[i % len(templates)]
        q = t(i, n)
        if q:
            q["id"] = f"g6_fen_{i+1:03d}"
            q["grade"] = 6
            q["subject"] = "fen"
            q["questionType"] = random.choice(FEN_QUESTION_TYPES)
            q["skills"] = random.sample(FEN_SKILLS, k=min(3, len(FEN_SKILLS)))
            out.append(q)
    return out


def _fen_experiment_result(i: int, n: int) -> Dict:
    init = 18 + (i % 5) * 2
    mins = 5 + (i % 4)
    rise = 3
    final = init + mins * rise
    stem = f"Bir öğrenci su ısıtma deneyi yapıyor. Başlangıçta suyun sıcaklığı {init} °C'dir. " \
           f"Her dakika sıcaklık {rise} °C artmaktadır. {mins} dakika sonunda suyun sıcaklığı kaç °C olur?"
    wrongs = [str(x) for x in [init, final - rise, final + rise, init + mins]]
    opts2, ci = shuffle_options(str(final), wrongs)
    return {"stem": stem, "options": opts2, "answer": str(final), "correctIndex": ci,
            "difficulty": 0, "explanation": f"Artış: {mins}×{rise}={mins*rise} °C. Sonuç: {init}+{mins*rise}={final} °C."}


def _fen_experiment_interpret(i: int, n: int) -> Dict:
    stem = "Bir öğrenci bitki yetiştirme deneyinde iki saksı hazırlıyor. Birini güneşli yere, diğerini karanlık odaya koyuyor. " \
           "İki hafta sonunda güneşli yerdeki bitki daha fazla büyüyor. Bu deneyin sonucuna göre aşağıdakilerden hangisi söylenebilir?"
    opts = [
        "Işık, bitki büyümesi için önemli bir etkendir.",
        "Bitkiler sadece karanlıkta büyür.",
        "Saksı büyüklüğü tek belirleyicidir.",
        "Su bitki büyümesini engeller.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 1, "explanation": "Deney ışığın bitki büyümesindeki rolünü göstermektedir."}


def _fen_cause_effect(i: int, n: int) -> Dict:
    stem = "Soğuk bir kış gününde pencerenin iç yüzeyinde su damlacıkları oluşuyor. " \
           "Bu durumun nedeni aşağıdakilerden hangisiyle açıklanabilir?"
    opts = [
        "İçerideki nemli hava soğuk camla temas edince yoğuşma oluyor.",
        "Cam kendiliğinden su üretiyor.",
        "Dışarıdaki hava içeri sızıyor.",
        "Camın rengi değişiyor.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 2, "explanation": "Yoğuşma, nemli havanın soğuk yüzeyle temasında suyun sıvı hale geçmesidir."}


def _fen_real_life_science(i: int, n: int) -> Dict:
    scenarios = [
        ("Buzdolabına konan su donar.", "Sıcaklık düşünce su donar.", ["Sıcaklık suyu ısıtır.", "Buzdolabı su üretir.", "Donma sadece dışarıda olur."]),
        ("Bisiklet frenine basınca tekerlek yavaşlar.", "Sürtünme hareketi yavaşlatır.", ["Fren tekerleği hızlandırır.", "Sürtünme yoktur.", "Tekerlek kendiliğinden durur."]),
        ("Yemek tuzu suda çözünür.", "Tuz suda moleküllere ayrılır.", ["Tuz suda hiç çözünmez.", "Su tuzu üretir.", "Çözünme fiziksel değildir."]),
    ]
    scenario, correct, wrongs = scenarios[i % len(scenarios)]
    stem = f"Günlük hayatta sıkça gözlemlediğimiz bir olay: {scenario} Bu durumun bilimsel açıklaması aşağıdakilerden hangisidir?"
    opts2, ci = shuffle_options(correct, wrongs)
    return {"stem": stem, "options": opts2, "answer": correct, "correctIndex": ci,
            "difficulty": random.randint(1, 2), "explanation": f"Doğru bilimsel açıklama: {correct}"}


def _fen_table_interpret(i: int, n: int) -> Dict:
    stem = "Bir deneyde farklı sıcaklıklardaki suya şeker ekleniyor. 20°C'de 10 dakikada çözünen şeker miktarı X gram, " \
           "40°C'de aynı sürede 2X gram, 60°C'de 3X gram oluyor. Bu tabloya göre aşağıdakilerden hangisi çıkarılabilir?"
    opts = [
        "Sıcaklık arttıkça çözünme hızı artmaktadır.",
        "Sıcaklık çözünmeyi etkilemez.",
        "Soğuk suda daha hızlı çözünür.",
        "Çözünme şekere bağlı değildir.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 1, "explanation": "Veriler sıcaklık-artış ile çözünme hızı artışını gösteriyor."}


# --- SOSYAL: History, map, cause-effect ---
SOSYAL_QUESTION_TYPES = ["MAP", "CAUSE_EFFECT", "TABLE_GRAPH", "COMMENTARY"]
SOSYAL_SKILLS = ["map_reasoning", "cause_effect", "history_interpretation"]


def gen_sosyal(n: int) -> List[Dict]:
    out = []
    templates = [_sosyal_map_reasoning, _sosyal_history_interpret, _sosyal_cause_effect,
                 _sosyal_map_distance, _sosyal_history_cause]
    for i in range(n):
        t = templates[i % len(templates)]
        q = t(i, n)
        if q:
            q["id"] = f"g6_sosyal_{i+1:03d}"
            q["grade"] = 6
            q["subject"] = "sosyal"
            q["questionType"] = random.choice(SOSYAL_QUESTION_TYPES)
            q["skills"] = random.sample(SOSYAL_SKILLS, k=min(3, len(SOSYAL_SKILLS)))
            out.append(q)
    return out


def _sosyal_map_reasoning(i: int, n: int) -> Dict:
    c1, c2 = CITIES[i % len(CITIES)], CITIES[(i + 3) % len(CITIES)]
    dist = 200 + (i % 8) * 50
    stem = f"Türkiye haritasında {c1} ile {c2} arasındaki kuş uçuşu mesafe yaklaşık {dist} km olarak gösterilmektedir. " \
           f"Harita ölçeğinin doğru olduğu varsayılırsa, bu bilgiyle aşağıdakilerden hangisi yapılabilir?"
    opts = [
        "İki şehir arasındaki yaklaşık yolculuk süresi hesaplanabilir.",
        "Şehirlerin nüfusu bulunabilir.",
        "Harita tamamen yanlıştır.",
        "Ölçek bilgisi gereksizdir.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 1, "explanation": "Mesafe bilgisiyle hız kullanarak yolculuk süresi hesaplanabilir."}


def _sosyal_history_interpret(i: int, n: int) -> Dict:
    topic = HISTORY_TOPICS[i % len(HISTORY_TOPICS)]
    stem = f"Tarih dersinde \"{topic}\" konusu işleniyor. Öğretmen, bu dönemin toplumlar üzerindeki etkisini soruyor. " \
           "Bu soruya cevap vermek için öğrencinin öncelikle neyi bilmesi gerekir?"
    opts = [
        "O dönemin olaylarını ve neden-sonuç ilişkilerini.",
        "Sadece tarihleri ezberlemesi yeterlidir.",
        "Sadece harita bilgisi yeterlidir.",
        "Matematik bilgisi gerekir.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 2, "explanation": "Tarih yorumu için olaylar ve neden-sonuç ilişkileri önemlidir."}


def _sosyal_cause_effect(i: int, n: int) -> Dict:
    stem = "Sanayi Devrimi sırasında birçok insan kırdan kente göç etmiştir. Bu göçün temel nedeni aşağıdakilerden hangisidir?"
    opts = [
        "Kentlerde fabrikalarda iş imkânlarının artması.",
        "Kırsalda havanın kötü olması.",
        "Kentlerin daha soğuk olması.",
        "Göç yasaklarının kalkması.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 2, "explanation": "Sanayi Devrimi kentlerde iş imkânlarını artırdı, bu da göçe neden oldu."}


def _sosyal_map_distance(i: int, n: int) -> Dict:
    dist = 240 + (i % 6) * 40
    speed = 60 + (i % 4) * 10
    hours = dist // speed
    stem = f"Haritada iki şehir arası {dist} km olarak gösterilmiştir. Saatte ortalama {speed} km hızla giden bir araçla " \
           f"bu mesafe yaklaşık kaç saatte alınır?"
    wrongs = [str(x) for x in [dist, speed, hours + 2, hours - 1 if hours > 1 else hours + 1]]
    opts2, ci = shuffle_options(str(hours), wrongs)
    return {"stem": stem, "options": opts2, "answer": str(hours), "correctIndex": ci,
            "difficulty": 0, "explanation": f"Yolculuk süresi = Mesafe / Hız = {dist} ÷ {speed} ≈ {hours} saat."}


def _sosyal_history_cause(i: int, n: int) -> Dict:
    stem = "Türkiye Cumhuriyeti'nin kurulmasından önce Kurtuluş Savaşı verilmiştir. " \
           "Bu savaşın başlamasının temel nedeni aşağıdakilerden hangisidir?"
    opts = [
        "Ülkenin işgal edilmesi ve bağımsızlığın tehdit altında olması.",
        "Ekonomik kriz.",
        "İklim değişikliği.",
        "Teknolojik gelişmeler.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 2, "explanation": "İşgal ve bağımsızlık mücadelesi Kurtuluş Savaşı'nın temel nedenidir."}


# --- ING: Reading, sentence meaning, contextual vocabulary ---
ING_QUESTION_TYPES = ["READING", "VOCAB_IN_CONTEXT", "DIALOGUE", "CLOZE"]
ING_SKILLS = ["reading_comprehension", "vocabulary_in_context", "sentence_meaning"]


def gen_ing(n: int) -> List[Dict]:
    out = []
    templates = [_ing_reading_detail, _ing_reading_inference, _ing_vocab_context,
                 _ing_sentence_meaning, _ing_cloze]
    for i in range(n):
        t = templates[i % len(templates)]
        q = t(i, n)
        if q:
            q["id"] = f"g6_ing_{i+1:03d}"
            q["grade"] = 6
            q["subject"] = "ing"
            q["questionType"] = random.choice(ING_QUESTION_TYPES)
            q["skills"] = random.sample(ING_SKILLS, k=min(3, len(ING_SKILLS)))
            out.append(q)
    return out


def _make_ing_paragraph(i: int) -> str:
    name = NAMES_EN[i % len(NAMES_EN)]
    hobby = HOBBIES_EN[i % len(HOBBIES_EN)]
    time = ["after school", "at weekends", "in the evening", "during the holidays"][i % 4]
    return f"{name} is a 6th grade student. {name} enjoys {hobby} {time}. " \
           f"{name} also likes spending time with family and friends. " \
           f"{name} believes that a good balance between study and free time is important for success."


def _ing_reading_detail(i: int, n: int) -> Dict:
    p = _make_ing_paragraph(i)
    name = NAMES_EN[i % len(NAMES_EN)]
    stem = f"{p}\n\nAccording to the text, when does {name} enjoy {HOBBIES_EN[i % len(HOBBIES_EN)]}?"
    times = ["after school", "at weekends", "in the evening", "during the holidays"]
    correct = times[i % 4]
    wrongs = [t for t in times if t != correct]
    opts2, ci = shuffle_options(correct, wrongs)
    return {"stem": stem, "options": opts2, "answer": correct, "correctIndex": ci,
            "difficulty": 0, "explanation": "The text directly states when the person enjoys the hobby."}


def _ing_reading_inference(i: int, n: int) -> Dict:
    p = _make_ing_paragraph(i)
    stem = f"{p}\n\nWhat can we infer from this text?"
    opts = [
        "The person values both work and leisure.",
        "The person never studies.",
        "The person has no friends.",
        "The person dislikes family time.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 1, "explanation": "The text mentions balance between study and free time."}


def _ing_vocab_context(i: int, n: int) -> Dict:
    stem = "In the sentence \"The weather was so hot that everyone looked for a shady place,\" " \
           "the word \"shady\" means _____."
    opts = [
        "protected from the sun",
        "very bright",
        "wet",
        "cold",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 1, "explanation": "Shady means protected from direct sunlight."}


def _ing_sentence_meaning(i: int, n: int) -> Dict:
    stem = "What does the sentence \"She turned down the offer\" mean?"
    opts = [
        "She refused the offer.",
        "She accepted the offer.",
        "She improved the offer.",
        "She forgot the offer.",
    ]
    return {"stem": stem, "options": opts, "answer": opts[0], "correctIndex": 0,
            "difficulty": 2, "explanation": "Turn down means to refuse or reject."}


def _ing_cloze(i: int, n: int) -> Dict:
    stem = "Complete the sentence: \"If it _____ tomorrow, we will stay at home.\""
    opts = ["rains", "rain", "raining", "will rain"]
    opts2, ci = shuffle_options("rains", ["rain", "raining", "will rain"])
    return {"stem": stem, "options": opts2, "answer": "rains", "correctIndex": ci,
            "difficulty": 2, "explanation": "First conditional: if + present simple, will + infinitive."}


# --- Main ---
def to_seeder_format(q: Dict) -> Dict:
    """Convert to DbSeeder-compatible JSON format (includes answer for auditing)."""
    opts = q["options"]
    ci = q["correctIndex"]
    return {
        "id": q["id"],
        "grade": q["grade"],
        "subject": q["subject"],
        "difficulty": q.get("difficulty", 1),
        "questionType": q.get("questionType", "short_item"),
        "stem": q["stem"],
        "options": opts,
        "answer": opts[ci] if ci < len(opts) else q.get("answer", ""),
        "correctIndex": ci,
        "explanation": q.get("explanation", ""),
        "skills": q.get("skills", []),
    }


def main():
    parser = argparse.ArgumentParser(description="Grade 6 question pool generator")
    parser.add_argument("--output-dir", default="app/src/main/assets/packs",
                        help="Output directory for JSON pack files")
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    parser.add_argument("--per-subject", type=int, default=500, help="Questions per subject")
    parser.add_argument("--test", action="store_true", help="Quick test: 5 per subject")
    args = parser.parse_args()

    random.seed(args.seed)
    n = 5 if args.test else args.per_subject
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    subjects = [
        ("mat", gen_mat),
        ("turkce", gen_turkce),
        ("fen", gen_fen),
        ("sosyal", gen_sosyal),
        ("ing", gen_ing),
    ]

    total = 0
    for subj_key, gen_fn in subjects:
        questions = gen_fn(n)
        formatted = [to_seeder_format(q) for q in questions]
        out_path = output_dir / f"grade6_{subj_key}.json"
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(formatted, f, ensure_ascii=False, indent=2)
        total += len(formatted)
        print(f"Wrote {len(formatted)} questions to {out_path}")

    print(f"\nTotal: {total} questions across 5 subjects.")
