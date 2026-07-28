#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 7th Grade Science (Fen Bilimleri) question bank for EDUmio.
50 packs × 10 questions = 500 questions.
EDUmio Question Design Standard: experiment interpretation, data analysis, scenario-based.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/fen7"

UNITS = [
    "gunes_sistemi_otesi",
    "hucre_bolunmeler",
    "kuvvet_enerji",
    "saf_maddeler_karisimlar",
    "isik_madde_etkilesim",
    "canlilarda_ureme",
    "elektrik_devreleri",
]

NEW_GEN_TYPES = ["deney_yorum", "grafik_yorum", "tablo_yorum", "senaryo_analiz", "gozlem_yorum", "veri_analiz"]

# Scenario/experiment-based stems - long, interpretation required
TEMPLATES = [
    ("Bir öğretmen sınıfa Güneş Sistemi modeli getirdi. Modelde gezegenler büyüklük ve Güneş'e uzaklık açısından ölçeklendirilmişti. Öğrenciler Dünya ile Jüpiter'in boyutlarını karşılaştırdı. Öğretmen \"Neden bazı gezegenler \"iç gezegen\", bazıları \"dış gezegen\" olarak adlandırılır?\" diye sordu.",
     "gunes_sistemi_otesi", "Bu model ve soruya göre iç ve dış gezegen ayrımının temel ölçütü aşağıdakilerden hangisidir?",
     ["Gezegenin boyutu", "Gezegenin Güneş'e uzaklığı ve yörünge konumu",
      "Gezegenin uydu sayısı", "Gezegenin atmosferi"],
     1),
    ("Öğrenciler mikroskopla soğan zarı hücrelerini inceledi. Hücre çekirdeği, hücre zarı ve sitoplazmayı gözlemlediler. Ardından ağız içi epitel hücrelerini de incelediler ve iki örnek arasında farklar olduğunu fark ettiler.",
     "hucre_bolunmeler", "Bu gözleme dayanarak aşağıdakilerden hangisi söylenebilir?",
     ["Tüm hücreler aynı yapıdadır", "Bitki ve hayvan hücreleri yapısal farklılıklar gösterebilir",
      "Mikroskop sadece bitki hücresi gösterir", "Hücre çekirdeği her zaman görünmez"],
     1),
    ("Bir öğrenci rampadan aşağı bırakılan topun farklı yüzeylerde farklı mesafeler katettiğini gözlemledi. Halı üzerinde az, parke üzerinde daha fazla yol aldı. Öğretmen \"Bu farkın nedeni ne olabilir?\" diye sordu.",
     "kuvvet_enerji", "Bu deneye göre topun katettiği mesafenin değişmesinin temel nedeni aşağıdakilerden hangisidir?",
     ["Topun ağırlığı", "Yüzeylerin sürtünme katsayısı farkı",
      "Rampanın eğimi", "Topun bırakıldığı yükseklik"],
     1),
    ("Laboratuvarda öğrenciler tuzlu su ve şekerli suyu karıştırdı. Tuzlu suda tuz görünmezken, kum-su karışımında kum dibe çöktü. Öğretmen \"Tuzlu su homojen, kum-su karışımı heterojen mi?\" diye sordu.",
     "saf_maddeler_karisimlar", "Bu gözlemlere dayanarak homojen ve heterojen karışım ayrımı aşağıdakilerden hangisiyle açıklanır?",
     ["Karışan maddelerin miktarı", "Karışımın görünümünde tek veya fazla faz olması",
      "Karışımın sıcaklığı", "Karışımın rengi"],
     1),
    ("Öğrenciler bir ışık demetini düz ayna, çukur ayna ve tümsek aynaya yöneltti. Görüntülerin farklı oluştuğunu gözlemlediler. Düz aynada düz, çukur aynada büyütülmüş görüntü elde ettiler.",
     "isik_madde_etkilesim", "Bu deneyde farklı görüntülerin oluşmasının nedeni aşağıdakilerden hangisidir?",
     ["Işığın rengi", "Aynaların yansıtma yüzeylerinin şekli",
      "Işığın yoğunluğu", "Aynaların büyüklüğü"],
     1),
    ("Bir öğrenci fasulye tohumlarını nemli pamuk üzerinde çimlendirdi. Bir hafta sonra kökün aşağı, gövdenin yukarı doğru büyüdüğünü gözlemledi. Aynı koşullarda farklı tohumlarda da benzer durum gördü.",
     "canlilarda_ureme", "Bu gözleme dayanarak tohum çimlenmesinde kök ve gövde büyüme yönüyle ilgili aşağıdakilerden hangisi söylenebilir?",
     ["Rastgele yönelir", "Kök aşağı (yer çekimi), gövde yukarı yönelir",
      "Her ikisi de yukarı büyür", "Işık yönüne bağlıdır"],
     1),
    ("Öğrenciler basit bir elektrik devresi kurdular. Pil, ampul ve anahtardan oluşan seri devrede ampul yandı. Devreye ikinci bir pil ekleyince ampulün parlaklığı arttı. Anahtarı açınca ampul söndü.",
     "elektrik_devreleri", "Bu deneyde ampulün parlaklığının artmasının nedeni aşağıdakilerden hangisidir?",
     ["Devredeki tel uzunluğu", "Devreye eklenen pil ile gerilim artışı",
      "Ampulün gücü", "Anahtarın konumu"],
     1),
    ("Grafikte bir gezegenin Güneş'e uzaklığına göre yörünge periyodu (yıl süresi) gösterilmiştir. Güneş'e yakın gezegenlerin periyodu kısa, uzak gezegenlerinki uzundur.",
     "gunes_sistemi_otesi", "Bu grafiğe göre aşağıdakilerden hangisi çıkarılabilir?",
     ["Tüm gezegenler aynı hızda döner", "Güneş'e uzaklık arttıkça yörünge periyodu uzar",
      "İç gezegenler daha hızlıdır", "Gezegen boyutu periyodu etkiler"],
     1),
    ("Mitoz bölünmede bir hücre ikiye bölünür. Öğrenciler soğan kökü ucu preparatında farklı aşamaları gözlemledi: kromozomların yoğunlaştığı, ekvator düzleminde dizildiği ve kutuplara çekildiği aşamalar.",
     "hucre_bolunmeler", "Bu gözlemlere dayanarak mitoz bölünmenin temel özelliği aşağıdakilerden hangisidir?",
     ["Yeni genetik çeşitlilik oluşturur", "Ana hücreyle aynı genetik yapıda iki yavru hücre oluşur",
      "Sadece üreme hücrelerinde görülür", "Tek aşamada gerçekleşir"],
     1),
    ("Bir öğrenci sarkacın salınımını inceledi. Sarkacı farklı açılardan bıraktığında salınım süresinin değişmediğini, ancak sarkacın uzunluğunu değiştirdiğinde sürenin değiştiğini gözlemledi.",
     "kuvvet_enerji", "Bu deneyde salınım periyodunu etkileyen temel değişken aşağıdakilerden hangisidir?",
     ["Bırakma açısı", "Sarkacın uzunluğu",
      "Topun kütlesi", "Ortam sıcaklığı"],
     1),
    ("Filtreleme deneyinde tuzlu su-kum karışımı filtreden geçirildi. Kum filtrede kaldı, tuzlu su süzüldü. Süzüntü buharlaştırıldığında tuz geri elde edildi.",
     "saf_maddeler_karisimlar", "Bu deneyde kum ve tuzu ayırmada kullanılan yöntemlerin dayandığı özellikler aşağıdakilerden hangisinde doğru verilmiştir?",
     ["Kum çözünür, tuz çözünmez", "Kum çözünmez (filtreyle ayrılır), tuz çözünür (buharlaştırmayla elde edilir)",
      "Her ikisi de buharlaştırılır", "Kum süzülür, tuz eritilir"],
     1),
    ("Işık prizma içinden geçirildiğinde gökkuşağı renklerine ayrıldı. Öğrenciler kırmızı ışığın en az, mor ışığın en çok kırıldığını gözlemledi.",
     "isik_madde_etkilesim", "Farklı renklerin farklı açılarla kırılmasının nedeni aşağıdakilerden hangisidir?",
     ["Işık şiddeti", "Dalga boyu farkı (her rengin farklı kırılma indisi)",
      "Prizma malzemesi", "Ortam sıcaklığı"],
     1),
    ("Grafikte bir canlının büyüme eğrisi yaşa göre boy uzunluğu olarak verilmiştir. İlk yıllarda hızlı büyüme, sonra yavaşlama görülmektedir.",
     "canlilarda_ureme", "Bu grafiğe dayanarak büyüme hızıyla ilgili aşağıdakilerden hangisi söylenebilir?",
     ["Her yaşta aynıdır", "Yaşamın belirli dönemlerinde daha hızlı olabilir",
      "Sadece çocuklukta görülür", "Sürekli artar"],
     1),
    ("Seri ve paralel bağlı ampullerin parlaklığı karşılaştırıldı. Seri bağlamada iki ampulden birini çıkarınca diğeri söndü. Paralel bağlamada bir ampul çıkarılınca diğeri yanmaya devam etti.",
     "elektrik_devreleri", "Bu deneyde seri ve paralel bağlamanın farkı aşağıdakilerden hangisiyle açıklanır?",
     ["Pil sayısı", "Devrede akımın tek veya çok yoldan geçmesi",
      "Tel kalınlığı", "Ampul gücü"],
     1),
    ("Astronotların Ay'da Dünya'dakinden daha az ağırlık hissetmelerinin nedeni Ay'ın kütle çekiminin Dünya'dan küçük olmasıdır. Öğretmen \"Aynı astronot Mars'ta nasıl hisseder?\" diye sordu.",
     "gunes_sistemi_otesi", "Mars'ın kütlesi Dünya'dan küçük, Ay'dan büyükse astronotun Mars'taki ağırlığı için ne söylenebilir?",
     ["Dünya'daki ile aynıdır", "Ay'dan fazla, Dünya'dan az olur",
      "Sıfırdır", "Ay'dan da azdır"],
     1),
    ("Hücre bölünmesinde DNA kopyalanır ve kromozomlar eşlenir. Öğrenci \"Neden DNA kopyalanması gerekir?\" diye sordu.",
     "hucre_bolunmeler", "DNA kopyalanmasının hücre bölünmesindeki işlevi aşağıdakilerden hangisidir?",
     ["Enerji sağlamak", "Her yavru hücreye kalıtım bilgisi aktarmak",
      "Hücreyi büyütmek", "Protein sentezi yapmak"],
     1),
    ("Sürtünme kuvveti hareketi engelleyici etki yapar. Buzlu yolda sürtünme az olduğu için kayma olur. Araba lastiklerinin dişli olması sürtünmeyi artırır.",
     "kuvvet_enerji", "Bu bilgilere dayanarak sürtünme kuvvetiyle ilgili aşağıdakilerden hangisi söylenebilir?",
     ["Her zaman zararlıdır", "Yüzeylerin pürüzlülüğüne bağlı olarak değişir",
      "Hareketi her zaman kolaylaştırır", "Yer çekimi ile aynıdır"],
     1),
    ("Çözünme hızı sıcaklıkla artar. Soğuk suda şeker yavaş, sıcak suda hızlı çözünür. Karıştırma da çözünme hızını artırır.",
     "saf_maddeler_karisimlar", "Çözünme hızını etkileyen faktörler aşağıdakilerden hangisinde doğru verilmiştir?",
     ["Sadece çözünen madde miktarı", "Sıcaklık ve karıştırma",
      "Sadece kabın şekli", "Sadece su miktarı"],
     1),
    ("Işık saydam bir ortamdan başka saydam ortama geçerken kırılır. Su içindeki çubuğun kırık görünmesinin nedeni ışığın su-hava sınırında kırılmasıdır.",
     "isik_madde_etkilesim", "Bu olayda kırılmanın temel nedeni aşağıdakilerden hangisidir?",
     ["Işığın rengi", "Işık hızının farklı ortamlarda değişmesi",
      "Ortamın rengi", "Işık kaynağının gücü"],
     1),
    ("Dişi ve erkek üreme hücrelerinin birleşmesi döllenmeyi oluşturur. Döllenmiş yumurta (zigot) bölünerek embriyoyu meydana getirir.",
     "canlilarda_ureme", "Döllenme ve zigot oluşumu aşağıdaki süreçlerden hangisinin başlangıcıdır?",
     ["Mitoz bölünme", "Yeni birey oluşumu (gelişim)",
      "Bağışıklık", "Sindirim"],
     1),
    ("Ohm yasasına göre iletkenin uçları arasındaki gerilim (V), üzerinden geçen akım (I) ile doğru orantılıdır: V = I × R. R dirençtir.",
     "elektrik_devreleri", "Direnci artan bir iletkende, sabit gerilim altında akım için ne söylenebilir?",
     ["Artar", "Azalır",
      "Değişmez", "Önce artar sonra azalır"],
     1),
    ("Güneş Sistemi'nde meteorlar, asteroid kuşağındaki küçük gök cisimlerinden kopan parçalardır. Dünya atmosferine girince sürtünme ile ısınıp yanarlar.",
     "gunes_sistemi_otesi", "Dünya'ya ulaşan meteorların çoğunun yanmasının nedeni aşağıdakilerden hangisidir?",
     ["Güneş ışığı", "Atmosferdeki sürtünme ile ısınma",
      "Yer çekimi", "Manyetik alan"],
     1),
    ("Hücre zarı seçici geçirgendir. Bazı maddeler geçer, bazıları geçemez. Bu sayede hücre içi ortam düzenlenir.",
     "hucre_bolunmeler", "Hücre zarının seçici geçirgenlik özelliğinin işlevi aşağıdakilerden hangisidir?",
     ["Enerji üretmek", "Hücre içi ortamı kontrol altında tutmak",
      "Bölünmeyi sağlamak", "DNA'yı korumak"],
     1),
    ("Potansiyel enerji yüksekliğe bağlıdır. Yüksekteki suyun barajda depolanan potansiyel enerjisi, türbine düşerken kinetik enerjiye dönüşür.",
     "kuvvet_enerji", "Barajda suyun yükseklikten aşağı düşmesi sırasında enerji dönüşümü aşağıdakilerden hangisidir?",
     ["Kinetik → Potansiyel", "Potansiyel → Kinetik",
      "Isı → Işık", "Kimyasal → Elektrik"],
     1),
    ("Saf su 0°C'de donar, 100°C'de kaynar. Tuzlu suyun donma noktası daha düşüktür. Kışın yollara tuz dökülmesinin nedeni budur.",
     "saf_maddeler_karisimlar", "Tuzlu suyun donma noktasının düşük olmasının sonucu aşağıdakilerden hangisidir?",
     ["Daha yavaş donar", "Daha düşük sıcaklıkta donar",
      "Daha hızlı kaynar", "Daha az çözünür"],
     1),
    ("Gölge oluşumu ışığın doğrusal yayıldığını gösterir. Işık opak cisimden geçemez, arkasında karanlık bölge (gölge) oluşur.",
     "isik_madde_etkilesim", "Gölge oluşumunun ışığın hangi özelliğini kanıtladığı söylenebilir?",
     ["Renkli olması", "Doğrusal yayılması",
      "Hızlı olması", "Soğurulması"],
     1),
    ("Metamorfoz geçiren canlılarda (ör. kurbağa) larva evresi yetişkinden farklıdır. Larva suda yaşar, yetişkin karada da yaşayabilir.",
     "canlilarda_ureme", "Metamorfoz geçiren canlılarda larva ve yetişkin arasındaki temel fark aşağıdakilerden hangisidir?",
     ["Sadece boyut farkı vardır", "Yaşam ortamı ve vücut yapısı değişir",
      "Renk değişir", "Sadece renk benzerdir"],
     1),
    ("Devredeki ampul sayısı artırıldığında (seri bağlamada) ampullerin parlaklığı azalır. Çünkü toplam direnç artar, akım azalır.",
     "elektrik_devreleri", "Seri devrede ampul sayısı artınca parlaklığın azalma nedeni aşağıdakilerden hangisidir?",
     ["Pil tükenir", "Toplam direnç artar, akım azalır",
      "Teller ısınır", "Anahtar kapanır"],
     1),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"fen7_{source_ref}",
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
    pad = ["Deney sonuçlarına dayanarak söylenemez", "Yukarıdakilerin hiçbiri", "Sadece I ve II", "Sadece II ve III"]
    for p in pad:
        if len(opts) >= 4:
            break
        if p not in seen:
            opts.append(p)
    return opts[:4]


def generate_questions():
    per_unit = 500 // len(UNITS)
    remainder = 500 % len(UNITS)
    counts = {u: per_unit + (1 if i < remainder else 0) for i, u in enumerate(UNITS)}

    out = []
    tpl_idx = 0
    for unit, count in counts.items():
        for _ in range(count):
            tpl = TEMPLATES[tpl_idx % len(TEMPLATES)]
            tpl_idx += 1
            para, tpl_unit, q_text, opts_list, ans_idx = tpl
            correct = opts_list[ans_idx]
            wrongs = [o for i, o in enumerate(opts_list) if i != ans_idx]
            options = ensure_4_opts(correct, wrongs)
            random.shuffle(options)
            ai = options.index(correct)
            stem = para + "\n\n" + q_text
            expl = f"Deney/gözlem sonuçlarının dikkatli yorumu gerekir. Doğru cevap: {correct}. Bilimsel kavramlar ve neden-sonuç ilişkisi dikkate alınmalıdır."
            out.append(q(
                len(out), stem, options, ai, 2,
                random.choice(NEW_GEN_TYPES), unit,
                [unit[:10], "deney_yorum", "veri_analiz"],
                expl, f"fen7_{unit}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"fen7_{idx:04d}"
        qq["id"] = f"fen7_{idx:04d}"

    packs = [all_q[i:i+10] for i in range(0, 500, 10)]
    unit_counts = {}
    new_gen_count = 0
    for pi, questions in enumerate(packs):
        for qq in questions:
            unit_counts[qq["topic"]] = unit_counts.get(qq["topic"], 0) + 1
            if qq["questionType"] in NEW_GEN_TYPES:
                new_gen_count += 1
        pack = {
            "version": 1,
            "mode": "LGS",
            "subject": "fen",
            "publisher": "edumio",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_fen7_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("FEN7 Question Bank Report")
    print("=" * 60)
    print(f"Packs: {len(packs)}, Questions: {len(all_q)}")
    print("\nUnit distribution:")
    for u in sorted(unit_counts.keys()):
        print(f"  {u}: {unit_counts[u]}")
    pct = 100 * new_gen_count / 500
    print(f"\nNew-generation: {new_gen_count}/500 = {pct:.1f}%")
    print(f"Output: {OUT_DIR}")
    print("=" * 60)


if __name__ == "__main__":
    random.seed(44)
    main()
