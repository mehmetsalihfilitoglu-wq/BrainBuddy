# Phase 6 — Notification Infrastructure (FCM) Report

**Branch:** `seeding-final-fix` · **Not pushed.** ✅ validated · 🟡 runtime owner-gated.

The **local WorkManager reminders remain PRIMARY** (they work offline; `DailyChallengeReminderPolicy` already
enforces the three slots 09:00/16:00/20:30, ≤3/day, stop-on-completion, idempotency — all tested). FCM adds
**server-initiated** pushes (streak-at-risk, re-engagement) as a supplement.

## Delivered — ✅
- **`EdumioMessagingService`** (`FirebaseMessagingService`) — displays pushes on the existing
  `dc_daily_challenge` channel (immutable PendingIntent → `MainActivity`) and registers the token on refresh.
  Declared in the manifest (`exported=false`, MESSAGING_EVENT). **Inert in local-fallback builds** (never
  invoked without a configured project). Manifest merges cleanly (assembleDebug ✅).
- **`FcmTokenRegistrar`** — writes the device token to `users/{uid}/fcmTokens/{token}` when configured +
  signed in; best-effort, failures swallowed.
- **`PushNotificationPolicy`** (pure, 4 tests) — server-push guards: ≤3/day cap, **quiet-hours** (incl.
  windows wrapping midnight), duplicate suppression, and challenge reminders **stop after completion**.
- **`firestore.rules`**: `fcmTokens/*` owner-read/write (client registers its own; server reads to send).
- **`functions/messaging.js`** (source): `sendPushToUser` (multicast + dead-token pruning) + `sendTestPush`
  callable; re-exported from `index.js`; `node --check` clean.

## Not done here (honest)
- 🟡 Live delivery + the server-scheduled reminder sweep (per-user completion/timezone/prefs) need the
  deployed function + a device with a real token. The local reminders cover the core reminder need offline;
  server-scheduled sweeps are a follow-up.
- Preferences (on/off, time, quiet hours, channels) already exist in `edu_notification_prefs` and sync via
  Phase 2.

## Guardrails
Content untouched (`af65df01…`). 200/0 JVM tests. Manifest merges. No guilt/deceptive copy. No secret committed.
