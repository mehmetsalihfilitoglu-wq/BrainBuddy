#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Extend Grade 1 banks: add packs 011+ to reach 300 mat, 300 turkce, 200 hayat, 200 english.
Keeps existing packs 001-010. Writes pack_011 through pack_030 (mat, turkce), pack_011-020 (hayat, english).
Visual-first, 50%+ imageAsset, 80%+ new-gen quality.
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

# New-gen question types
NEW_GEN_TYPES = {"gorsel_sayma", "sekil_tanima", "gorsel_kelime", "gorsel_davranis", "picture_interpretation",
                 "situation_response", "meaning_inference", "dogru_davranis", "harf_hece", "islem"}


def q(subject, global_idx, stem, options, ai, diff, qtype, topic, skills, explanation, image_asset, source_ref):
    subj_key = "mat" if subject == "mat" else "turkce" if subject == "turkce" else "hayat" if subject == "hayat" else "ing"
    return {
        "id": f"{subject}1_{global_idx:04d}",
        "stem": stem,
        "options": options,
        "answerIndex": ai,
        "difficulty": diff,
        "questionType": qtype,
        "topic": topic,
        "skills": skills,
        "explanation": explanation,
        "source": "brainbuddy",
        "sourceRef": source_ref,
        "imageAsset": image_asset,
        "subject": subj_key,
    }


def shuffle_opts(opts, ai):
    correct = opts[ai]
    rest = [o for i, o in enumerate(opts) if i != ai]
    random.shuffle(rest)
    new_opts = [correct] + rest[:3]
    random.shuffle(new_opts)
    return new_opts, new_opts.index(correct)


# ---- MATH: 200 additional (idx 100-299) ----
def gen_mat_extra(start_idx):
    out = []
    count_imgs = ["g1_count_3.png", "g1_count_4.png", "g1_count_5.png", "g1_count_6.png",
                  "g1_count_7.png", "g1_count_8.png", "g1_count_10.png"]
    count_correct = [3, 4, 5, 6, 7, 8, 10]
    tpls_vis = []
    for img, corr in zip(count_imgs * 12, count_correct * 12):
        wrongs = [str(corr + d) for d in [-2, -1, 1, 2] if 1 <= corr + d <= 12]
        opts = [str(corr)] + [w for w in wrongs if w != str(corr)][:3]
        stems = ["Resimde kaç nesne var?", "Kaç tane say?", "Görselde kaç tane var?", "Resimde kaç tane görüyorsun?"]
        tpls_vis.append((random.choice(stems), opts, 0, f"quiz_images/{img}"))
    for stem, opts, _, img in tpls_vis:
        opts, ai = shuffle_opts(opts, 0)
        out.append(q("mat", start_idx + len(out), stem, opts, ai, 4, "gorsel_sayma", "sayilar",
                    ["sayma", "gorsel"], "Resimde doğru sayıyı say.", img, f"mat1_{start_idx + len(out):04d}"))
    shape_tpls = [
        ("Resimde hangi şekil var?", ["Daire", "Kare", "Üçgen", "Yıldız"], [0, 1, 2], "g1_shapes.png"),
        ("Şekiller arasında hangisi yok?", ["Daire", "Kare", "Üçgen", "Beşgen"], 3, "g1_shapes.png"),
    ]
    for _ in range(20):
        s, o, ai, img = random.choice(shape_tpls)
        if isinstance(ai, list):
            ai = random.choice(ai)
        opts, ai2 = shuffle_opts(o, ai)
        out.append(q("mat", start_idx + len(out), s, opts, ai2, 5, "sekil_tanima", "geometri",
                    ["sekil", "gorsel"], "Şekilleri resimden yorumla.", f"quiz_images/{img}", f"mat1_{start_idx + len(out):04d}"))
    text_tpls = [
        ("1 + 1 = ?", ["1", "2", "3", "4"], 1),
        ("2 + 1 = ?", ["2", "3", "4", "5"], 1),
        ("3 + 2 = ?", ["4", "5", "6", "7"], 1),
        ("4 + 1 = ?", ["4", "5", "6", "7"], 1),
        ("5 - 2 = ?", ["2", "3", "4", "5"], 1),
        ("6 - 1 = ?", ["4", "5", "6", "7"], 1),
        ("2, 4, 6, ? Sıradaki?", ["7", "8", "9", "10"], 1),
        ("1, 2, 3, 4, ?", ["4", "5", "6", "7"], 2),
        ("En az hangisi?", ["8", "3", "7", "9"], 1),
        ("En çok hangisi?", ["2", "4", "9", "6"], 2),
        ("3 ile 2'nin toplamı?", ["4", "5", "6", "7"], 1),
        ("7'den 2 çıkarırsak?", ["4", "5", "6", "7"], 0),
    ]
    for _ in range(96):
        stem, opts, ai = random.choice(text_tpls)
        opts, ai2 = shuffle_opts(opts, ai)
        out.append(q("mat", start_idx + len(out), stem, opts, ai2, 4, "islem", "sayilar",
                    ["islem", "sayilar"], "Basit işlem yap.", None, f"mat1_{start_idx + len(out):04d}"))
    return out[:200]


