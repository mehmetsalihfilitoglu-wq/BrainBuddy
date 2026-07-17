# Final QA — Coverage Report

## Coverage tooling
The Gradle build has **no JaCoCo/Kover plugin configured** (verified: no `jacoco`/`kover` in
`app/build.gradle.kts`). Therefore **no line/branch coverage percentages are reported** — inventing
them would be dishonest. Coverage below is expressed as *what is exercised by JVM unit tests* per module,
plus an explicit list of code with **no** automated coverage.

## JVM unit tests by Daily Challenge module (68 tests; full app suite 112)
| Module (pure logic) | Test suite | Tests | What's covered |
|---|---|---:|---|
| Blueprint balancer | `DailyChallengeBlueprintTest` + `...ExtraTest` | 9 | exactly-5, 365 & 1000-day convergence (<1 drift), sub-section rotation (logic/reading, CS/representation), selection distinctness, answer-letter spread |
| Selection / options | `DailyChallengeOptionsTest` | 5 | stable id-seeded permutation, restart stability, answer round-trip |
| Date / TZ / DST | `DailyChallengeDatesTest` | 6 | midnight boundary, east/west, 23h/25h DST days, DST-boundary, shiftDate leap/year, same-day |
| Streak | `DailyChallengeStreakTest` | 8 | first/consecutive/same-day/gap/multi-gap/milestone-once/longest/DST |
| Review scheduler | `ReviewSchedulerTest` | 7 | SR ladder, mastery, requeue, due-logic, decay, premium cap, review-only-seen |
| Review presenter | `DailyChallengeReviewPresenterTest` | 5 | cap/lock/step/progress |
| Guardrails | `DailyChallengeGuardrailsTest` | 7 | ≤5 new, premium-neutral, review-no-new, retirement, isolation, no-reminder-after-completion |
| Home presenter | `DailyChallengeHomePresenterTest` | 6 | card states, progress, resume/no-6th, countdown, duration |
| Reminder policy | `DailyChallengeReminderPolicyTest` | 8 | slot times, delay wrap, remaining slots, fire gate, completion suppression |
| Notification permission | `NotificationPermissionPolicyTest` | 5 | <33 skip, ask-once, deny-respect, settings path |
| Entitlement policy | `DailyChallengeEntitlementPolicyTest` | 2 | review-unlimited vs cap; new-Q limit constant |

The extraction of `DailyChallengeDates`/`DailyChallengeStreak` this phase moved the release-critical
date/streak logic from the untestable Context-bound engine into pure objects, taking those two areas from
**0 → 14 tests**.

## Code with NO automated coverage (honest gaps)
These require an Android runtime (Robolectric or instrumented/Espresso), which is **not** set up:
- **Activities**: `DailyChallengeActivity`, `DailyChallengeResultActivity`, `DailyChallengeReviewActivity`,
  `HomeActivity.refreshDailyChallenge` — rendering, option selection, image decode, process-death resume
  *as actually run on device*, back-navigation, contextual permission dialog.
- **Room integration**: `DailyChallengeDao`, `DailyChallengeDatabase`, `getOrCreateToday`/`submitAnswer`
  end-to-end (DB reads/writes, the mutex under real concurrency), `ReviewEngine` queue queries,
  `DbSeeder.parseDailyChallengeAsset` seeding.
- **WorkManager**: `DailyChallengeReminderScheduler`/`Worker` scheduling & delivery on device.
- **Entitlement/analytics wiring**: `DailyChallengeEntitlement` (reads `EntitlementProvider`),
  `LocalDailyChallengeAnalytics` sink.

The pure logic underneath each of the above *is* unit-tested (presenters, policies, scheduler, dates,
streak, guardrails); what is uncovered is the thin Android glue that binds it to views, Room, and WorkManager.

## Recommendation
Before public release, add: (1) Robolectric tests for the three Activities' state transitions and
process-death resume; (2) an in-memory Room test for `getOrCreateToday`/`submitAnswer` idempotency and the
generation mutex; (3) a WorkManager `TestDriver` test for reminder scheduling + completion cancellation.
