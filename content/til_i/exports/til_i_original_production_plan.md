# TIL-I Original Question Production Plan

**Goal:** an isolated, production-ready TIL-I original question bank (`examProfile: "TIL_I"`), mirroring the frozen-official + original-bank architecture proven on IMAT. Built **after** official extraction, seeded separately, never blended with IMAT / Mioitalia / CEnT-S.

## 1. Target composition (weighted to the real TIL-I exam)

Exam weights: Math 16 / Reading 5 / Logic 5 / Physics 10 / Representation 3 / CS 3 (of 42). Proposed **original** bank ≈ **800 questions**, proportional so any number of full 42-Q mock exams can be assembled:

| Section | Target | % | Rationale |
|---|---|---|---|
| Mathematics | 300 | 37% | Largest section; broadest topic tree |
| Physics | 190 | 24% | 10/42 weight; formula + conceptual |
| Logic | 100 | 12% | Aptitude; high CEnT-S transfer |
| Reading comprehension | 80 | 10% | Passage-based (≈16 passages × 5 Q) |
| Computer science | 65 | 8% | TIL-I-unique; conceptual + code-reading |
| Representation | 65 | 8% | TIL-I-unique; **image-heavy** (spatial) |
| **Total** | **800** | | |

## 2. Difficulty distribution

TIL-I is a **school-level + aptitude** engineering-entry test (not a medicine-selection filter like IMAT), and calculus is excluded. Target **30% easy / 50% medium / 20% hard** (no "very_hard" tier). Graphic/graphical interpretation is privileged over long algebra (per the syllabus).

## 3. questionType mix per section

- **Mathematics:** calculation, formula_manipulation, graph_interpretation, word_problem, geometry_diagram, conceptual.
- **Physics:** calculation, conceptual, unit_dimensional, figure_based, word_problem.
- **Logic:** logic_deduction (orderings, quantifiers, conditionals, negation, elementary puzzles).
- **Reading:** reading_inference (main idea / detail / inference / tone / vocabulary-in-context), always text-bounded.
- **Computer science:** code_reading (trace a loop/recursion), conceptual (systems/tools).
- **Representation:** spatial_reasoning, figure_based (nets, projected views, rotations) — **requires generated diagrams → own image assets**.

## 4. Avoid-list (do NOT clone the official questions)

Do not reproduce, number-swap, or lightly reskin any official TIL-I/mock item. Specific templates already used by the official mock (produce genuinely different scenarios):
- Kepler T∝r^{3/2} period-ratio; free-fall time from a height; v²=2as "after X m"; P=VI power-units trick; equipotential-surfaces-never-intersect; Q=mcΔT mass comparison; sound-needs-a-medium on the Moon; equal-T ⇒ equal-mean-KE gas comparison.
- Currency-conversion arithmetic; two-children age linear system; percentage-from-a-2×2-table.
- "Which Venn diagram relates SQUARES/RECTANGLES/PARALLELOGRAMS"; cyclic pile-rearrangement ordering; "all enrolled are diligent" contrapositive; sign/parity of a product of N integers; +/− sign-assignment to hit a target sum; if-more-than-300-pupils conditional.
- Also cross-check against the full official bank once image-extraction completes.

## 5. CEnT-S-adaptable original types (mark, don't mix)

When producing TIL-I originals, tag those with cross-exam value `sharedCandidate: true` + `"shared_candidate_cents_s"`: general **logic**, **reading comprehension**, and **exam-agnostic quantitative word problems**. Physics/CS/Representation stay TIL-I-only until a CEnT-S syllabus is provided. **No CEnT-S export happens from the TIL-I bank** — a shared item is later copied as a separate `cents_s_...` record.

## 6. Pipeline (reuse the IMAT/Mioitalia machinery)

1. Editorial batches (~50), topic-steered per the taxonomy, rotating subtopics via an avoid-list.
2. Generate → **independent adversarial verify** (re-solve each from scratch) → dedup vs official + prior originals (Jaccard) → hand-verify every quantitative answer → assemble → build asset → seed-version bump → build → commit → report.
3. **Math/Physics/Representation need generated figures/formulas** rendered as `til_i_<...>.png` image assets (LaTeX→PNG for formulas, diagram generation for representation) — the biggest new pipeline component vs. IMAT.
4. Isolation: `examProfile='TIL_I'` (add to ExamType), own asset `assets/til_i/questions.json`, own seed `seedTilIfNeeded`, own DAO pool, own version key.

## 7. Editorial rules (quality standard for TIL-I originals)

- **Never copy** an official question; never produce a copy by only changing numbers/values/context.
- **Exactly one** defensible correct answer per question; 5 options, mutually exclusive, similar length.
- Every distractor encodes a **specific, plausible misconception** (state it in the option analysis).
- **Explanation:** short but instructive (1–4 sentences); show the key step, not padding.
- Avoid artificially long / contrived stems; keep to real TIL-I / Polito level and phrasing.
- Stay **within the TIL-I syllabus**; calculus is excluded; privilege graphical/conceptual reasoning over heavy algebra.
- Reading questions must be answerable **from the passage alone** (no outside knowledge).
- CS at **basic** level (concepts + short code traces); Representation at basic graphic-language level.
- CEnT-S-suitable items are **only tagged**, never separated out of the TIL-I bank unless/until a dedicated CEnT-S export runs.

## 8. Open items requiring your input / source confirmation

- **TIL-I scoring** is not stated in the provided TIL-I sources (only TOLC-I's +1/−0.25/0 is documented, a different exam). Confirm TIL-I scoring before analytics rely on it.
- **Structure discrepancy:** the official mock uses an older 18/12/6/6 layout with **no Basic-Technical section**; the current Quick Guide uses 16/10/10/6 **with** Representation + CS. Confirm which format the app should target (recommended: current Quick-Guide format).
- Tier-2/Tier-4 PDFs (`TIL MATHEMATICS QUESTIONS`, `TIL-PHYSICS-QUESTIONS`, the `4_*` past papers, `MATH/PHYSICS/LOGIC/ENGLISH/Mock_Test-1/Pol`) need **content inspection** to confirm they are TIL-I (vs TOLC-I/IMAT/other) before official extraction.
- **CEnT-S syllabus** is absent — needed before shared-candidate selection can be finalised.
