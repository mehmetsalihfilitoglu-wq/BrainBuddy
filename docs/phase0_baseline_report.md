# Phase 0 — Baseline Report

**Date:** 2026-07-19 · Branch `seeding-final-fix` · Captured at the start of Phase 0 (HEAD `46612d8`).

## Build & test baseline
| Metric | Value at Phase 0 start |
|---|---|
| Debug build | `assembleDebug` succeeds |
| Debug APK size | 79,230,807 bytes (~79.2 MB) |
| JVM unit tests | 152 |
| Room `exportSchema` | `false` on both DBs (no frozen schema) |
| Release signing | none configured (release would sign with debug key or fail unpredictably) |
| Legal | Privacy/Terms drafts; a **false AdMob disclosure** live in `ad_info` legal doc |
| **Brand-compliance test** | **RED (pre-existing)** — see below |

## Honest correction to the baseline
The brand-compliance gate (`BrandComplianceTest.noHistoricalBrandAnywhereInActiveRepo`) was **already
failing** at `46612d8`: the Phase 2 architecture doc (committed just before Phase 0) contained the historical
package name and legacy parental-control brand tokens and was not in `brand_allowlist.txt`. An initial baseline glance
recorded "152/0", but a full run shows this gate was red before Phase 0 touched anything. Phase 0 cleaned
those references (and kept its own new docs brand-clean), so the suite is now fully green.

## Post-Phase-0 state (for comparison)
| Metric | After Phase 0 |
|---|---|
| Debug APK size | 80,749,809 bytes (~80.7 MB; up ~1.5 MB only because `resources.arsc` is now stored uncompressed — packaging, not content) |
| Release APK size | 70.8 MB (R8 shrink/obfuscate; full release build verified end-to-end) |
| JVM unit tests | **162 / 0 failures** (+3 Room schema-freeze, +7 premium fail-safe) |
| Room `exportSchema` | `true`; schemas frozen at `edumio.db` v24, `daily_challenge.db` v2 |
| Release signing | untracked `keystore.properties` + build guard blocks unsigned release |
| Legal | false AdMob disclosure removed; drafts still gated from publication |
| Brand-compliance test | **GREEN** |

The content layer (question banks, answer keys, figures, verified solutions, Daily-Challenge selection,
five-question rule, blueprints) was **not modified** at any point in Phase 0.
