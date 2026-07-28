#!/usr/bin/env python3
"""
EDUmio Final Visual Completion Pass — eliminate remaining generic visual assets.
Scope: fen, english, sosyal. Prefer representative visual over generic placeholder.
"""
import json
import re
from pathlib import Path
from collections import defaultdict

try:
    from upgrade_visual_assets import (
        collect_eligible,
        is_generic_asset,
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
        ASSETS,
        ROOT,
        SUBJECT_FOLDERS,
    )
except ImportError:
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from upgrade_visual_assets import (
        collect_eligible,
        is_generic_asset,
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
        ASSETS,
        ROOT,
        SUBJECT_FOLDERS,
    )

try:
    from upgrade_visual_assets_pass2 import (
        draw_simple_map,
        extract_map_labels,
        extract_geometry_shape,
        draw_english_vocabulary_icon,
        _english_vocabulary_category,
    )
except ImportError:
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from upgrade_visual_assets_pass2 import (
        draw_simple_map,
        extract_map_labels,
        extract_geometry_shape,
        draw_english_vocabulary_icon,
        _english_vocabulary_category,
    )

FINAL_TARGET_SUBJECTS = ["fen", "english", "sosyal"]
CUMULATIVE_BEFORE_FINAL = 340

VISUAL_REFERENCE_PATTERN = re.compile(
    r"grafiğe\s+göre|tabloya\s+göre|şekle\s+göre|haritaya\s+göre|görsele\s+göre"
    r"|aşağıdaki\s+düzenek|verilen\s+deney|resme\s+göre|numaralı\s+yerler|yukarıdaki\s+görsel"
    r"|grafiğe\s+bakarak|tabloya\s+bakarak|şekildeki|haritadaki|görselde"
    r"|according\s+to\s+the\s+graph|according\s+to\s+the\s+table|according\s+to\s+the\s+figure"
    r"|given\s+diagram|see\s+the\s+picture",
    re.I,
)

REPORT_TAGS = (
    "exact_data_match", "inferred_axis_match", "inferred_table_match",
    "explicit_shape_match", "inferred_map_match", "explicit_object_match",
    "keyword_safe_template", "representative_graph_match", "representative_table_match",
    "representative_shape_match", "representative_map_match",
    "representative_experiment_match", "representative_picture_match",
    "truly_ambiguous_skip",
)


def collect_eligible_final():
    out = []
    for path, idx, q, subject, asset in collect_eligible():
        if subject not in FINAL_TARGET_SUBJECTS:
            continue
        out.append((path, idx, q, subject, asset))
    return out


def has_visual_reference(text):
    return bool(VISUAL_REFERENCE_PATTERN.search(text)) if text else False


def has_graph_reference(text):
    t = (text or "").lower()
    return bool(re.search(r"grafik|graph|artar|azalır|değişim|eğri|zaman\s*[-–]\s*|zamana\s+göre|en\s+fazla|en\s+az|karşılaştırma", t))


def has_table_reference(text):
    t = (text or "").lower()
    return bool(re.search(r"tablo|table|veriler|değerler", t))


def has_shape_reference(text):
    t = (text or "").lower()
    return bool(re.search(r"şekil|figure|üçgen|kare|dikdörtgen|çember|daire|açı|paralel|köşegen|kenar|çevre|alan", t))


def has_experiment_reference(text):
    t = (text or "").lower()
    return bool(re.search(r"düzenek|deney|kap|termometre|özdeş\s+kaplar|ışık\s+kaynağı|mıknatıs|elektrik\s+devresi|ampul|pil|basınç|sıcaklık|derinlik|madde\s+halleri|genleşme|çözünme", t))


def has_map_reference(text):
    t = (text or "").lower()
    return bool(re.search(r"harita|bölge|yön|kuzey|güney|doğu|batı|il\b|ülke|deniz|boğaz|komşu|numaralı|hitit|frig|lidya|anadolu", t))


