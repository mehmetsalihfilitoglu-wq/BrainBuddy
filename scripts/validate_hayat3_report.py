#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Validate Hayat3 packs and produce report. Compatible with QuestionPackImporter."""
import json
from pathlib import Path
from collections import Counter

OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/hayat3"
REQUIRED_KEYS = {
    "id", "stem", "options", "answerIndex", "difficulty", "questionType",
    "topic", "skills", "explanation", "source", "sourceRef"
}
NEW_GEN_TYPES = {
    "senaryo_yorumlama", "gunluk_hayat_durumu", "davranis_analizi",
    "sebep_sonuc", "deger_yorumlama", "dogru_tavir_secimi"
}


def main():
    packs = sorted(OUT_DIR.glob("lgs_hayat3_pack_*.json"))
    total_questions = 0
    topic_counts = Counter()
    type_counts = Counter()
    diff_counts = Counter()
    errors = []
    parse_ok = True

    for p in packs:
        try:
            d = json.load(open(p, encoding="utf-8"))
        except Exception as e:
            errors.append(f"{p.name}: JSON parse error: {e}")
            parse_ok = False
            continue
        if d.get("subject") != "hayat":
            errors.append(f"{p.name}: expected subject=hayat, got {d.get('subject')}")
        qs = d.get("questions") or []
        for q in qs:
            total_questions += 1
            topic_counts[q.get("topic", "?")] += 1
            type_counts[q.get("questionType", "?")] += 1
            diff_counts[q.get("difficulty")] += 1
            opts = q.get("options") or []
            if len(opts) != 4:
                errors.append(f"{p.name} q={q.get('id')}: expected 4 options, got {len(opts)}")
            ai = q.get("answerIndex", -1)
            if not (0 <= ai < len(opts)):
                errors.append(f"{p.name} q={q.get('id')}: invalid answerIndex {ai}")
            for k in REQUIRED_KEYS:
                if k not in q:
                    errors.append(f"{p.name} q={q.get('id')}: missing key {k}")

    new_gen = sum(type_counts.get(t, 0) for t in NEW_GEN_TYPES)
    new_gen_pct = (new_gen / total_questions * 100) if total_questions else 0

    print("=" * 60)
    print("HAYAT3 VALIDATION REPORT")
    print("=" * 60)
    print(f"Packs: {len(packs)}, Expected: 50")
    print(f"Total questions: {total_questions}, Expected: 500")
    print()
    print("Topic distribution:")
    for t, c in sorted(topic_counts.items(), key=lambda x: -x[1]):
        print(f"  {t}: {c}")
    print()
    print("Difficulty distribution:")
    for d, c in sorted(diff_counts.items()):
        print(f"  {d}: {c}")
    print()
    print(f"New-generation ratio: {new_gen}/{total_questions} = {new_gen_pct:.1f}%")
    print()
    print("JSON parse: OK" if parse_ok and not errors else f"Errors: {len(errors)}")
    if errors:
        for e in errors[:25]:
            print(f"  - {e}")
        if len(errors) > 25:
            print(f"  ... and {len(errors) - 25} more")
    print("=" * 60)
    return 0 if parse_ok and not errors and len(packs) == 50 and total_questions == 500 else 1


if __name__ == "__main__":
    exit(main())
