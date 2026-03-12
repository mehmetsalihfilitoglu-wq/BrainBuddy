# Hayat2 (2. Sınıf Hayat Bilgisi) Question Bank Report

## Official Curriculum Sources

- **MEB 2024-2025 2. Sınıf Hayat Bilgisi Öğretim Programı**
- **Türkiye Yüzyılı Maarif Modeli (TYMM)** – Hayat Bilgisi dersi
- **EgitimOkulu** – 2. Sınıf Hayat Bilgisi Kazanımları 2024-2025

## Summary

| Item | Value |
|------|-------|
| Packs created | 50 |
| Total questions | 500 |
| Target directory | `app/src/main/assets/lgs_import/hayat2/` |
| Pack naming | `lgs_hayat2_pack_001.json` … `lgs_hayat2_pack_050.json` |

## Topic Distribution (6 MEB Öğrenme Alanları)

| Topic | Questions |
|-------|-----------|
| ben_ve_okulum | 84 |
| sagligim_guvenligim | 84 |
| ailem_ve_toplum | 83 |
| yasadigim_yer_ulkem | 83 |
| doga_ve_cevre | 83 |
| bilim_teknoloji_sanat | 83 |

## Quality Metrics

- **New-generation ratio**: 100% (≥80% required)
- **Question types**: senaryo_yorumlama, gunluk_hayat_durumu, dogru_davranis_buldurma, sebep_sonuc, deger_davranis_analizi, yorum_gerektiren
- **Difficulty**: 4–5 (mostly 5)
- **Schema**: id, stem, options (4), answerIndex, difficulty, questionType, topic, skills, explanation, source, sourceRef

## Validation

- All JSON files parse successfully.
- All packs compatible with `QuestionPackImporter`.
- Run: `python scripts/validate_hayat2_report.py`

## Importer Support

- `LGS_HAYAT2_IMPORT_DIR` = `lgs_import/hayat2`
- `runHayat2LgsImportFromAssets` – `forceSubject = "hayat"`, `forceGrade = 2`
- `importHayat2LgsPacksFromAssets` – public entry point
- `importAllLgsPacksFromAssets` – includes hayat2 import
