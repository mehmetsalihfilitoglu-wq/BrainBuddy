#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 3rd Grade Hayat Bilgisi (Life Studies) question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: senaryo tabanlı, günlük hayat durumları, yorum, sebep-sonuç, değer/davranış analizi.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/hayat3"

TOPICS = [
    "okulumuzda_hayat",
    "evimizde_hayat",
    "saglikli_hayat",
    "guvenli_hayat",
    "ulkemizde_hayat",
    "dogada_hayat",
]

NEW_GEN_TYPES = [
    "senaryo_yorumlama",
    "gunluk_hayat_durumu",
    "davranis_analizi",
    "sebep_sonuc",
    "deger_yorumlama",
    "dogru_tavir_secimi",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    # Okulumuzda Hayat
    ("Ahmet sınıfta arkadaşının kalemini düşürdüğünü görüyor ve hemen yerden alıp geri veriyor.\n\nBu davranışla ilgili hangi değer öne çıkmaktadır?",
     "okulumuzda_hayat", ["Yardımlaşma ve sorumluluk", "Sadece kendi eşyasına dikkat etmek yeterlidir", "Kalem vermek zorunludur", "Başkalarının eşyasına karışmamak daha doğrudur"]),
    ("Sınıf başkanı seçiminde Zeynep, konuşmak isteyen herkese sırayla söz veriyor.\n\nBu tutumun sebebi aşağıdakilerden hangisi olabilir?",
     "okulumuzda_hayat", ["Herkesin fikrini söyleme hakkına saygı göstermek", "Sadece kendi konuşmak istediği için", "Öğretmen istediği için", "Kurallar böyle diye"]),
    ("Derste bir konuyu anlamayan Elif, arkadaşına sormak yerine parmak kaldırıp öğretmene soruyor.\n\nElif'in bu davranışı hangi sonucu doğurur?",
     "okulumuzda_hayat", ["Sınıftaki herkes doğru bilgiyi öğrenir; ders akışı bozulmaz", "Sadece Elif öğrenir", "Arkadaşına sormak daha iyidir", "Soru sormak yasaktır"]),
    ("Okul bahçesinde oyun oynarken Mehmet topu yanlışlıkla başka birinin üzerine attı. Hemen özür diledi ve \"Kazara oldu\" dedi.\n\nBu durumda özür dilemenin önemi nedir?",
     "okulumuzda_hayat", ["Karşıdaki kişiyi incittiğimizi kabul edip ilişkiyi düzeltmek", "Özür dilemek zorunludur ama anlamı yoktur", "Sadece büyükler özür diler", "Oyun sırasında kaza sayılmaz"]),
    ("Sınıfta bir öğrenci hasta olduğu için evde dinleniyor. Arkadaşları ona kart yazıp geçmiş olsun diyor.\n\nBu davranışın nedeni aşağıdakilerden hangisidir?",
     "okulumuzda_hayat", ["Empati kurarak arkadaşını düşünmek ve destek olmak", "Öğretmen istediği için", "Kart yazmak eğlencelidir", "Sadece aile ilgilenmelidir"]),
    ("Teneffüste sıraya giren çocuklar, önlerindeki arkadaşlarına saygı göstererek bekliyor.\n\nSıra kuralına uymanın sonucu nedir?",
     "okulumuzda_hayat", ["Adil ve düzenli bir ortam oluşur; herkes sırasını alır", "Sadece hızlı olanlar kazanır", "Kurallar gereksizdir", "Sıra sadece kantinde vardır"]),
    ("Öğretmen tahtaya yazı yazarken öğrenciler sessizce dinliyor.\n\nBu davranışın sebebi aşağıdakilerden hangisi olabilir?",
     "okulumuzda_hayat", ["Öğrenmeyi kolaylaştırmak ve başkalarının hakkına saygı göstermek", "Öğretmen kızmaması için", "Sessizlik zorunludur", "Konuşmak yasaktır"]),
    ("Okulda yeni gelen bir öğrenciyle tanışan çocuklar onu oyuna davet ediyor.\n\nBu davranış hangi değeri yansıtır?",
     "okulumuzda_hayat", ["Misafirperverlik ve hoşgörü", "Sadece tanıdıklarla oynamak", "Yeni gelen zorunlu katılmalıdır", "Oyunda yeni kişi olmamalıdır"]),
    # Evimizde Hayat
    ("Aile sofrasında herkes yemeğe birlikte oturuyor. Çocuk tabağına alırken \"Teşekkürler\" diyor.\n\nBu ifade hangi değeri gösterir?",
     "evimizde_hayat", ["Nezaket ve minnettarlık", "Zorunlu bir söz", "Sadece yemek için teşekkür", "Büyükler teşekkür etmelidir"]),
    ("Evde televizyon izlerken aile bireyleri farklı program istiyor. Çocuk, sırayla izlenmesini öneriyor.\n\nBu öneri hangi davranışı yansıtır?",
     "evimizde_hayat", ["Uzlaşma ve adil paylaşım", "Sadece kendi istediğini izlemek", "Televizyonu kapatmak", "En büyük karar vermelidir"]),
    ("Kardeşi oyuncağını kırdığında çocuk önce kızgınlık duyuyor, sonra kardeşinin kasıtsız yaptığını düşünüp konuşarak çözüm arıyor.\n\nBu tutumun sebebi nedir?",
     "evimizde_hayat", ["Kazayı anlayıp ilişkiyi korumak; öfkeyle hareket etmemek", "Kardeşini cezalandırmak", "Oyuncak önemsizdir", "Büyüklere söylemek yeterlidir"]),
    ("Aile toplantısında herkes ev işlerini paylaşıyor. Çocuk da masayı toplamayı üstleniyor.\n\nBu paylaşımın faydası nedir?",
     "evimizde_hayat", ["Sorumluluk almak ve aileye katkı sağlamak", "Sadece büyükler iş yapmalıdır", "Çocuk sadece oyun oynamalıdır", "Ev işi tek kişinin görevidir"]),
    ("Babası yorgun geldiğinde çocuk ona bir bardak su getiriyor.\n\nBu davranış hangi değeri gösterir?",
     "evimizde_hayat", ["İlgi ve saygı", "Zorunluluk", "Su getirmek yeterlidir", "Sadece anne ilgilenmelidir"]),
    ("Evde elektrik kesildiğinde aile birlikte mum yakıp sohbet ediyor. Çocuk da katılıyor.\n\nBu durumda ailenin birlikte vakit geçirmesi neyi ifade eder?",
     "evimizde_hayat", ["Zor durumda birbirine destek olmak ve birliktelik", "Sadece elektrik gelene kadar beklemek", "Mum yakmak tehlikelidir", "Çocuk uyumalıdır"]),
    ("Anne evi temizlerken çocuk odasını topluyor.\n\nBu iş bölümünün anlamı nedir?",
     "evimizde_hayat", ["Herkes kendi alanından sorumludur; ortak yaşam için katkı", "Sadece annenin işi temizliktir", "Çocuk sadece oyun oynar", "Oda toplamak zorunludur"]),
    # Sağlıklı Hayat
    ("Sabah kahvaltı yapan çocuk, öğle yemeğinde de düzenli besleniyor. Akşam abur cubur yerine meyve yiyor.\n\nBu beslenme düzeninin sonucu nedir?",
     "saglikli_hayat", ["Enerji ve sağlıklı büyüme; dikkat ve öğrenme kolaylaşır", "Sadece tok hissetmek", "Abur cubur yasaktır", "Meyve her zaman yeterlidir"]),
    ("Elif her gün dişlerini fırçalıyor ve ellerini yemekten önce yıkıyor.\n\nBu alışkanlıkların sebebi nedir?",
     "saglikli_hayat", ["Hijyen ve sağlığı korumak; hastalıkları önlemek", "Ailesi istediği için", "Sadece diş çürümesini önlemek", "El yıkamak yeterlidir"]),
    ("Yazın güneşe çıkmadan önce güneş kremi süren çocuk, şapka da takıyor.\n\nBu önlemlerin nedeni aşağıdakilerden hangisidir?",
     "saglikli_hayat", ["Güneşin zararlı etkilerinden korunmak", "Sadece güzel görünmek için", "Şapka zorunludur", "Krem sürmek yeterlidir"]),
    ("Uyku saatinde yatağına giden çocuk, ekrana bakmayı bırakıyor.\n\nBu davranışın faydası nedir?",
     "saglikli_hayat", ["Kaliteli uyku; ertesi gün dinç ve dikkatli olmak", "Sadece aile rahat uyur", "Ekran zararsızdır", "Uyumak zorunludur"]),
    ("Hasta olan arkadaşına \"Geçmiş olsun, bol bol su iç\" diyen çocuk, doğru bir tavsiye veriyor.\n\nSu içmenin hastalıkta faydası nedir?",
     "saglikli_hayat", ["Vücudun iyileşmesine yardımcı olmak; ateşi düşürmek", "Sadece susuzluğu gidermek", "İlaç yerine geçer", "Her hastalıkta aynıdır"]),
    ("Spor yapan çocuk, düzenli hareket ettiği için kendini daha iyi hissediyor.\n\nDüzenli sporun etkisi aşağıdakilerden hangisidir?",
     "saglikli_hayat", ["Fiziksel ve ruhsal sağlığı desteklemek", "Sadece kilo vermek için", "Spor zorunludur", "Sadece yarışmalar için yapılır"]),
    # Güvenli Hayat
    ("Yolda yürürken kaldırımı kullanan çocuk, trafik ışığında bekliyor.\n\nBu davranışların sebebi nedir?",
     "guvenli_hayat", ["Güvenliği sağlamak; trafik kurallarına uymak", "Sadece polis gördüğünde", "Kaldırımda yürümek zorunludur", "Trafik ışığı sadece araçlar içindir"]),
    ("Bilinmeyen bir kişi \"Seni evine götüreyim\" dediğinde çocuk \"Hayır, teşekkürler\" deyip uzaklaşıyor.\n\nBu tepkinin nedeni aşağıdakilerden hangisidir?",
     "guvenli_hayat", ["Yabancılarla gitmemek; kendini korumak", "Kaba davranmak", "Herkes tehlikelidir", "Sadece anne-baba yanında olmalıdır"]),
    ("Evde yalnızken kapı çaldığında çocuk, kim olduğunu sormadan kapıyı açmıyor.\n\nBu tutumun amacı nedir?",
     "guvenli_hayat", ["Güvenlik; tanımadığı kişilere kapı açmamak", "Misafir kabul etmemek", "Kapıyı açmak tehlikelidir", "Sadece büyükler kapı açar"]),
    ("Bisiklete binerken kask takan çocuk, düşme durumunda kafasını korumayı hedefliyor.\n\nKask takmanın sebebi nedir?",
     "guvenli_hayat", ["Olası kaza durumunda yaralanmayı azaltmak", "Sadece güzel görünmek için", "Bisiklet tehlikelidir", "Kask zorunludur ama işe yaramaz"]),
    ("Yangın tatbikatında sınıf düzenli şekilde çıkışa yönlendiriliyor. Çocuk paniğe kapılmadan sırayı takip ediyor.\n\nBu davranışın önemi nedir?",
     "guvenli_hayat", ["Acil durumda güvenli tahliye; panik yerine plana uymak", "Sadece öğretmen söylediği için", "Tatbikat gereksizdir", "Herkes kendi çıkmalıdır"]),
    ("Yüzme bilmeyen çocuk, derin suya girmiyor; sadece sığ yerde oynuyor.\n\nBu davranış hangi güvenlik kuralına uygundur?",
     "guvenli_hayat", ["Bilmediği ortamda risk almamak; kendi sınırlarını bilmek", "Asla suya girmemek", "Derin su güvenlidir", "Yüzme öğrenmek gerekmez"]),
    # Ülkemizde Hayat
    ("23 Nisan'da okulda bayram kutlaması yapılıyor. Çocuklar Atatürk'ü anıyor ve şiirler okuyor.\n\nBu kutlamanın anlamı nedir?",
     "ulkemizde_hayat", ["Ulusal egemenlik ve çocuklara verilen değeri kutlamak", "Sadece tatil günüdür", "Sadece okul etkinliğidir", "Atatürk sadece askerdir"]),
    ("Türk bayrağının dalgalanması, vatandaşlar için ne ifade eder?",
     "ulkemizde_hayat", ["Bağımsızlık ve millî kimlik sembolü", "Sadece bir bez parçasıdır", "Sadece resmî binalarda asılır", "Bayrak renkleri önemsizdir"]),
    ("İstiklal Marşı okunurken ayağa kalkan çocuklar sessizce dinliyor.\n\nBu tutumun sebebi nedir?",
     "ulkemizde_hayat", ["Millî değerlere saygı göstermek", "Öğretmen söylediği için", "Marş zorunludur", "Sadece spor karşılaşmalarında önemlidir"]),
    ("Ülkemizde farklı yörelerden gelen aileler aynı mahallede yaşıyor. Çocuklar birbirleriyle oynuyor.\n\nBu durum neyi gösterir?",
     "ulkemizde_hayat", ["Farklılıklara rağmen birlikte yaşama; hoşgörü ve uyum", "Herkes aynı yöreden olmalıdır", "Farklılık sorun yaratır", "Sadece akraba olanlar bir arada olmalıdır"]),
    ("Kurtuluş Savaşı'nda Atatürk ve Türk halkı birlikte mücadele etti. Bu süreç neyi simgeler?",
     "ulkemizde_hayat", ["Millî mücadele ve bağımsızlık yolunda birlik", "Sadece askerler savaştı", "Savaş bitince unutulmalıdır", "Sadece Atatürk çalıştı"]),
    ("Cumhuriyet Bayramı'nda okulda tören yapılıyor. Öğrenciler \"Cumhuriyet\" kelimesinin anlamını tartışıyor.\n\nCumhuriyet nedir?",
     "ulkemizde_hayat", ["Halkın yönetime katıldığı; seçimle işbaşına gelen yönetim biçimi", "Sadece bir bayram adıdır", "Sadece Türkiye'de vardır", "Cumhurbaşkanı her şeye karar verir"]),
    # Doğada Hayat
    ("Piknikte çöpleri toplayıp çöp kutusuna atan aile, doğayı temiz bırakıyor.\n\nBu davranışın sonucu nedir?",
     "dogada_hayat", ["Çevre korunur; diğer canlılar ve insanlar sağlıklı ortamda yaşar", "Sadece ceza yememek için", "Çöp doğada kalmalıdır", "Piknik alanı zaten kirli olur"]),
    ("Ormanlık alanda ateş yakmadan önce izin alan ve söndüren çocuk, hangi kurala uyuyor?",
     "dogada_hayat", ["Yangın riskini azaltmak; doğayı korumak", "Ateş yakmak yasaktır", "Sadece büyükler ateş yakar", "Orman yanarsa sorun olmaz"]),
    ("Hayvanlara zarar vermeyen, onları besleyen çocuk, doğadaki canlılara saygı gösteriyor.\n\nBu tutumun nedeni nedir?",
     "dogada_hayat", ["Canlılara merhamet; ekosistemin dengesine katkı", "Hayvanlar tehlikelidir", "Sadece evcil hayvanlar önemlidir", "Doğa insana aittir"]),
    ("Geri dönüşüm kutularına cam, kağıt ve plastiği ayrı atan çocuk ne yapıyor?",
     "dogada_hayat", ["Kaynakları korumak; çevreye katkı sağlamak", "Sadece öğretmen söylediği için", "Geri dönüşüm gereksizdir", "Tüm çöpler aynıdır"]),
    ("Suyu boşa harcamayan çocuk, musluğu kapalı tutuyor. Bu alışkanlık neden önemlidir?",
     "dogada_hayat", ["Su kaynaklarını korumak; tasarruf bilinci", "Sadece fatura düşer", "Su sınırsızdır", "Musluk kapatmak zorundadır"]),
    ("Ağaç diken çocuk, \"İleride bu ağaç gölge ve oksijen sağlayacak\" diyor.\n\nBu ifade neyi vurgular?",
     "dogada_hayat", ["Ağaçların canlılara faydası; gelecek nesillere katkı", "Sadece güzel görünür", "Ağaç dikmek zorunludur", "Ağaçlar sadece meyve verir"]),
    # Extra templates for balance and variety
    ("Ödevini bitirdikten sonra çantasını hazırlayan çocuk, ertesi güne hazırlanıyor.\n\nBu alışkanlığın faydası nedir?",
     "okulumuzda_hayat", ["Düzen ve sorumluluk; okulda eksik kalmamak", "Sadece anne hazırlasın", "Çanta sabah da hazırlanabilir", "Ödev zorunlu değildir"]),
    ("Arkadaşı üzüldüğünde yanına oturup dinleyen çocuk, \"Senin yanındayım\" diyor.\n\nBu davranış neyi gösterir?",
     "okulumuzda_hayat", ["Empati ve destek olmak", "Sadece konuşmak", "Üzüntü geçer", "Başkalarının sorununa karışmamak gerekir"]),
    ("Odasını toplamadan oyuna geçmek isteyen çocuğa anne \"Önce odanı topla\" diyor. Çocuk kabul ediyor.\n\nBu kabullenmenin sebebi nedir?",
     "evimizde_hayat", ["Sorumlulukları önce tamamlamak; kurallara uymak", "Anne kızar diye", "Oda toplamak önemsizdir", "Oyuna geçmek daha önemlidir"]),
    ("Hasta olduğunda doktora giden çocuk, ilacını zamanında alıyor.\n\nBu davranışın amacı nedir?",
     "saglikli_hayat", ["İyileşmek ve sağlığa kavuşmak", "Aile istediği için", "İlaç almak zorundadır", "Doktor her şeyi bilir"]),
    ("Trafik lambası kırmızıyken duran araçların arasından geçmeyen çocuk, yaya geçidini kullanıyor.\n\nBu tutumun sebebi nedir?",
     "guvenli_hayat", ["Trafik güvenliği kurallarına uymak; kaza riskini azaltmak", "Yaya geçidi daha kısadır", "Araçlar durmuştur", "Kırmızıda geçmek yasaktır ama tehlikesizdir"]),
    ("Milli bayramlarda Türk bayrağının renginin kırmızı ve beyaz olmasının anlamı tartışılıyor.\n\nBayrağın renkleri neyi temsil eder?",
     "ulkemizde_hayat", ["Bağımsızlık mücadelesi ve temizlik/dürüstlük sembolleri", "Sadece güzel görünür", "Rastgele seçilmiştir", "Başka ülkelerde de aynıdır"]),
    ("Parkta çiçekleri koparmayan, sadece fotoğrafını çeken çocuk ne yapıyor?",
     "dogada_hayat", ["Doğayı koruyarak keyif almak", "Fotoğraf çekmek yasaktır", "Çiçek koparmak serbesttir", "Park sadece oyun içindir"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"hayat3_{idx:04d}",
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
        "subject": "hayat",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = [
        "Bu çıkarım senaryodan yapılamaz.",
        "Sebep-sonuç ilişkisi yanlış kurulmuştur.",
        "Davranış yorumu hatalıdır.",
        "Değer analizi eksiktir.",
    ]
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
            expl = f"Senaryo ve günlük hayat durumuna göre doğru davranış/yorum \"{correct}\" seçeneğidir. Diğer seçenekler yanlış sebep-sonuç, hatalı değer yorumu veya eleyici distractor içermektedir."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:12] if len(topic) >= 12 else topic, "yorumlama", "davranis_analizi"],
                expl, f"hayat3_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"hayat3_{idx:04d}"
        qq["id"] = f"hayat3_{idx:04d}"

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
            "subject": "hayat",
            "publisher": "edumio",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_hayat3_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("HAYAT3 (3. Sınıf Hayat Bilgisi) Question Bank Report")
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
    random.seed(42)
    main()