def classify_final(path, idx, q, subject, current_asset):
    stem = (q.get("stem") or "").lower()
    options = q.get("options") or []
    explanation = (q.get("explanation") or "").lower()
    text = stem + " " + " ".join(str(o) for o in options).lower() + " " + explanation
    visual_type = infer_visual_type(current_asset)
    has_ref = has_visual_reference(text)

    if visual_type in ("graph_line", "graph_bar"):
        if extract_line_data(q.get("stem"), options, q.get("explanation")) or extract_bar_data(q.get("stem"), options, q.get("explanation")):
            return "exact_replace"
        if has_ref or has_graph_reference(text):
            return "representative_replace"
        return "keep_generic"

    if visual_type == "table":
        if extract_table_rows(q.get("stem"), options, q.get("explanation")):
            return "exact_replace"
        if has_ref or has_table_reference(text):
            return "representative_replace"
        return "keep_generic"

    if visual_type == "geometry":
        if has_shape_reference(stem) or has_ref:
            return "representative_replace"  # we always draw a shape
        return "keep_generic"

    if visual_type == "experiment":
        if has_ref or has_experiment_reference(text):
            return "representative_replace"
        return "keep_generic"

    if visual_type == "map":
        if has_ref or has_map_reference(text):
            return "representative_replace"
        return "keep_generic"

    if visual_type == "picture":
        if subject == "english":
            if _english_vocabulary_category(stem, options) or has_ref:
                return "representative_replace"
            return "keep_generic"
        if subject == "sosyal":
            if has_map_reference(stem) or has_ref:
                return "representative_replace"
            return "keep_generic"
        if subject == "fen":
            if has_experiment_reference(stem) or has_ref:
                return "representative_replace"
            return "keep_generic"

    return "keep_generic"


def representative_graph_data(stem, options, explanation):
    """Infer trend and return (x_label, y_label, points) or (labels, values) for bar."""
    text = " ".join([str(stem or ""), str(explanation or "")]).lower()
    nums = extract_numbers(stem + " " + (explanation or ""))
    if len(nums) >= 2 and len(nums) <= 8:
        return ("X", "Y", list(enumerate([int(n) if n == int(n) else n for n in nums[:8]])))
    if "azalır" in text or "azalıyor" in text or "düşer" in text or "decreasing" in text:
        return ("X", "Y", [(0, 70), (1, 55), (2, 40), (3, 25)])
    if "en fazla" in text or "en yüksek" in text or "karşılaştırma" in text:
        parts = re.split(r"[,;]", stem + " " + (explanation or ""))
        labels, vals = [], []
        for i, p in enumerate(parts[:6]):
            n = extract_numbers(p)
            label = re.sub(r"\d+[.,]?\d*", "", p).strip()[:10] or f"#{i+1}"
            labels.append(label)
            vals.append(n[0] if n else (30 - i * 5))
        if len(labels) >= 2 and len(vals) >= 2:
            return (labels, vals)
    return ("X", "Y", [(0, 15), (1, 35), (2, 55), (3, 75)])


def representative_table_rows(stem, options, explanation):
    """Return a small representative table (list of rows, each row list of cell values)."""
    text = stem + " " + (explanation or "")
    nums = extract_numbers(text)
    if len(nums) >= 4 and len(nums) <= 12:
        cols = 2 if len(nums) <= 6 else 3
        return [nums[i:i+cols] for i in range(0, min(len(nums), 9), cols)][:4]
    labels = re.findall(r"\b([A-ZÇĞİÖŞÜa-zçğıöşü]{2,})\s*[:=]", text)
    if not labels:
        labels = ["A", "B", "C"]
    n = min(6, max(3, len(labels)))
    return [[labels[i % len(labels)][:4], 10 + i * 10] for i in range(n)]


