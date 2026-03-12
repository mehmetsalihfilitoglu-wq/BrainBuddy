#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Validate MAT7 packs and produce report."""
import json
from pathlib import Path
from collections import Counter

BASE = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/mat7"
NEW_GEN = {"yeni_nesil_problem", "grafik_yorum", "tablo_yorum", "geometri_yorum", "mantik", "scenario_problem"}

def main():
    packs = list(BASE.glob("lgs_mat7_pack_*.json"))
    total = 0
    topics = Counter()
    new_gen = 0
    parse_ok = 0
    
    for p in sorted(packs):
        try:
            d = json.load(open(p, encoding="utf-8"))
            qs = d.get("questions", [])
            total += len(qs)
            for q in qs:
                topics[q.get("topic", "")] += 1
                if q.get("questionType") in NEW_GEN:
                    new_gen += 1
            parse_ok += 1
        except Exception as e:
            print(f"Parse error {p.name}: {e}")
    
    print("=" * 60)
    print("MAT7 VALIDATION REPORT")
    print("=" * 60)
    print(f"Packs created: {len(packs)}")
    print(f"Total questions: {total}")
    print("\nTopic distribution:")
    for t in sorted(topics.keys()):
        print(f"  {t}: {topics[t]}")
    pct = 100 * new_gen / total if total else 0
    print(f"\nNew-generation ratio: {new_gen}/{total} = {pct:.1f}%")
    print(f"JSON parse success: {parse_ok}/{len(packs)} files")
    print("=" * 60)

if __name__ == "__main__":
    main()
