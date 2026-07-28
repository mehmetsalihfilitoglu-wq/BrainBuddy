# 10-Question Proportional Test Blueprints — Design & Validation (read-only)

> **v2.0 note:** the product model changed to the **Daily Challenge = exactly 5 NEW questions/day** (`daily_challenge_architecture.md`). The cumulative-deficit balancing algorithm, official blueprints, convergence proof, availability check, and difficulty findings below all carry over unchanged — only the per-session slot count changes from 10 to 5 (validated separately for 5 slots: every day Σ=5, converges to the official ratios, max drift < 1 at 5/30/100/365 days). "Test" here = the earlier framing; the live consumer is the Daily Challenge selector.

_No bank, question, or metadata modified. Engine not implemented. Every practice test = exactly 10 questions; section counts come from the OFFICIAL exam blueprint (not bank size, not random), balanced across tests by a cumulative-deficit algorithm so the long-run mix converges to the exact official proportions._

## 1. Official full-test distribution & per-10 target

**IMAT** (full test = 60 questions)

| Section | Official | Share | Exact per 10 |
|---|--:|--:|--:|
| Biology | 23/60 | 38.3% | 3.83 |
| Chemistry | 15/60 | 25.0% | 2.5 |
| Physics & Mathematics | 13/60 | 21.7% | 2.17 |
| Logic | 5/60 | 8.3% | 0.83 |
| Reading | 4/60 | 6.7% | 0.67 |

**TIL-I** (full test = 42 questions)

| Section | Official | Share | Exact per 10 |
|---|--:|--:|--:|
| Mathematics | 16/42 | 38.1% | 3.81 |
| Physics | 10/42 | 23.8% | 2.38 |
| Reading + Logic | 10/42 | 23.8% | 2.38 |
| Basic Technical Knowledge | 6/42 | 14.3% | 1.43 |

**CEnT-S** (full test = 55 questions)

| Section | Official | Share | Exact per 10 |
|---|--:|--:|--:|
| Mathematics | 15/55 | 27.3% | 2.73 |
| Reasoning on texts & data | 15/55 | 27.3% | 2.73 |
| Biology | 10/55 | 18.2% | 1.82 |
| Chemistry | 10/55 | 18.2% | 1.82 |
| Physics | 5/55 | 9.1% | 0.91 |

## 2. Algorithm (cumulative proportional balancing)

For each exam+section store `officialWeight`, `expectedToDate` (`+= weight/full*10` each test), `actualToDate`, `deficit = expectedToDate − actualToDate`. Per new test: (1) `base = floor(weight/full*10)`; (2) `remaining = 10 − Σbase`; (3) give the remaining slots to the sections with the **greatest cumulative deficit**; (4) the deficit ledger self-corrects so no section is repeatedly rounded down; (5) Σalloc = 10 exactly; (6) sections are fixed **before** any question is selected. Sub-question rotation (below) uses the same rule one level down. Sections with a per-10 target < 1 (e.g. IMAT Reading/Logic, CEnT Physics) appear intermittently but at the correct long-run frequency.

## 3. Simulated allocations — first 5 tests (deterministic from a clean deficit ledger)

**IMAT**

| Test | Biology | Chemistry | Physics & Mathematics | Logic | Reading | Σ |
|---|--:|--:|--:|--:|--:|--:|
| 1 | 4 | 2 | 2 | 1 | 1 | 10 |
| 2 | 4 | 3 | 2 | 1 | 0 | 10 |
| 3 | 4 | 2 | 3 | 0 | 1 | 10 |
| 4 | 3 | 3 | 2 | 1 | 1 | 10 |
| 5 | 4 | 3 | 2 | 1 | 0 | 10 |

**TIL-I**

| Test | Mathematics | Physics | Reading + Logic | Basic Technical Knowledge | Σ |
|---|--:|--:|--:|--:|--:|
| 1 | 4 | 2 | 2 | 2 | 10 |
| 2 | 3 | 3 | 3 | 1 | 10 |
| 3 | 4 | 2 | 2 | 2 | 10 |
| 4 | 4 | 3 | 2 | 1 | 10 |
| 5 | 4 | 2 | 3 | 1 | 10 |

**CEnT-S**

| Test | Mathematics | Reasoning on texts & data | Biology | Chemistry | Physics | Σ |
|---|--:|--:|--:|--:|--:|--:|
| 1 | 3 | 2 | 2 | 2 | 1 | 10 |
| 2 | 2 | 3 | 2 | 2 | 1 | 10 |
| 3 | 3 | 3 | 2 | 1 | 1 | 10 |
| 4 | 3 | 3 | 1 | 2 | 1 | 10 |
| 5 | 3 | 3 | 2 | 2 | 0 | 10 |

