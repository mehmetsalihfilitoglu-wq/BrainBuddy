# TIL-I — Verified Exam Specification

**Exam:** TIL-I (Test In Laboratorio, Ingegneria) — Politecnico di Torino English-language engineering admission test.
**Status of this spec:** Preserved from the previously accepted production structure (Polito Quick Guide). No newer primary Politecnico source in the current upload supersedes it.

## Structure (production target)
| Section | Questions | Minutes |
|---|---|---|
| Mathematics | 16 | 36 |
| Reading & Logic | 10 (5 reading + 5 logic) | 20 |
| Physics | 10 | 22 |
| Basic Technical (Representation 3 + Computer Science 3) | 6 | 12 |
| **Total** | **42** | **90** |

## Syllabus notes
- Subjects: Mathematics, Physics, Logic, Reading comprehension, Representation (technical drawing), Computer Science.
- **NO Chemistry, NO Biology.**
- **Calculus (differential/integral) is explicitly excluded.**
- Full topic tree: `content/til_i/taxonomy/til_i_taxonomy.json`.

## Scoring
`not_verified_from_current_sources` — the TIL-I sources provided do not state a scoring rule. The TOLC-I +1/−0.25/0 scheme is a DIFFERENT exam and must not be assumed.

## Distinction from other exams (do not merge)
- **TOLC-I / TOLC-E (CISIA):** different structure, includes Chemistry; not TIL-I.
- **CEnT-S (CISIA, 2025):** different exam (has Biology + Chemistry, no Representation/CS) — see `content/cents_s/exports/cents_s_verified_exam_spec.md`. Kept in a fully separate product.
- **SAT / Italian maturità:** unrelated; reference only.

## Current official bank
`content/til_i/official/til_i_official_bank.json` — 479 questions (bank v1.5-answered, 97.5% answered). This ingestion phase adds only NEW, fully-verified material through validation; it does not modify accepted records except by explicit validation.
