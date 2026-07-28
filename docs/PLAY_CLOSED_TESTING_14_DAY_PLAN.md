# EDUmio — Play Closed Testing 14-Day Plan

Google requires **personal** Play developer accounts to run a closed test with **≥12 testers opted in for a
continuous 14 days** before applying for production access. This plan uses the **same signed v1.0 AAB** — no
rebuild needed.

## Setup (Play Console)
1. **Test and release → Testing → Closed testing → Create track** (e.g. "closed-14day").
2. Add an **email tester list of ≥12 real testers** (personal Gmail addresses; more is safer against
   drop-off — aim for 15–20).
3. Promote the v1.0 `app-release.aab` (already uploaded to Internal) to this track, or upload it here.
4. Share the opt-in link; confirm each tester **installs from Play and stays opted in**.

## Keep the 14 days valid
- Testers must remain **opted in for 14 continuous days** (do not remove them; don't reset the track).
- Encourage real usage: complete a few Daily Challenges over the two weeks.
- Track daily that ≥12 remain enrolled (Play shows tester counts).

## Evidence to retain (for the production-access application)
- Screenshot of the tester list (≥12) + opt-in dates.
- Crashlytics dashboard showing **crash-free users** over the period.
- Analytics showing active testers / challenge completions.
- Feedback collected (form or email) + a short "what we learned / fixed" note.

## Measurable exit gates (declare closed-test done only with evidence)
| Gate | Target | Source |
|---|---|---|
| Testers opted in ≥14 days | ≥12 continuous | Play Console |
| Crash-free users | ≥99% | Crashlytics |
| Daily Challenge completion | healthy, tracked | Analytics |
| No 6th question / no duplicate challenge | 0 incidents | tests + reports |
| No visible feature fails (Spark-safe) | 0 | tester feedback |
| Local learning works offline | verified | tester feedback |

## Feedback channel
Provide testers a simple form/email (owner sets up). Log issues by severity (P0–P3); fix P0/P1 before
requesting production. Reuse `CLOSED_BETA_CHECKLIST.md` for the operational checklist.

## After 14 days
Apply for **production access** in Play Console with the retained evidence. Only then prepare the production
listing + full launch (and, when the backend deploys, flip `SPARK_SAFE=false` in a later version).
