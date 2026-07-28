# CEnT-S — Licensed-Source Validation

**Bank:** `content/cents_s/licensed/cents_s_licensed_bank.json` — **547** questions. `sourceType: licensed_or_user_provided_source`, ids `cents_s_lic_*`. NOT official (kept out of `cents_s_official_bank.json`, which stays empty). Nothing committed.

## Sources (all authorized)
1. `CEnT_S_Exam_Book` (EuCrack CEnT-S 2025 Preparation Book) — 202 pp, all 39 chunks → ~492 records.
2. **CISIA TOLC-I sample** — the science/math/physics that fit CEnT-S → +chemistry/physics/math/reasoning (TOLC = different exam, so licensed-not-official).
3. **CISIA TOLC-E samples ×2** — logic/math → +reasoning/math (paper 2 fully duplicated paper 1).
Every record retains `sourceFile` + `sourcePage`. (Section counts below reflect the CEnT-S book; TOLC additions folded into the same sections — see `licensed_source_processing_log.md`.)

## Totals: 492 by section
| Section | Count |
|---|---|
| Chemistry | 118 |
| Biology | 109 |
| Mathematics | 135 (shared w/ TIL) |
| Physics | 85 (shared w/ TIL) |
| Reasoning on Texts & Data | 45 |

- Difficulty: easy 397 / medium 91 / hard 4 (the source is predominantly single-step recall).
- **255** of these are shared with TIL-I (Math/Physics/Logic) → each has a separate TIL record (`til_i_lic_*`) and separate metadata, linked by `licensed_shared_*` in `content/shared_ingestion/licensed_cross_exam_map.json`. Biology/Chemistry/Reasoning are CEnT-only.
- 141 records are legitimate 4-option (A–D) items (tag `four_option`); the rest 5-option.
- Answer provenance: 597 `answer_source_confirmed` (book's printed answer matched BOTH independent solves), 151 `answer_independent` (no printed answer → derived at ≥0.95). No figures were required (all text/formula items).

## Method (every record)
Vision extraction (exact wording, source page, printed answer) → **independent solve (pass 1)** → **independent adversarial verify (pass 2)** → three-signal gate: the two independent solves must agree **and** the printed answer must concur (else rejected) → single-correct + complete + CEnT-S-syllabus + no unresolved source-defect + confidence ≥0.90 (≥0.95 reading/no-printed-answer) → dedup vs official + original + hard + licensed.

## Rejections (quality gate worked)
190 total (TIL+CEnT routed). Independent verification caught **37 source-answer conflicts** (book's answer wrong) and **93 source defects** (e.g. multiple correct options, answer not among choices, pentagon/hexagon confusion) — all rejected, not included. Detail: `content/shared_ingestion/licensed_rejection_report.md`, `content/cents_s/rejected/cents_s_licensed_rejected.json`.

## QC (all 15 gates PASS)
IDs unique; examProfile/namespace correct; no representation/CS; every record single A–E answer + validation flags + confidence≥0.90; sourceFile+page present; licensed relations link two distinct records with `licensed_shared_*`; no shared assets; image paths valid.

## Caveats
- Difficulty skews easy (source nature). For a harder pool, favor the original hard banks.
- The EuCrack book is a third-party prep product (authorized, not CISIA-official); its high defect rate (≈17% of readable items had wrong/defective answers) is why every item was independently re-solved.
