# Premium Solution Access — Security Report

**Date:** 2026-07-18 · Branch `seeding-final-fix` (not pushed). Scope: the Premium solution layer and
wrong-question hub added by this feature. Companion: [premium_solution_architecture.md](premium_solution_architecture.md).

## Enforcement model

Access is enforced by the **entitlement layer**, not by hiding buttons:

| Surface | Enforcement point | Behaviour for Free / unknown / failed entitlement |
|---|---|---|
| `SolutionActivity` (the only screen that renders solution bodies) | `onCreate` + `onResume` re-resolve entitlement → `SolutionAccessPolicy.access()` (fail-closed) | Locked layout; **no solution text is ever bound** into the view tree |
| `WrongQuestionsActivity` hub | same policy at every `refresh()` | Counts only; locked layout; **no stems/actions bound** |
| Review reveal (`DailyChallengeReviewActivity`) | entitlement checked per reveal | Correct option is marked (never gated); explanation body replaced by a locked hint |
| Result screen wrong-answer rows | entitlement checked at bind | Locked CTA rows; no solution actions |

`SolutionAccessPolicy.access(null) == LOCKED` — an unknown or errored entitlement state always behaves
as Free (`unknownOrFailedEntitlement_behavesAsFree` test).

## Attack-path review

- **Deep links / crafted intents:** `SolutionActivity` and `WrongQuestionsActivity` are
  `exported="false"` (no external intents at all), and both re-check entitlement at entry, so even an
  in-app navigation bug cannot render solution content for Free. Activity extras carry only question
  id / exam name / chosen index — no argument can unlock content, because the gate reads the
  entitlement repository, never the intent.
- **Leakage channels:** because binding is gated (not visibility), locked states have no solution text
  in the view hierarchy — nothing for accessibility services, `onSaveInstanceState`, window previews,
  or layout inspection to read. Solution text is never logged; analytics events carry question IDs
  only (`analytics_neverCarryContent` test asserts no stem/solution content and bounded param length).
- **Entitlement freshness:** `onResume` re-resolves, so a mid-session expiry downgrades on the next
  screen entry; a mid-session purchase upgrades in place (tracked as CTA conversion).
- **Free correctness visibility (product requirement):** Free always sees whether the answer was
  correct and which option is correct — only the solution BODY is premium.

## Honest limitations (no impossible-DRM claims)

1. **Bundled content is extractable.** Solutions ship inside the APK as a JSON asset for offline
   access. Anyone who unpacks the APK can read them. The entitlement gate is a *product* boundary, not
   cryptographic protection; on-device DRM for bundled content is not meaningfully achievable and is
   not claimed. Moving solution bodies behind an authenticated server endpoint would close this at the
   cost of offline access — the `SolutionStore` seam supports that migration later.
2. **Local entitlement store.** Until Play Billing / server verification goes live, the entitlement
   repository is the local seam; on a rooted device it could be tampered with. Same class of risk as
   (1), resolved by the server-side entitlement planned in the backend architecture.
3. **DB explanations predate the gate.** The TIL-I/CEnT-S banks' legacy `explanation` column still
   exists in `edumio.db` (used by the premium reveal as a fallback). It is only ever bound behind the
   same entitlement check; it is not reachable by Free UI paths.

## Verified by tests

`SolutionAccessPolicyTest` (fail-closed gate), `WrongPoolInvariantsTest.analytics_neverCarryContent`
(no content in analytics), `SolutionCoverageTest.solutions_containNoInternalMetadataOrLetterReferences`
(no internal verification fields or provenance shipped in the asset), plus the Daily-Challenge
invariant suite proving Premium never affects generation.
