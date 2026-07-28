# BrainBuddy Visual Upgrade — Report

## Summary

| Metric | Count |
|--------|-------|
| **Total upgraded (question-specific asset)** | 268 |
| Visual questions still using generic assets | 282 |
| JSON files modified | 88 |
| Skipped (with reason) | 282 |

**Validation:** `scripts/validate_image_assets.py` — all imageAsset paths resolve (1207 questions with imageAsset). JSON remains importer-compatible.

## Subject breakdown

| Subject | Upgraded |
|---------|----------|
| fen | 111 |
| mat | 157 |
| sosyal | 0 |
| english | 0 |

## Question-type breakdown

| Type | Count |
|------|-------|
| experiment | 104 |
| geometry | 12 |
| graph_line | 27 |
| picture | 125 |

## Subjects improved most

- **mat**: 157 question-specific visuals (counting pictures with correct N objects, graph_line with extracted data)
- **fen**: 111 question-specific visuals (graph_line with depth/pressure data, experiment schematics)
- **sosyal**: 0 (picture.png — ambiguous stem; not mass-replaced with weak visuals)
- **english**: 0 (picture.png — same; non-priority for this pass)

## 20 sample question IDs: old imageAsset -> new imageAsset

| Question ID | Old | New |
|-------------|-----|-----|
| fen_bas_001_q03 | quiz_images/fen/graph_line.png | quiz_images/fen/fen_basinc_001_fen_bas_001_q03_graph_line.png |
| fen7_0004 | quiz_images/fen/experiment.png | quiz_images/fen/fen_001_fen7_0004_experiment.png |
| fen7_0320 | quiz_images/fen/graph_line.png | quiz_images/fen/fen_033_fen7_0320_graph_line.png |
| fen7_0321 | quiz_images/fen/experiment.png | quiz_images/fen/fen_033_fen7_0321_experiment.png |
| mat5_0023 | quiz_images/mat/graph_line.png | quiz_images/mat/mat_003_mat5_0023_graph_line.png |
| mat1_0160 | quiz_images/mat/picture.png | quiz_images/mat/mat_017_mat1_0160_picture.png |
| mat1_0161 | quiz_images/mat/picture.png | quiz_images/mat/mat_017_mat1_0161_picture.png |
| mat1_0170 | quiz_images/mat/picture.png | quiz_images/mat/mat_018_mat1_0170_picture.png |
| fen4_0076 | quiz_images/fen/experiment.png | quiz_images/fen/fen_008_fen4_0076_experiment.png |
| fen4_0083 | quiz_images/fen/experiment.png | quiz_images/fen/fen_009_fen4_0083_experiment.png |
| mat1_0000 | quiz_images/mat/picture.png | quiz_images/mat/mat_001_mat1_0000_picture.png |
| mat1_0001 | quiz_images/mat/picture.png | quiz_images/mat/mat_001_mat1_0001_picture.png |
| fen_bas_002_q05 | quiz_images/fen/graph_line.png | quiz_images/fen/fen_basinc_002_fen_bas_002_q05_graph_line.png |
| fen_bas_003_q04 | quiz_images/fen/graph_line.png | quiz_images/fen/fen_basinc_003_fen_bas_003_q04_graph_line.png |
| fen7_0010 | quiz_images/fen/experiment.png | quiz_images/fen/fen_002_fen7_0010_experiment.png |
| mat1_0151 | quiz_images/mat/picture.png | quiz_images/mat/mat_016_mat1_0151_picture.png |
| mat1_0152 | quiz_images/mat/picture.png | quiz_images/mat/mat_016_mat1_0152_picture.png |
| fen4_0090 | quiz_images/fen/experiment.png | quiz_images/fen/fen_010_fen4_0090_experiment.png |
| fen4_0097 | quiz_images/fen/experiment.png | quiz_images/fen/fen_010_fen4_0097_experiment.png |
| mat1_0179 | quiz_images/mat/picture.png | quiz_images/mat/mat_018_mat1_0179_picture.png |

## Skipped questions (sample, with reason)

- **fen3_0422** (graph_line): insufficient data to construct graph
- **fen3_0428** (graph_line): insufficient data to construct graph
- **fen4_0073** (graph_line): insufficient data to construct graph
- **fen7_0012** (graph_line): insufficient data to construct graph
- **sosyal** (picture): ambiguous stem / non-priority picture type
- **english** (picture): ambiguous stem / non-priority picture type

Most skipped: graph_line questions where stem does not contain extractable numeric series or clear axis labels; picture questions in sosyal/english where we do not have a deterministic way to generate content-matching images without weak placeholders.

... and 252+ more skipped (insufficient data, ambiguous stem, or non-priority type).

## Files modified

- 88 JSON files under `app/src/main/assets/lgs_import/` (fen, fen4, fen7, mat1, mat3, mat5, fen basinc/elektrik/enerji/madde/mevsimler packs).
- Only `imageAsset` field was changed; schema, ids, ordering, UTF-8 preserved.

## Implementation

- **Script:** `scripts/upgrade_visual_assets.py`
- **Asset naming:** `{subject}_{packId}_{questionId}_{type}.png` under `quiz_images/{subject}/`
- **Generation:** Pillow (bar/line from extracted numbers and labels; experiment schematic from keywords; counting picture with N shapes for mat).
- **Reuse:** Not implemented in this pass (each upgraded question got its own file); can be added later via content hash.
- **Quality rule:** Upgraded only when content could be inferred (e.g. “en yüksek 60, en düşük 20”, “Pazartesi 40, Salı 35”, pressure–depth, experiment keywords). No mass-replace with weak visuals.
