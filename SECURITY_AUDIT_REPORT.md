# BrainBuddy Security, Stability & Production-Readiness Audit Report

**Date:** March 2025  
**Scope:** Full Android (Kotlin) codebase

---

## 1. VULNERABILITIES FOUND & FIXED

### 1.1 App Blocking Security

| Issue | Severity | Fix Applied |
|-------|----------|-------------|
| Race condition when opening blocked app (multiple `TYPE_WINDOW_STATE_CHANGED` events) | Medium | Added 800ms debounce in `ForegroundAppBlockerService` to prevent multiple Gate launches |
| No try-catch around ReportStore/startActivity | Low | Wrapped in try-catch to prevent crashes from blocking service |
| Package name not validated | Low | Added `trim()` and empty check for `pkg` in accessibility event handler |

### 1.2 Data Safety

| Issue | Severity | Fix Applied |
|-------|----------|-------------|
| `ProtectionPrefs.studentLevel()` could throw on invalid enum | Medium | Safe `valueOf` with fallback to `AGE_3_5` |
| `ProfileStore.getProfiles()` could crash on malformed JSON | Medium | Try-catch with default profile fallback |
| ` quizIntervalMinutes()` read could be corrupted | Low | Added `coerceIn(30, 60)` on read |
| `QuestionHistoryStore` timesCorrect/timesWrong unbounded | Low | Capped at 10,000 to prevent overflow |
| `RewardedRetryStore.recordRetryUsed` could exceed limit | Low | Coerced count to `[0, MAX_RETRIES_PER_DAY]` |
| `TamperStore.getEvents` could throw on unknown TamperType | Low | Safe enum parsing with fallback |

### 1.3 Quiz Logic

| Issue | Severity | Fix Applied |
|-------|----------|-------------|
| Empty question pool could leave user stuck | Medium | Added "Ana Sayfaya Dön" button when no questions |
| Gate mode + empty pool: wrong navigation | Medium | When locked + gate mode, navigate to LockScreenActivity |
| `GateRetrySingleActivity` wrongCount could go negative | Low | `coerceAtLeast(0)` on `newWrongCount` |

### 1.4 Crash & Edge Cases

| Issue | Severity | Fix Applied |
|-------|----------|-------------|
| `PermissionMonitorLauncher` resumedCount could go negative | Medium | `coerceAtLeast(0)` in `onActivityStopped` |
| `HomeActivity.onResume` crash when content not set | Medium | Added `contentSet` flag; skip view access if not set |
| `PermissionMonitor.checkAndLockIfDisabled` unhandled exception | Low | Wrapped in try-catch |
| `ReloadSettingsWorker` no error handling | Low | Try-catch with `Result.retry()` on failure |

### 1.5 UI / Orientation

| Issue | Severity | Fix Applied |
|-------|----------|-------------|
| GateActivity/QuizActivity config changes | Low | Added `android:configChanges="orientation|screenSize|screenLayout"` |

### 1.6 BlockedAppsStore

| Issue | Severity | Fix Applied |
|-------|----------|-------------|
| `isBlocked("")` could match | Low | Added `isNotBlank()` and `trim()` check |

---

## 2. CODE PATCHES SUMMARY

### Files Modified

- `ForegroundAppBlockerService.kt` – Debounce, try-catch, validation
- `GateActivity.kt` – Sanitize blocked package extra
- `ProtectionPrefs.kt` – Safe studentLevel, quizInterval read
- `ProfileStore.kt` – Safe JSON parsing
- `RewardedRetryStore.kt` – Coerce retry count
- `QuestionHistoryStore.kt` – Cap counters
- `PermissionMonitorLauncher.kt` – Prevent negative resumedCount
- `PermissionMonitor.kt` – Try-catch
- `ReloadSettingsWorker.kt` – Try-catch, retry on failure
- `QuizActivity.kt` – Empty pool exit path
- `GateRetrySingleActivity.kt` – wrongCount validation
- `TamperStore.kt` – Safe enum parsing
- `BlockedAppsStore.kt` – isBlocked validation
- `HomeActivity.kt` – Safe onResume
- `AndroidManifest.xml` – configChanges
- `strings.xml` – Privacy policy URL placeholder

### Files Created

- `GateLogicTest.kt` – Unit tests for gate pass/fail
- `RewardedRetryLimitTest.kt` – Unit tests for retry limits
- `QuizSessionModelTest.kt` – Unit tests for QuizSession
- `GateAndRetryInstrumentedTest.kt` – Instrumented tests

---

## 3. REMAINING RISKS

| Risk | Mitigation |
|------|------------|
| **AccessibilityService disabled** | User can use blocked apps until they open BrainBuddy. PermissionMonitor + ReloadSettingsWorker set lock; next launch redirects to LockScreen. |
| **Usage access permission** | App does not use `PACKAGE_USAGE_STATS`. If added later, detect removal and lock similarly. |
| **Premium state in SharedPreferences** | Not encrypted. Consider Android Keystore for production. |
| **PIN** | Already PBKDF2-hashed with salt; secure. |
| **Retry history tampering** | Stored in app-private SharedPreferences. Root access could modify; acceptable for consumer app. |

---

## 4. SUGGESTIONS FOR FURTHER IMPROVEMENT

1. **Encrypt premium state** – Use Android Keystore for `PremiumStore`.
2. **Add usage access check** – If you add PACKAGE_USAGE_STATS, mirror accessibility lock logic.
3. **Replace test ad ID** – `ca-app-pub-3940256099942544~3347511713` is a test ID; use production ID for release.
4. **Privacy policy URL** – Replace `@string/privacy_policy_url` in Settings/About with your real URL.
5. **ProGuard** – Enable for release; add keep rules for reflection-used classes.
6. **More tests** – Add UI tests for LockScreen, PinLock, and gate flow.

---

## 5. VERIFIED BEHAVIORS (NO CHANGES NEEDED)

- Gate FAIL logic: `userLocked=true` on fail; `GateManager.gateRequiredNow()` returns true when locked.
- BootReceiver handles `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`.
- ReloadSettingsWorker checks accessibility and sets lock when disabled.
- Rewarded retry: 1/day per profile; `canRetryWithAd` enforces.
- Question pool fallback: `getFallbackQuestions()` in QuestionRepository.
- Parent PIN: PBKDF2 120k iterations, constant-time compare.
- ParentAccessGuard protects parent-only activities.
