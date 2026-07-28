# CEnT-S — Verified Exam Specification

**Exam:** CEnT-S — CISIA English-language admission test for English-taught bachelor's programs in Italy (engineering, economics, pharmacy, other scientific disciplines). Introduced by **CISIA in November 2025** to replace the English TOLC exams (TOLC-I/E/F).

## ⚠️ Source-verification status
The structure below is **derived from a third-party preparation book** — *"CEnT-S 2025 Preparation Book" by EuCrack* (ISBN 9798269728759, © 2025 EuCrack), which states it is an independent publication and that "The CEnT-S exam is administered by CISIA." **It is NOT a primary CISIA document.** No primary CISIA CEnT-S specification is present in the uploaded sources. Treat the structure as a strong working assumption; verify against an official CISIA CEnT-S source before any high-stakes use.

## Structure (as stated by the EuCrack book — third-party)
| Section | Questions | Minutes |
|---|---|---|
| Mathematics | 15 | 30 |
| Reasoning on Texts and Data | 15 | 30 |
| Biology | 10 | 20 |
| Chemistry | 10 | 20 |
| Physics | 5 | 10 |
| **Total** | **55** | **110** |

- Section-timed: once a section's time is up you advance and **cannot return**; finishing early forfeits the remaining time.

## Syllabus (permitted subjects)
Mathematics, Reasoning on Texts and Data, **Biology, Chemistry**, Physics.
- **HAS Biology and Chemistry** (unlike TIL-I).
- **NO Representation / technical drawing, NO Computer Science** section (unlike TIL-I).
- Detailed topic tree: `content/cents_s/exports/cents_s_taxonomy.json`.

## Scoring
`not_verified_from_current_sources` — no scoring rule is stated in the available CEnT-S material. Do NOT assume the TOLC scheme.

## Distinction from TIL-I (strict isolation)
CEnT-S and TIL-I are separate products. Overlap only in Mathematics / Physics / (verbal reasoning) at the *topic* level; even then, section framing, depth and the Bio/Chem vs Representation/CS split differ. Compatibility is decided per question, never assumed.

## Copyright / official-status caveat (load-bearing)
The only CEnT-S-specific question sources in this upload are **third-party prep books / mocks** (EuCrack; "CENTS Deneme Sınavı" mocks; "italostudy" mock). Per the ingestion rules these are `third_party_prep`, **NOT** official, and are **copyright-restricted**: their questions may be used for topic/style analysis and to inspire genuinely NEW original questions, but must **not** be reproduced verbatim as production items, and must **never** be placed in a `sourceType: "official"` bank. Consequently the CEnT-S **official** bank is expected to remain empty unless a genuine CISIA official source appears; CEnT-S production is **original** (`content/cents_s/original/`).
