#!/usr/bin/env python3
"""Create placeholder PNG files for grade 1 visual questions. Paths match imageAsset references."""
import base64
from pathlib import Path

# Minimal 1x1 transparent PNG (valid PNG)
PNG_1X1 = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)

ASSETS = Path(__file__).resolve().parent.parent / "app/src/main/assets/quiz_images"

# Asset filenames used by grade 1 question packs
ASSET_NAMES = [
    "g1_count_3.png",   # 3 objects
    "g1_count_5.png",   # 5 objects
    "g1_count_7.png",   # 7 objects
    "g1_count_4.png",
    "g1_count_6.png",
    "g1_count_8.png",
    "g1_count_10.png",
    "g1_shapes.png",    # circle, square, triangle
    "g1_apple.png",
    "g1_ball.png",
    "g1_book.png",
    "g1_crayon.png",
    "g1_desk.png",
    "g1_family.png",
    "g1_classroom.png",
    "g1_red.png",
    "g1_blue.png",
    "g1_green.png",
    "g1_yellow.png",
    "g1_playground.png",
    "g1_crossing.png",  # traffic / crossing
    "g1_handwash.png",
    "g1_share.png",
    "g1_hello.png",
    "g1_numbers.png",
]


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    created = []
    for name in ASSET_NAMES:
        path = ASSETS / name
        path.write_bytes(PNG_1X1)
        created.append(name)
    print(f"Created {len(created)} placeholder images in {ASSETS}")
    for n in created:
        print(f"  - {n}")
    return 0


if __name__ == "__main__":
    exit(main())
