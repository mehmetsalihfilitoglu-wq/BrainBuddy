# EDUmio — External Credentials Required

Every secret/value the app needs from an external system. **None of these may be invented** (Firebase ids,
API keys, OAuth ids, passwords, signing secrets, prices, legal facts). Each row states where the value comes
from, where it is consumed, and the safe (never-committed) place it lives.

| Value | Source | Consumed by | Storage (never commit) |
|---|---|---|---|
| `google-services.json` | Firebase console (§4 guide) | Google Services Gradle plugin → `google_app_id`, API key, `default_web_client_id` | `app/google-services.json` (git-ignored) |
| Firebase Web/OAuth client id | inside `google-services.json` | Google Sign-In (Credential Manager) | from the JSON, not hand-entered |
| Firebase Admin service-account key | Firebase console → Service accounts | non-Functions backend only (Functions use built-in identity) | secret manager / server env — never repo |
| Release keystore + passwords | owner's `edumio_release.jks` | release signing | `keystore.properties` (git-ignored) — see `PHASE0_RELEASE_SIGNING_REPORT.md` |
| Play subscription product ids | Play Console (owner-created) | Billing config seam (Phase 4) | `docs/OWNER_ACTIONS_REQUIRED.md` + a non-secret config file/Remote Config |
| Play RTDN Pub/Sub topic | Play Console + GCP | Phase 4 entitlement function | server config |
| Email provider API key | email provider (owner) | Phase 6 email provider adapter (server) | server env / secret manager |
| Email sending domain + SPF/DKIM/DMARC | owner DNS | deliverability | DNS (owner) |
| Hosted Privacy Policy URL | owner hosting | `PRIVACY_POLICY_URL` build field + Play listing | build config (non-secret) |
| Hosted Terms URL | owner hosting | `TERMS_URL` build field + Play listing | build config (non-secret) |
| Account-deletion info URL | owner hosting | Play listing | build config (non-secret) |

## How the app behaves with each value absent
Each is gated: absent → the corresponding subsystem uses its **local fallback** and the feature is marked
*externally blocked* in the master report. No absent credential causes a crash or blocks the core learning
loop.

## Verification per credential
- `google-services.json`: after placing it, `./gradlew :app:assembleDebug` succeeds and
  `isFirebaseConfigured()` (resource probe for `google_app_id`) returns true.
- Release signing: `./gradlew :app:bundleRelease` signs with the real key (guard passes).
- Product ids / prices: injected via the billing config seam; the app never hardcodes them.
