# TIL-I — New Original Question Report

**Bank:** `content/til_i/original/til_i_original_bank.json` (v0.2-original). All records `sourceType: new_original`, `examProfile: TIL_I`, ids `til_i_orig_*`. Nothing committed.

## Totals: 199 approved original questions
| Section | Count | Figures |
|---|---|---|
| Mathematics | 76 | (geometry/graph diagrams) |
| Physics | 46 | (free-body/circuit/graph diagrams) |
| Logic | 33 | — |
| Computer science | 22 | — |
| Representation | 11 | 11 (all figure-based) |
| Reading | 11 | — |
| **Total** | **199** | **70 figures** |

- Difficulty: easy 87 / medium 112 (hard-authored items were mostly re-rated medium by the independent verifier or rejected; see recommendations).
- 145 of these are shared with CEnT-S (separate CEnT record + separate asset, linked by `crossExamRelationId`).

## Method (every record)
Authored original (new wording/values/context/distractors — never reproduced from any source) → **independent first-principles solve** → **adversarial second-pass verify** → three-way answer agreement → single-correct + valid-distractors + TIL-I-syllabus-verified + no-source-resemblance → number-aware dedup vs the 479-Q TIL-I official bank + this bank + within-batch → figures rasterized from original SVG (node-canvas) and verified for consistency. Confidence gate ≥0.90 (≥0.95 reading/figure). Failures rejected to `content/til_i/rejected/til_i_rejected.json` (94 rejects).

## Quality gates (Phase J): all PASS
IDs unique; all examProfile=TIL_I; no chemistry/biology; every record one A-E answer + validation flags + confidence≥0.90; every imageAsset path exists in `til_i/assets/figures/`; no asset shared with CEnT-S.

## Recommendations
- Add more genuinely **hard** items (current skew easy/medium — the verifier is conservative on difficulty).
- Grow Representation and Reading further (hardest to verify; currently 11 each).
