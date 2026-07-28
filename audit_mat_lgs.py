#!/usr/bin/env python3
"""
MAT LGS Asset Audit - Full Recovery Report Generator.
Replicates QuestionPackImporter, MatQuestionValidator, QuestionStemHash, LgsQualityRules logic.
DO NOT MODIFY ANY FILES - report only.
"""

import hashlib
import json
import os
import re
from collections import defaultdict
from pathlib import Path
from typing import Any, Optional, Tuple

MAT_DIR = Path(__file__).parent / "app/src/main/assets/lgs_import/mat"
LGS_GRADE = 8

# --- QuestionStemHash (replicate Kotlin) ---
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
    norm = normalize_stem(stem)
    return hashlib.sha256(norm.encode('utf-8')).hexdigest()

def stem_key(grade: int, subject: str, h: str) -> str:
    return f"{grade}|{subject}|{h}"

# --- MatQuestionValidator (replicate Kotlin) ---
MIN_STEM_LENGTH = 40
TRIVIAL_STEM_MAX_LENGTH = 60

def validate_question(q: dict, seen_normalized_stems: set) -> Tuple[bool, list]:
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
    if d is None:
        reasons.append("difficulty_invalid")
    elif isinstance(d, str):
        if d.upper() not in ("EASY", "MEDIUM", "MED", "HARD", "VERY_HARD"):
            reasons.append("difficulty_invalid")
    elif isinstance(d, int) and d not in (0, 1, 2):
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
    if norm in seen_normalized_stems:
        reasons.append("duplicate_stem_in_pack")
    return len(reasons) == 0, reasons

# --- parseLgsQuestion (basic parse check) ---
def parse_ok(q: dict) -> bool:
    stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
    if not stem:
        return False
    opts = q.get("options") or q.get("choices")
    if opts is None:
        return False
    raw = [str(o).strip() for o in opts if o is not None and str(o).strip()]
    return len(raw) >= 2

# --- LgsQualityRules (simplified) ---
def quality_is_active(stem: str, options: list, subject: str) -> bool:
    s = stem.strip()
    if len(s) < 80:  # Config.minStemLength
        return False
    lower = s.lower()
    # One-step arithmetic
    if re.search(r'\d+\s*[+\-×xX*/:÷]\s*\d+', lower) and len(lower) <= 100:
        if "problemi" not in lower and "grafik" not in lower and "tablo" not in lower:
            if "oran" not in lower and "yüzde" not in lower and "problem" not in lower:
                return False
    # Direct definition
    if len(s) <= 120 and any(x in lower for x in ["nedir?", "ne demektir?", "tanımı"]):
        if "grafik" not in lower and "tablo" not in lower:
            return False
    return True

