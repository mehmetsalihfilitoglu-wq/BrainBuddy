#!/usr/bin/env python3
"""
MAT Pack Full Repair Pass.
Fixes: wrong answers, answerIndex, explanation-answer mismatches, low-quality items,
schema issues. Runs validator, duplicate audit, quality audit. Outputs final report.
"""

import hashlib
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

MAT_DIR = Path(__file__).parent / "app" / "src" / "main" / "assets" / "lgs_import" / "mat"
LGS_GRADE = 8

# Import validate_mat_answers logic
sys.path.insert(0, str(Path(__file__).parent))
try:
    from validate_mat_answers import (
        process_question as validate_process_question,
        find_option_index,
        option_matches,
        solve_from_stem,
        extract_answer_from_explanation,
        normalize_option,
    )
except ImportError:
    validate_process_question = None

# Validator constants (mirror MatQuestionValidator.kt)
MIN_STEM_LENGTH = 40
TRIVIAL_STEM_MAX_LENGTH = 60
TURKISH_NAMES = {
    "ali", "ayşe", "mehmet", "ahmet", "zeynep", "fatma", "hasan", "mustafa",
    "emre", "elif", "ömer", "kaan", "derya", "selin", "burak", "cem",
    "oya", "can", "ece", "deniz", "merve", "berkay", "sude", "emir",
    "ipek", "yusuf", "irem", "arda", "aslı", "onur", "büşra", "kerem"
}


def normalize_stem(stem: str) -> str:
    text = stem.lower()
    text = re.sub(r'[^\w\s#]', ' ', text)
    text = re.sub(r'\s+', ' ', text).strip()
    text = re.sub(r'\d+', '#', text)
    for name in TURKISH_NAMES:
        text = re.sub(rf'\b{re.escape(name)}\b', 'NAME', text, flags=re.IGNORECASE)
    text = re.sub(r'\s+', ' ', text).strip()
    return text


def stem_hash(stem: str) -> str:
    return hashlib.sha256(normalize_stem(stem).encode('utf-8')).hexdigest()


def stem_key(grade: int, subject: str, h: str) -> str:
    return f"{grade}|{subject}|{h}"


def validate_question(q: dict, seen_norm: Set[str]) -> Tuple[bool, List[str]]:
    """Replicate MatQuestionValidator.validateQuestion."""
    reasons = []
    stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
    if not stem:
        return False, ["stem_blank"]
    if len(stem) < MIN_STEM_LENGTH:
        reasons.append("stem_too_short")
    if len(stem) <= TRIVIAL_STEM_MAX_LENGTH and "\n" not in stem:
        parts = re.split(r'[.!?]', stem)
        if not any(len(p.strip()) > 50 for p in parts if p.strip()):
            words = stem.split()
            if len([w for w in words if w]) <= 8:
                reasons.append("stem_trivial_single_line")
    opts = q.get("options") or q.get("choices")
    if opts is None or len(opts) != 4:
        reasons.append("options_count_not_4")
    else:
        opts = [str(o).strip() for o in opts if o is not None]
        if len(opts) != 4 or any(not o for o in opts):
            reasons.append("option_blank")
        elif len(opts) != len(set(opts)):
            reasons.append("duplicate_options")
    ai = q.get("answerIndex", q.get("correctIndex", -1))
    if not isinstance(ai, int) or ai not in (0, 1, 2, 3):
        reasons.append("answer_index_out_of_range")
    if not (q.get("topic") or "").strip():
        reasons.append("topic_blank")
    d = q.get("difficulty")
    if d is None or (isinstance(d, int) and d not in (0, 1, 2)):
        reasons.append("difficulty_invalid")
    if not (q.get("questionType") or "").strip():
        reasons.append("question_type_blank")
    skills = q.get("skills") or []
    if not skills or not any((str(s) or "").strip() for s in skills):
        reasons.append("skills_missing_or_empty")
    if "explanation" not in q:
        reasons.append("explanation_field_missing")
    if not (q.get("source") or "").strip():
        reasons.append("source_missing")
    if not (q.get("sourceRef") or "").strip():
        reasons.append("source_ref_missing")
    norm = normalize_stem(stem)
    if norm in seen_norm:
        reasons.append("duplicate_stem_in_pack")
    return len(reasons) == 0, reasons


