# EDUmio — Owner Console Setup Guide (Phase 0.5)

This is the exact, click-by-click guide for the **external** setup only you (the owner) can perform:
Firebase, Google Play, an email provider, and hosted legal pages. The app is engineered to **keep working
in full local-fallback mode until real config is supplied** — nothing here needs to be done for internal
development to continue, but **all of it is required before a public release**.

Values that could be computed from the repository are filled in below. Values marked **`<OWNER>`** are
genuine external facts/decisions that must not be invented — supply them yourself.

> Legend: 🖥️ console action (you) · ✅ already computed for you · ⛔ do NOT let anyone fabricate this

---

## 0. Facts already known (no action needed)

| Item | Value |
|---|---|
| Android application id | `com.edumio.app` |
| Min SDK / Target-Compile SDK | 24 / 35 |
| **Debug signing SHA-1** ✅ | `60:B7:7B:EE:9E:E4:0C:4F:AE:85:E3:20:88:18:49:E0:3E:5B:38:13` |
| **Debug signing SHA-256** ✅ | `C9:D0:24:C3:79:6C:3D:1C:4F:BD:F0:19:CB:35:44:6B:E5:53:5E:B6:9F:DB:62:22:22:B9:74:C6:59:AC:36:F7` |
| Release keystore file | `edumio_release.jks` (on the build machine, git-ignored) |
| Release signing SHA-1 / SHA-256 | ⛔ compute yourself (command below — needs the keystore password we do NOT have) |

To print the **release** fingerprints (run on the build machine, substitute your alias/password):
```bash
keytool -list -v -keystore edumio_release.jks -alias <YOUR_ALIAS>
# copy the SHA1 and SHA-256 lines
```
The debug fingerprints above were computed from `~/.android/debug.keystore` (the standard debug key,
password `android`, alias `androiddebugkey`).

---

## 1. Firebase project

1. 🖥️ Go to <https://console.firebase.google.com> → **Add project**.
2. Name it (e.g. `EDUmio`). Project id will be **`<OWNER>`** (Firebase assigns/derives it).
3. Choose whether to enable Google Analytics for the project → **Yes** (we use it, Phase 7).
4. Accept terms, create.

## 2. Register the Android app

1. 🖥️ Project overview → **Add app** → Android.
2. **Android package name:** `com.edumio.app` (exact).
3. App nickname: `EDUmio Android` (optional).
4. **Debug SHA-1:** paste `60:B7:7B:EE:9E:E4:0C:4F:AE:85:E3:20:88:18:49:E0:3E:5B:38:13` (✅ above).
5. Register app.

## 3. Add all signing fingerprints

Under **Project settings → Your apps → EDUmio Android → Add fingerprint**, add:
1. ✅ Debug SHA-1 and SHA-256 (values in §0) — needed for Google Sign-In on debug builds.
2. ⛔ Release keystore SHA-1 and SHA-256 (compute with the `keytool` command in §0).
3. ⛔ **Google Play App Signing SHA-1 and SHA-256** — after you upload the first bundle, Play generates an
   *app-signing* key different from your upload key. Copy both fingerprints from **Play Console → Test and
   release → App integrity → App signing** and add them here too, or Google Sign-In will fail for
   Play-installed builds. (You cannot know these until after the first upload — do this in Phase 11.)

## 4. Download `google-services.json`

1. 🖥️ Download `google-services.json` from the app settings.
2. Place it at **`app/google-services.json`** on the build machine. **Do not commit it** — it is already
   git-ignored (see `.gitignore`). The build auto-activates Firebase only when this file is present; without
   it the app runs in local fallback.

## 5. Authentication providers

1. 🖥️ Build → **Authentication** → Get started.
2. **Sign-in method** → enable **Email/Password**. (Optionally enable *Email link* later.)
3. Enable **Google** provider. Set the **project support email** = **`<OWNER support email>`** ⛔.
4. Google provider also needs a **Web client (OAuth) ID** — Firebase auto-creates one; the app reads it from
   `google-services.json` (`default_web_client_id`). No manual client id to paste. If you use a custom
   OAuth consent screen, configure it under Google Cloud → APIs & Services → OAuth consent (§13).

## 6. Firestore

1. 🖥️ Build → **Firestore Database** → Create database.
2. Start in **Production mode** (we ship security rules — `firestore.rules`).
3. **Location/region:** **`<OWNER>`** — pick the region closest to your users (Turkey-first → `eur3`
   (europe-west) is a reasonable default). **This is permanent**; choose deliberately.
