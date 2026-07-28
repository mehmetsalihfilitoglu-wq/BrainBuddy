# Daily Challenge — Notification, Email, Consent & Orchestration Spec

Companion to the product spec / state machine. All push & email reminders flow through ONE **reminder-orchestration layer**. Docs only — no code, no commit.

## 1. Orchestration layer (single gate)
Before ANY message is sent, evaluate in order and abort if any fails:
1. Challenge already **completed today**? → suppress all remaining reminders for the day.
2. User timezone resolved? (all times are local).
3. Channel-specific **preference** enabled? (push / email reminders / weekly / monthly / marketing).
4. **Consent** valid for this category? (non-essential requires opt-in).
5. Channel+slot already used today? (no duplicate per slot).
6. **Frequency cap** not exceeded (per channel per day, and rolling caps).
7. User currently active in-app? → defer/skip.
8. Within **quiet hours**? → skip or shift.
9. Streak state / inactivity (tone + whether re-engagement applies).
10. Premium status (for premium-lifecycle messages only).
**Idempotency:** every send carries a key so retries can't double-send. Format: `daily_challenge_reminder:{userId}:{localDate}:{channel}:{slot}` (and analogous keys for weekly/monthly/re-engagement). Persist a `NotificationLog(userId, localDate, channel, slot, status, idempotencyKey, sentAt)`.

## 2. Reminder priority & channel escalation
Order: **1) in-app reminder → 2) push → 3) email reminder → 4) re-engagement lifecycle.** Do not fire all channels at once. **If push converts, suppress the email.** If the user opens the app, re-assess whether the next reminder is still needed. Email reminder only fires if push hasn't converted (or the configured email time is reached).

## 3. Push policy (incomplete challenge only)
Up to **3** reminders/day, local tz, cancelled the instant the challenge completes:
- 09:00 — "Today's Challenge is ready"
- 16:00 — "Your five questions are still waiting"
- 20:30 — "Complete today's challenge and protect your streak"
User controls: enable/disable reminders, preferred reminder window, mute specific channels, manage consent. **Never** notify about an already-completed challenge. Tone: encouraging, never guilt-heavy or false-urgency.

## 4. Email lifecycle
All emails: local-tz timing, consent- & preference-gated, unsubscribe + preference-center links, suppression honored, idempotency-keyed. Types:
- **Daily reminder** — only if challenge incomplete AND email reminders enabled AND (push didn't convert OR configured email time reached). **Max 1/day.** Evening 18:00–20:00 local. **Suppress immediately on completion.**
- **Weekly progress** — 1/week: challenges completed, completion rate, new questions solved, review questions solved, accuracy, strongest & weakest subject, most-improved topic, current streak, one personalized recommendation, CTA → Today's Challenge.
- **Monthly progress** — monthly completion rate, total solved, accuracy trend, strongest/weakest topics, revision performance, streak milestones, improvement vs previous month, next-month recommendation.
- **Re-engagement** — sequence at **3 / 7 / 14 / 30** inactive days; **stop the whole sequence immediately when the student returns**; never send the sequence blindly.
- **Premium lifecycle** — explanation availability, weak-topic insights, revision reminders, personalized reports, feature education, subscription/billing. No spammy/deceptive language.

## 5. Consent & preferences (mandatory, separate states)
Independent consent per category: **essential/transactional · learning reminders · progress reports · product updates · marketing.** Never auto-subscribe to marketing without valid consent. Store `ConsentRecord(userId, category, granted, timestampUtc, source)` with audit history; every non-essential email supports unsubscribe + a **preference center**. `NotificationPreferences(userId, pushEnabled, emailRemindersEnabled, weeklyEmail, monthlyEmail, marketing, preferredWindow, quietHours{start,end}, mutedChannels[])`.

## 6. Deliverability & suppression (protect sender reputation)
Track per message: **delivery, bounce, open, click, unsubscribe, spam complaint, conversion** (`EmailEvent`). Enforce: bounce suppression, complaint suppression, frequency caps, domain authentication (SPF/DKIM/DMARC) as a send prerequisite, preference center, consent timestamps, audit history. A hard bounce or complaint permanently suppresses that address for non-essential categories.

## 7. Safety guardrails (enforced here)
No reminders after completion · no >1 email/day for the same incomplete challenge · no endless loops (caps + idempotency) · no false urgency · no shaming · respect quiet hours · stop re-engagement on return · suppress email when push converts. All copy: positive, recovery-oriented.
