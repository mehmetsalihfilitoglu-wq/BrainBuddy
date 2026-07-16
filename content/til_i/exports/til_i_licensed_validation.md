# TIL-I — Licensed-Source Validation

**Bank:** `content/til_i/licensed/til_i_licensed_bank.json` — **311** questions. `sourceType: licensed_or_user_provided_source`, ids `til_i_lic_*`. NOT official; the frozen 479-Q TIL-I official bank is untouched. Nothing committed.

## Sources (all authorized)
Only the questions whose competency is **genuinely shared** with the TIL-I syllabus (Mathematics, Physics, Logic; + one self-contained Reading item):
1. `CEnT_S_Exam_Book` (EuCrack) — math/physics/logic → ~256.
2. **CISIA TOLC-I sample** (engineering) — math/physics/logic → ~54.
3. **CISIA TOLC-E samples** — logic/math → ~10 (paper 2 duplicated paper 1).
Biology/Chemistry/Reasoning are CEnT-only, excluded here. Every record retains `sourceFile` + `sourcePage`. Final section counts: math 163, physics 93, logic 54, reading 1.

## Totals: 256 by section
| Section | Count |
|---|---|
| Mathematics | 135 |
| Physics | 85 |
| Logic | 36 |

- Difficulty: easy 174 / medium 78 / hard 4.
- All 256 are shared with CEnT-S → each has a **separate** CEnT record (`cents_s_lic_*`) and separate metadata, linked by `licensed_shared_*`. Separate ID, separate bank, separate validation; only `crossExamRelationId` is common.
- No figures required (all text/formula).

## Method
Identical gated pipeline to the CEnT-S licensed extraction (extract → independent solve → second independent verify → three-signal agreement incl. printed answer → single-correct + complete + **TIL-I-syllabus** (no chem/bio, no calculus) + no source-defect → dedup vs official/original/hard/licensed). Confidence ≥0.90.

## Isolation & QC (all gates PASS)
No chemistry/biology/reasoning in this bank; IDs unique + `til_i_lic_*` namespace; sourceType=licensed (never `official`); every record single A–E answer + validation flags + confidence≥0.90; sourceFile+page present; shared questions use separate assets/records. Dedup caught 3 conflicts against the TIL-I official bank and 2 against the original bank (excluded).

## Note
These are authorized third-party prep questions, not CISIA/Politecnico official material — hence the licensed bank, kept strictly separate from `til_i_official_bank.json`.
