# GENERAL (Non-LGS) Question Bank Audit — Grades 1–7

## 1. Source counts for GENERAL grades 1–7

Counts are taken from assets only (no device `imported_questions.json`).

### questions_tr.json

| Grade | MAT | TURKCE | FEN | SOSYAL | ING | **Total** |
|-------|-----|--------|-----|--------|-----|-----------|
| 5     | 14  | 9      | 7   | 6      | 6   | **42**   |
| 6     | 16  | 10     | 11  | 7      | 8   | **52**   |
| 7     | 33  | 19     | 19  | 18     | 17  | **106**  |
| **Total** | **63** | **38** | **37** | **31** | **31** | **200** |

- **No questions for grades 1, 2, 3, or 4** in `questions_tr.json`.
- All entries use `gradeTag` / inferred grade; none use `examType`, so they are all GENERAL.

### assets/packs

- **Pattern:** `grade{G}_{subject}.json` with G ∈ 2..7, subject ∈ mat, turkce, fen, sosyal, ing.
- **Present files:** `grade6_mat.json`, `grade6_turkce.json`, `grade6_fen.json`, `grade6_sosyal.json`, `grade6_ing.json`.
- **Content:** Each file is an empty array `[]`, so **0 questions** from packs.

### Combined source total (assets only)

- **GENERAL grades 1–7 in source:** **200** (all from `questions_tr.json`; grades **5, 6, 7** only).
- **GENERAL grades 1–4 in source:** **0**.

---

## 2. DB counts for GENERAL grades 1–7

Room does not store `examType` for seeded GENERAL content (it stays `NULL`). So **all rows with `grade` 1–7 are GENERAL** (LGS uses `grade = 8`).

- **Total DB count (active + inactive) by grade:**  
  `QuestionDao.countByGradeOnly(1)` … `countByGradeOnly(7)` — one call per grade.
- **Total DB count by (grade, subject):**  
  `QuestionDao.countByGradeSubject(grade, subject)` for each grade 1..7 and each of mat, turkce, fen, sosyal, ing.

**How to obtain on device:**  
Use a debug/diagnostic screen or one-off code that runs these DAO methods and logs or displays the sums. The app does not currently expose “total (active+inactive) by grade/subject” in one place; `buildPoolDebugStatsForGrade` uses the same DAO but focuses on one grade and mixes total/active.

**After a normal seed (no imported_questions.json):**

- Seeding loads `questions_tr.json` + packs (empty) and may add **synthetic grade-6** questions when grade-6 counts per subject are below `TARGET_QUESTIONS_PER_SUBJECT` (500).
- Dedup: `DbSeeder.performSeed` uses `distinctBy(grade, subject, stemHash)` then `insertAllIgnore`, so the DB can have **fewer** rows than the 200 from `questions_tr.json` if there are duplicate stems, and **more** if synthetic questions are added (up to 500 per subject for grade 6).

So **exact DB counts depend on the device** (seed version, optional `imported_questions.json`, synthetic generation). Use the DAO above to get real numbers.

---

## 3. Active counts for GENERAL grades 1–7

- **Definition:** Rows with `isActive = 1` and `grade` between 1 and 7 (no `examType` filter; LGS is grade 8).
- **DAO:**  
  - `QuestionDao.getCountsByGradeSubject()` → `WHERE isActive = 1 AND grade BETWEEN 1 AND 7` grouped by grade, subject.  
  - `QuestionDao.getActiveCountsByGradeSubjectDifficulty()` → same filter, grouped by grade, subject, difficulty.

**How to obtain on device:**  
In **grade mode**, the debug screen (“Havuz durumu”) shows:

- `buildPoolDebugStatsForGrade(effectiveGrade, difficulty)` → includes “grade=$g total/active” and “per-subject total/active” for the **selected grade** (from the same DAO counts).
- `buildImportDebugActiveCounts()` → “ACTIVE by grade/subject/diff” from `getActiveCountsByGradeSubjectDifficulty()` for **all grades 1–7**.

So the debug screen **does show GENERAL 1–7 active counts** when in grade mode (and when a grade 1–7 is selected). It does **not** show LGS-only counts in that block (LGS is grade 8 and is excluded by `grade BETWEEN 1 AND 7`).