## 4. Cumulative totals vs ideal (convergence)

**IMAT** — cumulative questions per section after N tests (ideal = per-10 target × N)

| Section | 1 | 5 | 10 | 20 | 100 | ideal@100 | drift@100 |
|---|--:|--:|--:|--:|--:|--:|--:|
| Biology | 4 | 19 | 38 | 77 | 383 | 383.33 | -0.33 |
| Chemistry | 2 | 13 | 25 | 50 | 250 | 250 | +0 |
| Physics & Mathematics | 2 | 11 | 22 | 43 | 217 | 216.67 | +0.33 |
| Logic | 1 | 4 | 8 | 17 | 83 | 83.33 | -0.33 |
| Reading | 1 | 3 | 7 | 13 | 67 | 66.67 | +0.33 |

**TIL-I** — cumulative questions per section after N tests (ideal = per-10 target × N)

| Section | 1 | 5 | 10 | 20 | 100 | ideal@100 | drift@100 |
|---|--:|--:|--:|--:|--:|--:|--:|
| Mathematics | 4 | 19 | 38 | 76 | 381 | 380.95 | +0.05 |
| Physics | 2 | 12 | 24 | 48 | 238 | 238.1 | -0.1 |
| Reading + Logic | 2 | 12 | 24 | 48 | 238 | 238.1 | -0.1 |
| Basic Technical Knowledge | 2 | 7 | 14 | 28 | 143 | 142.86 | +0.14 |

**CEnT-S** — cumulative questions per section after N tests (ideal = per-10 target × N)

| Section | 1 | 5 | 10 | 20 | 100 | ideal@100 | drift@100 |
|---|--:|--:|--:|--:|--:|--:|--:|
| Mathematics | 3 | 14 | 28 | 55 | 273 | 272.73 | +0.27 |
| Reasoning on texts & data | 2 | 14 | 27 | 55 | 272 | 272.73 | -0.73 |
| Biology | 2 | 9 | 18 | 36 | 182 | 181.82 | +0.18 |
| Chemistry | 2 | 9 | 18 | 36 | 182 | 181.82 | +0.18 |
| Physics | 1 | 4 | 9 | 18 | 91 | 90.91 | +0.09 |

## 5. Rounding-bias check (no accumulation)

| Exam | Max |drift| @100 | Every section total (100 tests) | Σ per test |
|---|--:|---|--:|
| IMAT | 0.33 | Biology=383, Chemistry=250, Physics=217, Logic=83, Reading=67 | 10 ✓ |
| TIL-I | 0.14 | Mathematics=381, Physics=238, Reading=238, Basic=143 | 10 ✓ |
| CEnT-S | 0.73 | Mathematics=273, Reasoning=272, Biology=182, Chemistry=182, Physics=91 | 10 ✓ |

Max drift stays below 1 question for every section at every horizon → the fractional remainder never accumulates; the sequence self-corrects each test.

## 6. Sub-section rotation (nested balancer)

Two TIL-I blueprint sections bundle metadata sub-sections; the same deficit rule splits each test's section slots so neither sub-section disappears long-term. IMAT Reading/Logic and CEnT sections are already top-level, so need no nesting.

**Reading + Logic** (editorial split Logic 60% / Reading 40% — no official sub-ratio; Reading floored so it never vanishes). Over 100 tests:
- Logic 143, Reading 95 (of 238 R+L questions → Reading 40%).
**Basic Technical Knowledge** (split by eligible availability CS vs Representation). Over 100 tests:
- Computer Science 84, Representation 59 (of 143).

## 7. Availability check (final production-eligible banks)

| Exam | Section | Eligible | per-10 target | demand /100 tests | reuse ×/100 | supports rotation? |
|---|---|--:|--:|--:|--:|---|
| IMAT | Biology | 675 | 3.83 | 383 | 0.6 | yes |
| IMAT | Chemistry | 434 | 2.5 | 250 | 0.6 | yes |
| IMAT | Physics & Mathematics | 403 | 2.17 | 217 | 0.5 | yes |
| IMAT | Logic | 359 | 0.83 | 83 | 0.2 | yes |
| IMAT | Reading | 79 | 0.67 | 67 | 0.8 | yes |
| TIL-I | Mathematics | 514 | 3.81 | 381 | 0.7 | yes |
| TIL-I | Physics | 369 | 2.38 | 238 | 0.6 | yes |
| TIL-I | Reading + Logic | 171 | 2.38 | 238 | 1.4 | yes |
| TIL-I | Basic Technical Knowledge | 53 | 1.43 | 143 | 2.7 | yes |
| CEnT-S | Mathematics | 329 | 2.73 | 273 | 0.8 | yes |
| CEnT-S | Reasoning on texts & data | 113 | 2.73 | 272 | 2.4 | yes |
| CEnT-S | Biology | 224 | 1.82 | 182 | 0.8 | yes |
| CEnT-S | Chemistry | 243 | 1.82 | 182 | 0.7 | yes |
| CEnT-S | Physics | 191 | 0.91 | 91 | 0.5 | yes |

