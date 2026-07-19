# Phase 0 — Release Signing Report

**Date:** 2026-07-19 · Branch `seeding-final-fix` · Scope: give the app a secure, reproducible release
signing setup **without committing any secret** and **without changing the signing certificate**.

## Design
Signing credentials are read from an **untracked** `keystore.properties` at the repo root:

```
storeFile=edumio_release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

- `app/build.gradle.kts` loads it via `FileInputStream` + `Properties` **only if the file exists**
  (`hasReleaseSigning`). When present, it defines `signingConfigs.release` and the `release` build type uses
  it. When absent, no release signing config is created.
- A `gradle.taskGraph.whenReady` guard throws a `GradleException` if a **release** task is requested while
  `keystore.properties` is missing:
  > "EDUmio release signing is not configured. Copy keystore.properties.example to keystore.properties and
  > fill in the EDUmio release keystore (edumio_release.jks) credentials. Secrets must never be committed."
- **Verified:** `./gradlew :app:assembleRelease` **fails fast** with that message when unconfigured — so a
  release artifact can never be silently produced unsigned or with the debug key. Debug builds are
  unaffected (debug keystore).

## Secret hygiene (see also `PHASE0_SECURITY_REPORT.md`)
- `keystore.properties` and `*.jks` / `*.keystore` are in `.gitignore`.
- `edumio_release.jks` exists on disk for local signing but is **not tracked** (only
  `keystore.properties.example`, a placeholder, is committed).
- **The signing certificate was not changed and no new keystore was generated** — per directive. The
  existing `edumio_release.jks` remains the app's identity.
- No password appears in any report, log, or tracked file.

## Owner action at release time
1. Ensure `edumio_release.jks` (the real release keystore) is present on the release machine — **back it up
   securely; losing it means the app can never be updated on Play**.
2. `cp keystore.properties.example keystore.properties` and fill in the real credentials (never commit it).
3. `./gradlew :app:bundleRelease` (or `assembleRelease`) — it will now sign with the EDUmio release key.
4. Recommended: enable Play App Signing so Google holds the upload/app key securely.

## Status
Release-signing architecture is **complete and secure for Phase 0**. The only remaining input is the owner
supplying real credentials at build time — by design, not a code gap.
