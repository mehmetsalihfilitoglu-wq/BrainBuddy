# 7. Sınıf Din Kültürü ve Ahlak Bilgisi Soru Bankası — Final Raporu

## Özet

| Metrik | Değer |
|--------|-------|
| **Oluşturulan paket sayısı** | 50 |
| **Toplam soru** | 500 |
| **Yeni nesil soru oranı** | %100 |
| **JSON doğrulama** | ✅ Tüm dosyalar başarıyla parse edildi |

---

## Dizin Yapısı

```
app/src/main/assets/lgs_import/din7/
├── lgs_din7_pack_001.json
├── lgs_din7_pack_002.json
├── ...
└── lgs_din7_pack_050.json
```

---

## Konu Dağılımı (7. Sınıf MEB Din Kültürü müfredatı)

| Konu | Soru Sayısı |
|------|-------------|
| Melek ve Ahiret İnancı (melek_ahiret) | 46 |
| Hac ve Kurban (hac_kurban) | 46 |
| Ahlaki Davranışlar (ahlaki_davranislar) | 46 |
| Allah'ın Kulu ve Elçisi: Hz. Muhammed (hz_muhammed) | 46 |
| İslam Düşüncesinde Yorumlar (islam_dusuncesinde_yorumlar) | 46 |
| Din ve Güzel Ahlak (din_guzel_ahlak) | 45 |
| İbadet ve Sorumluluk Bilinci (ibadet_sorumluluk) | 45 |
| Paylaşma ve Yardımlaşma (paylasma_yardimlasma) | 45 |
| Peygamberlerin Özellikleri (peygamberler_ozellikleri) | 45 |
| Ayet ve Hadis Yorumlama (ayet_hadis_yorumlama) | 45 |
| İslami Kavramlar ve Değerler (islami_kavramlar_degerler) | 45 |

---

## Zorluk Dağılımı

| Zorluk | Soru Sayısı |
|--------|-------------|
| 4 | 166 |
| 5 | 334 |

Kolay soru yok; tüm sorular zorluk 4 veya 5.

---

## Soru Tarzları

- Ayet ve hadis yorumlama
- Senaryo tabanlı ahlak soruları
- Kavram çıkarımı
- Metin analizi
- Paragraf yorumlama
- Değer çıkarımı

Öğrenci, metni dikkatle okuyup ayet/hadis mealini yorumlamalı, ahlaki/değer çıkarımı yapmalı, yakın kavramları ayırt edip çeldiricileri eleyebilmelidir.

---

## BrainBuddy Şema Uyumu

Her soru şu alanları içerir:

- `id`, `stem`, `options` (4 adet), `answerIndex`
- `difficulty`, `questionType`, `topic`, `skills`
- `explanation`, `source`, `sourceRef`

Tüm JSON paketleri `QuestionPackImporter` ile uyumludur.

---

## Entegrasyon

`QuestionPackImporter.kt` içinde eklenenler:

- `LGS_DIN7_IMPORT_DIR = "lgs_import/din7"`
- `runDin7LgsImportFromAssets()`
- `importDin7LgsPacksFromAssets()`
- `importAllLgsPacksFromAssets()` içinden çağrı

Uygulamada **Import LGS Packs** kullanıldığında din7 paketleri otomatik olarak yüklenir.
