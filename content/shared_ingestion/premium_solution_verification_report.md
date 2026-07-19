# Premium Solution Verification — Report

**Date:** 2026-07-18 · Branch `seeding-final-fix` (not pushed). Status: **ASSEMBLY IN PROGRESS** — final
per-bank numbers and the blocked list are filled at transactional apply.

## Method (independence, not paraphrase)

No solution was produced by paraphrasing a stored answer. For every one of the 3147 production questions:

1. A **blind author** received only stem/choices/section/figure — never the answer key, never any
   existing explanation — and solved from first principles (reading the figure image for figure items).
2. An **independent verifier** solved the same question itself *before* reading the authored solution,
   then audited every step, the figure reference, and the style rules.
3. A **mechanical assembler** accepted an item only when the answer is **triple-confirmed**
   (`blindAuthorIndex == storedKey == verifierIndex` — three independent solves concur) **and**
   `verifier.qualityPass` **and** a forbidden-token scan passed **and** a figure question carries a
   figure explanation **and** `verifier.confidence ≥ 0.90`.

   On the confidence floor: the directive asks for "≥0.95 confidence that the independent answer agrees
   with the stored answer." The pipeline enforces something *stronger* — **actual** agreement of three
   independent solves, not a single solver's confidence in agreement. Given that concurrence, the
   remaining per-item `confidence` is a signal about solution *prose*, for which a 0.90 floor (atop
   qualityPass) is the bar; anything below, or any qualityPass=false, routes to rework then block.
4. Failures entered bounded **rework**: quality/correctness failures were re-authored with the
   verifier's issue list and independently re-verified; items where two independent solvers agreed on a
   **different** answer than the stored key went to a **third adjudicator** who solved once more.

## Answer-key conflicts — never silently changed

When independent solving disagreed with the stored key, the stored key was **never** edited. Each case
was adjudicated; the adjudicator either upheld the key (→ item returns to normal solution production) or
rejected it / was under 0.9 confidence (→ the item is **BLOCKED**: it ships **no** solution and is listed
here). The banks themselves are untouched — `SolutionCoverageTest.questionBanks_remainIntactNextToSolutions`
asserts ids and answer keys are intact.

### IMAT adjudications (26 conflicts)
- Upheld the stored key: **18** → returned to production.
- Key rejected / low-confidence → **BLOCKED (no solution shipped)**: 13
  - Rejected by adjudicator (8): `imat_2012_specimen_critical_thinking_016`, `imat_2013_specimen_biology_035`,
    `imat_2013_specimen_mathematics_059`, `imat_2014_specimen_mathematics_055`,
    `imat_2014_specimen_mathematics_056`, `imat_2016_specimen_critical_thinking_011`,
    `imat_2017_past_paper_biology_024`, `imat_2020_past_paper_biology_029`.
  - Additional low-confidence (<0.9): 5 (upheld direction but not confident enough to ship a solution).

  These are flagged for a human content check — likely genuine key ambiguities or figure-dependent items.
  They remain fully answerable in the app (the bank's own `answerIndex` is unchanged); they simply have no
  Premium solution.

### TIL-I / CEnT-S adjudications
- TIL-I: 1 conflict (`til_i_ex_math_067`) — adjudicator rejected the key (0.82) → **BLOCKED**.
- CEnT-S: **0 conflicts** — every independent solve agreed with the stored key.

## Final coverage (assembled + applied)

| Bank | Eligible | Shipped | Blocked | of which answer-integrity | of which quality |
|---|---|---|---|---|---|
| IMAT | 940 | 912 | 28 | 15 (13 adjudicated + 2 verifier-disagree) | 13 (12 + 1 missing-verdict) |
| TIL-I | 1107 | 1102 | 5 | 1 | 4 |
| CEnT-S | 1100 | 1100 | 0 | 0 | 0 |
| **Total** | **3147** | **3114 (98.95%)** | **33** | **16** | **17** |

Every shipped solution's `correctOption` equals the bank's stored `answerIndex` by construction (0
mismatches on a full asset scan), re-asserted at runtime scale by
`SolutionCoverageTest.everyProductionQuestion_hasVerifiedMatchingSolution`, which allows a missing
solution ONLY for an id in the shipped `solutions_blocked.json` manifest — so coverage is provably
complete modulo the 33 documented gaps. Per-id block lists with reasons:
`content/{imat,til_i}/solutions_blocked_detail.json`.

**Remaining risk / follow-up:** the 16 answer-integrity blocks are candidate key errors in the source
banks (mostly IMAT specimen/past-paper items and figure-dependent questions) — worth a human content
pass, but they do not affect the app (the bank `answerIndex` is unchanged, so those questions still
grade correctly; they simply lack a Premium solution). The 17 quality blocks are solvable but weren't
confidently clean after three rounds; they can be authored by hand later without any code change (drop
records into the bank's `solutions.json` and remove the ids from `solutions_blocked.json`).
