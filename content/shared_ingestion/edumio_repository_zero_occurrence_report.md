# EDUmio — Repository Zero-Occurrence Report

Enforced by `BrandComplianceTest.noHistoricalBrandAnywhereInActiveRepo`, which walks the entire active
repository (file **contents** + file **names** + **directory** names) for every forbidden variant and
fails the build on any hit outside the allowlist.

## Result: 0 unexplained occurrences in the active repository ✅

Forbidden tokens checked (case-insensitive): `brainbuddy`, `brain buddy`, `barjin`, `mioitalia`,
`mio italia`, `mioacademy`, `mio academy`.

## Every remaining occurrence, classified
| Location | Classification | Why allowed |
|---|---|---|
| `.git/**` | Immutable Git history | Cannot/should not rewrite history |
| `.claude/worktrees/**` (e.g. `agitated-shtern`) | Tool artifact | Ephemeral Claude Code worktrees — separate checkouts, like `.git`/`.idea`; excluded from the scan |
| `build/`, `.gradle/`, `.idea/`, `.kotlin/` | Build / IDE artifacts | Regenerated; excluded |
| `barjin_release.jks` | **Untracked** local signing keystore | Not part of the tracked repo; signing files are protected — owner should rename out-of-band |
| `brand_allowlist.txt`, `BrandComplianceTest.kt` | Enforcement machinery | Must name the forbidden brands to detect them |
| `content/shared_ingestion/edumio_*_report.md`, `edumio_technical_exception_map.md`, `edumio_legal_brand_audit.md`, `legal_gap_report.md`, `edumio_full_technical_migration_report.md`, `edumio_persistence_migration_report.md`, `edumio_package_namespace_report.md`, `edumio_archive_cleanup_report.md`, `edumio_upgrade_compatibility_test_report.md`, this file | Migration documentation | Record what was removed; naming the old brands is their purpose |
| `docs/archive/pre-edumio/**` | Archived historical evidence | Retained with an `ARCHIVE_README` header; excluded from active scan |

## Scan scope confirmation
- **Contents:** all text files (kt/java/xml/json/md/html/txt/py/kts/gradle/…) under 4 MB.
- **File names & directory names:** every path segment checked.
- **Excluded dirs:** `.git .claude build .gradle .idea node_modules out .kotlin .cxx captures`.
- **Allowlist:** `brand_allowlist.txt` — 17 entries, each with a reason + removal condition; every entry
  is enforcement machinery, migration documentation, archive, or the untracked keystore.

## Independent spot-check
`git grep` over tracked, non-build, non-`.claude` files for each token confirms the only tracked hits are
the allowlisted enforcement/report files. The APK binary contains **0** old-package/brand bytes.
