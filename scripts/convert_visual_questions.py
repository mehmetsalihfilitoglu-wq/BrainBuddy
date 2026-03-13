#!/usr/bin/env python3
"""Convert BrainBuddy visual-reference questions into true visual questions.
- A (default): assign imageAsset to reusable placeholder images
- B: rewrite stem to remove visual references when stem fully describes content

Uses Pillow for image generation. Install with: pip install Pillow
"""
import json
import re
from pathlib import Path
from collections import defaultdict

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
LGS_IMPORT = ASSETS / "lgs_import"
QUIZ_IMAGES = ASSETS / "quiz_images"

# Stem patterns (from audit_visual_questions.py)
VISUAL_PATTERNS = [
    r"\bgrafikte\b", r"\bgrafiğe\b", r"\bgrafikteki\b", r"\bgrafiğin\b",
    r"\btabloda\b", r"\btabloya\b", r"\btabloda\s+veril", r"\btabloda\s+verilmiş",
    r"\bresimde\b", r"\bresimdeki\b", r"\bGörselde\b", r"\bgörselde\b",
    r"\byukarıdaki\s+(grafik|tablo|resim|görsel|şekil|figür)",
    r"\b(grafik|tablo|resim|görsel)\s+(gösteriyor|verilmiş|verilmiştir|gösterilmiş)",
    r"\bşekilde\s+(gösteril|veril|sunul)", r"\bşekildeki\b", r"\bdiyagramda\b",
    r"görsel\s+([a-z]+\s+)?yorum", r"tablo\s+grafik", r"grafik\s+tablo",
    r"\bin the picture\b", r"\bthe picture\s+(shows|above)\b", r"\blook at the picture\b",
    r"\bsee the (picture|graph|table)\b", r"\bfrom the (picture|graph|table)\b",
    r"\bin the (graph|chart|table)\b", r"\bthe (graph|chart|table)\s+(shows|above)\b",
    r"\baccording to the (graph|chart|table|figure)\b",
    r"\bthe (graph|chart|table)\s+above\b",
]
VISUAL_RE = re.compile("|".join(f"({p})" for p in VISUAL_PATTERNS), re.I | re.UNICODE)

# B: rewrite patterns (Turkish)
REWRITE_PAIRS_TR = [
    (re.compile(r"\bgrafikte\s+", re.I), "Verilen bilgilere göre "),
    (re.compile(r"\bgrafiğe\s+göre\s*", re.I), "Verilen bilgilere göre "),
    (re.compile(r"\bbu grafiğe göre\s*", re.I), "Verilen bilgilere göre "),
    (re.compile(r"\btabloda\s+", re.I), "Verilen bilgilere göre "),
    (re.compile(r"\bbu tabloya göre\s*", re.I), "Verilen bilgilere göre "),
    (re.compile(r"\bgrafikteki\s+", re.I), "Verilen bilgilerdeki "),
    (re.compile(r"\btabloda\s+verilen\s+", re.I), "Verilen "),
    (re.compile(r"\bgrafikte\s+verilen\s+", re.I), "Verilen "),
]
# B: rewrite patterns (English)
REWRITE_PAIRS_EN = [
    (re.compile(r"\bin the picture[,\s]+", re.I), "Given: "),
    (re.compile(r"\baccording to the (graph|chart|table|figure)\s*", re.I), "According to the given information, "),
    (re.compile(r"\bfrom the (picture|graph|table)\s*", re.I), "From the given information, "),
    (re.compile(r"\bsee the (picture|graph|table)\s*", re.I), ""),
    (re.compile(r"\blook at the picture[.\s]*", re.I), ""),
    (re.compile(r"\bthe (graph|chart|table)\s+(shows|above)\s*", re.I), "The given information "),
]

