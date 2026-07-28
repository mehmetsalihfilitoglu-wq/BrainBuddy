# EDUmio — Backend Architecture & Contract

**Status:** the Android client is *backend-ready*. Every server responsibility lives
behind a client interface with a working on-device implementation, so the app builds,
runs, and is fully usable offline **today**. Going live means provisioning the backend
and swapping each `*Provider` from its local implementation to a Firebase one — **no
business-logic or UI rewrite**.

Platform: **Firebase**, interface-first. Target: **Android / Google Play first**;
Apple Sign-In + App Store are a later iOS phase (auth is already provider-agnostic).

---

## 1. Client seams (built) — interface → local impl → provider

| Concern | Contract | Local impl (today) | Firebase impl (later) |
|---|---|---|---|
| Authentication | `auth.AuthRepository` | `LocalAuthStubRepository` | `FirebaseAuthRepository` |
| Federated tokens | `auth.CredentialProvider` | `StubCredentialProvider` | `GoogleCredentialProvider` |
| Cloud sync | `sync.SyncRepository` | `LocalMirrorSyncRepository` | `FirestoreSyncRepository` |
| Remote config | `remote.RemoteConfig` | `LocalRemoteConfig` | `FirebaseRemoteConfigAdapter` |
| Premium entitlement | `billing.EntitlementRepository` | `LocalEntitlementRepository` | `PlayBillingEntitlementRepository` |
| Subscriptions/billing | `billing.BillingRepository` | `PlayBillingRepository` (real, default) · `LocalBillingRepository` (dev) | server receipt validation via `verifyPurchase` Fn |
| Report delivery | `report.ReportDeliveryService` | `LocalReportDeliveryService` | `CloudReportDeliveryService` |
| CMS content | `content.ContentGateway` | `LocalContentGateway` | `RemoteContentGateway` |
| Product analytics | `analytics.AnalyticsTracker` | `LogcatAnalyticsTracker` | `FirebaseAnalyticsTracker` |
| Data rights (GDPR) | `privacy.DataRightsService` | `LocalDataRightsService` | `FirebaseDataRightsService` |

Composition roots (flip one line each to go live): `AuthProvider`, `SyncProvider`,
`RemoteConfigProvider`, `EntitlementProvider`, `ReportDeliveryProvider`,
`ContentProvider`, `AnalyticsProvider`, `DataRightsProvider`.

---

## 2. Auth (Firebase Auth)

- Providers: **Email/Password**, **Google** (now), **Apple** (with iOS). Email
  verification + password reset are Firebase-native.
- Client maps `FirebaseUser → auth.AuthUser`; callers never see a vendor type.
- **UID is the single identity** once connected — the root of every user document below.
- `AuthProvider.isFirebaseConfigured()` flips to Firebase when `google_app_id` exists.
- `deleteAccount()` → `FirebaseUser.delete()` + a Cloud Function erasing the user's
  Firestore tree (see §9).

## 3. Firestore user-data structure

Per-user, **last-write-wins by `updatedAt`**, study areas isolated by `areaId`. Each
node stores `{ scope, areaId, storeKey, updatedAt (server ts), schemaVersion, payload }`.
Paths are produced by `SyncRecord.firestorePath(uid)` and declared in `sync.SyncRegistry`.

```
users/{uid}
  /profile                      ← study-area list + active area   (bb_profiles)
  /settings                     ← goals / app settings            (user_goal_prefs)
  /onboarding                                                     (bb_onboarding_prefs)
  /notificationPreferences                                        (bb_notification_prefs)
  /reportPreferences                                             (bb_email_report_prefs)
  /premium                      ← entitlement cache (server-authoritative)
  /questionHistory                                               (bb_question_history)
  /favorites                    ← saved universities/programs
  /discoveryProgress            ← viewed universities/programs
  /areas/{areaId}
      /analytics                ← sessions + past tests           (bb_analytics)
      /gamification             ← XP, level, streak, achievements (bb_gamification)
      /mastery                  ← Leitner boxes + review queue     (bb_wrong_scheduler)
      /wrongQuestions                                             (bb_wrong_question_pool)
      /wrongQuestionsStore                                        (bb_wrong_question_store)
      /dailyMissions                                              (bb_quiz_prefs)
      /studentProfile                                             (bb_student_profile)
      /avatar                                                     (bb_avatar)
      /weeklyReward                                               (bb_weekly_reward)
      /reports                                                    (bb_reports)
      /readinessSnapshots       ← readiness over time (change trends)
```

Derived surfaces (heatmap, learning journey, insights, readiness view) are **not
stored** — they recompute from the above, so syncing the stores syncs them too.
Excluded from sync: auth credentials (device-local), sync bookkeeping.

**Migration local→cloud:** on first sign-in, the client runs a normal sync — `SyncEngine`
pushes all local stores (each hashed → one `updatedAt`) then pulls; last-write-wins
merges any existing cloud data. Because study areas are keyed by `areaId`, two devices
merge per-area without cross-contamination. Turn on via Remote Config
`cloud_sync_enabled=true` + `SyncWorker.schedulePeriodic()`.

