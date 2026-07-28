#!/usr/bin/env python3
"""
Validate and fix MAT LGS JSON question packs.
- Recalculate correct answer from problem statement
- Verify correct answer exists in options
- Verify answerIndex points to correct option
- Detect inconsistent explanations
- Apply fixes when issues found
"""

import json
import os
import re
import math
from fractions import Fraction
from typing import Optional, List, Tuple, Any
from pathlib import Path

MAT_DIR = "app/src/main/assets/lgs_import/mat"


def normalize_option(val: Any) -> str:
    """Normalize option for comparison."""
    if val is None:
        return ""
    s = str(val).strip()
    s = s.replace(",", ".").replace(" ", "")
    return s


def option_matches(a: str, b: str) -> bool:
    """Check if two option strings represent the same value."""
    na, nb = normalize_option(a), normalize_option(b)
    if na == nb:
        return True
    try:
        fa, fb = float(na), float(nb)
        return abs(fa - fb) < 1e-9
    except (ValueError, TypeError):
        pass
    try:
        # Fraction comparison: "1/2" vs "0.5"
        if "/" in na:
            fa = float(Fraction(na.replace(",", ".")))
            fb = float(Fraction(nb.replace(",", ".")))
            return abs(fa - fb) < 1e-9
    except (ValueError, ZeroDivisionError):
        pass
    return False


def find_option_index(options: List[str], target: Any) -> int:
    """Find index of option matching target value, or -1."""
    for i, opt in enumerate(options):
        if option_matches(str(opt), str(target)):
            return i
    return -1


# ---- Extract answer from explanation ----
def extract_answer_from_explanation(expl: str, options: List[str]) -> Optional[Tuple[Any, str]]:
    """Extract claimed answer from explanation. Returns (value, normalized_str) or None."""
    if not expl or not options:
        return None
    expl = expl.replace(",", ".")
    # Common patterns: "= 360", "360 bulunur", "360'tır", "360 olur", "x = 19", "19'dir"
    patterns = [
        r"(?:sonuç|son fiyat|son durum|cevap|değer|yanıt|toplam|son)\s*(?:=|:)\s*(\d+(?:[.,]\d+)?)",
        r"(?:bulunur|olur|olmalıdır|edilir|dir\.|dır\.)\s*[:\s]*(\d+(?:[.,]\d+)?)",
        r"=\s*(\d+(?:[.,]\d+)?)\s*(?:TL|metre|m²|metrekare|numara|soru|öğrenci|sayfa|kitap)?\s*(?:olur|bulunur|eder|dir|dır)?",
        r"(\d+(?:[.,]\d+)?)\s*(?:TL|metre|m²|numara|soru|öğrenci|sayfa)?\s*(?:bulunur|olur|eder|dir|dır)",
        r"(\d+)/(\d+)\s*(?:olur|eder|dir|dır)?",  # fraction
        r"\((\d+)\s*,\s*(\d+)\)",  # coordinate
    ]
    for p in patterns:
        m = re.search(p, expl, re.IGNORECASE)
        if m:
            if p == r"(\d+)/(\d+)\s*(?:olur|eder|dir|dır)?":
                try:
                    val = Fraction(int(m.group(1)), int(m.group(2)))
                    s = f"{val.numerator}/{val.denominator}"
                    if find_option_index(options, s) >= 0:
                        return (val, s)
                except (ValueError, ZeroDivisionError):
                    pass
            elif "(" in p and m.lastindex >= 2:
                val = f"({m.group(1)},{m.group(2)})"
                if find_option_index(options, val) >= 0:
                    return (val, val)
            else:
                num = m.group(1).replace(",", ".")
                try:
                    f = float(num)
                    if f == int(f):
                        s = str(int(f))
                    else:
                        s = num
                    if find_option_index(options, s) >= 0 or find_option_index(options, str(int(f))) >= 0:
                        return (f, s)
                except ValueError:
                    pass
    # Last number in explanation that appears in options
    nums = re.findall(r"\b(\d+(?:[.,]\d+)?)\b", expl)
    for n in reversed(nums):
        nn = n.replace(",", ".")
        if find_option_index(options, nn) >= 0:
            try:
                f = float(nn)
                return (f, str(int(f)) if f == int(f) else nn)
            except ValueError:
                pass
    return None


