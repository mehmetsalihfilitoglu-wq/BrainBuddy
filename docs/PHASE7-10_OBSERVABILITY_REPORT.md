# Phases 7–10 — Analytics, Crashlytics, Remote Config Report

**Branch:** `seeding-final-fix` · **Not pushed.** ✅ validated (compile/test) · 🟡 runtime owner-gated.

All three are adapters behind their existing seams — activated when Firebase is configured, local defaults
otherwise. Callers never change.

## Analytics (Phase 7) — ✅
- `FirebaseAnalyticsTracker` behind `AnalyticsTracker`; `AnalyticsProvider.tracker(context)` returns it when
  configured, else the Logcat tracker (transient, uncached, when no context yet).
- **`AnalyticsSafety`** (pure, 4 tests): drops any param key that looks like PII or question/solution content
  (email/password/token/stem/option/choice/solution/display_name…) and truncates strings + event names.
  **Analytics can never carry stems, options, solutions, emails or tokens.** Applied on every event.
- Analytics failure never blocks learning (it's fire-and-forget behind the seam).

## Crashlytics (Phase 9) — ✅
- New `CrashReporter` seam + `CrashlyticsReporter` (Firebase) + `NoOpCrashReporter` (Logcat) +
  `CrashReporterProvider`. Reports non-fatals + breadcrumbs + safe keys; **contract forbids PII/content**.
- ⏭️ Wiring it into the global uncaught-exception handler is a small follow-up kept out now to avoid changing
  crash behaviour without device validation; the seam + adapter are ready.

## Remote Config (Phase 8/10) — ✅
- `FirebaseRemoteConfigAdapter` behind `RemoteConfig`: seeds FRC with the bundled `RemoteConfigKeys.defaults`
  so **every getter has a safe value before the first fetch and a fetch failure never breaks the app**; for
  unknown keys the caller's default wins. `RemoteConfigProvider.get(context)` returns it when configured.
- Remote Config can tune paywall copy, feature flags, kill switches, maintenance mode, min-version, etc. It
  **cannot** change answers, question content, the five-question rule, blueprints, or verified solutions —
  none of those are config-driven.

## Not done here (honest)
- 🟡 Live event delivery, crash upload, and remote fetch need the owner's Firebase project (dashboards +
  Crashlytics + Performance are enabled per `OWNER_CONSOLE_SETUP_GUIDE.md` §7). Performance Monitoring traces
  (startup / challenge-gen / image decode / Firestore latency) are a follow-up increment.

## Guardrails
Content untouched (`af65df01…`). 196/0 JVM tests. No secret committed. `firebase-config` added to the BoM set.
