# EDUmio — Persistence Migration Report

**Decision (owner):** the app is **unreleased** — no production users, no local data to preserve.
Therefore persisted identifiers were renamed **directly** to EDUmio with **NO backward-compatibility
layer**, and obsolete compatibility code was removed rather than carried forward.

## Persisted identifiers — old → new
| Kind | Old | New | Back-compat kept? |
|---|---|---|:--:|
| Room DB file | `brainbuddy.db` | `edumio.db` | No (fresh install) |
| Room `@Database` class | `BrainBuddyDatabase` | `EdumioDatabase` | — |
| SharedPreferences files (~20) | `bb_premium`, `bb_onboarding_prefs`, `bb_gamification`, `bb_analytics`, `bb_notification_prefs`, `bb_subscription`, `bb_favorites`, `bb_grade_prefs`, `bb_wrong_question_store/_pool`, `bb_wrong_scheduler`, `bb_profile_scoped_migration`, `bb_auth_stub`, `bb_wise_coach`, `bb_daily_ad_quota`, `bb_parent_view_quota`, `bb_discovery_progress`, `bb_email_report_prefs`, `bb_last_test_unlock`, … | same names with `edu_` prefix | No |
| Seed-version key | `mioitalia_seed_version` | `edumio_original_seed_version` | No |
| Persisted pool tag | examType `MIOITALIA` | examType `EDUMIO_ORIGINAL` | No |
| Startup audit file | `brainbuddy_audit.txt` | `edumio_audit.txt` | No |
| WorkManager unique-work names | `brainbuddy_league_weekly_reset`, `brainbuddy_daily_report`, `brainbuddy_weekly_report`, `brainbuddy_email_report` | `edumio_*` | No |
| Notification channel IDs | (already neutral: `edu_*`/`dc_*`/`bb_motivation`→ n/a) | DC channel display name → "EDUmio Günün Görevi" | — |
| Billing product ids | `mioitalia_premium_{monthly,yearly}` | `edumio_premium_*` | No (no live Play products exist) |

## Data-loss prevention strategy
Because the app is unreleased, **there is no existing on-device data to migrate**; a direct rename is
the safe, clean choice (no risk of orphaning user data). No detect/copy/verify migration code was added,
and none is required. If the app HAD shipped, this rename would instead require the copy-then-verify
migrations described in the task spec — explicitly out of scope per the fresh-install decision.

## Obsolete compatibility code
- The Room migration chain (`MIGRATION_11_12` … `MIGRATION_23_24`) contains **no brand tokens** and was
  left intact (renaming it carries no brand benefit and it still defines the historical schema path).
  It can be collapsed to a clean v1 baseline as a future cleanup; not required for eradication.
- No legacy-name back-compat parsers were introduced.

## Testing limitation (honest)
On-device Room/SharedPreferences upgrade tests are **not runnable** in this environment (no emulator /
Robolectric). This is acceptable because the fresh-install decision means there is **no upgrade path to
test** — a clean install creates `edumio.db` and `edu_*` prefs from scratch. First-run seeding of the
renamed pool/asset is exercised indirectly (build + unit tests + APK assemble green; DC banks validate
2207/0). See `edumio_upgrade_compatibility_test_report.md`.
