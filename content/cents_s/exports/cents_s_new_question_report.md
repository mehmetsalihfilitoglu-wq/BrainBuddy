# CEnT-S — New Original Question Report

**Bank:** `content/cents_s/original/cents_s_original_bank.json` (v0.2-original). All records `sourceType: new_original`, `examProfile: CENTS_S`, ids `cents_s_orig_*`. The CEnT-S **official** bank is intentionally empty (no genuine CISIA source among uploads). Nothing committed.

## Totals: 202 approved original questions
| Section | Count | Figures |
|---|---|---|
| Mathematics | 76 | (geometry/graph diagrams) |
| Physics | 46 | (free-body/circuit/graph diagrams) |
| Reasoning on Texts & Data | 29 | — |
| Chemistry | 26 | — |
| Biology | 25 | — |
| **Total** | **202** | **60 figures** |

- Difficulty: easy 89 / medium 113.
- 145 are shared with TIL-I (separate TIL record + separate asset, linked by `crossExamRelationId`).
- Biology & Chemistry are CEnT-S-only (absent from TIL-I) and were built from scratch here.

## Method
Identical gated pipeline to TIL-I (author → independent solve → adversarial verify → 3-way agreement → single-correct/distractors/**CEnT-S-syllabus**-verified/no-source-resemblance → dedup → figure rasterize+verify), confidence ≥0.90 (≥0.95 reading/figure). Rejects → `content/cents_s/rejected/cents_s_rejected.json` (23 rejects).

## Quality gates (Phase J): all PASS
IDs unique; all examProfile=CENTS_S; no representation/CS; every record one A-E answer + validation + confidence≥0.90; every imageAsset in `cents_s/assets/figures/`; no asset shared with TIL-I.

## Caveats / recommendations
- The CEnT-S **spec itself is third-party-derived** (EuCrack book), not CISIA-verified — re-validate against an official CISIA source when available; scoring remains unverified.
- Biology/Chemistry facts were independently verified per question, but a domain-expert spot review before high-stakes use is prudent.
- Add more **hard** items and grow Reasoning-on-Texts-&-Data (data-table/passage items are the hardest to pass the 0.95 bar).
