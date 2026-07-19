# Current Data-Processing Inventory (Phase 0 truth)

**As of Phase 0 (no Firebase / no server wired).** This is the *current* reality for the Play Data Safety
form and the privacy policy — not the future planned state. Everything below is verifiable in code.

## A. Data stored locally on the device (now)

All user data lives on-device only. No account is required to use the app.

- **SharedPreferences (~30 stores)** — settings, onboarding, study-area selection, progress/stats,
  gamification (XP/streak), quiz history, wrong-question pool + review schedule, notification prefs, email/
  report prefs, premium cache. Enumerated by `sync/SyncRegistry` and serialized by
  `privacy/LocalDataRightsService` (GDPR export/delete already work on-device).
- **Room `edumio.db`** — the shipped question/content DB (no personal data) + local history/snapshot/
  wrong-answer tables.
- **Room `daily_challenge.db`** — the user's Daily Challenge state (challenges, answers, retirement/review
  state, streak) keyed by a local account id.
- **Local auth (only if the user creates a local account)** — `LocalAuthStubRepository` stores email +
  a PBKDF2-hashed password on-device. **This is device-local; nothing is sent anywhere** (no Firebase yet).

## B. Data that leaves the device (now)

- **In-app purchases (subscriptions)** — Google Play Billing is integrated (`PlayBillingRepository`,
  billing-ktx 7.1.1). When a user subscribes, **Google Play** processes the payment and purchase data;
  EDUmio's own servers receive nothing today (server-side verification is Phase 3). The app itself does not
  collect or transmit payment information.
- **Nothing else.** No analytics upload (the analytics sink is on-device only — `LocalAnalytics`/local
  metrics; no Firebase Analytics), no crash upload (local `CrashActivity` handler only), no email server,
  no cloud sync (`SyncProvider` returns the local mirror), no ads network. All backend seams
  (`AuthProvider`, `SyncProvider`, `AnalyticsTracker`, `RemoteConfig`, `EntitlementRepository`,
  `EmailDeliveryRepository`) resolve to **local implementations**.
- **User-initiated only:** report PDFs are shared via the Android share sheet **if the user taps share** —
  the destination (email app, etc.) is chosen by the user, not sent by us.

## C. Data NOT collected (now) — and the corrected disclosure

- **No advertising / no ads SDK.** `ads/RewardedAdManager` is an *ad-free unlock shim* (grants a free
  unlock instantly, bounded by a daily quota); there is no Google Mobile Ads SDK, no AdMob app id, no ad
  network. **Phase 0 removed the false "the app uses Google AdMob" legal disclosure** (`ad_info` doc).
  > Follow-up (product, not a release blocker): some review/report buttons still read "Reklam İzle"
  > ("Watch Ad") though the unlock is now free/ad-free — recommend renaming to honest "free unlock" copy.
- **No microphone / camera / location / contacts.** Manifest permissions are minimal (`INTERNET`,
  `POST_NOTIFICATIONS`, and standard boot/vibrate for reminders). No dangerous permissions.
- **No third-party data sharing.** Nothing is shared with any third party today.

## D. Future data processing (planned — NOT active; do not disclose as current)

Only becomes real when the corresponding Phase 2 system is wired (see `PHASE2_ARCHITECTURE.md`):

- **Firebase Auth** (Phase 1) — email, display name, provider id leave the device to Google.
- **Firestore sync** (Phase 2) — profile/progress/wrong-question/streak sync to Google servers.
- **Firebase Analytics + Crashlytics + Performance** (Phase 1) — usage events (IDs only, no PII/solution
  text), crash reports, performance traces to Google.
- **Cloud Functions + email ESP** (Phase 4) — email address used for transactional + (consented) marketing
  email.
- **Purchase-token verification** (Phase 3) — purchase token sent to EDUmio's Cloud Function → Play
  Developer API.

**When each ships, update this inventory, the privacy policy, and the Play Data Safety form together.**

## Play Data Safety form (current-state guidance)

- Data collected/shared **by the app itself**: **none** today (all on-device). 
- **In-app purchases**: yes (subscriptions via Google Play).
- **Ads**: **no.**
- Data is **not** transmitted off-device by the app today (Play Billing payment handling is Google's).
- Account creation is **optional and local** today.
