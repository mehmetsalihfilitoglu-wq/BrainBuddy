# EDUmio Brand — Occurrence Audit

Recursive `git grep` over tracked files (excluding `*/build/*`). Counts are occurrences (and files).

## Before vs after
| Brand | Before | After | User-facing after |
|---|---:|---:|---:|
| BrainBuddy | 23,267 (2,040 f) | 23,184 (2,025 f) | **0** |
| Barjin | 29 (6 f) | 3 (2 f) | **0** |
| MioItalia / Mioitalia / MIOITALIA | 2,124 (129 f) | 2,129 (130 f) | **0** |
| Brain Buddy / Mio Italia (spaced) | 0 | 0 | 0 |

The residual totals are dominated by frozen question-bank provenance and internal identifiers; the
user-facing surface is zero (enforced by `BrandComplianceTest`). MioItalia's count is ~flat because its
occurrences are almost entirely inside the frozen original-question bank (`examType: MIOITALIA`,
`publisher`/`source` tags), which are intentionally not modified.

## Classification of every remaining occurrence

### Barjin — 3, all "fixed by design"
| Location | Classification |
|---|---|
| `BrandComplianceTest.kt` (×2), `brand_allowlist.txt` (×1) | **False positive** — these files must *name* the forbidden brands to detect them. |

### BrainBuddy — 23,184
| Category | ~Count | Classification |
|---|---:|---|
| Question banks (`assets/grade_based/**`, `content/**`) — `publisher`/`source` provenance | 18,531 | **Allowed technical exception** — frozen banks; student-invisible provenance metadata; "do not modify question banks". |
| App `*.kt` — `BrainBuddyDatabase` class + `brainbuddy.db` filename + refs | 50 | **Allowed technical exception (Cat C)** — DB filename renaming orphans installed users' data; class name internal. |
| Historical `*.md` reports + `scripts/*.py` generators | 193 | **Historical archive / internal tooling** — see doc-header policy; not user-facing. |
| `compile_log.txt` | 466 | **Build-log artifact** — occurrences are the filesystem path `…\BrainBuddy\…` (the checkout directory name). Recommend `.gitignore`. |
| Build metadata (`settings.gradle.kts`, `proguard-rules.pro`) | (was 2) | **Fixed** → EDUmio. |

### MioItalia / Mioitalia / MIOITALIA — 2,129
| Category | ~Count | Classification |
|---|---:|---|
| Frozen original-question bank (`content/**`, mioitalia asset) — `examType: MIOITALIA`, provenance | 2,035 | **Allowed technical exception** — internal pool tag + frozen bank; student-invisible. |
| App `*.kt` — `MIOITALIA` enum, `seedMioitalia*`, `parseMioitaliaAsset`, `*_MIOITALIA_SEED_VERSION`, pref key `mioitalia_seed_version` | 77 | **Allowed technical exception (Cat C)** — persisted seed-version keys + internal pool identifiers; renaming breaks reseed detection on upgrade. |
| Docs / architecture / DC specs / scripts | 25 | **Historical archive / internal** — describe the internal pool; not user-facing. Active DC docs updated where the *brand* (not the pool name) was meant. |

### User-facing directories — 0
`BrandComplianceTest` scans `res/values*`, `res/layout`, launcher-icon vectors, `assets/*.html`, and
`AndroidManifest.xml`; result: **no forbidden brand**, `app_name == EDUmio`. Test is green.

## Files scanned / modified
- Scanned: all tracked files (~repo), non-build. Brand-migration commits changed **55 files** (+565/−676).
- No question-bank JSON or figure asset was modified.
