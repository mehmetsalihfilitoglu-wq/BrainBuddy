#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""500 grade-6 mat questions — single assets/grade6_mat.json (PACK_FILE_REGEX name)."""
from __future__ import annotations

import json
import os
import random


OUT_FILE = os.path.join(
    os.path.dirname(__file__), "..", "app", "src", "main", "assets", "grade6_mat.json"
)


def build_pairs() -> list[tuple[int, int]]:
    pairs: list[tuple[int, int]] = []
    pairs += [(1, 1)] * 38 + [(1, 2)] * 90
    pairs += [(2, 1)] * 34 + [(2, 2)] * 78
    pairs += [(3, 1)] * 20 + [(3, 2)] * 48
    pairs += [(4, 1)] * 34 + [(4, 2)] * 78
    pairs += [(5, 1)] * 24 + [(5, 2)] * 56
    assert len(pairs) == 500
    rng = random.Random(20260321)
    rng.shuffle(pairs)
    return pairs


def sent(*parts: str) -> str:
    return " ".join(p.strip() for p in parts if p).strip()


def opt4(a: str, b: str, c: str, d: str, correct: int) -> tuple[list[str], int]:
    assert 0 <= correct <= 3
    return [a, b, c, d], correct


def shuffle_options(opts: list[str], correct_index: int, seed: int) -> tuple[list[str], int]:
    rng = random.Random(seed)
    pair = list(enumerate(opts))
    rng.shuffle(pair)
    new_opts = [opts[i] for i, _ in pair]
    new_ci = next(j for j, (old_i, _) in enumerate(pair) if old_i == correct_index)
    return new_opts, new_ci


def shuffle_unique_strings(correct: str, pool: list[str], seed: int) -> tuple[list[str], int]:
    """correct + distractors; dedupe strings, pad with shifted numerics if needed."""
    out: list[str] = []
    for p in [correct] + pool:
        if p not in out:
            out.append(p)
    k = 0
    while len(out) < 4:
        k += 1
        try:
            b = float(str(correct).replace(",", "."))
            cand = str(int(round(b)) + k)
        except ValueError:
            cand = f"{k}"
        if cand not in out:
            out.append(cand)
    assert len(set(out[:4])) == 4
    return shuffle_options(out[:4], 0, seed)


def tl_unique(ca: int, candidates: list[int], seed: int) -> tuple[list[str], int]:
    raw = [int(ca)] + [int(x) for x in candidates]
    vals: list[int] = []
    for v in raw:
        if v > 0 and v not in vals:
            vals.append(v)
    pad = 1
    ca_i = int(ca)
    while len(vals) < 4:
        t = ca_i + pad
        if t not in vals and t > 0:
            vals.append(t)
        pad += 1
    opts0 = [f"{v} TL" for v in vals[:4]]
    assert len(set(opts0)) == 4
    return shuffle_options(opts0, 0, seed)


def str_num_unique(ca: int, candidates: list[int], seed: int, suffix: str = "") -> tuple[list[str], int]:
    raw = [int(ca)] + [int(x) for x in candidates]
    vals: list[int] = []
    for v in raw:
        if v not in vals:
            vals.append(v)
    pad = 1
    ca_i = int(ca)
    while len(vals) < 4:
        t = ca_i + pad
        if t not in vals:
            vals.append(t)
        pad += 1
    opts0 = [f"{v}{suffix}" for v in vals[:4]]
    assert len(set(opts0)) == 4
    return shuffle_options(opts0, 0, seed)


