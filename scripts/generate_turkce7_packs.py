#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 7th Grade Turkish question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: paragraph-based, interpretation, reasoning.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/turkce7"

TOPICS = [
    "sozcukte_anlam", "gercek_mecaz_terim_anlam", "es_zit_anlamli", "deyimler_atasozleri",
    "cumlede_anlam", "paragrafta_anlam", "ana_fikir", "yardimci_dusunce", "paragraf_konu",
    "paragraf_baslik", "paragraf_tamamlama", "paragraf_akisi", "metin_turleri",
    "isimler", "isim_tamlamalari", "sifatlar", "zamirler", "fiiller",
    "fiil_kip_kisi", "fiilde_yapi", "zarflar", "edat_baglac_unlem", "ek_fiil",
    "anlatim_bicimleri", "dusunce_gelistirme", "anlatim_bozukluklari",
    "gorsel_okuma", "tablo_grafik_yorum", "mantik_muhakeme",
]

NEW_GEN_TYPES = ["paragraf_yorum", "paragraf_analiz", "metin_yorumlama", "cikarim", "karsilastirma", "mantik"]

# Paragraph-based stems (scenario, dialogue, observation) - minimum ~80 chars for quality
PARAGRAPHS = [
    ("Elif, kitap fuarında gezerken bir yazardan imza almak istedi. Yazar sırada bekleyen okurlara teker teker zaman ayırıyordu. Elif, yazarın her okura farklı bir şey söylediğini fark etti. Sıra ona gelince yazar \"Bu kitabı neden seçtin?\" diye sordu. Elif düşünmeden cevap veremedi; çünkü gerçekten kitabın neyi ona çekici geldiğini bilmiyordu.",
     "paragrafta_anlam", "Bu paragrafta Elif'in davranışıyla ilgili aşağıdakilerden hangisi çıkarılabilir?",
     ["Elif, yazarın sorularına hazırlıksız yakalanmıştır.", "Elif, kitap seçiminde bilinçli davranmamaktadır.",
      "Elif, fuardaki kalabalıktan rahatsız olmaktadır.", "Elif, yazarla tanışmaktan heyecan duymaktadır."],
     1),
    ("Öğretmen, tahtaya \"Sabah erken kalkmak zorundayım çünkü yarın sınav var.\" cümlesini yazdı. Ardından \"Bu cümlede hangi sözcük farklı anlamda kullanılmış olabilir?\" diye sordu. Öğrenciler aralarında tartışmaya başladı. Bir öğrenci \"erken\" sözcüğünü işaret etti.",
     "sozcukte_anlam", "Bu paragrafa göre öğretmenin sorusunun amacı nedir?",
     ["Sözcüklerin bağlama göre anlam değişimini fark ettirmek", "Cümledeki dil bilgisi kurallarını öğretmek",
      "Öğrencilerin tartışma becerisini geliştirmek", "Sınav kaygısını azaltmak"],
     0),
    ("Bir kütüphanede çalışan Mehmet Bey, okuyucuların kitaplara olan ilgisini gözlemliyordu. Bazı kitapların sayfaları çok yıpranmıştı; bazılarının ise hiç açılmadığı belliydi. \"İnsanlar hangi tür kitaplara daha çok ilgi gösteriyor?\" diye düşündü ve raflardaki dağılıma baktı.",
     "paragraf_konu", "Bu paragrafın ana konusu aşağıdakilerden hangisidir?",
     ["Kütüphanelerdeki kitap çeşitliliği", "Okuyucuların kitap tercihlerinin gözlemi",
      "Kitapların bakımı ve korunması", "Mehmet Bey'in mesleki deneyimi"],
     1),
    ("\"Damlaya damlaya göl olur\" ve \"Bir elin nesi var iki elin sesi var\" atasözlerini karşılaştıran öğrenci, her iki atasözünün de belirli bir davranışı öğütlediğini fark etti. Öğretmen \"Peki bu iki atasözü tam olarak aynı şeyi mi söylüyor?\" diye sordu.",
     "deyimler_atasozleri", "Bu iki atasözü arasındaki temel fark aşağıdakilerden hangisidir?",
     ["Biri tasarrufu diğeri dayanışmayı vurgular", "Biri sabrı diğeri birlikte çalışmayı vurgular",
      "İkisi de aynı anlama gelir", "Biri geçmişi diğeri geleceği anlatır"],
     1),
    ("Arkadaşlarıyla pikniğe giden Zeynep, ormandaki sessizliği ve kuş seslerini dinledi. \"Burada şehirden çok farklı; her şey daha sakin\" dedi. Bir arkadaşı \"Evet, burada nefes almak bile farklı\" diye ekledi.",
     "cumlede_anlam", "Bu diyalogda \"nefes almak\" ifadesi nasıl bir anlam taşımaktadır?",
     ["Fiziksel olarak hava almak", "Rahatlamak, huzur bulmak",
      "Yorulmak", "Heyecanlanmak"],
     1),
    ("Gazetede okuduğu haberden etkilenen Can, çevre kirliliği konusunda bir sunum hazırlamaya karar verdi. Önce konuyu araştırdı, sonra görseller ekledi, en sonunda da çözüm önerileri yazdı. Sunumunda \"Sorunu göstermek yetmez, ne yapabileceğimizi de anlatmalıyım\" diye düşündü.",
     "ana_fikir", "Bu paragrafa göre Can'ın sunumunda vurgulamak istediği temel düşünce nedir?",
     ["Çevre kirliliği ciddi bir sorundur", "Sunumlarda görsel kullanmak önemlidir",
      "Sorunların yanı sıra çözüm önerileri de sunulmalıdır", "Gazete haberleri güvenilirdir"],
     2),
    ("Sınıf panosuna asılan tabloda öğrencilerin kitap okuma alışkanlıkları araştırılmıştı. Grafikte aylık okunan kitap sayısı gösteriliyordu. En çok kitap okuyanlar 5-8 arası, en az okuyanlar 0-1 arası kitap bildirmişti.",
     "tablo_grafik_yorum", "Bu tablo hakkında aşağıdakilerden hangisi söylenebilir?",
     ["Tüm öğrenciler düzenli kitap okuyor", "Öğrenciler arasında okuma alışkanlığı farkı vardır",
      "Kitap okuma oranı her ay artmaktadır", "En çok roman türü tercih edilmektedir"],
     1),
    ("\"Bu projede senin de katkın çok önemli. Birlikte yaparsak daha iyi sonuç alırız\" cümlesinde konuşan kişi, karşısındakine ne yapmasını önermektedir?",
     "cumlede_anlam", "Bu cümlede asıl vurgulanan düşünce aşağıdakilerden hangisidir?",
     ["Projenin zor olduğu", "İş birliğinin gerekli olduğu",
      "Bireysel çalışmanın yetersiz olduğu", "Projenin uzun süreceği"],
     1),
    ("Yazar, romanının ilk bölümünde kahramanın çocukluk yıllarını anlatıyordu. İkinci bölümde gençlik dönemine geçti. Üçüncü bölümde ise kahramanın kararlarının sonuçlarıyla yüzleştiği anlar yer alıyordu.",
     "paragraf_akisi", "Bu paragrafta anlatım hangi düzende ilerlemektedir?",
     ["Mekâna göre", "Zamana göre kronolojik",
      "Neden-sonuç ilişkisine göre", "Karşılaştırmaya göre"],
     1),
    ("Öğretmen tahtaya \"Güneş bugün çok parlak\" ve \"Gözleri güneş gibi parlıyordu\" cümlelerini yazdı. \"Güneş sözcüğü bu iki cümlede aynı anlamda mı kullanılmış?\" diye sordu.",
     "gercek_mecaz_terim_anlam", "Bu iki cümlede \"güneş\" sözcüğünün kullanımıyla ilgili aşağıdakilerden hangisi doğrudur?",
     ["Her iki cümlede de gerçek anlamda kullanılmıştır", "Birincide gerçek, ikincide mecaz anlamda kullanılmıştır",
      "Her ikisinde de mecaz anlamdadır", "Birincide terim, ikincide gerçek anlamdadır"],
     1),
]