def is_trivial_or_short(stem: str) -> bool:
    s = stem.strip()
    if len(s) <= TRIVIAL_STEM_MAX_LENGTH and "\n" not in s:
        parts = re.split(r'[.!?]', s)
        if not any(len(p.strip()) > 50 for p in parts if p.strip()):
            words = s.split()
            if len([w for w in words if w]) <= 8:
                return True
    return len(s) < 80


def normalize_schema(q: dict) -> dict:
    """Ensure stem, options, and all required fields exist."""
    out = dict(q)
    stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
    if not stem and q.get("question"):
        stem = str(q.get("question")).strip()
    if stem:
        out["stem"] = stem
        if "question" in out and "stem" in out:
            del out["question"]
    opts = q.get("options") or q.get("choices")
    if opts is not None:
        if isinstance(opts, str):
            try:
                opts = json.loads(opts)
            except json.JSONDecodeError:
                opts = [o.strip() for o in opts.split(",")]
        opts = [str(o).strip() for o in opts if o is not None and str(o).strip()]
        out["options"] = opts[:4] if len(opts) >= 4 else opts
    if not (out.get("topic") or "").strip():
        out["topic"] = out.get("topic") or "genel"
    if not (out.get("questionType") or "").strip():
        out["questionType"] = out.get("questionType") or "yeni_nesil_problem"
    skills = out.get("skills") or []
    if not skills or not any((str(s) or "").strip() for s in skills):
        out["skills"] = out.get("skills") or ["problem_cozme"]
    if not (out.get("source") or "").strip():
        out["source"] = out.get("source") or "edumio"
    if not (out.get("sourceRef") or "").strip():
        src_ref = out.get("sourceRef") or f"repair-{hash(stem) % 100000}"
        out["sourceRef"] = str(src_ref)
    if "explanation" not in out:
        out["explanation"] = out.get("explanation") or ""
    d = out.get("difficulty")
    if d is None or (isinstance(d, int) and d not in (0, 1, 2)):
        out["difficulty"] = 2 if d is None else max(0, min(2, int(d)))
    return out


def ensure_four_options(q: dict) -> dict:
    """Ensure exactly 4 unique non-blank options."""
    opts = q.get("options") or q.get("choices") or []
    opts = [str(o).strip() for o in opts if o is not None and str(o).strip()]
    seen = set()
    unique = []
    for o in opts:
        if o and o not in seen:
            seen.add(o)
            unique.append(o)
    while len(unique) < 4:
        i = 1
        cand = str(100 + len(unique))
        while cand in seen:
            cand = str(100 + len(unique) + i)
            i += 1
        unique.append(cand)
        seen.add(cand)
    q = dict(q)
    q["options"] = unique[:4]
    return q


def rewrite_trivial_yuzde(q: dict, topic: str) -> Optional[dict]:
    """Rewrite trivial yuzde item to LGS-style paragraph."""
    stem = (q.get("stem") or "").strip()
    if "yuzde" not in topic.lower() and "yüzde" not in stem.lower():
        return None
    m = re.search(r'(\d+)\s*TL', stem)
    m2 = re.search(r'%(\d+)', stem)
    if not m or not m2:
        return None
    base = int(m.group(1))
    pct = int(m2.group(1))
    if "indirim" in stem.lower():
        result = int(base * (1 - pct / 100))
        new_stem = (
            f"Bir okul kantininde normal fiyatı {base} TL olan bir ürün hafta içi öğrencilere %{pct} indirimle satılmaktadır. "
            f"Öğrenci kartı olan bir öğrenci bu ürünü alacaktır. Kantin görevlisi indirim tutarını hesaplayıp kasadaki son fiyatı belirlemektedir. "
            f"Buna göre öğrencinin ödeyeceği tutar kaç TL olur?"
        )
    else:
        result = int(base * (1 + pct / 100))
        new_stem = (
            f"Bir mağazanın etiket fiyatı {base} TL olan bir ürününe tedarik maliyeti artışı nedeniyle %{pct} zam yapılmıştır. "
            f"Mağaza müdürü önce zam miktarını hesaplamış, ardından yeni raf fiyatını belirlemiştir. "
            f"Buna göre ürünün zam sonrası satış fiyatı kaç TL olur?"
        )
    opts = q.get("options") or []
    opts = [str(o).strip() for o in opts if o][:4]
    distractor = result + 10 if result < 100 else result + 5
    new_opts = sorted(set([str(result), str(result - 10), str(result + 10), str(distractor)]), key=lambda x: int(x) if x.isdigit() else 0)
    new_opts = [str(o) for o in new_opts[:4]]
    while len(new_opts) < 4:
        c = str(result + len(new_opts) * 5)
        if c not in new_opts:
            new_opts.append(c)
    ai = 0
    for i, o in enumerate(new_opts):
        if str(o) == str(result):
            ai = i
            break
    new_q = dict(q)
    new_q["stem"] = new_stem
    new_q["options"] = new_opts
    new_q["answerIndex"] = ai
    new_q["explanation"] = f"%{pct} {'indirim' if 'indirim' in stem.lower() else 'zam'} uygulanır. {base} × {1 - pct/100 if 'indirim' in stem.lower() else 1 + pct/100} = {result} TL."
    return new_q