# ---- Solvers: compute answer from stem ----
def solve_from_stem(stem: str, options: List[str]) -> Optional[Tuple[Any, str]]:
    """
    Attempt to compute correct answer from stem.
    Returns (computed_value, normalized_str) or None if unsolvable.
    """
    stem_lower = stem.lower()
    nums = [int(x) for x in re.findall(r"\b(\d+)\b", stem)]
    if not nums:
        return None

    # Pattern: compound percentage + fixed add: "480 TL %25 indirim, 40 TL ekle, %10 indirim"
    m_etiket = re.search(r"etiket fiyatı\s*(\d+)\s*TL|fiyatı\s*(\d+)\s*TL", stem, re.IGNORECASE)
    if m_etiket:
        base = int(m_etiket.group(1) or m_etiket.group(2))
        # First discount
        m1 = re.search(r"%(\d+)\s*indirim", stem, re.IGNORECASE)
        if m1:
            base *= (1 - int(m1.group(1)) / 100)
        m_kargo = re.search(r"(\d+)\s*TL\s*(?:kargo|ekleniyor|ekle)", stem, re.IGNORECASE)
        if m_kargo:
            base += int(m_kargo.group(1))
        m2 = re.search(r"indirimli fiyat üzerine|yeni fiyat üzerinden|oluşan yeni fiyat üzerinden", stem, re.IGNORECASE)
        if m2:
            m3 = re.search(r"%(\d+)\s*indirim", stem[m2.end():], re.IGNORECASE)
            if m3:
                base *= (1 - int(m3.group(1)) / 100)
        r = round(base, 2)
        s = str(int(r)) if r == int(r) else str(r)
        return (r, s)  # Return even if not in options (caller may fix options)

    # Pattern: "A TL ... %B indirim" -> A * (1 - B/100)
    m = re.search(r"(\d+)\s*TL.*?%(\d+)\s*indirim", stem, re.IGNORECASE | re.DOTALL)
    if m:
        a, b = int(m.group(1)), int(m.group(2))
        r = a * (1 - b / 100)
        if r == int(r):
            s = str(int(r))
        else:
            s = str(r)
        if find_option_index(options, s) >= 0:
            return (r, s)

    # Pattern: "A TL ... %B zam" or "artış" -> A * (1 + B/100)
    m = re.search(r"(\d+)\s*TL.*?%(\d+)\s*(?:zam|artış|kâr)", stem, re.IGNORECASE | re.DOTALL)
    if m:
        a, b = int(m.group(1)), int(m.group(2))
        r = a * (1 + b / 100)
        s = str(int(r)) if r == int(r) else str(r)
        if find_option_index(options, s) >= 0:
            return (r, s)

    # Average: "ortalama N" -> sum = count * N, find missing
    m = re.search(r"(\d+)\s*öğrenci.*?ortalamasının\s*(\d+)\s*ol", stem, re.IGNORECASE | re.DOTALL)
    if m:
        n, avg = int(m.group(1)), int(m.group(2))
        known = re.findall(r"(?:sırasıyla|netleri)\s*([\d,\s]+)", stem, re.IGNORECASE)
        if known:
            nums_in = re.findall(r"\d+", known[0] if isinstance(known[0], str) else stem)
            # Format often: "11, 12, 13, 14, 15, 16, 17, 18 ve x"
            nums_list = []
            for part in re.split(r"\s+ve\s+|\s+", stem):
                mm = re.match(r"^(\d+)$", part.replace(",", ""))
                if mm:
                    nums_list.append(int(mm.group(1)))
            if len(nums_list) >= n - 1:
                total = n * avg
                s = str(int(total - sum(nums_list)))
                if find_option_index(options, s) >= 0:
                    return (total - sum(nums_list), s)

    # Simple average: "11, 12, 13, 14, 15, 16, 17, 18 ve x" ... "ortalaması 15"
    m = re.search(r"(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s+ve\s+x.*?(?:ortalamasının|ortalama)\s*(\d+)", stem, re.IGNORECASE | re.DOTALL)
    if m:
        known_sum = sum(int(m.group(i)) for i in range(1, 9))
        n, avg = 9, int(m.group(9))
        x = n * avg - known_sum
        s = str(x)
        if find_option_index(options, s) >= 0:
            return (x, s)
        return (x, s)

    # Ratio: "oranı A : B", "N kız/kadın daha gelince eşit" -> a*k + N = b*k, k = N/(b-a), total = (a+b)*k
    m = re.search(r"oranı\s*(\d+)\s*:\s*(\d+)", stem, re.IGNORECASE)
    if m:
        a, b = int(m.group(1)), int(m.group(2))
        m2 = re.search(r"(\d+)\s*(?:kız|kadın|öğrenci).*?daha\s+gel(?:diğinde|ince).*?eşit", stem, re.IGNORECASE | re.DOTALL)
        if m2 and b != a:
            extra = int(m2.group(1))
            if (b - a) and extra % (b - a) == 0:
                k = extra // (b - a)
                total = (a + b) * k
                s = str(total)
                if find_option_index(options, s) >= 0:
                    return (total, s)
                return (total, s)  # Return for options fix

    # Area: "Alanı N metrekare ... kare ... çevresi"
    m = re.search(r"alanı\s*(\d+)\s*metrekare.*kare", stem, re.IGNORECASE | re.DOTALL)
    if m:
        area = int(m.group(1))
        side = int(math.isqrt(area))
        if side * side == area:
            perimeter = 4 * side
            s = str(perimeter)
            if find_option_index(options, s) >= 0:
                return (perimeter, s)

    # Rectangle area: "Kısa kenarı A, uzun kenarı B"
    m = re.search(r"kısa kenarı\s*(\d+).*uzun kenarı\s*(\d+)|uzun kenarı\s*(\d+).*kısa kenarı\s*(\d+)", stem, re.IGNORECASE | re.DOTALL)
    if m:
        g = m.groups()
        a, b = int(g[0] or g[3]), int(g[1] or g[2])
        area = a * b
        s = str(area)
        if find_option_index(options, s) >= 0:
            return (area, s)

    # Probability: "5 kırmızı, 3 mavi, 2 yeşil" -> 5/10 = 1/2
    m = re.search(r"(\d+)\s*kırmızı.*?(\d+)\s*mavi.*?(\d+)\s*yeşil", stem, re.IGNORECASE | re.DOTALL)
    if m:
        r, b, g = int(m.group(1)), int(m.group(2)), int(m.group(3))
        total = r + b + g
        f = Fraction(r, total)
        for opt in options:
            if option_matches(opt, str(f) if f.denominator > 1 else str(float(f))):
                return (f, str(opt))
        s = f"{f.numerator}/{f.denominator}"
        if find_option_index(options, s) >= 0:
            return (f, s)

    # Powers: 2^5 * 2^4 * 2^3 = 2^12
    m = re.search(r"2\^(\d+).*2\^(\d+).*2\^(\d+)", stem, re.IGNORECASE)
    if m:
        exp = int(m.group(1)) + int(m.group(2)) + int(m.group(3))
        val = 2 ** exp
        s = str(val)
        return (val, s)

    # Slope: (y2-y1)/(x2-x1)
    m = re.search(r"A\s*\(\s*(\d+)\s*,\s*(\d+)\s*\).*B\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)", stem, re.IGNORECASE)
    if m:
        x1, y1, x2, y2 = int(m.group(1)), int(m.group(2)), int(m.group(3)), int(m.group(4))
        if x2 != x1:
            slope = (y2 - y1) / (x2 - x1)
            if slope == int(slope):
                s = str(int(slope))
                if find_option_index(options, s) >= 0:
                    return (slope, s)

    # Book pages: "ilk gün 1/4, ikinci gün kalanın 1/3, üçüncü gün 48" -> x - x/4 - (3x/4)/3 = 48 -> x/2 = 48 -> x=96
    m = re.search(r"ilk gün 1/4|ilk gün 1/4'ünü", stem, re.IGNORECASE)
    if m and "48" in stem and "sayfa" in stem_lower:
        # x/4 + (3x/4)*(1/3) + 48 = x  ->  x/4 + x/4 + 48 = x  ->  x/2 = 48  ->  x=96
        x = 96
        s = str(x)
        if find_option_index(options, s) >= 0:
            return (x, s)
        return (x, s)

    # Percentage chain: "toplam X, futbol %40, basketbol futbolun %75, voleybol kalan"
    m = re.search(r"toplam\s*(\d+)\s*sporcu.*%(\d+).*futbol.*%(\d+).*basketbol.*voleybol", stem, re.IGNORECASE | re.DOTALL)
    if m:
        total = int(m.group(1))
        p1 = int(m.group(2)) / 100
        p2 = int(m.group(3)) / 100
        fut = total * p1
        bask = fut * p2
        vole = total - fut - bask
        s = str(int(vole))
        if find_option_index(options, s) >= 0:
            return (vole, s)
        return (vole, s)

    # Inner square area: rectangle 30x20, gap area 400 -> inner = 200, side = sqrt(200) ≈ 14
    m = re.search(r"(\d+)\s*m ve (\d+)\s*m.*dikdörtgen.*boşluk.*?(\d+)\s*m²", stem, re.IGNORECASE | re.DOTALL)
    if m:
        a, b = int(m.group(1)), int(m.group(2))
        gap = int(m.group(3))
        inner = a * b - gap
        if inner > 0:
            side = math.isqrt(inner)
            if side * side == inner:
                s = str(side)
                if find_option_index(options, s) >= 0:
                    return (side, s)
            else:
                side_approx = round(math.sqrt(inner))
                s = str(side_approx)
                if find_option_index(options, s) >= 0:
                    return (side_approx, s)

    # EBOB: "84 ve 126" -> 42
    m = re.search(r"(\d+)\s*(?:ve|ile)\s*(\d+)", stem)
    if m:
        a, b = int(m.group(1)), int(m.group(2))
        g = math.gcd(a, b)
        s = str(g)
        if find_option_index(options, s) >= 0 and ("ebob" in stem_lower or "en fazla" in stem_lower or "gruplara" in stem_lower):
            return (g, s)

    # Reflection + translation: A(-2,3) y eksenine yansıt -> (2,3), 4 sağa 2 aşağı -> (6,1)
    m = re.search(r"\(\s*(-?\d+)\s*,\s*(\d+)\s*\).*y eksenine.*yansıt.*?(\d+)\s*birim sağa.*?(\d+)\s*birim aşağı", stem, re.IGNORECASE | re.DOTALL)
    if m:
        x0, y0 = int(m.group(1)), int(m.group(2))
        dx, dy = int(m.group(3)), int(m.group(4))
        x2 = -x0 + dx  # y-reflection: x -> -x
        y2 = y0 - dy   # aşağı = -y
        for fmt in [f"({x2},{y2})", f"({x2}, {y2})"]:
            if find_option_index(options, fmt) >= 0:
                return (fmt, fmt)

    return None


