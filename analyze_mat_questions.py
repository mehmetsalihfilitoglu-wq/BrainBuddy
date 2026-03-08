#!/usr/bin/env python3
"""Analyze MAT LGS JSON files: parse failures, short_item (exact + heuristic)."""

import json
import os

MAT_DIR = "app/src/main/assets/lgs_import/mat"

PROBLEM_KEYWORDS = [
    "problemi", "problem", "oran", "yüzde", "grafik", "tablo",
    "şekilde", "aşağıdaki", "metne göre", "parçaya göre"
]


def get_stem(q):
    stem = (q.get("stem") or q.get("questionText") or "").strip()
    return stem


def get_options_raw(q):
    opts = q.get("options") or q.get("choices")
    return opts


def parse_lgs_question(o, index):
    """Replicate QuestionPackImporter.parseLgsQuestion logic.
    Returns (None, reason) on failure, or (entity_data, None) on success.
    """
    stem = (o.get("stem") or o.get("questionText") or "").strip()
    if not stem:
        return None, "stem_blank"

    options_raw = o.get("options") or o.get("choices")
    if options_raw is None:
        return None, "options_missing"

    # Filter blank options (Kotlin: optString/opt().toString, then filter isNotBlank)
    raw_opts = []
    for x in options_raw:
        s = str(x).strip() if x is not None else ""
        if s:
            raw_opts.append(s)

    if len(raw_opts) < 2:
        return None, f"fewer_than_2_options_after_filter ({len(raw_opts)} valid)"

    return {"stem": stem, "questionType": o.get("questionType", ""), "index": index}, None


def has_problem_keyword(stem):
    s = stem.lower()
    for kw in PROBLEM_KEYWORDS:
        if kw in s:
            return True
    return False


def is_short_item_heuristic(stem, question_type):
    """scan_short_items.py heuristic."""
    stem_len = len(stem)
    if question_type == "short_item":
        return True
    if stem_len < 80:
        return True
    if stem_len < 120 and not has_problem_keyword(stem):
        return True
    return False


def main():
    base = os.path.dirname(os.path.abspath(__file__))
    mat_path = os.path.join(base, MAT_DIR)

    parse_failures = []
    short_item_exact = []
    short_item_heuristic = []

    json_files = sorted([f for f in os.listdir(mat_path) if f.endswith(".json")])

    for fn in json_files:
        filepath = os.path.join(mat_path, fn)
        rel_path = os.path.join(MAT_DIR, fn)
        try:
            with open(filepath, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception as e:
            parse_failures.append({
                "file": rel_path,
                "index": -1,
                "stem_preview": "",
                "reason": f"file_load_error: {e}"
            })
            continue

        questions = data.get("questions") or []
        for i, q in enumerate(questions):
            entity, reason = parse_lgs_question(q, i)
            if entity is None:
                stem_preview = (get_stem(q) or "(no stem)")[:80]
                parse_failures.append({
                    "file": rel_path,
                    "index": i,
                    "stem_preview": stem_preview,
                    "reason": reason
                })
            else:
                stem = entity["stem"]
                qt = entity.get("questionType") or ""

                full_q = {**q, "file": rel_path, "index": i}

                if qt == "short_item":
                    short_item_exact.append(full_q)

                if is_short_item_heuristic(stem, qt):
                    short_item_heuristic.append({
                        "file": rel_path,
                        "index": i,
                        "stem": stem,
                        "stem_length": len(stem),
                        "questionType": qt,
                        "full_question": full_q
                    })

    # Report
    print("=" * 60)
    print("1. PARSE-FAILING QUESTIONS (parseLgsQuestion returns null)")
    print("=" * 60)
    for p in parse_failures:
        print(f"  File: {p['file']}")
        print(f"  Index: {p['index']}")
        print(f"  Stem preview: {p['stem_preview'][:80]}...")
        print(f"  Reason: {p['reason']}")
        print()

    print("=" * 60)
    print("2. SHORT_ITEM (questionType == 'short_item' exact match)")
    print("=" * 60)
    for q in short_item_exact:
        stem = get_stem(q)
        print(f"  File: {q['file']}, Index: {q['index']}")
        print(f"  Stem: {stem[:100]}{'...' if len(stem) > 100 else ''}")
        print(f"  Full question data: {json.dumps(q, ensure_ascii=False, indent=2)[:500]}...")
        print()

    print("=" * 60)
    print("3. SHORT_ITEM (scan_short_items heuristic: stem<80 OR stem<120+no problem keywords OR questionType short_item)")
    print("=" * 60)
    for s in short_item_heuristic:
        print(f"  File: {s['file']}, Index: {s['index']}")
        print(f"  Stem length: {s['stem_length']}, questionType: {s['questionType']}")
        print(f"  Stem: {s['stem'][:100]}{'...' if len(s['stem']) > 100 else ''}")
        print()

    # JSON output for programmatic use
    output = {
        "parse_failures": parse_failures,
        "short_item_exact": short_item_exact,
        "short_item_heuristic": short_item_heuristic,
    }
    out_path = os.path.join(base, "mat_analysis_output.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\nFull output saved to: {out_path}")
    print(f"\nSummary: {len(parse_failures)} parse failures, {len(short_item_exact)} short_item exact, {len(short_item_heuristic)} short_item heuristic")


if __name__ == "__main__":
    main()
