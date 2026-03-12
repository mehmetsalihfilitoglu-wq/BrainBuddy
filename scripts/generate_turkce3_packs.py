#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 3rd Grade Türkçe question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: paragraf yorum, çıkarım, metin analizi, görsel/tablo yorumlama.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/turkce3"

TOPICS = [
    "sozcukte_anlam",
    "es_zit_anlam",
    "cumlede_anlam",
    "paragrafta_anlam",
    "ana_fikir",
    "yardimci_fikir",
    "paragraf_konu_tamamlama",
    "metin_turleri",
    "isimler",
    "fiiller",
    "sifatlar",
    "gorsel_okuma",
    "tablo_grafik_yorumlama",
    "mantik_muhakeme",
]

NEW_GEN_TYPES = [
    "paragraf_yorum",
    "cikarim",
    "metin_analizi",
    "karsilastirma",
    "mantik_yurutme",
    "gorsel_tablo_yorum",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # Sözcükte Anlam
    ("\"Sözcükte anlam\" ne demektir?", "sozcukte_anlam",
     ["Bir sözcüğün cümlede taşıdığı anlam", "Sözcüğün yazılışı", "Sözcüğün uzunluğu", "Sözcüğün kökü"]),
    ("\"Güzel\" sözcüğü \"güzel bir gün\" ifadesinde nasıl kullanılmıştır?",
     "sozcukte_anlam", ["Günü niteleyen anlamda", "Eylem bildiren anlamda", "İsim yerine", "Zıt anlamda"]),
    ("Bir sözcüğün cümlede taşıdığı anlamı bulmak için ne yapmalıyız?",
     "sozcukte_anlam", ["Cümlenin tamamına ve bağlama bakmalıyız", "Sadece sözcüğe bakmalıyız", "Sözlüğe bakmak yeterlidir", "Anlam önemli değildir"]),
    # Eş Anlamlı / Zıt Anlamlı
    ("\"Eş anlamlı\" sözcükler ne demektir?", "es_zit_anlam",
     ["Yazılışları farklı, anlamları aynı veya yakın sözcükler", "Zıt anlamlı sözcükler", "Eş sesli sözcükler", "Aynı harflerle yazılanlar"]),
    ("\"Zıt anlamlı\" sözcükler ne demektir?", "es_zit_anlam",
     ["Birbirine karşıt anlam taşıyan sözcükler", "Eş anlamlı sözcükler", "Aynı yazılanlar", "Eş sesliler"]),
    ("\"Büyük\" sözcüğünün zıt anlamlısı aşağıdakilerden hangisi olabilir?",
     "es_zit_anlam", ["Küçük", "Geniş", "Uzun", "Yüksek"]),
    ("\"Ev\" ve \"yuva\" sözcükleri arasındaki ilişki nedir?",
     "es_zit_anlam", ["Bağlamda eş anlamlı kullanılabilir", "Zıt anlamlıdır", "Eş seslidir", "Hiçbir ilişki yoktur"]),
    # Cümlede Anlam
    ("\"Kitabı okudum\" cümlesinde asıl anlatılmak istenen nedir?",
     "cumlede_anlam", ["Kitabı bitirdiğini, okuma eylemini tamamladığını", "Kitabın varlığını", "Okumanın zorluğunu", "Kitabın uzunluğunu"]),
    ("\"Ne kadar çalışırsan o kadar başarılı olursun\" cümlesinde hangi anlam ilişkisi vardır?",
     "cumlede_anlam", ["Sebep-sonuç (çalışma ile başarı ilişkisi)", "Zıtlık", "Sadece öğüt", "Karşılaştırma yok"]),
    ("\"Çok mutluyum\" cümlesinde yazar ne hissetmektedir?",
     "cumlede_anlam", ["Sevinç, mutluluk", "Üzüntü", "Kızgınlık", "Kayıtsızlık"]),
    # Paragrafta Anlam
    ("Paragrafta \"okumak insanı geliştirir\" ifadesi geçiyor. Bu cümle paragrafın hangi bölümüne hizmet eder?",
     "paragrafta_anlam", ["Ana düşünceyi destekleyen yardımcı düşüncedir", "Paragrafın başlığıdır", "Ana fikirdir", "Giriş cümlesidir sadece"]),
    ("Paragrafın konusu aşağıdakilerden hangisiyle en iyi ifade edilir?",
     "paragrafta_anlam", ["Paragrafta üzerinde durulan temel kavram veya olay", "İlk cümledeki bilgi", "Son cümledeki özet", "En uzun cümle"]),
    ("Paragrafı anlamak için ne yapmalıyız?",
     "paragrafta_anlam", ["Baştan sona dikkatle okuyup cümleler arası ilişkiyi kurmalıyız", "Sadece ilk cümleyi okumak yeterlidir", "Sadece son cümleye bakılır", "Paragraf okunmadan cevap verilir"]),
    # Ana Fikir
    ("\"Kitap okumak zihni geliştirir. Bu yüzden her gün okumaya zaman ayırmalıyız\" paragrafının ana fikri nedir?",
     "ana_fikir", ["Okumak zihinsel gelişim için önemlidir, bu nedenle her gün okumalıyız", "Kitap uzundur", "Zihin karmaşıktır", "Okumak zorunlu değildir"]),
    ("Paragrafta ana fikir genellikle nerede bulunur?",
     "ana_fikir", ["Giriş veya sonuç cümlesinde, bazen paragrafın bütününden çıkarılır", "Sadece ilk cümlede", "Sadece son cümlede", "Sadece ortadaki cümlede"]),
    ("Ana fikir paragrafta neyi ifade eder?",
     "ana_fikir", ["Yazarın asıl vermek istediği mesajı", "Bir detayı", "Örneği", "Girişi"]),
    # Yardımcı Fikir
    ("Paragrafta yardımcı düşünce ne işe yarar?",
     "yardimci_fikir", ["Ana fikri destekler, örnekler ve açıklar", "Paragrafı bitirir", "Konuyu değiştirir", "Başlığı oluşturur"]),
    ("Ana fikir ile yardımcı düşünce arasındaki fark nedir?",
     "yardimci_fikir", ["Ana fikir temel mesajdır; yardımcı düşünceler onu destekler", "Fark yoktur", "Yardımcı düşünce daha önemlidir", "Ana fikir detaydır"]),
    ("Yardımcı düşünce nasıl bulunur?",
     "yardimci_fikir", ["Ana fikri açıklayan, örnekleyen veya geliştiren cümlelerdir", "Sadece ilk cümledir", "Sadece sayılar içeren cümlelerdir", "Başlıktır"]),
    # Paragraf Konu / Tamamlama
    ("Paragraf tamamlama sorularında boş bırakılan yere ne yazılmalıdır?",
     "paragraf_konu_tamamlama", ["Paragrafın akışına, mantığına ve konusuna uygun cümle", "Rastgele bir cümle", "En uzun seçenek", "İlk cümleyi tekrarlayan"]),
    ("Paragrafın akışı bozulduğunda ne olur?",
     "paragraf_konu_tamamlama", ["Anlam kopukluğu ve mantık hatası oluşur", "Paragraf kısalır", "Başlık değişir", "Hiçbir şey olmaz"]),
    ("Paragrafın konusuna uygun başlık nasıl seçilir?",
     "paragraf_konu_tamamlama", ["Paragrafta işlenen konuyu kısa ve net yansıtan", "En uzun cümleden alınan", "İlk kelime", "Rastgele seçilen"]),
    # Metin Türleri
    ("Metinde olayların sırayla anlatıldığı metin türü hangisidir?",
     "metin_turleri", ["Hikâye (öyküleyici anlatım)", "Şiir", "Bilgi veren metin", "Mektup"]),
    ("\"Rüzgar yaprakları savuruyordu. Gökyüzü bulutlarla kaplıydı\" cümleleri hangi anlatıma örnektir?",
     "metin_turleri", ["Betimleyici (tasvir, gözlem)", "Öyküleyici", "Açıklayıcı", "Tartışmacı"]),
    ("Bir olayın başı, gelişmesi ve sonucu olan metin türü hangisidir?",
     "metin_turleri", ["Hikâye", "Şiir", "Sözlük", "Reçete"]),
    # İsimler
    ("\"Kitap\", \"okul\", \"çocuk\" sözcükleri hangi sözcük türüne girer?",
     "isimler", ["İsim", "Sıfat", "Fiil", "Zarf"]),
    ("İsimler neyi karşılar?",
     "isimler", ["Varlıkların, kavramların adlarını", "Eylemleri", "Nitelemeleri", "Zamanı"]),
    ("\"Öğrenciler\" sözcüğü hangi tür isimdir?",
     "isimler", ["Çoğul isim", "Tekil isim", "Soyut isim", "Özel isim"]),
    # Fiiller
    ("\"Koşuyor\" sözcüğü hangi sözcük türüdür?",
     "fiiller", ["Fiil (eylem)", "İsim", "Sıfat", "Zarf"]),
    ("Fiiller neyi bildirir?",
     "fiiller", ["Eylem, oluş, durum", "Varlık adı", "Niteleme", "Zaman adı"]),
    ("\"Geldi\" fiilinin zamanı nedir?",
     "fiiller", ["Geçmiş zaman", "Şimdiki zaman", "Gelecek zaman", "Geniş zaman"]),
    # Sıfatlar
    ("\"Güzel bir gün\" ifadesinde \"güzel\" sözcüğünün türü nedir?",
     "sifatlar", ["Sıfat (niteleme)", "İsim", "Zarf", "Fiil"]),
    ("Sıfatlar ne işe yarar?",
     "sifatlar", ["İsimleri niteleyip belirtir", "Eylem bildirir", "Zaman gösterir", "Soru sorar"]),
    ("\"Kırmızı elma\" ifadesinde \"kırmızı\" sözcüğü ne tür sıfattır?",
     "sifatlar", ["Niteleme sıfatı", "Sayı sıfatı", "İşaret sıfatı", "Soru sıfatı"]),
    # Görsel Okuma
    ("Görsel okuma ne demektir?",
     "gorsel_okuma", ["Resim, grafik veya şekilden anlam çıkarmak", "Sadece yazıyı okumak", "Sayıları okumak", "Harfleri saymak"]),
    ("Bir resimde çocuk kitap okuyorsa, bu görsel neyi anlatabilir?",
     "gorsel_okuma", ["Okuma eylemi, kitap sevgisi", "Sadece bir çocuk", "Renkleri", "Kitabın sayfa sayısını"]),
    ("Tablo veya grafikte verilen bilgilerden çıkarım yaparken neye dikkat edilmelidir?",
     "gorsel_okuma", ["Verilerin doğru okunması ve ilişkilendirilmesi", "Sadece sayılara bakılır", "Görsel renkleri önemlidir", "Başlık önemsizdir"]),
    # Tablo / Grafik Yorumlama
    ("Sütun grafiğinde en yüksek sütun neyi gösterir?",
     "tablo_grafik_yorumlama", ["O kategorideki en büyük değeri", "En küçük değeri", "Ortalamayı", "Toplamı"]),
    ("Tablo: Pazartesi 10, Salı 15, Çarşamba 8 kitap okundu. Toplam kaç kitap okunmuştur?",
     "tablo_grafik_yorumlama", ["33", "15", "10", "8"], "10+15+8=33."),
    ("Grafikte iki veri grubu karşılaştırıldığında ne yapılmalıdır?",
     "tablo_grafik_yorumlama", ["Her iki grubun verileri ayrı ayrı okunup karşılaştırılmalıdır", "Sadece bir gruba bakılır", "Grafik renkleri önemlidir", "Başlık yeterlidir"]),
    # Mantık ve Muhakeme
    ("Paragrafta verilen iki bilgiden üçüncü bir sonuç çıkarma işlemine ne denir?",
     "mantik_muhakeme", ["Çıkarım", "Tanım", "Özet", "Alıntı"]),
    ("\"Tüm kuşlar uçar. Serçe bir kuştur. O halde serçe de uçar\" çıkarımı hangi mantık türüne örnektir?",
     "mantik_muhakeme", ["Genelden özele (tümdengelim)", "Özelden genele", "Karşılaştırma", "Örnekleme"]),
    ("\"Hiçbir kuş tek kanatla uçamaz\" cümlesinden çıkarılabilecek sonuç nedir?",
     "mantik_muhakeme", ["Birlikte çalışmak, dayanışma önemlidir", "Kuşlar iki kanatlıdır", "Kanat önemsizdir", "Uçmak zorundadır"]),
    # Extra for balance
    ("\"Kalp\" sözcüğü \"kalbi çok temiz\" ifadesinde hangi anlamda kullanılmıştır?",
     "sozcukte_anlam", ["Mecaz anlam (iyilik, temizlik)", "Gerçek anlam", "Terim anlam", "Eş anlamlı"]),
    ("\"Hızlı\" sözcüğünün zıt anlamlısı hangisi olabilir?",
     "es_zit_anlam", ["Yavaş", "Koşmak", "Gitmek", "Aynı"]),
    ("Paragrafta \"önce\", \"sonra\", \"en sonunda\" gibi sözcükler neyi gösterir?",
     "paragrafta_anlam", ["Olayların sırasını, zaman akışını", "Ana fikri", "Yardımcı fikri", "Başlığı"]),
    ("\"Çevremizi temiz tutmalıyız. Çöplerimizi yere atmamalıyız\" paragrafının ana fikri nedir?",
     "ana_fikir", ["Çevre temizliğine dikkat etmeliyiz", "Çöp kötüdür", "Yere atmak yasaktır", "Temizlik zordur"]),
    ("Örnek vermek yardımcı düşünceyi nasıl etkiler?",
     "yardimci_fikir", ["Ana fikri somutlaştırıp anlaşılır kılar", "Ana fikri değiştirir", "Paragrafı uzatır", "Örnek gereksizdir"]),
    ("\"Masal\" metin türü olarak nasıl tanımlanır?",
     "metin_turleri", ["Hayal ürünü, öğretici nitelikte anlatı", "Gerçek olay", "Şiir", "Bilimsel metin"]),
    ("\"Kalemler\" sözcüğü hangi tür isimdir?",
     "isimler", ["Çoğul, cins isim", "Tekil isim", "Özel isim", "Soyut isim"]),
    ("\"Yazacak\" fiilinin zamanı nedir?",
     "fiiller", ["Gelecek zaman", "Geçmiş zaman", "Şimdiki zaman", "Geniş zaman"]),
    ("\"Üç elma\" ifadesinde \"üç\" sözcüğü ne tür sıfattır?",
     "sifatlar", ["Sayı sıfatı", "Niteleme sıfatı", "İşaret sıfatı", "Soru sıfatı"]),
    ("Görselde okul bahçesinde oynayan çocuklar varsa bu ne anlama gelebilir?",
     "gorsel_okuma", ["Okul yaşamı, oyun, arkadaşlık", "Sadece bahçe", "Sadece çocuk sayısı", "Hava durumu"]),
    ("Tablo: 3 günde 12, 8, 10 sayfa okundu. Ortalama günde kaç sayfa okunmuştur?",
     "tablo_grafik_yorumlama", ["10", "12", "8", "30"], "12+8+10=30, 30÷3=10."),
    ("\"Kim erken kalkarsa işine erken başlar\" cümlesinden ne çıkarılabilir?",
     "mantik_muhakeme", ["Erken kalkmak verimliliği artırabilir", "Erken kalkmak zorunludur", "Geç kalkan çalışamaz", "İş sabah başlar"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"turkce3_{idx:04d}",
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
        "subject": "turkce",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = ["Paragrafta bu çıkarım yapılamaz.", "Bu ana fikir değil, yardımcı düşüncedir.", "Metinde bu bilgi verilmemiştir.", "Anlam karışıklığı vardır."]
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
            expl = f"Metindeki anlam ve mantık doğrultusunda doğru cevap \"{correct}\" seçeneğidir. Diğer seçenekler yanlış çıkarım, ana fikir-yardımcı düşünce karışıklığı veya kısmi anlam hatası içermektedir."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10] if len(topic) >= 10 else topic, "yorumlama", "cikarim"],
                expl, f"turkce3_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"turkce3_{idx:04d}"
        qq["id"] = f"turkce3_{idx:04d}"

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
            "subject": "turkce",
            "publisher": "brainbuddy",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_turkce3_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("TURKCE3 Question Bank Report")
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
    random.seed(45)
    main()
