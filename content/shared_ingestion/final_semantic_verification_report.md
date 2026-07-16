# Final Semantic Verification — Publish Gate Report (TIL-I & CEnT-S)

Every production-eligible question was **independently re-solved from first principles** by a high-effort verifier agent (reading the figure image where present), then audited for stem/OCR, figure consistency, options, answer support, difficulty, and explanation, and assigned a semantic confidence. Gate: `PASS` only if the independent answer matched the stored answer, exactly one option correct, no duplicate/contradictory options, figure consistent, no OCR/stem defect, answer fully supported, and confidence ≥ 0.95. **No answers were edited; no replacement questions generated.** REVIEW and BLOCK were removed from the eligible pool with a documented reason (ledger: `semantic_verification_blocked.jsonl`).

## Totals

| | Count |
|---|--:|
| Questions checked | **2292** |
| PASS (remain eligible) | **2207** |
| REVIEW (removed) | 38 |
| BLOCK (removed) | 47 |
| **Removed total** | **85** |

Removed by exam: TIL-I 60 · CEnT-S 25. Post-gate eligible pool: **TIL-I 1107 · CEnT-S 1100 · total 2207** — every remaining question PASS-verified (0 BLOCK/REVIEW still eligible). Validation: **0 problems**, 0 missing figures, licensed `sourceType` intact.

## Defect breakdown (all 85 removed)

| Category | Count | Notes |
|---|--:|---|
| Answer mismatches | 8 | independent solve ≠ stored key — all in the TIL-I **official extracted** layer (`til_i_ex_*`) |
| No correct answer | 3 | stored key matches no option |
| Duplicate options | 8 | two byte-identical options |
| Figure inconsistencies | 15 | figure↔stem mismatch (8), label/text overlap or garbled labels (6), answer leaked in figure (1) — spanning geometry/representation/graph/circuit renders |
| OCR defects | 9 | corrupted symbols/characters in extracted stems |
| Stem defects | 3 | incomplete (2) / truncated (1) |
| Missing reading passage | 2 | reading item with no passage body |
| Explanation errors | 11 | stored explanation disagrees with stem/answer |
| Weak/impossible distractors | 3 | an implausible or impossible option |
| Empty/blank/unreadable option | 6 | includes benign blank fifth option on 4-option licensed items |
| Low confidence (<0.95) | 14 | correct-looking but verifier not ≥95% sure |
| Difficulty / premise / unsupported | 3 | difficulty (1), questionable premise (1), unsupported answer (1) |

**Answer mismatches (the critical check), all TIL-I official extractions — removed, not edited:**
`til_i_ex_math_062` (B→E), `til_i_ex_phys_095` (C→D), `til_i_ex_phys_096` (E→C), `til_i_ex_logic_033` (B→C), `til_i_ex_logic_034` (E→C), `til_i_ex_math_074` (E→A), `til_i_ex_math_082` (C→E), `til_i_ex_phys_132` (C→A).

By layer, removals concentrate in the **official/licensed extracted** banks (OCR, answer-key, duplicate-option, missing-passage defects inherent to third-party extraction); the **authored original + Elite** content passed at a very high rate, with its few removals being figure-render polish issues (label overlaps) rather than reasoning errors.

## Production Readiness

| | Checked | PASS | Survival | Post-gate pool status |
|---|--:|--:|--:|---|
| TIL-I | 1167 | 1107 | 94.9% | 100% independently re-solved & verified |
| CEnT-S | 1125 | 1100 | 97.8% | 100% independently re-solved & verified |
| **Overall** | **2292** | **2207** | **96.3%** | **100% verified** |

Every question now in the eligible pool has been independently re-solved with the stored answer confirmed at confidence ≥ 0.95, exactly one correct option, consistent figure (if any), and no stem/OCR/option/answer defect.

## Critical (BLOCK) defects remaining

**0.** All 47 BLOCK and 38 REVIEW questions have been removed from the production pool (`eligibleForProduction=false`, each with a `verifyDefect` reason; full ledger in `semantic_verification_blocked.jsonl`). Note for later (non-blocking): several REVIEW removals are benign — 4-option licensed items flagged only for a blank fifth option, or correct-but-<0.95-confidence items — candidates for reinstatement after a trivial cleanup; they are held out now to honor the "only PASS is eligible" rule.

# ✅ READY FOR PRODUCTION

Nothing committed or pushed. The unified-pool app engine remains queued for `seeding-final-fix` pending your go-ahead.
