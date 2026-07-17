# Release Readiness Checklist — Daily Challenge

Legend: ✅ done/verified · ⚠️ needs on-device confirmation · ⛔ blocker for the named gate · — not started (out of scope).

## Hard product invariants
| Item | Status |
|---|---|
| Exactly 5 new questions/day | ✅ AUTO (allocate, 1000-day) |
| Never a 6th new question | ✅ AUTO (resumeIndex clamp) + ⚠️ device (step 9,13) |
| One challenge per local day, idempotent | ✅ AUTO+AUDIT + P1 mutex fix |
| Completed challenge never regenerates | ✅ AUDIT |
| Premium never increases new-question count | ✅ AUTO (entitlement + guardrail) |
| Review never adds new questions | ✅ AUTO |
| Retirement once per student | ✅ AUTO+AUDIT |
| No cross-exam contamination | ✅ DATA (validator) + AUTO |
| No provenance shown to students | ✅ DATA (0 forbidden keys) + AUDIT |
| Blueprint decides allocation before selection | ✅ AUDIT |

## Content
| Item | Status |
|---|---|
| IMAT/TIL-I/CEnT-S banks intact | ✅ no content file touched this phase |
| Answer keys / figures / assets preserved | ✅ validator: 465/465 figures present, 0 missing |
| Asset integrity (schema/options/index/unicode) | ✅ validator: 0 problems over 2207 records |
| Per-section visual spot-checks | ⚠️ device (manual §5) |

## Build & tests
| Item | Status |
|---|---|
| `:app:compileDebugKotlin` | ✅ SUCCESSFUL |
| `:app:testDebugUnitTest` | ✅ 112/112, 0 failures |
| `:app:assembleDebug` | ✅ APK 77,346,746 B |
| Instrumented/Espresso tests | — none (gap; see coverage report) |
| Coverage tool (JaCoCo/Kover) | — not configured (no % reported) |

## Reliability
| Item | Status |
|---|---|
| Generation concurrency (double-retire) | ✅ fixed (mutex) |
| Fail-closed guardrail side-effect-free | ✅ fixed |
| Notification post crash-safety | ✅ fixed |
| Process-death resume | ✅ AUTO (logic) + ⚠️ device (steps 11–14) |
| Offline behaviour | ✅ AUDIT (local-only) + ⚠️ device (step 34) |

## Notifications
| Item | Status |
|---|---|
| Contextual permission (not on launch) | ✅ AUDIT + ⚠️ device (steps 17–18) |
| Respect denial / settings recovery | ✅ AUTO+AUDIT + ⚠️ device (steps 19–20) |
| No reminder after completion | ✅ AUTO + ⚠️ device (step 22) |
| No duplicate WorkManager jobs | ✅ AUDIT |

## Accessibility
| Item | Status |
|---|---|
| Touch targets ≥48dp, text in sp, contrast AA | ✅ AUDIT |
| Not colour-only (review ✓/✗ markers) | ✅ fixed |
| Large-font (200%) no clipping | ⚠️ device (A11Y-4, step 36) |
| TalkBack announces options/verdict | ⚠️ device (A11Y-3, step 37) |

## Security / privacy (local build)
| Item | Status |
|---|---|
| No secrets/paths, DC components not exported | ✅ AUDIT |
| Room parameterized, no PendingIntent/WebView | ✅ AUDIT |
| No sensitive content logged; local-only analytics | ✅ AUDIT |
| Server-side entitlement verification | — backend (deferred) |

## Device matrix (⛔ for Closed Beta / Public)
| Item | Status |
|---|---|
| API 24 / 29 / 33 / 35 pass | ⚠️ NOT RUN (no device) |
| Small / standard / large / tablet | ⚠️ NOT RUN |
| Light/dark, low-memory, slow device | ⚠️ NOT RUN |

## Out of scope this phase (do not block internal QA)
- Backend, sync activation, email system — interface-only, not implemented.
- Play Billing live purchases — `LocalEntitlementRepository` (local flag).

## Gate summary
- **Internal QA gate:** all P0/P1 fixed, build+unit-tests green, content intact, invariants enforced →
  **MET**.
- **Closed Beta gate:** requires the manual device script (all four API levels) to pass → **NOT YET**
  (no device available here).
- **Public Release gate:** additionally requires instrumented tests, Play Billing live, backend/email, and
  A11Y device confirmations → **NOT YET**.
