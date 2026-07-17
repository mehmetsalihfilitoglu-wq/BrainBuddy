# EDUmio — Full Technical Brand Eradication (Master Report)

**Date:** 2026-07-18 · **Branch:** `seeding-final-fix` (not pushed) · **Final brand:** **EDUmio (only)**
**Basis:** owner confirmed the app is UNRELEASED (fresh-install; no production users / no legacy data),
and that **MioAcademy** is also a forbidden historical brand. So all identifiers were renamed **directly**
to EDUmio with **no back-compat/migration layer**.

Forbidden set (all case/spacing variants): BrainBuddy, Brain Buddy, Barjin, MioItalia, Mio Italia,
Mioitalia, MIOITALIA, MioAcademy, Mio Academy, mioacademy.

Companion reports: `edumio_persistence_migration_report.md`, `edumio_package_namespace_report.md`,
`edumio_repository_zero_occurrence_report.md`, `edumio_archive_cleanup_report.md`,
`edumio_upgrade_compatibility_test_report.md`.

## What was renamed
| Layer | Old → New |
|---|---|
| Package / namespace / applicationId | `com.mioacademy.app` → **`com.edumio.app`** (all source moved to `com/edumio/app/**`) |
| Application class | `MioAcademyApp` → **`EDUmioApp`** |
| Room database class + file | `BrainBuddyDatabase` → **`EdumioDatabase`**; `brainbuddy.db` → **`edumio.db`** |
| SharedPreferences file names | `bb_*` (~20) → **`edu_*`** |
| Colors / styles | `bb_*` → `edu_*`; `BB.*` / `BB` → `Edu.*` / `Edu` |
| Original-question pool | examType `MIOITALIA` → **`EDUMIO_ORIGINAL`**; `seedMioitalia*`/`getMioitaliaPool`/… → `…EdumioOriginal…`; key `mioitalia_seed_version` → `edumio_original_seed_version`; asset dir `assets/mioitalia` → `assets/edumio_original` |
| WorkManager unique-work names | `brainbuddy_*` → `edumio_*` |
| Billing product ids | `mioitalia_premium_*` → `edumio_premium_*` |
| Audit filename / log tags | `brainbuddy_audit.txt` → `edumio_audit.txt`; `BrainBuddyStartup`/`…SeedAudit` → `EDUmio…` |
| Content source dir | `content/mioitalia` → **`content/edumio_original`** |
| Asset self-provenance | `source`/`publisher`/`origin`/`sourceRef` = brainbuddy/mioitalia → **edumio** (metadata only) |
| Build metadata | `rootProject.name` → EDUmio; proguard header |
| Files renamed | `scripts/build_mioitalia_asset.js` → `build_edumio_original_asset.js` |
| Files deleted | `compile_log.txt` (stale build log) |
| Docs archived | 12 historical reports → `docs/archive/pre-edumio/` |

## Enforcement
`BrandComplianceTest` now scans the **entire active repository** (file contents + file names + directory
names) and fails on any forbidden token, excluding `.git/.claude/build/.idea/.gradle` and the minimal
`brand_allowlist.txt` paths. **Result: 0 violations.** `appLabelIsEduMio` asserts the label is EDUmio.

## Verification
| Check | Result |
|---|---|
| Whole-repo brand scan | **0 violations** (BrandComplianceTest green) |
| App label | **EDUmio** (aapt `application-label:'EDUmio'`) |
| Built APK package | **`com.edumio.app`** (aapt badging) |
| Old package/brand bytes in APK | **0** |
| `:app:compileDebugKotlin` | SUCCESSFUL |
| `:app:testDebugUnitTest` | **114 / 0 fail** |
| `:app:assembleDebug` | SUCCESSFUL — `app/build/outputs/apk/debug/app-debug.apk`, 76,748,723 B |
| DC banks (TIL-I+CEnT-S) | 2207 questions, 465 figures, **0 problems** (unchanged) |
| Question counts | unchanged — only metadata/provenance fields edited, never stem/options/answer/explanation |
| 5-new-per-day / Premium-review-only | unchanged (tests green) |
| `.idea/misc.xml` | never staged |

## Commits (fresh-install, focused; not pushed)
```
cd66f08 refactor(package): move code to com.edumio.app namespace + rename app/DB classes
fbd0603 refactor(brand): rename bb_/BB. identifiers to edu_/Edu.
b982e4a refactor(brand): migrate MioItalia pool + asset self-provenance to EDUmio
e06ce04 chore(brand): eradicate historical names from content/docs/scripts + whole-repo enforcement
```
~2,474 files changed across the eradication (dominated by asset-metadata + package moves).

## Status
**EDUmio FULL TECHNICAL BRAND ERADICATION COMPLETE** for the active repository: zero unexplained
historical-brand occurrences in contents, file names, or directory paths; the project builds; all tests
pass; the APK assembles as `com.edumio.app`/EDUmio; content and business logic are unchanged. Remaining
occurrences exist only in immutable Git history, the `.claude` tool worktrees (ephemeral, excluded), an
untracked local signing keystore (protected), and the migration reports/allowlist that must name the
removed brands — all documented in `edumio_repository_zero_occurrence_report.md`.
