#!/usr/bin/env python3
"""EDUmio visual upgrade pass 3: hard-case recovery only. fen, english, sosyal."""
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

PASS3_TARGET_SUBJECTS = ["fen", "english", "sosyal"]
CUMULATIVE_BEFORE_PASS3 = 340
REPORT_TAGS = (
    "exact_data_match", "inferred_axis_match", "inferred_table_match",
    "explicit_shape_match", "inferred_map_match", "explicit_object_match", "ambiguous_skip"
)
CAUSES = (
    "no extractable numbers", "ambiguous vocabulary", "missing labels",
    "weak map signal", "multi-object ambiguity", "abstract verbal-only visual reference"
)


def collect_eligible_pass3():
    out = []
    for path, idx, q, subject, asset in collect_eligible():
        if subject not in PASS3_TARGET_SUBJECTS:
            continue
        out.append((path, idx, q, subject, asset))
    return out


def has_axis_phrase(text):
    t = text.lower()
    return bool(re.search(r"zamana\s+göre|derinlik|basınç|basinc|sıcaklık|hız-zaman|yükseklik-sıcaklık", t))


def has_geometry_keyword(text):
    t = text.lower()
    return bool(re.search(r"üçgen|kare|dikdörtgen|çember|daire|açı|paralel|köşegen|yükseklik|kenar|çevre|alan", t))


def has_map_signal(text):
    t = text.lower()
    return bool(re.search(r"harita|bölge|yön|kuzey|güney|doğu|batı|il\b|ülke|deniz|boğaz|komşu|numaralı", t))


def classify_pass3(path, idx, q, subject, current_asset):
    stem = (q.get("stem") or "").lower()
    options = q.get("options") or []
    explanation = (q.get("explanation") or "").lower()
    text = stem + " " + " ".join(str(o) for o in options).lower() + " " + explanation
    visual_type = infer_visual_type(current_asset)

    if visual_type in ("graph_line", "graph_bar"):
        line_data = extract_line_data(q.get("stem"), options, q.get("explanation"))
        bar_data = extract_bar_data(q.get("stem"), options, q.get("explanation"))
        if line_data or bar_data:
            return ("deterministic_replace", visual_type, None)
        if has_axis_phrase(text) and len(extract_numbers(text)) >= 2:
            return ("high_confidence_extract", visual_type, None)
        if has_axis_phrase(text) or re.search(r"artar|azalır|değişim|en fazla|en az|karşılaştırma", text):
            return ("manual_review_candidate", visual_type, "no extractable numbers")
        return ("keep_generic", visual_type, "no extractable numbers")

    if visual_type == "table":
        rows = extract_table_rows(q.get("stem"), options, q.get("explanation"))
        if rows:
            return ("deterministic_replace", visual_type, None)
        if len(extract_numbers(text)) >= 4:
            return ("high_confidence_extract", visual_type, None)
        return ("keep_generic", visual_type, "missing labels")

    if visual_type == "experiment":
        return ("deterministic_replace", visual_type, None)

    if visual_type == "geometry":
        if has_geometry_keyword(stem):
            return ("deterministic_replace", visual_type, None)
        return ("manual_review_candidate", visual_type, "missing labels")

    if visual_type == "map":
        labels = extract_map_labels(q.get("stem"), options, q.get("explanation"))
        if labels and has_map_signal(stem):
            return ("high_confidence_extract", visual_type, None)
        if has_map_signal(stem):
            return ("manual_review_candidate", visual_type, "weak map signal")
        return ("keep_generic", visual_type, "weak map signal")

    if visual_type == "picture":
        if subject == "english":
            vocab = _english_vocabulary_category(stem, options)
            if vocab:
                return ("deterministic_replace", visual_type, None)
            return ("keep_generic", visual_type, "ambiguous vocabulary")
        if subject == "sosyal" and has_map_signal(stem):
            labels = extract_map_labels(q.get("stem"), options, q.get("explanation"))
            if labels:
                return ("high_confidence_extract", visual_type, None)
            return ("manual_review_candidate", visual_type, "weak map signal")
        if subject == "fen" and re.search(r"ampul|devre|deney|beaker|tüp|pil", stem):
            return ("deterministic_replace", visual_type, None)
        return ("keep_generic", visual_type, "abstract verbal-only visual reference")

    return ("keep_generic", visual_type, "abstract verbal-only visual reference")


