# Phase 0 — Firebase Readiness Report

**Date:** 2026-07-19 · Branch `seeding-final-fix` · Scope: verify every backend seam still resolves to a
**local, no-network default** and that Phase 0 did **not** begin any Firebase work. Per the directive,
Phase 0 must leave the app fully offline-capable and Firebase-*ready* but not Firebase-*wired*.

## No Firebase is present (verified)
- **No `google-services.json`** anywhere in the repo (outside build output).
- **No Firebase Gradle plugin or dependency** in `settings.gradle.kts`, root or app `build.gradle.kts`
  (no `com.google.gms`, `firebase-*`, or `crashlytics`).
- **No Firebase SDK symbol** referenced in `main` source. The `isFirebaseConfigured` probe is
  **resource-based** (`resources.getIdentifier("google_app_id", ...)`), so nothing imports a Firebase class
  until the SDK is actually added.

## Seam-by-seam status (all LOCAL today)

| Seam | Interface | Active local impl | Sends off-device? | Switch point → Firebase |
|---|---|---|---|---|
| Auth | `AuthRepository` | `LocalAuthStubRepository` (PBKDF2, on-device) | No | `AuthProvider.buildRepository` (`isFirebaseConfigured`) → `FirebaseAuthRepository` |
| Credentials | `CredentialProvider` | `StubCredentialProvider` | No | `AuthProvider.credentialProvider` → Google/Apple provider |
| Sync | `SyncRepository` | `LocalMirrorSyncRepository` (on-device mirror) | No | `SyncProvider.build` → `FirestoreSyncRepository` |
| Analytics | `AnalyticsTracker` | `LogcatAnalyticsTracker` (Logcat only, no PII) | No | `AnalyticsProvider.tracker` → `FirebaseAnalyticsTracker` |
| Remote Config | `RemoteConfig` | `LocalRemoteConfig` (bundled defaults) | No | `RemoteConfig` provider → Firebase Remote Config |
| Entitlement | `EntitlementRepository` | `LocalEntitlementRepository` (reads hardened `PremiumStore`) | No | `EntitlementProvider` → server-verified entitlement |
| Billing | `BillingRepository` | `PlayBillingRepository` / `LocalBillingRepository` | Play only¹ | + Cloud Function purchase verification (Phase 3) |
| Email | `EmailDeliveryRepository` | `ShareIntentEmailDelivery` (Android share sheet, user-initiated) | No² | Cloud Function + ESP (Phase 4) |

¹ Google Play handles purchase/payment data; EDUmio's own servers receive nothing today.
² Email leaves only if the user picks a mail app in the share sheet; nothing is auto-sent.

**Every seam that could ever talk to Firebase currently returns a local implementation.** Two extra safety
facts: (a) `isFirebaseConfigured` returns `false` (no `google_app_id` resource), and (b) even its `true`
branch presently returns the local stub — the `FirebaseAuthRepository` / `FirestoreSyncRepository` lines are
commented placeholders — so mis-adding a config alone cannot silently start sending data before the adapters
are written and reviewed.

## Readiness checklist for Phase 1 (NOT done here — deferred by directive)
When Phase 1 begins (a separate, approved phase):
1. Add Firebase project + `google-services.json` + Gradle plugin + SDKs.
2. Implement `FirebaseAuthRepository`, `FirestoreSyncRepository`, `FirebaseAnalyticsTracker`, Remote Config
   adapter, and the purchase-verification Cloud Function — each behind its existing interface, flipped on
   only via the provider switch points above.
3. Update `CURRENT_DATA_PROCESSING_INVENTORY.md`, the privacy policy, and the Play Data Safety form in the
   same change (data now leaves the device).

## Conclusion
**The Firebase-ready seams are intact and 100% local.** No Auth, Firestore, Cloud Functions, Analytics,
Crashlytics, Remote Config backend, live email, or server billing verification was started in Phase 0 — as
required. The architecture can adopt Firebase later as a localized, provider-level change.