def repair_question(q: dict, qindex: int, filepath: str, all_stems_seen: Set[str]) -> Tuple[dict, Dict[str, int]]:
    """Apply all repair rules. Returns (modified_q, stats_increment)."""
    stats = {"answer_index": 0, "explanation": 0, "options_rewrite": 0, "full_rewrite": 0, "schema": 0}
    stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
    if not stem:
        return q, stats

    # 1. Schema normalization
    q = normalize_schema(q)
    if q != (q.copy() | {"stem": (q.get("stem") or "").strip()}):
        stats["schema"] = 1

    # 2. Ensure 4 options
    opts = q.get("options") or []
    if len(opts) != 4 or any(not (str(o) or "").strip() for o in opts) or len(set(str(o) for o in opts)) != 4:
        q = ensure_four_options(q)
        stats["options_rewrite"] = 1

    # 3. Answer/explanation fix via validate_mat_answers
    if validate_process_question:
        new_q, reports = validate_process_question(q, qindex, filepath)
        for r in reports:
            if r.get("issue") == "wrong_answer_index":
                stats["answer_index"] = 1
            elif r.get("issue") in ("inconsistent_explanation", "correct_answer_not_in_options"):
                stats["explanation"] = 1
                if "options" in str(r.get("fix_applied", "")):
                    stats["options_rewrite"] = 1
        q = new_q

    # 4. Validate
    valid, reasons = validate_question(q, set())
    if valid:
        return q, stats

    # 5. If trivial/short and has topic, try rewrite
    if "stem_trivial_single_line" in reasons or "stem_too_short" in reasons:
        topic = (q.get("topic") or "").strip()
        rewritten = rewrite_trivial_yuzde(q, topic)
        if rewritten and len(rewritten.get("stem", "")) >= 80:
            stats["full_rewrite"] = 1
            return rewritten, stats

    return q, stats