def try_generate_pass3(subject, pack_id, q_slug, visual_type, q, classification):
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

    if visual_type in ("graph_line", "graph_bar"):
        data = extract_line_data(stem, options, explanation) or extract_bar_data(stem, options, explanation)
        if data:
            img = draw_graph_line(data)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                tag = "inferred_axis_match" if has_axis_phrase(stem + " " + (explanation or "")) and not extract_line_data(stem, options, explanation) and extract_bar_data(stem, options, explanation) else "exact_data_match"
                return (rel_path, None, tag)
        return (None, "insufficient graph data", "ambiguous_skip")

    if visual_type == "table":
        rows = extract_table_rows(stem, options, explanation)
        if rows:
            img = draw_table(rows)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "inferred_table_match" if len(extract_numbers(stem + " " + (explanation or ""))) >= 4 else "exact_data_match")
        return (None, "insufficient table data", "ambiguous_skip")

    if visual_type == "experiment":
        keywords = re.findall(r"\w{4,}", stem + " " + (explanation or ""))
        img = draw_experiment_schematic(keywords[:15])
        if img:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            img.save(out_path, "PNG")
            return (rel_path, None, "exact_data_match")
        return (None, "could not draw experiment", "ambiguous_skip")

    if visual_type == "geometry":
        shape = extract_geometry_shape(stem)
        img = draw_geometry_simple(shape)
        if img:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            img.save(out_path, "PNG")
            return (rel_path, None, "explicit_shape_match")
        return (None, "could not draw geometry", "ambiguous_skip")

    if visual_type == "map":
        labels = extract_map_labels(stem, options, explanation)
        img = draw_simple_map(labels)
        if img:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            img.save(out_path, "PNG")
            return (rel_path, None, "inferred_map_match")
        return (None, "could not draw map", "ambiguous_skip")

    if visual_type == "picture":
        if subject == "english":
            vocab = _english_vocabulary_category(stem, options)
            if vocab:
                img = draw_english_vocabulary_icon(vocab)
                if img:
                    out_path.parent.mkdir(parents=True, exist_ok=True)
                    img.save(out_path, "PNG")
                    return (rel_path, None, "explicit_object_match")
            return (None, "ambiguous vocabulary", "ambiguous_skip")
        if subject == "sosyal" and has_map_signal(stem):
            labels = extract_map_labels(stem, options, explanation)
            img = draw_simple_map(labels)
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "inferred_map_match")
            return (None, "could not draw map", "ambiguous_skip")
        if subject == "fen":
            keywords = re.findall(r"\w{4,}", stem + " " + (explanation or ""))
            img = draw_experiment_schematic(keywords[:15])
            if img:
                out_path.parent.mkdir(parents=True, exist_ok=True)
                img.save(out_path, "PNG")
                return (rel_path, None, "exact_data_match")
            return (None, "could not draw experiment", "ambiguous_skip")
        return (None, "picture not safe", "ambiguous_skip")

    return (None, "unknown type", "ambiguous_skip")


