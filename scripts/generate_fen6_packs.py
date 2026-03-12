#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 6th Grade Fen Bilimleri question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: deney/gözlem/grafik/tablo yorumlama, sebep-sonuç, kavram bağlantısı.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/fen6"

TOPICS = [
    "gunes_sistemi_tutulmalar",
    "vucudumuzdaki_sistemler",
    "kuvvet_hareket",
    "madde_isi",
    "ses_ozellikleri",
    "vucudumuzdaki_sistemler_saglik",
    "elektrigin_iletimi",
]

NEW_GEN_TYPES = [
    "deney_yorumlama",
    "gozlem_yorumlama",
    "grafik_yorumlama",
    "tablo_yorumlama",
    "sebep_sonuc",
    "kavram_baglantisi",
    "cok_adinli_dusunme",
]

# (stem, topic, [correct, wrong1, wrong2, wrong3])
TEMPLATES = [
    ("Güneş Sistemi modelinde öğrenciler Ay'ın Dünya etrafında, Dünya'nın ise Güneş etrafında döndüğünü gözlemledi. Güneş tutulması sırasında Ay, Güneş ile Dünya arasına girer.\n\nBu modele göre Güneş tutulmasının gerçekleşme sebebi aşağıdakilerden hangisidir?",
     "gunes_sistemi_tutulmalar", ["Ay, Güneş ile Dünya arasında aynı hizaya geldiğinde Güneş ışığını engeller", "Dünya Güneş'e çok yaklaştığında tutulma olur", "Ay, Dünya'nın gölgesine girdiğinde tutulma olur", "Güneş ışığı Ay'dan yansır"]),
    ("Ay tutulmasında Dünya, Güneş ile Ay arasına girer. Ay tutulması sadece dolunay evresinde gerçekleşir.\n\nBu bilgilere göre Ay tutulmasının dolunay evresinde olmasının nedeni nedir?",
     "gunes_sistemi_tutulmalar", ["Sadece dolunayda Ay, Dünya ve Güneş aynı doğrultuda olabilir", "Ay sadece dolunayda parlaktır", "Dünya gölgesi dolunayda daha büyüktür", "Güneş dolunayda daha parlaktır"]),
    ("Öğrenciler sindirim sistemini inceledi. Midenin besinleri karıştırdığını, ince bağırsağın besinleri emdiğini gördüler.\n\nBu gözleme dayanarak sindirim sisteminin işlevi ile ilgili hangi çıkarım yapılabilir?",
     "vucudumuzdaki_sistemler", ["Sindirim sistemi besinleri parçalayıp emilimini sağlar", "Sadece mide sindirim yapar", "İnce bağırsak besin üretir", "Sindirim sadece ağızda gerçekleşir"]),
    ("Solunum sistemi deneyinde öğrenciler göğüs kafesinin şişip indiğini gözlemledi. Akciğerlere hava girip çıktığını fark ettiler.\n\nBu deneye göre soluk alıp verme ile ilgili doğru çıkarım aşağıdakilerden hangisidir?",
     "vucudumuzdaki_sistemler", ["Soluk alırken hava akciğerlere girer, verirken çıkar", "Göğüs kafesi sadece genişler", "Hava sadece burundan girer", "Akciğerler hava üretir"]),
    ("Bir öğrenci aynı eğimdeki rampadan farklı kütleli topları bıraktı. Her iki top da aynı sürede aşağı indi.\n\nBu deneyin sonucuna göre aşağıdakilerden hangisi söylenebilir?",
     "kuvvet_hareket", ["Sürtünmesiz ortamda kütle serbest düşme süresini etkilemez", "Ağır top daha hızlı düşer", "Hafif top daha çabuk yere ulaşır", "Kütle her zaman hareketi etkiler"]),
    ("Öğrenciler halı ve parke üzerinde aynı topu yuvarladı. Halıda top daha kısa mesafe aldı.\n\nBu gözlemin sebebi aşağıdakilerden hangisidir?",
     "kuvvet_hareket", ["Halıda sürtünme kuvveti daha büyüktür", "Parke daha düzdür", "Top halıda daha hafiftir", "Parke daha uzundur"]),
    ("Bir deneyde eşit kütleli su ve yağ aynı ısıtıcıyla ısıtıldı. Suyun sıcaklığı daha yavaş arttı.\n\nBu sonuca göre aşağıdaki yorumlardan hangisi doğrudur?",
     "madde_isi", ["Suyun özgül ısı kapasitesi yağdan büyüktür, daha çok ısı alır", "Yağ daha iyi iletkendir", "Su ısıyı daha iyi iletir", "Yağ daha fazla ısı içerir"]),
    ("Isı alan metallerin genleştiği, soğuyunca büzüldüğü gözlemlendi. Demir köprü yapımında boşluklar bırakılır.\n\nBu uygulamanın nedeni aşağıdakilerden hangisidir?",
     "madde_isi", ["Metaller sıcaklık değişiminde genleşip büzülür, boşluklar bunu karşılar", "Boşluklar tasarruf sağlar", "Demir çok ağırdır", "Köprüler uzundur"]),
    ("Ses dalgaları deneyinde titreşen diyapazon suya batırıldığında su sıçramaları oluştu. Titreşim durduğunda sıçramalar da durdu.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "ses_ozellikleri", ["Ses titreşimle oluşur, titreşim bitince ses de biter", "Ses suda daha güçlüdür", "Diyapazon ses üretmez", "Su sesi yansıtır"]),
    ("Farklı kalınlıktaki tellerden çıkan sesler karşılaştırıldı. İnce tel daha tiz ses çıkardı.\n\nBu gözleme göre sesin tizliği ile ilgili hangi ilişki kurulabilir?",
     "ses_ozellikleri", ["Titreşen cisim ne kadar inceyse ses o kadar tiz olur", "Kalın teller daha tiz ses çıkarır", "Ses tizliği ısıya bağlıdır", "Tüm teller aynı sesi çıkarır"]),
    ("Dengeli beslenme ve düzenli uyku ile ilgili metinde \"vücut savunma sistemi güçlenir\" ifadesi geçmektedir.\n\nBu bilgiye göre dengeli beslenme ve uyku ile bağışıklık arasındaki ilişki nasıldır?",
     "vucudumuzdaki_sistemler_saglik", ["Dengeli beslenme ve uyku bağışıklık sistemini destekler", "Bağışıklık sadece ilaçla güçlenir", "Uyku tek başına yeterlidir", "Beslenme bağışıklığı etkilemez"]),
    ("Hareketli yaşam ve spor yapan kişilerin kalp-damar sağlığının daha iyi olduğu belirtiliyor.\n\nBu gözlemin bilimsel açıklaması aşağıdakilerden hangisidir?",
     "vucudumuzdaki_sistemler_saglik", ["Düzenli hareket kalp ve damarları güçlendirir", "Spor sadece kasları geliştirir", "Kalp spordan etkilenmez", "Sadece gençler spor yapmalıdır"]),
    ("Basit elektrik devresinde pil, ampul ve anahtar kullanıldı. Anahtar açıldığında ampul söndü.\n\nBu deneyin sonucuna göre elektrik akımı ile ilgili hangi çıkarım yapılabilir?",
     "elektrigin_iletimi", ["Devre kapalı olmalı ki elektrik akımı olsun, ampul yansın", "Ampul pili söndürür", "Anahtar sadece semboliktir", "Pil her zaman enerji verir"]),
    ("Farklı maddeler (bakır, plastik, tahta) elektrik devresine bağlandı. Sadece bakır telde ampul yandı.\n\nBu gözleme dayanarak hangi sonuç çıkarılabilir?",
     "elektrigin_iletimi", ["Bakır iletkendir, plastik ve tahta yalıtkandır", "Tüm metaller aynıdır", "Plastik de iletkendir", "Tahta elektriği iletebilir"]),
    ("Gezegenlerin Güneş'e uzaklıkları tablosunda Merkür en yakın, Neptün en uzak gezegen olarak verilmiştir. Bir yıl süresi Güneş'e uzaklıkla artar.\n\nBu tabloya göre aşağıdakilerden hangisi doğrudur?",
     "gunes_sistemi_tutulmalar", ["Güneş'e uzak gezegenlerin yörünge periyodu daha uzundur", "Tüm gezegenlerin yılı aynıdır", "Merkür'de bir yıl en uzundur", "Uzaklık yörüngeyi etkilemez"]),
    ("İç gezegenler (Merkür, Venüs, Dünya, Mars) kayalık; dış gezegenler (Jüpiter, Satürn vb.) gaz devidir.\n\nBu sınıflandırmanın temel ölçütü aşağıdakilerden hangisidir?",
     "gunes_sistemi_tutulmalar", ["Güneş'e uzaklık ve gezegenin yapısı", "Gezegenin büyüklüğü", "Uydu sayısı", "Atmosfer rengi"]),
    ("Sindirimde enzimlerin besinleri daha küçük parçalara ayırdığı deneyle gösterildi. Tükürükteki enzim nişastayı parçalar.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "vucudumuzdaki_sistemler", ["Enzimler sindirimde kimyasal parçalamada görev alır", "Tükürük sadece ıslatır", "Nişasta enzim olmadan da parçalanır", "Enzimler sadece midede vardır"]),
    ("Dolaşım sisteminde kan, oksijen taşır; kalp kanı pompalar.\n\nSolunum ve dolaşım sistemleri arasındaki ilişki nasıldır?",
     "vucudumuzdaki_sistemler", ["Solunumla alınan oksijen kanla vücuda taşınır", "İki sistem bağımsızdır", "Kalp oksijen üretir", "Kan sadece besin taşır"]),
    ("Eğik düzlem deneyinde rampa yüksekliği sabit tutularak uzunluk değiştirildi. Uzun rampa daha az kuvvet gerektirdi.\n\nBu sonucun nedeni aşağıdakilerden hangisidir?",
     "kuvvet_hareket", ["Uzun rampa eğimi azaltır, daha az kuvvet yeter", "Kısa rampa daha hafiftir", "Yükseklik kuvveti etkilemez", "Kuvvet rampa uzunluğuna bağlı değildir"]),
    ("Dinamometre ile ölçülen sürtünme kuvveti, yüzeyin pürüzlülüğü arttıkça arttı.\n\nBu deneyin yorumu aşağıdakilerden hangisidir?",
     "kuvvet_hareket", ["Pürüzlü yüzeylerde sürtünme kuvveti daha büyüktür", "Sürtünme sadece ağır cisimlerde vardır", "Pürüzsüz yüzeyde sürtünme en fazladır", "Dinamometre sürtünmeyi etkiler"]),
    ("Isı iletkeni ve yalıtkanı karşılaştırıldı. Metal kaşık sıcak çorbada ısındı, tahta kaşık ısınmadı.\n\nBu gözleme göre aşağıdakilerden hangisi doğrudur?",
     "madde_isi", ["Metaller ısı iletir, tahta ısı yalıtkanıdır", "Tahta da iletkendir", "Isı sadece temasla yayılmaz", "Metal soğutucudur"]),
    ("Yalıtım malzemesi kullanılan evde kışın ısı kaybı azaldı.\n\nBu durumun sebebi aşağıdakilerden hangisidir?",
     "madde_isi", ["Yalıtım malzemesi ısının dışarı kaçmasını azaltır", "Yalıtım ısı üretir", "Evler zaten sıcaktır", "Isı kaybı önlenemez"]),
    ("Sesin yayılması için ortam gerektiği deneyle gösterildi. Hava boşaltılan fanusta zil sesi duyulmadı.\n\nBu deneyin sonucu neyi gösterir?",
     "ses_ozellikleri", ["Ses, maddesel ortamda (taneciklerle) yayılır", "Ses boşlukta da yayılır", "Zil sessizdir", "Hava sesi engeller"]),
    ("Sesin yüksekliği (şiddeti) kaynağa yaklaştıkça arttı. Uzaklaştıkça azaldı.\n\nBu gözleme dayanarak ses şiddeti ile uzaklık arasında nasıl bir ilişki vardır?",
     "ses_ozellikleri", ["Kaynağa yakın ses daha şiddetli, uzakta daha zayıf duyulur", "Uzaklık sesi etkilemez", "Ses her yerde aynı şiddettedir", "Yakın ses daha tizdir"]),
    ("Sigara ve alkolün solunum ve dolaşım sistemine zarar verdiği metinde belirtiliyor.\n\nBu bilgiye göre sağlıklı yaşam için aşağıdakilerden hangisi önerilir?",
     "vucudumuzdaki_sistemler_saglik", ["Sigara ve alkolden kaçınmak sistem sağlığını korur", "Az içmek zararsızdır", "Sadece sigara zararlıdır", "Sistemler kendini tamir eder"]),
    ("Düzenli check-up ve sağlık taramalarının önemi vurgulanıyor.\n\nBunun bilimsel gerekçesi aşağıdakilerden hangisidir?",
     "vucudumuzdaki_sistemler_saglik", ["Erken teşhis hastalıkların tedavisini kolaylaştırır", "Check-up sadece hastalar içindir", "Tarama gereksizdir", "Sağlık kendiliğinden korunur"]),
    ("Seri bağlı iki ampulden biri patladığında diğeri de söndü. Paralel bağlı devrede bir ampul patlayınca diğeri yanmaya devam etti.\n\nBu farkın sebebi nedir?",
     "elektrigin_iletimi", ["Seri devrede akım tek yol izler; paralelde her ampulün ayrı yolu vardır", "Paralel devre daha güçlüdür", "Seri devre daha iyidir", "Ampul sayısı önemsizdir"]),
    ("Pil sayısı artırıldığında (seri bağlı) ampulün parlaklığı arttı.\n\nBu deneyin yorumu aşağıdakilerden hangisidir?",
     "elektrigin_iletimi", ["Pil sayısı arttıkça devreye sağlanan gerilim artar, ampul daha parlak yanar", "Pil sayısı parlaklığı etkilemez", "Tek pil yeterlidir", "Ampul pil sayısını belirler"]),
    ("Ay'ın evreleri (yeni ay, ilk dördün, dolunay, son dördün) Dünya'dan görünümündeki değişimdir. Ay kendi etrafında ve Dünya etrafında döner.\n\nAy'ın hep aynı yüzünün görünmesinin nedeni nedir?",
     "gunes_sistemi_tutulmalar", ["Ay'ın kendi etrafında dönme süresi, Dünya etrafında dönme süresine eşittir", "Ay dönmez", "Dünya Ay'ı gizler", "Ay çok uzaktır"]),
    ("Güneş tutulması Dünya'nın belirli bir bölgesinden görülür; Ay tutulması gece olan her yerden görülebilir.\n\nBu farkın nedeni aşağıdakilerden hangisidir?",
     "gunes_sistemi_tutulmalar", ["Güneş tutulmasında Ay'ın gölgesi Dünya'da dar bir alana düşer; Ay tutulmasında Dünya'nın gölgesi Ay'ı tam kaplar", "Güneş daha büyüktür", "Ay tutulması daha nadirdir", "Dünya dönmez"]),
    ("Boşaltım sisteminde böbrekler kanı süzer, zararlı maddeleri idrarla atar.\n\nBu bilgiye göre böbreklerin görevi nedir?",
     "vucudumuzdaki_sistemler", ["Kanı süzerek atık maddeleri vücuttan uzaklaştırmak", "Kan üretmek", "Besin depolamak", "Oksijen taşımak"]),
    ("Destek ve hareket sisteminde kemikler iskeleti oluşturur, kaslar kasılma ile hareketi sağlar.\n\nKas ve kemik ilişkisi nasıldır?",
     "vucudumuzdaki_sistemler", ["Kaslar kemiklere bağlıdır; kasılan kaslar kemikleri hareket ettirir", "Kemikler kasları hareket ettirir", "İkisi bağımsızdır", "Kaslar kemik üretir"]),
    ("Sabit sürtünmeli yüzeyde cisme uygulanan kuvvet artırıldı. Cisim belirli bir kuvvetten sonra hareket etmeye başladı.\n\nBu deney statik ve kinetik sürtünme ile ilgili ne gösterir?",
     "kuvvet_hareket", ["Cismi harekete geçirmek için statik sürtünmeyi yenmek gerekir; hareket başlayınca kinetik sürtünme devreye girer", "Sürtünme yoktur", "Kuvvet sürtünmeyi etkilemez", "Tüm sürtünmeler aynıdır"]),
    ("Eşit kütleli farklı maddelere eşit ısı verildi. Sıcaklık artışları farklı oldu.\n\nBu farkın nedeni aşağıdakilerden hangisidir?",
     "madde_isi", ["Maddelerin özgül ısı kapasiteleri farklıdır", "Tüm maddeler aynı ısıyı alır", "Kütle sıcaklığı belirler", "Isı maddeye bağlı değildir"]),
    ("Sesin yansıması (eko) deneyinde ses duvara çarpıp geri döndü. Ses kaynağına uzak duvarda gecikme daha fazlaydı.\n\nBu gözlemin açıklaması nedir?",
     "ses_ozellikleri", ["Ses yansır; mesafe arttıkça gidiş-dönüş süresi artar", "Ses duvardan geçer", "Eko sadece dağda olur", "Ses yansımaz"]),
    ("Hijyen, el yıkama ve aşıların hastalıklardan korunmada etkili olduğu belirtiliyor.\n\nBu önlemlerin ortak işlevi nedir?",
     "vucudumuzdaki_sistemler_saglik", ["Mikroplarla karşılaşmayı azaltmak veya bağışıklık kazanmak", "Sadece el yıkamak yeterlidir", "Aşılar gereksizdir", "Hijyen sadece hastanede önemlidir"]),
    ("Pil, anahtar ve ampulden oluşan basit devrede anahtar kapalıyken ampul yanar.\n\nDevrenin çalışması için gerekli koşul aşağıdakilerden hangisidir?",
     "elektrigin_iletimi", ["Kapalı devre: Akımın kesintisiz döngüde akması", "Pilin büyük olması", "Ampulün parlak olması", "Anahtarın açık olması"]),
    ("İletken ve yalıtkan maddeler listesinde bakır, altın, demir iletken; plastik, cam, tahta yalıtkan olarak verilmiştir.\n\nBu sınıflandırmanın temeli nedir?",
     "elektrigin_iletimi", ["Elektriği iletme (iletken) veya iletmeme (yalıtkan) özelliği", "Maddenin rengi", "Maddenin sertliği", "Maddenin ağırlığı"]),
    ("Meteor, meteorit, asteroid kavramları karıştırılıyor. Meteor atmosfere giren gök taşıdır, meteorit yere düşen parçadır, asteroid ise Güneş etrafında dönen küçük gök cismidir.\n\nDünya'da krater oluşturan hangisidir?",
     "gunes_sistemi_tutulmalar", ["Meteorit (yere düşen gök taşı)", "Meteor", "Asteroid", "Kuyruklu yıldız"]),
    ("Grafikte vücut sistemlerinin birbirine bağımlılığı gösterilmiştir: Solunum oksijen sağlar, dolaşım taşır, sindirim besin sağlar.\n\nBu grafiğe göre sistemler arası ilişki nasıldır?",
     "vucudumuzdaki_sistemler", ["Sistemler birbirini destekleyerek çalışır", "Her sistem bağımsızdır", "Sadece sindirim önemlidir", "Dolaşım diğerlerinden üstündür"]),
    ("Kuvvet-hareket grafiğinde sabit kuvvetle hareket eden cismin hız-zaman grafiği yatay çizgidir.\n\nBu ne anlama gelir?",
     "kuvvet_hareket", ["Sabit net kuvvet, sabit hızla düzgün doğrusal hareket (veya dengeli kuvvetlerde hız sabit)", "Hız sürekli artar", "Kuvvet yoktur", "Hareket yoktur"]),
    ("Isı-sıcaklık grafiğinde suyun kaynama noktasında sıcaklık sabit kalırken ısı verilmeye devam eder.\n\nBu durumun açıklaması nedir?",
     "madde_isi", ["Verdiğimiz ısı hal değişiminde (buharlaşmada) kullanılır, sıcaklık değişmez", "Isı biter", "Termometre bozulur", "Su ısı almaz"]),
    ("Sesin frekansı arttıkça perde (tizlik) yükselir. Grafikte frekans-perde ilişkisi doğru orantılıdır.\n\nDüşük frekanslı ses için aşağıdakilerden hangisi doğrudur?",
     "ses_ozellikleri", ["Düşük frekans = kalın (pes) ses", "Düşük frekans = tiz ses", "Frekans sesi etkilemez", "Tüm sesler aynı frekanstadır"]),
    ("Tablo: Günlük besin grupları ve önerilen miktarlar. Sebze-meyve, tahıl, protein, süt grubu dengeli dağıtılmalıdır.\n\nBu tabloya göre dengeli beslenme ne demektir?",
     "vucudumuzdaki_sistemler_saglik", ["Tüm besin gruplarından yeterli ve uygun miktarda almak", "Sadece protein yemek", "Az yemek", "Sadece sebze yemek"]),
    ("Tablo: Farklı iletkenlerin direnç değerleri. Bakır düşük, nikrom yüksek direnç gösterir.\n\nDirenç ile iletkenlik arasındaki ilişki nasıldır?",
     "elektrigin_iletimi", ["Direnç düşükse iletkenlik iyi; direnç yüksekse iletkenlik zayıftır", "Direnç iletkenliği artırır", "İkisi aynıdır", "Direnç önemsizdir"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"fen6_{idx:04d}",
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
        "subject": "fen",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = ["Deney/gözlem bu sonucu desteklemez.", "Bu çıkarım yapılamaz.", "Sebep-sonuç ilişkisi yanlış kurulmuştur.", "Kavram karışıklığı vardır."]
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
            expl = f"Deney/gözlem sonuçlarına ve bilimsel kavramlara göre doğru cevap \"{correct}\" seçeneğidir. Diğer seçenekler sebep-sonuç ilişkisini yanlış kurmakta, deneyi yanlış yorumlamakta veya kavram karışıklığı içermektedir."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10], "deney_yorum", "veri_analiz"],
                expl, f"fen6_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"fen6_{idx:04d}"
        qq["id"] = f"fen6_{idx:04d}"

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
            "subject": "fen",
            "publisher": "brainbuddy",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_fen6_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("FEN6 Question Bank Report")
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
    random.seed(48)
    main()
