# Phase 0 — Legal & Privacy Report

**Date:** 2026-07-19 · Branch `seeding-final-fix` · Scope: align the app's legal/privacy state with reality
and prepare (not complete) the integration. No content changed.

## What was audited
- In-app legal docs (`legal/LegalConfig` + `assets/*_tr.html`), the `BuildConfig` legal URLs, and all
  advertising/data-safety disclosure material.

## Findings & actions

| Finding | Severity | Action taken |
|---|---|---|
| `ad_info` legal doc claimed **"the app uses Google AdMob"** — false (no ads SDK, no AdMob) | **High (Data-Safety inaccuracy)** | **Removed** the doc from `LegalConfig.allDocuments()` + its `ASSET_AD_INFO` const + `legal_ad_info_*` strings + `assets/ad_info_tr.html` |
| Stale Barjin-era `parent_info` doc (parental-control) still referenced by const | Low (dead/stale) | **Removed** the const + `assets/parent_info_tr.html` (was already absent from the doc list) |
| Privacy Policy + Terms are **drafts** (marked "yayına hazır DEĞİLdir") | **High (blocks public release)** | Left as drafts; confirmed the app does **not** present them as final (web button hidden while URLs empty). Owner checklist created. |
| `PRIVACY_POLICY_URL` / `TERMS_URL` empty in `BuildConfig` | High (Play requires a hosted privacy URL) | Left empty (owner must host + provide); integration point documented |
| `data_usage_tr.html` | — (verified honest) | No change — it already states there are **no ads, no microphone, no server data transfer** today |
| "Watch Ad" (Reklam İzle) button copy remains though unlock is ad-free | Low (UX, not a disclosure) | **Documented** as a product follow-up (not a release blocker); not changed in Phase 0 to keep the change scoped to disclosures |

## Result
- The app no longer makes any **false advertising / data-sharing disclosure**. The active legal set is:
  Privacy Policy (draft), Terms (draft), Data Usage (honest). Compile verified after removal.
- Public release remains **correctly blocked** on: a lawyer-reviewed Privacy Policy + Terms hosted at real
  URLs and injected via `BuildConfig`. See **`LEGAL_OWNER_INPUT_REQUIRED.md`** (13 owner-only items) and
  **`CURRENT_DATA_PROCESSING_INVENTORY.md`** (Data Safety truth).

## Recommendation
Internal QA / closed beta can proceed (no false claims remain). **Public Play Store release stays blocked**
until the owner supplies the hosted, legally-reviewed policy + terms and the 13 checklist values.
