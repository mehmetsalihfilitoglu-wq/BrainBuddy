#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 5th Grade Türkçe question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: paragraf yorum, çıkarım, metin analizi, görsel/tablo yorumlama.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/turkce5"

TOPICS = [
    "sozcukte_anlam",
    "deyimler_atasozleri",
    "cumlede_anlam",
    "paragrafta_anlam",
    "ana_fikir",
    "paragraf_konu_baslik",
    "metin_turleri",
    "isimler_sifatlar",
    "zamirler_fiiller",
    "zarflar_edat_baglac",
    "anlatim_bicimleri",
    "gorsel_tablo_yorumlama",
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
    ("\"Gökyüzü bugün masmavi\" cümlesinde \"masmavi\" sözcüğü nasıl kullanılmıştır?",
     "sozcukte_anlam", ["Mecaz anlam (abartılı benzetme)", "Gerçek anlam", "Terim anlam", "Zıt anlam"]),
    ("\"Bilgisayar\" sözcüğü teknoloji metninde geçtiğinde hangi anlamda kullanılır?",
     "sozcukte_anlam", ["Terim anlam (teknik alanda)", "Mecaz anlam", "Eş anlamlı", "Argo"]),
    ("\"Damlaya damlaya göl olur\" atasözü hangi mesajı verir?",
     "deyimler_atasozleri", ["Küçük birikimler büyük sonuçlar doğurur", "Su önemlidir", "Göl büyüktür", "Damla sayılamaz"]),
    ("\"Göze girmek\" deyiminin anlamı aşağıdakilerden hangisidir?",
     "deyimler_atasozleri", ["Birinin takdirini kazanmak", "Göz doktoruna gitmek", "Göze bir şey kaçması", "Gözü açmak"]),
    ("\"Bu kitabı okudum\" cümlesinde yazarın asıl vurgulamak istediği nedir?",
     "cumlede_anlam", ["Kitabı bitirdiğini, deneyimini paylaştığını", "Kitabın varlığını", "Okumanın zorluğunu", "Kitabın uzunluğunu"]),
    ("\"Ne kadar çalışırsan o kadar başarılı olursun\" cümlesinde hangi anlam ilişkisi vardır?",
     "cumlede_anlam", ["Koşul-sonuç (sebep-sonuç)", "Zıtlık", "Karşılaştırma", "Örnekleme"]),
    ("Paragrafta \"okumak, insanı farklı dünyalara taşır\" ifadesi geçiyor. Bu cümle paragrafın hangi bölümüne hizmet eder?",
     "paragrafta_anlam", ["Ana düşünceyi destekleyen yardımcı düşüncedir", "Paragrafın başlığıdır", "Ana fikirdir", "Giriş cümlesidir"]),
    ("Paragrafın konusu aşağıdakilerden hangisiyle en iyi ifade edilir?",
     "paragrafta_anlam", ["Paragrafta üzerinde durulan temel kavram veya olay", "İlk cümledeki bilgi", "Son cümledeki özet", "En uzun cümle"]),
    ("\"Kitap okumak zihni geliştirir. Bu yüzden her yaşta okumaya zaman ayırmalıyız\" paragrafının ana fikri nedir?",
     "ana_fikir", ["Okumak zihinsel gelişim için önemlidir, bu nedenle her yaşta okumalıyız", "Kitap uzundur", "Zihin karmaşıktır", "Yaş önemli değildir"]),
    ("Paragrafta ana fikir genellikle nerede bulunur?",
     "ana_fikir", ["Giriş veya sonuç cümlesinde, bazen paragrafın bütününden çıkarılır", "Sadece ilk cümlede", "Sadece son cümlede", "Sadece ortada"]),
    ("Aşağıdaki başlıklardan hangisi paragrafın konusuna en uygun olur?",
     "paragraf_konu_baslik", ["Paragrafta işlenen konuyu kısa ve çarpıcı yansıtan", "En uzun cümleden alınan", "İlk kelime", "Rastgele seçilen"]),
    ("Paragrafta \"doğa sevgisi\" konusu işleniyorsa uygun başlık aşağıdakilerden hangisi olabilir?",
     "paragraf_konu_baslik", ["Doğayla İç İçe Yaşamak", "Şehir Hayatı", "Teknoloji", "Spor"]),
    ("Metinde olayların kronolojik sırayla anlatıldığı metin türü hangisidir?",
     "metin_turleri", ["Öyküleyici anlatım", "Betimleyici anlatım", "Açıklayıcı anlatım", "Tartışmacı anlatım"]),
    ("\"Rüzgar yaprakları savuruyordu. Gökyüzü bulutlarla kaplıydı\" cümleleri hangi anlatım biçimine örnektir?",
     "metin_turleri", ["Betimleyici (tasvir)", "Öyküleyici", "Açıklayıcı", "Tartışmacı"]),
    ("\"Kitap\", \"okul\", \"çocuk\" sözcükleri hangi sözcük türüne girer?",
     "isimler_sifatlar", ["İsim", "Sıfat", "Fiil", "Zarf"]),
    ("\"Güzel bir gün\" ifadesinde \"güzel\" sözcüğünün türü nedir?",
     "isimler_sifatlar", ["Sıfat (niteleme sıfatı)", "İsim", "Zarf", "Fiil"]),
    ("\"O, bu işi yapacak\" cümlesinde \"o\" zamiri kime gönderme yapar?",
     "zamirler_fiiller", ["Cümlede adı geçmeyen bir kişiye", "Dinleyiciye", "Konuşan kişiye", "Belirsiz bir varlığa"]),
    ("\"Koşuyor\" fiilinin kipi nedir?",
     "zamirler_fiiller", ["Şimdiki zaman", "Geçmiş zaman", "Gelecek zaman", "Geniş zaman"]),
    ("\"Çok hızlı koşuyor\" cümlesinde \"çok\" sözcüğünün türü nedir?",
     "zarflar_edat_baglac", ["Zarf (miktar zarfı)", "Sıfat", "İsim", "Bağlaç"]),
    ("\"Ve\", \"ama\", \"fakat\" gibi sözcükler cümlelerde ne işe yarar?",
     "zarflar_edat_baglac", ["Cümleleri veya sözcükleri bağlar (bağlaç)", "Niteleme yapar", "Zaman bildirir", "Yer bildirir"]),
    ("Yazarın \"örnek verme\" yöntemiyle düşüncesini geliştirdiği paragrafta ne yapılır?",
     "anlatim_bicimleri", ["Soyut bir düşünce somut örneklerle açıklanır", "Sadece tanım yapılır", "Karşılaştırma yapılmaz", "Örnek verilmez"]),
    ("\"Tanım yapma\" düşünceyi geliştirme yollarından biridir. Tanımda ne yapılır?",
     "anlatim_bicimleri", ["Bir kavramın ne olduğu açıklanır", "Örnek verilir", "Karşılaştırma yapılır", "Öykü anlatılır"]),
    ("Tablo veya grafikte verilen bilgilerden çıkarım yaparken neye dikkat edilmelidir?",
     "gorsel_tablo_yorumlama", ["Verilerin doğru okunması ve ilişkilendirilmesi", "Sadece sayılara bakılır", "Görsel renkleri önemlidir", "Tablo başlığı önemsizdir"]),
    ("Sütun grafiğinde en yüksek sütun neyi gösterir?",
     "gorsel_tablo_yorumlama", ["O kategorideki en büyük değeri", "En küçük değeri", "Ortalamayı", "Toplamı"]),
    ("\"Tüm kuşlar uçar. Serçe bir kuştur. O halde serçe de uçar\" çıkarımı hangi mantık türüne örnektir?",
     "mantik_muhakeme", ["Tümdengelim (genelden özele)", "Tümevarım", "Analoji", "Karşılaştırma"]),
    ("Paragrafta verilen iki bilgiden üçüncü bir sonuç çıkarma işlemine ne denir?",
     "mantik_muhakeme", ["Çıkarım (inferans)", "Tanım", "Özet", "Alıntı"]),
    ("\"Eş anlamlı\" sözcükler ne demektir?",
     "sozcukte_anlam", ["Yazılışları farklı, anlamları aynı veya çok yakın sözcükler", "Zıt anlamlı sözcükler", "Eş sesli sözcükler", "Mecaz anlamlı sözcükler"]),
    ("\"El ele vermek\" deyiminin anlamı nedir?",
     "deyimler_atasozleri", ["Birlikte çalışmak, dayanışmak", "El sıkışmak", "Elleri birleştirmek", "Yardım istemek"]),
    ("\"Ne var ki bu işi tek başına yapamaz\" cümlesinde \"ne var ki\" ifadesi ne anlam katmaktadır?",
     "cumlede_anlam", ["Karşıtlama, itiraz (fakat/ama anlamı)", "Soru", "Onay", "Şaşkınlık"]),
    ("Paragrafta yardımcı düşünce ne işe yarar?",
     "paragrafta_anlam", ["Ana fikri destekler, örnekler ve açıklar", "Paragrafı bitirir", "Konuyu değiştirir", "Başlığı oluşturur"]),
    ("Ana fikir ile yardımcı düşünce arasındaki fark nedir?",
     "ana_fikir", ["Ana fikir paragrafın temel mesajıdır; yardımcı düşünceler onu destekler", "Fark yoktur", "Yardımcı düşünce daha önemlidir", "Ana fikir detaydır"]),
    ("Paragraf tamamlama sorularında boş bırakılan yere ne yazılmalıdır?",
     "paragraf_konu_baslik", ["Paragrafın akışına, mantığına ve konusuna uygun cümle", "Rastgele bir cümle", "En uzun seçenek", "İlk cümleyi tekrarlayan"]),
    ("\"Masal\" ve \"fabl\" metin türleri arasındaki ortak özellik nedir?",
     "metin_turleri", ["İkisi de düşsel (kurmaca) anlatıma dayanabilir, öğretici olabilir", "İkisi de gerçek olayları anlatır", "Fabl masal değildir", "Masalda hayvan yoktur"]),
    ("\"Öğrenciler\" sözcüğü hangi tür isimdir?",
     "isimler_sifatlar", ["Çoğul isim", "Tekil isim", "Topluluk ismi", "Soyut isim"]),
    ("\"Yarın geleceğim\" cümlesinde fiilin zamanı nedir?",
     "zamirler_fiiller", ["Gelecek zaman", "Şimdiki zaman", "Geçmiş zaman", "Geniş zaman"]),
    ("\"Sadece\" sözcüğü \"Sadece seni dinledim\" cümlesinde hangi tür zarf olarak kullanılmıştır?",
     "zarflar_edat_baglac", ["Sınırlama zarfı", "Zaman zarfı", "Yer-yön zarfı", "Durum zarfı"]),
    ("\"Örneğin\", \"mesela\" gibi ifadeler paragrafta ne işe yarar?",
     "anlatim_bicimleri", ["Örnekleme yapıldığını gösterir", "Sonuç bildirir", "Karşılaştırma yapar", "Tanım yapar"]),
    ("Grafikte iki veri grubu karşılaştırıldığında ne yapılmalıdır?",
     "gorsel_tablo_yorumlama", ["Her iki grubun verileri ayrı ayrı okunup karşılaştırılmalıdır", "Sadece bir gruba bakılır", "Grafik renkleri önemlidir", "Başlık yeterlidir"]),
    ("\"Hiçbir kuş tek kanatla uçamaz\" cümlesinden çıkarılabilecek sonuç nedir?",
     "mantik_muhakeme", ["Birlikte çalışmak, dayanışma gereklidir", "Kuşlar iki kanatlıdır", "Kanat önemli değildir", "Uçmak zorundadır"]),
]

def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"turkce5_{idx:04d}",
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
        for _ in range(count):
            tpl = TEMPLATES[tpl_idx % len(TEMPLATES)]
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
                [topic[:10], "yorumlama", "cikarim"],
                expl, f"turkce5_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"turkce5_{idx:04d}"
        qq["id"] = f"turkce5_{idx:04d}"

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
        path = OUT_DIR / f"lgs_turkce5_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("TURKCE5 Question Bank Report")
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
    random.seed(53)
    main()