4. After creating, deploy our rules (Phase 2 delivers `firestore.rules` + `firestore.indexes.json`):
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes
   ```

## 7. Analytics & Crashlytics

1. 🖥️ Analytics is enabled from §1. Nothing else to click for basic events.
2. Build → **Crashlytics** → Enable. First crash appears after a release/debug build with the SDK runs.
3. (Phase 7) Build → **Performance Monitoring** → Enable.

## 8. Cloud Messaging (push)

1. 🖥️ Engage → **Messaging** — no key to copy for FCM v1 (uses the service account). The app registers a
   device token automatically once Firebase is configured. Server sends (Phase 5) use the Admin SDK.

## 9. Service account (for server functions)

1. 🖥️ Project settings → **Service accounts** → *Firebase Admin SDK* → **Generate new private key**.
2. This downloads a JSON key. ⛔ Treat as a top secret. Do **not** commit it. Cloud Functions use the
   project's built-in service account automatically; you only need this JSON for a non-Functions backend.

## 10. Cloud Functions / backend

1. 🖥️ Upgrade the project to the **Blaze** (pay-as-you-go) plan — Cloud Functions + outbound network
   require it. (Free tier quotas are generous.)
2. Install tooling on the build machine: `npm i -g firebase-tools`, then `firebase login`.
3. Phases 3/4 deliver the `functions/` source. Deploy with `firebase deploy --only functions`.

## 11. Google Play Billing products

1. 🖥️ Play Console → **Monetize → Products → Subscriptions** → create:
   - Monthly subscription — product id **`<OWNER>`**, price **`<OWNER>`** ⛔.
   - Yearly subscription (if you offer it) — product id **`<OWNER>`**, price **`<OWNER>`** ⛔.
2. Record the exact product ids in `docs/OWNER_ACTIONS_REQUIRED.md` (Phase 4 reads them from a config seam;
   the app must not invent ids/prices).
3. Enable **Real-time Developer Notifications** → Pub/Sub topic (Phase 4 backend consumes it).

## 12. Email provider

1. 🖥️ Choose a transactional email provider (SendGrid, Postmark, Amazon SES, etc.) — **`<OWNER decision>`**.
2. Create an API key ⛔ and verify a sending domain.
3. Configure **SPF, DKIM, DMARC** DNS records for your domain (provider gives exact values) — required for
   deliverability. Phase 6 delivers the provider abstraction; you supply the credential via server config,
   never in the repo.

## 13. OAuth consent screen (if using Google Sign-In broadly)

1. 🖥️ Google Cloud Console → **APIs & Services → OAuth consent screen**.
2. User type **External**; app name **EDUmio**; support email **`<OWNER>`** ⛔; app logo; privacy + terms
   URLs (§15); authorized domains. Submit for verification if you exceed unverified limits.

## 14. Play Data Safety form

1. 🖥️ Play Console → **App content → Data safety**. Fill truthfully from
   `docs/CURRENT_DATA_PROCESSING_INVENTORY.md` (kept current each phase): today the app collects nothing
   off-device except IAP; once Firebase/Analytics/email ship, update this and the form together.

## 15. Host legal pages

1. 🖥️ Host the **lawyer-reviewed** Privacy Policy and Terms at stable public URLs — **`<OWNER>`** ⛔ (see
   `docs/LEGAL_OWNER_INPUT_REQUIRED.md` / `docs/LEGAL_RELEASE_BLOCKERS.md`).
2. Provide the URLs to engineering; they go into `PRIVACY_POLICY_URL` / `TERMS_URL` in `app/build.gradle.kts`
   and the Play listing. Also host an **account-deletion** info URL (Play requires it once accounts exist).

---

## What happens in the app before vs after this setup

| State | Behaviour |
|---|---|
| No `app/google-services.json` (today) | Full **local fallback**: local auth stub, on-device sync mirror, Logcat analytics, bundled Remote Config defaults, local reminders. No crash. |
| `google-services.json` present + providers enabled | Firebase adapters activate behind the same seams (auth, sync, analytics, crashlytics, messaging). |
| Functions deployed + Billing products live | Server-authoritative Daily Challenge + verified Premium entitlements. |

## Verification commands (run after each step)
- Config present & parsed: build the app — if `google_app_id` resolves, `isFirebaseConfigured()` returns
  true. `./gradlew :app:assembleDebug` still succeeds either way.
- Rules deploy: `firebase deploy --only firestore:rules` (Phase 2 adds emulator tests:
  `firebase emulators:exec --only firestore "..."`).
- Functions: `firebase deploy --only functions` then check the Functions log.

**Nothing in this guide has been performed by the tooling — these are your actions. Engineering has wired
everything to activate the moment the real config lands.**