# ---------- Topic 1: Numbers & Quantities ----------
def gen_t1(diff: int, vi: int) -> dict:
    r = vi
    if diff == 1:
        n = 100 + (r % 45) * 2
        p = 12 + (r % 11)
        inc = (n * p + 50) // 100
        ca = n + inc
        w_inc = inc
        w_sub = n - inc
        w_mispct = n + (n * p) // 1000 + (1 if (n * p) % 1000 >= 500 else 0)
        w_double = n + 2 * inc
        stem = sent(
            f"Bir kırtasiye ürününün raf fiyatı {n} TL olarak işaretlenmiştir.",
            f"Kasada bu ürüne yüzde {p} oranında zam uygulanıyor ve zam tutarı etiket fiyatı üzerinden hesaplanıyor.",
            f"Öğrenci kartı veya ek indirim olmadığı varsayılıyor; yalnızca zam dikkate alınacaktır.",
            f"Buna göre ödenecek yeni tutar kaç TL olur?",
        )
        opts, ci = tl_unique(ca, [w_inc, w_sub, w_mispct, w_double], 910000 + r)
        expl = f"Zam tutarı {n}×{p}/100={inc} TL; yeni tutar {n}+{inc}={ca} TL."
    else:
        base = 240 + (r % 18) * 5
        a1 = 20 + (r % 8)
        a2 = 15 + (r % 9)
        r1 = base - (base * a1 + 50) // 100
        final = r1 - (r1 * a2 + 50) // 100
        same_base_sum = base - (base * (a1 + a2) + 50) // 100
        two_on_base = base - (base * a1 + 50) // 100 - (base * a2 + 50) // 100
        step1_wrong = base - (base * a2 + 50) // 100
        wrong_order = step1_wrong - (step1_wrong * a1 + 50) // 100
        stem = sent(
            f"Bir teknoloji mağazasında kulaklığın etiket fiyatı {base} TL'dir.",
            f"Önce etiket fiyatına yüzde {a1} indirim uygulanıyor; ardından oluşan satış tutarı üzerinden yüzde {a2} ek indirim yapılıyor.",
            f"İkinci indirim oranı, ilk indirimden sonra kalan tutar üzerinden hesaplanır; toplam indirimi tek seferde yüzde ({a1}+{a2}) gibi düşünmek yaygın bir hatadır.",
            f"Buna göre son ödenecek tutar kaç TL olur?",
        )
        opts, ci = tl_unique(final, [same_base_sum, two_on_base, wrong_order], 920000 + r)
        expl = (
            f"İlk indirim sonrası: {base}-{base}×{a1}/100={r1} TL. İkinci indirim {r1} üzerinden: "
            f"{r1}-{r1}×{a2}/100={final} TL."
        )
    return {"stem": stem, "options": opts, "correctIndex": ci, "explanation": expl}


# ---------- Topic 2: Algebraic thinking ----------
def gen_t2(diff: int, vi: int) -> dict:
    r = vi
    if diff == 1:
        x = 4 + (r % 7)
        k = 7 + (r % 6)
        rhs = k * x - (2 * x + 7)
        d1 = x + 1
        d2 = (rhs + 7) // max(k - 2, 1)
        if d2 == x:
            d2 = x - 1
        d3 = rhs // max(k, 1)
        stem = sent(
            f"Bilinmeyen bir doğal sayı x ile gösterilsin.",
            f"{k} çarpı x ile (2x + 7) ifadesinin farkı {rhs} ise denklem {k}x - (2x + 7) = {rhs} biçiminde yazılabilir.",
            f"Parantez dağıtımı ve benzer terimleri toplama adımlarında işlem sırası karıştırılabilir.",
            f"Buna göre x kaçtır?",
        )
        opts, ci = str_num_unique(x, [d1, d2, d3], 930000 + r, "")
        expl = f"{k}x-2x-7={rhs} → ({k}-2)x={rhs+7} → x={x}."
    else:
        a = 4 + (r % 5)
        m = 2 + (r % 9)
        prod = a * (m + 3)
        lo, hi = 6, max(7, prod - 6)
        span = max(1, hi - lo + 1)
        c = lo + (r % span)
        c = min(c, prod - 6)
        b = prod - c
        if b < 6:
            b = 6
            c = prod - b
        trap_box_count = (c + b) // a
        trap_round = m + 1
        trap_off = m - 1 if m > 2 else m + 2
        stem = sent(
            f"Bir sınıfta her kutuda eşit sayıda cetvel bulunmaktadır.",
            f"Kutu sayısı (m + 3) ile gösteriliyor ve her kutuda {a} cetvel olduğu biliniyor.",
            f"Cetvellerin toplam sayısından {b} çıkarıldığında geriye {c} cetvel kalıyorsa denklem {a}(m + 3) - {b} = {c} kurulabilir.",
            f"Buna göre m kaçtır?",
        )
        opts, ci = str_num_unique(m, [trap_box_count, trap_round, trap_off], 940000 + r, "")
        expl = f"{a}(m+3)-{b}={c} → {a}(m+3)={c+b} → m+3={(c+b)//a} → m={m}."
    return {"stem": stem, "options": opts, "correctIndex": ci, "explanation": expl}


