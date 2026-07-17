#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 1st Grade visual-first question banks for EDUmio.
Math, Turkish, Life Studies, English.
Target: 50%+ imageAsset, 30% visual-supported, 20% text-only.
10 packs × 10 questions = 100 per subject = 400 total.
"""
import json
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = ROOT / "app/src/main/assets"
OUT_DIRS = {
    "mat": ASSETS_DIR / "lgs_import/mat1",
    "turkce": ASSETS_DIR / "lgs_import/turkce1",
    "hayat": ASSETS_DIR / "lgs_import/hayat1",
    "ing": ASSETS_DIR / "lgs_import/english1",
}


def q(subject, idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, image_asset, source_ref):
    return {
        "id": f"{subject}1_{idx:04d}",
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
        "imageAsset": image_asset,
        "subject": "mat" if subject == "mat" else "turkce" if subject == "turkce" else "hayat" if subject == "hayat" else "ing",
    }


# ---- MATH (mat1) ----
def gen_mat():
    out = []
    visuals = ["quiz_images/g1_count_3.png", "quiz_images/g1_count_5.png", "quiz_images/g1_count_7.png",
               "quiz_images/g1_count_4.png", "quiz_images/g1_count_6.png", "quiz_images/g1_count_8.png",
               "quiz_images/g1_count_10.png", "quiz_images/g1_shapes.png"]
    tpls_img = [
        ("Resimde kaç nesne var?", ["3", "4", "5", "6"], 0, "sayma", "g1_count_3.png"),
        ("Resimde kaç nesne var?", ["4", "5", "6", "7"], 1, "sayma", "g1_count_5.png"),
        ("Resimde kaç tane var?", ["6", "7", "8", "9"], 1, "sayma", "g1_count_7.png"),
        ("Resimde kaç tane görüyorsun?", ["3", "4", "5", "6"], 1, "sayma", "g1_count_4.png"),
        ("Kaç tane?", ["5", "6", "7", "8"], 0, "sayma", "g1_count_5.png"),
        ("Resimde kaç tane var?", ["5", "6", "7", "8"], 2, "sayma", "g1_count_7.png"),
        ("Kaç nesne?", ["4", "5", "6", "7"], 1, "sayma", "g1_count_5.png"),
        ("Görselde kaç tane var?", ["3", "4", "5", "6"], 2, "sayma", "g1_count_5.png"),
    ]
    tpls_shape = [
        ("Resimde hangi şekil yok?", ["Daire", "Kare", "Üçgen", "Yıldız"], 3, "g1_shapes.png"),
        ("Resimde kaç daire var?", ["1", "2", "3", "4"], 0, "g1_shapes.png"),
    ]
    tpls_vis_simple = [
        ("Resimde kaç tane var? Say.", ["4", "5", "6", "7"], 0, "g1_count_4.png"),
        ("Kaç nesne görüyorsun?", ["6", "7", "8", "9"], 0, "g1_count_6.png"),
        ("Görselde kaç tane?", ["7", "8", "9", "10"], 0, "g1_count_7.png"),
        ("Resimde kaç tane var?", ["8", "9", "10", "11"], 0, "g1_count_8.png"),
        ("Kaç tane say?", ["9", "10", "11", "12"], 1, "g1_count_10.png"),
    ]
    tpls_text = [
        ("3 + 2 kaç eder?", ["4", "5", "6", "7"], 1),
        ("5 - 1 kaç eder?", ["3", "4", "5", "6"], 1),
        ("2 + 3 = ?", ["4", "5", "6", "7"], 1),
        ("4 - 2 = ?", ["1", "2", "3", "4"], 1),
        ("1, 2, 3, ? Sıradaki sayı hangisi?", ["3", "4", "5", "6"], 1),
        ("En az hangisi?", ["5", "3", "7", "9"], 1),
        ("En çok hangisi?", ["2", "4", "8", "6"], 2),
    ]
    for i, (stem, opts, ai, *rest) in enumerate(tpls_img):
        img = f"quiz_images/{rest[1]}" if len(rest) > 1 else f"quiz_images/{rest[0]}"
        out.append(q("mat", len(out), stem, opts, ai, 4, "gorsel_sayma", "sayilar", ["sayma", "gorsel"], 
                    f"Resimde doğru sayıyı sayarak bul.", img, f"mat1_{len(out):04d}"))
    for i, (stem, opts, ai, img) in enumerate(tpls_shape):
        out.append(q("mat", len(out), stem, opts, ai, 5, "sekil_tanima", "geometri", ["sekil", "gorsel"],
                    "Şekilleri resimden yorumla.", f"quiz_images/{img}", f"mat1_{len(out):04d}"))
    for stem, opts, ai, img in tpls_vis_simple * 6:
        out.append(q("mat", len(out), stem, opts, ai, 4, "gorsel_sayma", "sayilar", ["sayma", "gorsel"],
                    "Resimdeki nesneleri say.", f"quiz_images/{img}", f"mat1_{len(out):04d}"))
    for stem, opts, ai in tpls_text * 2:
        out.append(q("mat", len(out), stem, opts, ai, 4, "islem", "sayilar", ["islem", "sayilar"],
                    "Basit işlem yap.", None, f"mat1_{len(out):04d}"))
    return out[:100]


# ---- TURKISH (turkce1) ----
def gen_turkce():
    out = []
    tpls_img = [
        ("Resimde ne var?", ["Elma", "Araba", "Ev", "Güneş"], 0, "g1_apple.png"),
        ("Resimde ne görüyorsun?", ["Top", "Kitap", "Kalem", "Defter"], 0, "g1_ball.png"),
        ("Bu resim neyi gösteriyor?", ["Kitap", "Masa", "Sandalye", "Pencere"], 0, "g1_book.png"),
        ("Resimdeki nesne ne?", ["Pastel", "Silgi", "Cetvel", "Kalem"], 0, "g1_crayon.png"),
        ("Resimde ne var?", ["Sıra", "Tahta", "Dolap", "Kapı"], 0, "g1_desk.png"),
        ("Görselde ne var?", ["Elma", "Portakal", "Muz", "Üzüm"], 0, "g1_apple.png"),
        ("Bu resim neyi anlatıyor?", ["Aile", "Okul", "Park", "Ev"], 0, "g1_family.png"),
        ("Resimde ne görüyorsun?", ["Sınıf", "Bahçe", "Koridor", "Yemekhane"], 0, "g1_classroom.png"),
    ]
    tpls_text = [
        ("\"A\" harfi hangi kelimede var?", ["Anne", "Baba", "Nine", "Dede"], 0),
        ("\"E\" ile başlayan kelime hangisi?", ["Elma", "Bal", "Kedi", "Top"], 0),
        ("Hangi kelime kısa?", ["Ev", "Okul", "Bilgisayar", "Televizyon"], 0),
        ("\"Anne\" kelimesi kaç harfli?", ["3", "4", "5", "6"], 1),
    ]
    for stem, opts, ai, img in tpls_img * 8:
        if len(out) >= 70:
            break
        out.append(q("turkce", len(out), stem, opts, ai, 4, "gorsel_kelime", "kelime", ["gorsel", "okuma"],
                    "Resimle kelimeyi eşleştir.", f"quiz_images/{img}", f"turkce1_{len(out):04d}"))
    for stem, opts, ai in tpls_text * 8:
        if len(out) >= 100:
            break
        out.append(q("turkce", len(out), stem, opts, ai, 5, "harf_hece", "okuma", ["harf", "hece"],
                    "Kısa metin ve kelime bilgisi.", None, f"turkce1_{len(out):04d}"))
    return out[:100]


# ---- LIFE STUDIES (hayat1) ----
def gen_hayat():
    out = []
    tpls_img = [
        ("Resimdeki çocuk ne yapıyor?", ["El yıkıyor", "Yemek yiyor", "Uyuyor", "Oynuyor"], 0, "g1_handwash.png"),
        ("Bu resim neyi gösteriyor?", ["Paylaşmak", "Kavga", "Ağlamak", "Koşmak"], 0, "g1_share.png"),
        ("Resimde ne var?", ["Yaya geçidi", "Otobüs", "Uçak", "Tren"], 0, "g1_crossing.png"),
        ("Görselde ne yapılıyor?", ["Oyun oynanıyor", "Yemek yeniyor", "Uyuyor", "Çalışıyor"], 0, "g1_playground.png"),
        ("Bu resim neyi anlatıyor?", ["Aile birlikte", "Yalnız", "Okul", "Park"], 0, "g1_family.png"),
        ("Resimdeki yer neresi?", ["Sınıf", "Ev", "Park", "Market"], 0, "g1_classroom.png"),
        ("Çocuk ne yapıyor olabilir?", ["Paylaşıyor", "Saklıyor", "Kırıyor", "Atıyor"], 0, "g1_share.png"),
    ]
    tpls_text = [
        ("Sınıfta konuşmak isteyince ne yapmalıyız?", ["Parmak kaldırmak", "Bağırmak", "Kalkmak", "Koşmak"], 0),
        ("Yemekten önce ne yapmalıyız?", ["El yıkamak", "Uyumak", "Oynamak", "Televizyon izlemek"], 0),
        ("Arkadaşımız üzgünse ne yapmalıyız?", ["Yanında olmak", "Gülmek", "Kızmak", "Koşmak"], 0),
        ("Yaya geçidinde ne yapmalıyız?", ["Sağa sola bakıp geçmek", "Koşmak", "Oynamak", "Durma"], 0),
    ]
    for stem, opts, ai, img in tpls_img * 8:
        if len(out) >= 65:
            break
        out.append(q("hayat", len(out), stem, opts, ai, 4, "gorsel_davranis", "gunluk_hayat", ["gorsel", "davranis"],
                    "Resimden doğru davranışı çıkar.", f"quiz_images/{img}", f"hayat1_{len(out):04d}"))
    for stem, opts, ai in tpls_text * 10:
        if len(out) >= 100:
            break
        out.append(q("hayat", len(out), stem, opts, ai, 5, "dogru_davranis", "degerler", ["davranis", "deger"],
                    "Doğru davranışı seç.", None, f"hayat1_{len(out):04d}"))
    return out[:100]


# ---- ENGLISH (english1) ----
def gen_english():
    out = []
    tpls_img = [
        ("What do you see?", ["Apple", "Car", "House", "Sun"], 0, "g1_apple.png"),
        ("What is in the picture?", ["Ball", "Book", "Pen", "Desk"], 0, "g1_ball.png"),
        ("What colour?", ["Red", "Blue", "Green", "Yellow"], 0, "g1_red.png"),
        ("What is this?", ["Book", "Table", "Chair", "Door"], 0, "g1_book.png"),
        ("How many?", ["3", "4", "5", "6"], 0, "g1_count_3.png"),
        ("What colour is it?", ["Blue", "Red", "Green", "Yellow"], 0, "g1_blue.png"),
        ("Count the objects.", ["5", "6", "7", "8"], 0, "g1_count_5.png"),
        ("What do you see?", ["Crayon", "Eraser", "Ruler", "Pen"], 0, "g1_crayon.png"),
    ]
    tpls_text = [
        ("Hello! You say:", ["Hi!", "Bye!", "Sleep!", "Run!"], 0),
        ("How are you? I am:", ["Fine.", "Apple.", "Book.", "Red."], 0),
        ("What is 1 + 1?", ["2", "3", "4", "5"], 0),
        ("Red and blue make:", ["Purple", "Green", "Yellow", "Orange"], 0),
    ]
    for stem, opts, ai, img in tpls_img * 8:
        if len(out) >= 65:
            break
        out.append(q("ing", len(out), stem, opts, ai, 4, "picture_interpretation", "words", ["visual", "vocabulary"],
                    "Match picture and word.", f"quiz_images/{img}", f"eng1_{len(out):04d}"))
    for stem, opts, ai in tpls_text * 10:
        if len(out) >= 100:
            break
        out.append(q("ing", len(out), stem, opts, ai, 5, "situation_response", "greetings", ["listening", "response"],
                    "Simple English response.", None, f"eng1_{len(out):04d}"))
    return out[:100]


def shuffle_options(qq):
    opts = qq["options"]
    ai = qq["answerIndex"]
    correct = opts[ai]
    rest = [o for i, o in enumerate(opts) if i != ai]
    random.shuffle(rest)
    new_opts = [correct] + rest[:3]
    random.shuffle(new_opts)
    qq["options"] = new_opts
    qq["answerIndex"] = new_opts.index(correct)


def main():
    random.seed(42)
    subjects = [
        ("mat", gen_mat, "lgs_mat1_pack"),
        ("turkce", gen_turkce, "lgs_turkce1_pack"),
        ("hayat", gen_hayat, "lgs_hayat1_pack"),
        ("ing", gen_english, "lgs_eng1_pack"),
    ]
    total_q = 0
    total_img = 0
    report = []

    for subj, gen_fn, pack_prefix in subjects:
        questions = gen_fn()
        while len(questions) < 100:
            questions.extend(gen_fn())
        questions = questions[:100]
        for qq in questions:
            shuffle_options(qq)
        for i, qq in enumerate(questions):
            qq["id"] = f"{subj}1_{i:04d}"
            qq["sourceRef"] = qq["id"]

        out_dir = OUT_DIRS["mat" if subj == "mat" else "turkce" if subj == "turkce" else "hayat" if subj == "hayat" else "ing"]
        out_dir.mkdir(parents=True, exist_ok=True)

        img_count = sum(1 for qq in questions if qq.get("imageAsset"))
        total_q += len(questions)
        total_img += img_count

        packs = [questions[i:i + 10] for i in range(0, len(questions), 10)]
        for pi, pack_q in enumerate(packs):
            pack = {
                "version": 1,
                "mode": "LGS",
                "subject": "mat" if subj == "mat" else "turkce" if subj == "turkce" else "hayat" if subj == "hayat" else "ing",
                "publisher": "edumio",
                "questions": pack_q,
            }
            path = out_dir / f"{pack_prefix}_{pi + 1:03d}.json"
            with open(path, "w", encoding="utf-8") as f:
                json.dump(pack, f, ensure_ascii=False, indent=2)

        report.append(f"{subj}: {len(questions)} questions, {img_count} with imageAsset ({100 * img_count / len(questions):.0f}%)")
        report.append(f"  Packs: {len(packs)}, Output: {out_dir}")

    print("=" * 60)
    print("GRADE 1 VISUAL-FIRST QUESTION BANK REPORT")
    print("=" * 60)
    print(f"Total questions: {total_q}")
    print(f"Total with imageAsset: {total_img} ({100 * total_img / total_q:.0f}%)")
    for line in report:
        print(line)
    print("=" * 60)
    return 0


if __name__ == "__main__":
    exit(main())
