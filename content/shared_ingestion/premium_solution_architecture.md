# Premium Solutions — Architecture

**Date:** 2026-07-18 · Branch `seeding-final-fix` (not pushed). Companion docs:
[premium_solution_coverage_report.md](premium_solution_coverage_report.md),
[wrong_question_pool_spec.md](wrong_question_pool_spec.md).

## Product rules (frozen)

Premium = access to the complete verified solution of every production question, unlimited
wrong-question review/retries, wrong-question history, and explanations/misconceptions/figure
interpretation. Premium **never** changes: the 5-new-questions/day count, challenge generation,
unseen-question consumption, or blueprint allocation. The immutable one-challenge-per-account-per-day
model (see [daily_challenge_single_immutable_model.md](daily_challenge_single_immutable_model.md)) is
untouched by this feature.

## Solution storage model

Solutions are **static content overlays**, one per bank — deliberately NOT rows in `edumio.db` (no
schema migration risk) and deliberately NOT edits to the question banks (the IMAT bank is frozen and
PDF-exact; it is never modified):

| Layer | File | Contains |
|---|---|---|
| Shipped asset (student-facing) | `app/src/main/assets/{imat,til_i,cents_s}/solutions.json` | public fields only |
| Content master (with verification metadata) | `content/{imat,til_i,cents_s}/solutions_master.json` | public fields + `verified`, `verificationConfidence` |

Because solutions ship as assets, they automatically survive app restart, process death, offline use,
app updates, and database reseeding — no sync or regeneration is ever needed to open one.

### Record schema (asset)

```json
{
  "questionId": "imat_2013_biology_012",
  "correctOption": 2,
  "shortExplanation": "1-3 sentences",
  "solutionSteps": ["step", "step", "..."],
  "keyConcept": "one line",
  "commonMistake": "one line",
  "optionExplanations": { "0": "why this distractor is wrong", "3": "..." },
  "figureExplanation": "only for figure questions",
  "formulaNotes": "only when a formula is central",
  "solutionVersion": 1
}
```

- `correctOption` indexes the bank's ORIGINAL choice order. The app shuffles displayed options
  (stable per-question seed), so solution text never refers to options by letter — always by content.
- Internal fields (`verified`, `verificationConfidence`) exist only in the content master and are
  stripped from the shipped asset. No provenance/sourceType/labels anywhere in student-facing text.

## Authoring + verification pipeline (how every solution was produced)

1. **Shard**: each bank is split into batches (25 text / 12 figure questions). Batch inputs contain
   stem/choices/section/topic/figure path — **no stored answer, no existing explanation**.
2. **Blind author (gen agent)**: independently solves every item from first principles (reading the
   actual figure image for figure items), records its `computedIndex`, and authors the structured
   solution under strict style rules (English, concise, plain-text notation, no letters, no metadata).
3. **Independent verifier**: solves each item itself BEFORE reading the authored solution, then audits
   every claim/step/figure reference and style rule; emits `verifiedIndex`, `qualityPass`, `confidence`.
4. **Mechanical gate (assembler)**: an item is accepted only when
   `computedIndex == storedKey == verifiedIndex` AND `qualityPass` AND `confidence >= 0.95` AND a
   forbidden-token scan passes. Items where BOTH independent solvers agree on a different answer than
   the stored key are **BLOCKED** (documented in the verification report; the stored key is never
   silently changed). Other failures are re-worked in bounded retry rounds; leftovers are blocked and
   documented, never shipped unverified.
5. **Transactional apply**: a bank's master + asset files are written in one step only after every
   non-conflict item is resolved. Partial batches are never applied.

## Runtime access

- `SolutionStore` lazily parses the active exam's asset once and indexes by `questionId`
  (in-memory cache; ~1MB/bank). Pure read-only lookups; no I/O beyond the first open per exam.
- **Entitlement gate**: solution content is only reachable through the entitlement layer
  (`EntitlementProvider` → fail-closed to Free on unknown/error). The gate is enforced at Activity
  entry (`onCreate`), not by hiding buttons — deep links or crafted intents into the solution screen
  re-check entitlement and bounce to the paywall. Free users see correct/incorrect and the correct
  option, never the solution body.
- **Honest limitation**: solutions ship inside the APK, so a determined attacker can unpack the APK
  and read the JSON. This is a deliberate trade-off for offline access; no DRM claim is made. The
  entitlement gate is a product boundary, not cryptographic protection. (A server-fetched solution
  body would close this at the cost of offline access — the seam allows moving to that later.)

## Analytics

Events use question IDs only — never stems, options, or solution bodies:
`sol_opened`, `sol_cta_shown`, `sol_cta_converted`, `retry_started`, `retry_correct`, `retry_wrong`,
`wrong_pool_resolved`, `wrong_pool_rescheduled`. Analytics failure never blocks the learning flow
(local sink, fire-and-forget).
