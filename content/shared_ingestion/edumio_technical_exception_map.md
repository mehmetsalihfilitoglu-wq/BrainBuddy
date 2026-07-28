# EDUmio Brand — Technical Exception / Compatibility Map

> **SUPERSEDED (2026-07-18):** the identifiers this map listed as "kept exceptions" (brainbuddy.db,
> BrainBuddyDatabase, com.mioacademy.app, MIOITALIA, mioitalia_seed_version, bb_/BB.) have since been
> FULLY ERADICATED under the fresh-install decision. See `edumio_full_technical_migration_report.md`
> and companion eradication reports. This file is retained as a historical record of the earlier phase.


Classification per the migration spec: **A** user-facing branding (replaced) · **B** internal identifier
safe to rename · **C** internal identifier unsafe to rename (would break upgrades / persisted data /
platform identity).

| Old identifier | Current use | Class | Renamed? | Reason | Migration required | User impact |
|---|---|:--:|:--:|---|:--:|:--:|
| `app_name` = "Mioitalia" | Android app label | A | ✅ → EDUmio | user-facing | none | label now EDUmio |
| Report/onboarding/data-rights strings | user-facing text | A | ✅ → EDUmio | user-facing | none | text now EDUmio |
| DC notification channel display name | notification UI | A | ✅ → "EDUmio Günün Görevi" | user-facing | none | shows EDUmio |
| `Theme/Widget/TextAppearance.BrainBuddy.*` | style/theme resource ids | B | ✅ → `.EDUmio.*` | compile-time only; no persistence; compile-verified | none | none |
| `rootProject.name` = "BrainBuddy" | Gradle project name (build metadata) | B | ✅ → EDUmio | build metadata; not applicationId | none | none |
| proguard header comment | comment | B | ✅ → EDUmio | cosmetic | none | none |
| **`applicationId` / namespace `com.mioacademy.app`** | Play Store identity, installed-app identity | C | ❌ | changing it is a new app on the Store, breaks updates & all installs; not a forbidden brand token anyway | — | would break all installs if changed |
| **`brainbuddy.db`** (Room db filename) | on-device database file | C | ❌ | renaming orphans every installed user's data (a fresh empty DB would be created) | would need a copy/rename migration | data loss if changed naively |
| `BrainBuddyDatabase` (Room `@Database` class) | internal class | C-ish | ❌ | tied to the db filename above; class rename is churn with no user benefit | none | none |
| **`mioitalia_seed_version`** (SharedPreferences key) | seed/version bookkeeping | C | ❌ | renaming makes the app think content is unseeded → unnecessary reseed / inconsistent version state on upgrade | would need a key-copy migration | reseed churn if changed naively |
| `MIOITALIA` (ExamType/pool constant), `seedMioitalia*`, `parseMioitaliaAsset`, `getMioitaliaPool`, `*_MIOITALIA_SEED_VERSION` | internal original-question pool identifiers | C | ❌ | the pool tag `examType='MIOITALIA'` is persisted in question rows; renaming desyncs stored rows from code | would need a data migration of stored rows | pool mismatch if changed naively |
| `BB.*` / `bb_*` (style/color prefixes) | design-system resource ids | B | ❌ (kept) | not a forbidden full-word token; pervasive references; zero user visibility | none | none |
| Question-bank `publisher`/`source` = "brainbuddy"/"brainbuddy_v2"; `examType='MIOITALIA'` | frozen bank provenance metadata | C | ❌ | banks are frozen ("do not modify question banks"); fields are student-invisible | none | none — never shown to students |
| `compile_log.txt` (path `…\BrainBuddy\…`) | stale Gradle build log | archive | ❌ | occurrences are the checkout **directory name**; renaming the folder is out of scope; file is a generated artifact | recommend `.gitignore` | none |
| Repo folder `…/BrainBuddy/` | filesystem checkout path | archive | ❌ | git/filesystem path; renaming the working tree is out of scope | optional local move | none |

## Rules honoured
- All **A** replaced.
- **B** renamed only where safe and verified by compile (styles, build metadata).
- **C** kept and documented; **no** applicationId / db filename / persisted-key / stored-tag change was
  made — installed apps and their data remain compatible.
- No technical identifier was left **silently**; every one is listed above and in `brand_allowlist.txt`.
