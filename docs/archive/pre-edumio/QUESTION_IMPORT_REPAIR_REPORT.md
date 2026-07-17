## BrainBuddy Question Import Repair Report

### 1. Files changed

- `DbSeeder.kt`
- `QuestionMapper.kt`
- `QuestionEntity.kt`
- `BrainBuddyDatabase.kt`
- `DatabaseProvider.kt`
- `QuestionRepository.kt`
- `QuestionPackImporter.kt`
- `QuestionDao.kt`
- `RoomQuizDataStore.kt`

### 2. Root causes fixed

1. **Grade clamping to 1–7 in seeding/domain mapping**
   - `DbSeeder.parseQuestionObject` now accepts grades **1..8** from JSON and from `gradeTag`/`grade_level`, instead of coercing everything into 1..7 or discarding grade‑8 content.
   - `QuestionMapper.toQuestion` now preserves grade 8 for LGS questions:
     - For `examType == LGS`, `grade` is taken from `QuestionEntity.grade` (≥1) without clamping to 7.
     - For non‑LGS, grades are still clamped to 1..7 for grade‑mode logic.

2. **Over‑aggressive deduplication**
   - The **unique index** on `(grade, subject, stemHash)` has been removed from the Room entity and schema:
     - `QuestionEntity` indices no longer declare a unique index on `(grade, subject, stemHash)`.
     - `BrainBuddyDatabase` now includes `MIGRATION_19_20` (and `@Database` version bumped to 20), which:
       - Drops `unique_questions_grade_subject_stem_hash` if it exists.
       - Recreates a **non‑unique** index `index_questions_grade_subject_stem_hash` on `(grade, subject, stemHash)` for lookup only.
     - `DatabaseProvider` registers `MIGRATION_19_20` so real devices migrate cleanly.
   - `QuestionRepository.mergeImportedQuestions` was relaxed:
     - Still de‑duplicates **within the imported JSON batch** via `batchSeenStemKeys`, but no longer skips questions just because an equivalent `(grade, subject, stemHash)` already exists in the DB.
   - `QuestionPackImporter.runLgsImport` was relaxed:
     - Still skips duplicates **within the current LGS pack import** using `batchSeenStemKeys`.
     - No longer filters out items solely because the same `(grade, subject, stemHash)` is already in the DB.

3. **Over‑pruning by LGS quality gate**
   - `QuestionPackImporter.runLgsImport` still computes `LgsQualityRules.evaluate`, preserving:
     - `qualityScore`
     - `isNewGenerationLike`
     - `questionType`
     - `deactivationReason`
   - However, deactivation is now **much less aggressive**:
     - Previously, any `qualityResult.isActive == false` marked the question inactive.
     - Now only questions where `deactivationReason == "too_short"` are actually stored with `isActive = 0` and counted as `deactivatedLowQuality`.
     - All other LGS questions (even if flagged non‑active by the old rules) are stored with `isActive = 1` and remain usable by the picker.

4. **Small candidate pool caps**
   - `QuestionDao` candidate pool limits were increased:
     - Grade‑mode, single difficulty:
       - `getCandidatePoolByGradeSubjectDifficulty` LIMIT increased from **200 → 2000**.
     - Grade‑mode, any difficulty:
       - `getCandidatePoolByGradeSubject` LIMIT increased from **200 → 2000**.
     - LGS mode:
       - `getCandidatePoolByLgsSubject` LIMIT increased from **300 → 2000**.
   - `RoomQuizDataStore` documentation comments were updated to reflect the new `2000` limits.

### 3. Grade handling status

- **Stored data**
  - Seeding from JSON (`DbSeeder`) now accepts and preserves grades **1..8**, so 8th‑grade non‑LGS content in base/import packs is no longer forced into 7 or dropped purely due to range restrictions.
  - LGS packs continue to store grade‑8 rows with `examType = "LGS"`.
- **Domain model**
  - For LGS questions, `Question.grade` now accurately reflects the underlying DB grade (typically 8), while `examType = LGS` distinguishes the mode.
  - For non‑LGS questions, `Question.grade` remains clamped to 1..7 so existing grade‑mode logic and queries remain correct.
  - UI presentation continues to use the separate display label (e.g. `"LGS"` instead of `"8"` for LGS headers), so the user‑visible model (grades 1–7 + LGS mode) is preserved without corrupting stored grade values.

### 4. Dedup behavior after repair

- **What is still deduped**
  - Within a single import batch (`mergeImportedQuestions` or `runLgsImport`), questions sharing the same `(grade, subject, stemHash)` are still treated as duplicates and only one instance is kept. This prevents exact same question text in the same pack from ballooning the DB.
- **What is no longer discarded**
  - The DB schema no longer enforces a unique `(grade, subject, stemHash)` constraint, so multiple variants of similar content across different imports/packs can coexist.
  - The import pipeline no longer discards new questions just because the existing DB already has a question with the same `(grade, subject, stemHash)` – only true, within‑batch duplicates are skipped.
  - As a result, the import pipeline is now **much less destructive**, preserving a significantly larger portion of the corpus.

### 5. LGS quality filtering status

- LGS items are still scored and annotated by `LgsQualityRules`, and debug metrics like `deactivatedLowQualityCount`, `qualityScore`, and `isNewGenerationLike` remain available.
- Actual deactivation (making questions unusable) is now restricted to the most clearly unusable items:
  - Only questions flagged with `deactivationReason == "too_short"` are persisted with `isActive = 0`.
  - All other LGS questions, even if previously considered low quality by heuristic rules, are preserved as `isActive = 1` and become part of the usable pool.
- This keeps obviously broken/empty questions out while avoiding over‑pruning of otherwise valid LGS content.

### 6. Picker pool behavior after repair

- **Grade mode**
  - Candidate pools per `(grade, subject[, difficulty])` now load up to **2000** active questions from the DB instead of 200.
  - Combined with the relaxed dedup, this means grade‑mode tests can draw from a much larger and more varied set of questions.
- **LGS mode**
  - Each LGS subject’s candidate pool now includes up to **2000** active `examType='LGS'` questions (vs 300 previously).
  - With more LGS rows kept active (quality gate relaxed) and a higher pool cap, the LGS picker can utilize a far larger fraction of the imported LGS bank.

### 7. Expected effects on usable pool sizes and mapping

- **Active, usable question counts**
  - More grade‑8 and LGS questions survive import instead of being coerced or dropped.
  - Non‑LGS questions with similar stems but different IDs/packs are preserved across imports, no longer culled by a global unique index.
  - LGS items are rarely deactivated (only for extremely short, clearly invalid stems), so the LGS active pool grows substantially.
- **Grade/mode correctness**
  - Stored grades now reflect true values (including 8), and `examType` remains the authoritative mode flag.
  - Domain mapping no longer hides grade 8 for LGS; UI continues to present LGS separately without altering the underlying data.
- **Picker variety**
  - With larger limits and less aggressive dedup/quality gating, both grade and LGS pickers now draw from a **much larger share of the database**, increasing variety and making better use of the imported bank.

Overall, the import pipeline has been repaired to:
- Preserve imported questions more faithfully.
- Maintain correct grade/examType data.
- Reduce overly aggressive deduplication and quality pruning.
- Allow pickers to operate over substantially larger, grade‑correct pools.

