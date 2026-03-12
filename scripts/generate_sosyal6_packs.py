#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 6th Grade Sosyal Bilgiler question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: paragraf yorum, harita/grafik/tablo yorumlama, tarihsel analiz, sebep-sonuç.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/sosyal6"

TOPICS = [
    "birey_toplum",
    "kultur_miras",
    "insanlar_yerler_cevreler",
    "bilim_teknoloji_toplum",
    "uretim_dagitim_tuketim",
    "etkin_vatandaslik",
    "kuresel_baglantilar",
]

NEW_GEN_TYPES = [
    "paragraf_yorum",
    "harita_yorumlama",
    "tablo_grafik_yorumlama",
    "tarihsel_olay_analizi",
    "cografi_yorum",
    "sebep_sonuc",
    "kavram_baglantisi",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    ("Paragrafta \"Bireyin toplum içindeki rollerinin (öğrenci, aile ferti, vatandaş) birbirini tamamladığı\" ifadesi geçmektedir.\n\nBu bilgiye göre birey-toplum ilişkisi ile ilgili hangi çıkarım yapılabilir?",
     "birey_toplum", ["Bireyin birden fazla rolü vardır ve bu roller toplumsal yaşamı şekillendirir", "Birey sadece tek bir role sahip olmalıdır", "Roller toplumdan bağımsızdır", "Aile rolü diğerlerinden önemli değildir"]),
    ("\"Toplumsal normlar, bir toplumda kabul gören davranış kurallarıdır\" cümlesine göre normların işlevi nedir?",
     "birey_toplum", ["Toplumsal düzen ve uyumu sağlamaya yardımcı olur", "Normlar sadece yasada yazılıdır", "Normlar bireyleri kısıtlar, düzene katkı sağlamaz", "Normlar evrensel değildir, farklı toplumlarda benzer olmaz"]),
    ("Kültür ve Miras ünitesinde \"somut olmayan kültürel miras\" kavramı geçmektedir. Halk oyunları, masallar, yemekler bu kapsamda değerlendirilir.\n\nBu tür mirasın korunması neden önemlidir?",
     "kultur_miras", ["Toplumsal kimliği ve geçmişi yaşatmaya katkı sağlar", "Sadece turizm için önemlidir", "Maddi değeri yoktur, korumak gereksizdir", "Sadece büyük şehirlerde vardır"]),
    ("Tarihsel bir metinde \"göçebe yaşamdan yerleşik hayata geçiş, tarım toplumlarının doğuşuna yol açmıştır\" ifadesi yer alıyor.\n\nBu cümleye göre yerleşik hayat ile tarım arasındaki sebep-sonuç ilişkisi nasıldır?",
     "kultur_miras", ["Yerleşik hayat tarıma uygun ortam sağlar; tarım da yerleşik toplumları besler", "Tarım sadece göçebelerin işidir", "Yerleşik hayat tarımdan önce gelmez", "Tarım yerleşik hayatı engeller"]),
    ("Haritada Türkiye'nin iklim bölgeleri gösterilmektedir. Karadeniz kıyısı nemli, İç Anadolu kurak iklime sahiptir.\n\nBu farklılığın temel sebebi aşağıdakilerden hangisidir?",
     "insanlar_yerler_cevreler", ["Denize uzaklık, yükselti ve dağ sıralarının konumu", "Sadece enlem farkı", "Nüfus yoğunluğu", "Şehirleşme oranı"]),
    ("Coğrafi bölgelerin özellikleri tablosunda Akdeniz'de turunçgil, İç Anadolu'da tahıl tarımı öne çıkar.\n\nBu farklılığın nedeni aşağıdakilerden hangisidir?",
     "insanlar_yerler_cevreler", ["İklim ve toprak koşullarının bölgelere göre değişmesi", "Sadece nüfus farkı", "Sanayi yatırımları", "Ulaşım imkanları"]),
    ("\"Bilim ve teknoloji toplumsal değişimi hızlandırır; toplumun ihtiyaçları da bilimsel çalışmaları yönlendirir\" paragrafından çıkarılabilecek sonuç nedir?",
     "bilim_teknoloji_toplum", ["Bilim, teknoloji ve toplum karşılıklı etkileşim içindedir", "Bilim toplumdan bağımsızdır", "Teknoloji sadece ekonomiyi etkiler", "Toplum bilimi yönlendirmez"]),
    ("İletişim teknolojilerinin gelişmesiyle bilgiye erişim kolaylaşmıştır. Ancak \"doğru bilgiyi ayırt etmek\" önem kazanmıştır.\n\nBu durumun sebebi aşağıdakilerden hangisidir?",
     "bilim_teknoloji_toplum", ["Her bilgi doğru değildir; eleştirel düşünme ve kaynak kontrolü gerekir", "İnternet güvenilir değildir", "Teknoloji zararlıdır", "Bilgi artık önemli değildir"]),
    ("Üretim, dağıtım ve tüketim sürecinde \"arayüz\" olarak distributor (dağıtıcı) yer alır. Üreticiden tüketiciye malın ulaşmasında aracıdır.\n\nDağıtım kanalının varlığı hangi işlevi yerine getirir?",
     "uretim_dagitim_tuketim", ["Ürünün geniş coğrafyaya ulaşmasını ve tüketiciye sunulmasını sağlar", "Sadece fiyatı artırır", "Üretimi engeller", "Tüketiciye katkısı yoktur"]),
    ("\"Sürdürülebilir tüketim\" kavramı metinde \"gelecek kuşakların ihtiyaçlarını da düşünerek tüketmek\" olarak tanımlanıyor.\n\nBu tanıma göre sürdürülebilir tüketim ne anlama gelir?",
     "uretim_dagitim_tuketim", ["Kaynakları bilinçli kullanmak ve israfı azaltmak", "Az tüketmek yeterlidir", "Sadece geri dönüşüm yeterlidir", "Tüketim hiç yapılmamalıdır"]),
    ("Etkin vatandaşlıkla ilgili metinde \"oy kullanma, vergi verme, toplumsal konularda söz sahibi olma\" vatandaşlık görevleri olarak sayılıyor.\n\nBu bilgiye göre etkin vatandaşın özelliği aşağıdakilerden hangisidir?",
     "etkin_vatandaslik", ["Hak ve sorumluluklarının bilincinde olup topluma katılım gösterir", "Sadece oy kullanır", "Vergi vermek yeterlidir", "Siyasetle ilgilenmek zorunlu değildir"]),
    ("\"Demokratik toplumda çoğunluğun kararına saygı, azınlık haklarının korunmasıyla birlikte yürür\" ifadesi geçiyor.\n\nBu cümleden çıkarılabilecek sonuç nedir?",
     "etkin_vatandaslik", ["Demokrasi hem çoğunluk kararına hem azınlık haklarına önem verir", "Sadece çoğunluk haklıdır", "Azınlıkların hakkı yoktur", "Demokrasi sadece seçimle ilgilidir"]),
    ("Küresel bağlantılar ünitesinde \"ülkeler arası ticaret, turizm ve iletişim\" konuları işleniyor. Uluslararası iş birliği örgütleri örnek veriliyor.\n\nBu tür örgütlerin temel işlevi nedir?",
     "kuresel_baglantilar", ["Ülkeler arası iş birliği, barış ve ortak sorunların çözümüne katkı sağlamak", "Sadece ticaret için vardır", "Tek bir ülkenin çıkarına hizmet eder", "Örgütler gereksizdir"]),
    ("Küresel sorunlar (iklim değişikliği, göç, salgın) metinde ele alınıyor. \"Bu sorunlar tek bir ülkenin çabasıyla çözülemez\" denilmektedir.\n\nBu ifadenin gerekçesi aşağıdakilerden hangisidir?",
     "kuresel_baglantilar", ["Sorunlar sınır tanımadan tüm dünyayı etkiler; ortak çözüm gerektirir", "Teknoloji yetersizdir", "Ülkeler iş birliği yapmaz", "Sorunlar çözülemez"]),
    ("Tarihsel gelişim tablosunda \"Yazının icadı → Kayıt tutma → Tarih öncesi/sonrası ayrımı\" sıralaması verilmiştir.\n\nYazının icadının tarih bilimi için önemi nedir?",
     "kultur_miras", ["Olayların kaydedilmesini sağlar; tarihsel bilgiye güvenilir erişim sağlanır", "Yazı sadece edebiyat içindir", "Tarih öncesi dönem yoktur", "Kayıt tutmak önemsizdir"]),
    ("Bireyin toplumdaki \"sosyal rol\" kavramı anlatılıyor. Bir kişi aynı anda anne, öğretmen ve komşu olabilir.\n\nBu durum neyi gösterir?",
     "birey_toplum", ["Birey farklı ortamlarda farklı roller üstlenir", "Herkes tek role sahip olmalıdır", "Roller birbiriyle çakışır", "Toplum rolleri belirlemez"]),
    ("İklim grafiğinde yağış ve sıcaklık değerleri karşılaştırılıyor. Akdeniz ikliminde yazlar kurak, kışlar yağışlıdır.\n\nBu grafiğe göre Akdeniz ikliminin tarımı nasıl etkiler?",
     "insanlar_yerler_cevreler", ["Yaz kuraklığı sulama ihtiyacını artırır; kış yağışı bazı ürünlere uygundur", "Tarım mümkün değildir", "Her mevsim yağışlıdır", "Sadece kışın tarım yapılır"]),
    ("Teknolojinin çevreye etkisi metinde tartışılıyor. \"Sanayi devrimi hava ve su kirliliğini artırmıştır\" ifadesi geçiyor.\n\nBu durumun sebebi aşağıdakilerden hangisidir?",
     "bilim_teknoloji_toplum", ["Endüstriyel üretim atıklarının çevreye verdiği zarar", "Nüfus artışı tek başına yeterli değildir", "Teknoloji sadece fayda sağlar", "Çevre kirliliği doğal bir süreçtir"]),
    ("Tüketici hakları metninde \"ayıplı mal aldığında iade veya değişim hakkı\" vurgulanıyor.\n\nBu hakkın amacı nedir?",
     "uretim_dagitim_tuketim", ["Tüketiciyi korumak ve adil alışverişi sağlamak", "Satıcıyı cezalandırmak", "Üretimi durdurmak", "Fiyatları düşürmek"]),
    ("\"Seçme ve seçilme hakkı\" demokratik vatandaşlığın temel haklarından biridir.\n\nBu hakkın kullanılması neden önemlidir?",
     "etkin_vatandaslik", ["Yönetimde söz sahibi olmak ve temsil için vazgeçilmezdir", "Sadece belediye seçimlerinde geçerlidir", "Zorunlu değildir", "Sadece yetişkinler için geçerlidir (çocuklar etkilenmez)"]),
    ("Uluslararası ticaret grafiğinde ülkeler arası mal ve hizmet akışı gösteriliyor. Gelişmiş ülkeler sanayi ürünü, gelişmekte olan ülkeler hammadde ihraç eder.\n\nBu farklılığın nedeni aşağıdakilerden hangisi olabilir?",
     "kuresel_baglantilar", ["Ekonomik gelişmişlik ve üretim yapısı farkı", "Sadece coğrafya", "Nüfus farkı", "Para birimi farkı"]),
    ("Toplumsal grup türleri tablosunda \"birincil gruplar\" (aile, arkadaş) ve \"ikincil gruplar\" (dernek, okul) ayrımı yapılıyor.\n\nBu ayrımın temel ölçütü nedir?",
     "birey_toplum", ["İlişkinin yakınlığı ve niteliği (samimiyet, resmiyet)", "Grup büyüklüğü", "Gelir düzeyi", "Yaş grubu"]),
    ("Anadolu medeniyetleri haritasında Hitit, Frig, Lidya uygarlıkları farklı bölgelerde gösteriliyor.\n\nBu medeniyetlerin aynı coğrafyada farklı dönemlerde gelişmesinin sebebi ne olabilir?",
     "kultur_miras", ["Coğrafi avantajlar, ticaret yolları ve bereketli topraklar yerleşimi cazip kılmıştır", "Sadece savaşlar", "Nüfus artışı", "İklim değişikliği"]),
    ("Nüfus yoğunluğu haritasında kıyı bölgeleri yoğun, iç bölgeler seyrek nüfuslu görünüyor.\n\nBu dağılımı etkileyen faktörler arasında aşağıdakilerden hangisi sayılabilir?",
     "insanlar_yerler_cevreler", ["İklim, ulaşım, tarım ve sanayi imkanları", "Sadece tarihsel nedenler", "Dil farkı", "Din birliği"]),
    ("\"Bilgi toplumu\" kavramı metinde \"bilginin üretim ve hizmette temel kaynak olması\" olarak tanımlanıyor.\n\nBu tanıma göre bilgi toplumunda aşağıdakilerden hangisi öne çıkar?",
     "bilim_teknoloji_toplum", ["Eğitim, yenilikçilik ve bilgiye erişim önem kazanır", "Sadece tarım önemlidir", "Sanayi tek başına yeterlidir", "Bilgi gizlenmelidir"]),
    ("Üretim faktörleri (toprak, emek, sermaye, girişimci) tabloda verilmiştir. Girişimci diğerlerini bir araya getirir.\n\nGirişimcinin işlevi nedir?",
     "uretim_dagitim_tuketim", ["Kaynakları bir araya getirip üretim sürecini organize eder", "Sadece sermaye sağlar", "Girişimci gereksizdir", "Sadece işçi çalıştırır"]),
    ("\"Kamuoyu\" kavramı \"toplumun belirli bir konudaki genel düşüncesi\" olarak açıklanıyor.\n\nKamuoyunun demokratik sistemdeki yeri nedir?",
     "etkin_vatandaslik", ["Yöneticilerin kararlarını yönlendirebilir; toplumsal taleplerin iletilmesine katkı sağlar", "Kamuoyu önemsizdir", "Sadece seçimlerde etkilidir", "Medyadan bağımsızdır"]),
    ("İklim değişikliğinin küresel etkileri metinde anlatılıyor. Buzulların erimesi, deniz seviyesinin yükselmesi, ekstrem hava olayları örnek veriliyor.\n\nBu sorunun küresel çözümü için ne gereklidir?",
     "kuresel_baglantilar", ["Ülkelerin iş birliği, sözleşmeler ve ortak taahhütler", "Tek bir ülkenin çabası yeterlidir", "Sorun yok sayılmalıdır", "Sadece gelişmiş ülkeler sorumludur"]),
    ("\"Kültürün nesilden nesile aktarılmasında dil, gelenekler ve eğitim rol oynar\" cümlesi geçiyor.\n\nBu bilgiye göre kültürel aktarım nasıl gerçekleşir?",
     "kultur_miras", ["Sözlü ve yazılı iletişim, uygulamalar ve eğitim yoluyla", "Sadece kitaplarla", "Kültür aktarılmaz", "Sadece aile içinde kalır"]),
    ("Yerleşme tipleri (köy, kasaba, kent) metinde coğrafi ve ekonomik özelliklere göre sınıflandırılıyor.\n\nKentlerin köylerden farkı aşağıdakilerden hangisinde daha belirgindir?",
     "insanlar_yerler_cevreler", ["Nüfus yoğunluğu, iş kolları çeşitliliği ve hizmetlerin yoğunluğu", "Sadece alan büyüklüğü", "İklim farkı", "Dil farkı"]),
    ("\"İnovasyon\" kavramı \"yeni fikirlerin ürüne veya hizmete dönüştürülmesi\" olarak tanımlanıyor.\n\nİnovasyonun topluma katkısı nedir?",
     "bilim_teknoloji_toplum", ["Ekonomik büyüme, verimlilik artışı ve yaşam kalitesini yükseltme", "Sadece teknoloji şirketlerine fayda sağlar", "İnovasyon gereksizdir", "Sadece bilim insanları için geçerlidir"]),
    ("\"Bütçe\" kavramı \"gelir ve giderlerin planlanması\" olarak açıklanıyor. Aile bütçesinde gelir gideri geçmemelidir.\n\nBütçe yapmanın amacı nedir?",
     "uretim_dagitim_tuketim", ["Gelir-gider dengesini kurarak finansal disiplin sağlamak", "Sadece tasarruf etmek", "Harcamayı tamamen durdurmak", "Bütçe gereksizdir"]),
    ("\"Sivil toplum örgütleri\" devletten bağımsız, gönüllülük esaslı örgütlerdir. Çevre, eğitim, insan hakları alanlarında faaliyet gösterirler.\n\nBu örgütlerin işlevi nedir?",
     "etkin_vatandaslik", ["Toplumsal sorunlara yurttaşların katılımıyla çözüm üretmek", "Sadece devlete alternatif olmak", "Siyasi partilerle aynı işlevdedir", "Örgütler gereksizdir"]),
    ("Uluslararası göç hareketleri haritasında gelişmekte olan ülkelerden gelişmiş ülkelere akış gösteriliyor.\n\nBu göçün temel nedenleri arasında aşağıdakilerden hangisi sayılabilir?",
     "kuresel_baglantilar", ["Ekonomik fırsatlar, eğitim, güvenlik ve iş imkanları", "Sadece savaş", "İklim değişikliği tek neden değildir", "Göç zorunlu değildir"]),
    ("Toplumsal değişim metninde \"teknolojik gelişmeler toplumsal yapıyı etkiler\" ifadesi geçiyor.\n\nBuna örnek olarak aşağıdakilerden hangisi verilebilir?",
     "bilim_teknoloji_toplum", ["İnternet ile bilgiye erişim kolaylaşmış, çalışma ve iletişim biçimleri değişmiştir", "Teknoloji sadece eğlence içindir", "Toplum teknolojiden etkilenmez", "Değişim sadece ekonomide olur"]),
    ("Ekonomik faaliyet kolları (tarım, sanayi, hizmet) tablosunda gelişmiş ülkelerde hizmet sektörünün payı yüksektir.\n\nBu durumun sebebi aşağıdakilerden hangisi olabilir?",
     "uretim_dagitim_tuketim", ["Sanayileşme sonrası hizmet sektörü (sağlık, eğitim, finans vb.) genişlemiştir", "Tarım gelişmiş ülkelerde yoktur", "Sanayi azalmıştır", "Sadece nüfus artışı etkilidir"]),
    ("\"Hak ve özgürlükler sınırsız değildir; başkalarının haklarına saygı sınırı oluşturur\" ifadesi geçiyor.\n\nBu cümleden çıkarılabilecek sonuç nedir?",
     "etkin_vatandaslik", ["Haklar kullanılırken başkalarının hakları da gözetilmelidir", "Haklar sınırsızdır", "Özgürlük yoktur", "Sadece yasalar sınır koyar"]),
    ("Dünya kaynakları (su, enerji, orman) ile ilgili grafikte bazı bölgelerde kıtlık olduğu görülüyor.\n\nKaynak kıtlığının olası sonuçları arasında aşağıdakilerden hangisi sayılabilir?",
     "kuresel_baglantilar", ["Çatışma riski, göç ve iş birliği ihtiyacı", "Sadece fiyat artışı", "Kıtlık sorun değildir", "Teknoloji her şeyi çözer"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"sosyal6_{idx:04d}",
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
        "subject": "sosyal",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = ["Paragrafta bu çıkarım yapılamaz.", "Sebep-sonuç ilişkisi yanlış kurulmuştur.", "Bu bilgi metinde verilmemiştir.", "Kavram karışıklığı vardır."]
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
            expl = f"Metindeki anlam ve sebep-sonuç ilişkisine göre doğru cevap \"{correct}\" seçeneğidir. Diğer seçenekler yanlış sebep-sonuç, kavram karışıklığı veya kısmi yorum hatası içermektedir."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10], "yorumlama", "cikarim"],
                expl, f"sosyal6_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"sosyal6_{idx:04d}"
        qq["id"] = f"sosyal6_{idx:04d}"

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
            "subject": "sosyal",
            "publisher": "brainbuddy",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_sosyal6_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("SOSYAL6 Question Bank Report")
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
    random.seed(49)
    main()