## 4. Cloud sync engine (client, already built)

`SyncEngine`: collect snapshots → push changed (content-hash dirty detection) → pull
since cursor → apply records newer than local (**GLOBAL applied first** so a newly
synced area exists before its data). `SyncStateStore` holds per-doc hash/updatedAt/pushed
+ per-user pull cursor. `LocalMirrorSyncRepository` exercises the whole contract offline.

## 5. Reports (Cloud Functions + email)

- **Weekly:** every Sunday 20:00 local. **Monthly:** the 1st, 20:00 local. Scheduled
  client-side by `PremiumReportScheduler` (weekly periodic; monthly self-rescheduling
  one-shot). Server-side scheduling (Cloud Scheduler) can replace this once live.
- Content is real-data only: `ReportBuilder` → `WeeklyReportData`/`MonthlyReportData`
  (questions, accuracy, active days, streak, comparison, biggest improvement,
  strongest/weakest topic, mastered, under review, readiness + change, heatmap snippet,
  journey milestone, recommendation). Honest "not enough data" state when thin.
- `ReportRenderer` → `ReportPayload` (subject + HTML + text). `ReportDeliveryService`
  delivers: local queues for manual share; the Cloud Function sends the email
  (e.g. SendGrid) and returns `Sent`. Gated by Premium + Remote Config flags.
- Email templates are CMS-editable (`ContentGateway.emailReportTemplate(type)`).

## 6. Notifications (FCM)

- Device registers its FCM token under `users/{uid}/devices/{token}`.
- Smart, real-data content reuses `core.SmartNotifications` signals (due reviews, streak
  proximity, weakest topic, readiness change) so server pushes match on-device logic.
- Marketing pushes come from CMS `notificationCampaigns` (schedule window, premium-only).

## 7. Remote Config keys (`remote.RemoteConfigKeys`, with local defaults)

Reports (`weekly_reports_enabled`, `monthly_reports_enabled`, `weekly_report_hour`,
`weekly_report_day_of_week`, `monthly_report_day_of_month`); platform
(`cloud_sync_enabled`, `maintenance_mode`, `min_supported_version_code`); paywall
(`paywall_variant`, `paywall_headline`, `paywall_subtext`, `premium_monthly_price`);
campaigns (`campaign_banner_enabled/_text`, `discovery_banner_enabled/_text`); weekly
challenge (`weekly_challenge_enabled/_target`); daily mission
(`daily_mission_question_count`, `daily_mission_minutes_estimate`); notifications
(`daily_reminder_hour`, `streak_warning_hour`); feature flags
(`feature_discovery_enabled`, `feature_league_enabled`); A/B (`ab_test_bucket`); content
versions (`cms_content_version`, `exam_guide_version`, `question_bank_version`).

## 8. CMS / Admin Panel (Firestore collections)

Everything content-heavy is CMS-managed — no app update to change content
(`content.ContentGateway`, models in `content.RemoteContent`):

```
content/questions/{examCode}/{questionId}     content/explanations/{questionId}
content/examSections/{examCode}/{sectionId}   content/topics/{examCode}/{topicId}   (subtopics via parentTopicId)
content/universities/{id}    content/programs/{id}    content/scholarships/{id}
content/blog/{id}            content/guides/{examCode}/{id}
content/announcements/{id}   content/news/{id}        content/premiumBanners/{id}
content/notificationCampaigns/{id}   content/emailTemplates/{type}
content/onboarding/{slideId} content/microcopy/{key}
```

Questions & universities keep bundled data as offline fallback, refreshed via
`refreshQuestions()/refreshUniversities()` and version-checked against Remote Config.
The Admin Panel is a separate web app writing these collections (Firebase Auth admin claims).

## 9. GDPR / privacy (`privacy.DataRightsService`)

- **Export My Data (works today):** `LocalDataRightsService.exportData()` serialises
  every local store (per study area) to shareable JSON via the "Gizlilik ve Verilerim"
  Settings screen.
- **Delete (works today, local):** clears all SharedPreferences (keeps bundled content)
  and the auth account; relaunches the app.
- **Cloud (later):** `deleteCloudData()` / cloud half of `deleteAccount()` → a Cloud
  Function deletes `users/{uid}` recursively + the Firebase Auth user; export can be
  generated server-side for completeness. Until then these return
  `LocalDoneCloudPending` — honest, never faked.
- Privacy Policy / Terms are linked from the same screen (`LegalHubActivity`).

## 10. Premium billing & entitlement (`billing.*`)

**Business model: ad-free.** No advertisements of any kind. Revenue is Free vs
Premium, sold as **monthly** and **yearly** subscriptions. Premium sells intelligence
and coaching, never XP / "more questions" / dopamine.

**Products & pricing** (Play Console product ids; prices/copy defaulted in Remote Config, overridable):

