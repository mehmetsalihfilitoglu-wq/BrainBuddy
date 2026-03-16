# Full Import Pipeline Diagnosis: Why ~18k Asset Questions Show as ~2.3k (All Invalid Grade)

## Executive summary

**Root cause:** The app runs the full seed (which loads root GENERAL files, `packs/`, and **all** `lgs_import` grade-based folders) **only when** `db_seed_version < CURRENT_DB_SEED_VERSION` (2). After the first successful seed, the DB stores `db_seed_version = 2`, so **every subsequent app startup skips seeding**. The ~18k questions in `lgs_import/**` are never loaded because `performSeed` is never called again. The ~2,348 rows you see are from an **old** seed (earlier install or older code) that either did not include `loadFromLgsGradePacksAsGeneral` or wrote wrong grades; all of them fall in “invalid grade” (0 or 8) for the Pool Status 1..7 view.

---

## 1. Which asset folders/files are actually scanned during seeding/import?

**DbSeeder (GENERAL seeding) – only runs when `seedIfNeeded` actually performs seed:**

| Source | What is scanned |
|--------|------------------|
| **Root GENERAL** | Exactly 2 files: `questions_tr.json`, `import_template.json` (from `ROOT_GENERAL_QUESTION_FILES`). |
| **packs/** | Recursive scan; only files whose **name** matches `PACK_FILE_REGEX`: `grade(1|2|3|4|5|6|7|8)_(mat|turkce|fen|sosyal|ing).json` (case-insensitive). So only `gradeN_subject.json` in `packs/` or subdirs. |
| **lgs_import (grade-based)** | **Fixed list of 38 directories** (no recursive discovery). Only these are scanned: `hayat2`, `hayat3`, `fen3`–`fen7`, `mat1`–`mat5`, `turkce1`–`turkce5`, `turkce7`, `english1`–`english7`, `din4`–`din7`, `sosyal4`–`sosyal6`, `inkilap7`. **Not** scanned by DbSeeder: `lgs_import/mat`, `lgs_import/fen`, `lgs_import/turkce`, `lgs_import/din`, `lgs_import/english`, `lgs_import/inkilap` (true LGS), and **`lgs_import/hayat1`** (missing from list). |

**QuestionPackImporter (LGS):** Runs only when user taps DEBUG buttons (e.g. “Import LGS Question Packs”). It imports from many `lgs_import` dirs (including grade-based and LGS). It does **not** run on normal startup.

**Code references:**
- Root + packs: `DbSeeder.loadFromAssets()` → `ROOT_GENERAL_QUESTION_FILES`, `discoverPackAssetFilesRecursive()` → `PACK_FILE_REGEX` in `collectPackPaths()`.
- lgs_import grade-based: `loadFromLgsGradePacksAsGeneral()` → hardcoded `gradeBasedDirs` list.

---

## 2. How many JSON files exist under assets?

- **Total JSON under `app/src/main/assets`:** **1,909**
- **Under `app/src/main/assets/lgs_import`:** **1,900**
- **Under `app/src/main/assets/packs`:** **5** (only those matching `gradeN_subject.json`)
- **Root:** 2 files (`questions_tr.json`, `import_template.json`); remaining 2 are elsewhere (e.g. under other asset subdirs).

So the vast majority of question JSONs (1,900) are under `lgs_import/`. All of them are only loaded when `performSeed()` runs.

---

## 3. How many questions exist physically in all JSON files combined?

- Root files: on the order of **~200** (e.g. 203 in `questions_tr.json`).
- packs: 5 files × variable = small.
- lgs_import: **~1,900 files**; from sample files, each has on the order of **~10–20** questions in the `questions` array → **~15,000–20,000** questions.

So **~18,000 questions in assets** is consistent with the repo. All of these are only considered when `loadFromAssets()` (and thus `loadFromLgsGradePacksAsGeneral()`) is executed inside `performSeed()`.

---

## 4. How many questions are actually inserted into the database?

- **Insert path:** `performSeed()` → `loadFromAssets()` + `loadFromImported()` → dedup by `(grade, subject, stemHash)` → `questionDao.insertAllIgnore(dedupedList)`.
- **insertAllIgnore** uses Room `OnConflictStrategy.IGNORE`: if a row with the same primary key `id` already exists, that row is **skipped** (no replace). So “inserted” count can be lower than `dedupedList.size` if IDs collide with existing DB rows.
- **When seed is skipped:** If `seedIfNeeded()` returns early (because `storedVersion >= 2`), then **zero** questions from assets are loaded or inserted in that session. The DB keeps whatever was inserted in a **previous** seed run (e.g. ~2,348 rows from an old run that didn’t load lgs_import or wrote wrong grades).

So in your current situation: **only the old seed run inserted ~2,348.** No new inserts happen on startup because seed is skipped.

---

## 5. How many are skipped, and exact reasons (by code path)

| Skip reason | Where it happens | Effect |
|-------------|------------------|--------|
| **Seed not run** | `DbSeeder.seedIfNeeded()`: `if (storedVersion >= CURRENT_DB_SEED_VERSION) return false` | Entire `performSeed()` and `loadFromAssets()` are never called → **all ~18k from assets are “skipped”** (never loaded). This is the main cause of “missing” questions. |
| **Invalid grade** | `parseQuestionObject()`: if grade not in 1..8 and gradeTag/grade_level parse fails → `throw IllegalArgumentException("Invalid grade for question index=...")` | That question is not added; caught in `parseWrappedQuestionArray` / `parseJsonArrayWithDefaults` → logged, next question. |
| **Invalid / missing subject** | `parseQuestionObject()`: missing/blank or unsupported subject → throw | Same as above; one question dropped per throw. |
| **Parse error** | Any JSON/field exception in parse (missing stem, options, etc.) | Caught in the same try/catch; question skipped, log “Parse failed …”. |
| **Duplicate (in-memory)** | `performSeed()`: `questions.distinctBy { stemKey(it) }` where `stemKey = "${grade}|${subject}|${stemHash}"` | Later occurrence of same (grade, subject, stemHash) in the same batch is dropped before insert. |
| **Duplicate (DB)** | `insertAllIgnore`: Room IGNORE on conflict by `id` | If an entity with same `id` already exists, that insert is skipped. |
| **Missing fields** | `parseQuestionObject()`: missing stem/questionText, options/choices, or &lt; 2 options → throw | Treated as parse error; question skipped. |
| **Unsupported format** | Root/pack file not starting with `[` or `{` → `emptyList()`; or no `questions` array in wrapped → `emptyList()` | Entire file yields 0 questions. |

So the **dominant** skip in your scenario is: **seed not run** (version check), so the whole asset set is never processed. The “invalid grade” you see in Pool Status refers to the **existing** ~2,348 rows (grade 0 or 8), not to questions skipped during parse.

---

## 6. Summary by source folder (when seed *does* run)

When `performSeed()` runs, the pipeline does **not** currently produce a per-folder summary in code. The following is the **intended** behavior by source:

| Folder / source | Files count (approx) | Questions (in files) | Inserted | Skipped (typical) |
|-----------------|----------------------|----------------------|----------|--------------------|
| Root (questions_tr, import_template) | 2 | ~203 + small | All if valid | Parse/validation only |
| packs/ (gradeN_subject.json) | 5 | Variable | All if valid | Same |
| lgs_import/hayat2, hayat3 | Many | Hundreds | With path grade 2/3 | Parse/dup |
| lgs_import/fen3–7 | Many | Thousands | With path grade 3–7 | Parse/dup |
| lgs_import/mat1–5 | Many | Thousands | With path grade 1–5 | Parse/dup |
| lgs_import/turkce1–5, turkce7 | Many | Thousands | With path grade 1–5, 7 | Parse/dup |
| lgs_import/english1–7 | Many | Thousands | With path grade 1–7 | Parse/dup |
| lgs_import/din4–7, sosyal4–6, inkilap7 | Many | Thousands | With path grade | Parse/dup |
| **lgs_import/hayat1** | Present in assets | Present | **0** (folder not in list) | **Folder not scanned** |
| imported_questions.json | 0 or 1 | Variable | Merged before dedup | - |

So the only folder with **files but 0 questions** in the current design is **lgs_import/hayat1**, because it is not in `gradeBasedDirs`.

---

## 7. Is the app using an old existing database instead of rebuilding from assets?

**Yes.** Seeding is **not** “rebuild from assets every time”. It runs only when:

- `db_seed_version` is missing or &lt; `CURRENT_DB_SEED_VERSION` (2).

So:

- First install or after DB wipe: version is 0 → seed runs once → loads assets → sets `db_seed_version = 2`.
- Every later startup: version is 2 → `seedIfNeeded()` returns false → **no** load from assets. The app keeps using the **existing** DB (your ~2,348 rows).

So the app is using an old DB that was filled in a **single** past seed run; it never “rebuilds” from the ~18k assets on normal startup.

---

## 8. Destructive migration, fallbackToDestructiveMigration, manual reseed

- **Room version:** 20. Migrations 11→12…→19→20 are registered; no automatic wipe on upgrade.
- **fallbackToDestructiveMigration:** Used **only in DEBUG** (`DatabaseProvider.get()`). On schema mismatch in debug, Room **recreates** the DB (all tables empty). Then `app_meta` is empty → `db_seed_version` null → next access to seed runs `performSeed()` once. So after a destructive run, one full seed happens. It does **not** run on every startup.
- **Manual reseed:**  
  - **Force Reseed All Question Banks** (Pool Status): calls `forceReseedGeneralBanks()` → deletes only GENERAL, sets `db_seed_version = 0`, then `performSeed()`. So it **does** rebuild GENERAL from assets (root + packs + lgs_import grade-based).  
  - **forceReseed()** (full): `deleteAll()`, then version 0, then `performSeed()`.

So: **no** automatic “destructive migration” or “rebuild from assets” on every launch. To get the full ~18k from assets into the DB you must either run a manual reseed (button) or force seed to run again by making the app believe the seed version is outdated (e.g. bump `CURRENT_DB_SEED_VERSION` and set stored version &lt; new value, or clear app data).

---

## 9. Is import limited to DEBUG only and not executed on normal startup?

- **DbSeeder.seedIfNeeded():** Called from **normal startup** in `BrainBuddyApp.onCreate()` (and from `RoomQuizDataStore.ensureSeeded()`). So GENERAL seeding **is** intended to run on normal startup — but **only when** `storedVersion < CURRENT_DB_SEED_VERSION`. So in practice it runs at most once (or after version bump / version reset).
- **QuestionPackImporter** (LGS): Invoked only from **DEBUG** UI (e.g. Pool Status “Import LGS …” buttons). Not run on normal startup.

So: GENERAL seed **is** part of normal startup, but the version check causes it to **not** run after the first successful seed. LGS import is DEBUG-only.

---

## 10. Do Pool Status filters show only a subset?

**No.** Pool Status uses:

- `dao.countAll()` → total rows.
- `dao.countByGradeOnly(g)` for g in 1..7.
- `dao.countInvalidGrades()` → `WHERE grade < 1 OR grade > 7`.

There is no filter that hides rows. So “2348 total / 2274 active” and “all invalid grade” mean: the DB really has 2,348 rows, and all of them have `grade` outside 1..7 (i.e. 0 or 8). So the **missing ~15,000 questions** are not in the DB at all; they were never inserted because the seed that loads lgs_import has not run on this DB (version already 2).

---

## Where the ~15,000 questions are lost (exact code path)

1. **BrainBuddyApp.onCreate()**  
   `CoroutineScope(...).launch { DbSeeder.seedIfNeeded(this@BrainBuddyApp) }`

2. **DbSeeder.seedIfNeeded()**  
   Reads `meta.get(KEY_DB_SEED_VERSION)`. If stored version ≥ 2, it logs “Seed already up to date” and **returns false** without calling `performSeed()`.

3. **performSeed()** is never called → **loadFromAssets()** is never called → **loadFromLgsGradePacksAsGeneral()** is never called.

4. So all **~1,900** JSON files under `lgs_import` (and their ~15k–18k questions) are never read or inserted. The only questions in the DB are from an **earlier** run of `performSeed()` (or from LGS import), which inserted ~2,348 rows, many/all with grade 0 or 8.

5. **insertAllIgnore** and **distinctBy(stemKey)** only affect the list that was actually built in that run; they do not limit the 18k, because the 18k are never added to that list.

---

## Proposed fixes (exact files / methods)

### Fix 1: Force one-time full reseed for existing installs (primary fix)

**File:** `app/src/main/java/com/brainbuddy/app/db/DbSeeder.kt`

- **Constant:** Bump `CURRENT_DB_SEED_VERSION` from `2` to `3`.
- **Effect:** On next app startup, `storedVersion` (2) is &lt; 3, so `seedIfNeeded()` runs `performSeed()`, which runs `loadFromAssets()` and `loadFromLgsGradePacksAsGeneral()`. All root, packs, and lgs_import grade-based JSONs are loaded with path-derived grades and inserted (after dedup). After that run, `db_seed_version` is set to 3, so future startups skip again (as intended).
- **User action:** No need to tap Force Reseed; just update the app and open it once. Optional: clear app data to get a completely fresh DB, then open app (seed will run with version 0 &lt; 3).

### Fix 2: Include lgs_import/hayat1 in grade-based scan

**File:** `app/src/main/java/com/brainbuddy/app/db/DbSeeder.kt`

- **Method:** `loadFromLgsGradePacksAsGeneral()`  
- **Change:** In the `gradeBasedDirs` list, add `"lgs_import/hayat1"` (e.g. after hayat2, hayat3 in the “Hayat Bilgisi” section).
- **Effect:** Questions in `lgs_import/hayat1` are loaded with grade 1 and subject hayat.

### Fix 3: Optional – diagnostic logging in performSeed

**File:** `app/src/main/java/com/brainbuddy/app/db/DbSeeder.kt`

- **Method:** `performSeed()`  
- **Change:** After `questions.addAll(loadFromAssets(context))`, log size of `questions` and, if you later add per-source counters in `loadFromAssets`, log per-source counts (e.g. root count, packs count, lgs_import count). Optionally after dedup log `dedupedList.size` and after `insertAllIgnore` run a `countAll()` and log “DB total after seed”.
- **Effect:** Easier to verify in logcat that the full set is loaded and inserted after the version bump.

---

## Verification after applying fixes

1. Bump `CURRENT_DB_SEED_VERSION` to 3 and add `hayat1` to `gradeBasedDirs`.
2. **Option A:** Clear app data, install/run app → seed runs (version 0 &lt; 3).  
   **Option B:** Just run app (version 2 &lt; 3) → seed runs once.
3. Open Pool Status: total should be on the order of **~15k–18k** (depending on dedup and INSERT IGNORE). Counts by grade 1..7 should be non-zero; “Invalid grade (0,8,9+) ” should be 0 or only LGS (grade 8) if you also import LGS.
4. If you added diagnostic logging, check logcat for “Seeded … questions” and any per-source or “DB total after seed” lines.

This gives you a full diagnosis and the exact code paths and edits so the ~18k asset questions are actually loaded and inserted, and no longer “lost” due to the seed version check.
