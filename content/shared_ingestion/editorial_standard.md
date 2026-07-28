# Editorial Standard — TIL-I & CEnT-S Original Question Production

**This is the standing quality bar for ALL future original-question production and for reviewing existing questions.**

## Objective
Do **not** mirror the average historical difficulty. Prepare students for the **upper bound of possible future exam difficulty** — assume future exams may become more selective and demand stronger reasoning, while remaining **inside the official verified syllabus**.

**Target experience:** *"A well-prepared student should find the real exam no harder than the questions in this platform."*

## Difficulty distribution (per exam, main production banks: original + hard)
- **~70–75%+ medium-hard and hard** — genuinely differentiate strong from average students.
- **Easy only where it has real value** — foundational items, topic introduction, adaptive-learning support. Never routine one-step filler to inflate the count.
- Genuinely-hard items route to the dedicated **hard pools** (`*_hard_bank.json`). Foundational easy items route to the **beginner/adaptive pools** (`*_beginner_bank.json`), NOT the main selective bank.

## Increase difficulty ONLY through
- deeper conceptual reasoning; combining ≥2 syllabus concepts; richer mathematical modelling; engineering-style thinking; stronger misconception-based distractors; interpretation of figures/graphs/tables; spatial reasoning; multi-step logical deduction; real problem-solving (not repetitive calculation).

## NEVER increase difficulty through
- out-of-syllabus material (no calculus for TIL-I/CEnT-S); obscure facts; ambiguous wording; unfairly hidden information; unrealistic/tedious calculation; gotcha tricks.

## Per-subject guidance
- **Mathematics** — avoid one-step algebra; combine algebra/functions/geometry/trig/analytical reasoning.
- **Physics** — conceptual reasoning over formula substitution; combine multiple principles.
- **Logic** — increase abstraction while staying fair.
- **Reading** — subtle inference; no answer findable by isolated word-matching; passage required.
- **Representation** — original figures requiring genuine spatial reasoning.
- **Computer Science (TIL-I)** — algorithmic thinking over memorized definitions.
- **Biology / Chemistry (CEnT-S)** — reasoning and application over factual recall wherever possible.

## Non-negotiable validation (every question, authored or rewritten)
Independent solve → second independent verify → exactly ONE correct answer confirmed → distractors valid → syllabus-verified → dedup → confidence ≥0.90 (≥0.95 reading/figure). Figures original, correct, verified, or the question is rejected. **Never keep a weak/trivial question in the main bank to increase the count** — rewrite it, replace it, or move it to the beginner pool.

## Chief-editor stance (standing, continuous)
Act as the **chief editor of a top-tier engineering entrance exam**, not a question generator. The objective is **maximum educational + selective value per question**, never question count. Continuously review existing banks. For every question ask:
1. Would it genuinely differentiate a top-performing student from an average one?
2. Does it require **reasoning rather than recognition**?
3. Is the difficulty **earned through good design**, not unnecessary calculation?
4. Would it still feel challenging to a well-prepared candidate?

If any answer is "no": **rewrite, replace, or move it to the beginner/adaptive pool**. Never keep weak filler. Target: students who consistently do well in this bank should find the real exam *easier* than their preparation, and should frequently think *"I had to stop and really reason through this."* Never design a question answerable by simple pattern recognition.

## Visual questions (high priority)
Visual questions are **among the most selective** in engineering entrance exams. Increase the proportion of high-quality visual questions wherever they naturally fit the syllabus. The figure must be **essential to solving — never decoration**. Reject any question solvable without carefully reading the figure.

Prioritize original figures for: analytical/coordinate geometry, graphs & functions, trigonometry, vectors, spatial reasoning, representation, orthographic projections, engineering drawings, mechanics, free-body diagrams, electric circuits, optics, statistics, data interpretation, logical diagrams, flowcharts, tables.

Good visual questions require the student to: interpret diagrams, infer hidden relationships, mentally manipulate objects, compare representations, analyze graphs, combine visual + mathematical reasoning.

Defaults: **Representation → almost always a figure. Geometry → almost always a diagram. Functions → generally a graph, not a text description. Physics → a diagram whenever it improves conceptual reasoning. Data/statistics/reasoning-on-data → a table or chart the answer depends on.**

Every figure: original, internally consistent, mathematically correct, visually clean, high-resolution, and **independently verified against the stem**. Reject any figure that introduces ambiguity.

## Licensed → Elite-original default workflow
Whenever a **licensed** question is judged below this editorial standard, do NOT edit or flag-and-forget it. Instead:
- **Keep the licensed record unchanged** — preserve its provenance, wording, `sourceType: licensed_or_user_provided_source`. It remains as historical/source material.
- **Immediately author a NEW Elite ORIGINAL** testing the same underlying competency. It must be completely original: different numbers, different context, different distractors, a different reasoning path, and a different figure where appropriate. It must NOT paraphrase or number-swap the licensed question.
- Run it through the full pipeline (independent solve → adversarial verify → single-correct/distractors/syllabus/essential-figure gates → dedup against ALL banks → a `resemblesSource='no'` check against the licensed source).
- Add it to the **original layer** (`sourceType: new_original`) with `derivedFromLicensedSkill: <licensed_id>` (and a `competency` note). This becomes the recommended production question.
Net effect: the licensed layer preserves provenance while the original layer continuously **surpasses** it in editorial quality. Same rule for official is NOT applied — official stays frozen and is only reviewed, never derived-from unless separately instructed.

## Isolation (unchanged)
`official` vs `licensed_or_user_provided_source` vs `new_original` kept separate; TIL-I vs CEnT-S kept separate; shared questions = two records linked only by `crossExamRelationId`. Elite originals derived from a licensed item live in the original layer and point back with `derivedFromLicensedSkill`; the licensed record itself is never modified.
