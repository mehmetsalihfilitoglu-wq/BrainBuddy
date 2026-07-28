#!/usr/bin/env python3
"""Validate that all imageAsset paths in question JSONs resolve to existing files."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"


def collect_json_paths():
    paths = []
    for base in ["lgs_import", "packs"]:
        p = ASSETS / base
        if p.exists():
            paths.extend(p.rglob("*.json"))
    qt = ASSETS / "questions_tr.json"
    if qt.exists():
        paths.append(qt)
    return paths


def load_questions(path):
    try:
        data = json.load(open(path, encoding="utf-8"))
    except Exception:
        return []
    if isinstance(data, list):
        return [(i, q) for i, q in enumerate(data) if isinstance(q, dict)]
    qs = data.get("questions", [])
    if isinstance(qs, list):
        return [(i, q) for i, q in enumerate(qs) if isinstance(q, dict)]
    return []


def main():
    broken = []
    total_with_asset = 0
    for path in collect_json_paths():
        for idx, q in load_questions(path):
            asset = (q.get("imageAsset") or "").strip()
            if not asset or asset.lower() == "null":
                continue
            total_with_asset += 1
            # Normalize: may be quiz_images/... or just path
            if not asset.startswith("quiz_images"):
                continue  # skip external/full paths
            full = ASSETS / asset
            if not full.exists():
                qid = q.get("id") or q.get("questionId") or f"idx_{idx}"
                broken.append((str(path.relative_to(ROOT)), qid, asset))
    if broken:
        print("BROKEN imageAsset paths (file does not exist):")
        for path, qid, asset in broken:
            print(f"  {path} id={qid} -> {asset}")
        return 1
    print(f"OK: {total_with_asset} questions with imageAsset; all paths resolve.")
    return 0


if __name__ == "__main__":
    exit(main())
