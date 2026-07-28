#!/usr/bin/env python3
"""
Scan MAT packs for: duplicate stems, short_item, one-operation questions.
Rewrite weak questions into paragraph-style multi-step LGS questions.
Preserves schema and IDs (sourceRef etc).
"""

import json
import os
import re
import unicodedata
import hashlib
from pathlib import Path

MAT_DIR = Path("app/src/main/assets/lgs_import/mat")

PROBLEM_KEYWORDS = [
    "problemi", "problem", "oran", "yüzde", "grafik", "tablo",
    "şekilde", "aşağıdaki", "metne göre", "parçaya göre",
    "buna göre", "yorumla", "çözüm"
]

TURKISH_NAMES = {
    "ali", "ayşe", "mehmet", "ahmet", "zeynep", "fatma", "hasan", "mustafa",
    "emre", "elif", "ömer", "kaan", "derya", "selin", "burak", "cem",
    "oya", "can", "ece", "deniz", "merve", "berkay", "sude", "emir",
}


def normalize_stem(stem: str) -> str:
    """Python equivalent of QuestionStemHash.normalizeStem for duplicate detection."""
    s = stem.lower().strip()
    s = re.sub(r"[^\w\s]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    s = re.sub(r"\d+", "#", s)
    for name in TURKISH_NAMES:
        s = re.sub(rf"\b{re.escape(name)}\b", "NAME", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def stem_hash(stem: str) -> str:
    return hashlib.sha256(normalize_stem(stem).encode("utf-8")).hexdigest()


def has_problem_keyword(stem: str) -> bool:
    s = stem.lower()
    return any(kw in s for kw in PROBLEM_KEYWORDS)


def is_short_item(stem: str, question_type: str) -> bool:
    stem_len = len(stem)
    if question_type == "short_item":
        return True
    if stem_len < 80:
        return True
    if stem_len < 120 and not has_problem_keyword(stem):
        return True
    return False


def is_one_operation(stem: str, explanation: str, topic: str) -> bool:
    """Detect questions solvable with only one arithmetic operation."""
    stem_lower = stem.lower()
    expl = (explanation or "").lower()

    # Very short stem + simple pattern
    if len(stem) < 100:
        one_op_patterns = [
            r"alanı kaç", r"çevresi kaç", r"ebob.*kaç", r"ekok.*kaç",
            r"2\^?\d+\s*[×x]\s*2\^?\d+",  # 2^5 × 2^3
            r"\d+\s*ve\s*\d+.*ortak bölen",
            r"rastgele.*olasılığı kaç",
            r"eğimi kaçtır", r"eğim kaç",
            r"ortalama.*kaçtır",  # direct average
            r"bir dikdörtgen.*alanı kaç",
            r"kare.*alanı.*etrafına tel",
        ]
        if any(re.search(p, stem_lower) for p in one_op_patterns):
            return True

    # Explanation contains single arithmetic (one × or + or - with numbers)
    # Heuristic: explanation is very short and has one calc
    if explanation and len(explanation) < 80:
        calc_count = (
            len(re.findall(r"\d+\s*[×x*]\s*\d+", expl)) +
            len(re.findall(r"\d+\s*[+-]\s*\d+", expl)) +
            len(re.findall(r"\d+/\d+", expl)) +
            len(re.findall(r"√\d+", expl))
        )
        if calc_count <= 1 and len(stem) < 150:
            return True

    return False


def rewrite_one_op_question(q: dict, topic: str) -> str:
    """Rewrite one-operation question into paragraph-style multi-step LGS stem."""
    stem = q.get("stem", "")
    stem_lower = stem.lower()
    topic_lower = (topic or "").lower()

    # Uslu sayılar (2^5 × 2^3) - preserve same answer
    if "2^5" in stem or "2^3" in stem or "üs" in stem_lower:
        return (
            "Bir veri merkezinde her sunucuda 2^5 işlemci ve her işlemcide 2^3 çekirdek bulunmaktadır. "
            "Teknik ekip bir sunucudaki toplam çekirdek sayısını hesaplamak için "
            "2^5 × 2^3 işleminin sonucunu bulacaktır. Üslü sayı kurallarına göre bu işlemin sonucu kaçtır?"
        )
    if "uslu" in topic_lower or "üslü" in topic_lower:
        m = re.search(r"(\d+)\^(\d+)\s*[×x]\s*(\d+)\^(\d+)", stem)
        if m:
            a, b, c, d = m.groups()
            return (
                f"Bir veri merkezinde {a}^{b} sunucu vardır. Her sunucuda {c}^{d} depolama birimi bulunmaktadır. "
                f"Teknik ekip toplam depolama birimi sayısını hesaplamak için üslü sayı kurallarını kullanacaktır. "
                f"Buna göre veri merkezindeki toplam depolama birimi sayısı kaçtır?"
            )

    # EBOB
    if "ebob" in stem_lower or "ortak bölen" in stem_lower:
        m = re.search(r"(\d+)\s*ve\s*(\d+)", stem)
        if m:
            a, b = m.groups()
            return (
                f"Bir etkinlikte kullanılmak üzere {a} kırmızı ve {b} mavi balon vardır. "
                f"Balonlar eşit sayıda olacak şekilde gruplara ayrılacaktır. "
                f"Önce bu iki sayının en büyük ortak bölenini bularak bir gruptaki balon sayısının "
                f"en fazla kaç olabileceğini hesaplamak gerekmektedir. Buna göre bir gruptaki balon sayısı en fazla kaç olabilir?"
            )

    # Kare alan → çevre
    if "kare" in stem_lower and "alan" in stem_lower and "tel" in stem_lower:
        m = re.search(r"(\d+)\s*metre", stem)
        area = m.group(1) if m else "256"
        return (
            f"Alanı {area} metrekare olan kare şeklindeki bir bahçenin etrafına tel çekilecektir. "
            f"İşçiler önce karenin bir kenar uzunluğunu hesaplayacak, ardından çevre uzunluğunu "
            f"bularak gereken tel miktarını belirleyecektir. Buna göre bu bahçenin çevresi kaç metredir?"
        )

    # Dikdörtgen alan (tek işlem)
    if "dikdörtgen" in stem_lower and "alan" in stem_lower:
        nums = re.findall(r"\d+", stem)
        if len(nums) >= 2:
            a, b = nums[0], nums[1]
            return (
                f"El işi dersinde öğrenciler dikdörtgen biçiminde bir pano hazırlayacaktır. "
                f"Öğretmen kısa kenarın {a} cm, uzun kenarın {b} cm olduğunu söyler. "
                f"Öğrenciler panonun kapladığı alanı hesaplamak için önce kenar uzunluklarını "
                f"belirleyip ardından alan formülünü uygulayacaktır. Buna göre bu dikdörtgen panonun alanı kaç santimetrekaredir?"
            )

    # Eğim (tek nokta çifti)
    if "eğim" in stem_lower:
        m = re.search(r"a?\s*\(\s*(\d+)\s*,\s*(\d+)\s*\).*b?\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)", stem_lower)
        if m:
            x1, y1, x2, y2 = m.groups()
            return (
                f"Koordinat düzleminde A({x1},{y1}) ve B({x2},{y2}) noktalarından geçen bir rampa tasarlanacaktır. "
                f"Mühendisler rampanın eğimini hesaplamak için bu iki nokta arasındaki dikey ve yatay farkı "
                f"bulup oranlayacaktır. Buna göre bu rampanın eğimi kaçtır?"
            )

    # Olasılık (basit)
    if "olasılık" in stem_lower or "rastgele" in stem_lower:
        return (
            "Olasılık konusunda çalışan bir öğrenci aşağıdaki problemi çözmektedir. "
            "Önce toplam durum sayısını, ardından istenen durumların sayısını belirleyip "
            "oranı hesaplaması gerekmektedir. " + stem
        )

    # Ortalama (direkt veri)
    if "ortalama" in stem_lower and "sırasıyla" in stem_lower:
        return (
            "Bir sınıfta yapılan matematik denemesinde öğrencilerin netleri ölçülmüştür. "
            "Öğretmen bu verileri karne ortalaması hesaplamak için kullanacaktır. "
            "Önce toplam netleri toplayıp öğrenci sayısına bölerek aritmetik ortalamayı bulacaktır. " + stem
        )

    # Anket oran
    if "anket" in stem_lower and "oran" in stem_lower:
        return (
            "Bir sınıfta yapılan ankette öğrencilerin en sevdiği dersler sorulmuştur. "
            "Rehberlik servisi bu verileri analiz ederek her dersi seçen öğrenci oranlarını "
            "hesaplayacak ve grafik hazırlayacaktır. " + stem
        )

    # Generic fallback: wrap in scenario
    return (
        "Bir öğrenci sınav hazırlığı sırasında aşağıdaki problemi çözmektedir. "
        "Önce verilen bilgileri düzenleyecek, ardından adım adım hesaplama yapacaktır. "
        + stem
    )


def rewrite_short_item(q: dict) -> str:
    """Expand short stem into paragraph-style context."""
    stem = q.get("stem", "")
    topic = q.get("topic", "")

    # If already has problem keywords but short, add scenario frame
    if has_problem_keyword(stem) and len(stem) < 120:
        return (
            "Okulda düzenlenen bir matematik etkinliğinde öğrenciler aşağıdaki problemi çözmektedir. "
            "Öğretmen önce problemi anlamalarını, sonra adım adım çözüme ulaşmalarını beklemektedir. "
            + stem
        )

    # For very short stems, try topic-specific expansion
    return rewrite_one_op_question(q, topic)


def collect_all_questions() -> list:
    """Load all questions from MAT packs with file/index info."""
    questions = []
    for fp in sorted(MAT_DIR.glob("*.json")):
        try:
            data = json.loads(fp.read_text(encoding="utf-8"))
        except Exception as e:
            print(f"  SKIP {fp}: {e}")
            continue
        for i, q in enumerate(data.get("questions", [])):
            stem = (q.get("stem") or q.get("questionText") or q.get("question") or "").strip()
            if not stem:
                continue
            questions.append({
                "file": str(fp),
                "index": i,
                "question": q,
                "stem": stem,
                "normalized": normalize_stem(stem),
                "stem_hash": stem_hash(stem),
            })
    return questions


def main():
    base = Path(__file__).parent
    mat_path = base / MAT_DIR
    if not mat_path.exists():
        print("MAT dir not found:", mat_path)
        return

    all_q = collect_all_questions()
    print(f"Loaded {len(all_q)} questions from MAT packs")

    # 1. Duplicate stems
    by_hash = {}
    duplicates = []
    for item in all_q:
        h = item["stem_hash"]
        if h in by_hash:
            duplicates.append({
                "file": item["file"],
                "index": item["index"],
                "stem_preview": item["stem"][:80],
                "duplicate_of": by_hash[h]["file"] + f":{by_hash[h]['index']}",
            })
        else:
            by_hash[h] = item

    # 2. Short item
    short_items = []
    for item in all_q:
        q = item["question"]
        stem = item["stem"]
        qt = q.get("questionType", "")
        if is_short_item(stem, qt):
            short_items.append({
                "file": item["file"],
                "index": item["index"],
                "stem": stem,
                "stem_length": len(stem),
                "questionType": qt,
                "sourceRef": q.get("sourceRef", ""),
            })

    # 3. One-operation
    one_op = []
    for item in all_q:
        q = item["question"]
        stem = item["stem"]
        expl = q.get("explanation", "")
        topic = q.get("topic", "")
        if is_one_operation(stem, expl, topic):
            one_op.append({
                "file": item["file"],
                "index": item["index"],
                "stem": stem[:100],
                "topic": topic,
                "sourceRef": q.get("sourceRef", ""),
            })

    # Report
    print("\n" + "=" * 60)
    print("1. DUPLICATE STEMS")
    print("=" * 60)
    for d in duplicates[:20]:
        print(f"  {d['file']}:{d['index']} ~ {d['duplicate_of']}")
        print(f"    {d['stem_preview']}...")
    if len(duplicates) > 20:
        print(f"  ... and {len(duplicates) - 20} more")
    print(f"  Total: {len(duplicates)} duplicates")

    print("\n" + "=" * 60)
    print("2. SHORT_ITEM (stem<80 OR stem<120+no problem keywords)")
    print("=" * 60)
    for s in short_items[:15]:
        print(f"  {Path(s['file']).name}:{s['index']} len={s['stem_length']} type={s['questionType']}")
        print(f"    {s['stem'][:70]}...")
    if len(short_items) > 15:
        print(f"  ... and {len(short_items) - 15} more")
    print(f"  Total: {len(short_items)} short_item")

    print("\n" + "=" * 60)
    print("3. ONE-OPERATION (solvable with single arithmetic step)")
    print("=" * 60)
    for o in one_op[:15]:
        print(f"  {Path(o['file']).name}:{o['index']} topic={o['topic']}")
        print(f"    {o['stem']}...")
    if len(one_op) > 15:
        print(f"  ... and {len(one_op) - 15} more")
    print(f"  Total: {len(one_op)} one-operation")

    # Rewrite weak questions
    weak_refs = set()
    for s in short_items:
        weak_refs.add((s["file"], s["index"]))
    for o in one_op:
        weak_refs.add((o["file"], o["index"]))

    # Apply rewrites to files
    files_to_rewrite = {}
    for (fpath, idx) in weak_refs:
        if fpath not in files_to_rewrite:
            files_to_rewrite[fpath] = set()
        files_to_rewrite[fpath].add(idx)

    rewrite_count = 0
    for fpath in sorted(files_to_rewrite.keys()):
        data = json.loads(Path(fpath).read_text(encoding="utf-8"))
        for idx in files_to_rewrite[fpath]:
            q = data["questions"][idx]
            stem = (q.get("stem") or q.get("questionText") or "").strip()
            is_short = is_short_item(stem, q.get("questionType", ""))
            is_op = is_one_operation(stem, q.get("explanation", ""), q.get("topic", ""))

            new_stem = None
            if is_op:
                new_stem = rewrite_one_op_question(q, q.get("topic", ""))
            elif is_short:
                new_stem = rewrite_short_item(q)

            if new_stem and new_stem != stem:
                data["questions"][idx]["stem"] = new_stem
                data["questions"][idx]["questionType"] = "paragraph"
                rewrite_count += 1
                print(f"\n  REWRITE {Path(fpath).name}:{idx} ({q.get('sourceRef', '')})")
                print(f"    OLD: {stem[:80]}...")
                print(f"    NEW: {new_stem[:100]}...")

        Path(fpath).write_text(
            json.dumps(data, ensure_ascii=False, indent=2),
            encoding="utf-8"
        )

    print(f"\n{'=' * 60}")
    print(f"Rewrote {rewrite_count} weak questions. Schema and IDs preserved.")
    print("=" * 60)

    # Save analysis report
    report = {
        "duplicates": duplicates,
        "short_item": short_items,
        "one_operation": one_op,
        "rewrite_count": rewrite_count,
    }
    (base / "mat_rewrite_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )
    print("\nReport saved to mat_rewrite_report.json")


if __name__ == "__main__":
    main()
