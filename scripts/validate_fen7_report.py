#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Validate FEN7 packs and produce report."""
import json
from pathlib import Path
from collections import Counter

BASE = Path(__file__).resolve().parent.parent / "app/src/main/assets/lgs_import/fen7"
NEW_GEN = {"deney_yorum", "grafik_yorum", "tablo_yorum", "senaryo_analiz", "gozlem_yorum", "veri_analiz"}

def main():
    packs = list(BASE.glob("lgs_fen7_pack_*.json"))
    total = 0
    units = Counter()
    new_gen = 0
    parse_ok = 0

    for p in sorted(packs):
        try:
            d = json.load(open(p, encoding="utf-8"))
            qs = d.get("questions", [])
            total += len(qs)
            for q in qs:
                units[q.get("topic", "")] += 1
                if q.get("questionType") in NEW_GEN:
                    new_gen += 1
            parse_ok += 1
        except Exception as e:
            print(f"Parse error {p.name}: {e}")

    print("=" * 60)
    print("FEN7 VALIDATION REPORT")
    print("=" * 60)
    print(f"Packs created: {len(packs)}")
    print(f"Total questions: {total}")
    print("\nUnit distribution:")
    for u in sorted(units.keys()):
        print(f"  {u}: {units[u]}")
    pct = 100 * new_gen / total if total else 0
    print(f"\nNew-generation ratio: {new_gen}/{total} = {pct:.1f}%")
    print(f"JSON parse success: {parse_ok}/{len(packs)} files")
    print("=" * 60)

if __name__ == "__main__":
    main()