def main():
    if not MAT_DIR.exists():
        print(f"ERROR: MAT dir not found: {MAT_DIR}")
        return

    json_files = sorted(MAT_DIR.glob("*.json"))
    gold_nums = set()
    for f in json_files:
        m = re.match(r"lgs_mat_gold_(\d{3})\.json", f.name)
        if m:
            gold_nums.add(int(m.group(1)))

    missing_gold = [i for i in range(1, 51) if i not in gold_nums]
    filenames = [f.name for f in json_files]
    dup_filenames = [f for f in filenames if filenames.count(f) > 1]
    unique_dup = sorted(set(dup_filenames))

    # Per-file stats
    file_stats = {}
    all_stems_seen = set()
    all_stem_keys_seen = set()
    total_inserted = 0
    total_active = 0

    for fn in sorted(f.name for f in json_files):
        fp = MAT_DIR / fn
        try:
            with open(fp, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception as e:
            file_stats[fn] = {"error": str(e), "total": 0, "accepted": 0, "rejected": 0, "dup_collision": 0, "parse_fail": 0, "inserted": 0, "active": 0}
            continue

        questions = data.get("questions") or []
        total = len(questions)
        seen_in_pack = set()
        accepted = 0
        rejected = 0
        parse_fail = 0
        dup_collision = 0
        inserted_this = 0
        active_this = 0

        for i, q in enumerate(questions):
            if not parse_ok(q):
                parse_fail += 1
                continue
            is_valid, reasons = validate_question(q, seen_in_pack)
            stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
            if stem:
                seen_in_pack.add(normalize_stem(stem))
            if is_valid:
                accepted += 1
                h = stem_hash(stem)
                sk = stem_key(LGS_GRADE, "mat", h)
                if sk in all_stem_keys_seen:
                    dup_collision += 1
                else:
                    all_stem_keys_seen.add(sk)
                    inserted_this += 1
                    opts = q.get("options") or q.get("choices") or []
                    opts = [str(o).strip() for o in opts if o][:4]
                    if quality_is_active(stem, opts, "mat"):
                        active_this += 1
            else:
                rejected += 1

        total_inserted += inserted_this
        total_active += active_this
        file_stats[fn] = {
            "total": total,
            "accepted": accepted,
            "rejected": rejected,
            "parse_fail": parse_fail,
            "dup_collision": dup_collision,
            "inserted": inserted_this,
            "active": active_this
        }

    # Build report
    report = []
    report.append("=" * 80)
    report.append("MAT LGS ASSET AUDIT - FULL RECOVERY REPORT")
    report.append("=" * 80)

    report.append("\n## 1. ALL MAT FILES (sorted)")
    report.append("-" * 60)
    for i, fn in enumerate(sorted(file_stats.keys()), 1):
        report.append(f"  {i:3}. {fn}")

    report.append("\n## 2. MISSING SEQUENCE NUMBERS (lgs_mat_gold_001 .. lgs_mat_gold_050)")
    report.append("-" * 60)
    if missing_gold:
        report.append(f"  Missing: {', '.join(f'{n:03d}' for n in missing_gold)}")
        report.append(f"  Count: {len(missing_gold)}")
    else:
        report.append("  None missing - all 50 gold files present.")

    report.append("\n## 3. OVERWRITTEN/DUPLICATED FILENAMES")
    report.append("-" * 60)
    if unique_dup:
        report.append(f"  Duplicated filenames: {unique_dup}")
    else:
        report.append("  No duplicated filenames - each filename appears once.")

    report.append("\n## 4. PER-FILE COUNTS (total, validator accepted, rejected, dup-stem, inserted, active)")
    report.append("-" * 80)
    report.append(f"  {'File':<45} {'Tot':>4} {'Acc':>4} {'Rej':>4} {'Dup':>4} {'Ins':>4} {'Act':>4}")
    report.append("-" * 80)
    for fn in sorted(file_stats.keys()):
        s = file_stats[fn]
        if "error" in s:
            report.append(f"  {fn:<45} ERROR: {s['error']}")
        else:
            report.append(f"  {fn:<45} {s['total']:>4} {s['accepted']:>4} {s['rejected']:>4} {s['dup_collision']:>4} {s['inserted']:>4} {s['active']:>4}")

    report.append("\n## 5. FILES CONTRIBUTING ACTIVE QUESTIONS (>0 active)")
    report.append("-" * 60)
    contrib = [(fn, file_stats[fn]["active"]) for fn in sorted(file_stats.keys()) if file_stats[fn].get("active", 0) > 0]
    for fn, act in contrib:
        report.append(f"  {fn}: {act} active")
    report.append(f"  Total: {len(contrib)} files, {sum(a for _, a in contrib)} active questions")

    report.append("\n## 6. FILES CONTRIBUTING ZERO ACTIVE QUESTIONS")
    report.append("-" * 60)
    zero_active = [fn for fn in sorted(file_stats.keys()) if file_stats[fn].get("active", 0) == 0 and "error" not in file_stats[fn]]
    for fn in zero_active:
        s = file_stats[fn]
        reasons = []
        if s.get("parse_fail", 0) == s.get("total", 0):
            reasons.append("all parse fail")
        elif s.get("rejected", 0) == s.get("accepted", 0) + s.get("rejected", 0) and s.get("accepted", 0) == 0:
            reasons.append("all validator rejected")
        elif s.get("inserted", 0) == 0 and s.get("accepted", 0) > 0:
            reasons.append("all duplicate collision")
        elif s.get("inserted", 0) > 0 and s.get("active", 0) == 0:
            reasons.append("inserted but all deactivated (low quality)")
        else:
            reasons.append("no questions / empty")
        report.append(f"  {fn}: {reasons}")

    report.append("\n## 7. WHY ACTIVE MAT COUNT IS ~346 INSTEAD OF ~500")
    report.append("-" * 60)
    grand_total_q = sum(s.get("total", 0) for s in file_stats.values() if "error" not in s)
    grand_parse = sum(s.get("parse_fail", 0) for s in file_stats.values() if "error" not in s)
    grand_rej = sum(s.get("rejected", 0) for s in file_stats.values() if "error" not in s)
    grand_dup = sum(s.get("dup_collision", 0) for s in file_stats.values() if "error" not in s)
    grand_ins = sum(s.get("inserted", 0) for s in file_stats.values() if "error" not in s)
    grand_act = sum(s.get("active", 0) for s in file_stats.values() if "error" not in s)
    deactivated = grand_ins - grand_act
    report.append(f"  Total questions in files:     {grand_total_q}")
    report.append(f"  Minus parse failures:         -{grand_parse}  => {grand_total_q - grand_parse}")
    report.append(f"  Minus validator rejected:     -{grand_rej}  => {grand_total_q - grand_parse - grand_rej}")
    report.append(f"  Minus duplicate-stem collision: -{grand_dup}  => {grand_ins} inserted")
    report.append(f"  Minus deactivated (low quality): -{deactivated}  => {grand_act} ACTIVE")
    report.append(f"  Expected ~500: 50 gold × ~10 + 2 pack files; many lost to validation, dup, quality.")

    report.append("\n## 8. RECOVERY PLAN")
    report.append("-" * 60)
    to_delete = []
    to_regenerate = []
    to_keep = []
    for fn in sorted(file_stats.keys()):
        s = file_stats[fn]
        if "error" in s:
            to_regenerate.append(fn)
            continue
        if fn.startswith("lgs_mat_pack_"):
            if s.get("parse_fail", 0) == s.get("total", 0) or s.get("active", 0) == 0:
                to_regenerate.append(fn)
            else:
                to_keep.append(fn)
            continue
        m = re.match(r"lgs_mat_gold_(\d{3})\.json", fn)
        if m:
            num = int(m.group(1))
            if s.get("active", 0) > 0 and s.get("dup_collision", 0) == 0 and s.get("rejected", 0) == 0:
                to_keep.append(fn)
            elif s.get("parse_fail", 0) == s.get("total", 0) or (s.get("total", 0) > 0 and s.get("active", 0) == 0 and s.get("rejected", 0) == s.get("total", 0)):
                to_regenerate.append(fn)
            elif s.get("dup_collision", 0) == s.get("accepted", 0) and s.get("accepted", 0) > 0:
                to_regenerate.append(fn)
            else:
                to_keep.append(fn)

    report.append("  DELETE (do not use; fully redundant or broken):")
    for fn in to_delete or ["(none)"]:
        report.append(f"    - {fn}")

    report.append("  REGENERATE (fix schema/validation or recreate content):")
    for fn in to_regenerate:
        report.append(f"    - {fn}")

    report.append("  KEEP (contributing active questions, no action needed):")
    for fn in to_keep:
        report.append(f"    - {fn}")

    report.append("\n" + "=" * 80)
    out = "\n".join(report)
    print(out)
    out_path = Path(__file__).parent / "mat_recovery_report.txt"
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(out)
    print(f"\nReport saved to: {out_path}")

if __name__ == "__main__":
    main()