# Map lgs_import folder -> quiz_images subfolder (priority subjects)
SUBJECT_MAP = {
    "mat": "mat", "mat1": "mat", "mat2": "mat", "mat3": "mat", "mat4": "mat",
    "mat5": "mat", "mat6": "mat", "mat7": "mat",
    "fen": "fen", "fen3": "fen", "fen4": "fen", "fen5": "fen", "fen6": "fen", "fen7": "fen",
    "sosyal": "sosyal", "sosyal4": "sosyal", "sosyal5": "sosyal", "sosyal6": "sosyal",
    "english": "english", "eng": "english", "english1": "english", "english2": "english",
    "english3": "english", "english4": "english", "english5": "english", "english6": "english", "english7": "english",
    "eng1": "english", "eng2": "english", "eng3": "english", "eng4": "english", "eng5": "english", "eng6": "english", "eng7": "english",
    "turkce": "turkce", "turkce2": "turkce", "turkce3": "turkce", "turkce4": "turkce",
    "turkce5": "turkce", "turkce6": "turkce", "turkce7": "turkce",
    "din": "din", "din4": "din", "din5": "din", "din6": "din", "din7": "din",
    "hayat": "hayat", "hayat1": "hayat", "hayat2": "hayat", "hayat3": "hayat",
    "inkilap": "inkilap", "inkilap7": "inkilap",
}

ASSET_TYPES = ["graph_bar", "graph_line", "table", "picture", "geometry", "map", "experiment"]


def has_visual_ref(stem):
    if not stem or not isinstance(stem, str):
        return False
    return bool(VISUAL_RE.search(stem))


def has_visual_asset(q):
    for key in ("imageAsset", "visualAsset", "graphicAsset", "tableAsset"):
        v = q.get(key)
        if v and isinstance(v, str) and v.strip() and v.lower() != "null":
            return True
    return False


def classify(q, stem):
    """
    1 (A) = receive asset (default) - preserve visual nature
    2 (B) = rewrite stem - ONLY when stem fully describes content AND visual adds no value
    Strategy: default to A; B only if truly impractical (very long text-only description)
    """
    stem_lower = stem.lower()
    # B only when stem is long and explicitly describes all data (no need for visual)
    if len(stem) > 220 and (
        re.search(r"tabloda\s+[^.]{40,}\s+verilmiştir", stem, re.I)
        or re.search(r"grafikte\s+[^.]{40,}\s+gösterilmiştir", stem, re.I)
    ):
        return 2
    # Default: preserve visual (A)
    return 1


def infer_asset_type(stem, subject):
    s = stem.lower()
    if re.search(r"\b(çubuk|sütun|bar)\s*(grafik|chart)\b", s) or "bar chart" in s or "çubuk grafik" in s:
        return "graph_bar"
    if re.search(r"\b(çizgi|line)\s*(grafik|graph)\b", s) or "line graph" in s or "çizgi grafik" in s:
        return "graph_line"
    if re.search(r"\btablo\b", s) or "table" in s or "chart" in s:
        return "table"
    if re.search(r"\bharita\b", s) or "map" in s:
        return "map"
    if re.search(r"\bdeney\b", s) or "experiment" in s:
        return "experiment"
    if re.search(r"\b(şekil|geometri|figure)\b", s) and subject == "mat":
        return "geometry"
    return "picture"


def get_subject_from_path(path, q=None):
    parts = path.parts
    if "lgs_import" not in parts:
        if q:
            subj = (q.get("subject") or "").strip()
            if subj:
                return SUBJECT_MAP.get(subj.lower(), subj.lower() or "other")
        return "other"
    i = parts.index("lgs_import")
    if i + 1 >= len(parts):
        return "other"
    folder = parts[i + 1]
    # JSON file directly in lgs_import (e.g. lgs_fen.json) -> extract subject
    if folder.endswith(".json"):
        name = folder.replace(".json", "")
        if name.startswith("lgs_"):
            subj = name[4:]  # strip "lgs_"
            return SUBJECT_MAP.get(subj, subj)
        return "other"
    return SUBJECT_MAP.get(folder, folder)


def rewrite_stem(stem):
    """Remove visual references, replace with neutral phrasing."""
    out = stem
    for pat, repl in REWRITE_PAIRS_TR:
        out = pat.sub(repl, out)
    for pat, repl in REWRITE_PAIRS_EN:
        out = pat.sub(repl, out)
    out = re.sub(r"\s+", " ", out).strip()
    return out


