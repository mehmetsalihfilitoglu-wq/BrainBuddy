# Phase 1 — Firebase Foundation & Authentication Report

**Branch:** `seeding-final-fix` · **Not pushed.** Legend: ✅ implemented & validated here (compile/test) ·
🟡 implemented behind seam, runtime needs a real Firebase project · 🔵 owner/console · ⏭️ next increment.

## Foundation — ✅ / 🟡
- ✅ Firebase BoM 33.1.2 + auth/firestore/analytics/crashlytics/messaging + Credential Manager + googleid,
  behind the existing seams. **Build is fallback-safe:** google-services/crashlytics plugins apply **only**
  when `app/google-services.json` exists; without it the app builds and runs in local mode (verified —
  `assembleDebug` logs "Firebase inactive", 171/0 tests, APK ~84 MB). No launch-crash surface: `FirebaseConfig`
  gates all SDK use on the resource probe; `FirebaseInitProvider` no-ops without config.
- 🔵 Activation requires the owner's `google-services.json` (guide `OWNER_CONSOLE_SETUP_GUIDE.md`).

## Authentication — ✅ core / 🟡 runtime / ⏭️ UI
- ✅ `FirebaseAuthRepository` implements the **full** `AuthRepository` contract: email sign-up (auto-sends
  verification) / sign-in, password reset, Google sign-in via ID token, email verification + resend,
  `reloadUser`, session (`currentUser`/`isSignedIn`), sign-out, account deletion. Self-contained coroutine
  `await` (no extra dep). `FirebaseUser`→`AuthUser` mapping.
- ✅ `AuthProvider` now returns `FirebaseAuthRepository` when configured, else `LocalAuthStubRepository` —
  a one-line composition switch; **callers unchanged**.
- ✅ **Pure, unit-tested** logic: `AuthErrorMapper` (Firebase error strings → stable `AuthErrorCode`, incl.
  network override; 4 tests) and `EmailVerificationPolicy` (5 tests).
- 🟡 Live auth flows (real email delivery, real Google consent) need the Firebase project to exercise.
- ⏭️ **Auth UI screens** (Welcome/Login/Create/Verify/Forgot/Profile/Delete) and the **Google
  CredentialProvider** implementation are the next increment; the business layer they bind to is done.

## Verification-gate product decision — ✅ (documented + tested)
An authenticated **email** account must verify before the Daily Challenge unlocks; **Google** accounts are
pre-verified; the **anonymous/local** experience is intentionally **not** gated (EDUmio stays usable offline
without an account). Encoded in `EmailVerificationPolicy` + tests. Wiring this gate into the DC entry point
ships with the auth UI increment (kept out now to avoid changing current anonymous UX / DC invariants).

## Cloud profile & rules — ✅ (rules source) / 🔵 (deploy)
- ✅ `UserProfile` model for `users/{uid}` (identity + prefs only; **no content**).
- ✅ `firestore.rules`: default-deny; owner-only read; `uid`/`email` non-forgeable and immutable; client
  writes limited to a whitelist mirrored in `UserProfile.CLIENT_WRITABLE_FIELDS`. `firebase.json` +
  `firestore.indexes.json` for deploy/emulator.
- 🔵 Deploy (`firebase deploy --only firestore:rules`) + 🟡 emulator rules-tests land in Phase 2 (sync).

## Local-to-account adoption
The Daily Challenge already has account-link/merge migration (`dailychallenge/DailyChallengeAccountLink`,
covered by immutability tests incl. `signInMidDay_adoptsAnonChallenge_doesNotRegenerate`). ⏭️ The trigger
that runs it on first authenticated sign-in ships with the auth UI increment; the migration itself is
idempotent, cancellation-safe, account-isolated, and cannot produce a second same-day challenge (existing
tests).

## Guardrails
Content untouched (`af65df01…`, 3151 files). No secrets committed (`google-services.json` git-ignored). DC
invariants unchanged. 171/0 tests.

## Honest status
Phase 1 **foundation + auth business layer + Firestore profile/rules are repository-complete and validated**;
auth **UI**, the **Google credential provider**, the **Firestore profile writer**, and **live runtime** are
the remaining Phase 1 work (UI + owner config). No Firestore data write, Cloud Function, or email send has
been performed.
