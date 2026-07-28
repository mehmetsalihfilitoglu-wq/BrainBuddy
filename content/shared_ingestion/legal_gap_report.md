# EDUmio — Legal Gap Report

**Date:** 2026-07-17 · **Trigger:** brand migration (BrainBuddy → EDUmio) surfaced that the shipped legal
pages describe a **different, historical product**. This report documents the gap and blocks public release
of the current legal text.

## 1. The problem
`docs/privacy.html` and `docs/terms.html` (linked from the in-app Legal hub) described the pre-pivot
**parental-control children's app**, not the current EDUmio exam-prep app. Every substantive clause was
inaccurate for EDUmio. Action taken this migration: rebranded to EDUmio, **removed the inaccurate claims**,
and replaced the body with a clear "revizyon aşamasında / not production-ready" stale notice. Clean DRAFTs
were authored (`docs/privacy_edumio_draft.md`, `docs/terms_edumio_draft.md`) based only on the implemented
product, with `[PLACEHOLDER]`s for unknown legal facts.

## 2. Obsolete / inaccurate sections in the OLD pages (now removed)
| Old clause | Why it is wrong for EDUmio |
|---|---|
| "ebeveyn kontrolü / cihaz kullanımını yönetme" (parental control) | EDUmio has no parental-control feature. |
| Hedef kitle: "18 yaş üstü ebeveynler … çocukların gözetiminde" | EDUmio is a self-serve exam-prep app, not a supervised-child app. |
| PIN / acil durum kodu (encrypted parent PIN) | No PIN/parent-security concept exists. |
| "Sesli Okuma" / SpeechRecognizer / mikrofon izni (BrainBuddy Junior) | No speech recognition, no microphone permission. |
| Google AdMob / COPPA / ödüllü reklam | EDUmio ships **no ads** (RewardedAdManager is a no-op stub). |
| Android Erişilebilirlik Hizmeti (app blocking) | No accessibility service / app-blocking. |
| Cihaz Yöneticisi (Device Admin) | No device-admin permission. |
| "Çocuk gizliliği" bölümü | Not a children's product as described. |
| Terms: "uygulama engelleme", intellectual-property clause referencing blocking | Feature does not exist. |

## 3. What EDUmio ACTUALLY does today (verified against source)
- Offline-first exam prep for IMAT / TIL-I / CEnT-S; content bundled in-app.
- **All user data is local** (Room DBs `brainbuddy.db` + `daily_challenge.db`, SharedPreferences). No
  personal data is transmitted to any server in the current build.
- Anonymous local user id (random UUID) or local auth stub; **Firebase/server auth is interface-only, not
  wired**.
- Local, in-memory analytics (bounded ring buffer); **no remote sink**; question stems/answers are never
  logged (ids + coarse params only).
- Optional performance reports sent via the **device's own** email/share client (user-initiated); **no
  server-side email**.
- Local notifications (POST_NOTIFICATIONS on Android 13+), requested contextually.
- Premium = local entitlement flag; **Play Billing library present but live purchases not enabled**.

## 4. Planned but NOT implemented (must update legal BEFORE enabling)
- Backend sync + server authentication (Firebase).
- Server-side entitlement/subscription verification.
- Server-delivered email (daily/weekly reports, re-engagement) + consent/suppression.
- Remote analytics collection.
- Live payments (Play Billing).
Each of these introduces data transfer / processors / retention that the current local-only policy does not
cover.

## 5. Information still required from the business/legal owner (placeholders in the drafts)
1. Legal entity name (data controller).
2. Registered business address.
3. Contact / DPO / KVKK e-mail. *(Also needed to replace any "future email placeholder" — none is invented.)*
4. Data controller identity + representative (if EU/TR).
5. Lawful bases (KVKK / GDPR) per processing activity.
6. Retention periods (local and, later, server).
7. Third-party processors (Google Play Billing, Firebase, email/analytics vendors — once enabled).
8. Governing law / jurisdiction for the Terms.
9. Minimum age / eligibility and children's-data stance for an exam-prep audience.
10. Subscription pricing, renewal, refund, and cancellation terms (when billing goes live).
11. Effective date + version once approved.

## 6. Release gate
- **PUBLIC-RELEASE BLOCKER:** EDUmio must NOT ship to public release with the current legal pages. The live
  `privacy.html`/`terms.html` are intentionally reduced to a non-binding "under revision" notice; the DRAFTs
  require legal review and completion of every `[PLACEHOLDER]` before they may replace them.
- No compliance claim, company detail, address, retention period, lawful basis, or processor has been
  invented. This report and the drafts contain only verified product behaviour and explicit placeholders.
