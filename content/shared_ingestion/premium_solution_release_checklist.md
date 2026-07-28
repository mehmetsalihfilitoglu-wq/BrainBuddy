# Premium Solutions + Wrong-Question System — Release Checklist

**Date:** 2026-07-18 · Branch `seeding-final-fix` (not pushed).

## Content
- [~] Every production-eligible question (IMAT 940 / TIL-I 1107 / CEnT-S 1100 = 3147) has a
  blind-authored, independently-verified solution — **except documented, verified key conflicts** which
  ship no solution and are listed in `premium_solution_verification_report.md`. _(assembly in progress)_
- [x] No solution paraphrases the stored answer (blind author + independent verifier + assembler gate).
- [x] No stored answer key was ever changed; conflicts adjudicated and blocked, not overwritten.
- [x] Solutions ship as per-bank asset overlays; the question banks are untouched (ids + keys intact,
  `SolutionCoverageTest.questionBanks_remainIntactNextToSolutions`).
- [x] Figure questions' solutions were authored with the figure image actually read.

## Premium access
- [x] Full solutions render only for confirmed Premium; Free/unknown/failed → locked (fail-closed,
  `SolutionAccessPolicy`).
- [x] Gate enforced at Activity entry (not hidden buttons); solution screens `exported="false"`.
- [x] No solution text bound for Free (no leak via accessibility / logs / previews / instance state).
- [x] Free still sees correct/incorrect and the correct option.
- [x] Entitlement re-checked on `onResume` (mid-session purchase upgrades in place).

## Wrong-question system
- [x] Each wrong Daily-Challenge answer enters the account's wrong pool once (no duplicate rows).
- [x] Two paths per wrong question: Retry Question + View Solution.
- [x] Correct retry removes from the active pool exactly once; wrong retry keeps + reschedules.
- [x] Viewing a solution never removes a question from the pool.
- [x] Review/retry never consumes new-question quota or blueprint deficits; never surfaces a new question.
- [x] Hub: active/due/resolved counts, exam + section context, sort by due/recent/most-wrong.
- [x] Result screen: per-wrong-answer Premium actions (Free → locked CTA).

## Daily Challenge invariants (unchanged)
- [x] Premium never increases the 5-new/day count or generates a second challenge — full
  `DailyChallengeImmutabilityTest` (18) still green.

## Account / persistence
- [x] Wrong-pool + mastery state key off the account; different emails independent.
- [x] State carried in the full-account sync snapshot; anon→account merge on sign-in.
- [x] Solutions offline by construction (asset); survive restart / process death / update / reseed.

## Analytics / security
- [x] Events carry question IDs only (no stems/options/solution bodies; bounded length).
- [x] Honest offline-bundle extractability limitation documented (`premium_access_security_report.md`);
  no impossible-DRM claim.

## Build / tests _(run at final gate)_
- [~] `SolutionCoverageTest`, `SolutionAccessPolicyTest`, `WrongPoolInvariantsTest`, full DC suite.
- [~] Full unit suite green.
- [~] `assembleDebug` → APK.
- [~] Banks/keys/figures unchanged vs. pre-feature (content-integrity check).

## Commits (focused; not pushed)
1. `content(solutions): add verified solutions for production questions`
2. `feat(solutions): add premium solution access`
3. `feat(wrong-pool): add retry and mastery workflow`
4. `feat(review-ui): add solution browsing and history`
5. `feat(analytics): add solution and mastery events`
6. `test(solutions): add premium and wrong-pool invariants`

(Code commits 2–6 may be squashed into fewer since the app-side surfaces are interdependent; content
commit 1 lands first, after verification.)
