# EDUmio — Owner Actions Required

Console actions and business decisions only the owner can perform. Engineering has wired the app to activate
each capability the moment its input lands. **Nothing here is fabricated by the tooling.** Full click-steps
are in `docs/OWNER_CONSOLE_SETUP_GUIDE.md`; the specific secret *values* are tracked in
`docs/EXTERNAL_CREDENTIALS_REQUIRED.md`.

Status: ☐ not started · ◐ in progress · ☑ done

| # | Action | Needed for | Status |
|---|---|---|---|
| 1 | Create Firebase project; register `com.edumio.app`; add debug SHA-1/256 (computed — see guide §0) | Phase 1+ | ☐ |
| 2 | Add **release** keystore SHA-1/256 + (after first upload) **Play App Signing** SHA-1/256 | Google Sign-In on release | ☐ |
| 3 | Download `google-services.json` → `app/google-services.json` (git-ignored) | Activates all Firebase | ☐ |
| 4 | Enable Email/Password + Google auth providers; set support email | Phase 1 auth | ☐ |
| 5 | Create Firestore (Production mode); choose region; deploy `firestore.rules` | Phase 2 sync | ☐ |
| 6 | Enable Crashlytics + Performance Monitoring | Phase 7 | ☐ |
| 7 | Upgrade to Blaze plan; `firebase login`; deploy `functions/` | Phases 3/4 backend | ☐ |
| 8 | Create Play subscription products; record exact product ids | Phase 4 billing | ☐ |
| 9 | Enable Play Real-time Developer Notifications (Pub/Sub) | Phase 4 entitlement | ☐ |
| 10 | Choose email provider; create API key; verify domain; set SPF/DKIM/DMARC | Phase 6 email | ☐ |
| 11 | Configure OAuth consent screen | Google Sign-In at scale | ☐ |
| 12 | Host lawyer-reviewed Privacy Policy + Terms + account-deletion URL | Public release | ☐ |
| 13 | Fill Play Data Safety, content rating, target audience | Public release | ☐ |
| 14 | Provide subscription price(s), refund policy, jurisdiction, min-age, legal entity, address, contacts | Legal + billing | ☐ |

**Business decisions (not credentials) still required:** subscription price(s); whether to offer a yearly
plan and/or free trial; refund policy; governing jurisdiction; minimum-age / parental-consent stance;
Firestore region; email provider choice.