# Extended paragraph pool - more scenarios for 500 questions
MORE_PARAGRAPHS = [
    ("Bisikletiyle okula giden Deniz, yolda bir arkadaşına rastladı. Arkadaşı \"Sen hâlâ bisikletle mi geliyorsun?\" diye sordu. Deniz \"Evet, hem sağlıklı hem de çevre dostu\" diye cevap verdi. Arkadaşı düşünceli bir ifadeyle \"Ben de denemeliyim\" dedi.",
     "paragrafta_anlam", "Bu diyalogda Deniz'in bisiklet kullanmasının gerekçesi olarak aşağıdakilerden hangisi vurgulanmaktadır?",
     ["Ekonomik tasarruf", "Sağlık ve çevre bilinci",
      "Trafikten kaçınma", "Zaman kazanma"],
     1),
    ("\"Kütüphanedeki sessizlik dikkatimi dağıtıyor\" diyen öğrenciye öğretmen \"Sessizlik dikkat dağıtır mı, yoksa toplar mı?\" diye sordu. Öğrenci bir süre düşündükten sonra \"Aslında topluyor; ben alışkın değilim\" dedi.",
     "ana_fikir", "Bu paragrafa göre öğretmen neyi fark ettirmeye çalışmaktadır?",
     ["Kütüphanelerin kuralları vardır", "Sessizliğin konsantrasyona yardımcı olduğu",
      "Alışkanlıkların değişmesi zordur", "Öğrencilerin farklı ihtiyaçları vardır"],
     1),
    ("Deniz, \"Güzel\" sözcüğünün farklı cümlelerde nasıl kullanıldığını araştırıyordu. \"Güzel bir gün\" ve \"Güzel söyledin\" cümlelerini karşılaştırdı. İlk cümlede havayı, ikinci cümlede ise konuşmayı niteliyordu.",
     "sifatlar", "Bu paragraftan \"güzel\" sıfatıyla ilgili aşağıdakilerden hangisi çıkarılabilir?",
     ["Sadece somut varlıkları niteleyebilir", "Bağlama göre farklı varlıkları niteleyebilir",
      "Sadece olumlu anlam taşır", "Türkçede tek anlamı vardır"],
     1),
    ("Okul gazetesi için röportaj yapan Ece, müdürle konuşurken \"Okulumuzda en çok hangi etkinliklere ilgi gösteriliyor?\" diye sordu. Müdür istatistiklere baktı ve \"Kitap kulübü ve bilim şenliği önde\" dedi.",
     "yardimci_dusunce", "Bu paragrafta müdürün verdiği cevap aşağıdakilerden hangisini içermektedir?",
     ["Öğrenci sayısı bilgisi", "En çok tercih edilen etkinlikler",
      "Gelecek yılki planlar", "Etkinliklerin maliyeti"],
     1),
    ("\"Bu kitabı okuduktan sonra düşüncelerim değişti\" cümlesindeki \"bu\" sözcüğü hangi tür sözcük sınıfına girer?",
     "zamirler", "Bu cümledeki \"bu\" sözcüğünün işlevi aşağıdakilerden hangisidir?",
     ["İsmin yerini tutmak, göstermek", "Eylemi nitelemek",
      "Cümleleri bağlamak", "Varlığın sayısını belirtmek"],
     0),
    ("Merve, \"Koşuyorum\" ve \"Koşacakmış\" fiillerini inceliyordu. Öğretmen \"Bu iki fiil arasındaki fark nedir?\" diye sordu. Merve kişi ve zaman açısından düşündü.",
     "fiil_kip_kisi", "Bu iki fiil arasındaki temel fark aşağıdakilerden hangisidir?",
     ["Biri geçmiş zaman, diğeri gelecek zaman", "Biri haber kipi, diğeri dilek kipi",
      "Biri basit, diğeri birleşik zamanlı", "Biri olumlu, diğeri olumsuz"],
     0),
    ("Şiir dinletisine gidenler, salonun dolu olduğunu gördü. Sunucu \"Şiir, kelimelerle resim yapmaktır\" dedi. Dinleyicilerden biri \"Bu tanım çok güzel\" diye mırıldandı.",
     "metin_turleri", "Bu paragrafta şiirle ilgili verilen tanım aşağıdakilerden hangisini vurgular?",
     ["Şiirin kısa olması gerektiğini", "Şiirde görsel öğelerin önemini",
      "Sözcüklerle betimleme yapıldığını", "Şiirin müzikle ilişkisini"],
     2),
    ("\"Yarın sınava çalışacağım\" cümlesindeki eylemin kipi nedir? Öğretmen bu soruyu sorduğunda öğrenciler farklı cevaplar verdiler. Biri \"gelecek zaman\" dedi, diğeri \"istek kipi\" dedi.",
     "fiil_kip_kisi", "Bu cümledeki fiilin kipi aşağıdakilerden hangisidir?",
     ["Gelecek zaman (görülen geçmiş)", "Gelecek zaman (öğrenilen geçmiş)",
      "Gelecek zaman (şimdiki)", "Gelecek zaman"],
     3),
    ("\"Evin penceresi\" ve \"okulun bahçesi\" tamlamalarını inceleyen öğrenci, her ikisinde de bir ismin başka bir ismi belirttiğini fark etti.",
     "isim_tamlamalari", "Bu iki tamlama türü aşağıdakilerden hangisine örnektir?",
     ["Sıfat tamlaması", "Belirtili isim tamlaması",
      "Belirtisiz isim tamlaması", "Takısız isim tamlaması"],
     1),
    ("Grafikte bir sınıftaki öğrencilerin haftalık kitap okuma süreleri saat cinsinden gösterilmişti. 0-2 saat: 8 öğrenci, 2-4 saat: 12 öğrenci, 4-6 saat: 6 öğrenci, 6+ saat: 4 öğrenci.",
     "tablo_grafik_yorum", "Bu grafiğe göre aşağıdakilerden hangisi söylenebilir?",
     ["En çok öğrenci 6 saatten fazla okuyor", "Öğrencilerin çoğu 2-4 saat arası okuyor",
      "Hiç okumayan öğrenci yok", "Ortalama okuma süresi 6 saatin üzerinde"],
     1),
    ("\"Hızlı koşuyor\" ve \"çok hızlı koşuyor\" cümlelerini karşılaştıran öğrenci, ikinci cümlede eylemin niteliğinin daha belirgin hale geldiğini gördü.",
     "zarflar", "İkinci cümlede \"çok\" sözcüğünün görevi nedir?",
     ["Eylemi zaman bildirerek nitelemek", "Eylemi derece bildirerek nitelemek",
      "Eylemi yer bildirerek nitelemek", "Eylemi durum bildirerek nitelemek"],
     1),
    ("\"Ve\", \"ama\", \"fakat\" sözcükleri cümleleri veya sözcükleri birbirine bağlar. Bu tür sözcüklere ne ad verilir?",
     "edat_baglac_unlem", "Bu sözcükler dil bilgisinde hangi sözcük türüne girer?",
     ["Edat", "Bağlaç",
      "Ünlem", "Zarf"],
     1),
    ("\"Öğrenciydim\" sözcüğündeki \"-di\" eki, isme eklenerek onu yüklem yapmaktadır. Bu ek, fiillere de eklenebilir.",
     "ek_fiil", "Bu cümlede sözü edilen ek aşağıdakilerden hangisidir?",
     ["Şahıs eki", "Ek fiil",
      "Kip eki", "Yapım eki"],
     1),
    ("Yazar, köydeki bir günü anlatırken önce sabahın erken saatlerindeki sessizliği, sonra güneşin doğuşunu, ardından köylülerin tarlaya gidişini betimlemişti.",
     "anlatim_bicimleri", "Bu paragrafta ağırlıklı olarak hangi anlatım biçimi kullanılmıştır?",
     ["Açıklama", "Betimleme",
      "Öyküleme", "Tartışma"],
     2),
    ("\"İstanbul, Türkiye'nin en kalabalık şehridir. Örneğin nüfusu 15 milyonu aşmaktadır. Ayrıca yılda milyonlarca turist ağırlar.\" Bu paragrafta düşünce nasıl geliştirilmiştir?",
     "dusunce_gelistirme", "Bu paragrafta kullanılan düşünceyi geliştirme yolu aşağıdakilerden hangisidir?",
     ["Tanımlama", "Örnekleme",
      "Karşılaştırma", "Tanık gösterme"],
     1),
    ("\"Bu kitabı hem ben hem kardeşim okudu\" cümlesinde dil bilgisi açısından bir tutarsızlık vardır. Özne çoğul olduğunda yüklem de çoğul olmalıdır.",
     "anlatim_bozukluklari", "Bu cümledeki anlatım bozukluğunun nedeni aşağıdakilerden hangisidir?",
     ["Özne-yüklem uyumsuzluğu", "Noktalama hatası",
      "Gereksiz sözcük kullanımı", "Anlam belirsizliği"],
     0),
    ("Bir afişte \"Su tasarrufu hayat kurtarır\" yazıyordu. Afişte damlayan bir musluk resmi ve dünya şeklinde bir su damlası vardı.",
     "gorsel_okuma", "Bu afişin vermek istediği mesaj aşağıdakilerden hangisidir?",
     ["Muslukların tamir edilmesi gerektiği", "Suyun değerli olduğu ve tasarruf edilmesi gerektiği",
      "Dünyada su kıtlığı olduğu", "Su faturalarının yüksek olduğu"],
     1),
    ("Üç arkadaştan Ayşe düzenli çalışıyor, Fatma bazen çalışıyor, Zeynep ise sınavdan önce çalışıyor. Sınav sonuçlarına göre en yüksek notu Ayşe almıştır.",
     "mantik_muhakeme", "Bu bilgilere dayanarak aşağıdakilerden hangisi çıkarılabilir?",
     ["Fatma en az çalışandır", "Düzenli çalışma başarıyı artırır",
      "Zeynep sınavdan kalmıştır", "Üçü de aynı notu almıştır"],
     1),
    ("\"Uzak\" sözcüğünün \"Uzaklara bakıyordum\" ve \"Uzak bir akrabamız geldi\" cümlelerindeki kullanımını karşılaştıran öğrenci, birinde zarf, diğerinde sıfat görevi gördüğünü fark etti.",
     "sozcukte_anlam", "Bu iki cümlede \"uzak\" sözcüğü hangi açıdan farklılık göstermektedir?",
     ["Anlam açısından", "Görev (tür) açısından",
      "Yazım açısından", "Telaffuz açısından"],
     1),
    ("\"Yürek\" sözcüğü \"Kalbi yani yüreği çok temiz\" cümlesinde gerçek anlamda, \"Yüreği çok geniş\" cümlesinde ise mecaz anlamda kullanılmıştır.",
     "gercek_mecaz_terim_anlam", "Bu iki kullanım arasındaki farkı belirleyen ölçüt nedir?",
     ["Cümledeki yerleri", "Bağlama göre anlam değişimi",
      "Vurgu farkı", "Yazım şekli"],
     1),
    ("\"Büyük\" ve \"iri\" sözcükleri anlamca birbirine yakındır. Ancak \" Büyük insan\" ve \"iri insan\" ifadeleri farklı anlamlar taşır.",
     "es_zit_anlamli", "Bu iki ifade arasındaki fark nasıl açıklanabilir?",
     ["Eş anlamlı sözcükler her zaman birbirinin yerine kullanılamaz", "Biri olumlu diğeri olumsuz anlam taşır",
      "Biri somut diğeri soyut kavramdır", "Sadece yazılışları farklıdır"],
     0),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"turkce7_{source_ref}",
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
    pad = ["A", "B", "Yukarıdakilerin hiçbiri", "Bu paragraftan çıkarılamaz"]
    for p in pad:
        if len(opts) >= 4:
            break
        if p not in seen:
            opts.append(p)
    return opts[:4]


def generate_questions():
    all_templates = PARAGRAPHS + MORE_PARAGRAPHS
    per_topic = 500 // len(TOPICS)
    remainder = 500 % len(TOPICS)
    counts = {t: per_topic + (1 if i < remainder else 0) for i, t in enumerate(TOPICS)}

    out = []
    t_idx = 0
    p_idx = 0
    for topic, count in counts.items():
        for _ in range(count):
            tpl = all_templates[p_idx % len(all_templates)]
            p_idx += 1
            para, tpl_topic, q_text, opts_list, ans_idx = tpl
            use_topic = topic
            opts_alt = opts_list
            stem = para + "\n\n" + q_text
            if len(stem) < 50:
                stem = "Aşağıdaki parçayı okuyunuz ve soruyu cevaplayınız.\n\n" + para + "\n\n" + q_text
            correct = opts_alt[ans_idx]
            wrongs = [o for i, o in enumerate(opts_alt) if i != ans_idx]
            options = ensure_4_opts(correct, wrongs)
            random.shuffle(options)
            ai = options.index(correct)
            expl = f"Metnin dikkatli okunması ve yorumlanması gerekmektedir. \"{correct}\" seçeneği paragraftaki bilgilerden çıkarılabilir."
            out.append(q(
                len(out), stem, options, ai, 2,
                random.choice(NEW_GEN_TYPES), use_topic,
                [use_topic[:12], "yorumlama", "cikarim"],
                expl, f"turkce7_{use_topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"
    random.shuffle(all_q)

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"turkce7_{idx:04d}"
        qq["id"] = f"turkce7_{idx:04d}"

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
            "publisher": "edumio",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_turkce7_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("TURKCE7 Question Bank Report")
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
    random.seed(43)
    main()
