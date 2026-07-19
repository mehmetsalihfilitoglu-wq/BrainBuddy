# Phase 3 — Server-Authoritative Daily Challenge Report

**Branch:** `seeding-final-fix` · **Not pushed.** ✅ validated · 🟡 runtime owner-gated (needs deployed
Functions) · 🔵 owner/console.

## Design (why this shape)
The question banks are **bundled in the app**, so moving *selection* server-side would mean shipping content
to a server — out of scope and unnecessary. Instead the server owns **identity**:

> Client computes the canonical day + proposes today's 5 question ids → **server transaction** creates
> exactly ONE immutable challenge per (uid, day) (first-writer-wins across devices) → client stores it and
> answers **offline** → completion syncs **idempotently** and the server owns the streak.

This makes the canonical challenge and streak **impossible to forge or duplicate** and defeats
clock/timezone manipulation, without touching content or the frozen local engine.

## Delivered — ✅
- **Cloud Functions source** (`functions/`, Node 20, not deployed by the Android build):
  - `claimDailyChallenge` — transaction: return the existing immutable challenge, or create one from a
    validated proposal (**exactly 5 distinct ids**). Two racing devices converge on the first-created.
  - `completeDailyChallenge` — completion **only for the server's current canonical day** (defeats
    back/forward-dating), **monotonic** (idempotent if already completed), and recomputes the
    **authoritative streak** from consecutive completed days (consecutive→+1, gap→reset, longest never
    decreases).
  - `onUserCreate` — writes the minimal `users/{uid}` profile server-side on account creation (the
    trustworthy Phase-1 profile writer; client only updates preference fields).
  - Pure logic extracted to `functions/lib/challenge.js` (canonicalDay, isValidProposal, nextStreak) with
    **mocha tests** (`functions/test/`); all files pass `node --check` and the pure assertions run green in
    Node here.
- **`ServerChallengeContract`** (Kotlin, pure) — the same rules the client relies on: idempotency key
  `dailyChallenge:{uid}:{day}`, 5-distinct proposal validation, completion-only-for-current-day, monotonic
  completion, must-return-existing. **6 JVM tests.**
- **`firestore.rules`**: `challenges/{day}`, `entitlements/{doc}`, `learningState/{doc}` are all
  **client-read-only** (`write: if false`) — only the Admin SDK (Functions) writes them. `firebase.json`
  wires the functions source + emulator.

## Invariants preserved
Exactly one immutable challenge per account per day (server transaction); exactly 5 (validated server-side +
`ServerChallengeContract`); no 6th; completion monotonic; streak authoritative + non-decreasing. The **local
engine is unchanged** — it remains the offline fallback and the selection source.

## Not done here (honest)
- ⏭️ **Client wiring** of a `ChallengeAuthority` seam (call `claimDailyChallenge`/`completeDailyChallenge`
  when Firebase is configured, fall back to the local engine otherwise) is the remaining integration; it is
  deliberately deferred because it needs the **deployed** functions + a device to validate without risking
  the frozen local DC flow.
- 🟡 Deploy + adversarial runtime tests (clock/timezone/2-device/replay) run on the owner's Blaze project via
  `firebase deploy --only functions` + emulator; the pure logic both sides share is tested here.

## Guardrails
Content untouched (`af65df01…`). 184/0 JVM tests. Functions JS `node --check` clean. No secret committed.
