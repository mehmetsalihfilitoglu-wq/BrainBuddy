# EDUmio — Owner Activation Checklist (live environment)

**One ordered, dependency-first list of the external actions only you can perform.** Repository-side code is
complete; nothing here is a placeholder. Do the steps in order — each says what depends on it. Values shown
as `code` are **exact, from the repository** (use them verbatim). "You choose" = a real decision/secret I must
not invent. "Return to me" = the file/identifier to hand back so I can wire it in (a small config edit, not
new architecture).

**Repo constants you'll reuse:**
- Application id: `com.edumio.app`
- Debug SHA-1: `60:B7:7B:EE:9E:E4:0C:4F:AE:85:E3:20:88:18:49:E0:3E:5B:38:13`
- Debug SHA-256: `C9:D0:24:C3:79:6C:3D:1C:4F:BD:F0:19:CB:35:44:6B:E5:53:5E:B6:9F:DB:62:22:22:B9:74:C6:59:AC:36:F7`
- Config file destination: `app/google-services.json` (git-ignored — never commit)
- Cloud Functions region: `europe-west1` · Node runtime: `nodejs20`
- Subscription product ids (hardcoded): `edumio_premium_monthly`, `edumio_premium_yearly`
- RTDN Pub/Sub topic (hardcoded): `play-rtdn`
- Rules file: `firestore.rules` (wired via `firebase.json`)

---

## STAGE A — Firebase core (unblocks the app)

### A1. Create the Firebase project  *(spec #1)*
- **Console:** Firebase — <https://console.firebase.google.com>
- **Path:** *Add project* → name it (e.g. `EDUmio`) → keep **Enable Google Analytics = ON** → create.
- **You choose:** project name (→ Firebase derives the **project id**).
- **Return to me:** the **project id**.
- **Verify:** the project dashboard loads.
- **Depends on / unblocks:** nothing before it; **everything** depends on this.

### A2. Confirm the Debug SHA fingerprints  *(spec #3)* — already computed
- **Console:** none (local). **Value (exact):** the Debug SHA-1/256 above (from `~/.android/debug.keystore`).
- **Verify (optional):** `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` → matches.
- **Unblocks:** A3 (registration), A6 (Google sign-in on debug).

### A3. Register the Android app  *(spec #2)*
- **Console:** Firebase → *Project settings* → *Your apps* → **Add app → Android**.
- **Path / values:** *Android package name* = `com.edumio.app` (exact) · nickname `EDUmio Android` (optional)
  · *Debug signing certificate SHA-1* = paste the Debug SHA-1 from A2.
- **Verify:** the app appears under *Your apps*.
- **Depends on:** A1, A2. **Unblocks:** A4, A6.

### A4. Download & place `google-services.json`  *(spec #5)*
- **Console:** Firebase → *Project settings* → *Your apps* → EDUmio Android → **Download google-services.json**.
- **Action:** place it at **`app/google-services.json`** on the build machine. **Do not commit** (already
  git-ignored).
- **Return to me:** confirmation it's placed (or the file, if you want me to verify locally). *Do not paste its
  contents in chat.*
- **Verify:** `./gradlew :app:assembleDebug` logs **"google-services.json found → Firebase build plugins ACTIVE."**
  (without it, it logs "Firebase inactive").
- **Depends on:** A3. **Unblocks:** every runtime Firebase feature (auth, Firestore, analytics, FCM, App Check).

### A5. Enable Email/Password authentication  *(spec #6)*
- **Console:** Firebase → **Build → Authentication → Get started → Sign-in method**.
- **Path:** enable **Email/Password**.
- **Verify:** provider shows *Enabled*.
- **Depends on:** A1. **Unblocks:** email sign-up/login/verification/reset (all native).

### A6. Enable Google authentication  *(spec #7)*
- **Console:** Firebase → Authentication → Sign-in method → **Google → Enable**.
- **You choose:** **project support email** (a real address you control).
- **Note:** the OAuth **web client id** is auto-created and read from `google-services.json`
  (`default_web_client_id`) — nothing to paste. Google sign-in on **release** also needs the release + Play
  App Signing SHAs (A11 / C2).
- **Return to me:** the support email (for docs) — the client needs no value.
- **Verify:** provider shows *Enabled* with a support email.
- **Depends on:** A3, A4. **Unblocks:** Google sign-in.

### A7. Create Firestore + choose region  *(spec #8)*
- **Console:** Firebase → **Build → Firestore Database → Create database**.
- **Path:** **Production mode** (rules ship in the repo) → **Location**.
- **You choose (PERMANENT):** the region. **Recommended: `eur3` (Europe multi-region)** — Turkey-first + it
  contains `europe-west1`, which the Cloud Functions use. ⚠️ **If you pick a region outside Europe, return it
  to me** so I align the Functions `region` constant before deploy (C-stage).
