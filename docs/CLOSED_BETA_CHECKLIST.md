# EDUmio — Closed Beta Checklist

Readiness for 20–50 real testers. Only declare "closed-beta ready" when the **measurable gates** below are
backed by evidence.

## Prerequisites
- 🔵 Signed release/internal-testing build on a Play closed track.
- 🔵 Real Firebase config; auth + Firestore rules deployed; Functions deployed (for server challenge/billing).
- 🔵 Privacy notice + consent flow reachable (hosted draft acceptable for closed testing).

## Tester program
- ☐ Tester onboarding doc (how to join, what to test).
- ☐ Feedback form (link).
- ☐ Known-issues list (living).
- ☐ In-app or documented bug-report flow.
- ☐ Support process + contact.
- ☐ Consent flow for data + (optional) analytics.
- ☐ Crash-monitoring dashboard (Crashlytics) watched daily.
- ☐ Severity matrix (P0…P3) + rollback plan.

## Measurable exit gates (need evidence, not assertion)
| Gate | Target | Evidence source |
|---|---|---|
| Crash-free users | ≥ 99% | Crashlytics |
| Auth success rate | ≥ 98% | Analytics funnel |
| Sync success rate | ≥ 99% | sync telemetry |
| **No duplicate Daily Challenge** (same account/day) | 0 incidents | server logs + `DailyChallengeImmutabilityTest` |
| **No sixth question** | 0 incidents | guardrail events + tests |
| Daily Challenge completion rate | tracked, healthy | Analytics |
| Notification correctness (right day/account, stop on completion) | 0 wrong sends | FCM logs |
| Billing correctness (grant/expire/restore) | 0 mis-grants | entitlement logs |
| Solution accessibility (Premium gate correct) | 0 leaks/lockouts | tests + telemetry |
| Account deletion completeness | verified across all systems | deletion audit |

Declare **READY FOR CLOSED BETA** only when every gate has supporting evidence.
