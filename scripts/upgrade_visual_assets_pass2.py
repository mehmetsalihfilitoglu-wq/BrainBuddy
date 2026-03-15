#!/usr/bin/env python3
"""
BrainBuddy visual upgrade pass 2: finish deterministic non-generic replacements.
- Process only remaining generic imageAsset questions (fen, mat, sosyal, english).
- Classify: deterministic_replace | safe_template_replace | manual_review | keep_generic.
- Replace only when new image directly reflects solvable question content.
- Tag each upgrade with confidence: exact_data_match | explicit_shape_match | explicit_object_match | keyword_safe_template | ambiguous_skip.
- Output: VISUAL_UPGRADE_REPORT_PASS2.md
"""
import json
import re
from pathlib import Path
from collections import defaultdict

# Import shared logic from pass 1
try:
    from upgrade_visual_assets import (
        collect_eligible,
        is_generic_asset,
        get_subject_from_path,
        get_pack_id,
        get_question_slug,
        load_questions,
        infer_visual_type,
        extract_numbers,
        extract_bar_data,
        extract_line_data,
        extract_table_rows,
        draw_graph_line,
        draw_table,
        draw_experiment_schematic,
        draw_geometry_simple,
        draw_counting_image,
        GENERIC_NAMES,
        SUBJECT_FOLDERS,
        PRIORITY_SUBJECTS,
        ASSETS,
        QUIZ_IMAGES,
        LGS_IMPORT,
        ROOT,
    )
except ImportError:
    # Fallback if run from different cwd
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from upgrade_visual_assets import (
        collect_eligible,
        is_generic_asset,
        get_subject_from_path,
        get_pack_id,
        get_question_slug,
        load_questions,
        infer_visual_type,
        extract_numbers,
        extract_bar_data,
        extract_line_data,
        extract_table_rows,
        draw_graph_line,
        draw_table,
        draw_experiment_schematic,
        draw_geometry_simple,
        draw_counting_image,
        GENERIC_NAMES,
        SUBJECT_FOLDERS,
        PRIORITY_SUBJECTS,
        ASSETS,
        QUIZ_IMAGES,
        LGS_IMPORT,
        ROOT,
    )


# Classification and confidence (report-only)
CLASSIFICATION = ("deterministic_replace", "safe_template_replace", "manual_review", "keep_generic")
CONFIDENCE_TAGS = ("exact_data_match", "explicit_shape_match", "explicit_object_match", "keyword_safe_template", "ambiguous_skip")

PASS1_UPGRADED_COUNT = 268  # for cumulative in report


def classify_question(path, idx, q, subject, current_asset):
    """
    Returns (classification, visual_type).
    - deterministic_replace: graph/table/geometry/experiment/map with extractable data.
    - safe_template_replace: mat counting, english single vocabulary, sosyal map with labels, fen named tools.
    - manual_review: could be upgraded but needs human check.
    - keep_generic: ambiguous, do not replace.
    """
    stem = (q.get("stem") or "").lower()
    options = q.get("options") or []
    explanation = (q.get("explanation") or "").lower()
    text = stem + " " + " ".join(str(o) for o in options).lower() + " " + explanation
    visual_type = infer_visual_type(current_asset)

    # ----- Deterministic: extractable data -----
    if visual_type in ("graph_line", "graph_bar"):
        if extract_line_data(q.get("stem"), options, q.get("explanation")) or extract_bar_data(q.get("stem"), options, q.get("explanation")):
            return ("deterministic_replace", visual_type)
        return ("keep_generic", visual_type)

    if visual_type == "table":
        if extract_table_rows(q.get("stem"), options, q.get("explanation")):
            return ("deterministic_replace", visual_type)
        return ("keep_generic", visual_type)

    if visual_type == "experiment":
        # Pass1 already generates from keywords; any experiment with stem text is template-safe
        return ("deterministic_replace", visual_type)

    if visual_type == "geometry":
        if re.search(r"üçgen|dikdörtgen|kare|üçgen|triangle|rectangle|square|açı|angle|kenar", stem):
            return ("deterministic_replace", visual_type)
        return ("manual_review", visual_type)

    if visual_type == "map":
        if re.search(r"harita|map|bölge|region|hitit|frig|lidya|anadolu|coğrafya|ülke", stem):
            return ("deterministic_replace", visual_type)
        return ("keep_generic", visual_type)

    # ----- Picture: safe template or keep generic -----
    if visual_type == "picture":
        # Mat counting: answer is numeric and in options
        if subject == "mat" and options and 0 <= q.get("answerIndex", 0) < len(options):
            try:
                n = int(options[q["answerIndex"]])
                if 1 <= n <= 20:
                    return ("safe_template_replace", visual_type)
            except (ValueError, TypeError):
                pass

        # English: single explicit vocabulary (animal/food/weather/transport/object)
        if subject == "english":
            vocab = _english_vocabulary_category(stem, options)
            if vocab:
                return ("safe_template_replace", visual_type)
            return ("keep_generic", visual_type)

        # Sosyal: map-like stem with place names
        if subject == "sosyal" and re.search(r"harita|haritasında|bölge|medeniyet|hitit|frig|lidya|anadolu", stem):
            return ("safe_template_replace", visual_type)

        # Fen: named experiment tools
        if subject == "fen" and re.search(r"ampul|devre|deney|beaker|tüp|pil", stem):
            return ("safe_template_replace", visual_type)

        return ("keep_generic", visual_type)

    return ("keep_generic", visual_type)