# ---- TURKISH: 200 additional ----
def gen_turkce_extra(start_idx):
    out = []
    vis_tpls = [
        ("Resimde ne var?", ["Elma", "Araba", "Ev", "Güneş"], 0, "g1_apple.png"),
        ("Resimde ne görüyorsun?", ["Top", "Kitap", "Kalem", "Defter"], 0, "g1_ball.png"),
        ("Bu resim neyi gösteriyor?", ["Kitap", "Masa", "Sandalye", "Pencere"], 0, "g1_book.png"),
        ("Resimdeki nesne ne?", ["Pastel", "Silgi", "Cetvel", "Kalem"], 0, "g1_crayon.png"),
        ("Resimde ne var?", ["Sıra", "Tahta", "Dolap", "Kapı"], 0, "g1_desk.png"),
        ("Görselde ne var?", ["Elma", "Portakal", "Muz", "Üzüm"], 0, "g1_apple.png"),
        ("Bu resim neyi anlatıyor?", ["Aile", "Okul", "Park", "Ev"], 0, "g1_family.png"),
        ("Resimde ne görüyorsun?", ["Sınıf", "Bahçe", "Koridor", "Yemekhane"], 0, "g1_classroom.png"),
        ("Resimde hangi renk?", ["Kırmızı", "Mavi", "Yeşil", "Sarı"], 0, "g1_red.png"),
        ("Görselde ne var?", ["Top", "Bisiklet", "Sandalye", "Lamba"], 0, "g1_ball.png"),
    ]
    for _ in range(130):
        stem, opts, ai, img = random.choice(vis_tpls)
        opts, ai2 = shuffle_opts(opts, ai)
        out.append(q("turkce", start_idx + len(out), stem, opts, ai2, 4, "gorsel_kelime", "kelime",
                    ["gorsel", "okuma"], "Resimle kelimeyi eşleştir.", f"quiz_images/{img}", f"turkce1_{start_idx + len(out):04d}"))
    text_tpls = [
        ("\"A\" harfi hangi kelimede var?", ["Anne", "Baba", "Nine", "Dede"], 0),
        ("\"E\" ile başlayan?", ["Elma", "Bal", "Kedi", "Top"], 0),
        ("Hangi kelime kısa?", ["Ev", "Okul", "Bilgisayar", "Televizyon"], 0),
        ("\"Anne\" kaç harfli?", ["3", "4", "5", "6"], 1),
        ("\"Kedi\" kelimesinin ilk harfi?", ["K", "C", "E", "D"], 0),
        ("Zıt anlamlı: büyük - ?", ["Küçük", "Uzun", "Geniş", "Yüksek"], 0),
        ("\"Okul\" kelimesinin son harfi?", ["O", "K", "U", "L"], 3),
        ("Hangi hece \"ma-sa\"da var?", ["ma", "sa", "as", "am"], 0),
    ]
    for _ in range(70):
        stem, opts, ai = random.choice(text_tpls)
        opts, ai2 = shuffle_opts(opts, ai)
        out.append(q("turkce", start_idx + len(out), stem, opts, ai2, 5, "harf_hece", "okuma",
                    ["harf", "hece"], "Kısa metin ve kelime bilgisi.", None, f"turkce1_{start_idx + len(out):04d}"))
    return out[:200]


