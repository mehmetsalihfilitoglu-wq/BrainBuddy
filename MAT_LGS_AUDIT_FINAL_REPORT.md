# LGS MAT Soru Bankası – Denetim ve Düzeltme Raporu

**Tarih:** 2025  
**Hedef:** `app/src/main/assets/lgs_import/mat/` klasöründeki tüm MAT JSON paketleri

---

## Özet

| Metrik | Değer |
|--------|-------|
| **İncelenen toplam soru sayısı** | 586 |
| **Düzeltilen soru sayısı** | 522 (schema) + 99 (answerIndex) + 1 (explanation) |
| **Yanlış answerIndex düzeltildi** | 99 |
| **Schema hatası düzeltildi** | 522 |
| **Duplicate soru kaldırıldı** | 64 |
| **Yeniden yazılan soru** | 0 |
| **Options düzeltildi** | 0 (tüm sorularda zaten 4 farklı seçenek vardı) |
| **Son soru sayısı** | 522 |

---

## 1. JSON Schema Kontrolü

- **Kök yapı:** `version`, `mode`, `subject`, `publisher`, `questions` – korundu
- **Soru alanları:** Eksik `subject`, `topic`, `skills`, `source`, `sourceRef` alanları eklendi
- `examType` ve `grade`: MAT dosyalarında yok; importer ekliyor
- **Sonuç:** Tüm sorularda gerekli alanlar mevcut

---

## 2. Seçenek Kontrolü

- Her soruda **4 seçenek** kontrol edildi
- Boş veya duplicate seçenek bulunmadı
- **Sonuç:** Hiçbir değişiklik gerekmedi

---

## 3. Cevap Doğrulama

- **validate_mat_answers.py** ile:
  - Stem’den matematiksel çözüm
  - Explanation’dan cevap çıkarma
  - `answerIndex` doğruluğu kontrol edildi
- **99 soruda** `answerIndex` yanlış bulundu ve düzeltildi
- **1 soruda** (lgs_mat_gold_032.json) explanation çelişkiliydi; düzeltildi

---

## 4. Duplicate Kontrolü

- Stem benzerliği > %85 olan sorular duplicate kabul edildi
- **64 duplicate** kaldırıldı (ilk geçen korundu)
- **Sonuç:** 586 → 522 soru

---

## 5. Difficulty Kontrolü

- Tüm sorularda `difficulty` 1, 2 veya 3
- Geçersiz değerler orta seviye (2) olarak ayarlandı

---

## 6. Yapılan Düzeltmeler

1. **Schema:** Eksik `topic`, `skills`, `source`, `sourceRef` eklendi  
2. **answerIndex:** 99 yanlış cevap indeksi düzeltildi  
3. **Duplicates:** 64 tekrarlayan soru silindi  
4. **Explanation:** 1 çelişkili açıklama güncellendi  

---

## 7. Değiştirilmeyenler

- Importer yapısı korundu
- Dosya path’leri değiştirilmedi
- JSON formatı aynı kaldı

---

## Kullanılan Araçlar

- **scripts/audit_mat_lgs.py** – Schema, duplicate ve answerIndex düzeltmeleri
- **validate_mat_answers.py** – Matematiksel çözüm ve cevap doğrulama