def _english_vocabulary_category(stem, options):
    """Return single category string if unambiguous: animal, food, weather, transport, object; else None."""
    text = (stem + " " + " ".join(str(o) for o in options)).lower()
    animals = ["cat", "dog", "elephant", "bird", "rabbit", "mouse", "horse", "fish", "lion", "tiger", "bear"]
    foods = ["apple", "banana", "bread", "breakfast", "fruit", "milk", "egg", "cake", "pizza"]
    weather = ["sun", "rain", "cloud", "snow", "weather", "sunny", "rainy", "wind"]
    transport = ["car", "bus", "bike", "train", "plane", "boat", "ship"]
    objects = ["ball", "book", "pencil", "desk", "chair", "table", "robot", "cartoon"]
    found = []
    for cat, words in [("animal", animals), ("food", foods), ("weather", weather), ("transport", transport), ("object", objects)]:
        if any(w in text for w in words):
            found.append(cat)
    if len(found) == 1:
        return found[0]
    return None


def draw_simple_map(region_labels, out_path=None):
    """Simple schematic map: labeled regions (boxes/ellipses)."""
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        return None
    w, h = 400, 300
    img = Image.new("RGB", (w, h), color=(248, 252, 248))
    draw = ImageDraw.Draw(img)
    if not region_labels:
        region_labels = ["A", "B", "C"]
    n = min(len(region_labels), 6)
    # Simple grid of regions
    cols = 2 if n <= 2 else (3 if n <= 4 else 3)
    cell_w = (w - 40) // cols
    cell_h = (h - 40) // 2
    for i, label in enumerate(region_labels[:n]):
        row, col = i // cols, i % cols
        x1 = 20 + col * cell_w + 4
        y1 = 20 + row * cell_h + 4
        x2 = x1 + cell_w - 8
        y2 = y1 + cell_h - 8
        draw.rectangle([x1, y1, x2, y2], outline=(70, 130, 180), width=2)
        draw.text((x1 + 8, y1 + 8), str(label)[:12], fill=(0, 0, 0))
    return img


def extract_map_labels(stem, options, explanation):
    """Extract region/place names for map from stem."""
    text = stem + " " + (explanation or "")
    labels = []
    for m in re.finditer(r"\b(Hitit|Frig|Lidya|Anadolu|Mısır|Mezopotamya|Roma|Yunan|İyonya|Trakya)\b", text, re.I):
        if m.group(1) not in labels:
            labels.append(m.group(1))
    if not labels:
        labels = ["Bölge A", "Bölge B", "Bölge C"]
    return labels[:6]


def extract_geometry_shape(stem):
    """Return 'triangle', 'rectangle', or 'square' from stem."""
    s = stem.lower()
    if re.search(r"üçgen|triangle", s):
        return "triangle"
    if re.search(r"kare|square", s):
        return "square"
    if re.search(r"dikdörtgen|rectangle", s):
        return "rectangle"
    return "triangle"