---

## 4. Picker pool sizes for GENERAL grades 1–7

Normal grade-based tests use:

- **API:** `RoomQuizDataStore.getCandidatePoolByGradeSubject(grade, subject)`  
  → `QuestionDao.getCandidatePoolByGradeSubject(grade, subject)`  
  → `SELECT … FROM questions WHERE grade = :grade AND subject = :subject AND isActive = 1 LIMIT 2000`.

So for each (grade, subject):

- **Picker pool size** = min(2000, number of active questions for that grade + subject).
- Only grades 1–7 are accepted; for other grades the store returns an empty list.

**How to obtain on device:**  
The same `buildPoolDebugStatsForGrade` data includes “grade=$grade $subj total/active” and “available_for_selectedDifficulty”; the **actual** pool size used by the picker is that active count capped at 2000. So the “active” count per (grade, subject) in the debug screen is the pool size when it’s under 2000.

---

## 5. Where missing questions are being lost

From code flow:

1. **Source limited to grades 5–7**  
   - `questions_tr.json` has **only** grades 5, 6, 7 (200 questions).  
   - Packs for grade 6 are empty.  
   - So **no source** for grades 1–4 in assets; any GENERAL 1–4 would have to come from `imported_questions.json` or synthetic generation (synthetic currently only fills grade 6).

2. **Dedup at seed**  
   - `DbSeeder.performSeed` uses `distinctBy(grade, subject, stemHash)`, then `insertAllIgnore`.  
   - Duplicate stems (same grade+subject+normalized stem) in the same batch become a single row.  
   - So 200 source questions can become fewer rows if there are stem duplicates.

3. **Inactive (isActive = 0)**  
   - Seeded GENERAL questions from `DbSeeder` are created with `isActive = true`; they are not run through `QuestionQualityGate` in the seed path.  
   - So loss from “marked inactive” applies mainly to **imported** (e.g. `mergeImportedQuestions`) or LGS import, not to the initial 200 from `questions_tr.json` in the seed.

4. **Picker cap**  
   - Pool per (grade, subject) is capped at **2000**.  
   - With only 200 source questions total and no grade 1–4, the cap is not the bottleneck for the current asset set.

5. **Debug screen scope**  
   - In **LGS mode**, the screen shows “Seçili Mod: LGS” and then `buildImportDebugActiveCounts()`, which still uses `getActiveCountsByGradeSubjectDifficulty()` — i.e. **grades 1–7 only**. So in LGS mode the user sees GENERAL 1–7 active counts, not LGS counts.  
   - In **grade mode**, the screen shows GENERAL pool for the selected grade + “ACTIVE by grade/subject/diff” for all 1–7. So the debug screen **does** cover the full GENERAL 1–7 scope when in grade mode; in LGS mode it is misleading (label says LGS but numbers are 1–7 GENERAL).

---

## 6. Import vs picker vs debug-screen scope

- **Import:**  
  - The **only** GENERAL source in assets is 200 questions in `questions_tr.json` (grades 5, 6, 7).  
  - Packs are empty.  
  - So the “missing” GENERAL bank for 1–7 is largely an **import/source** issue: there are no asset files for grades 1–4, and no extra GENERAL content in packs.

- **Picker:**  
  - Uses active rows only, grade 1–7, with a 2000 cap per (grade, subject).  
  - For the current source size, the picker is not dropping questions; it can only use what’s in the DB and active.

- **Debug screen:**  
  - In grade mode it shows GENERAL 1–7 (and per-grade/per-subject breakdown).  
  - In LGS mode it still shows GENERAL 1–7 counts under an “LGS” label, so the **scope** of the screen is wrong only in terms of labeling in LGS mode, not in terms of hiding GENERAL 1–7 when in grade mode.

**Conclusion:**  
The main reason the app does not “expose/use the full 1–7 question bank” is that the **source** only contains 200 GENERAL questions for **grades 5, 6, 7**. There is no GENERAL content for grades 1–4 in assets, and pack files are empty. So this is primarily an **import/source** gap, not a picker or debug-screen scope bug. To get real DB, active, and picker-pool numbers on a device, use the debug screen in grade mode (and the DAO methods above) as described in sections 2–4.
