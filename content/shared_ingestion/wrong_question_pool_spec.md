# Wrong-Question Pool & Mastery — Spec

**Date:** 2026-07-18 · Branch `seeding-final-fix` (not pushed). Companion:
[premium_solution_architecture.md](premium_solution_architecture.md).

## Definitions (mapped onto the existing state machine)

One row per (account, question) in `user_question_state` — the primary key guarantees **no duplicate
wrong-pool rows by construction**. Attempt history lives on the same row (`timesSeen`, `timesCorrect`,
`timesIncorrect`, `firstSeenAt` = first-wrong date for wrong answers, `lastSeenAt`, `consecutiveCorrect`,
`nextReviewAt`). The student's selected option per Daily-Challenge answer is preserved in
`challenge_answer.chosenIndex`.

| Product term | States |
|---|---|
| **ACTIVE wrong pool** (always due) | `INCORRECT_ONCE`, `INCORRECT_MULTIPLE`, `FORGOTTEN` |
| **Scheduled review** (due at `nextReviewAt`) | `NEEDS_REVISION` |
| **Resolved history** | `NEEDS_REVISION` (recently answered correctly), `MASTERED` |

## Entry

A wrong Daily-Challenge answer → state `INCORRECT_ONCE` (or `INCORRECT_MULTIPLE` on repeat), attempt
counters updated, review scheduled. It is retired from the new-question pool (already `SEEN_ONCE` at
serve time) — it can never count as a new question again.

## The two Premium paths

### A. Retry Question
The student re-answers WITHOUT seeing the solution first. Option order is the stable per-question
id-seeded order (constant for the retry session). A retry is recorded as a **review attempt** via
`ReviewScheduler.onReview` — it never touches challenge generation, the 5-new/day count, or blueprint
deficits (review reads/writes only `user_question_state`).

- **Correct** → state `NEEDS_REVISION` with the next spaced interval (1→3→7→16→35 days), i.e. it
  LEAVES the active wrong pool exactly once; 5 consecutive correct → `MASTERED` (retired from review
  entirely). History is preserved on the same row (counters never reset). Later reappearance happens
  only through the spaced-repetition schedule — exactly the "mastery system requires it" rule.
- **Wrong** → `INCORRECT_MULTIPLE`, streak reset, rescheduled for tomorrow; stays in the active pool;
  the UI then offers *Retry later* and *View solution*.

### B. View Solution
Opens the full verified solution (question, figure, options, the student's previous answer, correct
answer, structured explanation). **Viewing NEVER advances or removes anything** — it performs no
`onReview` write. There is deliberately no "mark as solved" shortcut: the only way out of the active
pool is answering correctly (or the mastery ladder).

## Free vs Premium

Free: sees correct/incorrect + the correct option; capped review depth (existing
`FREE_REVIEW_DAILY_CAP`); no solution bodies; a clear Premium CTA (no solution text in logs,
accessibility nodes, or previews — the body is simply never bound for Free). Premium: full solutions,
unlimited retries, history. Unknown/failed entitlement behaves as Free (fail-closed).

## Account model

All state keys off the account userId (auth uid, else persisted anon id; anon state is adopted into
the account at sign-in by the existing merge migration). Different emails are fully independent. The
wrong-pool tables are already part of the full-account sync snapshot
(`DailyChallengeSnapshot.states`), so the backend seam carries pool/history/mastery per account.

## UI

- **Hub** (`WrongQuestionsActivity`, Premium surface): active count, due count, resolved count;
  exam filter; section filter; sort by due date / most recent / most-frequently-wrong; cards showing
  exam, section/topic, last-wrong date, wrong-attempt count, review status, and the two actions.
- **Retry** runs on the existing review flow in single-question mode.
- **Solution reader** (`SolutionActivity`) is the only place a solution body renders; entitlement is
  re-checked in `onCreate`.
- **Daily Challenge result screen**: every wrong answer row gets *Retry in Wrong Questions* and
  *View full solution* (Premium) or a locked CTA (Free).

No provenance/tier/source labels anywhere in this UI.
