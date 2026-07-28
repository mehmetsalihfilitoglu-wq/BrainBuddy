# IMAT Question Bank — content/imat/

Structured, exam-isolated IMAT questions extracted from **official IMAT papers only**.

## Hard isolation rule

Every question here belongs to **IMAT and nothing else**. Do NOT place CEnT-S, TOLC,
TIL, SAT, BMAT, UKCAT, A-Level, LGS, TYT, or AYT questions in these folders — even when
the subject (biology, chemistry, physics…) overlaps. Shared subjects do **not** mean
shared folders. Each exam gets its own namespace; this one is `imat`.

A question is only IMAT if its **source is explicitly and reliably IMAT** (an official
Cambridge/MUR IMAT specimen or past paper). Subject alone is never enough. If the source
is unclear → `needs_manual_review/`, never a subject folder.

## Folders

```
content/imat/
  biology/            chemistry/        physics/
  mathematics/        critical_thinking/  reading_comprehension/
  general_knowledge/  needs_manual_review/
```

## File naming

One JSON file per **(source paper × subject)**, holding an array of question objects,
so provenance stays clear and de-duplication is easy:

```
content/imat/biology/imat_2011_specimen.json      # biology Qs from the 2011 specimen
content/imat/chemistry/imat_2019_past_paper.json  # chemistry Qs from the 2019 past paper
content/imat/needs_manual_review/imat_2016_humanitas.json
```

(If per-question files are preferred instead, the same objects split one-per-file:
`imat_biology_001.json`, etc. — say the word and the exporter will emit that layout.)

## Question schema

```json
{
  "id": "imat_2011_specimen_biology_037",
  "exam": "IMAT",
  "subject": "biology",
  "topic": "cell biology",
  "difficulty": null,
  "source_file": "234515-imat-specimen-paper-2011.pdf",
  "source_exam": "IMAT",
  "source_year": 2011,
  "source_type": "specimen",
  "original_number": 37,
  "question": "verbatim question text",
  "choices": { "A": "…", "B": "…", "C": "…", "D": "…", "E": "…" },
  "correct_answer": "C",
  "explanation": null,
  "explanation_status": "missing",
  "has_figure": false,
  "duplicate_source": null,
  "extraction_confidence": "high"
}
```

Rules applied during extraction:
- **No rewriting / no translation / no invented answers / no generated questions.** Verbatim only.
- `correct_answer` comes from the paper's **official answer key**. If missing/uncertain → the
  question goes to `needs_manual_review/` (never guessed).
- Missing explanation → still imported, `explanation_status = "missing"`.
- `has_figure: true` (diagrams/graphs that can't be transcribed as text) → routed to
  `needs_manual_review/` unless the figure is inessential, because the item is incomplete without it.
- Duplicates (same question in multiple papers) → kept once, `duplicate_source` records the other.
- `subject` uses IMAT sections: biology, chemistry, physics, mathematics, critical_thinking
  (logical reasoning / problem solving), reading_comprehension, general_knowledge.

## Source classification

See `SOURCE_CLASSIFICATION.md` for the definitive list of which uploaded files are
IMAT (import), which are NOT (skip), and which are ambiguous (review).
