#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate 7th Grade İnkılap Tarihi (Revolution History) question bank for BrainBuddy.
50 packs × 10 questions = 500 questions.
BrainBuddy Question Design Standard: paragraph interpretation, cause-effect, reasoning.
"""
import json
import random
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/inkilap7"

TOPICS = [
    "osmanli_son_donem",
    "19yuzyil_degisim_reform",
    "osmanli_toplum_yonetim",
    "sanayi_devrimi",
    "osmanli_modernlesme",
    "fikir_akimlari",
    "milliyetcilik",
    "1dunya_savasi",
    "mondros_isgaller",
    "milli_mucadele_hazirlik",
    "kongreler",
    "tbmm_acilisi",
    "kurtulus_savasi_cepheleri",
    "lozan",
    "ataturk_ilkeler",
    "cumhuriyet_kurulus",
]

NEW_GEN_TYPES = ["paragraf_yorum", "sebep_sonuc", "tarihsel_analiz", "kronoloji", "yorum", "karsilastirma"]

TEMPLATES = [
    ("19. yüzyılda Osmanlı Devleti, Avrupa devletlerinin sanayi ürünleriyle rekabet edemez hale geldi. Lonca teşkilatı zayıfladı, yerli üretim geriledi. Bu dönemde Osmanlı pazarı yabancı mallarla doldu.",
     "osmanli_son_donem", "Bu paragrafta Osmanlı ekonomisinin zayıflamasının temel nedeni olarak aşağıdakilerden hangisi vurgulanmaktadır?",
     ["Tarımın gerilemesi", "Sanayi Devrimi sonrası Avrupa ile rekabet edilememesi", "Vergi sisteminin bozulması", "Nüfus azalması"],
     1),
    ("Tanzimat Fermanı'nda \"can, mal ve namus güvenliği\" ilkesi yer aldı. Bu ilke, devletin tebaasına karşı sorumluluğunu hukuki temele oturtmayı hedefliyordu. Padişah da kanunlara uyacağını taahhüt etti.",
     "19yuzyil_degisim_reform", "Bu bilgilere dayanarak Tanzimat Fermanı'nın getirdiği temel değişiklik aşağıdakilerden hangisidir?",
     ["Saltanatın kaldırılması", "Hukuk önünde eşitlik ve padişahın kanunlara bağlılığı", "Cumhuriyetin ilanı", "Halifeliğin kaldırılması"],
     1),
    ("Osmanlı toplumunda millet sistemi vardı. Her dini cemaat kendi hukukuyla yönetilirdi. Bu sistem çok uluslu yapıyı bir arada tutmaya yönelikti. Ancak milliyetçilik akımıyla birlikte bu yapı sarsılmaya başladı.",
     "osmanli_toplum_yonetim", "Bu paragrafta millet sisteminin zayıflamasına yol açan temel faktör aşağıdakilerden hangisi olarak gösterilmektedir?",
     ["Ekonomik kriz", "Milliyetçilik akımının yükselmesi", "Merkezi otoritenin güçlenmesi", "Dış borçlar"],
     1),
    ("Sanayi Devrimi, üretimi atölyelerden fabrikalara taşıdı. Buhar gücü ve makineler üretimi hızlandırdı. Osmanlı Devleti bu sürece ayak uyduramadı; el tezgâhları Avrupa mallarıyla rekabet edemedi.",
     "sanayi_devrimi", "Sanayi Devrimi'nin Osmanlı ekonomisi üzerindeki temel etkisi aşağıdakilerden hangisidir?",
     ["Tarımı güçlendirmesi", "Yerli üretimin rekabet gücünü kaybetmesi", "Ticaret yollarının değişmesi", "Nüfusun artması"],
     1),
    ("Islahat Fermanı'nda Müslüman ve gayrimüslimlere eşit haklar tanındı. Devlet memuriyetine giriş, askerlik ve eğitim alanlarında eşitlik getirildi. Bu ferman, dış baskılar sonucu ilan edildi.",
     "osmanli_modernlesme", "Islahat Fermanı'nın ilanında dış baskıların etkili olması, aşağıdakilerden hangisini gösterir?",
     ["Osmanlı'nın tam bağımsız karar aldığını", "Avrupa devletlerinin Osmanlı iç işlerine müdahale ettiğini", "Reformların halk tarafından istendiğini", "Padişahın tek yetkili olduğunu"],
     1),
    ("Osmanlıcılık, tüm Osmanlı tebaasını ortak vatandaşlıkta birleştirmeyi hedefliyordu. Türkçülük ise Türk kimliğini ön plana çıkarıyordu. İslamcılık, hilafet etrafında birlik kurmayı amaçlıyordu.",
     "fikir_akimlari", "Bu fikir akımlarının ortak hedefi aşağıdakilerden hangisidir?",
     ["Cumhuriyet ilanı", "Devletin bütünlüğünü korumak veya güçlendirmek", "Saltanatı kaldırmak", "Dış borçları ödemek"],
     1),
    ("Balkan Savaşları sonrasında Balkanlardaki Türk ve Müslüman nüfus Anadolu'ya göç etti. Bu göçler, milliyetçilik akımının bölgede yarattığı etkinin sonucuydu. Osmanlı Devleti toprak kaybetti.",
     "milliyetcilik", "Bu paragrafta Balkan göçlerinin temel nedeni olarak aşağıdakilerden hangisi vurgulanmaktadır?",
     ["Ekonomik zorluklar", "Milliyetçilik akımının Balkanlarda bağımsızlık hareketlerini tetiklemesi", "Kıtlık", "Salgın hastalıklar"],
     1),
    ("I. Dünya Savaşı, Avrupalı devletlerin ittifaklar kurması, sömürge yarışı ve milliyetçilik gibi nedenlerle patlak verdi. Osmanlı Devleti İttifak Devletleri yanında savaşa girdi.",
     "1dunya_savasi", "Osmanlı Devleti'nin I. Dünya Savaşı'na girmesinde aşağıdakilerden hangisi etkili olmuştur?",
     ["Tarafsız kalma isteği", "İttifak Devletleri ile ittifak yapması", "Sömürgelerini genişletme amacı", "ABD'nin daveti"],
     1),
    ("Mondros Ateşkes Antlaşması'nda \"güvenliği tehdit eden herhangi bir durumda itilaf devletleri herhangi bir stratejik noktayı işgal edebilecektir\" maddesi yer aldı. Bu madde, işgallerin yasal dayanağı olarak kullanıldı.",
     "mondros_isgaller", "Bu maddenin işgallere zemin hazırlamasının nedeni aşağıdakilerden hangisidir?",
     ["Osmanlı ordusunun terhis edilmesi", "Belirsiz ifade nedeniyle keyfi yorumlama imkânı vermesi", "Ekonomik yaptırımlar", "Donanmanın teslimi"],
     1),
    ("Mustafa Kemal, Samsun'a çıktığında \"Millet birlik ve beraberlik içinde hareket ederse bağımsızlık kazanılabilir\" düşüncesindeydi. Havza ve Amasya Genelgeleri ile bu birliği sağlama yönünde adımlar atıldı.",
     "milli_mucadele_hazirlik", "Amasya Genelgesi'nin Milli Mücadele açısından temel önemi aşağıdakilerden hangisidir?",
     ["Saltanatı kaldırması", "Milli mücadelenin gerekçe ve yöntemini ilk kez resmen açıklaması", "Cumhuriyeti ilan etmesi", "Halifeliği kaldırması"],
     1),
    ("Erzurum Kongresi'nde \"Vatan bir bütündür, bölünemez\" kararı alındı. Sivas Kongresi bu kararı onayladı ve tüm cemiyetleri bir çatı altında topladı. Temsil Heyeti oluşturuldu.",
     "kongreler", "Erzurum ve Sivas Kongrelerinin ortak yönü aşağıdakilerden hangisidir?",
     ["Padişahı desteklemeleri", "Vatanın bütünlüğünü savunmaları ve Milli Mücadele örgütlenmesi", "İşgalleri kabul etmeleri", "Saltanatı kaldırmaları"],
     1),
    ("23 Nisan 1920'de TBMM açıldı. \"Egemenlik kayıtsız şartsız milletindir\" ilkesi benimsendi. Meclis, yasama ve yürütme yetkisini kendinde topladı. İstanbul Hükümeti'nin otoritesi reddedildi.",
     "tbmm_acilisi", "TBMM'nin açılışıyla birlikte aşağıdakilerden hangisi gerçekleşmiştir?",
     ["Padişahın yetkileri artmıştır", "Ulusal iradenin yasama organı olarak meclis oluşturulmuştur", "Halifelik kaldırılmıştır", "Cumhuriyet ilan edilmiştir"],
     1),
    ("Batı Cephesi'nde Yunan ordusuna karşı savaşıldı. İnönü ve Sakarya Muharebeleri bu cephede gerçekleşti. Büyük Taarruz ile düşman tamamen Anadolu'dan çıkarıldı.",
     "kurtulus_savasi_cepheleri", "Batı Cephesi'ndeki zaferlerin sırası aşağıdakilerden hangisinde doğru verilmiştir?",
     ["Sakarya – İnönü – Büyük Taarruz", "İnönü – Sakarya – Büyük Taarruz", "Büyük Taarruz – İnönü – Sakarya", "Sakarya – Büyük Taarruz – İnönü"],
     1),
    ("Lozan Antlaşması'nda Türkiye'nin sınırları belirlendi. Kapitülasyonlar kaldırıldı. Boğazların yönetimi uluslararası komisyona bırakıldı. Dış borçların ödenmesi taksitlere bağlandı.",
     "lozan", "Lozan Antlaşması'nda kapitülasyonların kaldırılması aşağıdakilerden hangisini sağlamıştır?",
     ["Ekonomik bağımsızlığın önünü açması", "Sınırların çizilmesi", "Boğazlar rejiminin belirlenmesi", "Nüfus mübadelesi"],
     0),
    ("Atatürk ilkeleri arasında Cumhuriyetçilik, Milliyetçilik, Halkçılık, Laiklik, Devletçilik ve İnkılapçılık yer alır. Bu ilkeler, Türkiye Cumhuriyeti'nin temel değerlerini oluşturur.",
     "ataturk_ilkeler", "Cumhuriyetçilik ilkesinin temel amacı aşağıdakilerden hangisidir?",
     ["Ekonomik kalkınma", "Egemenliğin millette olması ve demokratik yönetim", "Dini kuralların uygulanması", "Saltanatın devamı"],
     1),
    ("29 Ekim 1923'te Cumhuriyet ilan edildi. \"Egemenlik kayıtsız şartsız milletindir\" ilkesi devlet yapısına tam olarak yansıdı. Devletin yönetim şekli netleşti.",
     "cumhuriyet_kurulus", "Cumhuriyetin ilanı aşağıdakilerden hangisini netleştirmiştir?",
     ["Halifeliğin konumunu", "Devletin yönetim şeklini (cumhuriyet) ve egemenliğin millette olduğunu", "Dış politika tercihini", "Ekonomik modeli"],
     1),
    ("Osmanlı Devleti 19. yüzyılda dış borçlanmaya gitti. Düyûn-ı Umûmiye İdaresi kuruldu. Bu idare, bazı gelir kaynaklarının yönetimini alacaklı devletlere bıraktı.",
     "osmanli_son_donem", "Düyûn-ı Umûmiye İdaresi'nin kurulması aşağıdakilerden hangisini gösterir?",
     ["Osmanlı ekonomisinin güçlendiğini", "Osmanlı'nın ekonomik bağımsızlığının zayıfladığını", "Sanayinin geliştiğini", "Tarımın modernleştiğini"],
     1),
    ("Batıcılık akımı, Osmanlı'nın Batı medeniyetini örnek alarak modernleşmesini savunuyordu. Eğitim, hukuk ve yönetim alanlarında Batı tarzı yenilikler önerildi.",
     "fikir_akimlari", "Batıcılık akımının temel hedefi aşağıdakilerden hangisidir?",
     ["Saltanatı güçlendirmek", "Batı'daki modern kurum ve değerleri örnek alarak yenileşme", "Halifeliği ön plana çıkarmak", "Gelenekleri korumak"],
     1),
    ("Çanakkale Savaşı, I. Dünya Savaşı'nın önemli cephelerinden biri oldu. İtilaf Devletleri boğazları geçemedi. Mustafa Kemal'in askeri dehası burada ortaya çıktı.",
     "1dunya_savasi", "Çanakkale zaferinin I. Dünya Savaşı açısından önemi aşağıdakilerden hangisidir?",
     ["Savaşı bitirmesi", "İstanbul'un işgalini engellemesi ve boğazların kontrol altında kalması", "Rusya'ya yardım ulaştırması", "ABD'nin savaşa girmesi"],
     1),
    ("İzmir'in işgali, Türk halkında büyük tepki yarattı. Mitingler düzenlendi. Redd-i İlhak Cemiyeti kuruldu. Milli mücadele ruhu güçlendi.",
     "mondros_isgaller", "İzmir'in işgalinin Milli Mücadele üzerindeki etkisi aşağıdakilerden hangisidir?",
     ["Teslimiyeti hızlandırması", "Halkta direniş ve milli bilincin artması", "Padişahın güçlenmesi", "İşgallerin sona ermesi"],
     1),
    ("Havza Genelgesi'nde mitingler düzenlenmesi ve işgallere tepki gösterilmesi istendi. Bu genelge, halkı bilinçlendirme ve örgütleme amacı taşıyordu.",
     "milli_mucadele_hazirlik", "Havza Genelgesi'nin amacı aşağıdakilerden hangisidir?",
     ["Padişaha bağlılığı artırmak", "Halkı işgallere karşı bilinçlendirmek ve tepki örgütlemek", "İşgalleri kabul etmek", "Saltanatı kaldırmak"],
     1),
    ("Sivas Kongresi'nde tüm milli cemiyetler \"Anadolu ve Rumeli Müdafaa-i Hukuk Cemiyeti\" adı altında birleştirildi. Temsil Heyeti genişletildi.",
     "kongreler", "Sivas Kongresi'nde cemiyetlerin tek çatı altında toplanmasının amacı aşağıdakilerden hangisidir?",
     ["Padişahı desteklemek", "Milli mücadeleyi tek elden ve güçlü yürütmek", "İşgalleri kabul etmek", "Halifeliği güçlendirmek"],
     1),
    ("TBMM'nin açılışından sonra İstanbul Hükümeti ile ilişkiler kesildi. Meclis, kendi hükümetini kurdu. Damat Ferit Paşa hükümeti Milli Mücadele aleyhine fetvalar yayınladı.",
     "tbmm_acilisi", "TBMM'nin İstanbul Hükümeti'ne karşı tavrı aşağıdakilerden hangisi olmuştur?",
     ["İstanbul'u desteklemesi", "Ulusal iradeyi temsil etmesi ve İstanbul Hükümeti'nin otoritesini reddetmesi", "Padişaha bağlı kalması", "İşgalleri kabul etmesi"],
     1),
    ("Güney Cephesi'nde Fransız ve Ermeni birliklerine karşı savaşıldı. Halkın direnişi etkili oldu. Ankara Antlaşması ile Fransızlar bölgeden çekildi.",
     "kurtulus_savasi_cepheleri", "Güney Cephesi'nde Fransızların çekilmesinde aşağıdakilerden hangisi etkili olmuştur?",
     ["İngiliz desteği", "Halkın direnişi ve Ankara Antlaşması", "ABD müdahalesi", "Rusya'nın baskısı"],
     1),
    ("Lozan'da Musul sorunu çözülemedi; İngiltere ile anlaşmazlık sürdü. Nüfus mübadelesi kararlaştırıldı. Azınlıkların hakları düzenlendi.",
     "lozan", "Lozan Antlaşması'nda çözülemeyen konu aşağıdakilerden hangisidir?",
     ["Boğazlar", "Musul meselesi", "Kapitülasyonlar", "Dış borçlar"],
     1),
    ("Laiklik ilkesi, din ve devlet işlerinin ayrılmasını öngörür. Devlet, tüm inançlara eşit mesafededir. Eğitim ve hukuk alanında düzenlemeler yapıldı.",
     "ataturk_ilkeler", "Laiklik ilkesinin getirdiği temel değişiklik aşağıdakilerden hangisidir?",
     ["Halifeliğin güçlenmesi", "Din ve devlet işlerinin ayrılması", "Saltanatın devamı", "Dini eğitimin zorunlu olması"],
     1),
    ("Saltanatın kaldırılması, cumhuriyetin ilanından önce gerçekleşti. Bu adım, ulusal egemenliğin önündeki engeli kaldırdı. Lozan görüşmelerinde TBMM tek temsilci olarak kabul edildi.",
     "cumhuriyet_kurulus", "Saltanatın kaldırılmasının sırası, cumhuriyetin ilanına göre nasıldır?",
     ["Cumhuriyetten sonra kaldırıldı", "Cumhuriyetten önce kaldırıldı", "Aynı gün ilan edildi", "Lozan'dan sonra kaldırıldı"],
     1),
    ("Sanayi Devrimi, Avrupa'da üretim artışına yol açtı. Ham madde ve pazar ihtiyacı arttı. Osmanlı, hem pazar hem de ham madde kaynağı olarak Avrupa'nın hedefi haline geldi.",
     "sanayi_devrimi", "Sanayi Devrimi sonrasında Osmanlı'nın Avrupa için öneminin artma nedeni aşağıdakilerden hangisidir?",
     ["Askeri gücü", "Ham madde ve pazar olarak görülmesi", "Nüfusunun fazla olması", "Coğrafi konumu sadece"],
     1),
    ("Türkçülük akımı, Türk dili, tarihi ve kültürünü ön plana çıkardı. Ziya Gökalp gibi düşünürler bu akımı geliştirdi. Milli kimlik vurgulandı.",
     "fikir_akimlari", "Türkçülük akımının vurguladığı temel kavram aşağıdakilerden hangisidir?",
     ["Osmanlı tebaasının birliği", "Türk milli kimliği ve kültürü", "Halifelik etrafında birlik", "Batı'nın taklidi"],
     1),
    ("Sevr Antlaşması, Osmanlı Devleti'ne ağır koşullar dayattı. Ancak bu antlaşma hiçbir zaman yürürlüğe girmedi. Kurtuluş Savaşı sonrası Lozan ile geçersiz kılındı.",
     "mondros_isgaller", "Sevr Antlaşması'nın uygulanamamasının nedeni aşağıdakilerden hangisidir?",
     ["Padişahın onaylamaması", "TBMM'nin kabul etmemesi ve Kurtuluş Savaşı'nın kazanılması", "İtilaf Devletleri'nin vazgeçmesi", "ABD'nin karşı çıkması"],
     1),
    ("Amasya Görüşmeleri'nde İstanbul Hükümeti temsilcisi ile görüşmeler yapıldı. Ancak anlaşma sağlanamadı. Temsil Heyeti'nin gücü artarken İstanbul'un otoritesi zayıfladı.",
     "milli_mucadele_hazirlik", "Amasya Görüşmeleri'nin sonucunda aşağıdakilerden hangisi gerçekleşmiştir?",
     ["Padişah TBMM'yi tanıdı", "Temsil Heyeti'nin meşruiyeti arttı, İstanbul ile uzlaşı sağlanamadı", "Saltanat kaldırıldı", "Cumhuriyet ilan edildi"],
     1),
]


def q(idx, stem, options, answer_index, difficulty, qtype, topic, skills, explanation, source_ref):
    return {
        "id": f"inkilap7_{source_ref}",
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
        "subject": "inkilap",
    }


def ensure_4_opts(correct, wrongs):
    opts = [correct]
    seen = {correct}
    for w in wrongs:
        if w not in seen and len(opts) < 4:
            opts.append(w)
            seen.add(w)
    pad = ["Paragraftan çıkarılamaz", "Yukarıdakilerin hiçbiri", "Sadece I ve II", "Sadece II ve III"]
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
            para, tpl_topic, q_text, opts_list, ans_idx = tpl
            correct = opts_list[ans_idx]
            wrongs = [o for i, o in enumerate(opts_list) if i != ans_idx]
            options = ensure_4_opts(correct, wrongs)
            random.shuffle(options)
            ai = options.index(correct)
            stem = para + "\n\n" + q_text
            expl = f"Tarihsel paragrafın dikkatli okunması ve sebep-sonuç ilişkilerinin yorumlanması gerekir. Doğru cevap: {correct}."
            out.append(q(
                len(out), stem, options, ai, 2,
                random.choice(NEW_GEN_TYPES), topic,
                [topic[:12], "tarihsel_yorum", "sebep_sonuc"],
                expl, f"inkilap7_{topic}_{len(out):04d}"
            ))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_q = generate_questions()
    assert len(all_q) == 500, f"Expected 500, got {len(all_q)}"

    for idx, qq in enumerate(all_q):
        qq["sourceRef"] = f"inkilap7_{idx:04d}"
        qq["id"] = f"inkilap7_{idx:04d}"

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
            "subject": "inkilap",
            "publisher": "brainbuddy",
            "questions": questions,
        }
        path = OUT_DIR / f"lgs_inkilap7_pack_{pi+1:03d}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2)

    print("=" * 60)
    print("INKILAP7 Question Bank Report")
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
