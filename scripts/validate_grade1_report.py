#!/usr/bin/env python3
"""Validate grade 1 packs: JSON parse, imageAsset paths, no 'look at picture' without image."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
DIRS = [
    ("mat1", ASSETS / "lgs_import/mat1"),
    ("turkce1", ASSETS / "lgs_import/turkce1"),
    ("hayat1", ASSETS / "lgs_import/hayat1"),
    ("english1", ASSETS / "lgs_import/english1"),
]
BAD_PHRASES = ["resimdeki", "görsele bak", "bakınız", "look at the picture", "see the picture"]
# Lowercase check - "Resimde" etc are ok when imageAsset is set


def main():
    errors = []
    total_q = 0
    total_img = 0
    packs_created = 0

    by_subj = {}
    for name, d in DIRS:
        if not d.exists():
            errors.append(f"Directory missing: {d}")
            continue
        by_subj[name] = {"packs": 0, "questions": 0, "imageAsset": 0}
        for p in sorted(d.glob("*.json")):
            try:
                data = json.load(open(p, encoding="utf-8"))
            except Exception as e:
                errors.append(f"{p.name}: JSON error: {e}")
                continue
            packs_created += 1
            by_subj[name]["packs"] += 1
            for qq in data.get("questions", []):
                total_q += 1
                by_subj[name]["questions"] += 1
                stem = (qq.get("stem") or "").lower()
                img = qq.get("imageAsset") or ""
                if img and img.strip():
                    total_img += 1
                    by_subj[name]["imageAsset"] += 1
                    # Verify file exists
                    rel = img.replace("quiz_images/", "")
                    path = ASSETS / "quiz_images" / rel
                    if not path.exists():
                        errors.append(f"{p.name} q={qq.get('id')}: imageAsset {img} not found")
                # No "look at picture" without image
                if any(b in stem for b in ["look at the picture", "see the picture above", "resimdeki gibi"]):
                    if not (img and img.strip()):
                        errors.append(f"{p.name} q={qq.get('id')}: mentions picture but no imageAsset")

    print("=" * 60)
    print("GRADE 1 VALIDATION REPORT")
    print("=" * 60)
    print(f"Packs: {packs_created} (expected 100)")
    print(f"Total questions: {total_q} (expected 1000)")
    print(f"With imageAsset: {total_img} ({100 * total_img / total_q:.0f}%)" if total_q else "N/A")
    print("\nBy subject:")
    for name, stats in by_subj.items():
        qn = stats["questions"]
        im = stats["imageAsset"]
        pct = 100 * im / qn if qn else 0
        print(f"  {name}: {stats['packs']} packs, {qn} questions, {im} imageAsset ({pct:.0f}%)")
    if errors:
        print(f"\nErrors ({len(errors)}):")
        for e in errors[:30]:
            print(f"  - {e}")
        if len(errors) > 30:
            print(f"  ... and {len(errors) - 30} more")
    else:
        print("\nAll imageAsset paths resolve. No 'look at picture' without image.")
    print("=" * 60)
    return 1 if errors else 0


if __name__ == "__main__":
    exit(main())
