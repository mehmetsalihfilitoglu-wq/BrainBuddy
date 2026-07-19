# EDUmio — Legal Release Blockers

Honest, living list of legal items that **block public release** until the owner + a lawyer supply them.
Engineering will not fabricate legal text or facts. Detailed checklist: `docs/LEGAL_OWNER_INPUT_REQUIRED.md`.

| # | Blocker | Blocks | Owner input | Status |
|---|---|---|---|---|
| 1 | Lawyer-reviewed **Privacy Policy** hosted at a stable URL | Public release (Play requires a privacy URL) | legal entity, controller, retention, processors, contacts | ☐ |
| 2 | Lawyer-reviewed **Terms of Service** hosted | Public release | jurisdiction, subscription/cancellation/refund terms | ☐ |
| 3 | `PRIVACY_POLICY_URL` / `TERMS_URL` filled in `build.gradle.kts` | In-app legal links | the two hosted URLs | ☐ |
| 4 | **Account-deletion** policy + info URL | Public release (accounts exist from Phase 1) | hosted URL + confirmed deletion scope | ☐ |
| 5 | **Data controller** identity + postal address | Privacy Policy, Play | legal entity facts | ☐ |
| 6 | **Retention periods** per data category | Privacy Policy | owner/legal decision | ☐ |
| 7 | **Subprocessors** list (Google Firebase, Play, email provider) | Privacy Policy, Data Safety | finalize once email provider chosen | ☐ |
| 8 | **Minimum age / parental-consent** stance (KVKK/GDPR-K/COPPA) | Privacy + Play target-audience | owner/legal decision | ☐ |
| 9 | **Subscription refund + cancellation** terms aligned with Play + EU/TR consumer law | Terms + paywall | owner/legal decision | ☐ |
| 10 | Marketing-email **consent** basis + records language | Phase 6 marketing email | owner/legal decision | ☐ |

**Engineering side is ready:** in-app privacy/terms viewers exist (drafts, not presented as final), the URL
injection points are wired, and account-deletion is implemented across all shipped systems (Phase 10). The
blockers above are strictly owner/legal deliverables.
