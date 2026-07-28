# Visual Questions Rendering Debug Report

## Executive Summary

**Root cause identified:** A mix of (1) null bitmap handling bug, (2) layout/visibility improvements needed. The image assets `quiz_images/fen/picture.png`, `quiz_images/turkce/table.png`, etc. are **real images** (400x300) and should render. The app was setting `ImageView` visibility to VISIBLE even when `BitmapFactory.decodeStream()` returned null, and did not properly guard against decode failures.

## 1. Asset Audit Results

### Usable assets (400x300, ~1–1.5 KB)

| Path | Size | Dimensions | Status |
|------|------|------------|--------|
| `quiz_images/fen/picture.png` | 1545 B | 400×300 | ✅ OK |
| `quiz_images/fen/table.png` | 1174 B | 400×300 | ✅ OK |
| `quiz_images/mat/picture.png` | 1545 B | 400×300 | ✅ OK |
| `quiz_images/mat/table.png` | 1174 B | 400×300 | ✅ OK |
| `quiz_images/turkce/picture.png` | 1545 B | 400×300 | ✅ OK |
| `quiz_images/turkce/table.png` | 1174 B | 400×300 | ✅ OK |
| `quiz_images/english/picture.png` | 1545 B | 400×300 | ✅ OK |
| `quiz_images/english/table.png` | 1174 B | 400×300 | ✅ OK |
| `quiz_images/sosyal/picture.png` | 1545 B | 400×300 | ✅ OK |
| `quiz_images/inkilap/picture.png` | 1545 B | 400×300 | ✅ OK |

### Unusable assets (1×1 placeholders, 70 bytes)

- All `quiz_images/g1_*.png` (26 files)
- `quiz_images/sample_breakfast.png`

**Total:** 36 files, **26 unusable** (placeholders), **10 usable**.

## 2. Screens Checked

| Screen | Loads imageAsset? | Fixed? |
|--------|-------------------|--------|
| **QuizActivity** (main quiz) | ✅ Yes | ✅ Null check + debug log |
| **QuizActivityRetryWrong** (retry wrong) | ✅ Yes | ✅ Null check |
| **WrongAnswerReviewActivity** | ✅ Yes | ✅ Null check |
| **QuizResultActivity** (wrong list) | ❌ No ImageView in `item_wrong_answer` | N/A |

## 3. Bugs Fixed

### A. Null bitmap handling

- **Issue:** `BitmapFactory.decodeStream()` can return null. The app called `setImageBitmap(null)` and still set visibility to VISIBLE, showing a blank 160dp area.
- **Fix:** Only set visibility to VISIBLE when the bitmap is non-null. If decode returns null, keep the ImageView GONE.

### B. Layout and sizing

- **Change:** ImageView height increased from 160dp to 200dp, `minHeight="120dp"`, `adjustViewBounds="true"`.
- **Reason:** Makes the image area more noticeable when the image is visible.

### C. Debug logging (BuildConfig.DEBUG)

- Logs: `imageAsset` path, decode success, bitmap dimensions, visibility decision.
- Tag: `QuizActivity`, filter: `[VISUAL]`.

## 4. Sample Test Data

**Sample question ID with image:** Any question from `lgs_fen7_pack_005.json` (first question) or `lgs_fen7_pack_047.json` (first question).

**Sample imageAsset path:** `quiz_images/fen/picture.png`

**How to test:**
1. Import LGS packs (7. sınıf Fen) so fen7 packs are in the DB.
2. Start a Grade 7 quiz or LGS quiz that includes Fen.
3. Navigate to a visual question (e.g. fen7 pack with imageAsset).
4. In debug builds, check Logcat for `[VISUAL] imageAsset=... decodeOk=...`.

## 5. Placeholder Asset Report

- **Visual questions with usable assets:** Those using `quiz_images/{fen,mat,turkce,english,sosyal,inkilap}/{picture,table}.png` (400×300).
- **Visual questions with unusable assets:** Those using `quiz_images/g1_*.png` or `sample_breakfast.png` (1×1 placeholders).
- **Recommendation:** Replace 1×1 placeholders with real images (charts, diagrams, photos) for g1/hayat packs.

## 6. Files Changed

| File | Changes |
|------|---------|
| `QuizActivity.kt` | Null check for bitmap, `BuildConfig.DEBUG` logging |
| `QuizActivityRetryWrong.kt` | Null check for bitmap |
| `WrongAnswerReviewActivity.kt` | Null check for bitmap |
| `activity_quiz.xml` | ImageView: 200dp height, minHeight, adjustViewBounds |
| `scripts/audit_image_assets.py` | New script for asset audit |

## 7. Diagnosis

| Category | Finding |
|----------|---------|
| **Asset loading** | `assets.open(path)` and `BitmapFactory.decodeStream` used correctly. Paths like `quiz_images/fen/picture.png` resolve. |
| **Asset quality** | 10 subject images (fen, mat, turkce, etc.) are usable 400×300; 26 g1/sample assets are 1×1 placeholders. |
| **UI rendering** | ImageView constraints, `scaleType`, and `visibility` are correct. Null bitmap was the main issue. |
| **Layout** | Constraints valid. ImageView has fixed height; when VISIBLE it occupies space above the question card. |
| **Path** | Paths in JSON match asset paths. No path bug identified. |
