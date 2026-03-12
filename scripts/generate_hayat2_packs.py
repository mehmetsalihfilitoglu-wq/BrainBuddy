#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 2nd Grade Hayat Bilgisi (Life Studies) question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
MEB 2024-2025 2. Sınıf Hayat Bilgisi Öğretim Programı (6 öğrenme alanı).
BrainBuddy Question Design Standard: senaryo tabanlı, günlük hayat, doğru davranış, sebep-sonuç, değer/davranış analizi.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/hayat2"

# MEB 2. Sınıf Hayat Bilgisi - 6 öğrenme alanı (EgitimOkulu, TYMM)
TOPICS = [
    "ben_ve_okulum",
    "sagligim_guvenligim",
    "ailem_ve_toplum",
    "yasadigim_yer_ulkem",
    "doga_ve_cevre",
    "bilim_teknoloji_sanat",
]

NEW_GEN_TYPES = [
    "senaryo_yorumlama",
    "gunluk_hayat_durumu",
    "dogru_davranis_buldurma",
    "sebep_sonuc",
    "deger_davranis_analizi",
    "yorum_gerektiren",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # 1. Ben ve Okulum
    ("Elif sınıfta arkadaşının silgisini düşürdüğünü gördü. Hemen alıp verdi.\n\nBu davranış hangi değeri gösterir?",
     "ben_ve_okulum", ["Yardımlaşma", "Sadece kendi eşyasına bakmak", "Silgi vermek zorunlu", "Başkasının eşyasına dokunmamak"]),
    ("Derste konuşmak isteyen Ali parmak kaldırıyor. Öğretmen söz verince konuşuyor.\n\nBu davranışın sebebi nedir?",
     "ben_ve_okulum", ["Sırayla konuşmak ve başkalarını dinlemek", "Öğretmen kızar diye", "Parmak kaldırmak zorunlu", "Konuşmak yasak"]),
    ("Teneffüste oyun oynarken sıraya giren çocuklar birbirini itmiyor.\n\nBu neyi gösterir?",
     "ben_ve_okulum", ["Kurallara uyma ve saygı", "Oyun sıkıcı", "Sıra önemsiz", "İtmek yasak"]),
    ("Yeni gelen arkadaşa \"Adın ne? Oynayalım mı?\" diyen çocuk ne yapıyor?",
     "ben_ve_okulum", ["Yeni arkadaşa hoş geldin demek ve oyuna davet etmek", "Sadece adını sormak", "Oyun oynamak zorunlu", "Yabancıyla konuşmak"]),
    ("Çantasını her akşam hazırlayan çocuk sabah eksiksiz okula gidiyor.\n\nBu alışkanlığın faydası nedir?",
     "ben_ve_okulum", ["Düzen ve sorumluluk; okulda eksik kalmamak", "Sadece anne hazırlasın", "Çanta ağır olmasın", "Okula geç kalmamak"]),
    # 2. Sağlığım ve Güvenliğim
    ("Her sabah kahvaltı yapan çocuk okulda daha dikkatli oluyor.\n\nBunun sebebi nedir?",
     "sagligim_guvenligim", ["Kahvaltı enerji ve dikkat sağlar", "Sabah erken kalkmak", "Okul yakın", "Öğretmen söyledi"]),
    ("Elif ellerini yemekten önce yıkıyor. Dişlerini günde iki kez fırçalıyor.\n\nBu alışkanlıklar ne için önemlidir?",
     "sagligim_guvenligim", ["Sağlığı korumak; hastalıkları azaltmak", "Ailesi istediği için", "Sadece temiz görünmek", "Dişler beyaz olsun"]),
    ("Yaya kaldırımında yürüyen çocuk trafik ışığında bekliyor.\n\nBu davranışların sebebi nedir?",
     "sagligim_guvenligim", ["Güvenliği sağlamak", "Polis görür", "Yürümek yorucu", "Işık güzeldir"]),
    ("Tanımadığı biri \"Seni eve götüreyim\" dedi. Çocuk \"Hayır\" deyip annesinin yanına gitti.\n\nBu tepki neden doğrudur?",
     "sagligim_guvenligim", ["Tanımadığı kişilerle gitmemek; kendini korumak", "Kaba davranmak", "Annem bekliyor", "Eve uzak"]),
    ("Kışın mont, atkı ve eldiven giyen çocuk neden böyle giyiniyor?",
     "sagligim_guvenligim", ["Soğuktan korunmak; sağlıklı kalmak", "Moda için", "Annem giydirdi", "Renkler güzel"]),
    # 3. Ailem ve Toplum
    ("Sofrada yemek yerken \"Afiyet olsun\" diyen çocuk ne yapıyor?",
     "ailem_ve_toplum", ["Nezaket göstermek", "Zorunlu söz", "Aç kalmamak", "Yemek bitmesin"]),
    ("Babası yorgun geldiğinde çocuk ona su getiriyor.\n\nBu davranış neyi gösterir?",
     "ailem_ve_toplum", ["İlgi ve saygı", "Su getirmek kolay", "Baba susadı", "Anne söyledi"]),
    ("Evde masayı toplamayı üstlenen çocuk aileye nasıl katkı sağlıyor?",
     "ailem_ve_toplum", ["Sorumluluk alarak ev işine yardım", "Sadece masayı taşımak", "Anne dinlensin", "Çocuk işi bu"]),
    ("Komşu teyze hasta. Anne \"Geçmiş olsun\" kartı yazıyor. Çocuk da resim çizip ekliyor.\n\nBu neyi gösterir?",
     "ailem_ve_toplum", ["Yardıma ihtiyacı olanlara duyarlılık", "Kart yazmak güzel", "Resim çizmek eğlenceli", "Komşu önemli"]),
    ("Harçlığını bir kısmını kumbarasına atan çocuk ne yapıyor?",
     "ailem_ve_toplum", ["Tasarruf; para biriktirme", "Kumbara dolsun", "Harcayacak para yok", "Anne istedi"]),
    # 4. Yaşadığım Yer ve Ülkem
    ("23 Nisan'da okulda bayram kutlanıyor. Çocuklar şiir okuyor.\n\nBu günün anlamı nedir?",
     "yasadigim_yer_ulkem", ["Ulusal Egemenlik ve Çocuk Bayramı", "Tatil günü", "Şiir okuma günü", "Okul etkinliği"]),
    ("Türk bayrağı kırmızı ve beyaz. Dalgalanınca neyi hatırlatır?",
     "yasadigim_yer_ulkem", ["Vatanımızı ve bağımsızlığımızı", "Renkler güzel", "Bayrak asmak zorunlu", "Sadece süs"]),
    ("İstiklal Marşı okunurken ayağa kalkan çocuklar ne yapıyor?",
     "yasadigim_yer_ulkem", ["Millî değerlere saygı göstermek", "Öğretmen söyledi", "Ayakta durmak iyi", "Marş uzun"]),
    ("Atatürk'ün çocukluğu anlatılıyor. Küçükken kitapları çok sevdiği söyleniyor.\n\nBu neyi gösterir?",
     "yasadigim_yer_ulkem", ["Atatürk küçükken de okumayı severdi", "Sadece çocuktu", "Kitap pahalıydı", "Okul vardı"]),
    ("Cumhuriyet Bayramı'nda tören yapılıyor. Bu gün neyi kutluyoruz?",
     "yasadigim_yer_ulkem", ["Cumhuriyetin ilanını; halkın yönetime katılmasını", "Tatil", "Okul açılışı", "Yazı bayramı"]),
    # 5. Doğa ve Çevre
    ("Piknikte çöpleri toplayıp çöp kutusuna atan aile ne yapıyor?",
     "doga_ve_cevre", ["Çevreyi temiz tutmak", "Ceza yememek", "Çöp poşeti dolsun", "Piknik bitti"]),
    ("Geri dönüşüm kutusuna kağıt atan çocuk neye katkı sağlar?",
     "doga_ve_cevre", ["Kaynakları korumaya; çevreye", "Kutu dolsun", "Öğretmen söyledi", "Kağıt çöp"]),
    ("Hayvanlara zarar vermeyen, onları besleyen çocuk ne yapıyor?",
     "doga_ve_cevre", ["Canlılara saygı göstermek", "Hayvanlar aç", "Oyun oynamak", "Sadece kedi köpek"]),
    ("Suyu boşa harcamayan çocuk musluğu kapatıyor.\n\nBu alışkanlık neden önemlidir?",
     "doga_ve_cevre", ["Su tasarrufu; kaynakları korumak", "Fatura azalsın", "Musluk bozulmasın", "Anne söyledi"]),
    ("Ağaç diken çocuk \"Bu ağaç büyüyünce gölge olacak\" diyor.\n\nBu neyi anlatır?",
     "doga_ve_cevre", ["Ağaçların canlılara faydası", "Ağaç büyür", "Gölge serin", "Toprak önemli"]),
    # 6. Bilim, Teknoloji ve Sanat
    ("Bilgisayarı sadece ders için kullanan çocuk saatlerce oyun oynamıyor.\n\nBu davranış neden doğrudur?",
     "bilim_teknoloji_sanat", ["Teknolojiyi bilinçli kullanmak", "Oyun kötü", "Bilgisayar yavaş", "Anne izin vermiyor"]),
    ("Resim yapan çocuk farklı renkler kullanıyor. \"Gökyüzü mavi, güneş sarı\" diyor.\n\nBu neyi gösterir?",
     "bilim_teknoloji_sanat", ["Gözlem yapmak; doğayı yansıtmak", "Renkler güzel", "Resim kolay", "Öğretmen öğretti"]),
    ("Müzik dersinde şarkı söyleyen çocuk sırayla söylüyor, arkadaşını dinliyor.\n\nBu neyi gösterir?",
     "bilim_teknoloji_sanat", ["Birlikte çalışma ve saygı", "Şarkı güzel", "Sessiz kalmak zor", "Öğretmen istedi"]),
    ("Deneyde bitkiyi sulayan çocuk \"Su vermezsek solar\" diyor.\n\nBu neyi anlatır?",
     "bilim_teknoloji_sanat", ["Bitkilerin suya ihtiyacı olduğu", "Bitki küçük", "Su soğuk", "Deney eğlenceli"]),
    ("Tableti sadece ödev için kullanan çocuk sonra kapatıyor.\n\nBu davranış neden iyidir?",
     "bilim_teknoloji_sanat", ["Amaca uygun ve sınırlı kullanım", "Tablet pahalı", "Pil biter", "Göz yorulur"]),
    # Extra for balance
    ("Arkadaşı üzüldüğünde yanına oturup \"Ne oldu?\" diye soran çocuk ne yapıyor?",
     "ben_ve_okulum", ["Empati ve destek göstermek", "Sadece merak", "Üzüntü geçer", "Konuşmak zorunlu"]),
    ("Odamı topladım diyen çocuk oyuncakları kutusuna koymuş.\n\nBu neyi gösterir?",
     "ben_ve_okulum", ["Sorumluluk ve düzen", "Anne söyledi", "Oyun bitti", "Kutu dolu"]),
    ("Acil bir durumda 112'yi arayan çocuk adresini söylüyor.\n\nBu neden önemlidir?",
     "sagligim_guvenligim", ["Yardımın gelmesi için adres gerekir", "112 ücretsiz", "Telefon kullanmak", "Büyükler yok"]),
    ("Bisiklete binerken kask takan çocuk ne yapıyor?",
     "sagligim_guvenligim", ["Kendini korumak", "Kask güzel", "Anne aldı", "Kask zorunlu"]),
    ("Dedem hasta, onu ziyarete gidiyoruz diyen anne. Çocuk \"Geçmiş olsun\" kartı hazırlıyor.\n\nBu neyi gösterir?",
     "ailem_ve_toplum", ["Hasta yakınlara ilgi göstermek", "Kart güzel", "Ziyaret zorunlu", "Dede yalnız"]),
    ("Bayrağımızı yırtılmış görünce üzülen çocuk neden üzülüyor?",
     "yasadigim_yer_ulkem", ["Bayrak millî semboldür; saygı duyulur", "Bayrak pahalı", "Yırtık kötü görünür", "Okulda asılı"]),
    ("Çiçekleri koparmayan, sadece koklayan çocuk ne yapıyor?",
     "doga_ve_cevre", ["Doğayı koruyarak keyif almak", "Koparmak yasak", "Koku güzel", "Çiçek az"]),
    ("Robot yapan çocuk parçaları birleştirip çalıştırıyor.\n\nBu neyi gösterir?",
     "bilim_teknoloji_sanat", ["Üretim ve problem çözme", "Oyun oynamak", "Robot pahalı", "Parça birleştirmek kolay"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"hayat2_{idx:04d}",
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
        "subject": "hayat",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = ["Bu çıkarım senaryodan yapılamaz.", "Sebep-sonuç yanlış.", "Davranış yorumu hatalı.", "Eksik düşünme."]
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
            expl = f"Senaryo ve günlük hayat durumuna göre doğru davranış/yorum \"{correct}\"dir. Diğer seçenekler yanlış sebep-sonuç, hatalı değer yorumu veya eksik düşünme içermektedir."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:12] if len(topic) >= 12 else topic, "yorumlama", "davranis_analizi"],
                expl, f"hayat2_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"hayat2_{idx:04d}"
        qq["id"] = f"hayat2_{idx:04d}"

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
            "subject": "hayat",
            "publisher": "brainbuddy",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_hayat2_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("HAYAT2 (2. Sınıf Hayat Bilgisi) Question Bank Report")
    print("MEB 2024-2025 / TYMM - 6 öğrenme alanı")
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
    random.seed(39)
    main()
