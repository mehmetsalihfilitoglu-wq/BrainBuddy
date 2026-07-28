# Phase 7 — Email Lifecycle Report

**Branch:** `seeding-final-fix` · **Not pushed.** ✅ validated · 🔵 provider credential owner-gated.

Email **verification** + **password reset** are sent **natively by Firebase Auth** (owner customizes the
templates in console). This layer governs everything else: **transactional** (receipts, security, deletion
confirmation), **learning** (weekly/monthly reports, due-review + streak reminders), and **marketing**.

## Delivered — ✅
- **Pure governance logic** (`EmailPolicy`, 5 tests): transactional always sends; **learning is opt-out**
  (on by default, user-controllable); **marketing is opt-in** (explicit consent); the **daily reminder email
  is ≤1/day and stops on completion**; frequency caps; idempotency/dedup; bounce/complaint suppression;
  transactional cannot be unsubscribed.
- **`functions/email.js`** (source): a **provider seam** (`sendViaProvider` — owner wires SendGrid/SES/
  Postmark; credential from Secret Manager, never the repo) with governed `sendEmail` (suppression → dedup →
  consent → send) and an `emailWebhook` that records hard bounces/complaints as suppression. `node --check`
  clean; re-exported from `index.js`.
- **`firestore.rules`**: `users/{uid}/preferences/*` owner-managed (consent + notification/email prefs);
  `emailSuppression/*` and `emailSends/*` entirely server-only.

## Guarantees
- **No solution bodies or answer data are ever emailed** — by contract, callers pass only safe report
  summaries (mirrors the analytics no-content rule).
- Weekly/monthly reports use **real user data** (the existing on-device report generator), never fabricated
  numbers.

## Not done here (honest) — 🔵
- Owner chooses a provider, creates the API key, verifies the sending domain, and sets SPF/DKIM/DMARC (guide
  §12). Until then `sendViaProvider` throws a clear "not configured" error and nothing is sent. Live
  send/bounce/unsubscribe flows validate on the owner's provider.

## Guardrails
Content untouched (`af65df01…`). 205/0 JVM tests. No secret committed.
