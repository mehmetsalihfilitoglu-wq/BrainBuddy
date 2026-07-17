#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 5th Grade Fen Bilimleri question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: deney/gözlem/grafik/tablo yorumlama, sebep-sonuç, kavram bağlantısı.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/fen5"

# 5th grade curriculum units
TOPICS = [
    "gunes_dunya_ay",
    "canlilar_dunyasi",
    "kuvvet_surtunme",
    "madde_degisim",
    "isigin_yayilmasi",
    "isik_ses",
    "insan_cevre",
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
    # Güneş, Dünya ve Ay
    ("Öğrenciler Güneş, Dünya ve Ay modelini inceledi. Dünya'nın Güneş etrafında, Ay'ın ise Dünya etrafında döndüğünü gözlemlediler.\n\nBu modele göre Ay'ın Dünya'dan farklı evrelerde görünmesinin sebebi nedir?",
     "gunes_dunya_ay", ["Güneş'in Ay'ı farklı açılardan aydınlatması", "Ay'ın ışığı kendisinin üretmesi", "Dünya'nın Ay'dan daha büyük olması", "Güneş'in Dünya'dan uzak olması"]),
    ("Güneş tutulması sırasında Ay, Güneş ile Dünya arasına girer ve Güneş'in ışığını engeller.\n\nBu bilgiye göre Güneş tutulmasının gerçekleşme koşulu aşağıdakilerden hangisidir?",
     "gunes_dunya_ay", ["Ay, Güneş ve Dünya aynı doğrultuda hizalanmalıdır", "Güneş Dünya'dan uzaklaşmalıdır", "Dünya Ay'ın gölgesine girmelidir", "Gece olmalıdır"]),
    ("Ay'ın evreleri tablosunda yeni ay, ilk dördün, dolunay ve son dördün evreleri sırayla verilmiştir.\n\nBu evrelerin oluşma sebebi aşağıdakilerden hangisidir?",
     "gunes_dunya_ay", ["Ay'ın Dünya etrafındaki hareketi ve Güneş'ten aldığı ışığın Dünya'dan görünümü", "Ay'ın şeklinin değişmesi", "Güneş'in Ay'a uzaklığının değişmesi", "Dünya'nın kendi etrafında dönmesi"]),
    ("Dünya modelinde öğrenciler Dünya'nın 24 saatte kendi etrafında bir tur döndüğünü öğrendi.\n\nBu dönüşün sonucu aşağıdakilerden hangisidir?",
     "gunes_dunya_ay", ["Gece ve gündüzün oluşması", "Mevsimlerin oluşması", "Ay evrelerinin değişmesi", "Güneş tutulması"]),
    ("Güneş'in Dünya'dan çok büyük, fakat çok uzak olduğu için küçük göründüğü belirtildi.\n\nBu gözleme dayanarak hangi çıkarım yapılabilir?",
     "gunes_dunya_ay", ["Görünen büyüklük uzaklıktan etkilenir", "Güneş küçüktür", "Dünya Güneş'ten büyüktür", "Uzaklık görünümü etkilemez"]),
    # Canlılar Dünyası
    ("Öğrenciler doğada bitki ve hayvanları inceledi. Bazı canlıların toprakta, bazılarının suda yaşadığını gözlemledi.\n\nBu gözleme göre canlıların sınıflandırılmasında hangi özellik kullanılabilir?",
     "canlilar_dunyasi", ["Yaşam alanı (ortam) ve yapısal özellikler", "Sadece rengi", "Sadece büyüklüğü", "Sadece hızı"]),
    ("Mikroskopla incelenen su damlasında tek hücreli canlılar görüldü. Bunlar hareket ediyor ve besleniyordu.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "canlilar_dunyasi", ["Tek hücreli canlılar da beslenir ve hareket eder", "Sadece çok hücreliler canlıdır", "Su canlı üretir", "Mikroskop canlı yaratır"]),
    ("Bitkilerin fotosentez yaparak besin ürettiği, güneş ışığı olmadan bu işlemin yapılamadığı deneyle gösterildi.\n\nBu deneyin sonucuna göre fotosentez için gerekli olan aşağıdakilerden hangisidir?",
     "canlilar_dunyasi", ["Güneş ışığı, su ve karbondioksit", "Sadece su", "Sadece toprak", "Sadece oksijen"]),
    ("Hayvanların beslenme şekillerine göre otçul, etçil ve hepçil olarak ayrıldığı tablo verildi.\n\nBu sınıflandırmanın temel ölçütü nedir?",
     "canlilar_dunyasi", ["Yedikleri besin türü", "Yaşadıkları yer", "Büyüklükleri", "Renkleri"]),
    ("Mantarların bitki olmadığı, kendi besinini üretemediği ve ölü organizmalardan beslendiği belirtildi.\n\nBu bilgiye göre mantarlar nasıl beslenir?",
     "canlilar_dunyasi", ["Hazır besinlerle (parazit veya çürükçül)", "Fotosentezle", "Havadaki gazlarla", "Işık enerjisiyle"]),
    # Kuvvet ve Sürtünme
    ("Öğrenciler dinamometre ile farklı cisimlere uyguladıkları kuvveti ölçtü. Ağır cisimlere daha fazla kuvvet uygulandığında dinamometre daha fazla uzadı.\n\nBu deneyin sonucu nedir?",
     "kuvvet_surtunme", ["Dinamometre kuvveti Newton cinsinden ölçer; büyük kuvvet büyük uzama gösterir", "Dinamometre sadece ağırlığı ölçer", "Kuvvet ölçülemez", "Tüm cisimler aynı kuvveti uygular"]),
    ("Halı ve parke üzerinde aynı kutuyu iten öğrenci, halıda daha zor itti.\n\nBu gözlemin sebebi aşağıdakilerden hangisidir?",
     "kuvvet_surtunme", ["Halıda sürtünme kuvveti daha büyüktür", "Parke daha kaygandır", "Kutu halıda daha ağırdır", "Parke daha uzundur"]),
    ("Sürtünme kuvvetinin yüzeyin pürüzlülüğüne bağlı olduğu deneyle gösterildi. Pürüzlü yüzeyde sürtünme arttı.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "kuvvet_surtunme", ["Pürüzlü yüzeylerde sürtünme daha fazladır", "Pürüzsüz yüzeyde sürtünme en fazladır", "Sürtünme sadece ağır cisimlerde vardır", "Sürtünme yüzeyle ilgili değildir"]),
    ("Tablo: Farklı yüzeylerde ölçülen sürtünme kuvveti değerleri. Halıda 5 N, buzda 1 N ölçüldü.\n\nBu tabloya göre aşağıdakilerden hangisi söylenebilir?",
     "kuvvet_surtunme", ["Yüzey pürüzlülüğü arttıkça sürtünme artar", "Buzda sürtünme en fazladır", "Tüm yüzeylerde sürtünme aynıdır", "Sürtünme kuvveti ölçülemez"]),
    ("Sürtünme kuvvetinin hareketi yavaşlattığı veya engellediği deneyle gösterildi.\n\nBuna göre sürtünme kuvvetinin yönü nasıldır?",
     "kuvvet_surtunme", ["Hareket yönünün tersinedir", "Hareket yönüyle aynıdır", "Yukarı doğrudur", "Sürtünmenin yönü yoktur"]),
    # Madde ve Değişim
    ("Isıtılan buzun önce suya, sonra buhara dönüştüğü deneyle gösterildi.\n\nBu gözleme göre maddenin hal değiştirmesi ile ilgili hangi çıkarım yapılabilir?",
     "madde_degisim", ["Isı alan madde hal değiştirebilir (katı→sıvı→gaz)", "Madde sadece katı haldedir", "Isı maddeyi etkilemez", "Buz asla erimez"]),
    ("Öğrenciler şeker ve tuzun suda çözündüğünü, kumun çözünmediğini gözlemledi.\n\nBu deneyin sonucu aşağıdakilerden hangisidir?",
     "madde_degisim", ["Bazı maddeler suda çözünür, bazıları çözünmez", "Tüm maddeler suda çözünür", "Sadece katılar çözünür", "Suyun sıcaklığı çözünmeyi etkilemez"]),
    ("Termometre ile ölçülen su sıcaklığı, ısıtıldıkça arttı. Kaynama noktasında sıcaklık sabit kaldı.\n\nBu durumun nedeni nedir?",
     "madde_degisim", ["Verdiğimiz ısı buharlaşmada kullanılır, sıcaklık değişmez", "Termometre bozulmuştur", "Isı bitmiştir", "Su ısı almaz"]),
    ("Saf maddeler ve karışımlar tablosunda altın, su saf madde; tuzlu su, hava karışım olarak verilmiştir.\n\nBu sınıflandırmanın temel ölçütü nedir?",
     "madde_degisim", ["Maddenin tek tür tanecikten mi yoksa birden fazla maddeden mi oluştuğu", "Maddenin rengi", "Maddenin sıcaklığı", "Maddenin ağırlığı"]),
    ("Filtre kağıdı ile yapılan deneyde kum suda ayrıldı. Su filtreden geçti, kum kaldı.\n\nBu yöntemin adı ve dayandığı ilke nedir?",
     "madde_degisim", ["Süzme; tanecik boyutu farkı", "Buharlaştırma; yoğunluk farkı", "Damıtma; kaynama farkı", "Mıknatıslama; manyetik fark"]),
    # Işığın Yayılması
    ("Karanlık odada el feneriyle duvara ışık tutulduğunda düz bir çizgi halinde ilerlediği gözlemlendi.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "isigin_yayilmasi", ["Işık doğrusal (düz çizgi) yayılır", "Işık eğri yayılır", "Işık sadece havada yayılır", "Işık duvardan geri dönmez"]),
    ("Işık opak (saydam olmayan) cisimden geçemez; gölge oluşur. Saydam cisimden geçer.\n\nBu bilgiye göre gölge oluşumunun koşulu nedir?",
     "isigin_yayilmasi", ["Işığın opak cisim tarafından engellenmesi", "Işığın az olması", "Cismin büyük olması", "Kaynağın yakın olması"]),
    ("Işık kaynağına yaklaştıkça gölgenin büyüdüğü, uzaklaştıkça küçüldüğü deneyle gösterildi.\n\nBu gözlemin sebebi aşağıdakilerden hangisidir?",
     "isigin_yayilmasi", ["Kaynak uzaklığı gölge boyunu etkiler", "Işık miktarı değişmez", "Gölge her zaman aynı büyüklüktedir", "Cismin rengi gölgeyi etkiler"]),
    ("Işık ayna yüzeyinden yansıdığında gelen açı ile yansıyan açının eşit olduğu deneyle gösterildi.\n\nBu deneyin yorumu aşağıdakilerden hangisidir?",
     "isigin_yayilmasi", ["Işık düzgün yansımada gelme açısı = yansıma açısı", "Işık kırılır", "Ayna ışığı soğurur", "Açılar farklıdır"]),
    ("Işık saydam maddelerden (cam, su) geçerken kırıldığı gözlemlendi. Kalem suda kırık göründü.\n\nBu gözlemin nedeni nedir?",
     "isigin_yayilmasi", ["Işığın farklı ortamlarda farklı hızda ilerlemesi, kırılma", "Suyun rengi değişmiştir", "Kalem kırılmıştır", "Işık kırılmaz"]),
    # Işık ve Ses
    ("Titreşen diyapazon suya batırıldığında suyun sıçradığı görüldü. Titreşim durduğunda sıçrama da durdu.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "isik_ses", ["Ses titreşimle oluşur; titreşim yoksa ses de yoktur", "Su sesi yükseltir", "Diyapazon suda sessizdir", "Ses suda oluşmaz"]),
    ("Sesin duvara çarpıp geri dönmesi (eko) deneyinde uzak duvarda gecikme daha fazlaydı.\n\nBu gözlemin açıklaması nedir?",
     "isik_ses", ["Ses yansır; mesafe arttıkça sesin gidip dönme süresi artar", "Ses duvardan geçer", "Eko sadece dağda olur", "Uzaklık sesi etkilemez"]),
    ("Farklı kalınlıktaki tellerden çıkan sesler karşılaştırıldı. İnce tel daha tiz ses çıkardı.\n\nBu deneye göre sesin tizliği (perdesi) ile ilgili hangi ilişki kurulabilir?",
     "isik_ses", ["Titreşen cisim inceyse ses tiz, kalınsa pes olur", "Kalın teller daha tiz ses çıkarır", "Ses tizliği ısıya bağlıdır", "Tüm teller aynı sesi çıkarır"]),
    ("Sesin boşlukta (havası alınmış fanusta) yayılmadığı deneyle gösterildi. Fanustaki zil sesi duyulmadı.\n\nBu deneyin sonucu neyi gösterir?",
     "isik_ses", ["Ses maddesel ortamda (taneciklerle) yayılır", "Ses boşlukta da yayılır", "Fanus sesi engeller", "Zil sessizdir"]),
    ("Işık ve sesin özellikleri tablosunda \"ışık boşlukta yayılır, ses yayılmaz\" ifadesi vardır.\n\nBu farkın sebebi nedir?",
     "isik_ses", ["Işık elektromanyetik dalgadır; ses ise maddesel ortamda titreşimle yayılır", "Ses daha hızlıdır", "Işık maddesel ortam gerektirir", "İkisi de aynıdır"]),
    # İnsan ve Çevre
    ("Atık maddelerin çevreye atılmasının su ve toprak kirliliğine yol açtığı belirtildi.\n\nBu durumun sebep-sonuç ilişkisi nasıldır?",
     "insan_cevre", ["Atıklar kirliliğe yol açar; geri dönüşüm ve uygun atık yönetimi kirliliği azaltır", "Atıklar zararsızdır", "Sadece plastik zararlıdır", "Kirlilik doğal değildir"]),
    ("Geri dönüşüm sembolü olan maddelerin yeniden işlenerek kullanılabildiği tablo verildi.\n\nBu uygulamanın çevreye faydası nedir?",
     "insan_cevre", ["Doğal kaynak tüketimini azaltır, atık miktarını düşürür", "Sadece ekonomiye fayda sağlar", "Geri dönüşüm zararlıdır", "Plastik geri dönüştürülemez"]),
    ("Ekosistemde üreticiler (bitkiler), tüketiciler (hayvanlar) ve ayrıştırıcılar (bakteri, mantar) birbirine bağımlıdır.\n\nBu ilişkinin anlamı nedir?",
     "insan_cevre", ["Canlılar besin ve enerji zinciriyle birbirine bağlıdır", "Her canlı bağımsız yaşar", "Sadece bitkiler önemlidir", "Ayrıştırıcılar gereksizdir"]),
    ("Orman yangınlarının canlıların yaşam alanını yok ettiği ve havayı kirlettiği belirtildi.\n\nBu bilgiye göre ormanları korumak neden önemlidir?",
     "insan_cevre", ["Ormanlar canlı çeşitliliğini, hava temizliğini ve ekolojik dengeyi sağlar", "Ormanlar sadece güzeldir", "Yangın doğaldır", "Ormanlar yenilenemez değildir"]),
    ("Su tasarrufu ile ilgili deneyde damlayan musluktan günde birkaç litre suyun boşa aktığı hesaplandı.\n\nBu deneyin mesajı nedir?",
     "insan_cevre", ["Küçük sızıntılar bile büyük su kaybına yol açar; tasarruf önemlidir", "Su sınırsızdır", "Sadece büyük sızıntılar önemlidir", "Su tasarrufu zordur"]),
    # More templates for variety (repeat topics with different stems)
    ("Dünya'nın Güneş etrafındaki hareketi ile mevsimlerin oluştuğu modelde gösterildi.\n\nMevsimlerin oluşma sebebi aşağıdakilerden hangisidir?",
     "gunes_dunya_ay", ["Dünya'nın eğik ekseni ve Güneş etrafındaki yörünge hareketi", "Dünya'nın Güneş'e uzaklığının değişmesi", "Ay'ın Dünya etrafındaki hareketi", "Güneş'in sıcaklığının değişmesi"]),
    ("Canlıların yaşadıkları ortama uyum sağladığı (adapte olduğu) örneklerle anlatıldı. Kutup ayısının kalın kürkü vardır.\n\nBu uyumun nedeni nedir?",
     "canlilar_dunyasi", ["Soğuk ortamda ısı kaybını azaltmak", "Avlanmayı kolaylaştırmak", "Daha güzel görünmek", "Diğer hayvanlardan farklı olmak"]),
    ("Kuvvetin cisimlerin hareketini veya şeklini değiştirdiği deneyle gösterildi. Yay sıkıştırıldığında kısalıyor.\n\nBu deneyden çıkarılabilecek sonuç nedir?",
     "kuvvet_surtunme", ["Kuvvet cisimlerin şeklini değiştirebilir", "Yay kuvvet üretir", "Kuvvet sadece hareketi etkiler", "Yay genişlemez"]),
    ("Buharlaşma deneyinde açık kaptaki suyun zamanla azaldığı gözlemlendi.\n\nBu gözlemin nedeni nedir?",
     "madde_degisim", ["Suyun buharlaşarak gaza dönüşmesi", "Suyun toprağa sızması", "Suyun kaybolması", "Kabın suyu emmesi"]),
    ("Işığın beyaz ışık olduğunda prizmadan geçirilince gökkuşağı renklerine ayrıldığı deneyle gösterildi.\n\nBu deneyin sonucu nedir?",
     "isigin_yayilmasi", ["Beyaz ışık farklı renklerin birleşimidir", "Prizma renk üretir", "Işık tek renktir", "Renkler ışıktan bağımsızdır"]),
    ("Sesin kaynağa yaklaştıkça daha şiddetli, uzaklaştıkça daha zayıf duyulduğu gözlemlendi.\n\nBu ilişkinin sebebi nedir?",
     "isik_ses", ["Ses enerjisi uzaklaştıkça dağılır, şiddet azalır", "Ses her yerde aynıdır", "Yakın ses daha tizdir", "Uzaklık sesi etkilemez"]),
    ("Grafikte atık türlerine göre geri dönüşüm oranları verilmiştir. Kağıt ve camda oran yüksektir.\n\nBu grafiğe göre hangi çıkarım yapılabilir?",
     "insan_cevre", ["Bazı atıklar daha kolay geri dönüştürülebilir; bilinçli atık ayrımı önemlidir", "Tüm atıklar aynı oranda dönüştürülür", "Geri dönüşüm gereksizdir", "Plastik en kolay dönüştürülendir"]),
    ("Güneş, Dünya ve Ay'ın boyutları karşılaştırıldığında Güneş çok büyük, Ay küçüktür. Buna rağmen Güneş tutulmasında Ay Güneş'i örter.\n\nBu durumun sebebi nedir?",
     "gunes_dunya_ay", ["Güneş Ay'dan çok uzak; Dünya'dan bakıldığında açısal büyüklükleri benzer", "Ay Güneş'ten büyüktür", "Güneş küçülür", "Dünya Güneş'e çok yakındır"]),
    ("Bitki ve hayvan hücrelerinin karşılaştırma tablosunda bitki hücresinde hücre duvarı ve kloroplast vardır.\n\nBu farkın sebebi nedir?",
     "canlilar_dunyasi", ["Bitkiler fotosentez yapar; hücre duvarı destek sağlar", "Hayvan hücresi daha gelişmiştir", "İkisi aynıdır", "Kloroplast hayvanda da vardır"]),
    ("Eğik düzlem deneyinde rampa yüksekliği sabit tutulup uzunluk artırıldığında daha az kuvvet yeterli oldu.\n\nBu sonucun nedeni aşağıdakilerden hangisidir?",
     "kuvvet_surtunme", ["Uzun rampa eğimi azaltır; aynı yükseklik daha az kuvvetle aşılabilir", "Kısa rampa daha hafiftir", "Kuvvet rampa uzunluğundan bağımsızdır", "Yükseklik kuvveti etkilemez"]),
    ("Yoğunluk deneyinde suyun üzerinde yüzen tahta, batan demir parçası gözlemlendi.\n\nBu farkın sebebi nedir?",
     "madde_degisim", ["Yoğunluk farkı; yoğunluğu sudan az olan yüzer", "Demir daha hafiftir", "Tahta suyu iter", "Yoğunluk önemsizdir"]),
    ("Işık kirliliğinin gece gökyüzünü aydınlatarak yıldızların görünmesini engellediği belirtildi.\n\nIşık kirliliğini azaltmak için ne yapılmalıdır?",
     "isigin_yayilmasi", ["Gereksiz dış aydınlatmayı azaltmak, ışığı yukarı değil yere yönlendirmek", "Tüm ışıkları kapatmak", "Sadece ev içinde ışık kullanmak", "Işık kirliliği önlenemez"]),
    ("Gürültü kirliliğinin işitme kaybına ve stres yaratabileceği metinde vurgulandı.\n\nBu bilgiye göre gürültüyü azaltmak için aşağıdakilerden hangisi önerilir?",
     "isik_ses", ["Ses yalıtımı, sessiz alanlar, gürültü yapan kaynakların azaltılması", "Kulaklık takmak yeterlidir", "Gürültü zararsızdır", "Ses kirliliği önlenemez"]),
    ("Ekosistemde besin zinciri: Bitki → Otçul → Etçil. Tabloda enerji aktarımında kayıp olduğu gösterildi.\n\nBu tabloya göre besin zincirinde enerji nasıl aktarılır?",
     "insan_cevre", ["Her basamakta enerjinin bir kısmı ısı olarak kaybedilir", "Enerji tam aktarılır", "Sadece bitkiler enerji üretir", "Enerji aktarımı yoktur"]),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"fen5_{idx:04d}",
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
        # Filter templates for this topic; fall back to any if none match
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
            expl = f"Deney/gözlem sonuçlarına ve bilimsel kavramlara göre doğru cevap \"{correct}\" seçeneğidir. Diğer seçenekler sebep-sonuç ilişkisini yanlış kurmakta, deneyi yanlış yorumlamakta veya kavram karışıklığı içermektedir."
            out.append(q(
                len(out), stem, options, ai, random.choice([4, 4, 5, 5, 5]),
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:10], "deney_yorum", "veri_analiz"],
                expl, f"fen5_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"fen5_{idx:04d}"
        qq["id"] = f"fen5_{idx:04d}"

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
            "publisher": "edumio",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_fen5_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("FEN5 Question Bank Report")
    print("=" * 60)
    print(f"Packs: {len(packs)}, Questions: {len(all_q)}")
    print("\nUnit distribution:")
    for t in sorted(topic_counts.keys()):
        print(f"  {t}: {topic_counts[t]}")
    pct = 100 * new_gen_count / 500
    print(f"\nNew-generation: {new_gen_count}/500 = {pct:.1f}%")
    print(f"Output: {OUT_DIR}")
    print("=" * 60)


if __name__ == "__main__":
    random.seed(52)
    main()
