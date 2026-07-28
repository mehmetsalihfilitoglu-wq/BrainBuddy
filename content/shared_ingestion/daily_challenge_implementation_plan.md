# Daily Challenge — Implementation Plan

How the Daily Challenge model will be built when implementation is approved. **Not built yet. No app code, no bank/metadata changes, no commit.** Target repo/branch per prior approval: primary checkout / `seeding-final-fix` (verify + baseline Gradle compile first). Aligns with `project_backend_ready_architecture` seams (auth/sync/entitlement/report) and consumes the verified eligible pool (`student_pool_architecture.md`, 2207 PASS-eligible).

## 1. Core domain objects
ExamProfile · Question(read-only bank) · UserQuestionState · DailyChallenge · ChallengeAnswer · SectionDeficitLedger(+nested) · Streak · ReviewQueueItem · TopicExposure · NotificationPreferences · ConsentRecord · NotificationLog · EmailEvent · AnalyticsEvent. (Fields in `daily_challenge_state_machine.md`.)

## 2. Required persistence
- **Content (ships in app / seeded):** the 5 eligible banks per exam → seeded to Room under `examType` (TIL_I/CENTS_S/IMAT) with internal columns `poolType, sourceType(internal), eligibleForProduction, finalVerify, section, subSection, topic, difficulty, eliteFlag, stemHash, figureFamily, passageId, solvePatternSignature`. IMAT/EDUMIO_ORIGINAL already seed; add TIL-I/CEnT-S seeders (mirror pattern). Copy `content/{til_i,cents_s}` banks into app assets.
- **Per-user state (local first, sync-ready):** UserQuestionState, DailyChallenge+ChallengeAnswer, SectionDeficitLedger, Streak, ReviewQueueItem, TopicExposure. Local Room now; behind the existing SyncRepository seam so it can move server-side without rework.
- **Comms/consent (server-side eventually):** NotificationPreferences, ConsentRecord+audit, NotificationLog, EmailEvent, suppression list.

## 3. Scheduling model
Timezone-aware daily unlock (reset 00:00 local); no stacking of missed days; one non-terminal challenge per (user,exam,localDate). Local unlock via a daily WorkManager tick keyed on the user's tz + a server cron once backend exists (idempotent generation guarded by the unique constraint). SR/review due-times computed per interval schedule.

## 4. Notification flow
Single reminder-orchestration service (the pre-send gate + idempotency keys) fronting: local push (WorkManager/AlarmManager, 09:00/16:00/20:30 slots) → cancel-on-completion → email (server) → re-engagement sequence. Priority in-app→push→email→lifecycle; suppress-on-convert. (Detail: notification/email spec.)

## 5. Email flow
Lifecycle emails (daily reminder / weekly / monthly / re-engagement / premium) via a server-side provider behind the report/CMS seam; SPF/DKIM/DMARC prerequisite; suppression + preference center; consent-gated; idempotency-keyed; one reminder/day cap.

## 6. Consent model
Separate consent states (essential/reminders/reports/updates/marketing); opt-in for non-essential; preference center; consent timestamps + audit; unsubscribe on every non-essential email; bounce/complaint suppression.

## 7. Premium behavior
Entitlement via existing entitlement seam. Free vs Premium differ ONLY in review depth/AI/analytics — **never** new-question count (5/day for all). Gate review-queue access, retries, AI explanation/tutoring, advanced analytics, weekly/monthly reports by tier.

## 8. Analytics
Event pipeline (§ analytics spec) behind the report seam; guardrail metrics as alarms (reminders-after-completion=0, ≤5 new/day, drift<1, no section substitution).

## 9. Technical risks
- **Timezone/day-boundary correctness** (DST, travel, clock changes) — unlock/expiry/streak must use a stable local-date function; test around DST.
- **New-question exhaustion** — a heavy daily user consumes the unseen pool over time (e.g. TIL selective pool is finite); need a content-replenishment cadence and graceful "section shortage" handling (report, never substitute). Model runway per exam/section from current eligible counts.
- **Retire-on-serve vs retire-on-answer** — decide so an expired/unopened challenge doesn't silently burn 5 new questions (recommend retire on first view/answer). Load-bearing for the "appears once" promise.
- **Offline/sync conflicts** — local completion then sync; idempotent by (challengeId,questionId); last-writer rules for streak.
- **Deficit-ledger persistence** — must survive reinstall/sync or blueprint convergence resets; store server-side once backend exists.
- **Notification duplication** across device + server — idempotency keys are mandatory.
- **Deliverability** — sender reputation; warm-up; suppression discipline.

## 10. Phased order
1. **Seed TIL-I/CEnT-S into the app** (assets + seeders + internal columns); verify unified eligible pool queryable; mapper exposes no provenance.
2. **Blueprint selector + deficit ledger** (offline, deterministic) → generate a valid 5-question challenge; diversity/history/difficulty filters; shortage signal.
3. **UserQuestionState + retirement + review queues** (question learning-state machine; wrong→review).
4. **Daily unlock + timezone + completion + streak** (lifecycle state machine; local WorkManager).
5. **Home & completion UX** (Today's Challenge primary; progress/score/next-unlock; no second challenge).
6. **Review experience + premium gating** (free caps vs premium unlimited; AI stubs behind seam).
7. **Notification orchestration + local push** (3 slots, cancel-on-complete, prefs).
8. **Analytics events + guardrail alarms.**
9. **Backend: sync, email lifecycle, consent, server scheduling, server analytics** (when backend provisioned).
10. **AI tutoring/explanations, spaced-repetition tuning, mock exams** (later).

Each phase ships behind the existing seams so nothing blocks on the backend. Content is production-ready (2207 PASS-eligible); phase 1 is the first engine step, to start only on explicit go-ahead.
