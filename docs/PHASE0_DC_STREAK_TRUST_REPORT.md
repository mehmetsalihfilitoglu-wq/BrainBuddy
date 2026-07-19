# Phase 0 — Daily Challenge & Streak Trust Audit

**Date:** 2026-07-19 · Branch `seeding-final-fix` · Scope: audit which Daily-Challenge / streak fields are
device-authoritative today, what a user could manipulate, and what must become server-authoritative in
Phase 2/3. **No selection logic, the five-question rule, or content changed** (content layer frozen).

## Trust model today (all device-authoritative — no server)

| Field / decision | Source today | Manipulable? | Correct future owner |
|---|---|---|---|
| "Today" local date | `DailyChallengeDates.localDate()` → `System.currentTimeMillis()` + device zone | **Yes** (device clock/zone) | **Server time** (Phase 3) |
| Which 5 new questions | `DailyChallengeEngine` blueprint selection, seeded by (account, date) | No (deterministic; no premium input) | Stays deterministic; server may co-sign |
| One-challenge-per-account-per-day | `daily_challenge.db` unique keying + `getOrCreateToday` | No (idempotent; concurrency-safe) | Server snapshot reconciles on multi-device |
| Answers / correctness | Local DB | Yes (rooted device) | Server-verified on sync (Phase 2) |
| Streak current/longest/milestones | `DailyChallengeStreak.onComplete` over local date labels | **Yes** (via clock) | **Server-recomputed** from server-stamped completions (Phase 3) |
| Premium depth (review/solutions) | `PremiumStore` (now HMAC-signed, fail-safe Free) | Hardened vs casual edit | **Server purchase verification** (Phase 3) |

## Invariants and their guards (all already enforced + tested)

The five hard invariants are enforced by pure guardrails/policies and covered by JVM tests — **no new tests
were required** (Section 7 adds tests only for *uncovered* edges; none were found):

1. **Exactly one immutable challenge / account / local day (cross-exam).**
   `DailyChallengeImmutabilityTest`: `challenge_isGeneratedOnlyOnce`, `concurrentOpens_produceExactlyOneChallenge`,
   `reopen_processDeath_rotation_returnSameChallenge`, `switchingExams_returnsSameChallenge`,
   `completedChallenge_cannotRegenerate`, `nextDay_createsNewChallenge`,
   `signInMidDay_adoptsAnonChallenge_doesNotRegenerate`.
2. **Exactly 5 new questions.** `DailyChallengeGuardrailsTest.neverMoreThanFiveNewQuestions`,
   `undersizedPool_servesNoChallenge`.
3. **Premium never increases the count.** `premium_neverChangesQuestionCount`,
   `DailyChallengeGuardrailsTest.premiumMustNotChangeNewQuestionCount`,
   `DailyChallengeEntitlementPolicyTest.newQuestionLimitIsIdenticalForFreeAndPremium`.
4. **Retire-after-first-exposure.** `DailyChallengeGuardrailsTest.retirementMeansServedLeavesUnseenPool`.
5. **Streak correctness across day/gap/DST.** `DailyChallengeStreakTest` (consecutive, same-day idempotent,
   1-day + many-day gap reset, milestone-fires-once, DST 23h day).

## Known residual gap (honest — a Phase 3 server task, NOT a Phase 0 blocker)

**Device clock / timezone is trusted for "today".** A user who changes their device clock can:
- pull *future* days' challenges early (consumes the question bank faster), and/or
- fill a streak gap or inflate the streak.

This is inherent to **any** client-only daily model and **cannot be fixed client-side** — a client cannot
know the true time on a manipulated device. It does **not** violate the five invariants (still exactly one
immutable 5-question challenge per account per local day; premium still neutral). The fix is **server time
authority**: stamp completions server-side and recompute streak from server timestamps when Firebase/Cloud
Functions land (Phase 3). Recommended Phase 2/3 additions when sync arrives:
- server-stamped `completedAt`; streak recomputed on the server, client value treated as advisory;
- idempotency key = `(accountId, serverLocalDate)` so a synced completion can't double-count;
- conflict rule: server record wins; client reconciles on next sync.

## Conclusion
The Daily Challenge / streak logic is **trustworthy for a single device with an honest clock** and its
release-critical invariants are fully guarded and tested. The only trust improvement left — resisting a
manipulated device clock — is a **server** capability deferred to Phase 3 by design and is documented, not
silently ignored.