def ensure_minimal_png(path, w=400, h=300):
    """Create minimal placeholder PNG (1x1 gray) without PIL. Used when Pillow unavailable."""
    import struct
    import zlib
    # Minimal valid PNG: 1x1 gray pixel
    def png_chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
    raw = b"\x08\x02\x00\x00\x00"  # 8-bit grayscale
    ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)
    idat = zlib.compress(raw, 9)
    png = b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", idat) + png_chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def create_asset_image(path, asset_type, use_pillow):
    """Create simple placeholder PNG. Returns True if created."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        return False
    if use_pillow:
        try:
            from PIL import Image, ImageDraw
            img = Image.new("RGB", (400, 300), color=(248, 248, 252))
            draw = ImageDraw.Draw(img)
            # Simple visual cues by type
            if asset_type == "graph_bar":
                for i, h in enumerate([80, 120, 90, 150, 100]):
                    x = 60 + i * 60
                    draw.rectangle([x, 250 - h, x + 40, 250], fill=(70, 130, 180), outline=(50, 100, 150))
            elif asset_type == "graph_line":
                pts = [(80, 200), (140, 120), (200, 160), (260, 80), (320, 100)]
                draw.line(pts, fill=(70, 130, 180), width=3)
            elif asset_type == "table":
                for r in range(3):
                    for c in range(3):
                        draw.rectangle([80 + c * 80, 80 + r * 50, 160 + c * 80, 130 + r * 50],
                                       outline=(100, 100, 100), fill=(255, 255, 255))
            elif asset_type == "geometry":
                draw.polygon([(200, 50), (350, 200), (50, 200)], outline=(70, 130, 180), width=2)
            elif asset_type == "map":
                draw.ellipse([120, 80, 280, 220], outline=(70, 130, 180), width=2)
                draw.rectangle([140, 120, 200, 160], outline=(180, 70, 70))
            elif asset_type == "experiment":
                draw.rectangle([100, 100, 300, 220], outline=(70, 130, 180))
                draw.ellipse([170, 140, 230, 200], outline=(180, 100, 70))
            else:  # picture
                draw.rectangle([80, 60, 320, 240], outline=(100, 100, 100))
                draw.ellipse([150, 100, 250, 200], outline=(70, 130, 180))
            img.save(path, "PNG")
            return True
        except Exception as e:
            print(f"  [Pillow error] {e}; falling back to minimal PNG")
            ensure_minimal_png(path)
            return True
    else:
        ensure_minimal_png(path)
        return True


def collect_json_paths():
    paths = []
    if LGS_IMPORT.exists():
        paths.extend(LGS_IMPORT.rglob("*.json"))
    qt = ASSETS / "questions_tr.json"
    if qt.exists():
        paths.append(qt)
    packs = ASSETS / "packs"
    if packs.exists():
        paths.extend(packs.rglob("*.json"))
    return paths


def load_questions_from_file(path):
    try:
        data = json.load(open(path, encoding="utf-8"))
    except Exception:
        return []
    if isinstance(data, list):
        return [(path, i, q) for i, q in enumerate(data) if isinstance(q, dict)]
    if isinstance(data, dict):
        qs = data.get("questions", [])
        if isinstance(qs, list):
            return [(path, i, q) for i, q in enumerate(qs) if isinstance(q, dict)]
    return []


def main():
    use_pillow = False
    try:
        from PIL import Image
        use_pillow = True
        print("Pillow found: will generate meaningful placeholder images.")
    except ImportError:
        print("Pillow not found. Install with: pip install Pillow")
        print("Creating minimal placeholder PNGs (1x1 pixel). Run with Pillow for better images.\n")

    # Collect all visual-ref, no-asset questions
    rows = []
    for path in collect_json_paths():
        for _, idx, q in load_questions_from_file(path):
            stem = q.get("stem") or q.get("questionText") or q.get("question") or ""
            if not has_visual_ref(stem) or has_visual_asset(q):
                continue
            cls = classify(q, stem)
            subject = get_subject_from_path(Path(path), q)
            rows.append((path, idx, q, stem, cls, subject))

    class_a = [r for r in rows if r[4] == 1]
    class_b = [r for r in rows if r[4] == 2]

    report = {
        "total": len(rows),
        "class_a_count": len(class_a),
        "class_b_count": len(class_b),
        "files_changed": set(),
        "assets_created": 0,
        "by_subject": defaultdict(lambda: {"a": 0, "b": 0}),
        "assets_by_type": defaultdict(set),
    }

    # Create subject dirs
    subjects_needed = {r[5] for r in class_a}
    for subj in subjects_needed:
        (QUIZ_IMAGES / subj).mkdir(parents=True, exist_ok=True)

    # Process B: rewrite stems
    for path, idx, q, stem, _, subject in class_b:
        report["by_subject"][subject]["b"] += 1
        new_stem = rewrite_stem(stem)
        if new_stem != stem:
            try:
                data = json.load(open(path, encoding="utf-8"))
                qs = data.get("questions", []) if isinstance(data, dict) else data
                if isinstance(qs, list) and idx < len(qs):
                    qs[idx]["stem"] = new_stem
                    with open(path, "w", encoding="utf-8") as f:
                        json.dump(data, f, ensure_ascii=False, indent=2)
                    report["files_changed"].add(str(path))
            except Exception as e:
                print(f"  [Error] {path}: {e}")

    # Process A: assign imageAsset
    asset_used = {}  # (subject, asset_type) -> asset_path
    for path, idx, q, stem, _, subject in class_a:
        asset_type = infer_asset_type(stem, subject)
        key = (subject, asset_type)
        if key not in asset_used:
            full_path = QUIZ_IMAGES / subject / f"{asset_type}.png"
            if create_asset_image(full_path, asset_type, use_pillow):
                report["assets_created"] += 1
            asset_used[key] = f"quiz_images/{subject}/{asset_type}.png"
        asset_path = asset_used[key]
        report["assets_by_type"][asset_type].add(subject)
        try:
            data = json.load(open(path, encoding="utf-8"))
            qs = data.get("questions", []) if isinstance(data, dict) else data
            if isinstance(qs, list) and idx < len(qs):
                qs[idx]["imageAsset"] = asset_path
                with open(path, "w", encoding="utf-8") as f:
                    json.dump(data, f, ensure_ascii=False, indent=2)
                report["files_changed"].add(str(path))
        except Exception as e:
            print(f"  [Error] {path}: {e}")
        report["by_subject"][subject]["a"] += 1

    # Write report
    report_path = ROOT / "visual_conversion_report.md"
    with open(report_path, "w", encoding="utf-8") as f:
        f.write("# Visual Questions Conversion Report\n\n")
        f.write("## Summary\n\n")
        f.write(f"- **Total questions processed:** {report['total']}\n")
        f.write(f"- **Class A (assigned imageAsset):** {report['class_a_count']}\n")
        f.write(f"- **Class B (rewritten stem):** {report['class_b_count']}\n")
        f.write(f"- **JSON files modified:** {len(report['files_changed'])}\n")
        f.write(f"- **Assets created:** {report['assets_created']}\n\n")
        f.write("### By subject\n\n")
        f.write("| Subject | Class A | Class B |\n|---------|---------|--------|\n")
        for subj in sorted(report["by_subject"].keys()):
            s = report["by_subject"][subj]
            f.write(f"| {subj} | {s['a']} | {s['b']} |\n")
        f.write("\n### Assets by type\n\n")
        for t in sorted(report["assets_by_type"].keys()):
            subs = ", ".join(sorted(report["assets_by_type"][t]))
            f.write(f"- **{t}**: {subs}\n")
        f.write("\n### Files changed\n\n")
        for p in sorted(report["files_changed"]):
            f.write(f"- {p}\n")

    print("=" * 60)
    print("VISUAL CONVERSION REPORT")
    print("=" * 60)
    print(f"Total: {report['total']}  |  A (asset): {report['class_a_count']}  |  B (rewrite): {report['class_b_count']}")
    print(f"Files changed: {len(report['files_changed'])}  |  Assets created: {report['assets_created']}")
    print("=" * 60)
    print(f"Report: {report_path}")
    return 0


if __name__ == "__main__":
    exit(main())
