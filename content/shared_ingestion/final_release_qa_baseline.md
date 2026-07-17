# Final Release QA — Baseline

**Date:** 2026-07-17
**Repository:** `C:/Users/39351/AndroidStudioProjects/BrainBuddy` (primary checkout)
**Branch:** `seeding-final-fix`
**Baseline HEAD (before QA phase):** `ed073cb` feat(entitlements): connect premium review access

## Working tree at start
```
 M .idea/misc.xml        (protected — never staged)
```
No other modified/staged/untracked files. `.idea/misc.xml` is intentionally left unstaged for the entire phase.

## Daily Challenge commits present (pre-QA)
```
ed073cb feat(entitlements): connect premium review access
41081c0 feat(notifications): add runtime permission and reminder controls
4b21cc8 feat(review-ui): add incorrect and scheduled revision screens
750087d feat(daily-challenge-ui): add home and five-question flow
0a34156 feat(analytics): add Daily Challenge events and guardrails
62c7c76 feat(reminders): add local Daily Challenge notifications
76c7931 feat(review): add per-user question state and premium review
e339ba8 feat(daily-challenge): add five-question daily engine
c43a229 content(seed): integrate TIL-I and CEnT-S banks
```
All nine present; 9 commits ahead of `origin/seeding-final-fix`; **not pushed**.

## Baseline build (before any change)
| Step | Result |
|---|---|
| `:app:compileDebugKotlin` | SUCCESSFUL |
| `:app:testDebugUnitTest` | 94 tests, 0 failures, 0 errors |
| `:app:assembleDebug` | SUCCESSFUL |
| APK path | `app/build/outputs/apk/debug/app-debug.apk` |
| APK size | 77,346,746 bytes (~73.8 MiB) |

Environment: Gradle 9.2.1, JDK = Android Studio JBR, compileSdk 35 / targetSdk 35 / minSdk 24.

## Environment limitation (declared up front)
No Android emulator or physical device is available in this environment. All verification is:
JVM unit tests (`testDebugUnitTest`), static/source audit, programmatic content-integrity checks, and
`assembleDebug`. **No on-device or instrumented (Espresso) testing was performed.** A full manual test
script is provided in `internal_qa_manual_test_script.md` for the human QA pass that must follow.

## Content preserved (verified — see final_qa_test_matrix.md §5)
IMAT / TIL-I / CEnT-S banks, verified answer keys, figures/assets, and the five-per-day / Premium-review /
blueprint / provenance-invisibility invariants are all preserved. No content or asset file was modified in
this phase (every commit touched only `app/src/main/java` and `app/src/test`).
