# Phase 4–5 — Premium Verification & Play Billing Report

**Branch:** `seeding-final-fix` · **Not pushed.** ✅ validated · 🟡 runtime owner-gated · 🔵 owner/console.

## Model
Premium is a **server-verified entitlement**, never trusted from the client. Play processes the purchase →
the client calls `verifyPurchase` with the token → the **Cloud Function** validates it with the Google Play
Developer API and writes `users/{uid}/entitlements/premium` (a **client-read-only** doc) → the client reads
it. Play RTDN keeps it fresh on renew/cancel/expire/hold. **Premium changes review/solution DEPTH only —
never the 5-new-questions/day count** (unchanged, enforced by the frozen DC engine + guardrails).

## Delivered — ✅
- **Pure, unit-tested** logic (the fail-safe core):
  - `EntitlementState` — Play subscription state → isPremium. ACTIVE/IN_GRACE → premium; CANCELED → premium
    only until `expiresAtMs`; **everything else (on-hold, paused, expired, revoked, pending, unknown, null)
    → Free.** (4 tests)
  - `PurchaseAccountBinding` — an entitlement grants Premium only to the exact account it was verified for;
    signed-out → Free; server-reachable → trust server iff bound; offline → trust cache iff bound; any
    mismatch → Free. (4 tests)
- **`FirestoreEntitlementRepository`** behind the `EntitlementRepository` seam: reads the server-written
  entitlement, applies the two pure rules, refreshes the `PremiumStore` cache, fails safe to cache/Free on
  error. `EntitlementProvider` returns it when Firebase is configured, else the local cache. Callers
  unchanged.
- **Cloud Functions billing source** (`functions/billing.js`, not deployed here):
  - `verifyPurchase` (callable) — validates the token via `androidpublisher.purchases.subscriptionsv2.get`,
    maps the state, writes the entitlement + a `purchaseTokens/{token}→uid` reverse index (server-only).
  - `playRtdnHandler` (Pub/Sub) — re-verifies on every lifecycle event and updates the entitlement without
    the client.
  - Re-exported from `index.js`; `googleapis` dependency added; `node --check` clean.
- **`firestore.rules`**: `entitlements/*` client-read-only (Phase 2); `purchaseTokens/*` fully server-only.

## Not done here (honest) — 🟡 / 🔵
- 🔵 **Product ids + prices** are owner-created and must not be invented — see
  `EXTERNAL_CREDENTIALS_REQUIRED.md` / `OWNER_ACTIONS_REQUIRED.md`. The existing `PlayBillingRepository`
  (real BillingClient) drives the purchase flow; it reads product ids from config (never hardcoded).
- 🔵 Owner must enable the Play Developer API, link Play ↔ GCP, grant the Functions service account Play
  access, and create the RTDN Pub/Sub topic (guide §10–11).
- 🟡 Live purchase/verify/restore/expire/hold flows require the deployed function + real Play products +
  a device; the fail-safe mapping + binding they depend on are fully tested here.
- ⏭️ Wiring `verifyPurchase` into the client purchase-success/restore callbacks ships with the deployed
  function (kept out now to avoid an untestable client path).

## Guardrails
Premium neutrality preserved (no path increases the new-question count). Unknown/error/offline/mismatch →
Free. 192/0 JVM tests. Functions `node --check` clean. Content unchanged (`af65df01…`). No secret committed.