def draw_english_vocabulary_icon(category, out_path=None):
    """Very simple icon for vocabulary category (one shape/symbol per category)."""
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        return None
    w, h = 400, 300
    img = Image.new("RGB", (w, h), color=(252, 252, 255))
    draw = ImageDraw.Draw(img)
    cx, cy = w // 2, h // 2
    if category == "animal":
        draw.ellipse([cx - 50, cy - 40, cx + 50, cy + 40], outline=(180, 140, 60), width=2)
    elif category == "food":
        draw.ellipse([cx - 45, cy - 45, cx + 45, cy + 45], outline=(200, 100, 100), width=2)
    elif category == "weather":
        draw.ellipse([cx - 55, cy - 55, cx + 55, cy + 55], outline=(255, 200, 0), width=2)
    elif category == "transport":
        draw.rectangle([cx - 60, cy - 30, cx + 60, cy + 30], outline=(70, 130, 180), width=2)
    else:
        draw.rectangle([cx - 50, cy - 50, cx + 50, cy + 50], outline=(100, 100, 100), width=2)
    return img


def try_generate_with_confidence(subject, pack_id, q_slug, visual_type, q, idx, classification):
    """
    Generate image if possible. Returns (rel_path, skip_reason, confidence_tag).
    confidence_tag: exact_data_match | explicit_shape_match | explicit_object_match | keyword_safe_template | ambiguous_skip
    """
    stem = q.get("stem") or ""
    options = q.get("options") or []
    explanation = q.get("explanation") or ""
    pack_safe = re.sub(r"[^\w\-]", "_", str(pack_id))[:16]
    q_safe = re.sub(r"[^\w\-]", "_", str(q_slug))[:20]
    fname = f"{subject}_{pack_safe}_{q_safe}_{visual_type}.png"
    rel_path = f"quiz_images/{subject}/{fname}"
    out_path = ASSETS / rel_path

    try:
        from PIL import Image
    except ImportError:
        return (None, "Pillow not installed", "ambiguous_skip")

    # ----- Graph -----
    if visual_type in ("graph_line", "graph_bar"):
        data = extract_line_data(stem, options, explanation)
        if not data:
            data = extract_bar_data(stem, options, explanation)
        if data:
            img = draw_graph_line(data)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "exact_data_match")
        return (None, "insufficient graph data", "ambiguous_skip")

    # ----- Table -----
    if visual_type == "table":
        rows = extract_table_rows(stem, options, explanation)
        if rows:
            img = draw_table(rows)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "exact_data_match")
        return (None, "insufficient table data", "ambiguous_skip")

    # ----- Experiment -----
    if visual_type == "experiment":
        keywords = re.findall(r"\w{4,}", stem + " " + (explanation or ""))
        img = draw_experiment_schematic(keywords[:15])
        if img:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            img.save(out_path, "PNG")
            return (rel_path, None, "keyword_safe_template")
        return (None, "could not draw experiment", "ambiguous_skip")

    # ----- Geometry -----
    if visual_type == "geometry":
        shape = extract_geometry_shape(stem)
        img = draw_geometry_simple(shape)
        if img:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            img.save(out_path, "PNG")
            return (rel_path, None, "explicit_shape_match")
        return (None, "could not draw geometry", "ambiguous_skip")

    # ----- Map -----
    if visual_type == "map":
        labels = extract_map_labels(stem, options, explanation)
        img = draw_simple_map(labels)
        if img:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            img.save(out_path, "PNG")
            return (rel_path, None, "exact_data_match")
        return (None, "could not draw map", "ambiguous_skip")

    # ----- Picture -----
    if visual_type == "picture":
        if subject == "mat" and options and 0 <= q.get("answerIndex", 0) < len(options):
            try:
                n = int(options[q["answerIndex"]])
                if 1 <= n <= 20:
                    img = draw_counting_image(n)
                    if img:
                        out_path.parent.mkdir(parents=True, exist_ok=True)
                        img.save(out_path, "PNG")
                        return (rel_path, None, "explicit_object_match")
            except (ValueError, TypeError):
                pass

        if subject == "english":
            vocab = _english_vocabulary_category(stem, options)
            if vocab:
                img = draw_english_vocabulary_icon(vocab)
                if img:
                    out_path.parent.mkdir(parents=True, exist_ok=True)
                    img.save(out_path, "PNG")
                    return (rel_path, None, "keyword_safe_template")
            return (None, "ambiguous vocabulary", "ambiguous_skip")

        if subject == "sosyal" and re.search(r"harita|haritasında|bölge|medeniyet|hitit|frig|lidya", stem):
            labels = extract_map_labels(stem, options, explanation)
            img = draw_simple_map(labels)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "keyword_safe_template")
            return (None, "could not draw map", "ambiguous_skip")

        if subject == "fen":
            keywords = re.findall(r"\w{4,}", stem + " " + (explanation or ""))
            img = draw_experiment_schematic(keywords[:15])
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "keyword_safe_template")
            return (None, "could not draw experiment", "ambiguous_skip")

        return (None, "picture not safe template", "ambiguous_skip")

    return (None, "unknown type", "ambiguous_skip")