def try_generate_final(subject, pack_id, q_slug, visual_type, q, classification):
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
        return (None, "Pillow not installed", "truly_ambiguous_skip")

    if visual_type in ("graph_line", "graph_bar"):
        data = extract_line_data(stem, options, explanation) or extract_bar_data(stem, options, explanation)
        if data:
            img = draw_graph_line(data)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "exact_data_match")
        data = representative_graph_data(stem, options, explanation)
        img = draw_graph_line(data)
        if img:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            img.save(out_path, "PNG")
            return (rel_path, None, "representative_graph_match")
        return (None, "could not draw graph", "truly_ambiguous_skip")

    if visual_type == "table":
        rows = extract_table_rows(stem, options, explanation)
        if rows:
            img = draw_table(rows)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "inferred_table_match" if len(extract_numbers(stem + " " + explanation)) >= 4 else "exact_data_match")
        rows = representative_table_rows(stem, options, explanation)
        if rows:
            img = draw_table(rows)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "representative_table_match")
        return (None, "could not draw table", "truly_ambiguous_skip")

    if visual_type == "experiment":
        keywords = re.findall(r"\w{4,}", stem + " " + (explanation or ""))
        img = draw_experiment_schematic(keywords[:15])
        if img:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            img.save(out_path, "PNG")
            return (rel_path, None, "representative_experiment_match")
        return (None, "could not draw experiment", "truly_ambiguous_skip")

    if visual_type == "geometry":
        shape = extract_geometry_shape(stem) or "triangle"
        img = draw_geometry_simple(shape)
        if img:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            img.save(out_path, "PNG")
            return (rel_path, None, "explicit_shape_match" if has_shape_reference(stem) else "representative_shape_match")
        return (None, "could not draw geometry", "truly_ambiguous_skip")

    if visual_type == "map":
        labels = extract_map_labels(stem, options, explanation)
        if not labels:
            labels = ["Bölge A", "Bölge B", "Bölge C"]
        img = draw_simple_map(labels)
        if img:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            img.save(out_path, "PNG")
            return (rel_path, None, "inferred_map_match" if extract_map_labels(stem, options, explanation) else "representative_map_match")
        return (None, "could not draw map", "truly_ambiguous_skip")

    if visual_type == "picture":
        if subject == "english":
            vocab = _english_vocabulary_category(stem, options) or "object"
            img = draw_english_vocabulary_icon(vocab)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "explicit_object_match" if _english_vocabulary_category(stem, options) else "representative_picture_match")
            return (None, "could not draw picture", "truly_ambiguous_skip")
        if subject == "sosyal":
            labels = extract_map_labels(stem, options, explanation) or ["A", "B", "C"]
            img = draw_simple_map(labels)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "representative_map_match")
            return (None, "could not draw map", "truly_ambiguous_skip")
        if subject == "fen":
            keywords = re.findall(r"\w{4,}", stem + " " + (explanation or ""))
            img = draw_experiment_schematic(keywords[:15])
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "representative_experiment_match")
            return (None, "could not draw experiment", "truly_ambiguous_skip")

    return (None, "unknown type", "truly_ambiguous_skip")


