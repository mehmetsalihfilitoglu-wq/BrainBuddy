# Phase 0 — Release Blockers (tiered)

**Date:** 2026-07-19 · Branch `seeding-final-fix`. Blockers are separated by **release stage** — something
that blocks a public launch may be perfectly fine for internal QA. A stage is clear only when **all** its
blockers are resolved. Phase 0 cleared every *local/engineering* blocker; the rest are owner- or
Phase-1-dependent.

Legend: ✅ done in Phase 0 · 🔵 owner input · 🟠 Phase 1+ engineering · ⚪ optional/nice-to-have

---

## Tier 1 — Internal QA (dogfooding on our own devices) — **UNBLOCKED**
Everything needed to build, install, and exercise the app internally is done.

| Item | Status |
|---|---|
| Debug build succeeds (`assembleDebug`, ~79.2 MB) | ✅ |
| Full unit suite green (155/0) | ✅ |
| No false legal/ads disclosure | ✅ (AdMob disclosure removed) |
| Room schemas frozen + migration-safety test | ✅ |
| Brand-compliance gate green | ✅ (cleaned pre-existing violation) |
| Content layer intact (banks/keys/figures/solutions/DC) | ✅ (untouched) |

**Verdict: internal QA can proceed now.**

---

## Tier 2 — Closed Beta (Play internal/closed track, real testers) — **A FEW BLOCKERS**
Needs a signed artifact and truthful store metadata, but not the full public legal surface.

| Item | Status | Owner |
|---|---|---|
| Secure release signing wired (no secret in git) | ✅ | — |
| Real `keystore.properties` + `edumio_release.jks` on the build machine | 🔵 | Owner supplies at build time (`PHASE0_RELEASE_SIGNING_REPORT.md`) |
| Signed release build (`bundleRelease`) verified once | 🟠 | Run once with real keystore |
| Play Data Safety form filled truthfully (no ads; IAP; on-device data) | 🔵 | Owner (`CURRENT_DATA_PROCESSING_INVENTORY.md`) |
| A privacy policy URL reachable (Play requires one even for closed testing) | 🔵 | Owner hosts (can be the reviewed draft) |
| `versionCode`/`versionName` bump policy | ⚪ | currently 3 / 1.2 |

**Verdict: closed beta is blocked only on owner-supplied signing + a hosted privacy URL + the Data Safety
form.** No further engineering required for a closed track.

---

## Tier 3 — Public Production Release — **BLOCKED (owner + Phase 1)**

### 3a. Legal / compliance (owner) — see `LEGAL_OWNER_INPUT_REQUIRED.md`
| Item | Status |
|---|---|
| Lawyer-reviewed Privacy Policy + Terms, hosted at stable URLs | 🔵 |
| `PRIVACY_POLICY_URL` / `TERMS_URL` filled in `BuildConfig` | 🔵 (integration ready) |
| 13 owner-only items (legal identity, address, contacts, retention, refund terms, jurisdiction, min-age) | 🔵 |
| Subscription terms + cancellation/refund copy aligned with Play + EU/TR law | 🔵 |

### 3b. Product / monetization readiness
| Item | Status |
|---|---|
| Play Console subscription products created + priced | 🔵 |
| "Reklam İzle" (Watch Ad) button copy renamed to honest ad-free unlock wording | ⚪ product polish (documented; not a disclosure risk) |
| A real (even if small) question bank per launch exam | 🔵 product decision |

### 3c. Trust / backend (Phase 1+) — see `PHASE0_FIREBASE_READINESS_REPORT.md`, `PHASE0_DC_STREAK_TRUST_REPORT.md`
| Item | Status |
|---|---|
| Server purchase verification (premium is client-cached, non-authoritative today) | 🟠 Phase 3 |
| Server time authority for streak/Daily-Challenge (resists device-clock manipulation) | 🟠 Phase 3 |
| `daily_challenge.db` destructive-fallback → tested additive migration | 🟠 before first real users persist state |
| Account/auth UI wired to a real backend (currently local stub) | 🟠 Phase 1 |
| Crash reporting (Crashlytics) for a production audience | 🟠 Phase 1 |

**Verdict: public release is blocked primarily on owner legal input + Phase-1/3 backend trust work. None of
these were in Phase 0's scope; they are documented, not started.**

---

## One-line summary
- **Internal QA:** go.
- **Closed beta:** owner supplies signing creds + hosted privacy URL + Data Safety form, then go.
- **Public release:** blocked on lawyer-reviewed hosted legal docs (13 owner items) and Phase-1/3 backend
  trust (server purchase verification, server time authority, real auth/crash reporting).
