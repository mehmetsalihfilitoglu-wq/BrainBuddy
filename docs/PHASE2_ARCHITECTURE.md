# EDUmio — Phase 2 Architecture & Implementation Roadmap

**Date:** 2026-07-19 · Branch `seeding-final-fix` · Status: **DESIGN — awaiting approval before any
implementation.** Content layer (question banks, 4124 solutions, Daily Challenge content) is **FROZEN**.

This is the architecture for the next several years, grounded in a full audit of the current code. It is a
plan, not a change: no production code is modified by this document.

---

## 0. Executive summary

EDUmio was deliberately built **backend-ready**: every Phase-2 system already exists as a
provider-agnostic **interface + on-device implementation + composition-root provider** (the `AuthProvider`
/ `SyncProvider` / `BillingProvider` / `RemoteConfigProvider` pattern), plus an existing design doc
`docs/BACKEND_ARCHITECTURE.md`. **No Firebase is wired yet** (no `google-services.json`, no Firebase
dependencies). Play Billing v7 is already a real adapter, not a stub.

So Phase 2 is **not** a rewrite. It is: (1) implement the cloud half of contracts that already exist,
(2) make **entitlement and anti-cheat state server-authoritative**, (3) unify a few duplicated seams,
(4) clear the concrete **Play Store release blockers**. The single most important architectural theme is
**"server-authoritative where trust matters (premium, streak), client-authoritative where UX matters
(offline generation)"**.

### The whole plan on one page

| # | System | Exists (seam) | Core Phase-2 work | Needs Firebase/server |
|---|---|---|---|---|
| 1 | Auth | `AuthRepository` + `LocalAuthStubRepository` + `CredentialProvider` | `FirebaseAuthRepository`, Google `CredentialProvider`, verify/reset/delete/re-auth, session listener | Firebase Auth |
| 2 | Cloud backend / Sync | `sync/*` engine + `SyncRepository` + `LocalMirrorSyncRepository` | `FirestoreSyncRepository`, bring DC Room DB into sync, server-write-only nodes, rules | Firestore + rules |
| 3 | Daily Challenge engine | frozen single-immutable model + `DailyChallengeSync` snapshot | server-per-day authority + row sync metadata + server clock | Firestore/Functions |
| 4 | Premium | **real** `PlayBillingRepository` + `EntitlementRepository` | `verifyPurchase` Fn, RTDN handler, expiry/grace, server entitlement, signed local cache | Functions + Play API |
| 5 | Notifications | 3 WorkManager subsystems | one `NotificationOrchestrator` + `NotifOutbox`, quiet hours, caps, FCM for win-back | FCM (push only) |
| 6 | Email | `ReportDeliveryService` + PDF + share-intent | server email (transactional + marketing), consent, unsubscribe, suppression | Functions + ESP |
| 7 | Analytics | `AnalyticsTracker` + `DailyChallengeAnalytics` + taxonomy | Firebase Analytics + Crashlytics + Perf behind existing sinks; funnels; consent gate | Firebase |
| 8 | Remote Config | `RemoteConfig` + `LocalRemoteConfig` + 34 keys | `FirebaseRemoteConfigAdapter`, fetch/activate, kill-switch gate, A/B bucket | Firebase RC |
| 9 | Security | rules design + `DataRightsService` + local crypto | Firestore rules, server verify, replay dedupe, signed offline caches, cloud erase | Firestore + Functions |
| 10 | Production readiness | 2 Room DBs, legal seam, GDPR export | **release blockers**: legal URLs, signing, Data Safety, AdMob truth, `exportSchema` | — (mostly local) |

---

## 1. Global architecture principles (decide these first — they bind every system)

1. **Server-authoritative for trust, client-authoritative for UX.** Premium entitlement, purchase state,
   and (recommended) streak/XP are **server-owned, client-pull-only**. Daily-Challenge *generation* stays
   client-side (offline-first) but the server is authoritative **per (userId, localDate)** on first push.
2. **Firebase behind the existing seams.** Every Firebase SDK stays behind its interface
   (`FirebaseAuthRepository : AuthRepository`, `FirestoreSyncRepository : SyncRepository`, …). Business
   code never imports Firebase. The `isFirebaseConfigured()` resource-probe (already in `AuthProvider`)
   lets builds without `google-services.json` keep the local impls — preserving pure-JVM testability.
3. **Content is frozen.** Question banks, the 4124 solutions, blueprint proportions, and Daily-Challenge
   content are never synced as user data and never regenerated. `content/**` is read-only.
4. **Offline-first everywhere.** Every online call has a local fallback and fails safe (unknown
   entitlement → Free; no network → serve cache, queue sync).
5. **Fresh-install posture.** Package was renamed `com.brainbuddy → com.edumio`; there are **no
   production users**. This is the last moment to freeze schemas and choose account/migration semantics
   cheaply — do it before public launch.
6. **Idempotency by construction.** Deterministic keys already exist (`challengeId = userId:localDate`,
   purchase token, `SyncRecord.documentId`); server ingestion must upsert on these, never duplicate.

---

## 2. Consolidated FREEZE list (must NOT change)

Derived from every system's audit. Changing any of these breaks existing data, tests, or the immutable
model.

- **Content:** all seed assets (`imat/`, `edumio_original/`, `til_i/`, `cents_s/`, K-12/LGS), the 4124
  solution overlays, blueprint proportions. Content-version RC keys may trigger a *check*, never a mutation.
