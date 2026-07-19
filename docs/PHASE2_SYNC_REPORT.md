# Phase 2 — Cloud Synchronization Report

**Branch:** `seeding-final-fix` · **Not pushed.** ✅ validated (compile/test) · 🟡 runtime owner-gated · 🔵 owner/console.

## Delivered
- ✅ **`FirestoreSyncRepository`** implements the existing `SyncRepository` contract (push/pull/serverTimeMs)
  against Firestore, so `SyncEngine` is unchanged. Storage: `users/{uid}/syncRecords/{documentId}` — one doc
  per store snapshot; **study-area isolation preserved by construction** (areaId in the documentId); opaque
  store payload stored as a JSON string for lossless round-trips. **No question/solution content is ever
  written** — only the user's own learning-state stores from `SyncRegistry`.
- ✅ Last-write-wins enforced **per document in a transaction** (incoming wins on `updatedAt` ties), matching
  the local mirror. A server timestamp (`serverAt`) is stamped on every write.
- ✅ **`SyncProvider`** now returns `FirestoreSyncRepository` when Firebase is configured, else the local
  mirror — one-line switch; callers unchanged.
- ✅ **`SyncConflictResolver`** — pure, unit-tested merge rules that protect the frozen invariants (7 tests):
  last-write-wins (server wins ties); **longest streak never decreases**; **completion is sticky**
  (monotonic — cannot re-open a completed day, so no 6th question); **canonical challenge = first-created**
  (never a merge that fabricates questions); deficit ledger merges per-section by recency.
- ✅ **`firestore.rules`** extended: `syncRecords` owner-only + shape-validated; `meta` owner-only; and the
  **server-authoritative** `challenges/{day}` + `entitlements/{doc}` are **client-read-only** (`write: if
  false` → only the Admin SDK / Cloud Functions can write them). This is what makes the canonical Daily
  Challenge and Premium entitlement impossible for a client to forge.
- ✅ **Rules-test harness source** (`firestore-tests/`): emulator tests for ownership isolation, uid/email
  non-forgeability, sync-record shape, and client-write-denied entitlements/challenges.

## What data syncs (learning state only)
Everything declared in `SyncRegistry` (analytics/sessions, gamification+streak, wrong-question pool + Leitner
mastery, daily-mission state, student profile, avatar, rewards, reports, readiness snapshots per area;
profiles/study-areas, settings, onboarding, notification + report prefs, premium **cache**, subscription
**cache**, question history, favorites, discovery). The Daily Challenge snapshot itself syncs via the
existing `DailyChallengeSync`; canonical identity becomes server-authoritative in Phase 3.

## Not done here (honest)
- 🟡 **Live two-device / offline→online / conflict / reinstall / revoked-auth behaviour** requires a real
  Firebase project + the emulator; the emulator rules-tests and the pure merge tests are provided, but the
  end-to-end integration tests run on the owner/CI machine (`firebase emulators:exec …`), not the Gradle
  build. Documented in `REAL_DEVICE_TEST_SCRIPT.md`.
- 🔵 Deploy rules/indexes (`firebase deploy --only firestore`).

## Guardrails
Content untouched (`af65df01…`). DC invariants preserved (merge rules are monotonic on completion + challenge
identity). 178/0 unit tests. No secret committed.
