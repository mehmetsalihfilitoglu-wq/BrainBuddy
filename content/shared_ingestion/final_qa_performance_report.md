# Final QA — Performance & Resource Report

Method: static/source analysis + build artifact measurement. **No on-device profiling** (no device);
latency/memory/battery numbers cannot be measured here and are not invented.

## Measured (build artifacts)
| Item | Value |
|---|---|
| Debug APK | 77,346,746 bytes (~73.8 MiB) |
| TIL-I asset bank | `questions.json` 1,049,530 B + 272 figures |
| CEnT-S asset bank | `questions.json` 1,319,184 B + 193 figures |
| Total DC question JSON | ~2.37 MB (parsed once at first seed) |
| DC figures shipped | 465 PNG (272 + 193) |

The ~74 MB APK is dominated by the bundled figure/question assets across IMAT + TIL-I + CEnT-S. This is
expected for an offline-first question bank; it is a **size** consideration, not a runtime cost.

## Source-level analysis
| Area | Assessment |
|---|---|
| **DB access off main thread** | All engine/review/DAO calls run in `withContext(Dispatchers.IO)`; Activities call them from `lifecycleScope`. No main-thread Room access found in the DC code. |
| **First-run seeding** | `DbSeeder.seedTilIIfNeeded`/`seedCentsIfNeeded` run inside the existing startup IO coroutine in `MioAcademyApp.onCreate` (sequential seed→audit→publish). Seeds ~2207 rows once, version-guarded so it never repeats. First launch pays a one-time parse+insert cost (not measured on device). |
| **Challenge generation latency** | `getOrCreateToday` after first day is a single indexed `getChallenge` read (fast path, no lock). Generation day-path does a bounded pool query (`LIMIT 5000` per section) + in-memory selection — infrequent (once/day). |
| **Image decode** | Figures decoded via `BitmapFactory.decodeStream` from assets, one at a time, only for the current question; `ImageView` is 200dp fixed. **Risk:** no downsampling (`inSampleSize`) — a very large figure PNG could spike memory. Figures are exam-scan sized (typically < 200 KB); no OOM observed in static review, but large-image decode is the main theoretical bitmap-OOM vector. Flagged for device profiling. |
| **Repeated image loading** | Each `render()` decodes the current question's figure fresh (no cache). For a 5-question flow this is ≤5 decodes; acceptable. No repeated re-decode of the same image within a screen. |
| **WorkManager overhead** | 3 unique periodic workers (one per slot), `UPDATE` policy — no duplication. Daily cadence; negligible. |
| **Analytics storage** | `LocalDailyChallengeAnalytics` ring buffer bounded at 200 entries in memory; no unbounded growth, no disk writes. |
| **daily_challenge.db growth** | One challenge row/day, ≤5 answer rows/day, one state row per seen question, small deficit/streak tables. Bounded by pool size (≤~2200 state rows/exam). Modest. |
| **StrictMode** | Not enabled in the DC code paths; no synchronous disk/network on main thread found. |

## Recommendations (for on-device profiling in internal QA)
1. **Measure** cold start, first-run seed duration, and challenge-generation latency on a low-end API 24
   device (the seed is the main first-run cost).
2. **Add bitmap downsampling** (`BitmapFactory.Options.inSampleSize` sized to the 200dp `ImageView`) if
   device profiling shows figure decode memory spikes — currently a documented risk, not a proven bug, so
   no speculative change was made.
3. Consider `android:largeHeap` only if profiling demands it (avoid by default).

## Net
No performance **defect** proven in source. The two watch-items — first-run seed cost and un-downsampled
figure decode — require device measurement before public release; neither blocks internal QA.