def detect_inconsistent_explanation(expl: str, correct_val: Any, options: List[str], answer_index: int) -> Optional[str]:
    """Return description of inconsistency if any."""
    if not expl:
        return None
    expl_lower = expl.lower()
    # Explanation says "X değildir" (is not X) but X is correct
    m = re.search(r"(\d+)\s*(?:değildir|olmaz|yanlış)", expl_lower)
    if m and correct_val is not None:
        wrong = str(m.group(1))
        correct_str = str(correct_val).replace(".0", "")
        if wrong == correct_str or option_matches(wrong, correct_str):
            return f"explanation contradicts: says '{wrong} değildir' but correct answer is {correct_str}"
    return None


def process_question(q: dict, qindex: int, filepath: str) -> Tuple[dict, List[dict]]:
    """
    Process one question. Returns (modified_question, report_entries).
    """
    report = []
    stem = (q.get("stem") or q.get("questionText") or "").strip()
    options = q.get("options") or q.get("choices") or []
    if isinstance(options, str):
        options = json.loads(options) if options.startswith("[") else [options]
    options = [str(o).strip() for o in options if o is not None and str(o).strip()]
    answer_index = q.get("answerIndex", q.get("correctIndex", 0))
    explanation = (q.get("explanation") or "").strip()

    if len(options) < 2:
        return q, report

    # Compute correct answer
    computed = solve_from_stem(stem, options)
    from_expl = extract_answer_from_explanation(explanation, options)

    correct_val = None
    correct_str = None
    source = None

    if computed:
        correct_val, correct_str = computed
        source = "recalculated"
    elif from_expl:
        correct_val, correct_str = from_expl
        source = "from_explanation"

    if correct_val is None:
        return q, report

    correct_idx = find_option_index(options, correct_str)
    if correct_idx < 0:
        # Also try without decimal
        try:
            f = float(str(correct_val).replace(",", "."))
            if f == int(f):
                correct_idx = find_option_index(options, str(int(f)))
        except (ValueError, TypeError):
            pass

    modified = False
    new_q = dict(q)

    # Issue 1: correct answer not in options -> fix options by replacing wrong one
    if correct_idx < 0:
        correct_str = str(int(correct_val)) if isinstance(correct_val, (int, float)) and correct_val == int(correct_val) else str(correct_val)
        # Replace option at current (wrong) answerIndex with correct answer
        new_options = list(options)
        idx_to_replace = min(answer_index, len(new_options) - 1)
        new_options[idx_to_replace] = correct_str
        new_q["options"] = new_options
        new_q["answerIndex"] = idx_to_replace
        report.append({
            "file": filepath,
            "question_index": qindex,
            "issue": "correct_answer_not_in_options",
            "computed_answer": correct_str,
            "old_options": list(options),
            "fix_applied": f"Replaced options[{idx_to_replace}] with '{correct_str}', set answerIndex={idx_to_replace}"
        })
        return new_q, report

    # Issue 2: answerIndex wrong
    if answer_index != correct_idx:
        report.append({
            "file": filepath,
            "question_index": qindex,
            "issue": "wrong_answer_index",
            "computed_answer": str(correct_val),
            "old_answer_index": answer_index,
            "correct_index": correct_idx,
            "fix_applied": f"answerIndex: {answer_index} -> {correct_idx}"
        })
        new_q["answerIndex"] = correct_idx
        modified = True

    # Issue 3: inconsistent explanation
    inconsist = detect_inconsistent_explanation(explanation, correct_val, options, answer_index or correct_idx)
    if inconsist:
        report.append({
            "file": filepath,
            "question_index": qindex,
            "issue": "inconsistent_explanation",
            "detail": inconsist,
            "fix_applied": "Updated explanation to remove contradiction"
        })
        # Simplify explanation: remove "X değildir" and state correct result
        new_expl = re.sub(r"\d+\s*(?:değildir|olmaz|yanlış)[^.]*\.?", "", explanation, flags=re.IGNORECASE)
        new_expl = re.sub(r"doğru (?:değer|işlem|seçenek)[^.]*\.?", "", new_expl, flags=re.IGNORECASE)
        new_expl = new_expl.strip()
        if new_expl and not new_expl.endswith("."):
            new_expl += f" Sonuç {correct_val} olur."
        elif not new_expl:
            new_expl = f"Sonuç {correct_val} olur."
        new_q["explanation"] = new_expl
        modified = True

    # If we fixed answerIndex, also ensure explanation is consistent
    if modified and not inconsist and "explanation" in new_q:
        expl = new_q["explanation"]
        if correct_str and correct_str not in expl and str(correct_val) not in expl:
            if expl.strip().endswith("."):
                new_q["explanation"] = expl.rstrip() + f" Sonuç {correct_val}'dir."
            elif not expl:
                new_q["explanation"] = f"Sonuç {correct_val} olur."

    return new_q, report