# ---------- Topic 3: Geometry — shapes ----------
def gen_t3(diff: int, vi: int) -> dict:
    r = vi
    if diff == 1:
        ang1 = 35 + (r % 25)
        ang2 = 45 + (r % 20)
        c_ang = 180 - ang1 - ang2
        stem = sent(
            f"Bir üçgende iki iç açı sırasıyla {ang1}° ve {ang2}° olarak ölçülmüştür.",
            f"Üçgenin üçüncü iç açısı, 180° ile bu iki ölçümün toplamının farkı olarak bulunur.",
            f"Kenar uzunlukları verilmediği için üçgenin türü tek başına sorulmuyor; yalnızca üçüncü açı isteniyor.",
            f"Buna göre üçüncü iç açı kaç derecedir?",
        )
        opts, ci = str_num_unique(
            c_ang,
            [ang1 + ang2, 180 - abs(ang1 - ang2), 360 - ang1 - ang2],
            950000 + r,
            "°",
        )
        expl = f"180°-({ang1}°+{ang2}°)={c_ang}°."
    else:
        n = 6 + (r % 5)
        interior = (n - 2) * 180
        stem = sent(
            f"Bir çokgenin kenar sayısı {n} olarak verilmiştir.",
            f"İç açılar toplamı, kenar sayısına bağlı olarak (n - 2) × 180° kuralı ile hesaplanır.",
            f"Bu kural, üçgende iç açılar toplamının 180° olması özel durumunun genellemesidir.",
            f"Buna göre iç açılar toplamı kaç derecedir?",
        )
        opts, ci = str_num_unique(interior, [n * 180, (n - 1) * 180, 360], 960000 + r, "°")
        expl = f"(n-2)×180° = ({n}-2)×180°={interior}°."
    return {"stem": stem, "options": opts, "correctIndex": ci, "explanation": expl}


# ---------- Topic 4: Geometric measurements ----------
def gen_t4(diff: int, vi: int) -> dict:
    r = vi
    if diff == 1:
        a = 7 + (r % 8)
        b = 9 + (r % 7)
        area = a * b
        peri = 2 * (a + b)
        stem = sent(
            f"Bir sınıf duvar gazetesinin görünen yüzü dikdörtgen biçimindedir.",
            f"Gazetenin bir kenarı {a} dm, diğer kenarı {b} dm olarak ölçülmüştür.",
            f"Alan ölçüsü, iki dik kenarın çarpımıdır; çevre ise kenarların toplamının iki katıdır.",
            f"Buna göre gazetenin alanı kaç dm²'dir?",
        )
        # parse-friendly: store as int then format
        opts, ci = shuffle_options(
            [
                f"{area} dm²",
                f"{peri} dm²",
                f"{a + b} dm²",
                f"{2 * a * b} dm²",
            ],
            0,
            970000 + r,
        )
        expl = f"Alan={a}×{b}={area} dm²."
    else:
        e = 5 + (r % 6)
        vol = e**3
        face = e * e
        sa_total = 6 * face
        d1 = sa_total if sa_total != vol else vol + 2
        d2 = face
        d3 = 12 * e
        stem = sent(
            f"Bir depo odasında kenarları eşit uzunlukta olan küp şeklinde bir kutu bulunmaktadır.",
            f"Kutunun bir kenarının uzunluğu {e} cm olarak verilmiştir.",
            f"Hacim, bir kenar uzunluğunun üçüncü kuvvetine eşittir; tek bir yüzün alanı kenarın karesidir.",
            f"Buna göre kutunun hacmi kaç cm³'tür?",
        )
        opts, ci = shuffle_options(
            [f"{vol} cm³", f"{d1} cm³", f"{d2} cm²", f"{d3} cm"],
            0,
            980000 + r,
        )
        expl = f"Hacim={e}³={vol} cm³."
    return {"stem": stem, "options": opts, "correctIndex": ci, "explanation": expl}


