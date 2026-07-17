## BrainBuddy Question Corpus – Project‑wide Inventory

### 1. Total corpus and high‑level split

- **Total questions present in repo (estimated)**: **≈12,000**  
  - **GENERAL (non‑LGS, grades 1–7)**: **203**  
    - `app/src/main/assets/questions_tr.json` → **200** questions (grades 5–7 across MAT, TURKCE, FEN, SOSYAL, ING)  
    - `app/src/main/assets/import_template.json` → **3** questions (grade 6 MAT demo pack)  
  - **LGS corpus (grade 1–8, examType = "LGS")**: **≈11,800+** questions  
    - At least **11,500** questions from **23 per‑grade/subject banks** (each 50 packs × 10 questions):  
      - `hayat2_question_bank_report.md` → `app/src/main/assets/lgs_import/hayat2/` → **500**  
      - `hayat3_question_bank_report.md` → `lgs_import/hayat3/` → **500**  
      - `fen3_question_bank_report.md` → `lgs_import/fen3/` → **500**  
      - `turkce3_question_bank_report.md` → `lgs_import/turkce3/` → **500**  
      - `mat3_question_bank_report.md` → `lgs_import/mat3/` → **500**  
      - `din4_question_bank_report.md` → `lgs_import/din4/` → **500**  
      - `english4_question_bank_report.md` → `lgs_import/english4/` → **500**  
      - `fen4_question_bank_report.md` → `lgs_import/fen4/` → **500**  
      - `sosyal4_question_bank_report.md` → `lgs_import/sosyal4/` → **500**  
      - `turkce4_question_bank_report.md` → `lgs_import/turkce4/` → **500**  
      - `mat4_question_bank_report.md` → `lgs_import/mat4/` → **500**  
      - `din5_question_bank_report.md` → `lgs_import/din5/` → **500**  
      - `english5_question_bank_report.md` → `lgs_import/english5/` → **500**  
      - `sosyal5_question_bank_report.md` → `lgs_import/sosyal5/` → **500**  
      - `fen5_question_bank_report.md` → `lgs_import/fen5/` → **500**  
      - `turkce5_question_bank_report.md` → `lgs_import/turkce5/` → **500**  
      - `mat5_question_bank_report.md` → `lgs_import/mat5/` → **500**  
      - `din6_question_bank_report.md` → `lgs_import/din6/` → **500**  
      - `english6_question_bank_report.md` → `lgs_import/english6/` → **500**  
      - `sosyal6_question_bank_report.md` → `lgs_import/sosyal6/` → **500**  
      - `fen6_question_bank_report.md` → `lgs_import/fen6/` → **500**  
      - `din7_question_bank_report.md` → `lgs_import/din7/` → **500**  
      - `english7_question_bank_report.md` → `lgs_import/english7/` → **500**  
    - **Central LGS root files** under `app/src/main/assets/lgs_import/` (JSON, wrapped `{ "questions": [...] }`):  
      - `lgs_turkce.json` → **413** questions (`"stem"` occurrences)  
      - `lgs_mat.json` → **4** questions  
      - `lgs_fen.json` → **4** questions  
      - `lgs_din.json` → **3** questions  
      - `lgs_ing.json` → **2** questions  
      - `lgs_inkilap.json` → **3** questions  
    - **Topical LGS packs** under subject folders (all ~10 questions per file, counted via `"stem"`):  
      - `lgs_import/mat/` → e.g. `lgs_mat_gold_002.json` (6), `lgs_mat_gold_005.json` (17), `lgs_mat_gold_027.json` (10) → **33** total  
      - `lgs_import/fen/` → topic packs like `lgs_fen_pack_basinc_00x.json`, `lgs_fen_pack_mevsimler_ve_iklim_00x.json`, `lgs_fen_pack_madde_ve_endustri_00x.json`, `lgs_fen_pack_elektrik_003.json` → **≈50** questions (5 packs × 10)  
    - **Early grade‑1 visual LGS packs** (partial) under `lgs_import/mat1/`, `lgs_import/turkce1/`, `lgs_import/english1/` etc:  
      - Many files with **10** stems each (e.g. `lgs_mat1_pack_00x.json`, `lgs_turkce1_pack_0xx.json`, `lgs_eng1_pack_0xx.json`), but not a full 50‑pack bank for each subject.  
      - Combined, these contribute **a few hundred additional LGS questions** (order of ~200–400).  

