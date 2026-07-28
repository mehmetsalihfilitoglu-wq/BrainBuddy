# Internal QA — Manual Test Script

**Why this exists:** no emulator/device was available during automated QA. A human tester must run this
script on real devices before the app advances past internal QA. Cover **API 24, 29, 33, 35** at minimum,
plus one small-screen and one tablet-width device.

Build under test: `app/build/outputs/apk/debug/app-debug.apk` (from `:app:assembleDebug` on
`seeding-final-fix`). Install: `adb install -r app-debug.apk`.

Each step: **Action → Expected**. Mark PASS/FAIL and attach a screenshot for UI steps.

## 1. Install & first launch
1. Install on a clean device (no prior data). → App installs; launcher icon "EDUmio".
2. Launch. → Onboarding (first run) or Home. Complete onboarding choosing an **IMAT** study area.
3. Observe first launch after seeding. → No ANR; Home renders within a few seconds (first run seeds
   TIL-I/CEnT-S banks in the background — Home must not block).

## 2. Exam selection
4. Home → note the "Günün Görevi" card. → Card shows state "Bugünün görevi hazır", progress 0/5,
   "~5 dk", streak line, CTA "Başla".
5. Switch study area to a **non-supported** exam (e.g. Architecture/TIL_A) via the identity card. → DC card
   shows "Bu çalışma alanı için henüz hazır değil"; no CTA. Switch back to IMAT.

## 3. Challenge start & five answers
6. Tap "Başla". → Question 1 of 5; progress "1/5", bar ~20%.
7. Answer Q1 (select an option → "Devam"). → Advances to Q2 "2/5".
8. Answer Q2, Q3, Q4. → Progress increments 3/5, 4/5, 5/5; last button reads "Bitir".
9. Confirm you are **never** offered a 6th new question. → After Q5 you go straight to the completion screen.
10. For a question with a figure, confirm the image renders and scales (fitCenter). → Figure visible, not
    clipped/stretched.

## 4. Process kill & resume (critical)
11. Start a fresh day's challenge, answer Q1 and Q2, then kill the app (swipe from recents / `adb shell am
    force-stop com.edumio.app`). Relaunch. → Home shows "Devam ediyor", 2/5. Tap "Devam et" → resumes
    at **Q3** (not Q1, not a new question).
12. Repeat killing after Q3 and after Q4. → Resumes at Q4, then Q5 respectively.
13. Answer all 5, kill before viewing result, relaunch, open the card. → Goes to completion (or completed
    state); **no new/6th question**, no regeneration.
14. Reboot the device mid-challenge (after Q2). → After reboot, resume at Q3.

## 5. Completion screen
15. Complete a challenge. → Score "n/5", per-section distribution rows (e.g. "Biyoloji: 2/2"), concise
    feedback, streak line, "Yeni görev … sonra açılıyor" countdown. **No** button offering another new
    challenge.
16. If any answer was wrong, confirm "Yanlışları tekrar et" appears; if all correct, it may be hidden.

## 6. Notification permission (API 33+)
17. First-ever completion on API 33+. → The system POST_NOTIFICATIONS prompt appears **now** (not on app
    launch). Grant it. → No crash; reminders can now be scheduled.
18. Fresh install on API 33+, open and close the app several times **without** completing. → The permission
    prompt does **not** appear on launch (contextual only).
19. Deny the prompt. → App continues normally; prompt is not shown again on subsequent completions.
20. Settings → toggle notifications on while permission denied. → A toast appears and the OS app-notification
    settings open (recovery path).

## 7. Notification cancellation after completion
21. Before completing today, verify reminders are scheduled (leave app; a reminder may appear at 09:00/16:00/
    20:30 local — or force by adjusting device clock near a slot). → A "Günün Görevi" reminder can appear.
22. Complete today's challenge, then advance the clock past the next slot. → **No** further reminder fires
    today. → Next day, reminders resume.
23. Complete, then verify no duplicate reminders (only one per slot). → At most one notification per slot.

## 8. Review (Free vs Premium)
24. Complete with ≥1 wrong answer → "Yanlışları tekrar et". → Review screen: "Tekrar", 1/n, the question.
25. Answer, tap "Kontrol Et". → Correct option marked "✓", your wrong pick "✗"; explanation shown; button
    becomes "Devam"/"Bitir".
26. As a **Free** user with many review items, reach the cap. → After the cap, a "… Premium ile sınırsız
    tekrar" hint appears; tapping it opens the paywall. New-question count is unaffected.
27. Set Premium (grant via the paywall/local entitlement) and reopen review. → Review depth is unlimited;
    still **no** additional new Daily Challenge questions anywhere.

## 9. Next-day unlock & streak
28. Complete today. Advance device clock to tomorrow (or wait). Open Home. → A **new** challenge (5 new
    questions) is available; streak incremented by 1.
29. Skip a day (advance clock 2 days). Complete. → Streak resets to 1; "longest" preserved.
30. Complete twice in one day (finish, reopen). → Streak counts once (no double increment); no second
    challenge.

## 10. Timezone / DST
31. Complete near 23:59 local, then cross midnight. → No duplicate/early unlock; completion not lost.
32. Change device timezone eastward, then westward. → Countdown stays correct; at most one challenge per
    local calendar day; yesterday's challenge cannot be claimed today.
33. If testing around a DST transition, confirm the day boundary and streak behave (23h/25h day).

## 11. Offline / airplane mode
34. Enable airplane mode. Open app, start and complete a challenge, do review. → Everything works
    (all data is local); no crash, no network errors.

## 12. Restart & config
35. Rotate the device during the flow. → No reset; same question retained.
36. Set system font to 200% (Accessibility). → Text scales; verify no clipping in cards/buttons (report
    A11Y-4). 
37. Enable TalkBack. → Options are announced; progress "n/5" announced; on reveal, the verdict is
    discoverable (note A11Y-3 if it isn't announced automatically).

## 13. Multi-user / reinstall
38. (If applicable) Sign out and sign in as a different user. → Each user has independent challenge/streak
    state (note P3-02: reminder completion marker is device-global).
39. Uninstall + reinstall. → Local state resets (expected for a local-only build); a fresh challenge is
    offered.

## Sign-off
All steps PASS on all four API levels + small/tablet → the device gate for "Ready for Closed Beta" is met.
Any FAIL on a hard-invariant step (9, 11, 13, 22, 27, 28) is a release blocker.
