#!/usr/bin/env python3
"""Audit quiz image assets: dimensions, file size, detect blank/placeholder."""
import os
import struct
import json
from pathlib import Path

ASSETS_ROOT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
QUIZ_IMAGES = ASSETS_ROOT / "quiz_images"


def png_dims(path):
    try:
        with open(path, "rb") as f:
            sig = f.read(8)
            if sig != b"\x89PNG\r\n\x1a\n":
                return None
            while True:
                raw = f.read(8)
                if len(raw) < 8:
                    return None
                length = struct.unpack(">I", raw[:4])[0]
                chunk = raw[4:8]
                if chunk == b"IHDR":
                    data = f.read(13)
                    if len(data) < 8:
                        return None
                    w, h = struct.unpack(">II", data[:8])
                    return (w, h)
                f.read(length + 4)
    except Exception as e:
        return f"Error: {e}"


def main():
    print("=== IMAGE ASSET AUDIT ===\n")
    print("1. Inspecting actual image files\n")

    all_files = list(QUIZ_IMAGES.rglob("*.png"))
    report = []
    unusable = []

    for p in sorted(all_files):
        rel = str(p.relative_to(ASSETS_ROOT)).replace("\\", "/")
        size = p.stat().st_size
        dims = png_dims(p)
        if isinstance(dims, tuple):
            w, h = dims
            pixels = w * h
            # 1x1 or very small = placeholder
            is_placeholder = pixels <= 1 or size < 200
            status = "UNUSABLE (placeholder)" if is_placeholder else "OK"
            if is_placeholder:
                unusable.append((rel, size, w, h))
            report.append((rel, size, w, h, status))
        else:
            report.append((rel, size, dims or "?", "?", "UNUSABLE"))
            unusable.append((rel, size, "?", "?"))

    for rel, size, w, h, status in report:
        dim_str = f"{w}x{h}" if isinstance(w, int) else str(w)
        print(f"  {rel}: {size} bytes, {dim_str} - {status}")

    print("\n2. UNUSABLE ASSETS (blank/placeholder/tiny):")
    for rel, size, w, h in unusable:
        dim_str = f"{w}x{h}" if isinstance(w, int) else "?"
        print(f"  - {rel} ({size} bytes, {dim_str})")

    print(f"\nTotal: {len(all_files)} files, {len(unusable)} unusable")
    return unusable


if __name__ == "__main__":
    main()
