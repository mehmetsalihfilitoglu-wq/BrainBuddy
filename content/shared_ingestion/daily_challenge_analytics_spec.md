# Daily Challenge — Analytics Event & Metrics Spec

Companion doc. Defines the event model and derived metrics powering insights, difficulty calibration, blueprint-adherence monitoring, and churn/notification-fatigue detection. Docs only.

## 1. Event model
`AnalyticsEvent(userId, name, propsJson, examProfile, localDate, tsUtc)`. Core events:
- `challenge_generated` {examProfile, sectionAllocation, questionIds, difficultyMix}
- `challenge_opened` {challengeId}
- `question_answered` {challengeId, questionId, section, topic, difficulty, isCorrect, timeMs, position(1..5)}
- `challenge_completed` {challengeId, score, totalTimeMs, sectionAllocation}
- `challenge_abandoned` {challengeId, answeredCount, lastPosition}  ← abandonment point
- `challenge_expired` {challengeId, answeredCount}
- `review_started` / `review_answered` {queueType, questionId, isCorrect, isPremium}
- `streak_incremented` / `streak_broken` / `streak_milestone` {value}
- `reminder_sent` {channel, slot, idempotencyKey} · `reminder_converted` {channel, slot, latencyMin}
- `email_sent` / `email_delivered|bounced|opened|clicked|unsubscribed|complained` {type}
- `shortage_detected` {examProfile, section, reason}  ← blueprint section short of eligible selective questions
- `premium_started` / `premium_review_used` / `paywall_viewed`

## 2. Required metrics (§18 of the vision)
- **Engagement:** Daily Challenge completion rate; five-question completion time; challenge abandonment point (avg last position); retention (D1/D7/D30); churn risk (declining completion + rising inactivity).
- **Conversion (reminders):** reminder-to-completion conversion; push conversion; email conversion; notification fatigue (reminders sent per completion, dismissal/mute rate).
- **Accuracy:** overall; **by section**; **by topic**; per-difficulty; review accuracy; premium-review usage & lift.
- **Streak:** current & longest distribution; milestone attainment; break/recovery rate.
- **Content quality:** question failure rate (per-question wrong-rate); difficulty calibration (observed correct-rate vs assigned tier — flag mis-tiered items); question quality signals.
- **Distribution integrity:** **blueprint adherence** = measured cumulative section mix vs official proportions per user and in aggregate (alert if drift > 1 sustained — should never happen with the deficit balancer, so it's a regression alarm).
- **Email health:** delivery, bounce, open, click, **unsubscribe rate**, **spam-complaint rate**, conversion.

## 3. Derived / product analytics
- **Weakest subjects & topics** (per user) — lowest accuracy sections/topics over rolling windows → feeds weak-topic review + weekly/monthly emails + one personalized recommendation.
- **Most-improved topic** — accuracy delta vs previous period.
- **Difficulty calibration loop** — if a question's live correct-rate contradicts its tier, flag for editorial re-review (never auto-edit content).
- **Reminder effectiveness by slot/channel** — to tune the orchestration schedule without over-messaging.

## 4. Guardrail metrics (must stay green)
- reminders_after_completion = 0 (hard invariant)
- emails_per_incomplete_challenge_per_day ≤ 1
- new_questions_per_user_per_day ≤ 5 (any pathway) = invariant
- section-substitution-on-shortage = 0
- blueprint drift < 1 at all horizons.

## 5. Privacy
Analytics respect consent; PII minimized; email-engagement analytics separate from learning analytics; retention/aggregation policies documented before wiring a provider (aligns with backend-ready architecture's report/CMS seams).