def main():
    eligible = collect_eligible_pass3()
    print(f"Pass 3: remaining generic (fen, english, sosyal): {len(eligible)}")

    type_priority = {"graph_line": 0, "graph_bar": 1, "table": 2, "experiment": 3, "geometry": 4, "map": 5, "picture": 6}
    eligible.sort(key=lambda x: (
        PASS3_TARGET_SUBJECTS.index(x[3]) if x[3] in PASS3_TARGET_SUBJECTS else 99,
        type_priority.get(infer_visual_type(x[4]), 7), str(x[0]), x[1]))

    upgraded = []
    manual_review = []
    keep_generic_with_cause = defaultdict(list)
    by_file = defaultdict(list)
    by_tag = defaultdict(int)
    by_subject_up = defaultdict(int)
    by_type_up = defaultdict(int)

    for path, idx, q, subject, old_asset in eligible:
        classification, visual_type, cause = classify_pass3(path, idx, q, subject, old_asset)
        qid = q.get("id") or q.get("sourceRef") or f"idx_{idx}"

        if classification == "keep_generic":
            keep_generic_with_cause[cause or "abstract verbal-only visual reference"].append(
                {"qid": qid, "subject": subject, "type": visual_type, "asset": old_asset})
            continue
        if classification == "manual_review_candidate":
            manual_review.append({
                "qid": qid, "subject": subject, "type": visual_type, "asset": old_asset,
                "reason": cause or "not enough structure for safe auto-generation",
            })
            continue

        pack_id = get_pack_id(path)
        q_slug = get_question_slug(q, idx)
        new_path, skip_reason, tag = try_generate_pass3(subject, pack_id, q_slug, visual_type, q, classification)
        if skip_reason or not new_path:
            keep_generic_with_cause["no extractable numbers" if "graph" in visual_type or "table" in visual_type else "missing labels"].append(
                {"qid": qid, "subject": subject, "type": visual_type, "asset": old_asset})
            continue

        upgraded.append({
            "path": path, "idx": idx, "qid": qid, "old_asset": old_asset, "new_asset": new_path,
            "subject": subject, "type": visual_type, "tag": tag,
        })
        by_tag[tag] += 1
        by_subject_up[subject] += 1
        by_type_up[visual_type] += 1
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

    still_generic = len(eligible) - len(upgraded)
    cumulative = CUMULATIVE_BEFORE_PASS3 + len(upgraded)
    upgraded_keys = {(str(u["path"]), u["idx"]) for u in upgraded}
    remaining_by_subject = defaultdict(int)
    remaining_by_type = defaultdict(int)
    for path, idx, q, subject, asset in eligible:
        if (str(path), idx) in upgraded_keys:
            continue
        remaining_by_subject[subject] += 1
        remaining_by_type[infer_visual_type(asset)] += 1

    rel = lambda p: str(Path(p).relative_to(ROOT)) if str(ROOT) in str(p) else str(p)

    with open(ROOT / "VISUAL_UPGRADE_REPORT_PASS3.md", "w", encoding="utf-8") as f:
        f.write("# EDUmio Visual Upgrade — Pass 3 Report\n\n")
        f.write("## Counts\n\n")
        f.write("| Metric | Count |\n|--------|-------|\n")
        f.write(f"| Newly upgraded (pass 3) | {len(upgraded)} |\n")
        f.write(f"| Cumulative upgraded total | {cumulative} |\n")
        f.write(f"| Remaining generic | {still_generic} |\n\n")
        f.write("## Subject breakdown\n\n")
        f.write("| Subject | Pass 3 upgraded | Remaining generic |\n|---------|-----------------|--------------------|\n")
        for s in PASS3_TARGET_SUBJECTS:
            f.write(f"| {s} | {by_subject_up[s]} | {remaining_by_subject.get(s, 0)} |\n")
        f.write("\n## Type breakdown\n\n")
        f.write("| Type | Upgraded | Remaining generic |\n|------|----------|--------------------|\n")
        for t in sorted(set(by_type_up.keys()) | set(remaining_by_type.keys())):
            f.write(f"| {t} | {by_type_up.get(t, 0)} | {remaining_by_type.get(t, 0)} |\n")
        f.write("\n## Tag breakdown\n\n")
        f.write("| Tag | Count |\n|-----|-------|\n")
        for tag in REPORT_TAGS:
            f.write(f"| {tag} | {by_tag.get(tag, 0)} |\n")
        f.write("\n## Top 40 upgraded (question id, old -> new, tag)\n\n")
        f.write("| Question ID | Old | New | Tag |\n|-------------|-----|-----|-----|\n")
        for u in upgraded[:40]:
            f.write(f"| `{u['qid']}` | `{u['old_asset']}` | `{u['new_asset']}` | {u['tag']} |\n")
        f.write("\n## Manual shortlist\n\n")
        for m in manual_review[:60]:
            f.write(f"- **{m['qid']}** | {m['subject']} | {m['asset']} | {m['type']} | {m['reason']}\n")
        f.write("\n## Remaining generic by cause\n\n")
        for cause in CAUSES:
            items = keep_generic_with_cause.get(cause, [])
            if items:
                f.write(f"### {cause}\n\n")
                for it in items[:15]:
                    f.write(f"- {it['qid']} ({it['subject']}, {it['type']})\n")
                if len(items) > 15:
                    f.write(f"- ... and {len(items) - 15} more.\n\n")

    with open(ROOT / "VISUAL_MANUAL_REVIEW_SHORTLIST.md", "w", encoding="utf-8") as f:
        f.write("# Visual Manual Review Shortlist\n\n")
        f.write("Best manual_review_candidate questions. Guidance for human designer.\n\n")
        for m in manual_review[:40]:
            f.write(f"## {m['qid']}\n")
            f.write(f"- **Subject:** {m['subject']}\n")
            f.write(f"- **Current imageAsset:** {m['asset']}\n")
            f.write(f"- **Inferred visual type:** {m['type']}\n")
            f.write(f"- **Why auto-generation was unsafe:** {m['reason']}\n")
            f.write(f"- **What a human should draw:** Create a simple, accurate {m['type']} visual that matches the question stem and supports solving the problem.\n\n")

    print("=" * 60)
    print("PASS 3: upgraded %d | cumulative %d | remaining generic %d" % (len(upgraded), cumulative, still_generic))
    print("=" * 60)
    return 0


if __name__ == "__main__":
    exit(main())
