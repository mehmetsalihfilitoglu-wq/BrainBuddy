# EDUmio Mascot — Icon System Report

**Date:** 2026-07-18 · Branch `seeding-final-fix` (not pushed).
The uploaded EDUmio mascot is now the app's permanent brand identity across **every** Android tier.
A faithful vector interpretation of the mascot was authored (rounded green body + black graduation
mortarboard + green tassel + two large eyes with highlights), and the full Android icon system —
adaptive, round, themed-monochrome, **legacy density rasters**, splash, notification, and in-app
expressions — was built from that single geometry.

## Assets created / updated
| Asset | File | Role |
|---|---|---|
| Master mascot (colour, gradient) | `drawable/ic_edumio_mascot.xml` | launcher foreground, splash, in-app illustration |
| Expression: happy | `drawable/ic_edumio_mascot_happy.xml` | correct answer |
| Expression: celebrating | `drawable/ic_edumio_mascot_celebrate.xml` | completion, premium |
| Expression: thoughtful | `drawable/ic_edumio_mascot_thoughtful.xml` | wrong answer |
| Expression: sleeping | `drawable/ic_edumio_mascot_sleeping.xml` | no challenge / empty review |
| Monochrome silhouette | `drawable/ic_edumio_mascot_mono.xml` | Android 13+ themed icon (`<monochrome>`) |
| Notification silhouette (white) | `drawable/ic_notification_mascot.xml` | status-bar small icon |
| Launcher foreground | `drawable/ic_launcher_foreground.xml` | = mascot (safe-zone) |
| Launcher background | `drawable/ic_launcher_background.xml` | white→green-tint gradient |
| Adaptive icon + round | `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | background+foreground+**monochrome** |
| **Legacy density rasters** | `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` + `ic_launcher_round.png` | **API 24–25** launcher (48→192 px) |
| Splash theme | `values/themes.xml` → `Theme.EDUmio.Splash` | Android 12 SplashScreen (mascot) |
| Splash layout | `layout/activity_splash.xml` | mascot + `EDU`(green)/`mio`(black) wordmark + tagline |
| Empty-review state | `layout/view_mascot_empty.xml` | sleeping mascot when no review items |
| Premium sheet | `layout/bottom_sheet_premium_paywall.xml` | celebrating mascot replaces the 👑 emoji |
| Play Store icon (512²) | `store_assets/edumio_play_icon_512.png` | listing artwork |
| Mascot expression API | `ui/EduMascot.kt` | product-moment → expression drawable mapping |

### Legacy rasters — how they were generated
The old placeholder `ic_launcher.webp` / `ic_launcher_round.webp` at all five densities were **removed**
and replaced with real PNGs rendered **headless via pure-JDK Java2D** (the Android Studio JBR's AWT +
ImageIO) from the exact same 108-unit mascot geometry — no external tools or designer round-trip. Squircle
clip for `ic_launcher`, circular clip for `ic_launcher_round`, scaled to each density (mdpi 48 · hdpi 72 ·
xhdpi 96 · xxhdpi 144 · xxxhdpi 192).

## Validation
| Check | Result |
|---|---|
| `:app:compileDebugKotlin` + `:app:processDebugResources` | SUCCESSFUL (all vectors/layouts well-formed, refs resolve) |
| `:app:testDebugUnitTest` | **114 / 0 fail / 0 error / 0 skipped** (incl. brand scan: 0 violations) |
| `:app:assembleDebug` | SUCCESSFUL — `app-debug.apk` |
| App label / package | `EDUmio` / `com.edumio.app` |
| **Legacy launcher (API 24–25)** | `application-icon-160…640 = res/mipmap-{m…xxxh}dpi-v4/ic_launcher.png` — **all PNG, no webp** ✓ |
| **Adaptive icon (API 26+)** | `application-icon-120 = res/mipmap-v26/ic_launcher.xml` → mascot ✓ |
| Monochrome layer | `<monochrome>@drawable/ic_edumio_mascot_mono` ✓ (evenOdd eye holes; keeps head/cap/eyes, not a letter) |
| Notification icon | `ic_notification_mascot` packaged; wired into 3 workers' `setSmallIcon` ✓ |
| Splash | `Theme.EDUmio.Splash` (platform attrs, safe degrade < 31) + `activity_splash.xml` layout on launch |
| Adaptive safe zone | mascot geometry within the central 66dp of the 108 viewport |
| Contrast | green mascot + black cap + white eyes on white/light-tint background — strong contrast |
| Brand compliance | whole-repo scan 0 violations (mascot files contain no historical brand) |
| Content banks | **unchanged** — no `content/` or `assets/` question edits in this change |

## Where the mascot appears (in the shipping app)
- **Launcher / adaptive / round icon** — API 26+ vector adaptive **and** API 24–25 density PNGs.
- **Themed monochrome icon** (Android 13+) — recognizable head + cap + eye holes.
- **Splash screen** on launch — mascot + `EDU`(#25D366 green)/`mio`(#000000 black) wordmark + tagline.
- **Notifications** — Daily Challenge reminders, motivation reminders, report-ready (status-bar silhouette).
- **Onboarding welcome** — mascot greets the user.
- **Home “Günün Görevi” card** — mascot beside the title; sleeps when no challenge is available.
- **Daily Challenge completion** — celebrating/expression-mapped mascot (`EduMascot.forCompletion`).
- **Empty review** — sleeping mascot empty-state instead of a bare toast.
- **Premium review sheet** — celebrating mascot (replaced the crown emoji).

_Restraint: the mascot is used at these product moments only — not repeated on every list row or button._

## Expression architecture
`EduMascot.Expression` = NEUTRAL / HAPPY (correct) / EXCITED (perfect streak) / THINKING (wrong) /
SLEEPING (no challenge) / CELEBRATE (completed) / PREMIUM / WAVE (onboarding). `EduMascot.drawable(expr)`
maps each moment to a drawn drawable: HAPPY→happy, EXCITED/CELEBRATE→celebrate, THINKING→thoughtful,
SLEEPING→sleeping, else→master. Adding animations (AnimatedVectorDrawable/Lottie) later changes **only**
`EduMascot.kt` + the drawables — call sites stay identical.

## Fidelity note (declared, not silently skipped)
The mascot is a hand-authored **vector interpretation** of the uploaded artwork, not a pixel-for-pixel
export of the source PNG. All Android tiers are now covered (legacy rasters included). If pixel-exact
parity with the original art is ever required, a designer can drop higher-fidelity master PNGs into the
same density paths without any code change.

## Constraints honoured
No business logic, Daily Challenge engine, question banks, or analytics were changed — only branding/icon
resources, the splash/empty/premium layouts, the mascot wiring in `HomeActivity` / `MainActivity` /
`DailyChallengeReviewActivity`, and the notification `setSmallIcon` calls.