# ---- LIFE STUDIES: 100 additional ----
def gen_hayat_extra(start_idx):
    out = []
    vis_tpls = [
        ("Resimdeki çocuk ne yapıyor?", ["El yıkıyor", "Yemek yiyor", "Uyuyor", "Oynuyor"], 0, "g1_handwash.png"),
        ("Bu resim neyi gösteriyor?", ["Paylaşmak", "Kavga", "Ağlamak", "Koşmak"], 0, "g1_share.png"),
        ("Resimde ne var?", ["Yaya geçidi", "Otobüs", "Uçak", "Tren"], 0, "g1_crossing.png"),
        ("Görselde ne yapılıyor?", ["Oyun oynanıyor", "Yemek yeniyor", "Uyuyor", "Çalışıyor"], 0, "g1_playground.png"),
        ("Bu resim neyi anlatıyor?", ["Aile birlikte", "Yalnız", "Okul", "Park"], 0, "g1_family.png"),
        ("Resimdeki yer neresi?", ["Sınıf", "Ev", "Park", "Market"], 0, "g1_classroom.png"),
        ("Çocuk ne yapıyor?", ["Paylaşıyor", "Saklıyor", "Kırıyor", "Atıyor"], 0, "g1_share.png"),
        ("Görselde ne oluyor?", ["El yıkama", "Yemek", "Uyku", "Koşu"], 0, "g1_handwash.png"),
    ]
    for _ in range(60):
        stem, opts, ai, img = random.choice(vis_tpls)
        opts, ai2 = shuffle_opts(opts, ai)
        out.append(q("hayat", start_idx + len(out), stem, opts, ai2, 4, "gorsel_davranis", "gunluk_hayat",
                    ["gorsel", "davranis"], "Resimden doğru davranışı çıkar.", f"quiz_images/{img}", f"hayat1_{start_idx + len(out):04d}"))
    text_tpls = [
        ("Sınıfta konuşmak isteyince?", ["Parmak kaldırmak", "Bağırmak", "Kalkmak", "Koşmak"], 0),
        ("Yemekten önce?", ["El yıkamak", "Uyumak", "Oynamak", "Televizyon"], 0),
        ("Arkadaşımız üzgünse?", ["Yanında olmak", "Gülmek", "Kızmak", "Koşmak"], 0),
        ("Yaya geçidinde?", ["Sağa sola bakıp geçmek", "Koşmak", "Oynamak", "Durma"], 0),
        ("Oyuncağı paylaşmak?", ["İyidir", "Kötüdür", "Önemsiz", "Yasak"], 0),
        ("Öğretmen konuşurken?", ["Dinlemek", "Konuşmak", "Kalkmak", "Oynamak"], 0),
        ("Temiz olmak neden önemli?", ["Sağlık için", "Uyumak için", "Oynamak için", "Koşmak için"], 0),
    ]
    for _ in range(40):
        stem, opts, ai = random.choice(text_tpls)
        opts, ai2 = shuffle_opts(opts, ai)
        out.append(q("hayat", start_idx + len(out), stem, opts, ai2, 5, "dogru_davranis", "degerler",
                    ["davranis", "deger"], "Doğru davranışı seç.", None, f"hayat1_{start_idx + len(out):04d}"))
    return out[:100]


