# TIL-I + CEnT-S Source Audit (Phase A)

All 13 uploaded files inspected by their **actual rendered contents** (not filenames): the two 202-page CEnT-S books and "Domande TIL-I" read directly; the other 10 classified by a per-file vision workflow over sampled pages (front matter + middle sample).

## Headline finding (governs Phases C–J)
**No uploaded file is simultaneously (a) genuinely official (CISIA / Politecnico), (b) reproducible (not copyright-restricted), and (c) answer-keyed.** Every file resolves to `reference_only`, `inspiration_only_no_repro`, or `reject`. Therefore, under the ingestion rules, **zero questions may be extracted verbatim into either official bank.** The only rule-compliant production path is **original authoring** inspired by the verified syllabi (see §Decision).

## Per-file audit

| # | File | ~Pages | Type | Lang | Subjects | Keys | Exam | Official? | © | Classification | Decision |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | CEnT_S_Exam_Book.pdf | 202 | prep book (EuCrack) | en | Math, Reasoning, Bio, Chem, Phys | yes (expl.) | cents_s | third_party_prep | **yes** | general_engineering_preparation | inspiration_only_no_repro |
| 2 | CEnT_S_Exam_Book-print.PDF | 202 | prep book (dup of #1) | en | same | yes | cents_s | third_party_prep | **yes** | duplicate_of_1 | inspiration_only_no_repro |
| 3 | Domande TIL-I (1).pdf | 28 | "Machine Translated by Google" study notes | en/it | Math (some Phys) | partial | til_i | user_notes | maybe | likely_til_i_needs_verification | mostly reject (defective); few math items → inspiration only |
| 4 | tolc e practise 2-print(1).PDF | 351 | self-compiled TOLC bank ("Farshid") | en | Math, Logic, Phys, Chem, Reading | **no** | tolc | third_party_prep | **yes** | general_engineering_preparation | inspiration_only_no_repro |
| 5 | theofficial_sat_studyguide_2016.pdf | 790 | Official SAT Study Guide (College Board) | en | SAT Reading/Writing/Math | yes | sat | reference (official-to-SAT) | **yes** | reference_only / out_of_syllabus | reference_only |
| 6 | T_T_P_U…mock….pdf | 31 | commercial mock (iClass, TTPU Tashkent) | en | Math, Logic, abstract reasoning | **no** | other_engineering | third_party_prep | **yes** | general_engineering_preparation | inspiration_only_no_repro |
| 7 | CENTS Deneme Sınavı 3..pdf | 28 | CEnT-S mock (İtalyan Dostluk Derneği/felsina) | en | Reasoning, Math, Bio, Chem | **no** | cents_s | third_party_prep | yes | likely_cents_s_needs_verification | inspiration_only_no_repro |
| 8 | CENTS Deneme Sınavı 5.pdf | 28 | CEnT-S mock (felsina) | en | Math, Reasoning, Bio | **no** | cents_s | third_party_prep | no notice | confirmed_cents_s (content) | inspiration_only_no_repro |
| 9 | 5467657659357010206-merged.pdf | 204 | Moodle quiz screenshots + handwritten solutions | it/en | Math, Physics | yes | other_engineering | user_notes | no | general_engineering_preparation | reference_only |
| 10 | WhatsApp Image 2022-07-09….pdf | 19 | TIL-I admission-test phone photos + Turkish recall | en/tr | Math, Phys, CS, Logic | partial | **til_i** | user_notes (recall of real exam) | yes (real exam content) | confirmed_til_i (content) | inspiration_only_no_repro (poor image + © real-exam) |
| 11 | Formulario Fisica PoliTo TIL.pdf | 9 | handwritten physics formula sheet | it | Physics (formulas only) | n/a | both | user_notes | no | reference_only | reference_only |
| 12 | Soluzione traccia matematica seconda prova.pdf | 26 | maturità worked solutions (Skuola.net) | it | Math (calculus) | yes | unrelated | third_party_prep | **yes** | out_of_syllabus | **reject** |
| 13 | MOCK_TEST-5…italostudy…Official_Mock.pdf | 12 | commercial mock (italostudy; "Official" = self-brand) | en | Math, Logic, Reading, Bio, Chem | **no** | unclear | third_party_prep | **yes** | out_of_syllabus / unclear | inspiration_only_no_repro |

## Why nothing enters an official bank
- **Copyright:** #1/#2 (EuCrack, explicit © + "all rights reserved"), #4 (usage-restricted), #5 (College Board, "reproduction prohibited"), #6 (iClass), #12 (Skuola.net), #13 (italostudy) are published/commercial products. Rule #8/#17 → not reproducible; inspiration only.
- **Not official:** every "official/mock" label here is self-branding by a prep vendor or a personal compilation; none is a CISIA or Politecnico authority document. Rule: do not label third-party as `official`.
- **Wrong exam / out of syllabus:** #5 (SAT), #12 (Italian maturità, calculus-heavy), #4 (TOLC), #6 (TTPU Tashkent) target different exams.
- **No answer keys:** #4, #6, #7, #8, #13 have no keys — not self-contained even setting copyright aside.
- **Defective transcription:** #3 is machine-translated with OCR-garbled equations and description-style fragments; #10 is low-resolution recall photos. Rule #7 → reject/inspiration only.

## What the sources ARE good for
- **CEnT-S syllabus/structure & topic coverage:** #1/#2 (the spec: 55 Q / 110 min, Math 15 / Reasoning 15 / Bio 10 / Chem 10 / Phys 5), #7/#8 (CEnT-S question *styles* for Bio/Chem/Reasoning).
- **TIL-I style/coverage corroboration:** #10 (real TIL-I item topics), #11 (Polito TIL physics formula scope), #3 (TIL math topic list).
- **General quantitative/logic style:** #4, #6, #9, #13.
- **Not usable at all:** #5 (SAT), #12 (maturità) → reference/reject.

## Decision
1. **Official banks (TIL-I & CEnT-S):** receive **0** new questions from this upload. `cents_s_official_bank.json` stays intentionally empty (no official CISIA source exists here); `til_i_official_bank.json` (479 Q) is untouched.
2. **Production = ORIGINAL authoring** (rule #8: "create genuinely new questions when reproduction is not appropriate"), targeting `content/til_i/original/` and `content/cents_s/original/`, using these sources only for syllabus/topic/style calibration — never copying wording, never number-swapping.
3. **Priorities:** CEnT-S is the larger gap (its Biology, Chemistry, and Reasoning-on-Texts-&-Data sections are entirely unserved and are new domains for this project). TIL-I already has a 479-Q official bank; its original bank can extend thin areas (reading, representation, CS).
4. Every original question must pass the full gate: independent solve + second-pass verify + single-correct + syllabus-verified + duplicate-checked, confidence ≥0.90 (≥0.95 reading/figure), else rejected.
