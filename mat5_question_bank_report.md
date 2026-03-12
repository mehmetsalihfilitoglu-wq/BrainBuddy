# 5. Sınıf Matematik Soru Bankası — Final Raporu

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
app/src/main/assets/lgs_import/mat5/
├── lgs_mat5_pack_001.json
├── lgs_mat5_pack_002.json
├── ...
└── lgs_mat5_pack_050.json
```

---

## Konu Dağılımı (5. Sınıf MEB Matematik müfredatı)

| Konu | Soru Sayısı |
|------|-------------|
| Doğal Sayılar | 42 |
| Doğal Sayılarla İşlemler | 42 |
| Kesirler | 42 |
| Kesirlerle İşlemler | 42 |
| Ondalık Gösterimler | 42 |
| Yüzdeler | 42 |
| Temel Geometrik Kavramlar | 42 |
| Üçgen ve Dörtgenler | 42 |
| Uzunluk Ölçme | 41 |
| Alan Ölçme | 41 |
| Veri Toplama ve Veri Analizi | 41 |
| Grafik Yorumlama | 41 |
| **Toplam** | **500** |

---

## Zorluk Dağılımı

| Zorluk | Soru Sayısı |
|--------|-------------|
| 4 | 210 |
| 5 | 290 |

Zorluk 4 ve 5. Kolay soru yok.

---

## Soru Tarzları

- Yorum gerektiren
- Çok adımlı işlem
- Gerçek hayat senaryosu
- Grafik/tablo yorumlama
- Mantık yürütme

---

## BrainBuddy Şema Uyumu

Her soru: `id`, `stem`, `options` (4), `answerIndex`, `difficulty`, `questionType`, `topic`, `skills`, `explanation`, `source`, `sourceRef`.

Tüm JSON paketleri `QuestionPackImporter` ile uyumludur.

---

## Entegrasyon

`QuestionPackImporter.kt`:
- `LGS_MAT5_IMPORT_DIR = "lgs_import/mat5"`
- `runMat5LgsImportFromAssets()`
- `importMat5LgsPacksFromAssets()`
- `importAllLgsPacksFromAssets()` içinden çağrı

Uygulamada **Import LGS Packs** ile mat5 paketleri otomatik yüklenir.
