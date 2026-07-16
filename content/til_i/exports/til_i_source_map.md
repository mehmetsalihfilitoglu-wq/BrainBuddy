# TIL-I Official Source Map

**Target exam:** TIL-I (Test In Laboratorio – Ingegneria, **Politecnico di Torino**).
**Prepared:** 2026-07 · **Rule:** official content is separated from commentary/reference. Where a source's nature could not be confirmed from its content, it is marked **UNVERIFIED — needs inspection** rather than guessed.

> ⚠️ **Key distinction — TIL-I vs TOLC-I.** Several uploaded files are **TOLC-I** (the CISIA national engineering test), which is a **different exam** from Polito's TIL-I (different section counts, TOLC-I has Chemistry, TIL-I has Computer Science + Representation). TOLC-I files are therefore classified as **related reference**, not TIL-I official. They must never be seeded into the TIL-I official bank.

## Tier 1 — CONFIRMED TIL-I OFFICIAL (Polito)

| File | Type | Year | Content | Use |
|---|---|---|---|---|
| `Quick Guide TIL-I-(English version).pdf` | Official syllabus & structure | current | The authoritative TIL-I section structure, per-section topic syllabus (Math, Reading+Logic, Physics, Basic Technical=Representation+CS), and rules. | **Structure + taxonomy source of truth** |
| `TIL-I _ MOCK EXAM.pdf` | Official mock exam (questions) | n/d | 42 questions: **Math 18, Comprehension 6, Physics 12, Logic 6**. NB: distribution differs from the *current* Quick-Guide structure (16/10/10/6) — this mock predates or simplifies the current format (**no Basic-Technical section**). | **Official question extraction** |
| `TIL-I _ MOCK_EXAM_ ANSWERS.pdf` | Official answer key | n/d | Answer letters for the mock: Math 1–18, Physics 1–12, Comprehension 1–6, Logic 1–6. | **Answer keys** (already extracted, see official bank) |

## Tier 2 — TIL-I QUESTION SETS (Polito-style, provenance UNVERIFIED)

| File | Pages | Inferred content | Status |
|---|---|---|---|
| `TIL MATHEMATICS QUESTIONS.pdf` | 68 | TIL-I math practice/past questions | **UNVERIFIED** — filename indicates TIL; needs image extraction (math = formulas) |
| `TIL-PHYSICS-QUESTIONS.pdf` | 42 | TIL-I physics practice/past questions | **UNVERIFIED** — needs inspection |

## Tier 3 — RELATED REFERENCE (TOLC-I — different exam, NOT TIL-I official)

| File | Content | Note |
|---|---|---|
| `TOLC-I_EXAM_STRUCTURE_AND_SYLLABUS 2 (1).pdf` | TOLC-I structure (Math 20 / Logic 10 / Sciences 10 / Reading 10 = 50) + full syllabus + scoring (+1 / −0.25 / 0). | Structural cross-reference only. Syllabus content overlaps TIL-I except **TOLC-I has Chemistry**. |
| `Esempio di prova English TOLC-I (2).pdf` | TOLC-I sample paper. | **DUPLICATE** of the file below (byte-identical extraction). |
| `tolc-i- past paper.pdf` | TOLC-I sample/past paper. | Same document as the row above. |

## Tier 4 — UNVERIFIED PAST-PAPER / QUESTION PDFs (need content inspection to classify)

| File | Pages | Best inference (LOW confidence) |
|---|---|---|
| `4_5859267575740894496.pdf` | 151 | Italian admission-test past paper (a screenshot showed its "Admission Test" CORPO-probability item). Likely IMAT/engineering past paper. |
| `4_5886766451745884996.pdf` | 151 | Same size as above — possible duplicate/related past paper. |
| `4_5886766451745885002.pdf` | 34 | Question set — subject unknown. |
| `4_5897861722366020670.pdf` | 8 | Short question set (text extracted, 252 lines) — needs classification. |
| `4_6001468552727497134.pdf` | 83 | Question set — subject unknown. |
| `4_6050926737523478323.pdf` | 106 | Question set — subject unknown. |
| `Mock_Test-1.pdf` | 28 | A mock test — exam family unconfirmed. |
| `Pol.pdf` | 96 | Possibly Politecnico material — unconfirmed. |
| `MATH.pdf` | 164 | Math question set/reference. |
| `PHYSICS.pdf` | 137 | Physics question set/reference. |
| `LOGIC.pdf` | 29 | Logic question set. |
| `ENGLISH.pdf` | 28 | English question set. |
| `FİZİK ÇIKMIŞ .pdf` | 142 | "Physics past" (Turkish "çıkmış" = past-exam) — a screenshot showed an "Admission Test" turntable item. Admission-test physics past questions. |

## Tier 5 — PREP TEXTBOOKS / STUDY REFERENCES (NOT question sources; use only to inform ORIGINAL production style & topic coverage)

| File | Content |
|---|---|
| `Cambridge Thinking skills by John Butterworth.pdf` (97p) | Critical-thinking textbook. |
| `CRITICAL THINKING SKILLS STELLA COTTRELL.pdf` (174p) | Critical-thinking textbook. |
| `Sat critical reading.pdf` | SAT reading prep. |
| `MATH CAMBRIDGE IB DIPLOMA.pdf` (401p) | IB math textbook. |
| `Math book answers.pdf` (54p) | Math answers reference. |
| `ALGEBRA.pdf` (36p) | Algebra reference. |
| `PHYSICS A LEVEL SECOND EDITION.pdf` (663p) | A-level physics textbook. |
| `PHYSICS HODDER.pdf` (138p), `PHYSICS IB CAMBRIDGE.pdf` (473p), `PHYSICS- ALEVEL- REVISION GUIDE.pdf` (115p) | Physics textbooks/revision guides. |

## Extraction constraints (important)

- **Math & many physics questions are formula/figure-based.** Plain-text (pdftotext) extraction **drops all equations and figures** — e.g. every mock Math stem (Q1–Q18) came out blank. These questions require **image-based extraction** from the PDF pages (the IMAT figure-extraction approach), and must **never** have their formulas guessed/reconstructed.
- **Text-only questions** (reading comprehension, most logic, physics word-problems) extract cleanly and are already captured.
- **Answer keys** for the mock are complete and reliable.

## Screenshots you pasted (for level/style reference, provenance noted)
- **CORPO probability** ("26 letters, Italian alphabet…") — from `4_5859267575740894496.pdf`, an "Admission Test". Combinatorics/probability, TIL-Math level.
- **Ice latent-heat** (Q=337.4 kJ, λ) and **turntable angular velocity** — physics past items (calorimetry; circular motion) at exactly TIL-I physics level. Your handwritten solutions (λ≈333 kJ/kg; v=ωr) are correct.