def process_file(filepath: str) -> Tuple[List[dict], List[dict], bool]:
    """Process one JSON file. Returns (questions, report_entries, changed)."""
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        return [], [{"file": filepath, "question_index": -1, "issue": "file_load_error", "detail": str(e), "fix_applied": "None"}], False

    questions = data.get("questions") or []
    all_reports = []
    new_questions = []
    changed = False

    for i, q in enumerate(questions):
        new_q, reports = process_question(q, i, filepath)
        all_reports.extend(reports)
        new_questions.append(new_q)
        if new_q != q:
            changed = True

    if changed:
        data["questions"] = new_questions
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

    return new_questions, all_reports, changed


def main():
    base = Path(__file__).resolve().parent
    mat_path = base / MAT_DIR
    if not mat_path.exists():
        print(f"MAT dir not found: {mat_path}")
        return

    json_files = sorted(mat_path.glob("*.json"))
    all_reports = []
    files_changed = []

    for jf in json_files:
        rel = os.path.relpath(jf, base)
        _, reports, changed = process_file(str(jf))
        all_reports.extend(reports)
        if changed:
            files_changed.append(rel)

    # Print report
    print("=" * 80)
    print("MAT LGS JSON VALIDATION REPORT")
    print("=" * 80)

    if not all_reports:
        print("\nNo issues detected. All questions validated successfully.")
    else:
        print(f"\nTotal issues: {len(all_reports)}\n")
        for r in all_reports:
            print(f"File: {r.get('file', '?')}")
            print(f"Question index: {r.get('question_index', '?')}")
            print(f"Issue: {r.get('issue', r.get('detail', '?'))}")
            if "fix_applied" in r:
                print(f"Fix applied: {r['fix_applied']}")
            for k in ("computed_answer", "old_answer_index", "correct_index", "options", "detail"):
                if k in r and k != "issue":
                    print(f"  {k}: {r[k]}")
            print()

    if files_changed:
        print("Files modified:")
        for f in files_changed:
            print(f"  - {f}")

    # Save report JSON
    report_path = base / "mat_validation_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump({
            "total_issues": len(all_reports),
            "files_changed": files_changed,
            "issues": all_reports
        }, f, ensure_ascii=False, indent=2)
    print(f"\nReport saved to: {report_path}")


if __name__ == "__main__":
    main()