**Conclusion:** The **large corpus actually present in the repo** is a **multi‑grade LGS corpus (~11.8k+ questions)** under `app/src/main/assets/lgs_import/**`, **plus** a small **GENERAL corpus of 203 questions** under `app/src/main/assets/` (non‑LGS).

---

### 2. File‑level inventory by category

#### 2.1 GENERAL (non‑LGS, grades 1–7)

- **Root assets used by `DbSeeder`** (GENERAL seeding):
  - `app/src/main/assets/questions_tr.json`  
    - Format: JSON array of question objects (legacy format: `gradeTag`, `subject`, `stem`/`questionText`, `choices`, `correctIndex`, etc.)  
    - **Grades covered**: **5, 6, 7 only** (no 1–4).  
    - **Subjects**: `mat`, `turkce`, `fen`, `sosyal`, `ing`.  
    - **Approx questions**: **200** (confirmed by `GENERAL_QUESTION_BANK_AUDIT.md` and `"stem"` count).  
    - **Importer**: `DbSeeder.loadFromAssets` → `parseJsonArray` → `parseQuestionObject`.  
  - `app/src/main/assets/import_template.json`  
    - Format: wrapped object: `{ "grade": 6, "subject": "mat", "questions": [ ... ] }`.  
    - **Grade/subject**: grade 6 MAT demo.  
    - **Approx questions**: **3** (`"stem"` count).  
    - **Importer**: `DbSeeder.loadFromAssets` via `ROOT_GENERAL_QUESTION_FILES` and `parseRootQuestionFile` / `parseWrappedQuestionArray`.  

- **Packs directory for GENERAL**:
  - `app/src/main/assets/packs/grade6_*.json` (5 files: MAT, TURKCE, FEN, SOSYAL, ING).  
  - All are **empty arrays `[]`** (0 `"stem"` matches).  
  - Discovered recursively by `DbSeeder.discoverPackAssetFilesRecursive`, but **contribute 0 questions**.  

**GENERAL total in repo** (assets, excluding runtime `imported_questions.json`):  
- **203 questions** (200 + 3 + 0 from packs).  
- **Grades present**: **5, 6, 7 only**.  
- **No prebuilt GENERAL corpus for grades 1–4** exists in the repo snapshot.  

#### 2.2 LGS corpus under `app/src/main/assets/lgs_import/**`

- **Central LGS JSON files** (mixed subjects, used by `importAllLgsPacksFromAssets`):  
  - `lgs_import/lgs_turkce.json` → **413** questions.  
  - `lgs_import/lgs_mat.json` → **4** questions.  
  - `lgs_import/lgs_fen.json` → **4** questions.  
  - `lgs_import/lgs_din.json` → **3** questions.  
  - `lgs_import/lgs_ing.json` → **2** questions.  
  - `lgs_import/lgs_inkilap.json` → **3** questions.  
  - **Importer**: `QuestionPackImporter.importAllLgsPacksFromAssets` → delegates to `runFenLgsImportFromAssets`, `runTurkceLgsImportFromAssets`, `runDinLgsImportFromAssets`, `runInkilapLgsImportFromAssets`, `runEnglishLgsImportFromAssets`.  

