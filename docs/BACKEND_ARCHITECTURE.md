# Mioitalia — Backend Architecture & Contract

**Status:** the Android client is *backend-ready*. Every server responsibility lives
behind a client interface with a working on-device implementation, so the app builds,
runs, and is fully usable offline **today**. Going live is a matter of provisioning the
backend and swapping each `*Provider` from its local implementation to a Firebase one —
**no business-logic or UI rewrite**.

Chosen platform: **Firebase** (managed, covers auth + sync + messaging + config +
functions + analytics). Target: **Android / Google Play first**; Apple Sign-In and the
App Store are a later iOS phase — the auth layer is already provider-agnostic for it.

---

## 1. Client seams (already built)

| Concern | Contract (interface) | Local impl (today) | Firebase impl (later) |
|---|---|---|---|
| Authentication | `auth.AuthRepository` | `LocalAuthStubRepository` (PBKDF2, sessions) | `FirebaseAuthRepository` |
| Federated tokens | `auth.CredentialProvider` | `StubCredentialProvider` | `GoogleCredentialProvider` (Credential Manager) |
| Cloud sync | `sync.SyncRepository` | `LocalMirrorSyncRepository` | `FirestoreSyncRepository` |
| Remote config | `remote.RemoteConfig` | `LocalRemoteConfig` (defaults) | `FirebaseRemoteConfigAdapter` |
| Premium entitlement | `billing.EntitlementRepository` | `LocalEntitlementRepository` | `PlayBillingEntitlementRepository` |
| Report delivery | `report.ReportDeliveryService` | `LocalReportDeliveryService` (manual share) | `CloudReportDeliveryService` (Functions + email) |
| CMS content | `content.ContentGateway` | `LocalContentGateway` (empty) | `RemoteContentGateway` (Firestore/CMS) |

Each has a single composition root: `AuthProvider`, `SyncProvider`, `RemoteConfigProvider`,
`EntitlementProvider`, `ReportDeliveryProvider`, `ContentProvider`. Flip one line in each to go live.

---

## 2. Auth (Firebase Auth)

- Providers: **Email/Password**, **Google** (now), **Apple** (with iOS). Email verification
  and password reset are Firebase-native.
- Client maps `FirebaseUser → auth.AuthUser`; callers never see a Firebase type.
- `AuthProvider.isFirebaseConfigured()` already flips to Firebase when `google_app_id`
  (from `google-services.json`) is present.
- Sessions: Firebase ID tokens (auto-refresh); `deleteAccount()` → `FirebaseUser.delete()`
  + a Cloud Function that erases the user's Firestore data (GDPR).

## 3. Cloud sync (Cloud Firestore)

Per-user, per-store documents; **last-write-wins by `updatedAt`**; study areas isolated by
embedding `areaId` in the document id.

```
users/{uid}/sync/{documentId}
  documentId = "global__<storeKey>" | "area__<areaId>__<storeKey>"
  fields: { scope, areaId, storeKey, updatedAt (server ts), schemaVersion, payload }
```

- `storeKey`s and scoping come from `sync.SyncRegistry` (client is source of truth for the shape).
- `pull(since)` → `where updatedAt > cursor`; `push` → batched `set(merge)` honouring `updatedAt`.
- Client `SyncEngine` already implements collect → push-dirty → pull → apply (GLOBAL first).
- **Security rules:** a user may read/write only under their own `users/{uid}`.
- Enable by setting Remote Config `cloud_sync_enabled = true` and scheduling `SyncWorker`.

## 4. Notifications (FCM) + smart content

- Device registers an FCM token under `users/{uid}/devices/{token}`.
- Server-side smart notifications reuse the same real-data signals the client computes
  (`SmartNotifications`): due-review count, streak proximity, weakest topic, readiness change.
- Client already builds specific, real-data messages; FCM lets them fire server-side even when
  the app is closed.

## 5. Weekly & monthly reports (Cloud Functions + email)

- Scheduled Function (Sunday evening / month-end) reads the user's synced stores, runs the same
  report model as `report.ReportGenerator`, renders the HTML email, and calls an email provider.
- Client contract: `ReportDeliveryService.deliver(ReportPayload)`. Local impl queues for manual
  share; cloud impl returns `Sent`.
- Gated by Remote Config `weekly_reports_enabled` / `monthly_reports_enabled`.

## 6. Premium verification (Play Billing + Functions)

- Play Billing purchase → client sends the purchase token to a `verifyPurchase` Function →
  validated against Google Play Developer API → entitlement written to `users/{uid}/entitlement`.
- `EntitlementRepository.refresh()` reads that; `PremiumStore` stays the fast local cache.
- Never trust client-only premium state for server-gated features.

## 7. Admin Panel & CMS (Firestore collections)

Managed remotely, no app update needed:

```
content/questions/{studyArea}/{questionId}
content/universities/{id}         content/programs/{id}
content/scholarships/{id}         content/news/{id}
content/announcements/{id}        content/premiumBanners/{id}
```

- Client reads via `content.ContentGateway`; questions/universities keep bundled data as the
  offline fallback and are refreshed through `refreshQuestions()` / `refreshUniversities()`.
- Admin Panel is a separate web app writing these collections (Firebase Auth admin claims).

## 8. Analytics & backups

- Firebase Analytics for product metrics (client events; no PII beyond uid).
- Firestore scheduled export to Cloud Storage for backups.

---

## 9. Go-live checklist

1. Create the Firebase project; add `google-services.json` + the Google Services Gradle plugin.
2. Add SDKs: Firebase Auth, Firestore, Messaging, Remote Config, Analytics; Credential Manager; Play Billing.
3. Implement the seven Firebase adapters (table §1) against the existing interfaces.
4. Return them from the `*Provider` composition roots.
5. Deploy Firestore security rules (per-user isolation) + the Cloud Functions (reports, purchase verify, GDPR delete).
6. Set Remote Config defaults; turn on `cloud_sync_enabled`; schedule `SyncWorker.schedulePeriodic()`.
7. QA cross-device sync, purchase verification, report email, account deletion/export.

> The only product component still outside this architecture is the **real exam question bank**,
> which plugs into `content/questions/...` and the existing quiz pipeline when it arrives.
