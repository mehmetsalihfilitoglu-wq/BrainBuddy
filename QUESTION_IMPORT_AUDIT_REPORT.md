## BrainBuddy Question Import Audit

**Scope**: JSON/assets → import pipeline → Room `questions` table → repository picker pools (grade mode + LGS + retry/wrong).

### 1. Pipeline overview

- **Source assets**
  - `app/src/main/assets/questions_tr.json` – legacy merged pool used by `QuestionImportRepository` for analytics and by `DbSeeder.loadFromAssets`.
  - `app/src/main/assets/packs/grade{2..8}_{subject}.json` – per-grade/subject packs, discovered by `DbSeeder.discoverPackAssetFiles`.
  - `app/src/main/assets/lgs_import/**` – LGS packs (mat, turkce, fen, inkilap, din, ing, english*, sosyal*, hayat*), consumed by `QuestionPackImporter`.
  - Optional runtime imports via `QuestionImportActivity` → `QuestionPackImporter.importFromJson` (wrapped JSON) or `.importRawArray` (bare array).

- **Parsing/mapping to entities**
  - **Grade‑mode base/imported packs** (non‑LGS):
    - Parsed by `DbSeeder.parseQuestionObject` into `QuestionEntity` with:
      - `grade` forced to **1..7 only**. Any `grade` or `gradeTag` outside 1..7 throws and *drops the question*.
      - `subject` normalized to short DB keys: `"mat" | "turkce" | "fen" | "sosyal" | "ing"`.
      - `difficulty` normalized to `0..2`.
      - `stemNormalized` + `stemHash` computed from `questionText`.
      - `examType` **not set** → remains `null` (treated as `GENERAL` by DAO).
  - **LGS packs (`lgs_import/**`)**:
    - Parsed by `QuestionPackImporter.parseLgsQuestion` into `QuestionEntity` with:
      - `grade = defaultGrade` (typically `LGS_GRADE = 8` or `forceGrade`).
      - `subject` normalized via `normalizeSubject` to DB keys (`mat`, `turkce`, `fen`, `inkilap`, `din`, `ing`, etc.).
      - `difficulty` normalized to `0..2`.
      - `examType = "LGS"`.
      - `stemNormalized`, `stemHash`, `type`, `skill`, `source*` fields populated.

- **Seeding / insert into Room**
  - **Initial/force seed**: `DbSeeder.performSeed`:
    - Loads:
      - Base `questions_tr.json`.
      - All discovered `packs/*` JSONs.
      - `files/imported_questions.json` (if present on device) via `DbSeeder.loadFromImported`.
    - Builds a batch list `questions: MutableList<QuestionEntity>`.
    - **In-batch dedup before insert**:
      - Key: `stemKey = "${grade}|${subject}|stemHashWithoutDupSuffix"`.
      - `questions.distinctBy { stemKey(it) }` → only one row survives per (grade, subject, normalized stem).
    - Inserts with `questionDao.insertAllIgnore(dedupedList)`:
      - `INSERT IGNORE` semantics at Room level (primary key = `id`).
      - Any ID collision with existing row in `questions` means the new question is silently dropped.
  - **Runtime imports via `QuestionImportActivity`**:
    - User JSON → `QuestionPackImporter.importFromJson` / `.importRawArray` → `runImport`.
    - `runImport`:
      - Parses each item into `QuestionEntity` (either LGS or GENERAL depending on pack).
      - Computes `stemKey = "${grade}|${subject}|stemHashWithoutDupSuffix"`.
      - Maintains:
        - `existingStemKeys` from DB (`questionDao.getAllQuestions()`).
        - `batchSeenStemKeys` for within‑import dedup.
      - Runs `QuestionQualityGate.evaluate(...)` and, if low quality:
        - Sets `isActive = false`, `deactivationReason = "low_lgs_quality"` (for LGS) or other reason for GENERAL.
      - Inserts with `questionDao.insertAll(entities)` (REPLACE on primary key).

