# Phase 0 — Security & Repo-Hygiene Report

**Date:** 2026-07-19 · Branch `seeding-final-fix` · Scope: secrets, signing, manifest/component exposure,
data-at-rest, network, and build hardening. **No content changed.** No penetration testing performed — this
is a static hygiene pass appropriate for the pre-launch stage.

## 1. Secrets & credentials — CLEAN
- **No keystore is tracked.** `edumio_release.jks` exists on disk (for local signing) but is **git-ignored**
  (`*.jks`) and **not** in `git ls-files`. Only `keystore.properties.example` (a placeholder template) is
  tracked.
- **`keystore.properties` is ignored and absent from disk** — no real signing passwords exist in the repo
  or working tree. A release build without it fails fast with actionable guidance (see §3).
- **No hardcoded secrets** in `app/src/main/java`: heuristic scan for API keys, `AIza…` Google keys,
  `sk_live`, bearer tokens, PEM blocks, and inline `password="…"` literals found **none**. The only
  `credential`/`token` hits are type names in the federated-auth seam (`CredentialProvider`,
  `FederatedToken`) — not secrets.
- Passwords in code are only the **local** PBKDF2 hash path (`LocalAuthStubRepository`) — hashed on-device,
  never transmitted.

## 2. Local data-at-rest — HARDENED where it matters
- **`android:allowBackup="false"`** + `backup_rules.xml` + `data_extraction_rules.xml` present → user data
  (prefs + Room DBs) is not extractable via `adb backup` / auto-backup.
- **Premium cache** is now **HMAC-signed with an Android-Keystore key** and fails safe to Free on tamper
  (see `PHASE0_DC_STREAK_TRUST_REPORT.md` §6). Documented as non-authoritative until server verification.
- Room DBs are unencrypted (standard for non-sensitive study data; no PII beyond an optional local email).
  If PII grows in Phase 2, consider SQLCipher — noted, not required now.

## 3. Release signing — SECURED
- Signing config loads from **untracked** `keystore.properties`; if absent, `signingConfigs` skips `release`
  and a `gradle.taskGraph.whenReady` guard throws with copy-the-example instructions. **A release artifact
  can never be silently produced unsigned or with the debug key.**
- Debug builds keep the debug key. Certificate unchanged (no new keystore generated — per directive).

## 4. Component exposure — MINIMAL
- **Exactly one exported component**: `MainActivity` (launcher, requires `exported=true` for the
  MAIN/LAUNCHER intent-filter). Every other activity is `exported="false"`.
- **FileProvider** is `exported="false"`, `grantUriPermissions="true"`, scoped by `@xml/file_paths` — the
  correct secure pattern for sharing report PDFs.
- No exported services/receivers/providers with custom actions; no `android:debuggable` forced in the
  manifest (left to build type → release is non-debuggable).

## 5. Network — DEFAULT-DENY CLEARTEXT
- Only `INTERNET` + `ACCESS_NETWORK_STATE` (+ `POST_NOTIFICATIONS`). No dangerous permissions.
- No `usesCleartextTraffic="true"` and no permissive `networkSecurityConfig` → cleartext is blocked by
  platform default. (Moot today anyway: the app makes no first-party network calls — all backend seams are
  local. See `PHASE0_FIREBASE_READINESS_REPORT.md`.)

## 6. WebView & IPC surface — SAFE
- **WebView** is used only by the legal viewers (`legal/LegalDocActivity`, `ui/PrivacyPolicyActivity`).
  Both set `settings.javaScriptEnabled = false` and load **only local** `file:///android_asset/*.html`. No
  `allowFileAccessFromFileURLs` / `allowUniversalAccessFromFileURLs` / `addJavascriptInterface`, and no
  remote URL loading → no XSS/file-exfil surface.
- **PendingIntent:** the only one (`report/ReportWorker`) is created with `FLAG_IMMUTABLE` (+
  `FLAG_UPDATE_CURRENT`) — correct for API 23+; no mutable implicit intents.

## 7. Debug-log leakage — FIXED
- The quiz/quality engines log question stems, correct answers, and choice text at `Log.d/v/i/w` (e.g.
  `AdaptiveQuizRuntime`, `QuizOutputGuard`, `QuestionRepository`). In a **release** build this would leak
  answer text to Logcat.
- Fix: a `-assumenosideeffects` rule in `proguard-rules.pro` strips all `Log.d/v/i/w` calls (and their
  now-dead string building) from the release build. `Log.e` is kept for genuine error diagnostics; its
  remaining sites log only ids/counts after redacting the one that printed full choices
  (`DISTRACTOR_SUFFIX_LEAK … choices=…` → `choiceCount=…`).
- Debug builds retain logs for development (minify off) — acceptable, developer-only.

## 8. Build hardening — ENABLED
- Release: `isMinifyEnabled = true` with `proguard-android-optimize.txt` + `proguard-rules.pro` (R8
  shrink + obfuscate). **Optional follow-up (not a blocker):** add `isShrinkResources = true` to also strip
  unused resources.

## Findings summary

| # | Area | Status | Action |
|---|---|---|---|
| 1 | Tracked secrets / keystore | ✅ Clean | None (already untracked + ignored) |
| 2 | Hardcoded API keys/passwords | ✅ None found | None |
| 3 | Release signing | ✅ Secured | Owner supplies real `keystore.properties` at release time |
| 4 | Premium tamper-resistance | ✅ Hardened | Server verification in Phase 3 |
| 5 | Backup extraction | ✅ Disabled | None |
| 6 | Exported components | ✅ Minimal | None |
| 7 | Cleartext / network | ✅ Default-deny | None |
| 8 | WebView (legal viewers) | ✅ JS off, local-only | None |
| 9 | PendingIntent flags | ✅ FLAG_IMMUTABLE | None |
| 10 | Debug logs leaking answers | ✅ Fixed | Release strips `Log.d/v/i/w`; one `Log.e` redacted |
| 11 | R8/ProGuard | ✅ On | Optional: `shrinkResources` |
| 12 | DB encryption | ⚠️ Plain (low risk) | Reconsider if PII grows (Phase 2) |

## Conclusion
**No security blocker for internal QA or closed beta.** The one release-time dependency is the owner
supplying the real signing `keystore.properties` (kept out of git). No secret was ever committed; the repo
is clean. `.idea`/personal local config was not touched.
