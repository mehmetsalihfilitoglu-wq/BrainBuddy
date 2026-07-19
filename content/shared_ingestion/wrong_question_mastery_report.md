# Wrong-Question Mastery — Report

**Date:** 2026-07-18 · Branch `seeding-final-fix` (not pushed). Companion:
[wrong_question_pool_spec.md](wrong_question_pool_spec.md).

## What was built

The two-path Premium wrong-question experience, on top of the existing (already-tested) spaced-repetition
state machine — so no new question can ever enter through it:

- **Retry Question** (`DailyChallengeReviewActivity` single-question mode via `retryIntent`): re-answers
  one already-served question. Correct → the mastery ladder moves it out of the always-due active pool
  (and 5-in-a-row → `MASTERED`); wrong → `INCORRECT_MULTIPLE`, rescheduled for tomorrow, stays active.
- **View Solution** (`SolutionActivity`): read-only. Performs **no** state write, so it can never remove
  a question from the pool.
- **Hub** (`WrongQuestionsActivity`): active / due / resolved counts, exam + section context, sort by
  due date / most recent / most-frequently-wrong, both actions per card. Free sees counts + a locked CTA.

## Invariants and how they're proven

All in `WrongPoolInvariantsTest` (real engines over the in-memory store) unless noted:

| Invariant | Test |
|---|---|
| A wrong answer creates exactly one pool row; a repeat never duplicates it | `wrongAnswer_createsExactlyOnePoolRow` (PK `(userId, questionId)` guarantees it) |
| Correct retry leaves the active pool **exactly once**; history preserved | `correctRetry_removesFromActivePoolExactlyOnce` |
| Rapid double submission resolves only once | same test (second correct advances the ladder, emits no second resolve) |
| Viewing a solution never removes/changes anything | `viewingSolution_neverRemovesFromPool` |
| Retry can never surface a never-served (new) question | `retry_neverSurfacesUnservedQuestion` |
| Review/retry never consumes new-question quota or blueprint deficits | `review_neverTouchesQuotaOrDeficits` |
| Process recreation restores retry state | `processRecreation_restoresRetryState` |
| Wrong-pool data is account-isolated | `wrongPool_isAccountIsolated` |
| Analytics carry IDs only (no stems/options/solution text; bounded length) | `analytics_neverCarryContent` |
| Mastery machine invariants hold over thousands of randomized attempts | `randomizedReviewSimulation_invariantsHold` (200 questions × up to 40 attempts) |
| Premium never increases the 5-new/day count; the Daily-Challenge model stays immutable | full `DailyChallengeImmutabilityTest` (18) still green |

## Account model

State keys off the account (`DailyChallengeUser.resolve`), and the wrong-pool/state tables are already
carried in the full-account sync snapshot (`DailyChallengeSnapshot.states`) with anon→account merge on
sign-in — so pool, attempt history, and mastery survive reinstall and belong to the account. Different
emails are independent (proven by the isolation test).

## Randomized simulation summary

The `randomizedReviewSimulation_invariantsHold` test runs 200 independent question lifelines of up to 40
random correct/wrong attempts and asserts, at every step: a wrong attempt always reschedules into the
future and resets the streak; a correct attempt either schedules the next spaced interval or, at
`MASTERY_STREAK` consecutive corrects, retires to `MASTERED` (never due again); the consecutive-correct
counter never runs away. A deterministic 5-correct path always reaches `MASTERED`.
