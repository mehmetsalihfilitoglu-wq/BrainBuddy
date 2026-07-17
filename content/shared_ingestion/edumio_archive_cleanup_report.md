# EDUmio — Archive & Cleanup Report

## Deleted (no release value)
- `compile_log.txt` — a stale committed Gradle build log (~466 checkout-path references to the old repo
  folder). Not source, not shipped; removed for hygiene.

## Archived → `docs/archive/pre-edumio/`
Historical engineering reports from before the EDUmio migration, retained as an archival record with a
top-level `ARCHIVE_README.md` header ("Project now branded as EDUmio; historical references below reflect
the name used at the time"). These are excluded from active documentation and from the brand scan.

| File |
|---|
| ASSET_DISCOVERY_AUDIT.md |
| FULL_QUESTION_CORPUS_INVENTORY.md |
| IMPLEMENTATION_SUMMARY.md |
| PERSISTENCE_AND_FRESH_INSTALL.md |
| PRODUCTION_IMPLEMENTATION_REPORT.md |
| QUESTION_IMPORT_REPAIR_REPORT.md |
| REAL_VISUAL_REPLACEMENT_REPORT.md |
| TEST_SCENARIOS_ACCESSIBILITY_BYPASS.md |
| VISUAL_CONVERSION_FINAL_REPORT.md |
| VISUAL_UPGRADE_REPORT.md |
| VISUAL_UPGRADE_REPORT_PASS2.md |
| docs/VISUAL_QUESTIONS.md |

## Renamed files / directories
| Old | New |
|---|---|
| `app/src/main/assets/mioitalia/` | `app/src/main/assets/edumio_original/` |
| `content/mioitalia/` | `content/edumio_original/` |
| `scripts/build_mioitalia_asset.js` | `scripts/build_edumio_original_asset.js` |
| `app/src/main/java/com/mioacademy/app/**` | `app/src/main/java/com/edumio/app/**` (+ test/androidTest) |
| `BrainBuddyDatabase.kt` | `EdumioDatabase.kt` |
| `MioAcademyApp.kt` | `EDUmioApp.kt` |

## Updated in place (active docs → EDUmio)
Active architecture / spec / QA docs under `content/shared_ingestion/` (daily_challenge_*, student_pool,
content_inventory, editorial_standard, final_qa_*, blueprint/audit logs, etc.), `docs/BACKEND_ARCHITECTURE.md`,
`content/til_i/exports/*`, `content/edumio_original/README.md`+`STYLE_GUIDE.md`, `tools/README.md`, and the
Python/JS generator scripts (self-provenance literals).

## Left untouched (by policy)
- `.claude/worktrees/**` (tool worktrees) and `.git/**` (history).
- `barjin_release.jks` (untracked, protected signing key — owner action).
- The migration-report files + `brand_allowlist.txt` + `BrandComplianceTest.kt` (must name old brands).