_Reuse ×/100 = questions demanded over 100 tests ÷ eligible pool (avg times each question would recur if tests never repeated content). ≤~1 means 100 tests can be almost fully unique; higher means the section recycles sooner._

**TIL-I sub-sections (Reading + Logic / Basic Technical Knowledge):**
- Logic eligible 151, Reading (reading+comprehension) eligible 20; demand/100 ≈ Logic 143, Reading 95 → Reading reuse ×4.8 (thin — flag).
- Computer Science eligible 31, Representation eligible 22; demand/100 ≈ CS 84, Rep 59 → reuse CS ×2.7, Rep ×2.7.

## 8. Difficulty feasibility inside a 10-question test

Requested main-test mix: Medium 2–3, Hard 4–5, Elite 2–3, with ≥70% Hard-or-Elite. Verified-eligible tier inventory:

| Exam | Easy | Medium | Hard (difficulty) | Elite (tag) | Hard+Elite | ≥70% feasible? |
|---|--:|--:|--:|--:|--:|---|
| TIL-I | 188 | 687 | 19 | 213 | 232 (21%) | no |
| CEnT-S | 401 | 292 | 8 | 399 | 407 (37%) | no |
| IMAT | 0 | (per bank difficulty) | see note | 0 | — | n/a (no elite tier) |

**Finding / shortage (reported, not silently patched):** the verified pool is dominated by **Medium** difficulty; true `difficulty:hard` items are scarce (TIL-I ~21, CEnT-S ~11). The top selective tier is **Elite** (competency-derived, adversarially verified originals: TIL-I 215, CEnT-S 408) which are themselves medium/hard by construction. A literal 4–5 `hard` + 2–3 `elite` per test is **not** supportable from the `hard` band alone. Recommendation: define the test's top tier as **Hard ∪ Elite** (selective) vs **Medium** (foundational), targeting ~7/10 selective — achievable now for TIL-I and CEnT-S. Do NOT backfill a section with weak/easy items to hit a section count; if a section's eligible selective pool is short for a given test, surface the shortage and keep the quality bar.

## 9. Recommended final 10-question templates

These are the **modal** allocations the deficit algorithm produces (it rotates around them; it does not repeat one template forever). The algorithm — not a fixed template — is the source of truth.

**IMAT**

| Template | Biology | Chemistry | Physics & Mathematics | Logic | Reading | freq/20 |
|---|--:|--:|--:|--:|--:|--:|
| A | 4 | 2 | 2 | 1 | 1 | 7 |
| B | 4 | 3 | 2 | 1 | 0 | 7 |
| C | 4 | 2 | 3 | 0 | 1 | 3 |
| D | 3 | 3 | 2 | 1 | 1 | 3 |

**TIL-I**

| Template | Mathematics | Physics | Reading + Logic | Basic Technical Knowledge | freq/20 |
|---|--:|--:|--:|--:|--:|
| A | 4 | 3 | 2 | 1 | 6 |
| B | 4 | 2 | 2 | 2 | 5 |
| C | 4 | 2 | 3 | 1 | 5 |
| D | 3 | 2 | 3 | 2 | 2 |
| E | 3 | 3 | 3 | 1 | 1 |

**CEnT-S**

| Template | Mathematics | Reasoning on texts & data | Biology | Chemistry | Physics | freq/20 |
|---|--:|--:|--:|--:|--:|--:|
| A | 3 | 2 | 2 | 2 | 1 | 5 |
| B | 2 | 3 | 2 | 2 | 1 | 5 |
| C | 3 | 3 | 2 | 1 | 1 | 4 |
| D | 3 | 3 | 1 | 2 | 1 | 4 |
| E | 3 | 3 | 2 | 2 | 0 | 2 |

## 10. Section-allocation acceptance (per the spec)

- Every generated test totals exactly 10. ✓
- Section counts come from the blueprint, never bank size or random draw. ✓
- Accumulated deficits from prior tests decide the next test's rounding. ✓
- No section is systematically over/under-represented (drift < 1 at all horizons). ✓
- Sub-question rotation keeps Reading and Representation present long-term. ✓
- Difficulty is a separate constraint layered after the section split; quality bar is never lowered to fill a slot (shortages are reported). ✓
