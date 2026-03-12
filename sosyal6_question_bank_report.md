# 6. Sınıf Sosyal Bilgiler Soru Bankası — Final Raporu

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
app/src/main/assets/lgs_import/sosyal6/
├── lgs_sosyal6_pack_001.json
├── lgs_sosyal6_pack_002.json
├── ...
└── lgs_sosyal6_pack_050.json
```

---

## Ünite Dağılımı (6. Sınıf MEB Sosyal Bilgiler müfredatı)

| Ünite | Soru Sayısı |
|-------|-------------|
| Birey ve Toplum (birey_toplum) | 72 |
| Kültür ve Miras (kultur_miras) | 72 |
| İnsanlar, Yerler ve Çevreler (insanlar_yerler_cevreler) | 72 |
| Bilim, Teknoloji ve Toplum (bilim_teknoloji_toplum) | 71 |
| Üretim, Dağıtım ve Tüketim (uretim_dagitim_tuketim) | 71 |
| Etkin Vatandaşlık (etkin_vatandaslik) | 71 |
| Küresel Bağlantılar (kuresel_baglantilar) | 71 |
| **Toplam** | **500** |

---

## Zorluk Dağılımı

| Zorluk | Soru Sayısı |
|--------|-------------|
| 4 | 214 |
| 5 | 286 |

Kolay soru yok; tüm sorular zorluk 4 veya 5 hedeflenmiştir.

---

## Soru Tarzları

- Paragraf yorum soruları
- Harita yorumlama
- Tablo / grafik yorumlama
- Tarihsel olay analizi
- Coğrafi yorum
- Sebep–sonuç ilişkisi
- Kavram bağlantısı

Öğrenci metni yorumlamalı, harita/grafik/tablo okumalı, sebep-sonuç ilişkisi kurmalı ve yakın çeldiricileri elemeyi başarmalıdır.

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

- `LGS_SOSYAL6_IMPORT_DIR = "lgs_import/sosyal6"`
- `runSosyal6LgsImportFromAssets()`
- `importSosyal6LgsPacksFromAssets()`
- `importAllLgsPacksFromAssets()` içinden çağrı

Uygulamada **Import LGS Packs** kullanıldığında sosyal6 paketleri otomatik olarak yüklenir.
