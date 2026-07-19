# EDUmio v1.0 — Play Data Safety (this build)

Fill Play Console → **App content → Data safety** from this. Answers are specific to the **Spark-safe v1.0**
build (no account, no ads, no purchases, no cloud sync). Only Firebase **Analytics** + **Crashlytics** are
active off-device.

## Does the app collect or share user data? → **Yes (collects), No sharing**

### Data collected (via Firebase Analytics + Crashlytics)
| Category | Data type | Collected | Shared | Purpose | Optional? |
|---|---|---|---|---|---|
| App activity | In-app actions / events (screen views, challenge events — **IDs only, no question/answer content**) | Yes | No | Analytics | No |
| App info & performance | Crash logs, diagnostics | Yes | No | Crash reporting, app functionality | No |
| Device or other IDs | Analytics / installation ID | Yes | No | Analytics | No |

### NOT collected
Name, email, address, phone, user IDs (no account), precise/approx location, financial info, health,
photos/videos, audio, files, contacts, calendar, SMS, browsing history, installed apps.

### Security & handling
- **Encrypted in transit:** Yes.
- **Data deletion:** No account exists → no account-data deletion path needed. Users can request on-device
  data removal in-app (Settings → Data Rights) and uninstalling removes all local data.
- **Committed to Play Families policy?** Only if you target children — decide in Target audience.

### Notes
- **Ads:** None. Declare **No ads**.
- **Data shared with third parties:** None. (Google/Firebase acts as a processor for analytics/crash; declare
  as *collected*, not *shared*.)
- Update this form + the privacy policy together the moment account/sync/Analytics-of-PII features are
  re-enabled (`SPARK_SAFE=false`).
