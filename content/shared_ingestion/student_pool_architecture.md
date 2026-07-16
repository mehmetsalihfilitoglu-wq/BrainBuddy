# Student-Facing Pool Architecture — TIL-I & CEnT-S

> **v2.0 update:** the consumer of this eligible pool is the **Daily Challenge** selector (5 NEW questions/day, one challenge per calendar day, retired-after-first-exposure), NOT a "test" mode — see `daily_challenge_architecture.md`. Everything below (one exam choice, source layer invisible, blueprint-driven selection, exclusions) still holds; "test engine" = Daily Challenge selector.

**Binding contract for how the app's Daily Challenge selector consumes the question banks. The bank layers below are INTERNAL content-management layers only; the student never chooses among them.**

## Student choice = exam profile only
The student selects only an **exam profile**: **TIL-I** or **CEnT-S**. There are NO student-facing "official / licensed / original / hard / elite" modes. After the exam is chosen, the engine draws from ONE unified **eligible pool** for that exam.

## One unified eligible pool per exam
The eligible pool for an exam is the UNION of all its eligible production layers, drawn together and indistinguishably:
- **TIL-I**: til_i official + til_i licensed + til_i original + til_i hard/elite.
- **CEnT-S**: cents_s official (when available) + cents_s licensed + cents_s original + cents_s hard/elite.

Selection must NEVER be made by source type. Source layer is invisible to the student.

## Excluded from normal test generation
- rejected questions
- unanswerable questions
- pending-review questions (reviewStatus != approved)
- beginner/adaptive-pool questions — UNLESS the selected mode explicitly requires easier content
- questions below the confidence threshold
- questions with unresolved source or figure defects (missing/invalid figure, defect flags)

## Selection signals (never source type)
exam profile · subject blueprint · topic coverage · difficulty target · recent user history · duplicate avoidance · weak-topic adaptation · estimated solve time.

## Within a single generated test
- no exact duplicates; no near-duplicates
- no two questions with the same solving pattern and only changed values
- no repeated use of the same passage or figure family
- subjects balanced per the exam specification (blueprint)
- medium-hard / hard / elite balanced per the selected mode

## Internal provenance is PRESERVED (never shown to the student)
Kept for QC, copyright/source tracking, duplicate detection, analytics, and maintenance — in the JSON banks and (when seeded) as internal DB columns:
`sourceType` (official | licensed_or_user_provided_source | new_original), `sourceName`, `sourceFile`, `sourcePage`, `crossExamRelationId`, `derivedFromLicensedSkill`, `confidence`, `answerSource`, `reviewStatus`, `validation.*`.

The student-facing question object MUST NOT expose any of: sourceType, sourceName, official/licensed/original labels, crossExamRelationId, internal confidence, or validation fields. (The current `Question` domain model already omits all of these — keep it that way when TIL-I/CEnT-S seeding is added.)

## Current implementation state (2026-07-12)
- `Question` domain model carries no provenance → "source invisible" already holds. GOOD.
- IMAT (`examType='IMAT'`) and MIOITALIA (`examType='MIOITALIA'`) already seed as isolated pools consumed uniformly. Pattern to follow.
- **NOT YET BUILT for TIL-I/CEnT-S:** (a) copy content/{til_i,cents_s} banks into app assets; (b) `DbSeeder.seedTilIIfNeeded` / `seedCentsIfNeeded` inserting ALL layers under `examType='TIL_I'|'CENTS_S'` with internal provenance columns + eligibility flags (approved-only, confidence≥threshold, no unresolved defects, beginner pool tagged and excluded by default); (c) TIL-I/CEnT-S `QuizBlueprint`s (subject targets from the verified exam specs); (d) eligible-pool query honoring the exclusions above; (e) verify the mapper still exposes no provenance. Implement in the app branch, not committed until the editorial audit completes.
