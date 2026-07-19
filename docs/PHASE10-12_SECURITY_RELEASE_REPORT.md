# Phases 10–12 — Security Hardening, Account Deletion & Release Infra Report

**Branch:** `seeding-final-fix` · **Not pushed.** ✅ validated · 🟡 runtime owner-gated · 🔵 owner/console.

## Security hardening (Phase 10) — ✅ / 🔵
- **App Check** — `AppCheckInitializer.install(context)` installs the **Play Integrity** provider when
  Firebase is configured (no-op + try/caught otherwise; safe from `Application.onCreate`). Lets the backend
  reject requests from tampered/emulated apps. Honest scope: a trust *signal*, **not** perfect anti-tamper —
  the server verifications (challenge identity, entitlement) are the real guarantees. 🔵 Owner registers the
  provider + enforces App Check on Firestore/Functions in the console.
- **Security rules** (built up across Phases 1–7, all owner-scoped + default-deny): profile
  uid/email non-forgeable; `syncRecords` shape-validated owner-only; `challenges` / `entitlements` /
  `learningState` **server-write-only**; `purchaseTokens` / `emailSuppression` / `emailSends` fully
  server-only; `fcmTokens` / `preferences` owner-managed. Emulator rules-tests in `firestore-tests/`.
- **Abuse limits** — auth brute-force / enumeration / reset-abuse are enforced by Firebase Auth's built-in
  quotas; billing replay is blocked by re-verifying every token server-side; email abuse by
  suppression + frequency caps + idempotency; push abuse by `PushNotificationPolicy`.
- Phase-0 hardening still holds: no cleartext, `allowBackup=false`, one exported component, FileProvider
  locked, PendingIntent immutable, WebView JS-off/local-only, R8 strips answer-leaking logs.

## Account deletion (all systems) — ✅
- Chain: the client calls `AuthRepository.deleteAccount()` (Firebase Auth `user.delete()`) → the
  **`onUserDeleted`** Cloud Function **recursively deletes the entire `users/{uid}` Firestore tree** +
  the user's `purchaseTokens`; **`LocalDataRightsService`** clears on-device data (already shipped). So
  deletion covers auth, profile, challenges, question/review/streak state, sync records, tokens, prefs, and
  local storage. Premium purchase records are retained only as long as legal accounting requires (handled
  outside the recursive delete if needed). 🔵 The user-facing delete screen + progress UI ship with the auth
  UI increment.

## Release infrastructure (Phase 11–12) — ✅ ready / 🔵 owner
- Already in place (Phase 0): secure release signing + fail-fast guard; R8/ProGuard; **full release build
  proven end-to-end** (`assembleRelease` with a stand-in key). Mapping file is produced by R8 for
  deobfuscated crashes.
- Checklists maintained: `PLAY_STORE_RELEASE_CHECKLIST.md` (AAB, App Signing, tracks, staged rollout,
  Data Safety, content rating, URLs), `REAL_DEVICE_TEST_SCRIPT.md`, `CLOSED_BETA_CHECKLIST.md`.
- 🔵 Owner: real keystore, first AAB upload, Play App Signing enrolment (then add its SHA to Firebase),
  store listing assets, staged rollout.

## Guardrails
Content untouched (`af65df01…`). 205/0 JVM tests. assembleDebug OK (App Check dep resolves). No secret committed.