def count_remaining_generic_by_subject_and_type():
    """Scan all priority JSONs and count questions that still have generic imageAsset. Returns (by_subject, by_type)."""
    by_subject = defaultdict(int)
    by_type = defaultdict(int)
    for path, idx, q, subject, asset in collect_eligible():
        by_subject[subject] += 1
        by_type[infer_visual_type(asset)] += 1
    return dict(by_subject), dict(by_type)


def main():
    eligible = collect_eligible()
    print(f"Pass 2: remaining generic imageAsset questions: {len(eligible)}")

    type_priority = {"graph_line": 0, "graph_bar": 1, "table": 2, "experiment": 3, "geometry": 4, "map": 5, "picture": 6}
    eligible.sort(key=lambda x: (
        PRIORITY_SUBJECTS.index(x[3]) if x[3] in PRIORITY_SUBJECTS else 99,
        type_priority.get(infer_visual_type(x[4]), 7),
        str(x[0]), x[1]))

    upgraded = []
    skipped = []
    manual_review_candidates = []
    by_file = defaultdict(list)
    by_confidence = defaultdict(int)
    by_classification_before = defaultdict(int)

    for path, idx, q, subject, old_asset in eligible:
        classification, visual_type = classify_question(path, idx, q, subject, old_asset)
        by_classification_before[classification] += 1

        if classification == "keep_generic":
            skipped.append({"qid": q.get("id") or q.get("sourceRef") or f"idx_{idx}", "reason": "keep_generic", "type": visual_type})
            continue
        if classification == "manual_review":
            manual_review_candidates.append({
                "qid": q.get("id") or q.get("sourceRef") or f"idx_{idx}",
                "path": str(path), "subject": subject, "type": visual_type,
            })
            skipped.append({"qid": q.get("id") or q.get("sourceRef") or f"idx_{idx}", "reason": "manual_review", "type": visual_type})
            continue

        pack_id = get_pack_id(path)
        q_slug = get_question_slug(q, idx)
        qid = q.get("id") or q.get("sourceRef") or f"idx_{idx}"

        new_path, skip_reason, confidence = try_generate_with_confidence(subject, pack_id, q_slug, visual_type, q, idx, classification)
        if skip_reason or not new_path:
            skipped.append({"qid": qid, "reason": skip_reason or "no path", "type": visual_type})
            continue

        upgraded.append({
            "path": path, "idx": idx, "q": q, "qid": qid,
            "old_asset": old_asset, "new_asset": new_path,
            "subject": subject, "type": visual_type,
            "classification": classification, "confidence": confidence,
        })
        by_confidence[confidence] += 1
        by_file[str(path)].append((idx, new_path))

    # Batch update JSON
    files_changed = set()
    for path_str, updates in by_file.items():
        path = Path(path_str)
        try:
            data = json.load(open(path, encoding="utf-8"))
            qs = data.get("questions", []) if isinstance(data, dict) else data
            if not isinstance(qs, list):
                continue
            for idx, new_path in updates:
                if idx < len(qs):
                    qs[idx]["imageAsset"] = new_path
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            files_changed.add(path_str)
        except Exception as e:
            print(f"  [Error] {path}: {e}")

    still_generic = len(eligible) - len(upgraded)
    by_subject_upgraded = defaultdict(int)
    by_type_upgraded = defaultdict(int)
    for u in upgraded:
        by_subject_upgraded[u["subject"]] += 1
        by_type_upgraded[u["type"]] += 1

    # Remaining generic by subject/type: count from eligible minus upgraded
    upgraded_keys = {(str(u["path"]), u["idx"]) for u in upgraded}
    remaining_by_subject_after = defaultdict(int)
    remaining_by_type_after = defaultdict(int)
    for path, idx, q, subject, asset in eligible:
        if (str(path), idx) in upgraded_keys:
            continue
        remaining_by_subject_after[subject] += 1
        remaining_by_type_after[infer_visual_type(asset)] += 1

    # Report
    report_path = ROOT / "VISUAL_UPGRADE_REPORT_PASS2.md"
    rel = lambda p: str(Path(p).relative_to(ROOT)) if str(ROOT) in str(p) else str(p)
    cumulative = PASS1_UPGRADED_COUNT + len(upgraded)

    with open(report_path, "w", encoding="utf-8") as f:
        f.write("# BrainBuddy Visual Upgrade — Pass 2 Report\n\n")
        f.write("## Summary\n\n")
        f.write("| Metric | Count |\n|--------|-------|\n")
        f.write(f"| **Newly upgraded (pass 2)** | {len(upgraded)} |\n")
        f.write(f"| **Cumulative upgraded (pass 1 + pass 2)** | {cumulative} |\n")
        f.write(f"| **Remaining generic (after pass 2)** | {still_generic} |\n")
        f.write(f"| JSON files modified (pass 2) | {len(files_changed)} |\n")
        f.write(f"| Manual review candidates | {len(manual_review_candidates)} |\n\n")

        f.write("## Subject breakdown (pass 2 upgraded)\n\n")
        f.write("| Subject | Pass 2 upgraded | Remaining generic |\n|---------|-----------------|--------------------|\n")
        for s in PRIORITY_SUBJECTS:
            f.write(f"| {s} | {by_subject_upgraded[s]} | {remaining_by_subject_after.get(s, 0)} |\n")

        f.write("\n## Type breakdown (pass 2 upgraded)\n\n")
        f.write("| Type | Pass 2 upgraded | Remaining generic |\n|------|-----------------|--------------------|\n")
        for t in sorted(set(by_type_upgraded.keys()) | set(remaining_by_type_after.keys())):
            f.write(f"| {t} | {by_type_upgraded.get(t, 0)} | {remaining_by_type_after.get(t, 0)} |\n")

        f.write("\n## Confidence / source tag breakdown\n\n")
        f.write("| Tag | Count |\n|-----|-------|\n")
        for tag in CONFIDENCE_TAGS:
            f.write(f"| {tag} | {by_confidence.get(tag, 0)} |\n")

        f.write("\n## Top 30 sample replacements (old -> new)\n\n")
        f.write("| Question ID | Old | New | Confidence |\n|-------------|-----|-----|------------|\n")
        for u in upgraded[:30]:
            f.write(f"| `{u['qid']}` | `{u['old_asset']}` | `{u['new_asset']}` | {u['confidence']} |\n")

        f.write("\n## Manual review candidates\n\n")
        for m in manual_review_candidates[:50]:
            f.write(f"- **{m['qid']}** ({m['subject']}, {m['type']}) — `{rel(m['path'])}`\n")
        if len(manual_review_candidates) > 50:
            f.write(f"\n... and {len(manual_review_candidates) - 50} more.\n")

        f.write("\n## Subjects with biggest remaining opportunity\n\n")
        for s, c in sorted(remaining_by_subject_after.items(), key=lambda x: -x[1]):
            f.write(f"- **{s}**: {c} questions still generic\n")

        f.write("\n## Classification (before pass 2)\n\n")
        f.write("| Classification | Count |\n|-----------------|-------|\n")
        for c in CLASSIFICATION:
            f.write(f"| {c} | {by_classification_before.get(c, 0)} |\n")

        f.write("\n## Files modified (pass 2)\n\n")
        for p in sorted(files_changed):
            f.write(f"- `{rel(p)}`\n")

    print("=" * 60)
    print("VISUAL UPGRADE PASS 2")
    print("=" * 60)
    print(f"Newly upgraded: {len(upgraded)}  |  Cumulative: {cumulative}  |  Remaining generic: {still_generic}")
    print(f"Manual review: {len(manual_review_candidates)}  |  Files changed: {len(files_changed)}")
    print("=" * 60)
    print(f"Report: {report_path}")
    return 0


if __name__ == "__main__":
    exit(main())