- **Domain mapping & picker inputs**
  - `QuestionMapper.toQuestion(e: QuestionEntity)`:
    - `subject` = enum via `mapSubject(e.subject)`.
    - `grade` = `e.grade.coerceIn(1, 7)`.
    - `gradeTag` = `grade.toString()`.
    - `examType` from `e.examType` (string `"LGS"`, `"TYT"`, `"AYT"`, `"GENERAL"`).
  - **Picker candidate pools** (`QuestionDao`):
    - Grade mode:
      - `getCandidatePoolByGradeSubjectDifficulty(grade, subject, difficulty)` – **LIMIT 200, isActive = 1**.
      - `getCandidatePoolByGradeSubject(grade, subject)` – **LIMIT 200, isActive = 1**.
    - LGS mode:
      - `getCandidatePoolByLgsSubject(subject)` – **LIMIT 300, isActive = 1, examType = 'LGS'**.
      - `getLgsCandidatePoolWithQuality(subject)` – same filter + ordered by `qualityScore DESC`.
  - **High‑level pickers**:
    - Grade tests: `RoomAdaptiveQuestionPicker` / `QuestionRepository.pickQuizQuestionsByGrade` using grade+subject pools.
    - LGS tests: LGS blueprint uses `LGS_SUBJECTS = [mat, turkce, fen, inkilap, din, ing]` and the LGS candidate pool queries above.
    - Retry/wrong flows reuse `QuestionRepository` + `WrongQuestionStore` but ultimately map back through the same `Question` model and chip header logic.

### 2. Database snapshot – what is actually stored

> Note: from this repository we cannot read your on‑device Room DB. The counts below describe *how the app computes them* and where loss happens. To see live numbers, open **Parent → Question Import → “Havuz Durumu”** in the app, which uses these DAO queries.

- **Core DB schema**: `QuestionEntity` (`app/src/main/java/com/brainbuddy/app/db/QuestionEntity.kt`)
  - Key fields:
    - `id` (PK, string), `grade` (Int), `subject` (String DB key), `difficulty` (0–2), `isActive` (Boolean), `examType` (String?).
    - Diversity fields: `type`, `skill`, `stemNormalized`, `stemHash`, `qualityScore`, `isNewGenerationLike`.
    - Source trace: `sourcePack`, `source`, `sourceRef`, `publisher`, `year`, `topic`.
  - Indices (affect dedup and queries):
    - `unique_questions_grade_subject_stem_hash` on `(grade, subject, stemHash)` – **unique**.

- **DB‑level count APIs used for audit**
  - Total & active:
    - `countAll()`, `countAllActive()`.
  - By grade / grade+subject / grade+subject+difficulty:
    - `getCountsByGradeSubject()` – active, grade 1..7.
    - `getActiveCountsByGradeSubjectDifficulty()` – active, grade 1..7.
  - Grade integrity:
    - `countByGradeOnly(g)`, `countActiveByGradeOnly(g)` for any grade (including 0, 8, >7).
    - `countInvalidGrades()` = rows with `grade < 1 OR grade > 7`.
  - LGS pool:
    - `countLgsActive()` – active, `examType='LGS'`.
    - `getLgsCountsBySubject()` – active LGS per subject.
    - `getLgsCountsByDifficulty()` – active LGS per difficulty.
    - `countLgsInactiveLowQuality()` – LGS rows where `isActive=0` and `deactivationReason='low_lgs_quality'`.
    - Per‑subject LGS breakdowns via `getLgsCountsByDifficultyForSubject`, `getLgsCountsByQuestionTypeForSubject`, `countLgsInactiveLowQualityBySubject`.

### 3. Source vs DB – where questions are lost

#### 3.1. Loss points from assets/JSON → Room

1. **Invalid or missing grade (grade‑mode packs via DbSeeder)**
   - `DbSeeder.parseQuestionObject` demands a **grade in 1..7**:
     - Reads `grade` or `grade_level` as int.
     - If not in `1..7`, tries to parse `gradeTag`/`grade_level` string and `coerceIn(1, 7)`.
     - If still invalid → throws `IllegalArgumentException("Invalid grade...")` → the question is **dropped**.
   - Any pack that encodes LGS/8th‑grade questions with `grade = 8` but without a compatible `gradeTag` string will have those items discarded at seed time.

