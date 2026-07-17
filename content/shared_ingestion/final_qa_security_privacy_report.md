# Final QA — Security & Privacy Report

Scope: the Daily Challenge feature and its immediate surroundings, current **local-app** state. Backend
and email are **not implemented** (out of scope this phase); their requirements are separated below.

## Current local-app posture (audited)
| Check | Result |
|---|---|
| Credentials / secrets / API keys in DC code | **None** (grep of `dailychallenge/` clean) |
| Private local paths / usernames shipped | **None** in DC code/assets; asset image paths are relative (`til_i/figures/…`) |
| Exported Android components | Only `MainActivity` is `exported="true"` (the launcher, no extras consumed). All DC activities are `exported="false"` — not launchable by other apps. |
| PendingIntent usage | **None** in DC (notifications carry no PendingIntent → no mutability/intent-injection surface) |
| WebView usage | **None** in DC |
| SQL parameterization | All `DailyChallengeDao` queries use Room named parameters (`:userId`, `:examType`, …) — no string concatenation, no injection surface |
| Unsafe file-path / asset traversal | Image paths validated (no `..`), opened from bundled assets under a fixed namespace; content is app-shipped and integrity-checked (validator §5) |
| External storage | **None** used by DC |
| Sensitive debug logging | `LocalDailyChallengeAnalytics` logs event **names + minimal params** (exam, date, slot, is_correct, score) via `Log.i` — no question stems, answer text, or PII. Bounded in-memory ring buffer; nothing leaves the device. |
| Anonymous user id | `DailyChallengeUser.resolve` uses the auth id when signed in, else a locally-generated random UUID (`anon_<uuid>`). Not derived from device identifiers; not PII. |
| DB access boundaries | `daily_challenge.db` is app-private (Room, internal storage); separate from `brainbuddy.db` (no schema/migration coupling) |
| Entitlement tampering | Premium is read from `EntitlementProvider` (currently local `PremiumStore`). A local flag is trivially tamperable on a rooted device, but tampering only widens **review depth** — it can never increase the 5-new-per-day count (enforced independently of entitlement; `DailyChallengeEntitlementPolicyTest`). Server-side entitlement verification is a backend requirement (below). |
| Backup behaviour | Uses the app's existing backup configuration; `daily_challenge.db` is local app data. No new backup surface introduced by DC. |

## Privacy — what is logged / stored
- **Stored locally:** per-user challenge/answer/state/streak/deficit rows in `daily_challenge.db`; a local
  anon id; reminder date markers. All device-local.
- **Analytics:** event names + coarse params, in-memory only (no network sink wired). Question content is
  referenced by **id**, never by stem/answer text — satisfies "don't log stems/answers when ids suffice".
- **No compilation of personal data**, no external transmission in the current build.

## Explicitly separated: future backend/email requirements (NOT implemented, NOT audited as shipped)
When the backend/email seams are activated, the following MUST be addressed (they are not in this build):
- Server-side entitlement verification (so a tampered local Premium flag can't unlock server features).
- Transport security + auth for sync of challenge/review state.
- Email consent, unsubscribe, and suppression handling (spec exists in
  `daily_challenge_notification_email_spec.md`; code not built).
- Anonymous→signed-in state migration privacy (merge/ownership rules).
- Data-retention / deletion (GDPR/KVKK) for synced user state.

## Compliance statement (honest)
No compliance certification is claimed. This is an assessment of the **current local build's** security
posture only. The local build ships no credentials, exposes no DC component, transmits no user data, and
logs no sensitive content. Backend/email compliance is future work and is not represented as done.

## Net
No security or privacy **defect** found in the current Daily Challenge build. One design note (local
entitlement is tamper-widenable but cannot break the new-question invariant) is documented; server-side
enforcement is deferred with the backend.
