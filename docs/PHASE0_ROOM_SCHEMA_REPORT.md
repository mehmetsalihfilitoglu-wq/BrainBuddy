# Phase 0 — Room Schema Freeze Report

**Date:** 2026-07-19 · Branch `seeding-final-fix` · Scope: make future DB migrations safe by exporting and
freezing the current Room schemas, without altering any data or migration.

## What changed
- Enabled `exportSchema = true` on **both** databases (was `false`):
  - `EdumioDatabase` — content DB, **version 24**.
  - `DailyChallengeDatabase` — user-state DB, **version 2**.
- Configured KSP to write schema JSON: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, and
  added `schemas/` as an `androidTest` asset source set (for future Room `MigrationTestHelper`).
- Committed the generated baselines:
  - `app/schemas/com.edumio.app.db.EdumioDatabase/24.json`
  - `app/schemas/com.edumio.app.dailychallenge.DailyChallengeDatabase/2.json`
- Added `RoomSchemaExportTest` (3 JVM tests) asserting each schema file exists at the frozen version, lists
  the expected entity tables, and has a non-blank `identityHash`. This fails the build if a future entity
  change is not accompanied by a bumped, exported schema.

## Why version 24 / 2 are the right freeze points
- `EdumioDatabase` already carries additive migrations 11→24; freezing at 24 captures the shipped content
  schema so the next change must be `24→25` with a real migration and a new exported JSON.
- `DailyChallengeDatabase` is v2 with `fallbackToDestructiveMigration` — acceptable **only** because there
  are **no production users** (fresh-install posture). Freezing the v2 schema now means that once real users
  exist, we can replace destructive fallback with tested additive migrations against this baseline.

## Guardrail for Phase 2+
When any entity changes:
1. Bump the DB version and write a real `Migration`.
2. Let KSP regenerate the schema JSON; commit it.
3. `RoomSchemaExportTest` (and, when added, a `MigrationTestHelper` test reading `schemas/`) enforces it.

**Before public launch**, `daily_challenge.db` must drop `fallbackToDestructiveMigration` for a tested
migration path — tracked in `PHASE0_RELEASE_BLOCKERS.md`.

## Verification
- `assembleDebug` succeeds; schema JSONs are generated deterministically (identityHash-based).
- `155/0` unit tests pass, including the 3 new schema-freeze assertions.
- No entity, migration, or data was modified — this change is purely additive (export + freeze + test).
