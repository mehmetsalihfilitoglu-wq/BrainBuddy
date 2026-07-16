# TIL-I + CEnT-S Ingestion — Processing Log

Chronological log of the combined ingestion/production phase. Nothing is committed.

## Phase A — Source audit
- Rendered sample pages of all 13 uploaded files (`pdf-to-img`, actual page counts differ from OS metadata).
- Personally inspected the two CEnT-S "Exam Book" PDFs (202 pp each) and "Domande TIL-I".
- Ran a 10-agent audit workflow over the remaining files.
- Output: `til_i_cents_s_source_audit.md`.

## Phase B — Verified specs
- `content/til_i/exports/til_i_verified_exam_spec.md` — preserved 42-Q Quick-Guide structure.
- `content/cents_s/exports/cents_s_verified_exam_spec.md` — 55-Q / 110-min structure **as stated by the third-party EuCrack book** (Math 15, Reasoning-on-Texts-&-Data 15, Biology 10, Chemistry 10, Physics 5); scoring `not_verified_from_current_sources`.
- `content/cents_s/exports/cents_s_taxonomy.json` — provisional (third-party-derived).

## Architecture
- Isolated roots created: `content/til_i/{official,original,rejected,assets/figures,exports}`, `content/cents_s/{...}`, `content/shared_ingestion/`.
- Empty banks initialised: `til_i_original_bank.json`, `cents_s_official_bank.json` (reserved, empty), `cents_s_original_bank.json`.
- Shared schema + `til_i_cents_s_cross_exam_map.json` created.
- Existing `til_i_official_bank.json` (479 Q, v1.5) untouched; IMAT untouched.

## Key early finding (governs the rest)
The CEnT-S-specific sources are **third-party, copyrighted prep books/mocks** — usable only as inspiration for genuinely NEW original questions, never reproduced verbatim and never labelled official. Therefore CEnT-S production targets the ORIGINAL bank; the CEnT-S OFFICIAL bank stays empty pending a real CISIA source.

## Phase A outcome (all 13 files processed)
Every file → `reference_only`, `inspiration_only_no_repro`, or `reject`. **0 questions extractable to any official/production bank** (copyright + not-official + no-answer-keys + wrong-exam + defective transcription). Full table in `til_i_cents_s_source_audit.md`; file-level dispositions in `til_i_cents_s_rejection_report.md`.

## Fork reached (Phases C–I)
The "extract candidates" interpretation is **not possible** (no reproducible official source). Rule-compliant continuation = **original authoring** inspired by the verified syllabi. Scope/priority of that (large) effort is a strategic decision → surfaced to the user before committing generation.

## Original production (scope confirmed by user: ~400+ approved records, quality-gated, both exams, figures required where they help)
Pipeline per question: **author → independent solve (first principles) → adversarial second-pass verify → 3-way answer agreement → single-correct + valid-distractors + syllabus-fit (per exam) + no-source-resemblance → dedup (number-aware Jaccard vs official+original+within) → figure rasterized & verified**. Confidence gate ≥0.90 (≥0.95 reading/figure). Shared questions produce **two separate records** (til_i_orig_* / cents_s_orig_*) linked only by `crossExamRelationId`, with separate figure assets.

Figure engine: agents emit self-contained SVG; rasterized to high-res PNG via node-canvas/librsvg (width/height injected to avoid "invalid matrix"); verifier confirms `figureConsistent`; unrenderable figures → question rejected.

- **Run 1** (shared Math/Physics/Logic, text): 56 candidates → 27 approved → **27 TIL + 27 CEnT records + 27 relations**. (Session limit truncated 26 candidates — resumable.)
- **Run 2** (figure-focused: geometry, function-graphs, physics-diagrams, TIL representation): in progress.
- **Runs 1–8 complete.** Milestone reached: **401 approved original records** — TIL-I **199** (math 76, physics 46, logic 33, CS 22, representation 11, reading 11; 70 figures) + CEnT-S **202** (math 76, physics 46, reasoning 29, chemistry 26, biology 25; 60 figures). **145** shared 2-record pairs. **117** rejected (quality gate). Recurrent session limits handled via resume.
- **Phase J quality gates: 26/26 PASS** (JSON valid, IDs unique per exam, exam-profile correct, no cross-file contamination, no chem/bio in TIL, no repr/CS in CEnT, every record single A-E answer + validation + confidence≥0.90, every imageAsset path exists in the correct namespace, shared pairs use separate assets, CEnT official empty). IMAT + TIL-I official (479 Q) + app code untouched. NOT committed.
- Reports: `til_i_new_question_report.md`, `cents_s_new_question_report.md`, `til_i_new_sources_validation.md`, `cents_s_new_sources_validation.md`.

## AUTHORIZATION UPDATE (owner confirms rights to the uploaded sources)
Copyright block lifted → sources may now be **extracted**. Authorization removes copyright ONLY: these remain authorized third-party prep (NOT CISIA/Politecnico official), so extractions go to new **licensed banks** (`content/{til_i,cents_s}/licensed/*_licensed_bank.json`, `sourceType: licensed_or_user_provided_source`), never the official banks. All quality gates still enforced: readable + complete + single INDEPENDENTLY-verified answer (source answer cross-checked, rejected on conflict) + syllabus match + dedup.
- **Disk reality:** the original exam PDFs are no longer on the Desktop; only the **CEnT-S Exam Book** remains fully rendered (`figtools/cents_book_print`, 202 pp, ~600 CEnT-S MCQs WITH Answer+Explanation) — the highest-value source. TIL-I photo sources were already extracted into the frozen TIL-I official bank. Other sources (TOLC/SAT/Deneme/TTPU/italostudy/WhatsApp) would need re-upload.
- **Pipeline:** `til_licensed_wf.js` (vision-extract chunk → per-question independent solve+audit) + `apply_licensed.mjs` (two-signal gate, dedup, figure page-copy, licensed banks). 39 chunks generated for the CEnT-S book. Extraction runs after the wave-2 original run completes.
