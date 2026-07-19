# Premium Solution Coverage — Report

**Date:** 2026-07-18 · Branch `seeding-final-fix` (not pushed). Status: **PRODUCTION IN PROGRESS** —
this report is updated with final numbers when each bank's batches are assembled and applied.

## Inventory (pre-production audit)

Production-eligible = every question the seeder ships active+servable for the three Daily-Challenge
exams (the seeder force-repairs `isActive`/`unservableReason`, so eligible == shipped).

| Bank | Eligible | Had usable explanation (≥80ch) | Short (<80ch) | Missing entirely | Figure questions | Figures on disk |
|---|---|---|---|---|---|---|
| IMAT (frozen official bank) | 940 | 0 | 0 | **940** | 181 | 181 ✓ |
| TIL-I | 1107 | 466 | 175 | **466** | 272 | 272 ✓ |
| CEnT-S | 1100 | 726 | 225 | **149** | 193 | 193 ✓ |
| **Total** | **3147** | | | | **646** | all present |

Notes:
- The IMAT bank is PDF-exact and frozen; it has no explanation field at all. Its solutions are a pure
  overlay (`imat/solutions.json`) — the bank file itself is never touched.
- Existing TIL-I/CEnT-S explanations were treated as **unverified raw material only**. Every one of the
  3147 questions gets a NEW structured solution authored blind (no stored answer shown to the author)
  and independently verified — populated fields were never assumed correct.
- EDUmio-Original (1010 questions) is an isolated pool outside the Daily-Challenge blueprint and outside
  this directive's bank list (IMAT / TIL-I / CEnT-S); it already carries `explanation` +
  `optionAnalysis` for all 1010 records.

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
| IMAT | 940 | 912 | 28 | 97.0% |
| TIL-I | 1107 | 1102 | 5 | 99.5% |
| CEnT-S | 1100 | 1100 | 0 | 100% |
| **Total** | **3147** | **3114** | **33** | **98.95%** |

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
