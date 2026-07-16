# Daily Challenge — Product Architecture v2.0

**Permanent product philosophy. This document supersedes every prior "test / practice / quiz" framing.** The product revolves around the **Daily Challenge**, not around tests. Documentation-only; no app code changes here. Nothing committed.

## 1. Core philosophy
The product is designed around **consistency, not quantity**. Goal: the student builds a habit of studying **every single day**. The app feels like Duolingo, not an exam platform.
- The student opens the app each day and is presented with exactly **one** challenge.
- It is named **"Daily Challenge"** or **"Today's Challenge"** — the only names to use anywhere in the app. Do **not** call it "Test", "Quiz", or "Practice".

## 2. The Daily Challenge — hard rules
- Every Daily Challenge contains **EXACTLY 5 NEW questions**. Five is permanent — never more, never fewer.
- A student may complete **ONE** Daily Challenge per **calendar day** (timezone-aware). A second is never unlocked.
- This applies **equally to Free and Premium**. There is **no** way to unlock additional *new* questions — ever. New-content consumption is intentionally rate-limited.

**Why:** the objective is not to let students finish the bank quickly; it is to keep them returning daily. Five carefully selected questions/day beat fifty random ones. Every future feature must reinforce this loop.

## 3. Premium philosophy
Premium is **"better learning," never "more new questions."** Premium **never** unlocks more Daily Challenge questions. Premium unlocks *learning depth* over the questions already seen:
- Unlimited review & retries of incorrect questions · AI explanations · AI tutoring · adaptive revision · weak-topic practice · performance analytics · spaced repetition · revision sessions · challenge history · study insights.

## 4. Question distribution (blueprint-driven, never random, never bank-size-driven)
The 5 questions are **never** random. Section allocation is decided **before** any question is selected, from the **official exam blueprint** using **cumulative proportional balancing** (same deficit algorithm validated for tests, now on 5 slots/day): per day `base = floor(share × 5)`; the remaining slots go to the sections with the greatest accumulated deficit; the ledger self-corrects so the multi-day distribution converges exactly to the official proportions. Bank size never influences allocation.

Because a day has only 5 slots, **not every section appears every day** — low-weight sections (IMAT Reading/Logic, CEnT-S Physics) rotate in at their correct long-run frequency. Validated (deficit balancer, simulated 365 days, every day Σ=5, max drift < 1 question at 5/30/100/365-day horizons):

**IMAT** (full 60 → per-day ×5): Biology 1.92 · Chemistry 1.25 · Physics & Mathematics 1.08 · Logic 0.42 · Reading 0.33. Modal days: `Bio2·Chem1·P&M1·(Logic|Reading)1`. 365-day totals 700/456/395/152/122 ≈ ideal 699.6/456.3/395.4/152.1/121.7.

**TIL-I** (full 42, official **4 sections**): Mathematics 1.90 · Physics 1.19 · **Reading + Logic** 1.19 · **Basic Technical Knowledge** 0.71. Modal day `Math2·Phys1·R+L1·BTK1`. 365-day 695/435/434/261 ≈ 16:10:10:6. Sub-rotation (nested deficit balancer): **Reading + Logic** alternates Logic / Reading (Reading floored so it never disappears long-term); **Basic Technical Knowledge** alternates Representation / Computer Science.

**CEnT-S** (full 55, official **5 sections**): Mathematics 1.36 · Reasoning 1.36 · Biology 0.91 · Chemistry 0.91 · Physics 0.45. Modal day `1-1-1-1-1` (one each) and rotations. 365-day 498/497/332/332/166 ≈ 15:15:10:10:5.

_(Full derivation, per-N convergence tables, and templates: `ten_question_blueprint_report.md` — the algorithm is identical; only the per-challenge slot count changes from 10 to 5.)_

## 5. Question selection (after allocation is fixed)
Only **production-eligible** questions (`eligibleForProduction=true`, PASS the semantic gate). Within each section's slot, maximize: topic diversity · concept diversity · difficulty balance. Avoid: duplicate solving patterns · repeated figures / figure families · near-identical questions · over-frequent topics. Respect **user history** (avoid recently solved), apply **adaptive spacing**.

## 6. Difficulty (separate constraint, applied after the section split)
Daily Challenges should feel **selective**. Per 5 questions target ≈ **1 Medium (20–30%) · 3 Hard/selective (50–60%) · 1 Elite (20–30%)**. Elite appears naturally; never force difficulty; **never lower quality to hit a blueprint quota.**
- **Known shortage (carry-forward finding):** the verified pool has very few `difficulty:hard` items (TIL-I ~19, CEnT-S ~8 eligible); the genuine top tier is **Elite** (competency-derived, adversarially verified: TIL-I 213, CEnT-S 399). Therefore treat the selective tier as **Hard ∪ Elite** vs Medium (foundational). If a section's eligible *selective* pool is temporarily too thin for a given day, **report the shortage; do not substitute another section and do not lower the bar.**

## 7. Question reuse & retirement (critical)
A **new** question may appear in a Daily Challenge **exactly once, ever, per student**. On first exposure it is **retired from that student's new-question pool permanently**. It can only be seen again through **review** modes: Incorrect-Question Review · Weak-Topic Review · Adaptive Revision · Spaced Repetition · AI Revision Sessions · Mock Exams. **Those modes reuse questions; the Daily Challenge never does.**

## 8. The central product loop
Open app → Today's Challenge appears → solve exactly 5 new questions → challenge completed → progress updated → wrong answers enter the review system → tomorrow a new Daily Challenge unlocks → repeat. Every future feature must strengthen this loop.

## 9. Engine requirements (for the future implementation — not built yet)
Daily unlock · **timezone-aware** unlock (one per calendar day in the user's tz) · daily **streaks** · missed-day recovery · adaptive review · revision scheduling · **question retirement from the Daily Challenge after first exposure** (per-user served set) · independent review queues (incorrect / weak-topic / spaced-repetition) · blueprint balancing (cumulative deficit) · topic balancing · difficulty balancing · duplicate/near-duplicate/figure-family prevention.

**Data-model implications (design so no rework is needed):** per-user `servedQuestionIds` (retired new-pool), per-user per-section deficit ledger (persist `expectedToDate`/`actualToDate`), challenge history (date, questions, answers, score), independent review queues, streak/last-completed-date. The content banks already carry the fields the selector needs (`poolType`, `eligibleForProduction`, `section`, `topic`, `difficulty`, elite tag, and seed-time `stemHash`/`figureFamily`/`passageId`/`solvePatternSignature` per `student_pool_architecture.md`) — selection is blueprint-first, then diversity/history filters over eligible questions.

## 10. Analytics to track
Daily completion rate · average Daily Challenge score · weakest subjects · weakest topics · average solving time · retention · streak · question quality · difficulty calibration · blueprint adherence (measured drift vs official proportions).

## 11. Naming & scope guardrails
Anywhere in the app and future docs: **Daily Challenge / Today's Challenge** only. The unified eligible pool (`student_pool_architecture.md`) still holds — the student still picks only an exam (TIL-I / CEnT-S / IMAT) and never a source layer — but its consumer is the **Daily Challenge selector** (5 new/day, retire-after-exposure), not a "test" mode. Mock Exams (a review-mode feature that *may* reuse questions) are the only place a longer multi-question session exists, and are distinct from the Daily Challenge.
