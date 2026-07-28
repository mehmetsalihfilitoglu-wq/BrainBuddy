# EDUmio v1.0 — Disabled / Deferred Features (Spark-safe)

Everything that needs deployed Cloud Functions / Blaze / live Play products is **dormant behind the
`SPARK_SAFE` build flag** — hidden from the UI, not deleted, not forked. Flip `SPARK_SAFE=false`
(`app/build.gradle.kts`) when the backend is deployed and each returns behind its existing seam.

| Feature | v1.0 state | Where gated | Re-enables when |
|---|---|---|---|
| Account creation + cloud sign-in (email/Google) | **Hidden** (Settings → Account & Sync entry removed) | `ReleaseProfile.cloudAccountEnabled` | backend deployed |
| Cloud synchronization (Firestore) | **Off** (uses local mirror) | `SyncProvider` + `ReleaseProfile.cloudSyncEnabled` | rules + backend deployed |
| Server entitlement verification | **Off** (local cache only, fail-safe Free) | `EntitlementProvider` + `serverEntitlementEnabled` | verify function deployed |
| Purchasable subscriptions | **Hidden** (paywall shows value + "coming soon", no CTA) | `PremiumPaywallSheet` + `purchasesEnabled` | Play products + verify function |
| Server-authoritative challenge | **Not wired** (local engine only) | never client-wired | Phase 3 client wiring |
| Server push / RTDN / email / admin | **Dormant** (source only, not called) | not invoked | respective backend deploy |

**Active in v1.0:** local Daily Challenge (immutable, 5/day, retirement, exam isolation), local progress,
local wrong-question pool + review, local reminders, Firebase **Analytics** + **Crashlytics** (Spark-free).

**Premium in v1.0:** everyone is Free; Premium-gated content (full solutions, unlimited review) shows the
"coming soon" paywall. No purchase can start. Premium never affects the 5-question count.
