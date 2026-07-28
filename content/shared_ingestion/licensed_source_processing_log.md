# Licensed-Source Extraction — Processing Log

Isolated from the original-authored production (separate IDs `*_lic_*`, sourceType `licensed_or_user_provided_source`, separate banks/maps/reports/counts). Nothing committed.

## Pipeline
Per authorized source: **render → vision-extract (exact wording, source page, shown answer, figure/passage) → independent solve (pass 1) → adversarial verify (pass 2, independent answer) → three-signal gate (two solves agree AND source answer concurs) → single-correct + complete + syllabus + no-source-defect + confidence≥0.90 (0.95 reading/figure) → dedup vs official+original+hard+licensed → classify TIL_I_ONLY / CENTS_S_ONLY / BOTH / REJECTED → separate records + separate assets for BOTH**. Source wording preserved verbatim; a source defect that cannot be resolved with certainty ⇒ reject (defect logged).

## Sources
| Source | Availability | Status |
|---|---|---|
| CEnT_S_Exam_Book (EuCrack, authorized) — 202 pp, ~600 CEnT-S MCQs with Answer+Explanation | rendered `figtools/cents_book_print` | PRIMARY — extracting |
| TIL-I photo sources (LOGIC/PHYSICS/MATH/Representation/admission photos) | already extracted → frozen TIL-I official bank (v1.5, 479 Q) | not re-extracted (would duplicate) |
| TOLC / SAT / CENTS Deneme 3·5 / TTPU / italostudy / WhatsApp / Domande-TIL / merged | **no longer on Desktop** | pending re-upload to extract |

## Runs (CEnT-S Exam Book)
- **Batch 1** (pages 9–28, math section): 67 extracted → **44 approved** (all universal math → BOTH exams = 44 TIL + 44 CEnT records, 44 `licensed_shared_*` pairs). Rejected 23: source-answer-conflict 10, source-defect 9, not-single-correct 9, verify-reject 9, ambiguity 6, low-confidence 6, solve/verify-disagree 5, duplicate 5 (2 vs official, 3 vs licensed), other-correct 1. (Reason counts overlap.) The 10 source-answer conflicts = book's printed answer disagreed with both independent solves → correctly rejected.
- **Batches 2–10 complete — CEnT-S Exam Book fully processed (all 202 pp / 39 chunks).**

## FINAL (CEnT-S Exam Book)
- **748 licensed records**: TIL-I **256** (math 135, logic 36, physics 85) + CEnT-S **492** (math 135, chemistry 118, biology 109, physics 85, reasoning 45). **255** `licensed_shared_*` pairs (math/physics/logic shared → 2 records each; bio/chem/reasoning CEnT-only). 141 legitimate 4-option items. 0 figures (all text). Answer provenance: 597 source-confirmed, 151 independent (≥0.95).
- **190 rejected** (dedup 73 [68 internal, 3 vs official, 2 vs original]; **source_defect 93**; **source_answer_conflict 37**; plus ambiguity/not-single-correct/low-confidence). ≈17% of readable items had a wrong or defective printed answer — all caught by dual independent solving and excluded.
- **Licensed QC: 15/15 gates PASS** (isolation, IDs, namespaces, sourceType, syllabus separation, single-answer, validation flags, confidence, traceability, cross-map, assets, images).
- Reports: `til_i_licensed_validation.md`, `cents_s_licensed_validation.md`, `licensed_rejection_report.md`, `licensed_cross_exam_map.json`.
- **Remaining authorized PDFs not on disk** (TOLC/SAT/CENTS-Deneme/TTPU/italostudy/WhatsApp/merged) — flagged for re-upload; cannot extract what isn't present.

## Wave-2 originals (relaunched fresh after the hung run)
Applied to original+hard banks: TIL original 214 (+3 hard), CEnT original 219 (+5 hard). Hard pools now seeded (Bayes tree, combinatorics, epistasis genetics, rough-incline round-trip, min-launch-speed projectile, cube body-diagonal section) with verified SVG figures.

## Additional authorized sources (re-appeared on disk: TOLC KIT zips)
The other original PDFs stayed absent, but two **TOLC KIT zips** appeared. Contents: mostly **published third-party textbooks** (Cambridge, Cottrell, Hodder, IB, SAT — prose reference, NOT MCQ banks) + the **TIL-I mock (already in the TIL-I official bank)**. The genuinely new MCQ sources = CISIA **TOLC-I** sample (50 pp) + **TOLC-E** samples ×2 (39/41 pp). TOLC ≠ TIL-I/CEnT-S, so `licensed` (authorized, official-to-TOLC not TIL/CEnT); per-question syllabus-fit only; no inline answers → independent-solve at ≥0.95.
- **TOLC-I** (Math/Logic/Sciences/Reading): +~55 licensed (math/logic/physics shared; chemistry CEnT-only; passage-reading rejected when the passage wasn't on the item's page; 1 deduped vs TIL official — CISIA reuse).
- **TOLC-E** paper 1 (Logic/Reading/Math): +~20 licensed (logic + math, shared). **TOLC-E paper 2 = duplicate of paper 1** → 0 net (all deduped).

## FINAL LICENSED TOTALS: **858** (TIL 311 · CEnT 547)
- TIL licensed by section: math 163, physics 93, logic 54, reading 1.
- CEnT licensed by section: math 163, biology 109, chemistry 119, physics 93, reasoning 63.
- **309** `licensed_shared_*` pairs. All from authorized sources (CEnT-S book + TOLC papers). Master QC across all 8 banks: **22/22 PASS**.

## Master inventory (all banks, nothing committed): **1778**
TIL: official 479 · original 214 · hard 3 · licensed 311 (=1007). CEnT: official 0 · original 219 · hard 5 · licensed 547 (=771). 194 figures (all valid). IMAT + app code untouched.

## Remaining / blocked
The other originally-uploaded exam PDFs (Domande-TIL, Deneme mocks, WhatsApp, italostudy, "merged", Formulario, Soluzione) are still not on disk → re-upload needed to extract them. The TOLC textbooks are prose reference (not extractable MCQ banks).
