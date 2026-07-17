# EDUmio Brand — Legal & Policy Audit

Scope: brand references and validity of every legal/policy surface. Detailed data-processing gap analysis
is in `legal_gap_report.md`; this file is the brand-focused audit + status.

## Documents reviewed
| Document | Where | Old brand | Action |
|---|---|---|---|
| Privacy Policy (in-app, live) | `assets/privacy_policy_tr.html` (WebView via `LegalConfig`/`PrivacyPolicyActivity`) | Barjin | Rebranded EDUmio + stale notice; false claims removed |
| Terms of Use (in-app, live) | `assets/terms_of_use_tr.html` | Barjin | Rebranded EDUmio + stale notice |
| Data Usage (in-app, live) | `assets/data_usage_tr.html` | Barjin | Rebranded EDUmio + stale notice |
| Ad Info (in-app, live) | `assets/ad_info_tr.html` | Barjin | Rebranded; states **EDUmio has no ads** (page obsolete) |
| Parent Info (in-app, live) | `assets/parent_info_tr.html` | Barjin | Rebranded; states EDUmio is **not** a parental-control app (obsolete) |
| Privacy Policy (web) | `docs/privacy.html` | BrainBuddy | Rebranded EDUmio + stale notice |
| Terms (web) | `docs/terms.html` | BrainBuddy | Rebranded EDUmio + stale notice |
| Privacy DRAFT | `docs/privacy_edumio_draft.md` (new) | — | Clean draft, placeholders, "DRAFT — REQUIRES LEGAL REVIEW" |
| Terms DRAFT | `docs/terms_edumio_draft.md` (new) | — | Clean draft, placeholders, "DRAFT — REQUIRES LEGAL REVIEW" |

## Why the old bodies were removed, not just rebranded
All described a different, historical product (Barjin/BrainBuddy parental-control app): app-blocking via
Accessibility Service, encrypted parent PIN, Device Admin, child speech-recognition/microphone, and
Google AdMob/COPPA. **None exists in EDUmio** (exam prep; no ads, no parental control, local-only data).
Per instruction, inaccurate legal claims were **not** preserved; the pages now carry an honest
"not production-ready / under revision" notice and point to the DRAFTs.

## Not invented
No legal entity name, address, contact e-mail, data-controller identity, lawful basis, retention period,
third-party processor, jurisdiction, pricing, or refund term was fabricated. Unknowns are explicit
`[PLACEHOLDER]`s. No sender domain/address was invented for the (unbuilt) email system — email/report
placeholders use the display name **EDUmio** only.

## Cookie / consent / marketing / billing surfaces
- No separate Cookie Policy, Data-Processing Agreement, Marketing/Email-Consent, or Subscription/Refund
  legal documents exist in the repo today (the app is local-only, ads-free, billing not live). These must
  be authored before the corresponding features go live — tracked in `legal_gap_report.md`.

## Release gate
- **PUBLIC-RELEASE BLOCKER:** EDUmio must not ship to public release on the current legal text. The live
  pages are deliberately non-binding notices; the DRAFTs need legal review + all placeholders filled
  (legal entity, address, contact, controller, lawful bases, retention, processors, jurisdiction,
  billing terms).
- Obsolete legal entries (`ad_info`, `parent_info`) describe non-existent features and should be removed
  from the in-app Legal hub by product before release (menu wiring is a product change, out of this
  branding pass).

## Status
Brand references in all legal/policy documents now use **EDUmio**; no historical brand remains in any
legal surface. Legal **content** validity is a documented public-release blocker, not a branding issue.
