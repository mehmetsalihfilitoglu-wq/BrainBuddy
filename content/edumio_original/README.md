# EDUmio Original Question Bank

Original, IMAT-level questions authored editorially by EDUmio. This is **primary intellectual
property** and production content — not synthetic filler.

## Hard boundary vs. the official bank

- The official IMAT bank lives in `content/imat/` and is **frozen** (see `content/imat/FROZEN.md`).
- These original questions live here (`content/edumio/`) and **never mix into** the official
  files. The official papers are our editorial *reference* only.
- Never copy, paraphrase, number-swap, or near-duplicate an official question or figure. Every item
  must test a genuinely new situation. See `STYLE_GUIDE.md`.

## Layout

```
content/edumio/
  README.md            ← this file (schema + conventions)
  STYLE_GUIDE.md       ← editorial reference distilled from the official papers
  _calibration/        ← format/quality calibration slice (sign-off before scaling)
  biology/  chemistry/  physics/  mathematics/  critical_thinking/
                       ← accepted questions, JSON arrays, per subject
```

## ID scheme

`mio_<subj>_<NNNN>` with subj ∈ {bio, chem, phys, math, ct}, e.g. `mio_bio_0001`. IDs are unique and
never reused, even if a question is later retired.

## Question schema (one JSON object per question)

| field | type | notes |
|-------|------|-------|
| `id` | string | `mio_<subj>_<NNNN>` |
| `origin` | string | always `"edumio_original"` |
| `exam_style` | string | `"IMAT"` |
| `subject` | string | biology \| chemistry \| physics \| mathematics \| critical_thinking |
| `topic` | string | broad topic |
| `subtopic` | string | specific concept tested |
| `difficulty` | string | `medium` \| `hard` \| `very_hard` |
| `estimated_time_seconds` | int | realistic solving time for a strong candidate |
| `tags` | string[] | concepts / skills / trap type |
| `learning_objective` | string | one sentence: what the question teaches/tests |
| `question` | string | verbatim stem (self-contained; no external figure unless an original figure is supplied) |
| `choices` | object | `{A,B,C,D,E}` — always five options |
| `correct_answer` | string | one of A–E |
| `explanation` | string | teaches the concept and justifies the correct answer |
| `option_analysis` | object | `{A..E}` — one line each; marks the correct option and gives the misconception behind every wrong one |
| `author` | string | `"edumio_editorial"` |
| `created` | string | ISO date |
| `review_status` | string | `accepted` (only accepted items are committed) |

## Randomization & metadata

Same architecture as the official bank: option order is stored in a fixed A–E order and randomized
only at runtime in the quiz engine. Difficulty/tags/learning objective live inline here because they
are authored *with* the question (unlike the official bank, where they are a separate overlay).

## Pipeline (to be wired when the bank is populated)

A generator will emit `app/src/main/assets/edumio/questions.json` (kept separate from the official
IMAT asset), served through its own seeding path so official and original pools stay isolated and can
be selected independently in the app.
