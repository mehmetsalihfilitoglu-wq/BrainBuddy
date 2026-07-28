# ❄️ FROZEN — official IMAT bank (2011–2025)

The official IMAT question bank is **complete and read-only**. The files under
`content/imat/<subject>/*.json` are an exact mirror of the source exam PDFs.

## Do NOT edit, for any official question:

- ❌ question text
- ❌ options (their text **and** their order — Form-A papers keep the correct answer at option A)
- ❌ figures (`app/src/main/assets/imat/figures/*.png`)
- ❌ answer keys (`correct_answer`)

The database/asset must always match the official PDF exactly. A CI/pre-commit guard enforces this:

```
node scripts/verify_imat_frozen.js        # fails if any official file drifted
```

The frozen fingerprint lives in `OFFICIAL_MANIFEST.sha256`. Regenerate it (`--write`) **only** for a
deliberate, reviewed correction to official content (e.g. a proven transcription error).

## Where enrichment goes instead

Anything that is not official exam content — **Turkish explanations, AI explanations, difficulty,
tags, notes, editorial comments** — goes in **`content/imat/metadata/`**, keyed by question id, never
inside the official files. See `metadata/README.md`. The asset generator merges metadata in at build
time, so official content stays frozen while enrichment is free to grow.

## Randomization

Option order is **not** randomized in the database. If enabled, option shuffling happens only inside
the quiz engine at runtime (`QuizActivity.SHUFFLE_OPTIONS_AT_RUNTIME`), and stored answers/scoring
always use the original (official) option index.

## Scope

| Papers | Count | Source |
|--------|-------|--------|
| 2011–2020 | 10 exams | official Cambridge/MUR specimens + past papers |
| 2021, 2022 | 2 exams | official UCLES/Cambridge (Form-A) |
| 2023 | 1 exam | genuine official content (third-party reprint), key validated |
| 2024, 2025 | 2 exams | official MUR (Form-A) |

**940 usable questions, 181 figures.** Extending to 2026+ means adding new frozen files — never
editing these.