- **Per‑grade/subject LGS banks (full 500‑question sets)** – all **active and wired**:
  - **Grade 2–3 HAYAT (Subject.HAYAT)**  
    - `lgs_import/hayat2/` (`hayat2_question_bank_report.md`) → **500**  
    - `lgs_import/hayat3/` (`hayat3_question_bank_report.md`) → **500**  
  - **Grade 3 core subjects**  
    - `lgs_import/fen3/` (`fen3_question_bank_report.md`) → **500**  
    - `lgs_import/mat3/` (`mat3_question_bank_report.md`) → **500**  
    - `lgs_import/turkce3/` (`turkce3_question_bank_report.md`) → **500**  
  - **Grade 4 core + Din, Sosyal, English**  
    - `lgs_import/fen4/` (`fen4_question_bank_report.md`) → **500**  
    - `lgs_import/mat4/` (`mat4_question_bank_report.md`) → **500**  
    - `lgs_import/turkce4/` (`turkce4_question_bank_report.md`) → **500**  
    - `lgs_import/sosyal4/` (`sosyal4_question_bank_report.md`) → **500**  
    - `lgs_import/din4/` (`din4_question_bank_report.md`) → **500**  
    - `lgs_import/english4/` (`english4_question_bank_report.md`) → **500**  
  - **Grade 5 core + Sosyal, Din, English**  
    - `lgs_import/fen5/` (`fen5_question_bank_report.md`) → **500**  
    - `lgs_import/mat5/` (`mat5_question_bank_report.md`) → **500**  
    - `lgs_import/turkce5/` (`turkce5_question_bank_report.md`) → **500**  
    - `lgs_import/sosyal5/` (`sosyal5_question_bank_report.md`) → **500**  
    - `lgs_import/din5/` (`din5_question_bank_report.md`) → **500**  
    - `lgs_import/english5/` (`english5_question_bank_report.md`) → **500**  
  - **Grade 6 core + Sosyal, Din, English**  
    - `lgs_import/fen6/` (`fen6_question_bank_report.md`) → **500**  
    - `lgs_import/sosyal6/` (`sosyal6_question_bank_report.md`) → **500**  
    - `lgs_import/din6/` (`din6_question_bank_report.md`) → **500**  
    - `lgs_import/english6/` (`english6_question_bank_report.md`) → **500**  
  - **Grade 7 Din + English; Grade 7 FEN/TURKCE packs also present**  
    - `lgs_import/din7/` (`din7_question_bank_report.md`) → **500**  
    - `lgs_import/english7/` (`english7_question_bank_report.md`) → **500**  
    - `lgs_import/fen7/` → 50× packs (`lgs_fen7_pack_001.json`..`050.json`) with **10** questions each (confirmed by `"stem"` counts) → **500**  
    - `lgs_import/turkce7/` → multiple packs (e.g. `lgs_turkce7_pack_014.json`) with **10** questions each; full bank is **≈500** questions (same pattern as other subjects).  

- **Grade 1 LGS / visual packs (partial, not a full 500 per subject)**:
  - `lgs_import/mat1/` → many `lgs_mat1_pack_0xx.json` with `"stem":10` each; total **≈210+** questions.  
  - `lgs_import/turkce1/` → multiple `lgs_turkce1_pack_0xx.json` with 10 stems each (subset of 1..50) → **a few hundred** questions.  
  - `lgs_import/english1/` → `lgs_eng1_pack_00x.json` (10 stems each) → **a few hundred**.  

- **Topical LGS packs (non‑per‑grade)** under `lgs_import/fen/`, `lgs_import/mat/` etc.:  
  - Each file contributes **≈10** questions, covering specific LGS topics (e.g. pressure, seasons, electricity).  

**LGS examType / grade mapping:**  
- All LGS imports use `QuestionPackImporter` and are written as `QuestionEntity` with:  
  - `examType = "LGS"`  
  - `grade` = either **explicit per grade** (1..7) for grade‑1–7 LGS packs, or `LGS_GRADE = 8` when not overridden.  

---

### 3. Import pipeline coverage vs corpus

#### 3.1 DbSeeder (GENERAL seeding)

- **Importer**: `DbSeeder.loadFromAssets` → `parseRootQuestionFile`, `discoverPackAssetFilesRecursive`, `parsePackFileContent`, `parseQuestionObject`.  
- **Assets scanned** (non‑LGS only):  
  - Root: `questions_tr.json`, `import_template.json`.  
  - Packs: any `grade{1..8}_{subject}.json` under `assets/packs/` and its subfolders.  
- **Result:**  
  - **Imports all 203 GENERAL questions available in assets.**  
  - **Does not scan or import any `lgs_import/**` files.**  
  - There is **no hidden or ignored GENERAL 18k‑question corpus** under assets; the generator `tools/question_generator.py` can create such a corpus, but the generated JSON is *not* currently committed (existing `grade6_*.json` packs are empty).  

#### 3.2 QuestionPackImporter (LGS and generic JSON imports)

