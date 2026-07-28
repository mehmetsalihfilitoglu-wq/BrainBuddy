# EDUmio Brand — Validation Checklist

Legend: ✅ verified · ⚠️ needs on-device / designer · ⛔ public-release blocker.

## Build & tests
- ✅ `:app:compileDebugKotlin` — SUCCESSFUL
- ✅ `:app:testDebugUnitTest` — 114/114, 0 failures (incl. `BrandComplianceTest` 2/2)
- ✅ `:app:assembleDebug` — SUCCESSFUL, `app/build/outputs/apk/debug/app-debug.apk` 77,346,413 B
- ✅ Manifest & resource merge valid (style ids resolved after rename)

## Branding
- ✅ App label resolves to **EDUmio** (`app_name` tr/en/it; asserted by test)
- ✅ Launcher/adaptive/round icon = EDUmio (green field + white "E"); safe zone valid
- ⚠️ Legacy density `.webp` launcher rasters (API <26) — designer regeneration required
- ✅ Splash: app has no dedicated splash screen; launch theme uses brand tokens (now EDUmio green)
- ✅ Notification channel display name = "EDUmio Günün Görevi"
- ✅ Onboarding / reports / data-rights / exam-pool label / PDF filename = EDUmio
- ✅ No forbidden brand in user-facing dirs (res, layout, icon vectors, assets/*.html, manifest)

## Color
- ✅ Brand green = `#25D366`, dark `#1DA851`, light `#E8FAEE` (tokens)
- ✅ Semantic (success/warning/error/info) + chart palettes preserved
- ⚠️ Large-font / dark-surface contrast — confirm on device (manual QA)

## Legal
- ✅ All legal/policy documents use EDUmio (no Barjin/BrainBuddy)
- ✅ Stale/false claims removed; honest "not production-ready" notices in place
- ✅ Clean DRAFTs + gap report authored; no invented legal facts
- ⛔ Legal **content** requires legal review before public release (placeholders + processors)

## Identifiers / data safety
- ✅ `applicationId` `com.mioacademy.app` unchanged
- ✅ `brainbuddy.db` filename unchanged (no user-data break)
- ✅ `mioitalia_seed_version` + `MIOITALIA` pool tag unchanged (no reseed/desync)
- ✅ Analytics event keys unchanged (continuity preserved)
- ✅ WorkManager unique-work names / notification channel IDs unchanged (no duplicate channels)

## Content & logic integrity
- ✅ No question-bank JSON or figure modified (git verified)
- ✅ Validator: 2207 questions, 465 figures, 0 problems (unchanged)
- ✅ 5-new-per-day invariant unchanged (tests green)
- ✅ Premium does not increase new-question count (tests green)
- ✅ Daily Challenge engine, blueprints, reminders, analytics behaviour unchanged

## Hygiene
- ✅ `.idea/misc.xml` never staged (only tracked-modified file remaining)
- ✅ Not pushed
- ⚠️ `compile_log.txt` (stale build log with checkout-path references) — recommend `.gitignore`

## Enforcement
- ✅ `BrandComplianceTest` fails the build if any historical brand reappears user-facing
- ✅ `brand_allowlist.txt` documents technical exceptions; empty for the scanned surface

## Real-device QA gate
- ✅ May begin (app builds, brand clean, content/logic intact)
- ⛔ Public release still gated on: legal DRAFT review, `.webp` icon regeneration.
