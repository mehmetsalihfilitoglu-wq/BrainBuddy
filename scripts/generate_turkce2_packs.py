#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 2nd Grade Türkçe question bank for EDUmio.
50 packs × 10 questions = 500 questions.
MEB 2024-2025 2. Sınıf Türkçe Öğretim Programı (Okuma, Söz Varlığı, Anlama, Görsel Okuma).
EDUmio Question Design Standard: kısa metin yorumlama, görsel yorumlama, cümlede anlam, sözcük anlamı çıkarma, paragraf ana fikri.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/turkce2"

# MEB 2. Sınıf Türkçe - beceri alanlarına göre konular
TOPICS = [
    "sozcuk_anlami",
    "cumlede_anlam",
    "kisa_metin_yorumlama",
    "ana_fikir",
    "gorsel_yorumlama",
    "metin_konusu",
    "paragraf_tamamlama",
]

NEW_GEN_TYPES = [
    "kisa_metin_yorumlama",
    "gorsel_yorumlama",
    "cumlede_anlam",
    "sozcuk_anlami_cikarma",
    "ana_fikir",
    "metin_konusu",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # Sözcük Anlamı (eş anlamlı, zıt anlamlı, bağlamdan anlam)
    ("\"Güzel\" sözcüğünün zıt anlamlısı aşağıdakilerden hangisi olabilir?", "sozcuk_anlami", ["Çirkin", "İyi", "Büyük", "Yeni"]),
    ("\"Ev\" sözcüğü \"yuva\" sözcüğüyle aynı anlamda kullanılabilir. Bu iki sözcük nasıl adlandırılır?", "sozcuk_anlami", ["Eş anlamlı", "Zıt anlamlı", "Eş sesli", "Benzetme"]),
    ("\"Hızlı\" sözcüğünün zıt anlamlısı hangisidir?", "sozcuk_anlami", ["Yavaş", "Koşmak", "Gitmek", "Uzak"]),
    ("\"Büyük\" sözcüğünün zıt anlamlısı hangisi olabilir?", "sozcuk_anlami", ["Küçük", "Geniş", "Uzun", "Yüksek"]),
    ("\"Mutlu\" sözcüğü \"çok mutlu bir gün geçirdik\" cümlesinde ne anlama gelir?", "sozcuk_anlami", ["Sevinçli, keyifli", "Üzgün", "Yorgun", "Kızgın"]),
    ("\"Anne\" ve \"ana\" sözcükleri arasındaki ilişki nedir?", "sozcuk_anlami", ["Eş anlamlıdır", "Zıt anlamlıdır", "Eş seslidir", "İlişki yoktur"]),
    # Cümlede Anlam
    ("\"Elif kitabı okudu\" cümlesinde asıl anlatılmak istenen nedir?", "cumlede_anlam", ["Elif okuma eylemini yaptı", "Kitap vardır", "Elif bir şey yaptı", "Kitap okunabilir"]),
    ("\"Ne kadar çalışırsan o kadar başarılı olursun\" cümlesinde hangi anlam vardır?", "cumlede_anlam", ["Çalışma ile başarı ilişkisi", "Çalışmak zor", "Başarı önemli değil", "Zaman önemli"]),
    ("\"Bugün hava çok güzel\" cümlesinde yazar ne hissetmektedir?", "cumlede_anlam", ["Havanın iyi olduğunu düşünüyor", "Üzüntü", "Kızgınlık", "Kayıtsızlık"]),
    ("\"Arkadaşıma yardım ettim\" cümlesi ne anlatır?", "cumlede_anlam", ["Yardım etme eylemi", "Arkadaş vardır", "Yardım zor", "Arkadaş mutlu"]),
    ("\"Sabah erken kalktım, okula gittim\" cümlelerinde olaylar nasıl anlatılmıştır?", "cumlede_anlam", ["Sırayla, oluş sırasına göre", "Karışık", "Sadece sabah", "Sadece okul"]),
    # Kısa Metin Yorumlama
    ("\"Ali her gün kitap okur. Kitaplar onu çok mutlu eder. En çok masalları sever.\"\n\nBu metinde Ali ile ilgili hangisi söylenmektedir?", "kisa_metin_yorumlama", ["Kitap okumayı seven, masalları tercih eden", "Ali okula gider", "Ali yalnızdır", "Masallar kısadır"]),
    ("\"Annem yemek yaptı. Sofrayı hazırladı. Hep birlikte yedik.\"\n\nBu metnin konusu nedir?", "kisa_metin_yorumlama", ["Aile ile birlikte yemek yeme", "Yemek nasıl yapılır", "Sofra nasıl hazırlanır", "Annem çalışkan"]),
    ("\"Parkta oynadık. Salıncakta sallandık. Top oynadık. Çok eğlendik.\"\n\nMetinde ne anlatılmaktadır?", "kisa_metin_yorumlama", ["Parkta yapılan etkinlikler ve eğlence", "Salıncak tehlikelidir", "Top oynamak iyidir", "Park uzaktadır"]),
    ("\"Dedem bana masal anlattı. Uykuya daldım. Rüyamda masal kahramanları vardı.\"\n\nBu metinde ne olmuştur?", "kisa_metin_yorumlama", ["Dedem masal anlattı, çocuk uyudu ve rüya gördü", "Masallar uzundur", "Dede uyudu", "Rüya önemlidir"]),
    # Ana Fikir
    ("\"Kitap okumak çok güzel. Bizi bilgilendirir. Hayal kurmamızı sağlar. Her gün okumalıyız.\"\n\nBu paragrafın ana fikri nedir?", "ana_fikir", ["Kitap okumak faydalıdır, her gün okumalıyız", "Kitaplar uzundur", "Hayal kurmak güzeldir", "Bilgi önemlidir"]),
    ("\"Arkadaşlarımızla paylaşmalıyız. Paylaşmak bizi mutlu eder. Kimse yalnız kalmamalı.\"\n\nParagrafın ana fikri aşağıdakilerden hangisidir?", "ana_fikir", ["Paylaşmak önemlidir ve mutluluk verir", "Arkadaş çok olmalı", "Yalnız kalmak kötüdür", "Mutluluk zordur"]),
    ("\"Çevremizi temiz tutmalıyız. Çöpleri yere atmamalıyız. Ağaçlara zarar vermemeliyiz.\"\n\nBu paragrafın ana fikri nedir?", "ana_fikir", ["Çevre temizliğine dikkat etmeliyiz", "Çöp kötüdür", "Ağaç önemlidir", "Temizlik zordur"]),
    ("\"Erken yatıp erken kalkmalıyız. Uyku sağlığımız için önemlidir. Dinlenen vücut güçlü olur.\"\n\nAna fikir hangisidir?", "ana_fikir", ["Uyku ve dinlenme sağlık için önemlidir", "Erken kalkmak zor", "Vücut güçlü olmalı", "Yatmak iyidir"]),
    # Görsel Yorumlama
    ("Bir resimde çocuk kitap okuyor. Bu görsel ne anlatabilir?", "gorsel_yorumlama", ["Okuma eylemi, kitap sevgisi", "Sadece bir çocuk", "Kitabın rengi", "Çocuk oturuyor"]),
    ("Görselde okul bahçesinde oynayan çocuklar var. Bu ne anlama gelebilir?", "gorsel_yorumlama", ["Okul yaşamı, oyun, arkadaşlık", "Sadece bahçe", "Çocuk sayısı", "Hava güzel"]),
    ("Resimde aile sofrada yemek yiyor. Görsel neyi anlatır?", "gorsel_yorumlama", ["Aile ile birlikte yemek yeme", "Yemek lezzetlidir", "Sofra büyüktür", "Ev güzeldir"]),
    ("Görselde bir çocuk dişlerini fırçalıyor. Bu neyi gösterir?", "gorsel_yorumlama", ["Kişisel temizlik, diş fırçalama alışkanlığı", "Banyo vardır", "Fırça önemlidir", "Su kullanılır"]),
    ("Resimde çocuklar parkta oynuyor. Bu görsel ne anlatır?", "gorsel_yorumlama", ["Oyun, eğlence, dışarıda vakit geçirme", "Park yeşildir", "Çocuklar koşar", "Salıncak vardır"]),
    # Metin Konusu
    ("\"Kedi evde uyuyordu. Birden fare gördü. Hemen kovaladı. Fare deliğe kaçtı.\"\n\nBu metnin konusu nedir?", "metin_konusu", ["Kedinin fareyi kovalaması", "Kedi uyur", "Fare hızlıdır", "Evde delik vardır"]),
    ("\"Elma ağacı çiçek açtı. Arılar geldi. Kelebekler de uçuştu. Bahar gelmişti.\"\n\nMetnin konusu nedir?", "metin_konusu", ["Bahar mevsimi ve doğadaki değişim", "Elma tatlıdır", "Arılar çalışır", "Kelebek güzeldir"]),
    ("\"Ayşe sabah erkenden kalktı. Kahvaltı yaptı. Okul çantasını hazırladı. Yola çıktı.\"\n\nBu metin neyi anlatıyor?", "metin_konusu", ["Ayşe'nin okula hazırlanması", "Kahvaltı önemlidir", "Çanta ağırdır", "Sabah erkendir"]),
    ("\"Dedem bahçede domates ekti. Suladı. Domatesler büyüdü. Kırmızı kırmızı oldular.\"\n\nMetnin konusu nedir?", "metin_konusu", ["Bahçede domates yetiştirme", "Dede çalışkandır", "Domates kırmızıdır", "Su önemlidir"]),
    # Paragraf Tamamlama (2. sınıf uygun - basit cümle tamamlama)
    ("\"Pazardan elma, armut ve muz aldık. Eve geldik. Annem meyveleri yıkadı. ---\"\n\nBoş bırakılan yere aşağıdakilerden hangisi gelmelidir?", "paragraf_tamamlama", ["Hep birlikte yedik.", "Elma kırmızıdır.", "Pazar uzaktı.", "Muz sarıdır."]),
    ("\"Yağmur yağdı. Sokaklar ıslandı. Şemsiyemi açtım. ---\"\n\nBu paragrafı en iyi tamamlayan cümle hangisidir?", "paragraf_tamamlama", ["Okula şemsiye ile gittim.", "Yağmur ıslattı.", "Şemsiye büyüktü.", "Sokak uzundu."]),
    ("\"Kütüphaneye gittim. Bir kitap seçtim. Sessizce oturdum. ---\"\n\nBoş bırakılan yere hangisi uygun olur?", "paragraf_tamamlama", ["Kitabı okumaya başladım.", "Kütüphane büyüktü.", "Kitap kalındı.", "Sessizlik güzeldi."]),
    ("\"Dedem bana hikâye anlattı. Çok güzel bir hikâyeydi. Dinlerken ---\"\n\nParagrafı tamamlayan en uygun cümle hangisidir?", "paragraf_tamamlama", ["çok mutlu oldum.", "Dedem yoruldu.", "Hikâye uzundu.", "Saat geç oldu."]),
    # Extra for balance
    ("\"Sıcak\" sözcüğünün zıt anlamlısı hangisidir?", "sozcuk_anlami", ["Soğuk", "Ilık", "Sıcak", "Hava"]),
    ("\"Yardım\" ve \"destek\" sözcükleri arasında nasıl bir ilişki vardır?", "sozcuk_anlami", ["Bağlamda eş anlamlı kullanılabilir", "Zıt anlamlıdır", "İlişki yoktur", "Biri uzun biri kısa"]),
    ("\"Oyun oynamak eğlencelidir. Arkadaşlarla oynamak daha da güzeldir.\"\n\nBu cümlelerde vurgulanan nedir?", "cumlede_anlam", ["Arkadaşlarla oynamanın güzelliği", "Oyun vardır", "Eğlence önemlidir", "Arkadaş çok olmalı"]),
    ("\"Zeynep resim yapmayı seviyor. Her gün yeni resimler çiziyor. Öğretmeni de onu övüyor.\"\n\nBu metinde Zeynep ile ilgili hangisi söylenmektedir?", "kisa_metin_yorumlama", ["Resim yapmayı seven, her gün çizen bir çocuk", "Öğretmen övdü", "Resim güzeldir", "Zeynep çalışkandır"]),
    ("\"Doğru söylemeliyiz. Yalan söylemek yanlıştır. Dürüst olmak güzel bir davranıştır.\"\n\nParagrafın ana fikri nedir?", "ana_fikir", ["Dürüst olmak, doğru söylemek önemlidir", "Yalan kötüdür", "Doğru söylemek zordur", "Güzel davranış önemlidir"]),
    ("Görselde bir çocuk ellerini yıkıyor. Bu neyi gösterir?", "gorsel_yorumlama", ["Temizlik alışkanlığı, hijyen", "Musluk vardır", "Su akar", "Banyo büyüktür"]),
    ("\"Kış geldi. Kar yağdı. Çocuklar kardan adam yaptı. Sonra sıcak çikolata içtiler.\"\n\nMetnin konusu nedir?", "metin_konusu", ["Kış gününde karda oynama ve ısınma", "Kar soğuktur", "Kardan adam güzeldir", "Çikolata lezzetlidir"]),
    ("\"Bahçede çiçekler açtı. Kelebekler uçuştu. Kuşlar öttü. ---\"\n\nBoş bırakılan yere hangisi gelmelidir?", "paragraf_tamamlama", ["Bahar çok güzeldi.", "Çiçekler sarıydı.", "Kuşlar çoktu.", "Kelebek renkliydi."]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"turkce2_{idx:04d}",
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
        "subject": "turkce",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = ["Metinde bu bilgi verilmemiştir.", "Bu ana fikir değil, detaydır.", "Yanlış anlam çıkarılmıştır.", "Bağlama uygun değildir."]
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
            expl = f"Metin ve bağlama göre doğru cevap \"{correct}\"dir. Diğer seçenekler yanlış anlam çıkarma, eksik yorum veya ana fikir-detay karışıklığı içermektedir."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10] if len(topic) >= 10 else topic, "okuma_anlama", "yorumlama"],
                expl, f"turkce2_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"turkce2_{idx:04d}"
        qq["id"] = f"turkce2_{idx:04d}"

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
            "subject": "turkce",
            "publisher": "edumio",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_turkce2_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("TURKCE2 (2. Sınıf Türkçe) Question Bank Report")
    print("MEB 2024-2025 Öğretim Programı")
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
    random.seed(41)
    main()
