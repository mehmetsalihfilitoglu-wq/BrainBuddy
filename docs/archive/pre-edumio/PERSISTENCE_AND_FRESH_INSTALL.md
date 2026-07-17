# Persistence and Fresh Install Diagnosis

## Why the setup screen doesn’t appear after “delete and run again”

**Cause:** After you uninstall and reinstall (or run from Android Studio again), **Android is restoring app data from Auto Backup**. So SharedPreferences (including onboarding “done”), the Room database, and DataStore files come back. The app then behaves as if it was never deleted.

**What actually prevents the setup screen:**  
**`OnboardingPrefs.isDone(context)` is `true`** because the SharedPreferences file `bb_onboarding_prefs.xml` (key `is_onboarding_done`) was restored from backup.  
**Code path:** `MainActivity.onCreate()` → `when { !OnboardingPrefs.isDone(this) -> OnboardingWizardActivity }` → because `isDone` is true, it goes to `ProfileSelectionActivity` or `HomeActivity` instead of onboarding.

---

## 1. Room database

- **Name:** `brainbuddy.db`
- **Location:** `context.getDatabasePath("brainbuddy.db")` → typically  
  `/data/data/com.brainbuddy.app/databases/brainbuddy.db`
- **Cleared on uninstall:** Yes, for a **real** uninstall. If data is restored from backup after reinstall, the DB is restored too.

---

## 2. DataStore files

- **LockModeDataStore:** `lock_mode_prefs` (under app’s DataStore dir)
- **ReportsActivity:** `reports_prefs` (preferencesDataStore)
- **WrongReportUnlockStore:** `wrong_report_unlock`
- **WrongReviewAccessManager:** `wrong_review_access`
- **WrongReviewQuotaStore:** `wrong_review_quota`  
All live under the app’s private files directory and are backed up by default when `allowBackup="true"`.

---

## 3. SharedPreferences files

- **Onboarding (controls setup screen):** `bb_onboarding_prefs` — key `is_onboarding_done` (boolean). When `true`, setup is skipped.
- **Others:** Many (GradePrefs, ProfileStore, ProtectionPrefs, CrashRecoveryPrefs, etc.) via `ProfileScopedPrefs` and direct `getSharedPreferences(...)`.  
All in `shared_prefs/` under app data; backed up by default.

---

## 4. First-launch / onboarding-complete flag

- **Where:** `OnboardingPrefs` → SharedPreferences `bb_onboarding_prefs`, key `is_onboarding_done`.
- **Set to done:** `OnboardingWizardActivity` and `OnboardingActivity` call `OnboardingPrefs.setDone(this, true)` when the user finishes onboarding.
- **Read:** `MainActivity` uses `OnboardingPrefs.isDone(this)`. If true → skip onboarding and go to profile selection or home.

---

## 5. db_seed_version storage

- **Where:** Room table `app_meta`, key `db_seed_version` (value stored by `AppMetaDao.set(AppMetaEntity("db_seed_version", "3"))`).
- **Read:** `DbSeeder.seedIfNeeded()` uses `meta.get(KEY_DB_SEED_VERSION)` to decide whether to run seed.

---

## 6. Does uninstall + reinstall clear them?

- **Normal uninstall:** Yes. All of the above are under `/data/data/com.brainbuddy.app/` and are removed on uninstall.
- **What you see:** If after “uninstall and run again” the app still has old state, it’s because **reinstall restored from backup** (Google Auto Backup / manufacturer backup). Restored data includes `shared_prefs/`, `databases/`, and DataStore files, so onboarding and DB look “as before.”

---

## 7. Android Studio: update vs full reinstall

- **Run (green play):** Usually does **install -r** (replace). That **replaces the APK but does not clear app data** by default. So data (SharedPreferences, DB, DataStore) is kept → no fresh install.
- **Uninstall from device/Settings** then **Run:** Removes app and data, then installs. If the system then **restores from backup**, data (including onboarding done and DB) comes back.
- **Uninstall via adb** (see below) then **Run:** Same as above; backup can still restore.

So “not a true fresh install” can be either: (1) Run kept data (update install), or (2) backup restored data after a real uninstall.

---

## 8. allowBackup / dataExtractionRules / fullBackupContent

- **AndroidManifest:** `android:allowBackup="true"` is set. No `android:fullBackupContent` or `android:dataExtractionRules` in the manifest.
- **Effect:** Default Auto Backup is used: **shared_prefs**, **databases**, **files**, and DataStore under app data are included. On reinstall, the system can restore them → onboarding and DB reappear.
- **Backup rules file:** `app/src/main/res/xml/backup_rules.xml` exists but is **not** referenced in the manifest, so it has no effect. To restrict backup you would add e.g. `android:fullBackupContent="@xml/backup_rules"` and define excludes there (or set `allowBackup="false"` for debug builds).

---

## 9. adb install flags / run config

- **Run config:** Typically uses `adb install -r` (replace). No flag to clear data; data is preserved.
- **To force a clean install from command line:** Uninstall first (`adb uninstall com.brainbuddy.app`), then install (`adb install-multiple ...` or Android Studio Run). Backup can still restore after that unless you disable restore for the app or turn off backup for the device.

---

## Startup persistence logging (added in code)

On every app launch the app now logs:

**Sync (main thread):**
- `onboardingDone` — value of `OnboardingPrefs.isDone(context)` (this is what prevents setup when true)
- `profileCount`
- `dataDir`
- `sharedPrefsDirExists`
- `onboardingPrefsFileExists`

**Async (IO, after DB open):**
- `dbPath` — path to `brainbuddy.db`
- `dbExists`
- `db_seeded` — from `app_meta`
- `db_seed_version` — from `app_meta`
- `questionCount`

**Logcat tag:** `BrainBuddyPersistence`. Filter by this tag to see why the app skipped onboarding and what DB/seed state it saw.

---

## Exact package name and full uninstall command

- **Package / applicationId:** `com.brainbuddy.app`
- **Full removal (app + data) from this device:**

```bash
adb uninstall com.brainbuddy.app
```

This removes the app and all its data. After that, installing again (e.g. via Android Studio Run) is a fresh install **unless** the system restores from backup. To get a true “first launch” experience:

1. Uninstall: `adb uninstall com.brainbuddy.app`
2. When (re)installing, if the device/Google prompts to **restore app data**, choose **“Don’t restore”** or **“Set up as new”** for this app, or temporarily disable backup for the app/device before reinstalling.

---

## Optional: disable backup for debug so reinstalls are fresh

- In **build.gradle.kts** (or a debug manifest), you can set `android:allowBackup="false"` for the debug build so that no backup is created and no restore happens on reinstall.
- Or keep `allowBackup="true"` and add `android:fullBackupContent="@xml/backup_rules"` and in `backup_rules.xml` exclude `shared_prefs` and/or `databases` if you want other data backed up but not onboarding/DB (more involved).

Using the log tag `BrainBuddyPersistence` you can confirm after any install whether `onboardingDone` and `db_seed_version` are non-fresh (restored) or fresh (first run).
