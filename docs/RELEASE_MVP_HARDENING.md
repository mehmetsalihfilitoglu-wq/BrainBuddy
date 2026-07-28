# EDUmio — MVP Hardening Round (Release Notes)

Product-engineering round completing the items reported "not done" after the
real-device fix pass. Content layer is frozen and untouched.

## Verified release artifact

| Field | Value |
|-------|-------|
| APK | `app/build/outputs/apk/release/app-release.apk` |
| Size | 72,438,833 bytes (~69.1 MiB) |
| Package | `com.edumio.app` |
| versionName / versionCode | `1.0.0` / `3` |
| targetSdk / compileSdk | 35 / 35 |
| Signing key (SHA-256) | `f90f2e06245c6149a2eed069cfdf7873d7c58081a5a3995b15dd7b14be235ced` |
| Signing key (SHA-1) | `0b416aa5c251504b08a939ead9e51a0bf5ba262b` |
| Signer DN | `CN=EDUmio, OU=Mobile, O=EDUmio, L=Istanbul, ST=Istanbul, C=TR` |

`apksigner verify` passes; `lintVitalRelease` passes; R8 minify enabled.

## Quality gates

- Unit tests: **219 passed, 0 failed, 0 errors** (`:app:testDebugUnitTest`),
  including the new `MvpRegressionTest` (11 tests).
- `:app:assembleDebug` and `:app:assembleRelease` both BUILD SUCCESSFUL.

## What shipped this round

1. Subject-based practice removed from Öğren (code + layout + strings).
2. Navigation deduplicated — Settings only via Profil; Reports only under
   İlerleme; Home settings gear and dead premium→Settings paths removed.
3. Settings rebuilt into four sections with Material icons, no emoji.
4. Home states corrected — no "completed"/"0/2" at zero progress; unsupported
   exam vs empty pool distinguished; primary CTA "Bugünkü Mini Teste Başla".
5. Weekly Reward Chest mechanic removed from code (store, prefs, sync spec, UI,
   strings); streak/progress untouched.
6. Profil simplified for MVP (name, exam, real XP/level, areas, settings).
7. Firebase e-mail authentication made mandatory and first-class (real UID, no
   anonymous); onboarding gains a sign-in/up step; Home is gated on sign-in.
8. MVP regression tests added.

Not pushed — local `seeding-final-fix` only.
