# TIL-I + CEnT-S Ingestion — Rejection Report

Records every source and candidate question removed from production, with an explicit reason. Rejected question records live in `content/til_i/rejected/` and `content/cents_s/rejected/`.

## Rejection reason codes
`copyrighted_no_repro` · `not_official_mislabeled` · `out_of_syllabus` · `incomplete_stem` · `unreadable_option` · `ambiguous_symbol` · `missing_figure` · `missing_passage` · `multiple_correct` · `no_correct` · `unstated_assumption` · `inconsistent_units` · `diagram_contradicts_stem` · `unverifiable_answer` · `syllabus_uncertain` · `wrong_difficulty_for_exam` · `near_duplicate` · `low_confidence` · `defective_source`

## File-level rejections / non-extraction decisions
No file qualified for verbatim extraction into a production bank. Dispositions:

| File | Decision | Reason code(s) |
|---|---|---|
| CEnT_S_Exam_Book.pdf / -print.PDF | inspiration_only | `copyrighted_no_repro` (EuCrack ©), `not_official_mislabeled` |
| Domande TIL-I (1).pdf | reject (mostly) | `defective_source` (machine-translated, OCR-garbled equations, description fragments) |
| tolc e practise 2-print.PDF | inspiration_only | `copyrighted_no_repro`, `unverifiable_answer` (no keys), TOLC not TIL |
| theofficial_sat_studyguide_2016.pdf | reference_only | `out_of_syllabus` (SAT), `copyrighted_no_repro` |
| T_T_P_U mock.pdf | inspiration_only | `copyrighted_no_repro`, `not_official_mislabeled`, `unverifiable_answer` |
| CENTS Deneme Sınavı 3 / 5 | inspiration_only | `copyrighted_no_repro`, `not_official_mislabeled`, `unverifiable_answer` (no keys) |
| 5467657659357010206-merged.pdf | reference_only | `not_official_mislabeled` (unknown course), personal notes |
| WhatsApp Image ….pdf | inspiration_only | `copyrighted_no_repro` (real-exam recall), image too poor for reliable transcription |
| Formulario Fisica PoliTo TIL.pdf | reference_only | formula sheet, not questions |
| Soluzione traccia matematica…pdf | **reject** | `out_of_syllabus` (maturità, calculus-heavy) |
| MOCK_TEST-5 italostudy.pdf | inspiration_only | `copyrighted_no_repro`, `not_official_mislabeled`, `unverifiable_answer` |

Net: **0 questions extracted to any bank.** Production must be original (see source audit §Decision).

## Question-level rejections
_(populated during Phases D–I)_
