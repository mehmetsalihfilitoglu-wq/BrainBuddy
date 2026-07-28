# Premium Solution Coverage — Report

**Date:** 2026-07-18 · Branch `seeding-final-fix` (not pushed). Status: **PRODUCTION IN PROGRESS** —
this report is updated with final numbers when each bank's batches are assembled and applied.

## Inventory (pre-production audit)

Production-eligible = every question the seeder ships active+servable for a production exam. This is
**four** banks — the three Daily-Challenge exams PLUS the EDUmio IMAT-original practice pool
(examType `EDUMIO_ORIGINAL`, served in the quiz/practice flow and unioned with official IMAT in the
IMAT-format mixed pool). **The original inventory wrongly excluded the 1010 EDUmio-original questions;
this is corrected below.** The true IMAT production pool is 940 official + 1010 original = 1950.

| Bank | Eligible | Had usable explanation (≥80ch) | Missing/short | Figure questions | Figures on disk |
|---|---|---|---|---|---|
| IMAT (frozen official bank) | 940 | 0 | 940 | 181 | 181 ✓ |
| **EDUmio IMAT-original** | **1010** | **1010** | **0** | **0** | n/a |
| TIL-I | 1107 | 466 | 641 | 272 | 272 ✓ |
| CEnT-S | 1100 | 726 | 374 | 193 | 193 ✓ |
| **Total** | **4157** | | | **646** | all present |

Notes:
- The IMAT official bank is PDF-exact and frozen (no explanation field); solutions are a pure overlay.
- Existing TIL-I/CEnT-S/EDUmio-original explanations were treated as raw material only — every question
  is independently re-verified (answer solved blind), populated fields never assumed correct.
- The EDUmio-original bank already carries verified `explanation` + `optionAnalysis` for all 1010
  records (prior original-IP production). Per the "don't regenerate strong work" rule, its solutions are
  produced by a **structure + independent-blind-verify** pipeline: a structurer restructures the
  existing explanation into the solution schema; an independent verifier solves each question blind
  (never shown the explanation or the stored answer) to confirm the answer and audit quality.

## Production pipeline (per bank)

Batches: 25 text / 12 figure questions → blind author (independent solve, figure images actually read)
→ independent verifier (re-solves before reading the authored solution) → mechanical assembler gate
(`author == storedKey == verifier`, `qualityPass`, `confidence ≥ 0.95`, forbidden-token scan) →
bounded rework rounds → transactional apply. Items where both independent solvers agree on a DIFFERENT
answer than the stored key are **BLOCKED and documented** — never silently changed, never shipped.

Batch counts: IMAT 47 (35 text + 12 figure... 759 text / 181 figure items), TIL-I 57 (835/272),
CEnT-S 54 (907/193) — 158 authoring batches + 158 verification batches total.

## Final coverage (assembled + applied)

| Bank | Eligible | Solutions shipped | Blocked (no solution) | Coverage |
|---|---|---|---|---|
| IMAT (official) | 940 | 912 | 28 | 97.0% |
| EDUmio IMAT-original | 1010 | 1010 | 0 | 100% |
| TIL-I | 1107 | 1102 | 5 | 99.5% |
| CEnT-S | 1100 | 1100 | 0 | 100% |
| **Total** | **4157** | **4124** | **33** | **99.21%** |

(IMAT as a whole = official 912/940 + original 1010/1010 = **1922/1950**.)

Every shipped solution: `correctOption == bank answerIndex` (0 mismatches), 0 internal-field leaks, and
every shipped figure question carries a figure explanation (0 gaps). Verified by `SolutionCoverageTest`
and a direct asset scan.

### The 33 blocked (ship no solution; question still works — answer key untouched)
- **16 answer-integrity blocks** — independent solving disagreed with the stored key and adjudication
  sided against / was unsure (13 IMAT adjudicated conflicts + 2 IMAT round-3 verifier disagreements + 1
  TIL-I). Never re-keyed; flagged for human review.
- **17 quality blocks** — items that could not reach the acceptance bar after **three** independent
  author+verify rounds (12 IMAT + 4 TIL-I + 1 IMAT missing-verdict). Correct answer known, but the
  student-facing solution wasn't confidently clean enough to ship.

Full per-id lists: `content/{imat,til_i}/solutions_blocked_detail.json` (+ `solutions_blocked.json`
manifests shipped beside each bank so the coverage test can prove the gaps are exactly the documented set).

## Production effort
158 first-pass authoring batches + 158 verification batches, then per-bank rework rounds
(adjudication + regeneration + independent re-verification) — IMAT went through 3 rework rounds,
TIL-I 2, CEnT-S 1. ~500 subagent runs total. Interrupted twice by session limits and resumed from disk
with zero lost work.
