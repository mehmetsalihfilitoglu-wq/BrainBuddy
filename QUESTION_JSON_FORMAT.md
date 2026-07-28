# Soru JSON Formatı (Sınıf Bazlı)

## Yeni Format (Önerilen)

```json
{
  "id": "6_MAT_000001",
  "grade": 6,
  "subject": "MAT",
  "difficulty": "MEDIUM",
  "stem": "Soru metni...",
  "choices": ["A", "B", "C", "D"],
  "correctIndex": 0,
  "hint": "İpucu (opsiyonel)",
  "imageAsset": ""
}
```

## Alanlar

| Alan | Tip | Zorunlu | Açıklama |
|------|-----|---------|----------|
| `id` | string | Hayır | Benzersiz ID. Verilmezse `{grade}_{subject}_{seq}` üretilir |
| `grade` | int | Evet* | Sınıf 1–7. `gradeTag`'den türetilebilir |
| `subject` | string | Evet | MAT, TURKCE, FEN, SOSYAL, ING |
| `difficulty` | string | Evet | EASY, MEDIUM, HARD |
| `stem` | string | Evet | Soru metni |
| `choices` | array | Evet | 4 şık |
| `correctIndex` | int | Evet | 0–3 |
| `hint` | string | Hayır | İpucu |
| `imageAsset` | string | Hayır | Görsel asset yolu (assets/ köküne göre, örn: `quiz_images/sample.png`). Desteklenen aliaslar: `visualAsset`, `graphicAsset`, `tableAsset` |
| `levelGroup` | string | Hayır | GRADE_5_8 vb. (geri uyum) |
| `gradeTag` | string | Hayır | "6" vb. (grade yoksa kullanılır) |

## Zorluk Dağılımı Hedefi

- %40 MEDIUM
- %40 HARD  
- %20 EASY (ısınma soruları)

## Havuz Hedefi

- Her sınıf (1–7) × her ders (5) = en az 500 soru
- Toplam: 7 × 5 × 500 = **17.500 soru** (1. sınıf = Junior)