- **Return to me:** the chosen region (only if not `eur3`/Europe).
- **Verify:** Firestore console shows an empty database in the chosen region.
- **Depends on:** A1. **Unblocks:** B2 (rules deploy), all sync/entitlement/challenge data, C-stage functions.

### A8. Enable Analytics, Crashlytics, Cloud Messaging  *(spec #13, #14, #15)*
- **Analytics:** already on from A1 (nothing to click).
- **Crashlytics:** Firebase → **Release & Monitor → Crashlytics → Enable**. (First report appears after a
  build with the SDK runs — A4 done.)
- **Cloud Messaging:** Firebase → **Engage → Messaging** — no key to copy (FCM v1 uses the service account);
  the app registers its token automatically once A4 is done and a user signs in.
- **Verify:** Crashlytics shows "waiting for first crash"; Messaging page loads.
- **Depends on:** A1, A4. **Unblocks:** crash reporting, analytics dashboards, push.

### A9. Enable Firebase App Check  *(spec #12)*
- **Console:** Firebase → **Build → App Check → Apps → EDUmio Android → Play Integrity → Register**.
- **Also:** add a **debug token** (App Check → your app → Manage debug tokens) for local testing.
- **Do NOT "Enforce"** on Firestore/Functions yet — turn enforcement on only after live testing (later).
- **Return to me:** nothing (the client already installs the Play Integrity provider).
- **Verify:** App Check → your app shows the Play Integrity provider registered.
- **Depends on:** A1, A3. **Unblocks:** anti-tamper enforcement (enabled later).

---

## STAGE B — Backend deployment (Functions + Rules)

### B1. Upgrade to the Blaze plan + install tooling  *(spec #10, #11)*
- **Console:** Firebase → **⚙ → Usage and billing → Modify plan → Blaze** (pay-as-you-go; links a Google
  Cloud **billing account** — you choose/confirm it).
- **Local tooling:** `npm i -g firebase-tools` → `firebase login` → `firebase use <project-id>`.
- **You choose:** the billing account.
- **Verify:** `firebase projects:list` shows your project; the plan reads **Blaze**.
- **Depends on:** A1. **Unblocks:** B2, B3 (Functions require Blaze + outbound network).

### B2. Deploy Firestore rules + indexes  *(spec #9)*
- **Command (repo root):** `firebase deploy --only firestore:rules,firestore:indexes`
- **Verify:** Firebase → Firestore → **Rules** tab shows the deployed `rules_version = '2'` ruleset; the CLI
  prints "Deploy complete".
- **Depends on:** A7, B1. **Unblocks:** all client reads/writes (until deployed, Production mode denies all).

### B3. Deploy Cloud Functions  *(spec #10)*
- **Preflight:** `cd functions && npm install` (installs `firebase-admin`, `firebase-functions`, `googleapis`).
- **Command (repo root):** `firebase deploy --only functions`
- **Verify:** the CLI lists deployed functions — `claimDailyChallenge`, `completeDailyChallenge`,
  `onUserCreate`, `onUserDeleted`, `verifyPurchase`, `playRtdnHandler`, `sendTestPush`, `emailWebhook` — all in
  `europe-west1`; Firebase → Functions shows them healthy.
- **Return to me:** nothing (or paste failures).
- **Depends on:** A7, B1. **Unblocks:** server-authoritative challenge, profile creation, deletion; C-stage
  billing depends on `verifyPurchase`/`playRtdnHandler` existing.

---

## STAGE C — Google Play + billing

### C1. Create the Play Console application  *(spec #16)*
- **Console:** Play Console — <https://play.google.com/console> → **Create app**.
- **Path / values:** app name (you choose) · default language · app/game = App · free/paid = **Free** (with
  in-app subscriptions) · package will be `com.edumio.app` at first upload.
- **You choose:** app name, declarations.
- **Verify:** the app dashboard exists.
- **Depends on:** a Play developer account. **Unblocks:** C2, C3, C4.

### C2. Enrol in Play App Signing + return the signing SHAs  *(spec #17, #4)*
- **Release SHA (yours):** on the build machine run
  `keytool -list -v -keystore edumio_release.jks -alias <YOUR_ALIAS>` (you have the password) → copy **SHA-1 +
  SHA-256**.
- **First upload:** build `./gradlew :app:bundleRelease` (needs your real `keystore.properties`), upload the
  `.aab` to an **Internal testing** release → Play enrols **Play App Signing** and shows an **app-signing**
  SHA-1/256 under **Test and release → App integrity → App signing** (different from your upload key).
- **Then:** add **both** sets (release upload SHA + Play App-signing SHA) to Firebase → *Project settings →
  Your apps → Add fingerprint* (or Google sign-in fails on Play-installed builds).
- **Return to me:** the release SHA-1/256 (for docs) — no code change needed.
- **Verify:** Firebase *Your apps* lists debug + release + Play app-signing fingerprints.
- **Depends on:** C1, A3. **Unblocks:** Google sign-in on release; store distribution.

