# EDUmio v1.0 — Remaining Play Console Tasks (owner-only)

The app is **release-ready** (see `V1_RELEASE_AUDIT.md`: package/signing config/targetSDK/manifest/icons/
Firebase all ✅, `lintVitalRelease` ✅, 208/0 tests ✅, Spark-safe freeze ✅). Below is **only** what remains,
and it is all **outside the codebase** — signing + Play Console. No Blaze, no Cloud Functions, no Premium.

---

## §0 — Produce the signed `app-release.aab` (the one build step that needs your key)
The AAB must be signed with the **real `edumio_release.jks`**, whose password is not in the repo. Choose one:

**Option A — you hand me the credential and I build it.** Create `keystore.properties` at the repo root
(git-ignored — never committed) from the template `keystore.properties.example`:
```
storeFile=edumio_release.jks
storePassword=<your real password>
keyAlias=<your real alias>
keyPassword=<your real key password>
```
Tell me it's placed and I run `./gradlew :app:bundleRelease` → produces
`app/build/outputs/bundle/release/app-release.aab`, then I report its size + signing fingerprints + mapping
path and delete `keystore.properties`.

**Option B — you build it yourself (one command):**
```
./gradlew :app:bundleRelease
```
(with your `keystore.properties` in place). Output: `app/build/outputs/bundle/release/app-release.aab`,
mapping at `app/build/outputs/mapping/release/mapping.txt`.

> Do **not** enrol the debug key. Use `edumio_release.jks` so Play App Signing takes it as your upload key.

---

## §1 — Upload to Internal Testing  *(minimum to get the build onto devices)*
1. Play Console → **EDUmio** → **Test and release → Testing → Internal testing → Create new release**.
2. Upload `app-release.aab`. Accept **Play App Signing** (Google manages the app-signing key; your
   `edumio_release.jks` is the upload key).
3. **Release name:** `1.0.0 (3)`. **Release notes:** see `PLAY_STORE_LISTING_DRAFT.md`.
4. **Testers:** create an email list (add your testers). Save → **Review release → Start rollout to Internal
   testing**.
5. Share the **opt-in link** with testers; they install via Play.

> After the first upload, copy the **Play App Signing SHA-1 + SHA-256** (App integrity → App signing) and add
> them to Firebase (Project settings → Your apps → Add fingerprint) so Google Sign-In works on Play builds
> **when** account features are re-enabled (not needed for v1.0, which has no sign-in).

---

## §2 — App content declarations Play requires before rollout
Complete under **Policy → App content** (values are build-specific — this v1.0 build):
1. **Privacy policy URL** — 🔵 you must host one. v1.0 collects Firebase **Analytics** + **Crashlytics**
   data, so Play requires a privacy policy. (Draft facts: no account, no ads, on-device learning; analytics +
   crash diagnostics via Google.) See `LEGAL_OWNER_INPUT_REQUIRED.md`.
2. **Data safety** — fill from `PLAY_DATA_SAFETY_BUILD_SPECIFIC.md` (below): collects app-activity + crash +
   device/analytics id via Firebase; **no data shared**; **no account**; **no ads**; data encrypted in
   transit.
3. **Ads** — declare **No ads**.
4. **Content rating** — complete the questionnaire (education app; no objectionable content) → expect
   Everyone/PEGI 3.
5. **Target audience & content** — choose your age groups (app is exam prep, ~18+; you decide). If you
   include under-18, extra policies apply.
6. **Government apps / Financial features / Health** — declare **No** (none apply; no purchases in v1.0).
7. **News app** — No.

> **Account deletion requirement does NOT apply to v1.0** — no account creation is exposed (Spark-safe). See
> `PLAY_ACCOUNT_DELETION_STATUS.md`. It becomes required only when account features are re-enabled.

---

## §3 — Store listing (needed to promote toward Closed/Production; minimal for internal)
Under **Grow → Store presence → Main store listing** (draft text in `PLAY_STORE_LISTING_DRAFT.md`):
- App name `EDUmio`, short + full description, app icon (512×512), feature graphic (1024×500), phone
  screenshots (≥2), category **Education**, contact email, privacy policy URL (§2).

---

## §4 — Closed Testing (14-day track for production access)
Personal Google Play developer accounts must run **Closed testing with ≥12 testers opted in for ≥14
continuous days** before requesting production access. Plan in `PLAY_CLOSED_TESTING_14_DAY_PLAN.md`. Reuse
the same signed AAB; add ≥12 testers; keep them opted in 14 days; retain feedback + usage evidence.

---

## Summary — what only you can do
| # | Task | Blocking |
|---|---|---|
| 0 | Provide `keystore.properties` (real key) **or** run `bundleRelease` → `app-release.aab` | **AAB build** |
| 1 | Create Internal Testing release, upload AAB, add testers, roll out | Internal testing |
| 2 | Host a **privacy policy URL**; fill Data Safety, content rating, target audience, ads=No | Rollout |
| 3 | Store listing assets (icon/graphic/screenshots/descriptions) | Closed/Production |
| 4 | Run ≥12-tester, 14-day closed test | Production access |

Everything else (code, signing config, Firebase wiring, R8, lint, tests, feature freeze) is **done**.