| Plan | Product id | Price (default) | Notes |
|---|---|---|---|
| Monthly | `edumio_premium_monthly` | **₺199 / ay** | |
| Yearly | `edumio_premium_yearly` | **₺1.699 / yıl** | badge **"En Avantajlı"**, default-selected, saving copy **"Aylık ödemeye göre yaklaşık %29 tasarruf"** |

Remote Config keys: `premium_monthly_price`, `premium_yearly_price`, `premium_yearly_badge`,
`premium_yearly_saving`. Play returns the localized `formattedPrice` at runtime; the Remote
Config values are the fallback shown when Play products aren't available yet.

**Client layers:**
- `billing.BillingRepository` is the subscription boundary (offers/purchase/restore/status/refresh).
  The UI (`PremiumPaywallSheet`) talks only to this — never to `BillingClient`.
- `PlayBillingRepository` is the **real Google Play Billing adapter** (v7, `billing-ktx`) and is
  the default via `BillingProvider`. It queries `SUBS` product details, launches the billing
  flow, handles `onPurchasesUpdated`, acknowledges purchases, and restores via
  `queryPurchasesAsync`. It degrades gracefully: no Play / unconfigured products / emulator →
  `offers()` falls back to Remote-Config prices (paywall still shows plans) and `purchase()`
  returns `PurchaseResult.Unavailable`. `BillingProvider.forceLocal = true` selects
  `LocalBillingRepository` for tests/dev.
- `SubscriptionStatus` lifecycle — `ACTIVE`, `IN_GRACE_PERIOD`, `CANCELLED` (entitled until
  period end), `EXPIRED`, `NONE`.

**Entitlement behaviour (no fake Premium):**
- Premium is granted **only** after a real `PURCHASED` purchase is acknowledged (or a restore
  that finds one). **Pending** purchases return `PurchaseResult.Pending` and grant nothing.
  `USER_CANCELED` → `Cancelled`; any other failure → `Error`; store unavailable → `Unavailable`.
- On grant, the adapter writes `SubscriptionStore` (`bb_subscription`) + the `PremiumStore` cache.
- **Restore** (`restore()`): `queryPurchasesAsync(SUBS)` → if an active purchase exists, grant;
  otherwise status `NONE` and Premium cleared.

**Play Console setup requirements (before it transacts):**
1. Create the app; add two **auto-renewing subscription** products with the ids above + base plans/offers.
2. Set prices per market (₺199 / ₺1.699 for TR).
3. Upload a signed build to at least an **internal/closed testing** track and add **license testers**.
4. Configure the real-money account; enable the Play Developer API for server validation.

**Testing requirements:** test on a device with Play Store signed in as a **license tester**
(billing is unavailable on emulators without Play) via internal/closed testing; exercise
purchase, cancel, restore, and a pending purchase (slow-test card).

**Server-side receipt validation (source of truth, when backend is live):**
`PlayBillingRepository` sends the purchase token to a `verifyPurchase` Cloud Function →
validated against the Google Play Developer API → the **verified** `SubscriptionStatus`
(incl. real `expiresAtMs`, grace/cancel state) written to `users/{uid}/purchases` and back into
the client cache. Until then the client grants a client-side `ACTIVE` (expiry unknown), replaced
by the verified value. Server-gated features never trust client-only premium state.

**Cross-device:** the subscription store (`bb_subscription`, sync path `users/{uid}/purchases`)
syncs, so Premium follows the account.

## 11. Product analytics (`analytics.AnalyticsTracker`)

~35 events in `analytics.AnalyticsEvents` (lifecycle/onboarding, study areas, daily
mission, quiz/review, mastery, achievements, progress/coach, discovery, premium,
reports, notifications, sync, account/GDPR). `LogcatAnalyticsTracker` logs locally;
`FirebaseAnalyticsTracker` maps 1:1 later. `setUserId(uid)` ties events to the account.

## 12. Security rules (assumptions)

- A user may read/write only under their own `users/{uid}/**`.
- `content/**` is world-readable, admin-writable (custom claim `admin == true`).
- Study-area isolation is structural (areaId in the path) and enforced by rules that
  never allow one user's tree to reference another's.
- Firestore scheduled export → Cloud Storage for backups.

---

## 13. Go-live checklist

1. Create the Firebase project; add `google-services.json` + the Google Services plugin.
2. Add SDKs: Auth, Firestore, Messaging, Remote Config, Analytics; Credential Manager; Play Billing.
3. Implement the nine Firebase adapters (§1) against the existing interfaces.
4. Return them from the `*Provider` composition roots.
5. Deploy Firestore security rules (§12) + Cloud Functions: weekly/monthly reports,
   `verifyPurchase`, GDPR export/delete, smart-notification push.
6. Set Remote Config defaults; set `cloud_sync_enabled=true`; call
   `SyncWorker.schedulePeriodic()` and `PremiumReportScheduler.scheduleAll()` after sign-in.
7. QA: cross-device sync, purchase verification, report email, account deletion/export.

> The only product component still outside this architecture is the **real exam question
> bank**, which plugs into `content/questions/{examCode}/…` and the existing quiz pipeline.