### C3. Create the subscription products  *(spec #18)*
- **Console:** Play Console → **Monetize → Products → Subscriptions → Create subscription**.
- **Exact product ids (must match the app):** `edumio_premium_monthly` and `edumio_premium_yearly`. Give each a
  base plan (monthly / yearly auto-renewing).
- **You choose:** the **prices** (per country) and base-plan details, free-trial (optional). *Prices are read
  from Play at runtime — not hardcoded.*
- **Return to me:** confirmation the two product ids exist exactly as above (and whether you offer yearly, so I
  can flip the `paywall_show_yearly` default if wanted).
- **Verify:** both subscriptions show **Active** with the exact ids.
- **Depends on:** C1. **Unblocks:** the purchase flow + C4/C5 verification.

### C4. Grant Play Developer API access  *(spec #19)*
- **Console:** Google Cloud Console → **APIs & Services → Enable APIs → "Google Play Android Developer API" →
  Enable**; then Play Console → **Users and permissions** (or **API access**) → link the GCP project and grant
  your **Cloud Functions service account** (`<project-id>@appspot.gserviceaccount.com`) permission to view
  financial/subscription data.
- **You choose:** confirm the GCP↔Play link.
- **Verify:** Play Console → *API access* shows the linked project + the service account with access.
- **Depends on:** B1, C1, B3. **Unblocks:** `verifyPurchase` can validate tokens (else it errors).

### C5. Set up Real-time Developer Notifications  *(spec #20)*
- **Console:** Google Cloud → **Pub/Sub → Create topic** named exactly **`play-rtdn`** → grant
  `google-play-developer-notifications@system.gserviceaccount.com` the **Pub/Sub Publisher** role on it. Then
  Play Console → **Monetize → Monetization setup → Real-time developer notifications** → paste the topic name
  `projects/<project-id>/topics/play-rtdn` → **Save / Send test notification**.
- **⚠️ Exact value:** topic short name must be `play-rtdn` (or return a different name for me to update
  `RTDN_TOPIC` before B3 redeploy).
- **Verify:** Play "Send test notification" succeeds; the `playRtdnHandler` function log shows an invocation.
- **Depends on:** C4, B3. **Unblocks:** automatic entitlement updates on renew/cancel/expire/hold.

---

## STAGE D — Email

### D1. Choose an email provider + credentials  *(spec #21)*
- **Console:** your chosen provider (SendGrid / Amazon SES / Postmark — **you choose**).
- **Action:** create an **API key** and a verified **sender address/domain**.
- **You choose / secret:** provider, API key, `EMAIL_FROM` address.
- **Return to me:** which provider you chose (so I wire its SDK into `functions/email.js`'s `sendViaProvider`
  seam — a small config edit once creds exist) — **paste the API key into Functions Secret Manager, not chat**:
  `firebase functions:secrets:set EMAIL_API_KEY` and set `EMAIL_FROM` similarly.
- **Verify:** after wiring + redeploy, `sendTestPush`-style receipt/report send returns `{sent:true}`.
- **Depends on:** B3. **Unblocks:** receipts, learning + marketing email (verification/reset are already native).

### D2. Configure SPF, DKIM, DMARC  *(spec #22)*
- **Console:** your DNS host (registrar) — add the exact TXT/CNAME records your provider (D1) gives you.
- **You choose:** the domain; **return** nothing to me (DNS only).
- **Verify:** provider's domain page shows SPF/DKIM/DMARC **verified**; a test send lands in inbox (not spam).
- **Depends on:** D1. **Unblocks:** reliable email deliverability.

---

## STAGE E — Legal

### E1. Host Privacy Policy + Terms; provide the URLs  *(spec #23)*
- **Console:** your web host (you choose) — publish the **lawyer-reviewed** Privacy Policy + Terms at stable
  public URLs (+ an account-deletion info URL for Play).
- **You choose / must supply:** the finalized legal text + the two (three) hosted URLs (see
  `LEGAL_OWNER_INPUT_REQUIRED.md` for the 13 facts a lawyer needs).
- **Return to me:** the **Privacy Policy URL** and **Terms URL** — I set `PRIVACY_POLICY_URL` / `TERMS_URL` in
  `app/build.gradle.kts` (currently `""`) and swap the draft in-app HTML for the finalized copy.
- **Verify:** both URLs load publicly; the in-app "view on web" button appears once the URLs are set.
- **Depends on:** legal review. **Unblocks:** public Play release + Play Data Safety completion.

---

## First action to take now
**Start with A1 (create the Firebase project) and return the project id.** Then A2→A4 in order; once
`app/google-services.json` is placed, hand it back (or confirm placement) and I will verify the app activates
Firebase and light up auth/Firestore. I will not change code until you supply real configuration for a step
that needs a value from me (e.g. a non-Europe Firestore region, a different RTDN topic name, the email
provider, or the hosted legal URLs).