# ---- ENGLISH: 100 additional ----
def gen_english_extra(start_idx):
    out = []
    vis_tpls = [
        ("What do you see?", ["Apple", "Car", "House", "Sun"], 0, "g1_apple.png"),
        ("What is in the picture?", ["Ball", "Book", "Pen", "Desk"], 0, "g1_ball.png"),
        ("What colour?", ["Red", "Blue", "Green", "Yellow"], 0, "g1_red.png"),
        ("What is this?", ["Book", "Table", "Chair", "Door"], 0, "g1_book.png"),
        ("How many?", ["3", "4", "5", "6"], 0, "g1_count_3.png"),
        ("What colour is it?", ["Blue", "Red", "Green", "Yellow"], 0, "g1_blue.png"),
        ("Count the objects.", ["5", "6", "7", "8"], 0, "g1_count_5.png"),
        ("What do you see?", ["Crayon", "Eraser", "Ruler", "Pen"], 0, "g1_crayon.png"),
        ("What is in the picture?", ["Desk", "Ball", "Apple", "Sun"], 0, "g1_desk.png"),
        ("What colour?", ["Green", "Red", "Blue", "Yellow"], 0, "g1_green.png"),
    ]
    for _ in range(65):
        stem, opts, ai, img = random.choice(vis_tpls)
        opts, ai2 = shuffle_opts(opts, ai)
        out.append(q("ing", start_idx + len(out), stem, opts, ai2, 4, "picture_interpretation", "words",
                    ["visual", "vocabulary"], "Match picture and word.", f"quiz_images/{img}", f"eng1_{start_idx + len(out):04d}"))
    text_tpls = [
        ("Hello! You say:", ["Hi!", "Bye!", "Sleep!", "Run!"], 0),
        ("How are you? I am:", ["Fine.", "Apple.", "Book.", "Red."], 0),
        ("What is 1 + 1?", ["2", "3", "4", "5"], 0),
        ("Red and blue make:", ["Purple", "Green", "Yellow", "Orange"], 0),
        ("Goodbye! You say:", ["Bye!", "Hello!", "Yes!", "No!"], 0),
        ("Thank you! You say:", ["You're welcome!", "Hi!", "Run!", "Sleep!"], 0),
        ("What is 2 + 2?", ["3", "4", "5", "6"], 1),
        ("Green and yellow make:", ["Blue", "Orange", "Purple", "Red"], 1),
    ]
    for _ in range(35):
        stem, opts, ai = random.choice(text_tpls)
        opts, ai2 = shuffle_opts(opts, ai)
        out.append(q("ing", start_idx + len(out), stem, opts, ai2, 5, "situation_response", "greetings",
                    ["listening", "response"], "Simple English response.", None, f"eng1_{start_idx + len(out):04d}"))
    return out[:100]


def main():
    random.seed(77)
    configs = [
        ("mat", gen_mat_extra, "lgs_mat1_pack", 100, 200, 11, 30),
        ("turkce", gen_turkce_extra, "lgs_turkce1_pack", 100, 200, 11, 30),
        ("hayat", gen_hayat_extra, "lgs_hayat1_pack", 100, 100, 11, 20),
        ("ing", gen_english_extra, "lgs_eng1_pack", 100, 100, 11, 20),
    ]
    report = []

    for subj, gen_fn, pack_prefix, start_idx, n_questions, pack_start, pack_end in configs:
        questions = gen_fn(start_idx)
        out_dir = OUT_DIRS[subj]
        out_dir.mkdir(parents=True, exist_ok=True)
        img_count = sum(1 for qq in questions if qq.get("imageAsset"))
        new_gen = sum(1 for qq in questions if qq.get("questionType") in NEW_GEN_TYPES)

        packs = [questions[i:i + 10] for i in range(0, len(questions), 10)]
        for pi, pack_q in enumerate(packs):
            pack_num = pack_start + pi
            pack = {
                "version": 1,
                "mode": "LGS",
                "subject": "mat" if subj == "mat" else "turkce" if subj == "turkce" else "hayat" if subj == "hayat" else "ing",
                "publisher": "brainbuddy",
                "questions": pack_q,
            }
            path = out_dir / f"{pack_prefix}_{pack_num:03d}.json"
            with open(path, "w", encoding="utf-8") as f:
                json.dump(pack, f, ensure_ascii=False, indent=2)

        report.append(f"{subj}: +{len(questions)} questions (packs {pack_start:03d}-{pack_end:03d})")
        report.append(f"  imageAsset: {img_count}/{len(questions)} ({100*img_count/len(questions):.0f}%), new-gen: {100*new_gen/len(questions):.0f}%")

    print("=" * 60)
    print("GRADE 1 EXTENSION REPORT (packs 011+)")
    print("=" * 60)
    for line in report:
        print(line)
    print("=" * 60)
    return 0


if __name__ == "__main__":
    exit(main())
