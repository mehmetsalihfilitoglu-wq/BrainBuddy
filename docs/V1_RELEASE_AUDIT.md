# EDUmio v1.0 — Google Play Release Audit (Spark-safe Internal Testing)

**Date:** 2026-07-20 · Branch `seeding-final-fix` · **Not pushed.** Product frozen — no features added.

Goal: a stable, Spark-compatible v1.0 for Play **Internal Testing** (no Blaze, no Cloud Functions, no
Premium purchases). Everything that needs an undeployed backend is dormant behind the `SPARK_SAFE` flag; no
visible feature can fail because the backend isn't deployed.

## Technical audit — all green

| Item | Value / Status |
|---|---|
| applicationId | `com.edumio.app` ✅ |
| app label | `EDUmio` (`@string/app_name`) ✅ |
| versionCode / versionName | `3` / `1.0.0` ✅ |
| minSdk / targetSdk / compileSdk | 24 / **35** / 35 ✅ (meets current Play target; API 36 needed for submissions after 2026-08-31 — see note) |
| Release signing | untracked `keystore.properties` + fail-fast guard ✅ (needs the real key to produce the AAB — see below) |
| R8 / minify | `isMinifyEnabled = true` + `proguard-android-optimize.txt` + `proguard-rules.pro` ✅ |
| Log stripping | R8 strips `Log.d/v/i/w` in release (no answer leakage) ✅ |
| Mapping file | produced by R8 at `app/build/outputs/mapping/release/mapping.txt` ✅ |
| `allowBackup` | `false` ✅ |
| Exported components | 1 (launcher `MainActivity`) ✅ |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` — no dangerous perms ✅ |
| Launcher icons | `ic_launcher` + `ic_launcher_round`, all densities incl. **xxxhdpi** ✅ |
| Adaptive + monochrome icon | `mipmap-v26/ic_launcher.xml` with background + foreground + **monochrome** (themed icons) ✅ |
| Notification icon | `drawable/ic_notification_mascot` ✅ |
| Splash | `Theme.EDUmio.Splash` ✅ |
| Firebase | `google-services.json` present → plugins active; Analytics + Crashlytics on (Spark-free) ✅ |
| Debug-only UI | none reachable in release (`DebugSeedStatusActivity` finishes when `!DEBUG`; `PoolStatus`/`SeedAudit` have no launch entry) ✅ |
| Stale brand | none (`BrandComplianceTest` green) ✅ |
| Secrets committed | none (`google-services.json` + `keystore.properties` git-ignored) ✅ |

## Verification run

| Gate | Result |
|---|---|
| `compileReleaseKotlin` | ✅ (via lintVitalRelease resource/code processing) |
| Full unit suite | ✅ **208 / 0 failures** (incl. DC invariants, Premium-neutrality, brand, content coverage, `ReleaseFlagsTest`) |
| `lintVitalRelease` | ✅ **BUILD SUCCESSFUL** (no fatal release lint) |
| Content integrity | ✅ aggregate asset hash `af65df01…` unchanged (banks/keys/figures/solutions frozen) |
| Daily Challenge invariants | ✅ one immutable challenge/day, exactly 5, no 6th, same after restart, retirement, exam isolation |
| Premium neutrality | ✅ Premium never increases the new-question count (and is Free in v1.0) |

## Spark-safe feature freeze (see `V1_DISABLED_FEATURES.md`)
Active in v1.0: local Daily Challenge, local progress/wrong-question/review, local reminders, Firebase
Analytics + Crashlytics. Dormant behind `SPARK_SAFE` (no UI, no failing call): account creation + cloud
sign-in, cloud sync, server entitlement, purchasable subscriptions, server challenge/push/email, admin,
server account-deletion. Flip `SPARK_SAFE=false` when the backend deploys — no code deleted or forked.

## The one build step that needs you: signing the AAB
Everything above is done. To produce the **release-signed `app-release.aab`** the build must sign with the
**real `edumio_release.jks`**, whose password only you hold (it is never in the repo). See
`PLAY_CONSOLE_REMAINING_TASKS.md` §0 for the exact one-step to hand back the credential (or the one command
to run yourself). The build pipeline itself is validated (`lintVitalRelease` + R8 + bundle config all pass).

## Note — future target-SDK requirement
New submissions/updates after **2026-08-31** must target **Android 16 / API 36**. v1.0 targets API 35 (valid
now). Migration is a one-line `targetSdk`/`compileSdk` bump + a re-test; tracked for the next update.
