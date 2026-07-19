# Legal — Owner Input Required (Public-Release Blocker)

**Status: BLOCKING public Play Store release.** The in-app Privacy Policy and Terms are **drafts** and are
correctly marked as not-for-publication. The app does **not** claim they are production-ready (the
"view on web" button is hidden while the hosted URLs are empty). Public release must stay blocked until a
lawyer-reviewed policy + terms are hosted and the values below are supplied by the owner.

The engineering integration is ready: fill `PRIVACY_POLICY_URL` / `TERMS_URL` in
`app/build.gradle.kts` (`buildConfigField`s, currently `""`) and replace the draft HTML in
`app/src/main/assets/{privacy_policy_tr,terms_of_use_tr}.html` with the finalized text.

## Items only the owner/lawyer can provide

| # | Item | Where it's needed |
|---|---|---|
| 1 | **Legal person / company name** (or sole-proprietor name) | Privacy + Terms header, Play listing |
| 2 | **Data controller identity** (KVKK "veri sorumlusu" / GDPR controller) | Privacy Policy |
| 3 | **Registered postal address** | Privacy + Terms |
| 4 | **Support email** | Privacy + Terms + Play listing |
| 5 | **Privacy contact email** (may equal support) | Privacy Policy |
| 6 | **Data retention periods** (per data category) | Privacy Policy |
| 7 | **Processors / service providers** (Google Firebase, Play Billing, email ESP — once wired) | Privacy Policy, Data Safety |
| 8 | **Subscription cancellation & refund terms** (align with Play + EU/TR consumer law) | Terms + paywall |
| 9 | **Governing country / jurisdiction** | Terms |
| 10 | **Minimum age & parental-consent position** (COPPA / KVKK / GDPR-K; the app targets ~18yo exam prep) | Privacy + Play "target age" |
| 11 | **Hosted Privacy Policy URL** (public, stable) | `PRIVACY_POLICY_URL`, Play Data Safety, Play listing |
| 12 | **Hosted Terms URL** (public, stable) | `TERMS_URL`, Play listing |
| 13 | **Legal review sign-off** of the final policy + terms | Gate for upload |

## What Phase 0 already fixed
- Removed the **false AdMob disclosure** (`ad_info` legal doc claimed "the app uses Google AdMob" — it
  does not; there is no ads SDK). See `CURRENT_DATA_PROCESSING_INVENTORY.md`.
- Removed the stale Barjin-era `parent_info` doc.
- Confirmed `data_usage_tr.html` is honest (states no ads / no microphone / no server data transfer today).

## Owner action
1. Engage a lawyer to finalize Privacy Policy + Terms with the values above (Turkey-first audience → KVKK
   + GDPR; note EU users).
2. Host both at stable public URLs.
3. Provide the URLs + finalized HTML to engineering; we inject them and flip the release from
   draft-blocked to legally-ready. **Until then, do not publish.**