- **Daily Challenge invariants:** exactly one challenge per `(userId, localDate)` cross-exam; `challengeId`;
  `questionIdsCsv` + order immutable once written; exactly-5-NEW + retire-after-first-exposure; fail-closed
  undersupply; exam-agnostic answer key; anon→account non-clobbering idempotent merge (writes challenge row
  last, clears anon pointer only on success).
- **Interface seams + DTOs:** `AuthRepository`/`AuthResult`/`OpResult`/`AuthErrorCode`/`AuthProviderType`
  string values; `BillingRepository`/`EntitlementRepository` + value types; product ids
  `edumio_premium_monthly`/`_yearly` (must byte-match Play Console); `SyncRepository` contract +
  `SyncRecord.firestorePath(uid)` + `SyncRegistry` remote paths; `RemoteConfig` method shapes +
  `RemoteConfigKeys` string values + defaults map; `DataRightsService` contract + its honest
  `LocalDoneCloudPending`; `RemoteConfigProvider`/`AuthProvider`/`SyncProvider`/`BillingProvider` single-swap
  composition roots.
- **Analytics taxonomy:** `dc_*`, `sol_*`, `wp_*` event/param name strings (dashboards key off them) —
  **extend, never rename**; the privacy invariant (IDs only, never stems/options/solution bodies/PII).
- **Data layer:** the additive `edumio.db` migration chain 11→24 (never edit shipped migrations; only append
  ≥25); the two-DB split (`edumio.db` content vs `daily_challenge.db` state); seed `*_seed_version` meta keys;
  release has **no** destructive fallback on `edumio.db`.
- **Honesty invariants:** never fake a charge (`purchase()`→`Unavailable`), PENDING grants nothing,
  acknowledge required; premium widens **review depth only** — never the 5-new-per-day count.

---

## 3. Cross-cutting foundation (do once, unblocks systems 1–9)

**FND — Firebase project + build wiring.**
- Create Firebase project(s) (`edumio-prod`, `edumio-dev`), download `google-services.json` (gitignored;
  CI-injected). Add Gradle: `com.google.gms.google-services` plugin + `firebase-bom` + per-module
  dependencies (auth, firestore, analytics, crashlytics, perf, config, messaging) added *only as each
  system lands*.
- Keep the `isFirebaseConfigured()` probe so a missing `google-services.json` falls back to local impls
  (protects pure-JVM CI + open-source builds).
- Cloud Functions project (`functions/`, TypeScript): scaffold for `verifyPurchase`, `onRtdnNotification`,
  `sendEmail`, `onAccountDelete`. Firestore + rules + emulator config committed.
- **Testing:** builds must pass with AND without `google-services.json`. Firebase emulator suite
  (auth, firestore, functions) for integration tests; rules-unit-testing for security rules.

---

## 4. Per-system architecture

Each system: **Exists → Missing → Architecture → Data model → Dependencies → Risks → Migration → Order →
Testing → Key decisions.**

### 4.1 Authentication (Firebase)

- **Exists:** full provider-agnostic `AuthRepository` (session, email/pw, federated via `signInWithProvider`,
  email-verify, GDPR `deleteAccount`); real `LocalAuthStubRepository` (PBKDF2 120k, SecureRandom salt,
  constant-time compare); `AuthProvider` composition root; `CredentialProvider` SDK seam (empty by default);
  `AuthActivity` (email/pw + forgot-password; Google button conditionally rendered); anon→account merge.
- **Missing:** `FirebaseAuthRepository`, `GoogleCredentialProvider` (Google Sign-In is inert end-to-end);
  email-verification enforcement (`reloadUser()` never called); re-auth flow for `REQUIRES_RECENT_LOGIN`;
  session-restore/`AuthStateListener`; custom-claims read (`getIdTokenResult`); `updatePassword/Email`,
  `linkWithCredential`.
- **Architecture:** `FirebaseAuthRepository : AuthRepository` maps `FirebaseUser`→`AuthUser`
  (`uid→userId`, `providerId→AuthProviderType`). `GoogleCredentialProvider : CredentialProvider` uses
  Credential Manager to return a Google ID token → `signInWithProvider`. `AuthStateListener` registered in
  `EDUmioApp.onCreate` restores session + refreshes claims. Add a `reAuthenticate()` capability for
  delete/change-password. Entitlement claims (`plan`, `entitlementExpiry`) read via `getIdTokenResult` and
  force-refreshed (`getIdToken(true)`) after a purchase.
- **Data model:** Firebase Auth user (uid PK, email, emailVerified, providerData[]) is identity SoT; custom
  claims `{ plan, entitlementExpiry, role }` set server-side (Admin SDK), server-authoritative.
- **Dependencies:** FND; `firebase-auth`, Credential Manager / Google Identity.
- **Risks:** userId cutover (local `local_*` → Firebase uid) re-keys sync cursor + DC state; verification
  enforcement flip changes behavior; recent-login requirement on delete; token-refresh races with entitlement.
- **Migration:** ship `FirebaseAuthRepository` behind the probe; one-time re-key of any existing anon/local
  ids to the Firebase uid via the existing `DailyChallengeAccountLink` merge extended to the sync cursor.
- **Order:** after FND; before Sync (Sync needs a stable uid) and before server entitlement (needs claims).
- **Testing:** contract tests reused for both impls (a `FakeAuthRepository`); emulator integration for
  verify/reset/delete/re-auth; assert anon→account merge still idempotent.
