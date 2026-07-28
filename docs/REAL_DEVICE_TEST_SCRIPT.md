# EDUmio — Real-Device Test Script

Exhaustive manual QA to run on physical devices/emulators. **This tooling cannot run these** (no devices, no
live Firebase). Where instrumentation tests exist they are noted; the rest is a human script. **Do not mark
any row passed without actually running it.**

## Device / config matrix
API 24, 29, 33, 35 × {small phone, normal phone, large phone, tablet} × {light, dark} × {default font,
largest font}. Prioritise API 24 (min) and API 35 (target) first.

## Core learning loop
| # | Step | Expected | Auto? |
|---|---|---|---|
| 1 | Fresh install, open | Onboarding; no crash; local fallback if no Firebase | ☐ |
| 2 | Start today's Daily Challenge | Exactly 5 new questions | JVM ✓ (logic) |
| 3 | Answer all 5, finish | Completion screen; streak +1; **no 6th question** | JVM ✓ (logic) |
| 4 | Reopen app / kill & relaunch | **Same** challenge, resumes at next unanswered | JVM ✓ |
| 5 | Switch exam mid-day | **No new** challenge generated | JVM ✓ |
| 6 | Next calendar day | New challenge (one only) | JVM ✓ |
| 7 | Wrong-question review | Never consumes new-question quota; Free capped, Premium unlimited | JVM ✓ |
| 8 | Open a Premium solution as Free | Gated (paywall); as Premium: full solution | JVM ✓ |

## Time / state manipulation
| # | Step | Expected |
|---|---|---|
| 9 | Change device clock forward a day | (Local build) may roll; (server build) server day authority prevents early/dup challenge |
| 10 | Change timezone / cross date line | No duplicate challenge; streak consistent |
| 11 | Airplane mode during a challenge | Answers captured locally; sync on reconnect |
| 12 | Force-stop mid-challenge; relaunch | Same challenge, correct resume |
| 13 | Reboot device | State intact |

## Account / auth (Phase 1+, needs Firebase)
| # | Step | Expected |
|---|---|---|
| 14 | Register email/password | Account created; verification email sent |
| 15 | Login before verification | Learning locked per policy; verify screen |
| 16 | Verify email, return | Full experience unlocks |
| 17 | Google Sign-In | Success; correct profile |
| 18 | Password reset | Email sent; reset works |
| 19 | Sign out / sign in other account | State isolated per account |
| 20 | Anonymous→account adoption | Today's challenge/answers/streak/wrong-pool preserved, no dup |
| 21 | Delete account | All shipped data removed; progress shown |

## Billing (Phase 4, needs Play + products)
| # | Step | Expected |
|---|---|---|
| 22 | Purchase subscription | Premium unlocks depth only; **still 5 new/day** |
| 23 | Restore purchase | Entitlement restored |
| 24 | Cancel / expire | Reverts to Free; unknown → Free |

## Notifications / email (Phases 5/6)
| # | Step | Expected |
|---|---|---|
| 25 | Notification permission grant/deny | Graceful either way |
| 26 | Daily reminders (09:00/16:00/20:30) | ≤3, stop after completion, correct day/account |
| 27 | Push receipt (FCM) | Delivered; taps route correctly |
| 28 | Email receipt (verification/reset/receipt) | Delivered; no solution bodies in email |

## Accessibility
| # | Step | Expected |
|---|---|---|
| 29 | TalkBack through core screens | All actionable elements labelled/reachable |
| 30 | Largest font scale | No truncation/overlap on key screens |

Record device, OS, build, and pass/fail per row. Attach Crashlytics session ids for any crash.
