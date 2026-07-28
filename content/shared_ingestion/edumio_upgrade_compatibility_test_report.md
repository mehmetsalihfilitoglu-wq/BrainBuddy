# EDUmio — Upgrade / Compatibility Test Report

## Context
Owner classified the app as **unreleased / fresh-install** with no production users and no local data to
preserve. Under that decision there is **no upgrade path from an old install to test** — a clean install
creates the EDUmio-named database (`edumio.db`), `edu_*` SharedPreferences, and the `EDUMIO_ORIGINAL`
pool from scratch. Persisted identifiers were renamed directly with no back-compat layer.

## What was validated (this environment)
| Check | Method | Result |
|---|---|---|
| App compiles with new package/DB/pool ids | `:app:compileDebugKotlin` | SUCCESSFUL |
| Full unit suite | `:app:testDebugUnitTest` | 114 / 0 fail |
| APK assembles as new identity | `:app:assembleDebug` + `aapt` | `com.edumio.app`, label EDUmio |
| No old package/brand in APK binary | grep app-debug.apk | 0 matches |
| Whole-repo brand scan | `BrandComplianceTest` | 0 violations |
| DC content banks intact | validator (`validate_assets.mjs`) | 2207 questions, 465 figures, 0 problems |
| Renamed pool asset resolves | `MIOITALIA_ASSET`→`edumio_original/questions.json` path present; seeder references updated | ✓ (source-verified) |
| 5-new-per-day / Premium review-only | unit tests | green (unchanged) |

## What was NOT run (declared, not claimed)
No emulator / Robolectric / instrumented tests are available here, so the following are **not executed**:
- On-device fresh-install run (DB creation, first-run seeding of the renamed pool + grade banks on device).
- Room/SharedPreferences behaviour on a real device.
- WorkManager job scheduling under the renamed unique-work names on device.
- Any old→new **upgrade** test — **not applicable** by the fresh-install decision (no prior install exists).

These are covered by the manual device script (`internal_qa_manual_test_script.md`) and must be executed
during real-device QA, which per the workflow follows this eradication.

## Conclusion
For a fresh-install/unreleased app, upgrade-compatibility risk is **not applicable**: there is no legacy
store to migrate, so a clean install is inherently safe. First-run behaviour with the renamed identifiers
is source-verified and build/test-green; on-device confirmation is part of the subsequent device QA pass.
