# Hayat3 (3. Sınıf Hayat Bilgisi) Question Bank Report

## Overview

- **Target directory**: `app/src/main/assets/lgs_import/hayat3/`
- **Pack format**: `lgs_hayat3_pack_001.json` … `lgs_hayat3_pack_050.json`
- **Total questions**: 500
- **Questions per pack**: 10

## Curriculum Units

| Topic | Questions |
|-------|-----------|
| okulumuzda_hayat | 84 |
| evimizde_hayat | 84 |
| saglikli_hayat | 83 |
| guvenli_hayat | 83 |
| ulkemizde_hayat | 83 |
| dogada_hayat | 83 |

## Quality Metrics

- **New-generation ratio**: 100%
- **Question types**: senaryo_yorumlama, gunluk_hayat_durumu, davranis_analizi, sebep_sonuc, deger_yorumlama, dogru_tavir_secimi
- **Difficulty distribution**: 4 (35%), 5 (65%)
- **BrainBuddy schema**: id, stem, options (4), answerIndex, difficulty, questionType, topic, skills, explanation, source, sourceRef

## Subject Support

The app now includes full **Hayat Bilgisi** (Subject.HAYAT) support:

- `Models.kt`: `Subject.HAYAT`
- `QuestionMapper.kt`: `"hayat"` / `"hayat bilgisi"` mapping
- `QuestionPackImporter.kt`: `LGS_HAYAT3_IMPORT_DIR`, `runHayat3LgsImportFromAssets`
- `importAllLgsPacksFromAssets` calls hayat3 import automatically

## Validation

Run: `python scripts/validate_hayat3_report.py`

## Generation

Run: `python scripts/generate_hayat3_packs.py`