- **Key decisions:** (a) anonymous identity — keep app-level anon id + copy-migrate (current, works) vs
  Firebase `signInAnonymously()` + `linkWithCredential` (uid stable across upgrade); (b) entitlement via
  custom claims vs Firestore doc; (c) email-verification enforcement policy (gate sync/premium or not);
  (d) who owns the one-time id re-key.

### 4.2 Cloud Backend (Firestore) + Sync

- **Exists:** complete backend-agnostic sync engine (`SyncEngine` content-hash dirty detection, `SyncManager`,
  `SyncWorker` 6h periodic + CONNECTED, `SyncStateStore` per-user cursor, `SyncRegistry` = 21 prefs stores
  with Firestore paths, `PrefsSnapshotCodec` lossless typed JSON, `SyncRecord` envelope +
  `firestorePath(uid)`); `SyncRepository` contract + `LocalMirrorSyncRepository`; a **separate** Room-based
  DC snapshot path (`DailyChallengeSync`); `docs/BACKEND_ARCHITECTURE.md` §3 document tree.
- **Missing:** `FirestoreSyncRepository` (the entire cloud half); **the DC Room DB is not registered in
  `SyncRegistry`** (biggest gap — challenge/answers/state/streak don't sync via the generic path);
  `SyncWorker.schedulePeriodic()` has zero callers; no real server clock (`serverTimeMs` returns local
  time); no connectivity gate; no Firestore rules committed; no FCM device registration; dual source of
  truth (wrong-question data in prefs AND Room).
- **Architecture:** `FirestoreSyncRepository : SyncRepository` — `push` writes `SyncRecord`s under
  `users/{uid}/…` with `updatedAt = FieldValue.serverTimestamp()`; `pull(since)` queries `updatedAt >
  cursor`. Bring the DC DB into sync as **one GLOBAL `SyncRecord`** carrying the `DailyChallengeSyncCodec`
  payload (reuses existing engine, simplest) — later upgrade high-value stores to field-level merge. Add a
  connectivity check + `Skipped/reschedule`. Wire `schedulePeriodic()` at startup + `syncOnce` after sign-in
  and after challenge completion.
- **Data model:** UID-rooted Firestore. `users/{uid}/meta,profile,settings,onboarding,notificationPrefs,
  reportPrefs,questionHistory,favorites,…` (one doc = one GLOBAL `SyncRecord`); `users/{uid}/premium,
  purchases` **server-write-only** (client pull-only); `users/{uid}/devices/{fcmToken}`;
  `users/{uid}/areas/{areaId}/{analytics|gamification|mastery|wrongQuestions|…}` (area-isolated); DC under
  `users/{uid}/challenges/{localDate}` + `.../answers/{questionId}` + `users/{uid}/questionState/{qid}`.
- **Dependencies:** FND, Auth (stable uid).
- **Risks:** dual source of truth (prefs vs Room) must be reconciled; whole-store-replace LWW can lose
  monotonic data (history, seen-sets, streak) — needs union/max for those; client-stamped time must move to
  server time; premium in the client-writable push set is a security hole (remove).
- **Migration:** implement adapter behind probe; first sync is additive (pull-then-merge, LWW by server
  time); DC brought in as one record initially. No destructive change to local stores.
- **Order:** after Auth; parallel with Premium server work; before Security rules finalize.
- **Testing:** emulator round-trip (push→pull equals), conflict cases (two devices), cursor monotonicity,
  DC snapshot reinstall-restore (existing test extended to Firestore), rules deny cross-user.
- **Key decisions:** (a) how to bring DC into sync — one GLOBAL record (fast) vs subcollection docs
  (granular, multi-device correct); (b) LWW vs field-level merge for high-value stores; (c) which nodes are
  server-write-only (premium, and arguably streak/XP); (d) adopt `serverTimestamp()` as the sole LWW clock.

### 4.3 Daily Challenge Engine (server-ready)

- **Exists:** the frozen single-immutable model, fully offline + tz-aware; `getOrCreateToday()` idempotent
  under a process Mutex; retire-after-exposure; fail-closed undersupply; full-account export/restore +
  non-clobbering merge; `DailyChallengeDates` (Calendar-based, DST-aware).
- **Missing:** live transport (export/restore have zero prod callers); server-time/timezone authority
  (uses device clock — vulnerable to clock changes for streak farming); multi-device same-day conflict
  resolution; row-level sync metadata (`updatedAt/deviceId/revision`); tombstones/GDPR wipe through the
  snapshot.
- **Architecture:** keep client generation (offline UX) but make the **server authoritative per day**:
  first device to push `users/{uid}/challenges/{localDate}` wins (uniqueness enforced by rules/Function);
  a second device that generated a different set for the same day **adopts the server's** on next pull and
  re-homes its answers by `questionId`. Adopt `SyncRepository.serverTimeMs()` as the day-boundary authority;
  device tz becomes a display hint; gate generation against implausible device clocks. Add
  `updatedAt/deviceId` to rows for pull-merge.
- **Data model:** `users/{uid}/challenges/{localDate}` (unique) + `.../answers/{questionId}` +
  `users/{uid}/questionState/{qid}` (retirement/review/mastery, LWW) + deficits.
- **Dependencies:** Sync (transport), Auth (uid), server time.
- **Risks:** streak integrity if device clock is trusted; multi-device generating divergent day-D sets;
  the anon-link merge policy must NOT be reused for multi-device (different semantics).
- **Migration:** additive row metadata (nullable columns → `daily_challenge.db` v3, still destructive
  pre-launch); wire the snapshot into Sync; server uniqueness on `(uid, localDate)`.
- **Order:** after Sync; server uniqueness with Firestore rules.
- **Testing:** existing immutability suite (18 tests) extended for server-per-day first-writer-wins,
  answer re-homing, clock-tamper rejection; keep pure-JVM where possible.
- **Key decisions:** (a) client-generate-server-reconcile (recommended) vs server-assign; (b) first-writer-
  wins-per-day + deterministic answer re-homing; (c) server time as boundary authority; (d) freeze the key +
  ids + questionIdsCsv + retire-once, allow only status/answers/score/streak/review-state to mutate.

### 4.4 Premium (Play Billing + entitlement)

- **Exists:** real Play Billing v7 adapter (`PlayBillingRepository`: product details, launch, purchases,
  acknowledge); `BillingRepository`/`EntitlementRepository` contracts + value types (`SubscriptionState`
  incl. `IN_GRACE_PERIOD/ON_HOLD/PAUSED/CANCELLED`); `PremiumStore` fast boolean cache;
  `DailyChallengeEntitlement` fail-safe gate; `PremiumPaywallSheet` (RemoteConfig pricing/copy);
  documented `verifyPurchase` design.
- **Missing:** **server-side purchase verification** (`verifyPurchase` Cloud Function absent — client
  optimistically grants unverified); no server entitlement SoT (`EntitlementProvider` always local); no real
  expiry (optimistic grant `expiresAtMs=null` never expires); no grace/hold/pause code paths; no RTDN/Pub-Sub
  handler; plaintext `PremiumStore`/`SubscriptionStore` (no HMAC); purchase not bound to account
  (`obfuscatedAccountId` unset); pending-purchase not reconciled on launch; refresh not wired to lifecycle.
- **Architecture:** `verifyPurchase(uid, packageName, productId, purchaseToken)` Cloud Function →
  Play Developer API `subscriptionsV2.get` → writes `users/{uid}/entitlement` (server-write-only) +
  server-acknowledges; `onRtdnNotification` (Pub/Sub) mutates entitlement on renew/cancel/refund/grace/hold.
  Client: `PlayBillingEntitlementRepository` reads the synced `entitlement` doc (SoT) with `PremiumStore` as
  an **HMAC-signed, TTL-bounded** offline cache. Bind purchases with `obfuscatedAccountId = uid`. Reconcile
  `queryPurchasesAsync` on launch. Collapse the two caches (`PremiumStore` boolean vs `SubscriptionStore`
  JSON) behind the entitlement result.
- **Data model:** `users/{uid}/entitlement` (account-level, GLOBAL — premium is cross-exam) `{isPremium,
  tier, plan, productId, state, startMs, expiryMs, autoRenewing, latestOrderId, purchaseTokenHash, source,
  verifiedAtMs}`; `users/{uid}/purchases/{purchaseToken}` (token = doc id → natural replay-dedupe).
- **Dependencies:** Auth (uid + claims), Sync (pull entitlement), Functions, Play Developer API creds, RTDN
  Pub/Sub topic.
- **Risks:** trusting client `isPremium`; optimistic grant never revoked (cancel/refund); acknowledge
  failure still granting (unacknowledged → auto-refund in 3 days); replay of a purchase token; offline
  entitlement forgery.
- **Migration:** ship signed local caches + `Purchase.signature` verification first (no backend); then
  `verifyPurchase` + entitlement doc becomes SoT; flip premium/subscription to server-write-only (rules).
- **Order:** after Auth + Sync; needs Functions.
- **Testing:** `verifyPurchase` with Play API sandbox; RTDN replay; expiry/grace transitions; rules deny
  client writes to entitlement; offline cache HMAC tamper rejected; "premium widens review only" invariant.
- **Key decisions:** (a) account-level server entitlement doc verified via Play API (recommended); (b)
  **server-write-only** premium/subscription (the single most important security choice); (c) offline trust
  model (signed cache + bounded TTL + grace); (d) optimistic-grant policy (short TTL + mandatory reconcile).

### 4.5 Notifications engine

- **Exists:** three uncoordinated WorkManager subsystems — DC reminders (3 slots, prefs-based idempotency
  `completed_date`/`slot_fired_N`), Motivation (daily/streak, real-data gated via `SmartNotifications`),
  Reports (weekly/monthly → local "ready" notif); `NotificationPermissionPolicy` (contextual ask, pure);
  `NotificationPrefs`; DC analytics (`REMINDER_SHOWN/SUPPRESSED`).
- **Missing:** FCM/push entirely; a **single orchestration gate**; quiet hours; global frequency cap/daily
  budget (a user could get 5+/day today); re-engagement/win-back; premium-lifecycle notifications; deep-link
  content intents on DC/Motivation; `DAILY_REMINDER_HOUR` RemoteConfig key is unused.
- **Architecture:** one **`NotificationOrchestrator`** every type routes through — owns enabled-check,
  quiet-hours, frequency-cap, dedup, idempotency, analytics. Backed by a Room **`NotifOutbox`** (dedupKey
  unique, status PLANNED/POSTED/SUPPRESSED/OPENED) replacing scattered `slot_fired_N` prefs. One
  `NotificationChannels` object (channel groups, per-type importance). Local WorkManager covers
  daily/streak/review/report (no backend). **FCM only for server-initiated** win-back + premium-lifecycle +
  announcements (`FirebaseMessagingService` + token registration `users/{uid}/devices/{token}`). Wire
  RemoteConfig slot hours + caps.
- **Data model:** `NotifOutbox` (Room), `NotifSettings` (DataStore: quiet hours, per-type toggles, cap);
  `users/{uid}/devices/{fcmToken}`.
- **Dependencies:** RemoteConfig (hours/caps), Analytics (events), FCM (push subset), Auth (device reg).
- **Risks:** notification fatigue → uninstalls; per-study-area multiplication (multiple areas → multiplied
  nudges); idempotency across multi-device; POST_NOTIFICATIONS timing regressions.
- **Migration:** Phase 0 no-behavior-change facade (route existing workers through orchestrator, same
  times); Phase 1 add outbox + quiet hours + caps; Phase 2 add FCM + win-back.
- **Order:** local orchestrator early (no deps); FCM after Auth + Functions.
- **Testing:** pure policy tests (existing `DailyChallengeReminderPolicy`/`NotificationPermissionPolicy`
  frozen + extended); outbox dedup/cap/quiet-hours unit tests; idempotency across simulated multi-device.
- **Key decisions:** (a) one orchestrator + outbox (recommended) vs keep three; (b) idempotency authority
  (Room outbox vs prefs flags); (c) FCM vs local-only (local covers all but server-initiated); (d)
  cross-area (one nudge/day/account) vs per-area.

### 4.6 Email lifecycle

- **Exists:** `ReportDeliveryService` (real payload path) + `LocalReportDeliveryService` + provider; report
  content pipeline (real-data-only, honest empty state, PDF builders); `EmailReportPrefs`; auth
  transactional-email contract (`sendEmailVerification/sendPasswordReset` — local no-op); billing lifecycle
  signals; draft legal docs. A **second, dead** `EmailDeliveryRepository` seam.
- **Missing:** any transactional email backend; marketing-consent capture (no checkbox, no KVKK/GDPR lawful
  basis field); unsubscribe (no token, no one-click / RFC 8058 `List-Unsubscribe`); suppression list
  (bounces/complaints/unsubs); email event/audit log; email TYPES (daily digest, re-engagement, drip,
  monthly-never-sent, premium receipt/expiry); server templates; double-opt-in guard; schedulers never
  invoked at startup; idempotency/dedup key.
- **Architecture:** **Firebase-native** verification/reset (via Auth). **Cloud Function + ESP**
  (SendGrid or Firebase "Trigger Email") for everything else. Split by class: **transactional** (bypass
  marketing consent) vs **marketing** (consent + unsubscribe + suppression enforced **server-side** at send
  time). Send loop moves to **server** (Cloud Scheduler + Firestore/Auth/RTDN triggers) for suppression
  enforcement + dedup; client keeps report *content generation*. Idempotency: `(uid, emailType, periodKey)`
  dedup doc created transactionally before enqueue. Collapse the two delivery seams onto `ReportDeliveryService`.
- **Data model:** `users/{uid}/emailPrefs {marketingConsent, consentSource, consentAtMs, consentPolicyVersion,
  locale, categories{reports,educational,product,promo}}`; `emailTokens/{token}` (verify/unsubscribe,
  capability-based); global `suppression/{emailHash}` (bounce/complaint/unsub); `emailLog` (queued/sent/
  delivered/opened/bounced/complained).
- **Dependencies:** Auth (verified email), Functions, ESP account, Sync (prefs).
- **Risks:** sending to unverified/typo addresses; consent/unsubscribe legal exposure (KVKK + GDPR);
  deliverability (SPF/DKIM/DMARC); double-send from WorkManager retries + multi-device; suppression must be
  server-enforced (never trust client).
- **Migration:** wire transactional (verify/reset) via Firebase first (needed by Auth anyway); then reports
  via `CloudReportDeliveryService`; then marketing with consent + unsubscribe + suppression.
- **Order:** transactional with Auth; reports after Sync; marketing last (consent UI + server).
- **Testing:** Function unit + emulator; suppression enforcement (suppressed → not sent); idempotency dedup;
  consent gating; RFC 8058 unsubscribe token round-trip.
- **Key decisions:** (a) transactional (Firebase) vs marketing (Fn+ESP) split + classification of each type;
  (b) send loop location (server-scheduled recommended); (c) consent + suppression SoT + server-only
  enforcement; (d) idempotency `periodKey` granularity per type.

### 4.7 Analytics + Crashlytics + Performance

- **Exists:** two interface+local+provider seams (`AnalyticsTracker`/`AnalyticsEvents` general taxonomy ~40
  events; `DailyChallengeAnalytics`/`DcEvents` `dc_*/sol_*/wp_*` — **actually wired and emitting**); a third
  legacy Logcat-only `WrongReviewAnalytics`; local `AnalyticsStore` (progress metrics, separate concern);
  local-only crash handler (`CrashActivity`); `RecordingAnalytics` test double.
- **Missing:** all Firebase (no analytics/crashlytics/perf); non-fatal `recordException` seam; perf traces;
  the onboarding→first-challenge→premium **funnel** is largely un-instrumented; **no `premium_purchased`
  event**; two disjoint taxonomies with overlap; no consent/collection gate; no retention/DAU design.
- **Architecture:** one `FirebaseAnalytics` behind the existing sinks (route all three through it — no
  call-site churn beyond wiring gaps). Crashlytics **chains** with the existing handler (report → then
  `CrashActivity` → kill). Firebase Performance for a **minimal** trace set (cold_start, db_seed_pipeline,
  dc_generate, sync, billing) — not blanket auto-tracing. Set user properties (exam focus, study area,
  premium state) centrally. A consent gate binds `setAnalyticsCollectionEnabled` to the GDPR/DataRights flow
  + a RemoteConfig kill-switch.
- **Data model:** flat non-PII event maps (existing shape); funnel + retention families defined in an
  event-schema doc; user properties for segmentation.
- **Dependencies:** FND; RemoteConfig (kill-switch); DataRights (consent).
- **Risks:** PII/solution-text leakage (freeze the privacy invariant); double-counting across two
  taxonomies; consent for EU+TR audience; perf trace cost on cold start.
- **Migration:** doc + freeze names → add deps → route sinks to Firebase → emit the missing funnel/purchase
  events → add Crashlytics chain + minimal perf traces.
- **Order:** early (low risk, high value); Crashlytics ASAP for release stability.
- **Testing:** `RecordingAnalytics` + a new `RecordingAnalyticsTracker` assert each flow emits exact events;
  no-PII assertion test over param values (extend existing `analytics_neverCarryContent`).
- **Key decisions:** (a) route both taxonomies into one Firebase instance vs converge names (route,
  recommended); (b) Crashlytics replace vs chain the local handler (chain); (c) opt-in vs opt-out consent
  (opt-out with kill-switch for EU+TR); (d) perf trace scope (minimal set).

### 4.8 Remote Config

- **Exists:** `RemoteConfig` interface (typed getters + `suspend refresh`), `RemoteConfigKeys` (34 keys +
  defaults), `LocalRemoteConfig`, `RemoteConfigProvider` singleton; **real live consumers** (billing pricing/
  copy, paywall yearly toggle, active exam types).
- **Missing:** Firebase adapter/dep; `refresh()` never called (no cadence); kill switches (`MAINTENANCE_MODE`,
  `MIN_SUPPORTED_VERSION_CODE`) unenforced; 20+ declared-only dead flags; no A/B bucket; no announcement-banner
  UI; no reactive re-render; no tests / QA override.
- **Architecture:** `FirebaseRemoteConfigAdapter : RemoteConfig` — `setDefaultsAsync(RemoteConfigKeys.
  defaults)`, `fetchAndActivate` on launch (serve cached instantly) + background fetch ~1h (values apply next
  launch — accept one-launch staleness). A single **launch-time kill-switch gate** (Application/first
  Activity) checks `maintenance_mode` + `min_supported_version_code` before content. Client-side stable
  random **A/B bucket** (persisted, set as Analytics user property) drives RC conditions. `RemoteConfigProvider`
  gets an install/rebind hook.
- **Data model:** Firebase RC params 1:1 with `RemoteConfigKeys` (booleans/numbers→Long/strings); console
  conditions on `appVersionCode`, `ab_bucket` user property, country/language (TR-first).
- **Dependencies:** FND; Analytics (bucket property, exposure events).
- **Risks:** a bad flag bricking the app (fail-safe defaults + staged rollout); price/exam defaults must
  match Play + content; kill-switch UX (hard interstitial).
- **Migration:** adapter behind probe; `setDefaultsAsync`; wire kill-switch gate + A/B bucket; route
  content-version keys into the content check (never mutate bundles).
- **Order:** after FND; before pricing experiments + kill-switch reliance.
- **Testing:** `LocalRemoteConfig` default/fallback tests; adapter fake-delegate (unfetched→default, coerce,
  refresh maps result); kill-switch gate unit test.
- **Key decisions:** (a) fetch/activate cadence + staleness contract (activate-on-launch + 1h background);
  (b) fail-safe three-tier fallback via `setDefaultsAsync`; (c) A/B assignment (client stable bucket); (d)
  kill-switch placement (single launch-time gate, hard interstitial).

### 4.9 Security

- **Exists:** auth contract + real local crypto; billing seams; `PremiumStore` (plaintext boolean);
  `DataRightsService` (real on-device export/delete, honest `LocalDoneCloudPending`); `DataIntegrityChecker`
  (content integrity at startup); structural per-user path isolation; documented rules assumptions; **dead**
  Barjin scaffolding (`TamperStore`, `SystemHealthStore` — zero callers).
- **Missing:** server purchase verification; `Purchase.signature` validation; replay protection (token
  reuse); offline integrity of premium/streak (plaintext, unsigned); Firebase Auth; **Firestore security
  rules** (only prose); cloud account-deletion Function; (no anti-tamper/Play Integrity — decide if needed).
- **Architecture:** **Firestore rules** enforce per-user isolation (`users/{uid}/** ` readable/writable only
  by `request.auth.uid == uid`), `content/**` read-open/write-admin-only, and **entitlement/purchases
  Cloud-Function-write-only** (client denied). Server verify (see 4.4). Replay dedupe: `purchases/{token}`
  doc id + `processedTokens` guard. Offline hardening: **Android-Keystore-backed HMAC** signs
  `PremiumStore`/`SubscriptionStore`/streak caches (casual edits detected). Delete the dead Tamper/Health
  stores. Cloud `onAccountDelete` erases `users/{uid}/**` + Auth user (flips `DataRightsService` to
  `Done`). **Honest limit:** APK-bundled banks/solutions cannot be truly protected on-device — price/segment
  around extractability, not DRM (already documented).
- **Data model:** rules + `users/{uid}/purchases/{token}` (replay key) + `users/{uid}/entitlement`
  (Fn-write-only).
- **Dependencies:** Auth, Firestore, Functions.
- **Risks:** client premium forgery; token replay; rules gaps (test exhaustively); recent-login on delete;
  over-investing in un-winnable on-device DRM.
- **Migration:** Phase 1 ship-safe hardening WITHOUT backend (delete dead stores, sign local caches, verify
  `Purchase.signature`); Phase 2 rules + server verify + cloud erase.
- **Order:** local hardening now; rules + server with Sync/Premium.
- **Testing:** rules-unit-testing (A can't read B; content write denied; entitlement client-write denied);
  `verifyPurchase` sandbox; HMAC tamper rejection; replay rejection.
- **Key decisions:** (a) server-authoritative entitlement + treat `PremiumStore` as untrusted cache; (b) how
  much client hardening to ship pre-backend (Keystore-HMAC + signature verify — recommended cheap wins); (c)
  replay/dedupe location (server token uniqueness + RTDN); (d) accept the client-confidentiality limit.

### 4.10 Data layer + Production readiness

- **Exists:** `edumio.db` v24 (additive 11→24 chain, release **no** destructive fallback — correct);
  isolated `daily_challenge.db` v2; versioned idempotent `DbSeeder`; startup pipeline
  (`seedIfNeeded → DataIntegrityChecker → StartupAuditRecorder`); GDPR `DataRightsService` (real on-device
  export/delete); legal seam (`LegalConfig` + in-app HTML); `targetSdk=35`, minimal permissions; ProGuard;
  a migration instrumentation test.
- **Missing / BLOCKERS:** ⛔ **no real privacy policy** (`privacy_policy_tr.html` is an explicit DRAFT); ⛔
  **no hosted legal URLs** (`PRIVACY_POLICY_URL`/`TERMS_URL` empty — Play requires a hosted privacy URL); ⛔
  **no release signing config**; ⚠️ **stale AdMob disclosure** (strings/docs claim AdMob but there's no ads
  SDK) — a Data Safety mismatch; `exportSchema=false` on both DBs (no schema JSON → can't
  `MigrationTestHelper`); `allowBackup=false` + sample backup-rules stubs; stale/lossy `BackupManager`
  (serializes dead parental fields); cloud GDPR erase unimplemented; no version-bump discipline.
- **Architecture:** keep the two-DB split. Flip `exportSchema=true`, **freeze a v24 baseline now** (last
  moment before public installs), commit schema JSONs, convert the migration test to `MigrationTestHelper`.
  Rebuild `BackupManager` on `DataRightsService.exportData/import` (one canonical, area-aware path).
  Resolve **ads truth**: strip AdMob strings/docs (ad-free) OR add the SDK — and make the privacy policy +
  Data Safety form + IAP disclosure all match. Host legally-reviewed privacy + terms, inject via
  `BuildConfig`. Add a release `signingConfig` (keystore in CI secrets).
- **Data model:** `edumio.db` = immutable seeded content (additive migration); `daily_challenge.db` = user
  state (destructive pre-launch only); ~30 SharedPreferences stores (the `SyncRegistry`/`DataRightsService`
  serialization surface).
- **Dependencies:** legal review (external), signing keystore, Firebase (for cloud erase).
- **Risks:** the three ⛔ items **block Play upload**; Data Safety inaccuracy risks rejection/removal;
  freezing schema too late (after installs) makes migrations painful.
- **Migration:** fresh-install posture is deliberate (renamed package, no legacy upgrades); do the schema
  freeze + legal + signing before the first public build.
- **Order:** **release blockers first** (parallel with everything, since they gate upload); schema freeze
  immediately; cloud erase after Auth/Sync.
- **Testing:** `MigrationTestHelper` 11→24 (once `exportSchema=true`); seed-idempotency (run twice → no
  dupes); data-rights export/import round-trip; a pre-release Play pre-launch report.
- **Key decisions:** (a) backup/portability — on-device-only (`allowBackup=false`, unify BackupManager on
  DataRights) vs Firebase sync now; (b) ads & Data Safety truthfulness (ad-free recommended given no SDK);
  (c) flip `exportSchema=true` + freeze v24 baseline **now**; (d) legal-readiness ownership (hosted,
  reviewed privacy + terms is the single gating upload item).

---

## 5. Global sequenced roadmap (dependency-ordered)

Phases are gates; within a phase, items can parallelize. **Nothing here is implemented until approved.**

- **Phase 0 — Freeze & release-blocker clearance (no Firebase needed).** Flip `exportSchema=true` + freeze
  v24 schema baseline; resolve ads truth + Data Safety; host + inject legal URLs; add release signing;
  ship-safe security hardening (delete dead Tamper/Health stores, Keystore-HMAC-sign local premium/streak
  caches, verify `Purchase.signature`); rebuild `BackupManager` on DataRights; local `NotificationOrchestrator`
  facade (no behavior change). *Unblocks a first internal release.*
- **Phase 1 — Firebase foundation + Auth + Analytics/Crashlytics.** FND wiring;
  `FirebaseAuthRepository` + Google `CredentialProvider` + session listener + verify/reset/delete/re-auth;
  Firebase Analytics + Crashlytics (chained) + minimal Perf behind existing sinks; emit the missing funnel +
  `premium_purchased` events; one-time id re-key. *Unblocks user accounts + observability.*
- **Phase 2 — Firestore sync + Remote Config.** `FirestoreSyncRepository`; bring DC DB into sync; server
  time; `schedulePeriodic` + sign-in/complete triggers; `FirebaseRemoteConfigAdapter` + kill-switch gate +
  A/B bucket; **Firestore security rules v1** (per-user isolation). *Unblocks cross-device continuity.*
- **Phase 3 — Premium server truth.** `verifyPurchase` Function (Play Developer API); `onRtdnNotification`
  (Pub/Sub); server `entitlement` doc as SoT; flip premium/subscription **server-write-only** in rules;
  expiry/grace/hold; launch reconcile; bind purchase to uid. *Unblocks trustworthy premium.*
- **Phase 4 — Email lifecycle.** Transactional (verify/reset already via Auth) → `CloudReportDeliveryService`
  (reports) → marketing (consent capture + unsubscribe + suppression, server-enforced). *Unblocks lifecycle.*
- **Phase 5 — Notifications push + engine completion.** `NotifOutbox` + quiet hours + frequency caps;
  FCM (`FirebaseMessagingService` + device registration) for win-back + premium-lifecycle + announcements;
  RemoteConfig-driven slots. *Unblocks retention loops.*
- **Phase 6 — Production hardening & launch.** Cloud `onAccountDelete` (GDPR erase); rules v2 (anti-cheat
  streak if chosen); Play pre-launch report; staged rollout; A/B pricing experiments; monitoring dashboards.

### Dependency graph (arrows = "must precede")

```
Phase 0 (blockers/freeze/local-hardening) ─┐
                                           ├─► Phase 1 (FND + Auth + Analytics)
                                           │        │
                                           │        ├─► Phase 2 (Firestore sync + RemoteConfig + rules v1)
                                           │        │        │
                                           │        │        ├─► Phase 3 (Premium server truth) ─► RTDN
                                           │        │        │
                                           │        │        └─► Phase 4 (Email)  ── needs verified email (Auth)
                                           │        │
                                           │        └─► Phase 5 (Notifications+FCM) — needs Auth (device reg)
                                           │
                                           └─► DC server-per-day (rides Phase 2 sync + rules)
Phase 6 (GDPR erase, rules v2, launch) ◄── everything
```

---

## 6. Risk register (top cross-system risks)

| Risk | System(s) | Severity | Mitigation |
|---|---|---|---|
| Client-trusted premium (forgery/replay/never-revoked) | Premium, Security | **High** | Server `verifyPurchase` + RTDN + server-write-only entitlement + signed offline cache + token dedupe |
| Streak/XP farming via device clock | DC, Security | High | Server time authority; sign streak cache; consider server-owned streak |
| Multi-device same-day divergence | DC, Sync | High | Server first-writer-wins per `(uid,localDate)` + answer re-homing by questionId |
| LWW whole-store replace loses monotonic data | Sync | Med-High | Field-level merge/union-max for history, seen-sets, streak |
| Legal/Data-Safety non-compliance (privacy URL, consent, ads mismatch) | Production, Email | **High (blocks upload)** | Host reviewed policy; resolve ads truth; server-enforced consent + unsubscribe + suppression |
| A bad Remote Config flag bricks the app | RemoteConfig | Med | Fail-safe `setDefaultsAsync` three-tier fallback + staged rollout + tested kill-switch |
| Notification fatigue → uninstalls | Notifications | Med | One orchestrator + global daily cap + quiet hours + per-account (not per-area) budget |
| PII/solution text leaking into analytics/email | Analytics, Email | Med | Freeze the IDs-only invariant; no-PII assertion tests; verified-email guard |
| Schema frozen too late (post-install migration pain) | Production | Med | Flip `exportSchema=true` + freeze v24 baseline in Phase 0 |
| userId cutover breaks sync cursor / DC state | Auth, Sync, DC | Med | One-time re-key via extended `DailyChallengeAccountLink`; test reinstall-restore |

---

## 7. Release-blocker checklist (gate the first public upload)

- [ ] Legally-reviewed **privacy policy + terms** hosted at real URLs, injected via `BuildConfig`.
- [ ] **Release signing** config (keystore in CI secrets; Play App Signing).
- [ ] **Ads truth** resolved (strip AdMob disclosure if ad-free) + **Data Safety** form matches reality
      (IAP present, no ads, what user data leaves the device).
- [ ] `exportSchema=true` + committed v24 schema baseline + `MigrationTestHelper` test.
- [ ] Crashlytics live (don't ship blind).
- [ ] Play pre-launch report clean; target SDK 35 (already met).

---

## 8. Approval gate

This document is the **complete Phase 2 architecture**. Per the directive, **implementation begins only
after approval.** On approval, the recommended first move is **Phase 0** (release-blocker clearance + freeze
+ local security hardening) because it needs no Firebase, unblocks an internal release, and de-risks
everything downstream — followed by Phase 1 (Firebase foundation + Auth + Crashlytics).

Open decisions that most shape the build (recommend resolving before Phase 1): anonymous-identity model
(§4.1), entitlement source of truth (§4.4), server-write-only boundaries (§4.2/4.4), DC generation authority
(§4.3), and email transactional/marketing split (§4.6).

*No content was changed by this plan. `content/**` remains frozen.*