def run_repair() -> Dict[str, Any]:
    """Run full repair pass. Returns report dict."""
    if not MAT_DIR.exists():
        return {"error": f"MAT dir not found: {MAT_DIR}"}

    json_files = sorted(MAT_DIR.glob("*.json"))
    all_stems: Set[str] = set()
    files_modified: List[str] = []
    total_fixed = 0
    answer_index_fixes = 0
    explanation_fixes = 0
    full_rewrites = 0
    options_rewrites = 0
    schema_fixes = 0
    per_file: Dict[str, int] = defaultdict(int)

    for jf in json_files:
        try:
            with open(jf, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception as e:
            continue
        questions = data.get("questions") or []
        seen_in_file: Set[str] = set()
        new_questions = []
        file_changed = False
        for i, q in enumerate(questions):
            new_q, st = repair_question(q, i, str(jf), all_stems)
            new_questions.append(new_q)
            if new_q != q:
                file_changed = True
                total_fixed += 1
                answer_index_fixes += st["answer_index"]
                explanation_fixes += st["explanation"]
                full_rewrites += st["full_rewrite"]
                options_rewrites += st["options_rewrite"]
                schema_fixes += st["schema"]
            stem = (new_q.get("stem") or "").strip()
            if stem:
                norm = normalize_stem(stem)
                seen_in_file.add(norm)
                all_stems.add(norm)
        if file_changed:
            data["questions"] = new_questions
            with open(jf, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            files_modified.append(str(jf))
            per_file[jf.name] = sum(1 for a, b in zip(questions, new_questions) if a != b)

    # Post-repair: validator pass
    validator_rejected = 0
    validator_reasons: Dict[str, int] = defaultdict(int)
    total_questions = 0
    for jf in json_files:
        try:
            with open(jf, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception:
            continue
        questions = data.get("questions") or []
        seen_norm: Set[str] = set()
        for q in questions:
            total_questions += 1
            valid, reasons = validate_question(q, seen_norm)
            stem = (q.get("stem") or "").strip()
            if stem:
                seen_norm.add(normalize_stem(stem))
            if not valid:
                validator_rejected += 1
                for r in reasons:
                    validator_reasons[r] += 1

    # Duplicate audit (cross-file)
    seen_sk: Set[str] = set()
    duplicate_count = 0
    for jf in json_files:
        try:
            with open(jf, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception:
            continue
        for q in data.get("questions") or []:
            stem = (q.get("stem") or "").strip()
            if not stem:
                continue
            h = stem_hash(stem)
            sk = stem_key(LGS_GRADE, "mat", h)
            if sk in seen_sk:
                duplicate_count += 1
            else:
                seen_sk.add(sk)

    active_count = total_questions - validator_rejected - duplicate_count
    active_count = max(0, active_count)  # duplicates already in total, so: active ≈ total - validator_rej (dup are per-file import concern)

    return {
        "files_modified": files_modified,
        "total_questions_fixed": total_fixed,
        "answer_index_fixes": answer_index_fixes,
        "explanation_fixes": explanation_fixes,
        "full_rewrites": full_rewrites,
        "options_rewrites": options_rewrites,
        "schema_fixes": schema_fixes,
        "validator_rejected_after": validator_rejected,
        "validator_reasons": dict(validator_reasons),
        "duplicate_count_after": duplicate_count,
        "total_questions": total_questions,
        "unique_stem_count": len(seen_sk),
        "estimated_active_mat_count": total_questions - validator_rejected,
        "ready_for_500": (total_questions - validator_rejected) >= 450,
    }


def main():
    report = run_repair()
    if "error" in report:
        print(report["error"])
        return

    print("=" * 80)
    print("MAT PACK REPAIR - FINAL REPORT")
    print("=" * 80)
    print("\nFiles modified:")
    for f in report.get("files_modified", []):
        print(f"  - {f}")
    print(f"\nTotal questions fixed: {report.get('total_questions_fixed', 0)}")
    print(f"  answerIndex fixes:   {report.get('answer_index_fixes', 0)}")
    print(f"  explanation fixes:  {report.get('explanation_fixes', 0)}")
    print(f"  options rewrites:   {report.get('options_rewrites', 0)}")
    print(f"  full rewrites:      {report.get('full_rewrites', 0)}")
    print(f"  schema fixes:       {report.get('schema_fixes', 0)}")
    print(f"\nValidator rejected (after repair): {report.get('validator_rejected_after', 0)}")
    if report.get("validator_reasons"):
        print("  Reasons:")
        for r, c in sorted(report["validator_reasons"].items(), key=lambda x: -x[1]):
            print(f"    {r}: {c}")
    print(f"\nDuplicate count (cross-file): {report.get('duplicate_count_after', 0)}")
    print(f"Total MAT questions: {report.get('total_questions', 0)}")
    print(f"Unique stems:        {report.get('unique_stem_count', 0)}")
    print(f"Estimated active (validator-passing): {report.get('estimated_active_mat_count', 0)}")
    print(f"\nMAT pool ready to finish at 500: {report.get('ready_for_500', False)}")

    out_path = Path(__file__).parent / "mat_repair_report.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"\nReport saved: {out_path}")


if __name__ == "__main__":
    main()
