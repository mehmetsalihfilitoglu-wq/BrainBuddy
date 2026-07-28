# BrainBuddy Visual Upgrade — Pass 2 Report

## Summary

| Metric | Count |
|--------|-------|
| **Newly upgraded (pass 2)** | 72 |
| **Cumulative upgraded (pass 1 + pass 2)** | 340 |
| **Remaining generic (after pass 2)** | 210 |
| JSON files modified (pass 2) | 54 |
| Manual review candidates | 0 |

**Validation:** `scripts/validate_image_assets.py` — all imageAsset paths resolve. JSON schema, ids, order, UTF-8 preserved.

## Subject breakdown (pass 2 upgraded)

| Subject | Pass 2 upgraded | Remaining generic |
|---------|-----------------|--------------------|
| fen | 57 | 127 |
| mat | 0 | 0 |
| sosyal | 0 | 27 |
| english | 15 | 56 |

## Type breakdown (pass 2 upgraded)

| Type | Pass 2 upgraded | Remaining generic |
|------|-----------------|--------------------|
| graph_line | 0 | 42 |
| picture | 72 | 137 |
| table | 0 | 31 |

## Confidence / source tag breakdown

| Tag | Count |
|-----|-------|
| exact_data_match | 0 |
| explicit_shape_match | 0 |
| explicit_object_match | 0 |
| keyword_safe_template | 72 |
| ambiguous_skip | 0 |

## Top 30 sample replacements (old -> new)

| Question ID | Old | New | Confidence |
|-------------|-----|-----|------------|
| `fen3_0418` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_042_fen3_0418_picture.png` | keyword_safe_template |
| `fen3_0424` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_043_fen3_0424_picture.png` | keyword_safe_template |
| `fen3_0430` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_044_fen3_0430_picture.png` | keyword_safe_template |
| `fen3_0436` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_044_fen3_0436_picture.png` | keyword_safe_template |
| `fen3_0442` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_045_fen3_0442_picture.png` | keyword_safe_template |
| `fen3_0448` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_045_fen3_0448_picture.png` | keyword_safe_template |
| `fen3_0454` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_046_fen3_0454_picture.png` | keyword_safe_template |
| `fen3_0460` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_047_fen3_0460_picture.png` | keyword_safe_template |
| `fen3_0466` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_047_fen3_0466_picture.png` | keyword_safe_template |
| `fen3_0472` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_048_fen3_0472_picture.png` | keyword_safe_template |
| `fen3_0478` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_048_fen3_0478_picture.png` | keyword_safe_template |
| `fen3_0484` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_049_fen3_0484_picture.png` | keyword_safe_template |
| `fen3_0490` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_050_fen3_0490_picture.png` | keyword_safe_template |
| `fen3_0496` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_050_fen3_0496_picture.png` | keyword_safe_template |
| `fen4_0216` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_022_fen4_0216_picture.png` | keyword_safe_template |
| `fen4_0223` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_023_fen4_0223_picture.png` | keyword_safe_template |
| `fen4_0230` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_024_fen4_0230_picture.png` | keyword_safe_template |
| `fen4_0237` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_024_fen4_0237_picture.png` | keyword_safe_template |
| `fen4_0244` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_025_fen4_0244_picture.png` | keyword_safe_template |
| `fen4_0251` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_026_fen4_0251_picture.png` | keyword_safe_template |
| `fen4_0258` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_026_fen4_0258_picture.png` | keyword_safe_template |
| `fen4_0265` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_027_fen4_0265_picture.png` | keyword_safe_template |
| `fen4_0272` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_028_fen4_0272_picture.png` | keyword_safe_template |
| `fen4_0279` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_028_fen4_0279_picture.png` | keyword_safe_template |
| `fen4_0286` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_029_fen4_0286_picture.png` | keyword_safe_template |
| `fen5_0221` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_023_fen5_0221_picture.png` | keyword_safe_template |
| `fen5_0228` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_023_fen5_0228_picture.png` | keyword_safe_template |
| `fen5_0235` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_024_fen5_0235_picture.png` | keyword_safe_template |
| `fen5_0242` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_025_fen5_0242_picture.png` | keyword_safe_template |
| `fen5_0249` | `quiz_images/fen/picture.png` | `quiz_images/fen/fen_025_fen5_0249_picture.png` | keyword_safe_template |

## Manual review candidates


## Subjects with biggest remaining opportunity

- **fen**: 127 questions still generic
- **english**: 56 questions still generic
- **sosyal**: 27 questions still generic

## Classification (before pass 2)

| Classification | Count |
|-----------------|-------|
| deterministic_replace | 0 |
| safe_template_replace | 72 |
| manual_review | 0 |
| keep_generic | 210 |

## Files modified (pass 2)

- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_001.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_002.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_003.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_004.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_005.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_006.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_011.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_013.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_014.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_015.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_016.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_042.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_043.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_044.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_045.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_046.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_047.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_048.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_049.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_050.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_022.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_023.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_024.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_025.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_026.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_027.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_028.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_029.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_023.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_024.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_025.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_026.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_027.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_028.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_029.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_044.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_045.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_046.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_047.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_048.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_049.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_050.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_001.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_006.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_010.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_015.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_019.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_024.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_028.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_033.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_037.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_042.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_046.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_madde_ve_endustri_008.json`
