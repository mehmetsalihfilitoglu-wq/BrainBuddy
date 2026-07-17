# Final QA — Test Matrix

Verification type: **AUTO** (JVM unit test) · **AUDIT** (source/static) · **DATA** (programmatic content check) · **MANUAL** (device script, not yet run).

## §2 Daily Challenge lifecycle & invariants
| Scenario / Invariant | Type | Evidence |
|---|---|---|
| Exactly 5 questions allocated every day | AUTO | `DailyChallengeBlueprintTest.everyChallengeAllocatesExactlyFive`, `...ExtraTest` 1000-day |
| Never 6th new question (resume clamps) | AUTO | `DailyChallengeHomePresenterTest.resumeIndexNeverExceedsTotal_noSixthQuestion` |
| Premium never increases new-question count | AUTO | `DailyChallengeEntitlementPolicyTest.newQuestionLimitIsIdenticalForFreeAndPremium`; `DailyChallengeGuardrailsTest.premiumMustNotChangeNewQuestionCount` |
| Review never counts as new questions | AUTO | `DailyChallengeGuardrailsTest.reviewNeverContainsNewQuestions`; `ReviewSchedulerTest.premiumNeverAddsNewQuestions_reviewOnlyResurfacesSeen` |
| One challenge per (user,exam,day) — idempotent | AUTO+AUDIT | PK `(userId,examType,localDate)` + fast-path return; **P1 fix** serializes generation (`8d0efe9`) |
| Completed challenge cannot be regenerated | AUDIT | `getOrCreateToday` returns existing; `DailyChallengeActivity.load` routes completed → result |
| Repeated requests return the same challenge | AUDIT | double-checked read under mutex |
| Retirement occurs once per student | AUTO+AUDIT | `DailyChallengeBlueprintTest.selectorExcludesAlreadyUsedStems`; `getSeenQuestionIds` exclusion |
| Retired questions never return as new | AUTO | selection excludes seen stems/ids |
| No cross-exam contamination | AUTO+DATA | `DailyChallengeGuardrailsTest.noCrossExamContamination`; asset validator §5 (examType per bank) |
| No source/provenance reaches UI | DATA+AUDIT | validator: 0 forbidden keys; Activities read only stem/choices/explanation/section |
| Blueprint allocation before selection | AUDIT | `allocate()` called before pool queries in `getOrCreateToday` |
| Process death after Q1..Q5 → resume | AUTO+AUDIT | `resumeIndex(answered,total,completed)` tests; each answer persisted immediately |
| Rapid taps / double submit | AUDIT | `busy` guard in `DailyChallengeActivity.onNext`; answer upsert is idempotent (PK) |
| Back button mid-flow | AUDIT | answers persisted; home shows IN_PROGRESS; resume at answeredCount |
| Rotation / config change | AUDIT | `configChanges` in manifest for all 3 activities |
| Eligible-question shortage | AUDIT | recorded in `challenge.shortage`; surfaced, never silently substituted |
| Missing figure asset | AUTO(behaviour)+AUDIT | flow/review catch asset-open failure → hide image, no crash |
| Malformed / null options | AUDIT | `parseChoices` falls back to `[A,B,C,D]` on JSON error |
| Missing exam selection / unsupported exam | AUDIT | `isSupported` gate → home shows "unavailable"; engine returns null |
| Anonymous ↔ signed-in id | AUDIT | `DailyChallengeUser.resolve` (auth id or persisted anon UUID) |

## §3 Timezone / date / DST / streak (release-critical)
| Scenario | Type | Evidence |
|---|---|---|
| 23:59 → 00:00 boundary | AUTO | `DailyChallengeDatesTest.localDateWalksTheMidnightBoundary` |
| Timezone east vs west (same instant, diff date) | AUTO | `...sameInstantIsDifferentDateEastVsWest` |
| DST start (23h day) / end (25h day) | AUTO | `...endOfLocalDayIsDstAware_23hAnd25hDays` |
| Boundary across the DST transition | AUTO | `...boundaryHoldsAcrossTheDstTransition` |
| shiftDate leap/year/DST-neutral | AUTO | `...shiftDateIsCalendarCorrectAndDstNeutral` |
| Same-local-day check per zone | AUTO | `...isSameLocalDayRespectsZone` |
| First-ever streak = 1 | AUTO | `DailyChallengeStreakTest.firstEverCompletionStartsAtOne` |
| Consecutive day extends | AUTO | `...consecutiveDayExtendsStreak` |
| Same-day completion idempotent (no double) | AUTO | `...sameDayCompletionIsIdempotent` |
| Missed 1 day / many days → reset to 1 | AUTO | `...oneMissedDayResetsToOne`, `...manyMissedDaysAlsoResetToOne` |
| Milestone fires exactly once | AUTO | `...milestoneFiresExactlyOnce` |
| Longest preserved across break | AUTO | assertions in reset tests |
| Streak survives DST day boundary | AUTO | `...streakSurvivesDstDayBoundary` |
| Countdown correct after tz/DST | AUTO | `DailyChallengeHomePresenterTest.countdownFormatsRemainingTime` (absolute-ms based) |