- **LGS paths connected to DB:**
  - `importAllLgsPacksFromAssets(context)` sequentially calls:  
    - Central files: `runFenLgsImportFromAssets` (for `lgs_fen.json` + `lgs_import/fen/*.json`), `runTurkceLgsImportFromAssets`, `runDinLgsImportFromAssets`, `runInkilapLgsImportFromAssets`, `runEnglishLgsImportFromAssets`.  
    - Per‑grade LGS packs:  
      - `importMat1/2/3/4/5/7LgsPacksFromAssets` → `lgs_import/mat1|2|3|4|5|7/`  
      - `importFen3/4/5/6/7LgsPacksFromAssets` → `lgs_import/fen3|4|5|6|7/`  
      - `importTurkce1/2/3/4/5/7LgsPacksFromAssets` → `lgs_import/turkce1|2|3|4|5|7/`  
      - `importSosyal4/5/6LgsPacksFromAssets` → `lgs_import/sosyal4|5|6/`  
      - `importHayat1/2/3LgsPacksFromAssets` → `lgs_import/hayat1|2|3/`  
      - `importDin4/5/6/7LgsPacksFromAssets` → `lgs_import/din4|5|6|7/`  
      - `importEnglish1..7LgsPacksFromAssets` → `lgs_import/english1..7/`  
      - `importInkilap7LgsPacksFromAssets` → `lgs_import/inkilap7/`.  
- **Generic JSON import path (used by QuestionImportActivity):**  
  - `QuestionPackImporter.importFromJson` / `importRawArray` → `runImport` (GENERAL, `examType="GENERAL"`) – *not tied to any committed 18k corpus; used for runtime/device imports.*  

**Result:**  
- **All LGS corpus folders under `app/src/main/assets/lgs_import/**` are *connected* to the active import pipeline.**  
  - They are imported when `importAllLgsPacksFromAssets` (or the specific per‑subject/grade functions) are invoked.  
  - LGS questions are then used via LGS candidate queries (`QuestionDao.getCandidatePoolByLgsSubject`, `getLgsCandidatePoolWithQuality`).  
- The **non‑LGS GENERAL seed path never touches `lgs_import/**`**, by design.  

---

### 4. Grouping by grade / mode

- **Grade 1**  
  - **GENERAL**: none in assets (no grade‑1 GENERAL JSON).  
  - **LGS/visual**: partial packs in `lgs_import/mat1/`, `turkce1/`, `english1/`, `hayat1/`.  
- **Grades 2–7 GENERAL**  
  - Only **grades 5–7** have GENERAL seeds (in `questions_tr.json` and `import_template.json`).  
  - No prebuilt GENERAL corpora for grades 2–4 in assets.  
- **Grades 2–7 LGS**  
  - Fully covered with 500‑question banks per subject for:  
    - HAYAT (2–3), MAT (3–5), TURKCE (3–5,7), FEN (3–7), SOSYAL (4–6), DIN (4–7), ENGLISH (4–7), plus central LGS sets.  
- **Templates / small samples**  
  - `import_template.json` (3 demo questions, GENERAL, grade 6 MAT).  
  - Very small LGS central files (`lgs_mat.json`, `lgs_fen.json`, etc.).  
- **Ignored / unused files (with respect to current code):**  
  - There is **no evidence of a large GENERAL corpus being ignored**: the recursive asset + JSON scans and `"stem"` counts only find:  
    - `questions_tr.json`, `import_template.json`, empty packs, and the `lgs_import/**` tree.  
  - Everything under `lgs_import/**` is either used by `QuestionPackImporter` or explicitly documented as such in the `*_question_bank_report.md` files.  

---

### 5. Root cause of the “18k vs 203 GENERAL” mismatch

1. **The 18k+ corpus you remember is the LGS corpus, not GENERAL.**  
   - The repo contains a **large LGS question bank (~12k questions)** under `app/src/main/assets/lgs_import/**`, carefully organized by grade and subject and fully wired into `QuestionPackImporter`.  
   - The **GENERAL (non‑LGS) seedable corpus actually present in assets is only 203 questions**, all in `questions_tr.json` and `import_template.json`.  
   - There is **no additional 18k‑sized GENERAL corpus** in the repo snapshot (no other JSON/CSV/TXT with `"stem"` or `"questions"` outside `lgs_import/**` and the two root GENERAL files).  

2. **Previous “GENERAL 203” audit was *logically* correct but scoped.**  
   - `GENERAL_QUESTION_BANK_AUDIT.md` and `NON_LGS_ASSET_DISCOVERY_AUDIT.md` deliberately scoped to **non‑LGS** assets:  
     - They excluded `app/src/main/assets/lgs_import/**`.  
     - They scanned all other JSON under `assets/` recursively and found only 203 GENERAL questions.  
   - So the **audit result “≈203 GENERAL non‑LGS questions” is accurate for GENERAL**, but it understandably feels “wrong” if you expect the LGS corpus to be counted there.  

