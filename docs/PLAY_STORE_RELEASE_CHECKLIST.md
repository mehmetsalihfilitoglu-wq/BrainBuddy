# EDUmio — Play Store Release Checklist

Everything required to ship to Google Play. ✅ = engineering-complete · 🔵 = owner action · 🟠 = later phase.

## Build & signing
- ✅ Release signing wired via untracked `keystore.properties` + fail-fast guard (`PHASE0_RELEASE_SIGNING_REPORT.md`).
- ✅ R8/ProGuard on; release build verified end-to-end (R8 + lintVital + signing).
- 🔵 Real `keystore.properties` + `edumio_release.jks` on the build machine.
- 🟠 **App Bundle** (`bundleRelease`) produced with the real key; upload once.
- 🔵 Enroll in **Play App Signing**; then add the Play app-signing SHA-1/256 to Firebase (guide §3).
- 🟠 `versionCode`/`versionName` bump policy (currently 3 / 1.2); mapping-file (`mapping.txt`) uploaded for deobfuscated crashes.

## Store listing (owner-authored)
- 🔵 App icon, feature graphic, screenshots (phone + tablet), short + full description, release notes.
- 🔵 Category, contact email, external marketing opt-outs.

## Policy & compliance
- 🔵 **Data safety** form (from `CURRENT_DATA_PROCESSING_INVENTORY.md`).
- 🔵 **Content rating** questionnaire.
- 🔵 **Target audience & content** (age groups — align with min-age decision).
- 🔵 **Privacy Policy URL**, **Terms URL**, **account-deletion URL** hosted + linked.
- 🔵 Ads declaration: **No ads** (true — app is ad-free).
- 🔵 Financial features / subscriptions declared; **In-app purchases** listed.

## Testing tracks
- 🟠 Internal testing track (dogfood).
- 🟠 Closed testing track (see `CLOSED_BETA_CHECKLIST.md`).
- 🟠 Staged rollout % + rollback plan.

## Pre-launch report (Play)
- 🟠 Address Play **pre-launch report** crashes/accessibility warnings.

**Public release is gated** on the 🔵 owner items + the 🟠 items that depend on real Firebase/billing config
and real-device QA. Do not publish until `docs/EDUMIO_COMPLETE_PRODUCT_IMPLEMENTATION_REPORT.md` shows no
P0/P1 blockers.
