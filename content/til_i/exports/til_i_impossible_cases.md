# TIL-I Official Bank — Unanswerable Cases Report (bank v1.5)

Answer coverage: **467 / 479 questions answered (97.5%)**. The **12** questions below could NOT be given a usable answer (`officialAnswer` or high-confidence `computedAnswer`) for the reasons stated. Every one is a **source-completeness defect** — the information required to answer is not present in the provided source scans — not a pipeline failure. A non-authoritative `tentativeAnswer` is retained on each record where a best-effort guess exists, but it is deliberately NOT used as the answer (`answerSource: "unanswerable"`, `correctAnswer: "unknown"`, tag `answer_impossible`).

## Root-cause summary
| Root cause | Count | Section(s) |
|---|---|---|
| Reading passage never photographed (only the question screens were captured) | 8 | reading_comprehension |
| Reading passage recovered but text does not unambiguously fix one option (<0.90) | 2 | reading_comprehension |
| Figure/reference solid not present in the scan | 1 | representation |
| Defective source question (missing datum; correct value not an option) | 1 | physics |
| **Total** | **12** | |

## Detailed list
| id | section | reason | tentative (not used) |
|---|---|---|---|
| `til_i_ex_read_007` | reading | passage not in scan — only question screens captured | A (0.85) |
| `til_i_ex_read_012` | reading | passage not in scan | B (0.55) |
| `til_i_ex_read_013` | reading | passage not in scan | A (0.55) |
| `til_i_ex_read_014` | reading | passage not in scan | B (0.80) |
| `til_i_ex_read_015` | reading | passage not in scan | A (0.80) |
| `til_i_ex_read_017` | reading | passage not in scan | E (0.55) |
| `til_i_ex_read_018` | reading | passage not in scan | E (0.78) |
| `til_i_ex_read_019` | reading | passage not in scan | B (0.55) |
| `til_i_ex_read_004` | reading | passage recovered (p058, partially cut off) but answer ambiguous | A (0.82) |
| `til_i_ex_read_006` | reading | passage recovered but text does not unambiguously fix one option | C (0.72) |
| `til_i_ex_repr_011` | representation | the axonometric reference solid the stem asks to match is not on any source page or the cropped figure (option bottoms also cut off) | — |
| `til_i_ex_phys_141` | physics | source-question defect: stem gives no time so energy is undefined and the correct power (20 W) is not among the options | D (0.35) |

## Why these are genuinely impossible (not a solver limitation)
- **Reading (10):** These items come from phone-photo source sets (`src_repr`, `src_4a`, `src_4c`) in which the photographer captured the *question* screens but not the preceding *passage* screens. A wide-window recovery pass (±6 pages) recovered 4 of the missing passages (now answered); the remaining passages are simply absent from the images. Recovering them requires re-photographing the original test's passage screens.
- **Representation (1):** `repr_011` requires matching orthographic views to a given axonometric solid, but that solid was never captured (the option figures are even cut off at the bottom).
- **Physics (1):** `phys_141` is internally inconsistent as printed (asks for energy but supplies no time; the derivable quantity, 20 W of power, is not an option) — a defect in the source question itself.

## Recommended remediation (source-side, not solvable in-pipeline)
1. Re-photograph / re-scan the missing reading passages for the 8 "not in scan" items and the 2 ambiguous ones, then re-run the solve pass.
2. Re-capture the `repr_011` axonometric solid figure.
3. Replace or drop `phys_141` (defective as printed).

Until then these 12 remain `answerSource: "unanswerable"` and should be **excluded from any graded quiz**, but their stems are retained for completeness and future recovery.
