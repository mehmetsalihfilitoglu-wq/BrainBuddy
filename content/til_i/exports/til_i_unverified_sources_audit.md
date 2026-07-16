# TIL-I Unverified Sources — Classification Audit (Batch 02)

Each previously-unverified file was inspected (page-1 render + montage; `Mock_Test-1` is text-based). Classification per the agreed categories. **Only files marked "Confirmed TIL-I" may enter the official bank directly; "Likely TIL-I" require per-page verification first; others stay reference/pending.**

> ⚠️ **Two cross-cutting caveats.** (1) Most of these are **phone photos of admission-test screens** (blur/glare/angle) → clean extraction is hard and each page needs careful reading. (2) **"Admission Test" is a generic platform header used by BOTH TIL and TOLC** — so even within a TIL-looking file, individual pages must be checked (a page showing Chemistry ⇒ TOLC, not TIL).

| # | File | Pages | Classification | Evidence | Can enter official bank? |
|---|---|---|---|---|---|
| 1 | `TIL MATHEMATICS QUESTIONS` | 68 | **Confirmed TIL-I** | Polito "Training test", "First section: Mathematics", score /1800 (18×100), Q1 prime-factorisation `(5⁴+5²)³·2⁴` — same template as mock Q17. | **YES** (image extraction; it's a results-review so correct answers are often highlighted) |
| 2 | `TIL-PHYSICS-QUESTIONS` | 42 | **Confirmed TIL-I** | Polito "Training test", Physics, score /1200 (12×100), Q1 first-law thermodynamics ΔU from Q & L. | **YES** (image extraction) |
| 3 | `LOGIC` | 29 | **Confirmed TIL-I** | Header "…orino" = Politecnico di **Torino** Admission Test; Q4 logic (100 people / hair-colour group reasoning). | **YES** (careful photo extraction) |
| 4 | `4_5886766451745884996` | 151 | **Likely TIL-I (strong)** | Header "…ico" (Politecnico); contains **Representation** questions ("What kind of representation is used in the figure?" + 3D truss) — Representation is a **TIL-I-only** section (absent from TOLC-I). | After per-page verify → likely YES; **prime source for the Representation section** |
| 5 | `4_5859267575740894496` | 151 | **Likely TIL-I** | "Admission Test" photos; engineering math (coordinate-geometry lines; the CORPO combinatorics item you showed). | Verify per-page first (watch for TOLC pages) |
| 6 | `4_5886766451745885002` | 34 | **Likely TIL-I** | "Admission Test"; line-&-parabola graph identification (TIL math style). | Verify per-page first |
| 7 | `4_6001468552727497134` | 83 | **Likely TIL-I** | "Admission Test" (Politecnico "ico"); logic/quantifier reasoning (cars/motorbikes/wheels). | Verify per-page first |
| 8 | `4_6050926737523478323` | 106 | **Likely TIL-I** | "Admission Test"; logic + geometry (inscribed-triangle statement disproof). | Verify per-page first |
| 9 | `PHYSICS` | 137 | **Likely TIL-I (physics past)** | Physics items (stone falls 20 m → speed; the ice-latent-heat & turntable items you showed). | Verify per-page (some may be TOLC) |
| 10 | `ENGLISH` | 28 | **Likely TIL-I (reading)** | "Admission Test" reading passages ("This plastic can repair itself…"). | Verify per-page |
| 11 | `Mock_Test-1` | 28 | **Likely TIL-I practice (needs source confirmation)** | Text-based; "Math Section (36 minutes)" matches current TIL-I timing. But authorship (official Polito vs third-party) unconfirmed. | Only if confirmed official Polito; else classify as practice, NOT official |
| 12 | `MATH` | 164 | **Needs verification — possibly TOLC-I** | "TOLC/ЛOC" watermark visible on the sampled page. | **NO** until confirmed TIL; treat as TOLC/reference (pending) |
| 13 | `Pol` | 96 | **NOT official** | **Handwritten** worked solutions/notes (e.g. "sin²x=1/7 in [−π,π]…"). | No — reference/solutions only |

## Summary
- **Confirmed TIL-I official (3):** `TIL MATHEMATICS QUESTIONS`, `TIL-PHYSICS-QUESTIONS`, `LOGIC`.
- **Likely TIL-I, verify per-page (7):** `4_5886766451745884996` (Representation — high value), `4_5859267575740894496`, `4_5886766451745885002`, `4_6001468552727497134`, `4_6050926737523478323`, `PHYSICS`, `ENGLISH`.
- **Practice, confirm authorship (1):** `Mock_Test-1`.
- **Pending/likely-TOLC, do NOT use as official (1):** `MATH`.
- **Not official — reference only (1):** `Pol` (handwritten).

## Recommended Batch 03 order
1. `TIL MATHEMATICS QUESTIONS` + `TIL-PHYSICS-QUESTIONS` (confirmed, results-review → answers visible) → highest-confidence official extraction.
2. `4_5886766451745884996` → seeds the **Representation** section (TIL-I-unique, otherwise unsourced).
3. `LOGIC` → confirmed Polito logic.
4. Per-page verify the remaining "Likely" files; extract only pages confirmed TIL-I; drop any TOLC/Chemistry page to reference.

**Extraction quality note:** photo-based sources will need per-image OCR/vision reading and manual answer verification (the results-review screens often show the correct option). Expect lower yield/higher effort than the clean mock PDF.

## Batch 03 — extraction progress

| Source | Rendered? | Extracted | Notes |
|---|---|---|---|
| TIL-I Mock Exam | ✅ | **42 / 42** (Batch 01) | complete; 3 figures; 1 source defect |
| `TIL MATHEMATICS QUESTIONS` | ✅ (38 pp) | **16 unique** (attempt 1 of ~5) | Polito `exercise.polito.it` review — answers explicit. 2 of attempt-1's 18 duplicated the mock (deduped). Attempts 2–5 (pp 8–38) draw from the same pool → mostly repeats; **remaining new items pending**. 2 figures cropped. |
| `TIL-PHYSICS-QUESTIONS` | ✅ (25 pp) | **pending** | same clean Polito-review format (answers explicit); ~12 Q/attempt. Rendered to `figtools/tilphys/`; not yet read. |
| `LOGIC` (Polito Torino) | ⬜ | pending | photo-based; confirmed TIL-I. |
| `4_5886…884996` (Representation) | ⬜ | pending | **high value** — only source for the Representation section. |
| `4_5859…`, `4_5886…885002`, `4_6001…`, `4_6050…`, `PHYSICS`, `ENGLISH` | ⬜ | pending (per-page verify) | photo-based likely-TIL. |
| `Mock_Test-1` | ⬜ | pending (confirm authorship) | text-based. |
| `MATH` (TOLC) / `Pol` (handwritten) | — | **excluded** | not official TIL-I. |

**Extracted so far into `til_i_official_bank.json`: 58 verified questions** (Mathematics 34, Reading 6, Physics 12*, Logic 6). *(the 12 Physics + 6 Logic are the mock's; training-test Physics/Logic and the Representation section are the next extraction targets.)*

---

## FINAL Official Extraction — Disposition (bank v1.3-verified)

The full extraction pipeline was then run over ALL confirmed + likely sources (971 rendered pages → 197-agent vision extraction → number-aware dedup → 53-agent adversarial audit+solve). Final bank = **479 official questions** (58 earlier + 421 net new).

### Per-source disposition

| Source file | Rendered pages | Classification | Unique → official | Pending (held back) | Basis |
|---|---|---|---|---|---|
| TIL-I Mock Exam + Key | 16 | Confirmed | 42 (earlier) | — | official PDF |
| `TIL MATHEMATICS QUESTIONS` (attempt 1) | — | Confirmed | 16 (earlier) | — | Polito review page, answers shown |
| `TIL MATHEMATICS QUESTIONS` (attempts 2–5, `tilmath_more`) | 38 | Confirmed | **43** | — | Polito review page, answers shown |
| `TIL-PHYSICS-QUESTIONS` (`tilphys`) | 25 | Confirmed | **43** | — | Polito review page, answers shown |
| `LOGIC` (`src_logic`) | 18 | Confirmed | **16** | — | Politecnico Torino logic |
| `4_5886766451745884996` (`src_repr`, Representation+mixed) | 283 | Likely→promoted per-page | **149** | 20 (uncertain) | per-page examConfidence=til_i; sole Representation source |
| `4_5859267575740894496` (`src_4a`) | 170 | Likely→promoted per-page | **61** | — | per-page til_i |
| `4_5886766451745885002` (`src_4b`) | 79 | Likely→promoted per-page | **23** | — | per-page til_i |
| `4_6001468552727497134` (`src_4c`) | 134 | Likely→promoted per-page | **37** | 5 (uncertain) | per-page til_i |
| `4_6050926737523478323` (`src_4d`) | 118 | Likely→promoted per-page | **2** | — | mostly duplicates of earlier sets |
| `PHYSICS` (`src_physics`) | 85 | Likely→promoted per-page | **44** | 25 (uncertain) | per-page til_i; 3 dropped in audit (unreadable) |
| `ENGLISH` (`src_english`) | 15 | Likely→promoted per-page | **3** | — | per-page til_i reading |
| `Mock_Test-1` (`src_mock1`) | 13 | Practice, authorship UNCONFIRMED | **0** | 36 (practice) | held to pending — not confirmed official Polito |
| `MATH` | 164 | **TOLC watermark** | 0 | — | REJECTED — reference only |
| `Pol` | 96 | **Handwritten solutions** | 0 | — | REJECTED — reference only |

`src_repr`'s 149 spans math/physics/logic/representation (it is a mixed admission-test set, not representation-only); it processed first in dedup order so it "won" uniqueness, which is why later overlapping photo sets (`src_4d`) contributed little.

### Pending (NOT in official bank) — `figtools/til_pending.json`
- **50** questions flagged `examConfidence=uncertain` by the extractor (src_physics 25, src_repr 20, src_4c 5) — could be TIL or leaked TOLC; held for manual confirmation.
- **36** questions from `Mock_Test-1` — authorship (official Polito vs third-party) unconfirmed; held as practice.

### Dedup accounting (reconciles to 1024 raw extracted)
424 unique official + 86 pending + 461 duplicates (23 vs existing bank + 438 internal, number-aware Jaccard ≥0.82 / ≥0.90 when number-sets differ) + 53 unreadable/malformed dropped at filter = **1024**. A further 78 near-duplicate pairs (0.62–0.82) were KEPT as genuinely distinct variants. 3 more dropped in the audit pass (unreadable physics placeholders) → 421 net new.

### Source defects / unresolved issues
1. **Mock physics Q2** (`til_i_mock_phys_02`): options C≡D, key marks C but V=I·R requires ampere×ohm — preserved verbatim (pre-existing, in `sourceIssues`).
2. **6 answer conflicts** (`answer_conflict_review` tag): source-shown key disagrees with a high-confidence independent solve (e.g. `til_i_ex_math_062` key B vs solve E=2b√a; `til_i_ex_math_082` key C vs 3√2=E; `til_i_ex_phys_096`, `til_i_ex_logic_034`, `til_i_ex_math_074`, `til_i_ex_phys_132`). Source key preserved; NOT overwritten; flagged for human adjudication.
3. **39 unknown answers** (`answer_unverified`): mostly reading_comprehension (19) + figure-dependent representation (5); no answer shown in source and none safely computable.
4. **CS content caveat**: several CS items are Scratch-style block-tracing / terminal / file-system questions from photo sources — confirm against the current Politecnico TIL-I "Basic Technical / Computer Science" syllabus before production use.
5. Quality tags: 26 `ocr_minor` (solvable typos), 4 `review_truncated`, 1 `review_garbled` (kept with flag).

---

## ANSWER CLOSEOUT (bank v1.5-answered)

After extraction, the bank was migrated to an explicit answer-provenance schema and every unanswered/conflict question was re-opened at its source.

### Provenance schema (every question)
`officialAnswer` (letter or "unknown"), `computedAnswer` (letter or null), `answerSource`, `confidence`, plus effective `correctAnswer`. Rule: **an official answer is never overwritten by a computed one.**

### Coverage: **467 / 479 answered (97.5%)**
| answerSource | count | meaning |
|---|---|---|
| `official` | 230 | answer explicitly shown in the official source |
| `computed_high_confidence` | 228 | independently solved (reading ≥0.90; others ≥0.85) |
| `computed_medium` | 2 | solved, 0.65–0.85 (`answer_needs_review`) |
| `computed_low_confidence` | 4 | solved, 0.50–0.65 (`answer_needs_review`) |
| `official_conflict_flagged` | 3 | extracted official key kept, disputed by high-conf solve (`answer_conflict_review`) |
| `unanswerable` | 12 | source-completeness defect — see `til_i_impossible_cases.md` |

### Re-solve pass (45 questions re-read from source images)
- Solver high-confidence agreement vs source-shown keys = 96.2% (measured on 157 keyed items).
- 39 unanswered solved from first principles + figures; 4 reading passages recovered by a ±6-page wide-window search (`read_001/002/003/005`).
- 6 answer conflicts adjudicated by source re-read: **3 confirmed** (source shows the extracted key; tagged `answer_key_disputed` because a 97–99% solve still disagrees — check the stem OCR), **3 kept flagged** (source key not re-visible; `answer_conflict_review`).
- CS: 17/17 verified in-syllabus (`cs_syllabus_verified`); 16 answers confirmed, `cs_010` `answer_needs_review`.

### Remaining 12 unanswerable (all incomplete-source; full detail in `til_i_impossible_cases.md`)
10 reading (8 passages never photographed, 2 recovered-but-ambiguous), 1 representation (`repr_011` reference solid not scanned), 1 physics (`phys_141` defective stem). These carry a non-authoritative `tentativeAnswer` but must be excluded from graded quizzes.

### Human-review queue (small)
`answer_key_disputed` ×3, `answer_conflict_review` ×3, `answer_needs_review` ×7 (incl. `cs_010`), plus the 12 unanswerable pending source re-capture.