def main():
    eligible = collect_eligible_final()
    print(f"Final pass: remaining generic (fen, english, sosyal): {len(eligible)}")

    type_priority = {"graph_line": 0, "graph_bar": 1, "table": 2, "experiment": 3, "geometry": 4, "map": 5, "picture": 6}
    eligible.sort(key=lambda x: (
        FINAL_TARGET_SUBJECTS.index(x[3]) if x[3] in FINAL_TARGET_SUBJECTS else 99,
        type_priority.get(infer_visual_type(x[4]), 7), str(x[0]), x[1]))

    upgraded = []
    keep_generic_list = []
    by_file = defaultdict(list)
    by_tag = defaultdict(int)
    by_subject_up = defaultdict(int)
    by_type_up = defaultdict(int)
    by_classification = defaultdict(int)

    for path, idx, q, subject, old_asset in eligible:
        classification = classify_final(path, idx, q, subject, old_asset)
        by_classification[classification] += 1

        if classification == "keep_generic":
            qid = q.get("id") or q.get("sourceRef") or f"idx_{idx}"
            keep_generic_list.append({"qid": qid, "subject": subject, "type": infer_visual_type(old_asset)})
            continue

        pack_id = get_pack_id(path)
        q_slug = get_question_slug(q, idx)
        qid = q.get("id") or q.get("sourceRef") or f"idx_{idx}"
        new_path, skip_reason, tag = try_generate_final(subject, pack_id, q_slug, infer_visual_type(old_asset), q, classification)
        if skip_reason or not new_path:
            keep_generic_list.append({"qid": qid, "subject": subject, "type": infer_visual_type(old_asset), "reason": skip_reason})
            continue

        upgraded.append({
            "path": path, "idx": idx, "qid": qid, "old_asset": old_asset, "new_asset": new_path,
            "subject": subject, "type": infer_visual_type(old_asset), "tag": tag,
        })
        by_tag[tag] += 1
        by_subject_up[subject] += 1
        by_type_up[infer_visual_type(old_asset)] += 1
        by_file[str(path)].append((idx, new_path))

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
        except Exception as e:
            print(f"  [Error] {path}: {e}")

    still_generic = len(keep_generic_list)
    cumulative = CUMULATIVE_BEFORE_FINAL + len(upgraded)
    upgraded_keys = {(str(u["path"]), u["idx"]) for u in upgraded}
    remaining_by_subject = defaultdict(int)
    remaining_by_type = defaultdict(int)
    for path, idx, q, subject, asset in eligible:
        if (str(path), idx) in upgraded_keys:
            continue
        remaining_by_subject[subject] += 1
        remaining_by_type[infer_visual_type(asset)] += 1

    rel = lambda p: str(Path(p).relative_to(ROOT)) if str(ROOT) in str(p) else str(p)

    with open(ROOT / "VISUAL_UPGRADE_REPORT_FINAL.md", "w", encoding="utf-8") as f:
        f.write("# EDUmio Visual Upgrade — Final Completion Pass Report\n\n")
        f.write("## 1. Counts\n\n")
        f.write("| Metric | Count |\n|--------|-------|\n")
        f.write(f"| Newly upgraded (final pass) | {len(upgraded)} |\n")
        f.write(f"| Cumulative upgraded total | {cumulative} |\n")
        f.write(f"| Remaining generic | {still_generic} |\n\n")
        f.write("## 2. Subject breakdown\n\n")
        f.write("| Subject | Final pass upgraded | Remaining generic |\n|---------|---------------------|--------------------|\n")
        for s in FINAL_TARGET_SUBJECTS:
            f.write(f"| {s} | {by_subject_up[s]} | {remaining_by_subject.get(s, 0)} |\n")
        f.write("\n## 3. Type breakdown\n\n")
        f.write("| Type | Upgraded | Remaining generic |\n|------|----------|--------------------|\n")
        for t in sorted(set(by_type_up.keys()) | set(remaining_by_type.keys())):
            f.write(f"| {t} | {by_type_up.get(t, 0)} | {remaining_by_type.get(t, 0)} |\n")
        f.write("\n## 4. Tag breakdown\n\n")
        f.write("| Tag | Count |\n|-----|-------|\n")
        for tag in REPORT_TAGS:
            f.write(f"| {tag} | {by_tag.get(tag, 0)} |\n")
        f.write("\n## 5. Top 50 replacements (question id, old -> new, tag)\n\n")
        f.write("| Question ID | Old | New | Tag |\n|-------------|-----|-----|-----|\n")
        for u in upgraded[:50]:
            f.write(f"| `{u['qid']}` | `{u['old_asset']}` | `{u['new_asset']}` | {u['tag']} |\n")
        f.write("\n## 6. Remaining generic grouped by reason\n\n")
        by_reason = defaultdict(list)
        for k in keep_generic_list:
            reason = k.get("reason", "keep_generic (no visual reference or unsafe)")
            by_reason[reason].append(k)
        for reason, items in sorted(by_reason.items(), key=lambda x: -len(x[1])):
            f.write(f"### {reason}\n\n")
            for it in items[:20]:
                f.write(f"- {it['qid']} ({it['subject']}, {it['type']})\n")
            if len(items) > 20:
                f.write(f"- ... and {len(items) - 20} more.\n\n")
        f.write("\n## 7. Explicit list of questions still left generic\n\n")
        for k in keep_generic_list:
            f.write(f"- **{k['qid']}** | {k['subject']} | {k['type']}\n")

    print("=" * 60)
    print("FINAL PASS: upgraded %d | cumulative %d | remaining generic %d" % (len(upgraded), cumulative, still_generic))
    print("=" * 60)
    return 0


if __name__ == "__main__":
    exit(main())
