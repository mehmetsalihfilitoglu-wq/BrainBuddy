# Daily Challenge — Single Immutable Model (per account, per day)

**Date:** 2026-07-18 · Branch `seeding-final-fix` (not pushed).

This supersedes the earlier *per-exam* Daily Challenge behaviour. There is **no** concept of "remaining
questions across exams" and **no** one-challenge-per-exam. Instead:

> **Exactly ONE Daily Challenge per user account per local calendar day** — 5 NEW questions, generated
> once, then **immutable** (same questions, same order, same ids, forever). Switching exams, reopening,
> rotating, process death, opening Review/Premium, or navigating **never** generate a second one.

## What changed (vs. the previous implementation)

| Aspect | Before | Now |
|---|---|---|
| Challenge key | `(userId, examType, localDate)` — one per exam | `(userId, localDate)` — one per **account/day** |
| Exam | part of the key | `examProfile` **recorded** (the exam it was generated from), **not** part of the key |
| Switch exam | new key ⇒ **new** challenge | same key ⇒ **same** challenge returned |
| Answer key | `userId:examType:localDate` | `userId:localDate` (exam-agnostic) |
| Persisted fields | status, questionIdsCsv, allocation, timings, score | + `challengeId`, `examProfile`, `currentIndex`, `createdAt` |
| Home gate | hid the card when the *active* exam was unsupported | shows today's challenge regardless of active exam; only "empty" when there is genuinely none |

## Persisted contract (`daily_challenge` + `challenge_answer`)

`DailyChallengeEntity` (PK `userId, localDate`): `userId`, `localDate`, `challengeId` (= `"userId:localDate"`,
deterministic), `examProfile`, `status`, `questionIdsCsv` (= orderedQuestionIds, immutable), `currentIndex`
(resume cursor = answered-count), `allocationCsv`, `createdAt`, `startedAt`, `completedAt`, `expiresAt`,
`score`, `shortage`. `ChallengeAnswerEntity` holds the answers (challengeKey `userId:localDate`).

Generation happens **once**, under a process-wide `Mutex`, only when no row exists for the account+day
**and** the active exam is blueprint-supported (IMAT / TIL-I / CEnT-S). It draws 5 NEW (`NEVER_SEEN`)
questions from that exam's pool, retires them (they never recur), and records `examProfile`. Every later
call returns the stored row verbatim.

## Resume, completion, next day

- **Resume:** each answer is persisted immediately; resume index = durable answered-count
  (`DailyChallengeHomePresenter.resumeIndex`), mirrored into `currentIndex`. A completed challenge jumps to
  its result — never a 6th question.
- **Completion:** only when all 5 are answered (`status = COMPLETED`, `completedAt`, `score`). No further
  challenge that day.
- **Next local day:** the first open of a new calendar day generates ONE brand-new challenge from the
  then-active exam; yesterday's row stays as read-only history.

## Review & Premium

Review (`ReviewEngine`) re-surfaces **only** already-served questions (`INCORRECT / NEEDS_REVISION /
FORGOTTEN`); it never creates a challenge row, never retires a `NEVER_SEEN` question, and cannot raise the
5-new-per-day count. Premium changes review depth and learning aids only — **never** the number of new
questions (the `isPremium` input is ignored by generation).

## Account model (email auth)

All state is keyed by `userId` = `AuthProvider.currentUser().userId` (the account/email identity), falling
back to a persisted anonymous id before sign-in. A **different email ⇒ different userId ⇒ an entirely
independent keyspace**, so that account starts from zero. No cross-account sharing.

## Reinstall / sync (full-account snapshot)

