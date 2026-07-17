# Asset discovery audit — non-LGS question files

## 1. Actual non-LGS JSON files in assets (recursive scan)

**Excluding `lgs_import/`** (LGS-only), the project contains:

| Folder / path       | File                  | Questions (approx) | Inferred grade/subject     |
|---------------------|-----------------------|--------------------|----------------------------|
| (root)              | questions_tr.json     | 200                | 5,6,7 × MAT,TURKCE,FEN,SOSYAL,ING |
| (root)              | import_template.json  | 3                  | 6 × mat (wrapped format)   |
| (root)              | junior_curriculum.json| 0 (curriculum data)| —                          |
| (root)              | lgs_turkce.json       | LGS                | LGS (excluded from GENERAL)|
| packs/              | grade6_fen.json       | 0 (empty array)    | 6 × fen                    |
| packs/              | grade6_ing.json       | 0                  | 6 × ing                     |
| packs/              | grade6_mat.json       | 0                  | 6 × mat                     |
| packs/              | grade6_sosyal.json    | 0                  | 6 × sosyal                  |
| packs/              | grade6_turkce.json    | 0                  | 6 × turkce                 |

**Non-LGS question-bearing files:** 2 (`questions_tr.json`, `import_template.json`).  
**Non-LGS pack files (discoverable):** 5 under `packs/` (all empty `[]`).  
**Total non-LGS JSON files that could be question sources:** 7 (root: 2 question + 1 LGS + 1 curriculum; packs: 5).

---

## 2. Previously discovered vs missed

**Previously discovered by DbSeeder:**

- **1 root file:** only `questions_tr.json` (hardcoded path).
- **5 pack files:** `discoverPackAssetFiles()` listed `assets.list("packs")` and matched `grade(2|3|4|5|6|7|8)_(mat|turkce|fen|sosyal|ing).json`. So `grade6_*.json` were discovered; **grade 1** was not in the regex, so any `grade1_*.json` would have been **ignored**.
- **Nested folders:** not scanned; only immediate children of `packs/` were listed.
- **Other root files:** `import_template.json` was **never loaded** (only `questions_tr.json` was).

**Missed / ignored:**

- `import_template.json` — root-level wrapped `{ "questions": [], "grade", "subject" }` format; not on the loaded list.
- Any `grade1_*.json` — regex did not include grade 1.
- Any pack under a subfolder of `packs/` (e.g. `packs/grade5/grade5_turkce.json`) — no recursive discovery.

---

## 3. Root cause of the discovery/import mismatch

1. **Single hardcoded root file**  
   Only `questions_tr.json` was loaded; other root-level question files (e.g. `import_template.json`) were not considered.

2. **Pack regex excluded grade 1**  
   Pattern was `grade(2|3|4|5|6|7|8)_...`, so `grade1_mat.json` (and any other grade-1 pack) would never be discovered.

3. **No recursive pack scan**  
   Only `assets.list("packs")` was used, so files in subfolders (e.g. `packs/grade5/grade5_turkce.json`) were never found.

4. **Wrapped format not supported**  
   Only root-level JSON arrays were parsed. Wrapped format `{ "questions": [], "grade", "subject" }` (as in `import_template.json`) was not loaded.

---

## 4. Files changed

- **`app/src/main/java/com/brainbuddy/app/db/DbSeeder.kt`**
  - Added `ROOT_GENERAL_QUESTION_FILES = listOf("questions_tr.json", "import_template.json")`.
  - Included **grade 1** in `PACK_FILE_REGEX`: `grade(1|2|3|4|5|6|7|8)_(mat|turkce|fen|sosyal|ing).json`.
  - Replaced single-root load with a loop over `ROOT_GENERAL_QUESTION_FILES` and `parseRootQuestionFile()` (supports both array and wrapped format).
  - Replaced `discoverPackAssetFiles()` with **recursive** `discoverPackAssetFilesRecursive()` using `collectPackPaths()` so any matching filename under `packs/` or its subdirs is discovered.
  - Pack content loading uses `parsePackFileContent()` so both root array and wrapped `{ "questions": [] }` are supported.
  - Added `parseWrappedQuestionArray()` to inject root `grade`/`subject` into each question object before `parseQuestionObject()`.

---

## 5. Confirmation

- **Root:** Both `questions_tr.json` and `import_template.json` are now loaded as GENERAL question sources; wrapped format is parsed and root `grade`/`subject` are applied to each item.
- **Packs:** All files matching `grade(1|2|3|4|5|6|7|8)_(mat|turkce|fen|sosyal|ing).json` under `packs/` or any subfolder are discovered and loaded; content can be either a JSON array or a wrapped object with `questions`.
- **LGS:** No change; `lgs_import/` is still used only by QuestionPackImporter, not by DbSeeder.

The importer now discovers and loads the existing non-LGS source files (root + packs, including grade 1 and nested pack paths) and supports the wrapped format used by `import_template.json`.
