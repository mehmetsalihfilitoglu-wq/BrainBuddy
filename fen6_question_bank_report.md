# 6. Sınıf Fen Bilimleri Soru Bankası — Final Raporu

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
app/src/main/assets/lgs_import/fen6/
├── lgs_fen6_pack_001.json
├── lgs_fen6_pack_002.json
├── ...
└── lgs_fen6_pack_050.json
```

---

## Ünite Dağılımı (6. Sınıf MEB Fen Bilimleri müfredatı)

| Ünite | Soru Sayısı |
|-------|-------------|
| Güneş Sistemi ve Tutulmalar | 72 |
| Vücudumuzdaki Sistemler | 72 |
| Kuvvet ve Hareket | 72 |
| Madde ve Isı | 71 |
| Ses ve Özellikleri | 71 |
| Vücudumuzdaki Sistemler ve Sağlığı | 71 |
| Elektriğin İletimi | 71 |
| **Toplam** | **500** |

---

## Zorluk Dağılımı

| Zorluk | Soru Sayısı |
|--------|-------------|
| 4 | 205 |
| 5 | 295 |

Kolay soru yok; tüm sorular zorluk 4 veya 5 hedeflenmiştir.

---

## Soru Tarzları

- Deney yorumlama
- Gözlem yorumlama
- Grafik yorumlama
- Tablo yorumlama
- Sebep–sonuç ilişkisi
- Kavram bağlantısı
- Çok adımlı düşünme

Öğrenci deneyi analiz etmeli, gözlemleri yorumlamalı, grafik/tablo okumalı, sebep-sonuç ilişkisi kurmalı ve yakın çeldiricileri elemeyi başarmalıdır.

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

- `LGS_FEN6_IMPORT_DIR = "lgs_import/fen6"`
- `runFen6LgsImportFromAssets()`
- `importFen6LgsPacksFromAssets()`
- `importAllLgsPacksFromAssets()` içinden çağrı

Uygulamada **Import LGS Packs** kullanıldığında fen6 paketleri otomatik olarak yüklenir.
