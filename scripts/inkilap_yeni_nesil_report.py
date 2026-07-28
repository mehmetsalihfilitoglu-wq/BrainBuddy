#!/usr/bin/env python3
"""İnkılap pack 025-040 yeni nesil dönüşüm raporu."""
import json
from pathlib import Path

INK_DIR = Path("app/src/main/assets/lgs_import/inkilap")
START, END = 25, 40

def is_yeni_nesil(stem):
    """Paragraf + soru formatında (\\n\\n ile ayrılmış) veya uzun bağlam içeren soru."""
    if not stem or not isinstance(stem, str):
        return False
    s = stem.strip()
    if "\n\n" in s:
        return True
    if len(s) > 350 and any(x in s.lower() for x in ("bu durum", "buna göre", "incelendiğinde", "değerlendirildiğinde")):
        return True
    return False

def main():
    total = 0
    yeni_nesil = 0
    by_pack = {}

    for i in range(START, END + 1):
        fp = INK_DIR / f"lgs_ink_pack_{i:03d}.json"
        if not fp.exists():
            continue
        with open(fp, "r", encoding="utf-8") as f:
            data = json.load(f)
        qs = data.get("questions", [])
        n = len(qs)
        yn = sum(1 for q in qs if is_yeni_nesil(q.get("stem", "")))
        total += n
        yeni_nesil += yn
        by_pack[f"lgs_ink_pack_{i:03d}"] = {"total": n, "yeni_nesil": yn, "oran": round(100*yn/n, 1) if n else 0}

    report = {
        "packs": list(range(START, END + 1)),
        "by_pack": by_pack,
        "total_questions": total,
        "yeni_nesil_count": yeni_nesil,
        "yeni_nesil_ratio_percent": round(100 * yeni_nesil / total, 1) if total else 0,
    }
    out = Path("inkilap_yeni_nesil_report.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"Toplam soru: {total}")
    print(f"Yeni nesil sayısı: {yeni_nesil}")
    print(f"Yeni nesil oranı: %{report['yeni_nesil_ratio_percent']}")
    print(f"Rapor: {out}")

if __name__ == "__main__":
    main()