A reinstall wipes local storage, so restoring genuinely requires backend sync. `DailyChallengeSync.kt`
defines a **full-account** snapshot (`DailyChallengeSnapshot` = all challenges + answers + **question-
retirement/review states** + deficit ledger + streak) with `DailyChallengeSyncCodec`, and
`DailyChallengeEngine.exportSnapshot(userId)` / `restoreSnapshot` are the hooks. Carrying the retirement
state matters: without it, a reinstall would re-serve already-seen questions the next day. Restore is
additive/idempotent, so a post-restore open returns the same challenge and **regenerates nothing**, and
future days do not re-serve retired questions. **No live backend is provisioned yet**; this is the seam a
real one plugs into, exercised end-to-end by `reinstall_restoresChallengeAfterSync` (which also asserts the
next day re-serves none of the restored day's questions).

## Anonymous → account linking (no second challenge on sign-in)

`DailyChallengeUser.resolve` returns the account uid when signed in, else a persisted anon id. On the first
sign-in that id flips — so without care the account uid would have no challenge for today and a **second**
one would generate. `DailyChallengeAccountLink.linkIfNeeded` (run before `resolve` at every DC entry point)
detects the anon→account transition and calls `DailyChallengeEngine.migrateAccount(anon, uid)`, which copies
the anon account's whole state to the uid, **re-keyed** (`rekeyedTo`). It **never clobbers** an account that
already owns data. After linking, the anon id is cleared, so the account returns its existing challenge and
generates nothing. Proven by `signInMidDay_adoptsAnonChallenge_doesNotRegenerate`.

## Undersupply is fail-closed

If the unseen pool can't fill all 5 slots, generation returns **null** (Home shows the empty state) rather
than serving a 1–4 question challenge — which could never satisfy the "answered ≥ 5" completion gate and
would strand the day `IN_PROGRESS`. Honors "exactly 5 NEW, never fewer". Proven by
`undersizedPool_servesNoChallenge`.

## Review is scoped to the challenge's exam

The single challenge is exam-agnostic, but its wrong answers are retired under the challenge's exam. So the
completion (`Completion.examProfile`) and Home pass that exam to `DailyChallengeReviewActivity.intent(context,
exam)` (read via an intent extra, falling back to the active area). Without this, switching study areas after
completing a challenge left the Review button a dead-end (it queried the *active* exam). Backed at the engine
level by `reviewCount_isScopedToChallengeExam`.

## Migration note (honest)

`DailyChallengeDatabase` bumps `version 1 → 2` with `fallbackToDestructiveMigration()`. The primary-key
change makes a clean data-preserving migration ambiguous (the old model could hold several challenges per
day across exams; the new model allows one), and the app is **pre-launch with no production users**
(consistent with the app-wide fresh-install decision). Concretely: this one schema-changing update rebuilds
the local Daily-Challenge store; **all future same-schema app updates preserve the challenge** (Room keeps
the DB file across updates), so "survive update" holds going forward.

## Invariants proven by tests

`DailyChallengeImmutabilityTest` (pure-JVM, real engine over an in-memory store) + `DailyChallengeSyncCodecTest`:

- generated exactly once (incl. under 24 concurrent opens) · reopen / process death / rotation return the
  same questions · switching exams (incl. to an unsupported exam) returns the same challenge · exactly one
  per account per day · next day creates a new, disjoint challenge · completed challenge cannot regenerate ·
  resume at the next unanswered question · review never generates new questions · reinstall restores after
  sync with no regeneration **and the next day re-serves no retired question** · a different account is
  independent and starts from zero · premium never changes the question count · **sign-in mid-day adopts the
  anonymous challenge instead of regenerating** · **an under-supplied pool serves nothing (never a stuck
  partial)** · review is scoped to the challenge's exam · full-account snapshot round-trips + re-keys losslessly.

Full suite: **135 unit tests, 0 failures**; `assembleDebug` SUCCESSFUL. The change was additionally put
through a two-round adversarial multi-agent review (independent agents trying to break each invariant).
Round 1 confirmed 5 findings (all fixed above). Round 2 confirmed the fixes held and found 2 more in the
new linking code — a *returning* account (with history) that used an anon session the same day still
regenerated, and `migrateAccount` was not crash-atomic — both fixed by making `migrateAccount` a
non-clobbering, idempotent, self-healing MERGE (adopt only days/questions the account lacks; write the
challenge row last so a crash re-adopts on retry). Backed by `returningAccount_signInAfterAnonSameDay_
adoptsNotRegenerates` and `migration_isIdempotentAndSelfHealing`.

A third focused pass on the linking code found one more real HIGH defect — `clearAnonId` ran even when the
migration was cancelled/failed (defeating the self-heal → possible 2nd challenge). Fixed: the anon pointer
is cleared **only after the migration completes**, and `CancellationException` is never swallowed
(`DailyChallengeAccountLink.linkIfNeeded`). The same pass verified the merge is otherwise non-clobbering and
idempotent, and that the re-key/`valueOf`/two-row attacks are all defended.

**Known minor (accepted / out of scope):**
- *(pre-existing)* `reviewCount` counts all review-state rows while the review queue is due-time-filtered,
  so the "Review" CTA can occasionally open the empty-review state (graceful; does not affect generation).
- Adopting a *completed* anon challenge into a returning account that already has a streak does not re-run
  the streak update, so that day's completion may not bump the account streak (no corruption; unusual flow).
- `Result`/`Review` screens don't call `linkIfNeeded`, but neither generates a challenge, so they can never
  create a second one; the state self-corrects at the next generating entry point (Home / the flow screen).
