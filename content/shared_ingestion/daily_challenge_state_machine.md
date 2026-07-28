# Daily Challenge — State Machines & Domain Model

Companion to `daily_challenge_product_spec.md`. Defines domain objects, the per-user question-state machine, the challenge lifecycle, streaks, and the selection/deficit model. Docs only.

## 1. Core domain objects (persistence-oriented)
- **ExamProfile** — `IMAT | TIL_I | CENTS_S`.
- **Question** (content bank, read-only): `id, examProfile, section, subSection?, topic, subtopic, difficulty, poolType, eligibleForProduction, finalVerify, imageAsset?, stemHash, figureFamily?, passageId?, solvePatternSignature?`. Selector reads only `eligibleForProduction && finalVerify=="PASS"`.
- **UserQuestionState** — `(userId, questionId)` → `state, timesSeen, timesCorrect, timesIncorrect, firstSeenAt, lastSeenAt, lastResult, nextReviewAt?, masteredAt?, examProfile, section, topic`. The unseen-pool = questions with no row (or state `NEVER_SEEN`).
- **DailyChallenge** — `id, userId, examProfile, localDate (YYYY-MM-DD in user tz), status, questionIds[5], sectionAllocation{section→n}, startedAt?, completedAt?, expiresAt, score?`. Unique on `(userId, examProfile, localDate)`.
- **ChallengeAnswer** — `challengeId, questionId, chosenIndex, isCorrect, timeMs, answeredAt`.
- **SectionDeficitLedger** — `(userId, examProfile, section)` → `expectedToDate, actualToDate`; plus nested `(…, section, subSection)` ledgers for TIL Reading+Logic and Basic Technical Knowledge. Drives blueprint balancing; persisted so convergence survives restarts.
- **Streak** — `userId, examProfile?, current, longest, lastCompletedLocalDate, milestonesReached[]`.
- **ReviewQueueItem** — `(userId, questionId, queueType)` → `dueAt, priority`; `queueType ∈ {INCORRECT, WEAK_TOPIC, SPACED_REPETITION, ADAPTIVE, AI_REVISION}`.
- **TopicExposure** — rolling per-user counters for `(topic)` over last 3 / 7 / 14 days (fatigue prevention).
- **NotificationPreferences / ConsentRecord / NotificationLog / EmailEvent** — see notification/email spec.

## 2. Per-user question learning-state machine
States: `NEVER_SEEN → SEEN_ONCE → {CORRECT | INCORRECT_ONCE} → INCORRECT_MULTIPLE → NEEDS_REVISION → FORGOTTEN → MASTERED`.

Transitions:
- `NEVER_SEEN → SEEN_ONCE`: question served in a Daily Challenge (retire from unseen pool immediately, before result).
- `SEEN_ONCE → CORRECT`: answered correctly first time.
- `SEEN_ONCE → INCORRECT_ONCE`: answered wrong → enqueue INCORRECT review.
- `INCORRECT_ONCE → INCORRECT_MULTIPLE`: wrong again on a later review attempt.
- `{CORRECT | INCORRECT_*} → NEEDS_REVISION`: spaced-repetition scheduler marks due (based on interval + prior result).
- `NEEDS_REVISION → FORGOTTEN`: overdue past a decay threshold without a correct review.
- `{NEEDS_REVISION | INCORRECT_*} → MASTERED`: N consecutive correct reviews at increasing intervals (SR criterion).
- `MASTERED → NEEDS_REVISION`: long-interval SR re-check may re-surface (prevents false mastery).

**Selection pools:** Daily Challenge selects ONLY from `NEVER_SEEN`. Review systems select from `INCORRECT_*`, `NEEDS_REVISION`, `FORGOTTEN`. `MASTERED`/`CORRECT` are excluded from active review unless SR re-checks. All state changes persisted per user; idempotent per `(challengeId, questionId)` answer.

## 3. Daily Challenge lifecycle state machine
States: `LOCKED → AVAILABLE → IN_PROGRESS → COMPLETED` (terminal) / `AVAILABLE|IN_PROGRESS → EXPIRED` (terminal).
- **LOCKED → AVAILABLE**: local calendar day rolls to a new date (reset 00:00 user tz) AND today's challenge not yet generated. Generation = fix section allocation (deficit balancer) → select 5 eligible unseen questions (diversity/history/difficulty filters) → persist DailyChallenge + retire the 5 (state→SEEN_ONCE on serve, or on first-open; recommend retire on first-open/serve to guarantee "appears once").
- **AVAILABLE → IN_PROGRESS**: student opens/starts; `startedAt` set.
- **IN_PROGRESS → IN_PROGRESS**: each of the 5 answered; progress `k/5`; each answer persists a ChallengeAnswer and updates UserQuestionState.
- **IN_PROGRESS → COMPLETED**: all 5 submitted; `completedAt`, `score`, sectionAllocation actualized into the deficit ledger; wrong answers enqueued to review; **cancel all remaining reminders for the day**; streak evaluated.
- **AVAILABLE|IN_PROGRESS → EXPIRED**: local day ends without completion. Missed challenges **do not stack**; a partially-started incomplete challenge does not carry over. Next day a fresh challenge unlocks. (Product choice: whether the 5 served-but-unanswered questions on an expired challenge return to unseen or stay retired — RECOMMEND: retire only on answer/first-view; if never viewed, they may return to unseen. Document the chosen rule before implementation.)

**Invariants:** at most one non-terminal challenge per `(user, exam, localDate)`; Σ allocation = 5; only eligible unseen questions; provenance never surfaced.

## 4. Streak state machine
States: `ACTIVE → AT_RISK → BROKEN`; milestone side-effects at 3/7/14/30/50/100/180/365.
- A challenge counts complete only when **all 5** are submitted.
- `ACTIVE`: `lastCompletedLocalDate == today`. On completion, `current++`, record any milestone reached, `longest = max`.
- `AT_RISK`: `lastCompletedLocalDate == yesterday` and today incomplete (used for reminder tone, not a state break).
- `BROKEN`: a full local day passed with no completion → `current = 0` (unless a future explicit streak-protection feature applies — freeze/repair). Recovery messaging is positive, never punitive.

## 5. Selection & blueprint-balancing model (deterministic, per user)
Per challenge: (1) `SectionDeficitLedger.expectedToDate += officialShare×5` per section; `base = floor(officialShare×5)`; `remaining = 5 − Σbase`; assign remaining to greatest-deficit sections; `actualToDate += allocation`. (2) For TIL Reading+Logic / Basic Technical Knowledge, run the nested deficit balancer to split that section's slots into sub-sections. (3) For each section slot, pick an eligible `NEVER_SEEN` question maximizing topic/concept diversity and difficulty balance, honoring rolling TopicExposure windows (3/7/14 days), avoiding repeated `solvePatternSignature`/`figureFamily`/`passageId`/near-identical `stemHash`, and weak-topic adaptation (may raise a topic's frequency but must avoid monotony). (4) If a section's eligible-unseen selective pool is exhausted for the day → **surface a shortage signal; do not substitute another section or lower quality.** (5) Persist the challenge; the ledger guarantees long-run convergence to official proportions with bounded (<1) drift.