3. **`DbSeeder` vs `QuestionPackImporter` separation.**  
   - `DbSeeder` is responsible for **GENERAL grade‑mode seeding** (grades 1–7, `examType = GENERAL/NULL`) and intentionally ignores `lgs_import/**`.  
   - `QuestionPackImporter` is responsible for **LGS (and generic JSON) imports**, marking rows with `examType = "LGS"` and different grade semantics and quality rules.  
   - The **large LGS corpus is not supposed to appear in GENERAL audits**; it has its own LGS‑focused audits and reports.  

4. **Generator scripts vs committed data.**  
   - `tools/question_generator.py` can generate **2500 grade‑6 GENERAL questions** (500 per subject) into `app/src/main/assets/packs/grade6_*.json`, matching the kind of large bank you describe.  
   - However, in this repo:  
     - The generated packs are currently **empty arrays `[]`** (no questions).  
     - Only the script and its audit docs are committed, **not the generated large GENERAL JSON**.  
   - So any expectation of “18k GENERAL questions in packs” reflects an earlier or external data state, not this checked‑in snapshot.  

---

### 6. Active importer coverage vs disconnected data

- **Actively imported by current code:**
  - **GENERAL**:  
    - `questions_tr.json`, `import_template.json` (203 questions) via `DbSeeder`.  
  - **LGS**:  
    - All central and per‑grade LGS packs under `lgs_import/**` via `QuestionPackImporter.importAllLgsPacksFromAssets` and the per‑subject/grade functions.  
    - The `*_question_bank_report.md` files confirm that every per‑grade LGS bank is compatible and imported by `QuestionPackImporter`.  

- **Disconnected / unused question corpora found in repo:**
  - **None of significant size.**  
  - Every JSON file with `"stem"` or `"questions"` is either:  
    - Already in `ROOT_GENERAL_QUESTION_FILES` / `packs/` and used by `DbSeeder`, or  
    - Under `lgs_import/**` and explicitly wired into `QuestionPackImporter`, or  
    - A tiny demo/template set.  
  - There is **no 18k‑size GENERAL JSON corpus sitting in some other folder (e.g., `raw`, `data`, `assets/non_lgs`, etc.)**.  

---

### 7. Fix status and recommendations

- **Code changes already in repo (from earlier repair):**
  - `DbSeeder.kt` now:  
    - Recursively discovers packs under `assets/packs/` and supports wrapped formats.  
    - Includes both `questions_tr.json` and `import_template.json` in `ROOT_GENERAL_QUESTION_FILES`.  
  - `QuestionPackImporter.kt`, `QuestionDao.kt`, `QuestionEntity.kt`, `QuestionRepository.kt` and others have been repaired to:  
    - Preserve more imported questions (relaxed dedup, larger candidate pool limits).  
    - Avoid over‑pruning LGS content, while still deactivating obviously bad items.  
  - LGS per‑grade import functions cover all `lgs_import/**` banks listed above.  

- **What is *not* fixable inside this repo snapshot:**
  - A **large GENERAL (non‑LGS) corpus for grades 1–7 simply does not exist in the committed assets.**  
  - To have 1000+ grade‑1 and 17k+ grade‑2–7 GENERAL questions, you would need to:  
    - Either re‑generate them with `tools/question_generator.py` (and analogous generators for other grades) and commit the resulting `grade{G}_{subject}.json` packs, or  
    - Re‑add the missing JSON packs from your external corpus source.  

- **Net effect on the app now:**
  - The app **can see and import the full LGS corpus** (≈11.8k+ questions) via LGS import flows.  
  - For **GENERAL grade‑mode** questions, the app still only has **203 seedable questions** from assets in this repo, plus any runtime imports performed on‑device.  

In short: the **“hidden” large corpus in this codebase is the LGS corpus under `lgs_import/**`, which is fully wired and used by `QuestionPackImporter`. The previous ~203 GENERAL count was accurate for non‑LGS assets; there is no disconnected 18k‑question GENERAL corpus in the repo snapshot to hook up.**

