# Daily Challenge — Product Spec

**Permanent product center.** The app is a daily learning-habit system: *"Five new questions. A few minutes. Every day."* Built for consistency, retention, selective quality, long-term progress — not question-bank completion. Umbrella philosophy: `daily_challenge_architecture.md`. Companion specs: `daily_challenge_state_machine.md`, `daily_challenge_notification_email_spec.md`, `daily_challenge_analytics_spec.md`, `daily_challenge_implementation_plan.md`. **Docs only — no code, no commit.**

## 1. Core daily loop
Student picks ONE exam profile (**IMAT · TIL-I · CEnT-S**) → receives exactly ONE **Daily Challenge** per calendar day = **5 NEW questions** (never more/less) → solves → results + learning feedback → wrong answers enter review → returns tomorrow. Cannot unlock a second challenge the same day. Identical for Free and Premium. Student-facing name is only **"Daily Challenge" / "Today's Challenge"** — never "Test"/"Quiz".

## 2. New-question limit (hard invariant)
Max **5 new questions/day per user**; Premium does **not** raise it. On first appearance a question **permanently leaves that user's unseen pool** (retired). It may reappear only in review modes (Incorrect-Answer Review, Weak-Topic Review, Spaced Repetition, Adaptive Revision, AI Revision, future Mock Exams) — **never** as a new Daily Challenge question. No premium/review pathway may enable new-question bingeing.

## 3. Exam-proportional distribution
Section allocation is decided **before** question selection, from the **official blueprint**, via **cumulative proportional balancing** (deficit algorithm) — never random, never by bank size. Across days the accumulated mix converges to official proportions with no rounding bias (validated: 5-slot balancer, 365-day sim, every day Σ=5, max drift <1). Per-day targets & modal shapes:
- **IMAT** (Reading · Logical Reasoning · Biology · Chemistry · Physics & Mathematics): typical `Bio2·Chem1·P&M1·(Reading|Logic)1`; Reading/Logic rotate at official shares (not daily).
- **TIL-I** (Mathematics · Physics · Reading + Logic · Basic Technical Knowledge): typical `Math2·Phys1·(R+L)1·BTK1`; **Reading + Logic** rotates Reading/Logic; **Basic Technical Knowledge** rotates Representation/Computer Science.
- **CEnT-S** (Mathematics · Reasoning on Texts & Data · Biology · Chemistry · Physics): common `1-1-1-1-1`, balancer corrects long-term.
Sub-section rotation (nested deficit balancer) keeps Reading and Representation present long-term. Full math: `ten_question_blueprint_report.md` (same algorithm, 5 slots).

## 4. Question quality (unified pool, provenance hidden)
Only **production-eligible, semantically verified** questions appear (each passed: answer verification, figure consistency, stem completeness, option integrity, exactly-one-correct, confidence ≥ threshold, syllabus fit, duplicate screening — `eligibleForProduction=true`, `finalVerify=PASS`). The student sees **one unified high-quality pool**. Never expose: official/licensed/original/Elite, `sourceType`, `confidence`, `validation`, `poolType` — all internal.

## 5. Difficulty philosophy
Feel selective. Per 5-question day ≈ **1 Medium · 3 Hard/Selective · 1 Elite**; treat **Hard ∪ Elite** as the selective tier (the true top tier is Elite — competency-derived, adversarially verified — since the raw `hard` band is thin: TIL-I ~19, CEnT-S ~8). Raise difficulty via conceptual integration, multi-step reasoning, visual/graph/data interpretation, spatial reasoning, misconception-based distractors, scientific application — **never** via ambiguity, excessive calculation, obscure facts, or out-of-syllabus content. **Never lower quality to fill a quota**; if a section's eligible selective pool is short, **report the shortage — do not substitute another section or backfill weak items.**

## 6. Premium philosophy
Premium = **better learning, not more new questions.** Premium still gets exactly 5 new/day. Premium unlocks depth over already-seen questions: unlimited incorrect-review & retries, AI explanations, AI tutoring, weak-topic practice, adaptive revision, spaced repetition, revision history, advanced analytics, personalized insights, weekly/monthly progress reports. Free vs Premium review boundaries in §8 and the state-machine spec.

## 7. Student-facing experience
Home makes **Today's Challenge** the primary action: progress `0/5…5/5`, estimated duration, current streak, selected exam, completion state. After completion: score, per-section distribution, correct/incorrect breakdown, concise feedback, wrong answers added to review queue, next-unlock countdown — and **never** an offer for another new challenge that day.

## 8. Review experience
**Core rule: no review action grants new questions.** Review shows: why the answer was wrong, correct reasoning, topic & skill, common misconception, mastery state, next recommended revision time. Free = limited review; Premium = unlimited incorrect-question review + retries + AI. (Exact free caps: TBD by product; the architecture supports per-tier gating.)

## 9. Guardrails (see notification/analytics specs for enforcement)
No reminders after completion · no multiple same-day emails for one incomplete challenge · no endless notification loops · no false urgency · no shaming · no exposed provenance · no weak-question substitution on section shortage · never silently break exam proportions · no new-question bingeing via any pathway.
