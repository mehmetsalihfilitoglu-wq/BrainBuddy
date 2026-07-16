# TIL-I & CEnT-S — Consolidated Content-Audit Report

**Status: content phase COMPLETE. Engine implementation NOT started (awaiting review). Nothing committed or pushed.**

Editorial standard enforced throughout: prepare students for the *upper bound* of exam difficulty; reasoning over recognition; essential (never decorative) figures; strict layer isolation; every accepted item independently solved + adversarially verified.

---

## 1. Grand totals (10 banks, 2401 records)

| Layer | TIL-I | CEnT-S | Notes |
|---|---:|---:|---|
| official (frozen, verbatim) | 479 | 0 | real past-exam items; never rewritten |
| licensed (source-faithful) | 311 | 547 | authorized third-party; never rewritten |
| original — main (orig+hard) | 399 | 583 | 100% medium+hard |
| beginner/adaptive pool | 33 | 49 | excluded from normal tests |
| **eligible-for-production** | **1169** | **1125** | unified pool per exam |

Total records 2401 · eligible 2294 · unique IDs (0 collisions) · 0 missing figures · all licensed `sourceType` intact.

---

## 2. Phase A — original-layer audit (407 questions, 8 batches)

Every pre-existing authored question triaged against the 4 editorial tests (differentiates? reasoning-not-recognition? difficulty-earned? still-challenging?) + a visual-upgrade lens.
- **Kept** (already selective): the majority, untouched.
- **Rewritten**: weak or text-only-visual items redesigned into selective and/or essential-figure questions, each re-solved + adversarially verified.
- **Routed to beginner pool**: genuinely foundational/recognition items (TIL +16, CEnT +32 over the pass).
- Result: **both main banks 100% medium+hard**; TIL-I visual coverage of the authored layer rose to **63%**.

## 3. Phase B — licensed → Elite derivation (858 licensed screened)

Weak licensed items spawn a brand-new **Elite original** (same competency, fully original — different numbers/context/distractors/reasoning/figure, `resemblesSource='no'` gated). **Licensed records themselves were never modified.**

**Two-stage screen statistics** (optimization active from batch 6; batches 1–5 used the original single-stage high-effort review):

| | Screened | Strong | Moderate | Weak |
|---|---:|---:|---:|---:|
| Two-stage batches (6–17) | 608 | 26 (4.3%) | 34 (5.6%) | 548 (90.1%) |
| Single-stage batches (1–5) | 250 | — | — | 250 (rated below-standard) |
| **All licensed** | **858** | **26** | **34** | **798 (~93%)** |

The TOLC/CISIA-prep licensed corpus proved overwhelmingly recognition-level, so the weak share is high by nature.
- **Strong** (26) & **Moderate** (34) → kept unchanged, no Elite generated. Moderate ids logged to `licensed_moderate_for_future.txt` for optional later improvement.
- **Weak** (798) → Elite pipeline; **623 Elite originals passed the gate and were added** (TIL-I 215, CEnT-S 408). The remainder were gate-rejected (low confidence / figure-not-essential / source-resemblance / etc.) — those licensed items simply keep no Elite this round.
- **2 items dropped** on deterministic failures (logged in `elite_dropped_deterministic_fail.txt`): `til_i_lic_logic_0100` (agent crash), `cents_s_lic_bio_0211` (content-filter block). Their licensed records are untouched.

## 4. Performance optimization outcome

The two-stage fast-screen changed Stage 1 from a **high-effort** review to a **low-effort** classification across all 608 questions it covered, and let **60 Strong/Moderate items (9.9%) bypass the full multi-agent Elite pipeline entirely** (~120 author/verify subagent invocations avoided) with **no change to validation standards** for accepted questions. Because the corpus was ~90% weak, skip-savings were inherently bounded; the dominant saving was the Stage-1 effort reduction plus the fully-skipped pipelines. (Wall-clock isn't quoted precisely — it varied with concurrency and session-limit resumes.)

---

## 5. Authored original layer (the audited/created content)

| Exam | Main (orig+hard) | medium+hard | Elite (from licensed) | Wave-authored | Visual |
|---|---:|:--:|---:|---:|:--:|
| TIL-I | 399 | **100%** | 215 | 184 | 63% |
| CEnT-S | 583 | **100%** | 408 | 175 | 34% |

## 6. Unified eligible pool (what the engine will serve)

Per exam, ONE pool drawn from all eligible layers — source invisible to the student:

| Exam | Eligible total | official | licensed | original | hard | difficulty (elig.) |
|---|---:|---:|---:|---:|---:|---|
| TIL-I | 1169 | 459 | 311 | 393 | 6 | medium 953 · easy 195 · hard 21 |
| CEnT-S | 1125 | 0 | 545 | 574 | 6 | medium 703 · easy 411 · hard 11 |

(The `easy` items are genuine **official/licensed** questions kept verbatim — the "majority medium-hard" standard governs *authored originals*, which are 100% medium+hard. The engine balances difficulty via blueprint/difficulty-target.) Beginner/rejected/pending/unanswerable/low-confidence/figure-defective are all excluded.

## 7. Engine-readiness

`normalize_engine_ready.mjs` stamped `poolType` + `eligibleForProduction` on **every** record (internal fields only — no content/provenance change). Contract in `student_pool_architecture.md`; schema in `til_i_cents_s_question_schema.json`. The future engine consumes these banks with **no database change**. Layers remain isolated: official/licensed/new_original never mixed; Elite originals link back via `derivedFromLicensedSkill`.

---

**Next (not started, pending your review):** the unified-pool app engine on `seeding-final-fix` (primary checkout), preceded by the pre-engine checklist incl. a baseline Gradle compile. Three separate future commits (content / engine / seed). No commits or pushes have been made.