2. **Unsupported subject keys**
   - `DbSeeder.parseQuestionObject` maps only:
     - `"mat" | "matematik" | "math"` → `"mat"`.
     - `"turkce" | "türkçe" | "tr"` → `"turkce"`.
     - `"fen" | "fen bilimleri"` → `"fen"`.
     - `"sosyal" | "sosyal bilgiler"` → `"sosyal"`.
     - `"ing" | \"ingilizce\" | \"english\" | \"eng\"` → `"ing"`.
   - Any other `subject` string throws `"Unsupported subject"` → question dropped.

3. **Malformed items**
   - Questions with:
     - Missing/blank `stem`/`questionText`.
     - Missing `options`/`choices` array.
     - Fewer than 2 non‑blank options.
   - All cause `IllegalArgumentException` in `DbSeeder.parseQuestionObject` → dropped.

4. **In‑batch dedup by `(grade, subject, stemHash)`**
   - `performSeed` constructs `dedupedList = questions.distinctBy { "${grade}|${subject}|stemHash" }`.
   - If packs contain near‑identical copies of a question (same normalized stem) for the same grade+subject, only one is kept **before** insert.
   - This is *in addition* to the DB‑level unique index on `(grade, subject, stemHash)`.

5. **Primary key collisions on `id`**
   - `QuestionEntity.id` is the primary key.
   - `performSeed` inserts via `insertAllIgnore` (INSERT IGNORE semantics):
     - If two source questions share the same `id`, the **first one wins**, subsequent ones are silently ignored.
   - For LGS packs, `parseLgsQuestion` generates deterministic IDs like:
     - `"lgs_${grade}_${subject}_${index}_${stemHash.take(8)}"`.
   - For generic imports, `parseAndNormalize` uses deterministic IDs from normalized stem+answer.
   - If any pack duplicates that ID (same stem & correct answer) across files, the second copy is effectively lost.

6. **Quality gating (LGS + some GENERAL imports)**
   - In `QuestionPackImporter.runLgsImport` and `runImport`:
     - Each parsed `QuestionEntity` is evaluated by `QuestionQualityGate.evaluate(...)`.
     - For LGS:
       - If gate marks it low quality, the entity is written with `isActive = false` and `deactivationReason = "low_lgs_quality"`.
     - For some GENERAL imports, subject‑specific “too simple” rules can also deactivate HARD items.
   - These rows remain in the DB but are **invisible to all pickers** (`isActive = 0` is filtered out in every candidate query).

#### 3.2. Loss from DB → picker candidate pools

1. **Active‑only filters**
   - Every candidate pool query (`getCandidatePoolByGradeSubject*`, `getCandidatePoolByLgsSubject`, `getLgsCandidatePoolWithQuality`) includes `isActive = 1`.
   - Any question deactivated by:
     - LGS quality gate (`low_lgs_quality`).
     - HARD contextuality/length rules in `QuestionRepository.mergeImportedQuestions`.
   - …is permanently excluded from tests.

2. **Strict grade/subject match**
   - Grade mode uses exact `(grade, subject)` match in queries; there is **no fuzzy fallback** to neighboring grades.
   - If an imported question has the wrong `grade` or `subject` DB key, it will never be considered for that grade/subject’s tests.

3. **Candidate pool LIMITs**
   - Grade mode:
     - Each `(grade, subject[, difficulty])` pool is capped at **200 rows** (`LIMIT 200`).
   - LGS mode:
     - Each LGS subject pool is capped at **300 rows**.
   - If you have thousands of questions per subject, most are **never even loaded into the in‑memory pool** and thus never eligible for selection.

4. **Similarity / recency filters at selection time**
   - `QuestionRepository` applies:
     - Near‑duplicate Jaccard similarity filtering between token sets of stem+options.
     - Recency/“recently seen” filters driven by `QuestionHistoryStore`.
   - These do **not change DB counts**, but further shrink the *effective* pool within a single test.

### 4. Critical field mapping audit

- **Grade**
  - Grade‑mode assets and imports (`DbSeeder.parseQuestionObject`):
    - Any grade outside 1..7 is coerced via `gradeTag` or discarded.
    - Resulting DB grade is always in `1..7`; there is **no grade 8** in this path.
  - LGS packs (`parseLgsQuestion`):
    - Use `defaultGrade` (usually `8`) untouched.
    - DAO treats them as grade 8, but domain `Question` clamps grade to 1..7 for display, while `examType = LGS` is retained.
  - **Mismatch risk**:
    - Any non‑LGS pack that encodes true 8th‑grade questions will be coerced into 7 or dropped entirely.

