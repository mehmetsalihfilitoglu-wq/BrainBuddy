# Final QA — Bug Register

Severity: **P0** release-must-stop · **P1** fix before internal QA · **P2** fix before public release · **P3** backlog.
Status: **FIXED** / **DOCUMENTED** (known, accepted for this phase) / **OPEN**.

## P0 — release must stop
_None found._ The hard invariants (≤5 new/day, one challenge/day, no regeneration, no wrong-user content,
no cross-exam contamination, no privacy vulnerability) all hold — see the invariant tests in
`final_qa_test_matrix.md` and the code audit below.

## P1 — fix before internal QA

### P1-01 — Concurrent generation could create/retire two challenges for one day — **FIXED** (`8d0efe9`)
- **Where:** `DailyChallengeEngine.getOrCreateToday`.
- **Repro (reasoned):** engine instances are created per-Activity (`HomeActivity.refreshDailyChallenge`
  builds one; `DailyChallengeActivity`/`ReviewActivity` build their own). If the home card's `today()`
  coroutine and the flow screen's `loadToday()` coroutine both pass the initial "no challenge yet" read
  before either persists, both run generation → up to 10 questions retired for the day and the challenge
  row written twice. Still ≤5 *served* (no invariant break), but wasteful and non-idempotent; also a
  duplicate `dc_challenge_generated` analytics event.
- **Fix:** generation now runs under a process-wide `Mutex` (companion-level, shared across instances)
  with a double-checked read inside the lock → strictly single-winner, idempotent. Also removes the
  duplicate-analytics risk.

## P2 — fix before public release

### P2-01 — Fail-closed size guardrail had side effects — **FIXED** (`8d0efe9`)
- **Where:** `DailyChallengeEngine.getOrCreateToday`.
- **Issue:** the "never > 5 new" guardrail ran *after* the retirement + ledger writes, so a rejected
  batch would still have marked questions `SEEN_ONCE` and advanced deficits before returning null.
- **Fix:** guardrail moved before any side effect; a rejected batch now leaves no trace.

### P2-02 — Review screen didn't show which option was correct — **FIXED** (`866cb03`)
- **Where:** `DailyChallengeReviewActivity.reveal`.
- **Issue:** on "Kontrol Et" the screen showed a verdict ("Doğru/Yanlış") + explanation but never marked
  the correct option, so a student reviewing a wrong answer couldn't see the right choice — a real
  learning-UX gap for a study product.
- **Fix:** the correct option is marked "✓" (emerald) and the user's wrong pick "✗" (red) — icon marker
  *in addition to* colour (not colour-alone, for accessibility). `render()` resets colour per question.

## P3 — backlog (documented, not blocking)

### P3-01 — Reminder crash-safety — **FIXED** (`a650b2f`)
`DailyChallengeReminderWorker.showNotification` now catches all throwables and returns success/failure
instead of letting an exception fail/retry the worker; slot-fired + `dc_reminder_shown` are recorded only
on a real post.

### P3-02 — Reminder completion marker is device-global, not per-user — **DOCUMENTED**
`DailyChallengeReminderPrefs` keys the "completed today" marker by date only. On a shared device where
user A completes and user B has not, B's reminders are suppressed for that day. Reminders are generic
"do your daily challenge" nudges (no user-specific content, so no privacy leak); impact is a missed nudge
for the second user. Fix deferred (needs per-user reminder prefs); low real-world incidence.

### P3-03 — Reminder does not verify the active exam is supported — **DOCUMENTED**
The worker nudges regardless of whether the user's active study area maps to a supported Daily Challenge
exam (IMAT/TIL-I/CEnT-S). A user whose only area is an unsupported exam could be nudged with nothing to
do. Default onboarding is IMAT (supported); low incidence.

### P3-04 — Abandoned (generated-but-unanswered) questions are retired but never reviewable — **DOCUMENTED**
A challenge is generated (and its 5 questions retired `SEEN_ONCE`) when the home card first renders it.
If the user never plays that day, those questions leave the unseen pool without becoming review-eligible
(`SEEN_ONCE` is not a review state). This is a property of the "challenge is fixed per calendar day"
model, not a defect; noted as a slow pool-consumption consideration for heavy-abandonment users. Changing
it (generate-on-start instead of generate-on-view) is a product-design decision, out of QA scope.

### P3-05 — Figure alt text is generic — **DOCUMENTED**
Question figures use one content description ("Soru görseli"). Per-figure alt text does not exist in the
bank. Acceptable (better than none); a content enhancement, not a defect.

### P3-06 — Reminder copy is hardcoded (not in strings.xml) — **DOCUMENTED**
`DailyChallengeReminderWorker.copyForSlot` holds Turkish literals rather than string resources. Cosmetic /
localization-hygiene; no functional impact.

### P3-07 — No "reminder opened" / "reminder delivered" analytics — **DOCUMENTED**
Notifications carry no `PendingIntent`, so taps aren't tracked and delivery isn't confirmed. `dc_reminder_shown`
records the post attempt. Adding click-through tracking is a feature, out of scope.

## Not defects (verified expectations)
- **Raw-bank `answerIndex` skews low** (index 0 most common). Neutralized at runtime by the deterministic
  per-question option shuffle (`DailyChallengeOptions`); verified by `DailyChallengeBlueprintExtraTest`.
- **Exam changed after generation** creates an independent challenge under the new exam's key; the old
  exam's challenge is untouched. Correct per "one challenge per (user, exam, local day)".
