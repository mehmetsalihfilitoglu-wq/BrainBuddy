# EDUmio Mascot — Icon System Report

**Date:** 2026-07-18 · **Commit:** `0d3a04b` · Branch `seeding-final-fix` (not pushed).
The uploaded EDUmio mascot is now the app's permanent brand icon. Because the raster artwork could not
be embedded or exported to binary densities in this environment, a **faithful vector interpretation** of
the mascot was authored (rounded green body + black graduation mortarboard + green tassel + two large
eyes with highlights) and the full Android icon system was built from it.

## Assets created / updated
| Asset | File | Role |
|---|---|---|
| Master mascot (colour, gradient) | `drawable/ic_edumio_mascot.xml` | launcher foreground, splash, in-app illustration |
| Monochrome silhouette | `drawable/ic_edumio_mascot_mono.xml` | Android 13+ themed icon (`<monochrome>`) |
| Notification silhouette (white) | `drawable/ic_notification_mascot.xml` | status-bar small icon |
| Launcher foreground | `drawable/ic_launcher_foreground.xml` | = mascot (safe-zone) |
| Launcher background | `drawable/ic_launcher_background.xml` | white→green-tint gradient |
| Adaptive icon + round | `mipmap-v26/ic_launcher.xml`, `ic_launcher_round.xml` | background+foreground+**monochrome** |
| Splash theme | `values/themes.xml` → `Theme.EDUmio.Splash` | Android 12 SplashScreen (mascot) |
| Mascot expression API | `ui/EduMascot.kt` | product-moment → expression mapping |

## Validation
| Check | Result |
|---|---|
| `:app:compileDebugKotlin` + `:app:processDebugResources` | SUCCESSFUL (all vectors well-formed, refs resolve) |
| `:app:testDebugUnitTest` | **114 / 0 fail** (incl. brand scan: 0 violations) |
| `:app:assembleDebug` | SUCCESSFUL — `app-debug.apk` |
| App label / package | `EDUmio` / `com.edumio.app` |
| **Adaptive icon (API 26+)** | `application-icon-120 = res/mipmap-v26/ic_launcher.xml` → mascot ✓ |
| Monochrome layer | `<monochrome>@drawable/ic_edumio_mascot_mono` ✓ (evenOdd eye holes) |
| Notification icon | `ic_notification_mascot` packaged; wired into 3 workers' `setSmallIcon` ✓ |
| Splash | `Theme.EDUmio.Splash` applied to `MainActivity`; platform attrs (API 31+), safe degrade < 31 |
| Adaptive safe zone | mascot geometry within the central 66dp of the 108 viewport |
| Contrast | green mascot + black cap + white eyes on white/light-tint background — strong contrast |
| Brand compliance | whole-repo scan 0 violations (mascot files contain no historical brand) |

## Where the mascot now appears
- **Launcher / adaptive / round icon** (API 26+), **themed monochrome icon** (Android 13+).
- **Splash screen** on launch (Android 12+).
- **Notifications** — Daily Challenge reminders, motivation reminders, report-ready (status-bar silhouette).
- **Onboarding welcome** — mascot greets the user.
- **Home “Günün Görevi” card** — mascot beside the title.
- **Daily Challenge completion** — mascot with a score/streak-based expression (`EduMascot.forCompletion`).

## Expression architecture (future-ready)
`EduMascot.Expression` = NEUTRAL / HAPPY (correct) / EXCITED (perfect streak) / THINKING (wrong) /
SLEEPING (no challenge) / CELEBRATE (completed) / PREMIUM (gold ribbon) / WAVE (onboarding). Every
expression currently resolves to the one master mascot; when per-expression artwork or
AnimatedVectorDrawable/Lottie animations (blink, bounce, celebrate, wave, thinking, sleeping) are added,
**only `EduMascot.kt` and the drawables change** — call sites stay identical.

## Known limitation (declared, not silently skipped)
- **Pre-API-26 launcher rasters** (`mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.webp`, used only on API 24–25)
  are binary and cannot be authored from a vector here — they still show the previous placeholder on very
  old devices. The adaptive vector covers **API 26+** (the effective target range). **Action:** a designer
  should export the mascot to PNG/WebP densities from the official artwork to complete API 24–25 coverage,
  and optionally provide a higher-fidelity master vector/PNG if pixel-exact parity with the uploaded art
  is required.
- Per-expression mascot artwork + animations are architected but not yet drawn (single master mascot today).

## Constraints honoured
No business logic, Daily Challenge engine, question banks, or analytics were changed — only branding/icon
resources and the three notification `setSmallIcon` calls.
