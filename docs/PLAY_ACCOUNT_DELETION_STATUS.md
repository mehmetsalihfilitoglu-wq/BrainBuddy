# EDUmio v1.0 — Play Account-Deletion Status

**Play's account-deletion requirement does NOT apply to v1.0.** That requirement is triggered only by apps
that let users **create an account**. In the Spark-safe v1.0 build, account creation + cloud sign-in are
**hidden** (Settings → Account & Sync entry removed via `SPARK_SAFE`), so there is no account to delete and
**no external deletion URL is required** for this submission.

## What v1.0 offers instead (local, already shipped)
- **On-device data rights:** Settings → **Data Rights** (`DataRightsActivity`) provides local export + delete
  of the user's on-device learning data.
- **Uninstall** removes all local data (`allowBackup=false`, no cloud copy in v1.0).

## When account features are re-enabled (`SPARK_SAFE=false`)
Both are already implemented in the codebase and must be surfaced + declared then:
1. **In-app deletion path:** `AuthRepository.deleteAccount()` → Firebase Auth `user.delete()` → the
   `onUserDeleted` Cloud Function recursively purges the Firestore user tree. (Cloud Function deploy needed.)
2. **External web deletion URL:** owner must host a "request account & data deletion" web page and enter it in
   Play Console → App content → Data deletion. **Mark as a blocker at that time — do not invent a URL now.**

**v1.0 action in Play Console:** in the Data deletion section, indicate the app does not offer account
creation (no user accounts) — no deletion URL needed.
