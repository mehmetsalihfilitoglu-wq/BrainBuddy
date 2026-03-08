#!/usr/bin/env python3
"""Scan MAT LGS JSON files for short_item candidates."""

import json
import os

# Problem keywords (case-insensitive)
PROBLEM_KEYWORDS = [
    "problemi", "problem", "oran", "yüzde", "grafik", "tablo",
    "şekilde", "aşağıdaki", "metne göre", "parçaya göre"
]

MAT_DIR = "app/src/main/assets/lgs_import/mat"
LGS_MAT = "app/src/main/assets/lgs_import/lgs_mat.json"

def get_stem(q):
    """Extract stem from question (stem, question, or questionText)."""
    return (q.get("stem") or q.get("question") or q.get("questionText") or "").strip()

def get_question_type(q):
    """Extract questionType."""
    return (q.get("questionType") or "").strip()

def has_problem_keyword(stem):
    """Check if stem contains any problem keyword (case-insensitive)."""
    s = stem.lower()
    for kw in PROBLEM_KEYWORDS:
        if kw in s:
            return True
    return False

def is_short_item(stem, question_type):
    """A question is short_item if:
    1. questionType is exactly 'short_item', OR
    2. stem length < 120 AND no problem keywords, OR
    3. stem length < 80 (trivially short)
    """
    stem_len = len(stem)
    if question_type == "short_item":
        return True
    if stem_len < 80:
        return True
    if stem_len < 120 and not has_problem_keyword(stem):
        return True
    return False

def has_standard_schema(q):
    """Check if question has standard LGS schema: stem and options."""
    stem = get_stem(q)
    options = q.get("options")
    has_answer = "answerIndex" in q or "answer" in q
    return bool(stem) and options is not None and len(options) > 0 and has_answer

def scan_file(filepath):
    """Scan a single JSON file. Returns list of short_item candidates."""
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        return [], f"Error loading: {e}"

    questions = data.get("questions") or []
    if not questions:
        return [], None

    candidates = []
    for i, q in enumerate(questions):
        if not has_standard_schema(q):
            continue
        stem = get_stem(q)
        question_type = get_question_type(q)
        if is_short_item(stem, question_type):
            candidates.append({
                "file": filepath,
                "index": i,
                "stem_preview": stem[:100] + ("..." if len(stem) > 100 else ""),
                "stem_length": len(stem),
                "question_type": question_type,
            })
    return candidates, None

def main():
    base = os.path.dirname(os.path.abspath(__file__))
    all_candidates = []
    files_scanned = 0

    # Scan mat/ directory
    mat_path = os.path.join(base, MAT_DIR)
    for fn in sorted(os.listdir(mat_path)):
        if fn.endswith(".json"):
            filepath = os.path.join(mat_path, fn)
            rel_path = os.path.join(MAT_DIR, fn)
            cands, err = scan_file(filepath)
            if err:
                print(f"SKIP {rel_path}: {err}")
            else:
                files_scanned += 1
                all_candidates.extend(cands)

    # Scan lgs_mat.json
    lgs_path = os.path.join(base, LGS_MAT)
    if os.path.exists(lgs_path):
        cands, err = scan_file(lgs_path)
        if err:
            print(f"SKIP {LGS_MAT}: {err}")
        else:
            files_scanned += 1
            all_candidates.extend(cands)

    # Report
    print(f"Scanned {files_scanned} files. Found {len(all_candidates)} short_item candidate(s):\n")
    for c in all_candidates:
        print(f"  File: {c['file']}")
        print(f"  Index: {c['index']}")
        print(f"  Stem (first 100): {c['stem_preview']}")
        print(f"  Stem length: {c['stem_length']}")
        print(f"  questionType: {c['question_type']}")
        print()

if __name__ == "__main__":
    main()