# ---------- Topic 5: Statistics ----------
def gen_t5(diff: int, vi: int) -> dict:
    r = vi
    if diff == 1:
        d0, d1, d2, d3, d4 = 11 + r % 6, 14 + r % 5, 17 + r % 4, 13 + r % 7, 16 + r % 8
        data = [d0, d1, d2, d3, d4]
        s = sum(data)
        avg = s / 5
        med = sorted(data)[2]
        rngv = max(data) - min(data)
        stem = sent(
            f"Bir okuma kulübünün beş günde okuduğu sayfa sayıları sırasıyla {d0}, {d1}, {d2}, {d3}, {d4} olarak kaydedilmiştir.",
            f"Aritmetik ortalama, tüm verilerin toplamının ölçüm sayısına bölünmesiyle bulunur.",
            f"Burada medyan veya açıklık değil, ortalama sayfa sayısı sorulmaktadır.",
            f"Buna göre günlük ortalama kaç sayfadır?",
        )
        ca = str(int(avg)) if avg == int(avg) else f"{avg:.1f}".replace(".0", "")
        opts, ci = shuffle_unique_strings(ca, [f"{med}", f"{rngv}", f"{s}", f"{s // 5}"], 990000 + r)
        expl = f"Toplam={s}, ortalama={s}/5={ca}."
    else:
        v = sorted([9 + r % 9, 11 + r % 8, 15 + r % 10, 18 + r % 7, 24 + r % 6])
        med = v[2]
        mean = sum(v) // 5
        stem = sent(
            f"Bir deneme sınavında beş öğrencinin netleri küçükten büyüğe sıralandığında {v[0]}, {v[1]}, {v[2]}, {v[3]}, {v[4]} olur.",
            f"Medyan, sıralı veri dizisinde tam ortadaki değerdir; veri sayısı tek olduğunda ortadaki tek sayı medyandır.",
            f"Ortalama ile medyan genelde farklıdır; burada istenen medyandır.",
            f"Buna göre medyan kaçtır?",
        )
        opts, ci = shuffle_unique_strings(
            f"{med}",
            [f"{mean}", f"{v[0]}", f"{v[4]}", f"{(v[0] + v[4]) // 2}"],
            991000 + r,
        )
        expl = f"Sıralı dizide 3. terim medyandır: {med}."
    return {"stem": stem, "options": opts, "correctIndex": ci, "explanation": expl}


def dispatch(topic: int, diff: int, vi: int) -> dict:
    if topic == 1:
        return gen_t1(diff, vi)
    if topic == 2:
        return gen_t2(diff, vi)
    if topic == 3:
        return gen_t3(diff, vi)
    if topic == 4:
        return gen_t4(diff, vi)
    return gen_t5(diff, vi)


def validate_answer_consistency(q: dict) -> None:
    opts = q["options"]
    ci = q["correctIndex"]
    assert opts[ci] and len(opts) == 4
    assert len(set(opts)) == 4, f"duplicate option: {opts}"


def main() -> None:
    pairs = build_pairs()
    counts: dict[tuple[int, int], int] = {}
    questions = []
    for i, (topic, diff) in enumerate(pairs):
        key = (topic, diff)
        vi = counts.get(key, 0)
        counts[key] = vi + 1
        qid = f"g6_mat_{i+1:04d}"
        body = dispatch(topic, diff, vi)
        q = {
            "id": qid,
            "grade": 6,
            "subject": "mat",
            "topicTheme": topic,
            "difficulty": diff,
            "stem": body["stem"],
            "options": body["options"],
            "correctIndex": body["correctIndex"],
            "explanation": body["explanation"],
        }
        validate_answer_consistency(q)
        questions.append(q)

    tcount = {1: 0, 2: 0, 3: 0, 4: 0, 5: 0}
    dcount = {1: 0, 2: 0}
    for q in questions:
        tcount[q["topicTheme"]] += 1
        dcount[q["difficulty"]] += 1
    assert tcount == {1: 128, 2: 112, 3: 68, 4: 112, 5: 80}
    assert dcount == {1: 150, 2: 350}

    os.makedirs(os.path.dirname(OUT_FILE), exist_ok=True)
    payload = {"questions": questions}
    with open(OUT_FILE, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print("OK", OUT_FILE)


if __name__ == "__main__":
    main()
