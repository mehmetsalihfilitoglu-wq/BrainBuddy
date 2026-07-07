# IMAT auxiliary metadata (editable enrichment layer)

The official question files under `content/imat/<subject>/` are **frozen** (see `../FROZEN.md`).
Anything that is NOT official exam content — difficulty ratings, tags, Turkish/AI explanations,
editorial notes — lives here instead, in **separate files keyed by question id**. This keeps the
official bank an exact mirror of the source PDFs while still letting us enrich it freely.

## Files

| File | Shape | Meaning |
|------|-------|---------|
| `difficulty.json` | `{ "<id>": 1 \| 2 \| 3 }` | 1 = easy, 2 = medium, 3 = hard. Missing → medium (1) default. |
| `tags.json` | `{ "<id>": ["osmosis", "trap"] }` | free-form topic/skill tags |
| `explanations_tr.json` | `{ "<id>": "Türkçe çözüm…" }` | worked solution / explanation (Turkish) |
| `notes.json` | `{ "<id>": "editorial note" }` | internal editorial comments |

`<id>` is the question's `id` from the official file, e.g. `imat_2021_past_paper_biology_031`.
Add `explanations_en.json`, `ai_explanations.json`, etc. the same way and wire them in the generator.

## How it reaches the app

`scripts/build_imat_asset.js` merges these files (by id) into the bundled asset
`app/src/main/assets/imat/imat_questions.json`. Re-run it after editing metadata:

```
node scripts/build_imat_asset.js
```

then bump `CURRENT_IMAT_SEED_VERSION` in `DbSeeder.kt` so devices re-seed.

## Rules

- **Never** put explanations/difficulty/tags/notes inside the official `<subject>/*.json` files.
- Editing metadata never changes official question text, options, figures, or answers.
- A question id that no longer exists is simply ignored by the generator (safe).