## §4 Blueprint & selection
| Scenario | Type | Evidence |
|---|---|---|
| 5/30/100/365/1000-day totals = 5 | AUTO | `DailyChallengeBlueprintExtraTest.convergesOver1000DaysWithBoundedDrift` |
| Long-term convergence, drift < 1 | AUTO | same (all 3 exams) |
| No section starved; bank size not controlling | AUTO | convergence uses official weights only |
| TIL logic_reading rotation (6:4) | AUTO | `...tilLogicReadingKeepsBothPresentAt6To4` |
| TIL basic_technical rotation (CS+Representation) | AUTO | `...tilBasicTechnicalRotatesComputerScienceAndRepresentation` |
| Distinct topics / unique stems in a challenge | AUTO | `DailyChallengeBlueprintTest.selectorReturnsDistinctSelectivePicks` |
| Selective-tier preference (~Elite/Hard) | AUTO | same |
| Answer-letter not predictable | AUTO | `...answerLetterIsNotAlwaysTheSamePosition` + stable shuffle round-trip |
| Shortages reported, never silently substituted | AUDIT | `shortage` field |

## §5 Content integrity (already-verified banks not corrupted by integration)
Programmatic validator (`scratchpad/validate_assets.mjs`) over both shipped asset banks — **0 problems**:
| Check | TIL-I | CEnT-S |
|---|---|---|
| Records | 1107 | 1100 |
| Unique ids (cross-bank) | 2207 total, 0 collisions | — |
| examType correct | ✓ all `TIL_I` | ✓ all `CENT_S` |
| Section mapping valid | ✓ | ✓ |
| subSection valid | ✓ | ✓ |
| Tier ∈ {EASY,MEDIUM,HARD,ELITE} | ✓ | ✓ |
| Stem complete | ✓ | ✓ |
| Options 4–5, non-empty, no dup | ✓ | ✓ |
| answerIndex in range | ✓ | ✓ |
| Figures referenced exist | 272/272, 0 missing | 193/193, 0 missing |
| Figure namespace correct | ✓ `til_i/figures/` | ✓ `cents_s/figures/` |
| Path traversal in image path | none | none |
| Unicode / mojibake | none | none |
| Forbidden provenance keys | 0 | 0 |

Risk-based visual spot-checks (per-section rendering) require a device and are listed in the manual script.

## §9 Notifications
| Scenario | Type | Evidence |
|---|---|---|
| 3 slots 09:00/16:00/20:30, delay wraps to tomorrow | AUTO | `DailyChallengeReminderPolicyTest` (8) |
| Completion suppresses remaining slots | AUTO | `...completionSuppressesAllRemainingReminders` |
| Disabled → never fire; already-fired idempotent | AUTO | policy tests |
| No duplicate WorkManager jobs | AUDIT | `enqueueUniquePeriodicWork` per unique slot name, `UPDATE` policy |
| No crash when notifications unavailable | AUDIT | **P3 fix** crash-safe `showNotification` (`a650b2f`) |
| Contextual permission (not on launch) | AUDIT | removed from `HomeActivity`; asked in `DailyChallengeResultActivity` |
| Ask once / respect denial / settings path | AUTO+AUDIT | `NotificationPermissionPolicyTest` (5); Settings routes to OS settings when denied |
| pre-API33 correct | AUTO | `...below33NeverNeedsRuntimePermission` |
| Delivery / quiet-hours / reboot / revocation | MANUAL | manual script §Notifications |

## §10 Premium & review
| Scenario | Type | Evidence |
|---|---|---|
| Unknown/error entitlement behaves as Free | AUDIT | `DailyChallengeEntitlement.isPremiumForReview` try/catch → false |
| Premium unlimited review; Free capped | AUTO | `ReviewSchedulerTest.premiumUnlocksUnlimitedReviewFreeIsCapped`; `DailyChallengeReviewPresenterTest` |
| No entitlement state changes 5-new rule | AUTO | `DailyChallengeEntitlementPolicyTest` |
| Review doesn't alter blueprint deficits | AUDIT | review path never calls `allocate`/`upsertDeficit` |
| Wrong answers reschedule; mastered leaves due; forgotten returns | AUTO | `ReviewSchedulerTest` (7) |

## §7 Accessibility / §12 Performance / §13 Security
See `final_qa_accessibility_report.md`, `final_qa_performance_report.md`, `final_qa_security_privacy_report.md`.

## §8 Device matrix (API 24/29/33/35, light/dark, fonts, offline)
**NOT RUN** — no emulator/device available. Full steps in `internal_qa_manual_test_script.md`.
