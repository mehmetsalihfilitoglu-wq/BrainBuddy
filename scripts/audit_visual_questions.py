#!/usr/bin/env python3
"""Audit: find questions that reference picture/graph/table/visual but have no imageAsset.
Report file, id, stem snippet, and classification (1=needs asset, 2=rewrite to remove)."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"

# Stem patterns that imply a visual is expected (case-insensitive)
# Excludes "şekilde" alone (often "en güzel şekilde" = in the best way)
VISUAL_PATTERNS = [
    # Turkish
    r"\bgrafikte\b", r"\bgrafiğe\b", r"\bgrafikteki\b", r"\bgrafiğin\b",
    r"\btabloda\b", r"\btabloya\b", r"\btabloda\s+veril", r"\btabloda\s+verilmiş",
    r"\bresimde\b", r"\bresimdeki\b", r"\bGörselde\b", r"\bgörselde\b",
    r"\byukarıdaki\s+(grafik|tablo|resim|görsel|şekil|figür)",
    r"\b(grafik|tablo|resim|görsel)\s+(gösteriyor|verilmiş|verilmiştir|gösterilmiş)",
    r"\bşekilde\s+(gösteril|veril|sunul)", r"\bşekildeki\b", r"\bdiyagramda\b",
    r"görsel\s+([a-z]+\s+)?yorum", r"tablo\s+grafik", r"grafik\s+tablo",
    # English
    r"\bin the picture\b", r"\bthe picture\s+(shows|above)\b", r"\blook at the picture\b",
    r"\bsee the (picture|graph|table)\b", r"\bfrom the (picture|graph|table)\b",
    r"\bin the (graph|chart|table)\b", r"\bthe (graph|chart|table)\s+(shows|above)\b",
    r"\baccording to the (graph|chart|table|figure)\b",
    r"\bthe (graph|chart|table)\s+above\b",
]

# Patterns that are text-only (describe content, don't require viewing a visual)
# e.g. "Grafikte X gösterilmiştir" - the stem DESCRIBES the graph, so answerable from text alone
TEXT_DESCRIBES_VISUAL = [
    r"grafikte\s+[^.]+\s+gösterilmiştir",
    r"tabloda\s+[^.]+\s+verilmiştir",
    r"grafiğe göre",  # "according to the graph" - but graph data is in stem
    r"bu grafiğe göre", r"bu tabloya göre",
    r"grafikte\s+[^?]+\s+yer\s+almakta",
]

# Combined regex
VISUAL_RE = re.compile("|".join(f"({p})" for p in VISUAL_PATTERNS), re.I | re.UNICODE)


def has_visual_ref(stem):
    """True if stem references a picture, graph, table, or visual."""
    if not stem or not isinstance(stem, str):
        return False
    return bool(VISUAL_RE.search(stem))


def has_visual_asset(q):
    """True if question has imageAsset, visualAsset, graphicAsset, or tableAsset set."""
    for key in ("imageAsset", "visualAsset", "graphicAsset", "tableAsset"):
        v = q.get(key)
        if v and isinstance(v, str) and v.strip() and v.lower() != "null":
            return True
    return False


def classify(q, stem):
    """
    1 = should get a real visual asset (question requires viewing the visual to answer)
    2 = should be rewritten to remove visual dependency (content is fully in stem text)
    """
    stem_lower = stem.lower()
    # If stem describes the graph/table content in full (e.g. "Grafikte X, Y, Z gösterilmiştir. Buna göre...")
    # then answer is derivable from text -> rewrite to remove dependency
    if re.search(r"grafikte\s+[^.]{20,}\s+gösterilmiştir", stem, re.I):
        return 2
    if re.search(r"tabloda\s+[^.]{20,}\s+verilmiştir", stem, re.I):
        return 2
    if re.search(r"grafikte\s+[^.]{30,}\.\s+", stem, re.I):
        return 2
    if "bu grafiğe göre" in stem_lower or "bu tabloya göre" in stem_lower:
        # Data might be in stem - check length
        if len(stem) > 150:
            return 2  # Likely full description in text
    # questionTypes that imply visual is essential
    qt = (q.get("questionType") or "").lower()
    if any(x in qt for x in ["grafik", "tablo", "görsel", "graph", "visual", "picture"]):
        return 1
    # Default: if they mention a visual, assume they need it
    return 1


def collect_json_paths():
    paths = []
    # lgs_import
    lgs = ASSETS / "lgs_import"
    if lgs.exists():
        for p in lgs.rglob("*.json"):
            paths.append(p)
    # packs
    packs = ASSETS / "packs"
    if packs.exists():
        for p in packs.rglob("*.json"):
            paths.append(p)
    # questions_tr.json
    qt = ASSETS / "questions_tr.json"
    if qt.exists():
        paths.append(qt)
    return paths


def load_questions_from_file(path):
    try:
        data = json.load(open(path, encoding="utf-8"))
    except Exception:
        return []
    if isinstance(data, list):
        return [(path, i, q) for i, q in enumerate(data) if isinstance(q, dict)]
    if isinstance(data, dict):
        qs = data.get("questions", data.get("questions", []))
        if isinstance(qs, list):
            return [(path, i, q) for i, q in enumerate(qs) if isinstance(q, dict)]
    return []


def main():
    rows = []
    for path in collect_json_paths():
        for _, idx, q in load_questions_from_file(path):
            stem = q.get("stem") or q.get("questionText") or q.get("question") or ""
            if not has_visual_ref(stem):
                continue
            if has_visual_asset(q):
                continue
            qid = q.get("id") or q.get("questionId") or f"idx_{idx}"
            cls = classify(q, stem)
            try:
                rel = path.relative_to(ROOT)
            except ValueError:
                rel = path
            rows.append((str(rel), qid, stem[:120], cls))
    rows.sort(key=lambda x: (x[3], x[0], x[1]))
    cls1 = [r for r in rows if r[3] == 1]
    cls2 = [r for r in rows if r[3] == 2]

    # Per-source breakdown (e.g. english3, mat, fen4)
    by_source = {}
    for rel, qid, _, cls in rows:
        parts = rel.replace("\\", "/").split("/")
        src = "lgs_import/" + parts[parts.index("lgs_import") + 1] if "lgs_import" in parts else parts[-2] + "/" + parts[-1] if len(parts) >= 2 else str(rel)
        by_source.setdefault(src, {"total": 0, "cls1": 0, "cls2": 0})
        by_source[src]["total"] += 1
        if cls == 1:
            by_source[src]["cls1"] += 1
        else:
            by_source[src]["cls2"] += 1

    out_path = ROOT / "visual_audit_report.md"
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("# Visual-Reference Audit Report\n\n")
        f.write("Questions that mention picture/graph/table/visual in the stem but have **no** imageAsset set.\n\n")
        f.write("## Summary\n\n")
        f.write(f"- **Total found:** {len(rows)}\n")
        f.write(f"- **Class 1 (should get real visual asset):** {len(cls1)}\n")
        f.write(f"- **Class 2 (should rewrite to remove visual dependency):** {len(cls2)}\n\n")
        f.write("### By subject/source\n\n")
        f.write("| Source | Total | Class 1 | Class 2 |\n|--------|-------|---------|--------|\n")
        for src in sorted(by_source.keys()):
            s = by_source[src]
            f.write(f"| {src} | {s['total']} | {s['cls1']} | {s['cls2']} |\n")
        f.write("\n## Class 1: Should get real visual asset\n\n")
        for rel, qid, stem_snip, _ in cls1:
            f.write(f"- **{rel}** id=`{qid}`  \n  stem: {stem_snip}\n\n")
        f.write("## Class 2: Should rewrite to remove visual dependency\n\n")
        for rel, qid, stem_snip, _ in cls2:
            f.write(f"- **{rel}** id=`{qid}`  \n  stem: {stem_snip}\n\n")
    print("Report written to", out_path)
    print("Total:", len(rows), "| Class 1:", len(cls1), "| Class 2:", len(cls2))
    return 0


if __name__ == "__main__":
    exit(main())