- **Subject**
  - DB keys are consistent (`mat`, `turkce`, `fen`, `sosyal`, `ing`, `inkilap`, `din`, …).
  - `QuestionMapper.mapSubject` and `QuestionMapper.toDbSubject` / `SpacedRepetitionPicker.toDbSubject` use the same mapping, so grade‑mode subjects are consistent.
  - LGS `normalizeSubject` in `QuestionPackImporter` targets the same keys for mat/turkce/fen/etc.
  - **Import risk** is primarily unsupported spellings (e.g. pack‑specific subject labels).

- **examType**
  - Base packs via `DbSeeder` never set `examType`, so `QuestionEntity.examType` remains `null` → treated as `'GENERAL'`.
  - LGS packs always set `examType = "LGS"`.
  - Generic imports via `QuestionPackImporter.importFromJson` default to `examType = "GENERAL"` unless the JSON explicitly carries LGS semantics and uses `parseLgsQuestion`.
  - Picker logic:
    - Grade mode **ignores** `examType`.
    - LGS mode **requires** `COALESCE(examType, 'GENERAL') = 'LGS'`.
  - **Result**:
    - Any LGS‑intended question that doesn’t come through the dedicated LGS import path (or doesn’t set `examType='LGS'`) will never appear in LGS tests.

### 5. Picker pool sizes vs expectations

- **Grade mode**
  - Logical DB pool per `(grade, subject)`:
    - All rows with `grade = g`, `subject = s`, `isActive = 1` (any difficulty).
  - Candidate pool actually used per test:
    - At most **200 rows** per `(grade, subject)` (`getCandidatePoolByGradeSubject*`), *before* similarity/recency filters.
  - If you expect ~500+ questions per (grade, subject), the picker is currently sampling from at most 40% of that pool.

- **LGS mode**
  - Logical DB pool per subject:
    - All rows with `examType='LGS'`, `subject = s`, `isActive = 1`.
  - Candidate pool actually used:
    - At most **300 rows** per subject (`getCandidatePoolByLgsSubject` / `getLgsCandidatePoolWithQuality`).
  - Given hundreds of LGS packs, the majority of LGS questions for a subject are likely never considered in any single run.

### 6. Root causes & verdict

Based on the code:

1. **Questions lost at import time (assets → Room)**
   - Invalid/missing grade or subject in JSON (including grade 8 in non‑LGS packs) causes hard drops.
   - In‑batch `(grade, subject, stemHash)` dedup in `DbSeeder.performSeed` discards additional variants of the same normalized stem.
   - Primary key collisions on `id` during `insertAllIgnore` (initial seed) or `insertAll` (runtime imports) silently drop later copies.

2. **LGS‑specific losses**
   - LGS packs are parsed into grade‑8 rows with `examType='LGS'`, but:
     - Many are marked inactive by `QuestionQualityGate` (`low_lgs_quality`) and thus never appear in candidate pools.
     - Candidate pools limit to 300 rows per LGS subject, heavily under‑utilizing the total imported LGS corpus.

3. **Display vs data mismatch**
   - Domain `Question.grade` is clamped to 1..7, even for grade‑8 LGS rows, and `gradeTag` is derived from that clamp.
   - While the header chip now uses `gradeDisplayLabel` (LGS → `"LGS"`), underlying `grade` still appears as 7 in some analytics, which can be confusing when manually inspecting DB rows.

4. **Picker pool much smaller than source**
   - Because of `LIMIT 200/300` and `isActive = 1`, the effective selection pool is a strict subset of the already‑filtered DB.

**Final verdict**: the import pipeline is **not fully healthy** for your “hundreds of questions per subject” goal. The main structural loss points are:

- Strict grade validation that discards any 8th‑grade/non‑LGS questions.
- Aggressive deduplication by `(grade, subject, stemHash)` in both seeding and import.
- Quality gates that deactivate a significant fraction of LGS items.
- Hard `LIMIT 200/300` caps on candidate pools, causing many valid questions to be effectively unreachable in normal use.

To get exact live counts on a device, use the existing debug APIs exposed via **Question Import → Havuz Durumu**, which run the DAO queries listed in section 2 against the actual Room database.

