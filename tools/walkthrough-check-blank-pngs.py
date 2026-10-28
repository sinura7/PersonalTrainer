#!/usr/bin/env python3
"""Fail if walkthrough PNGs look blank or near-uniform (CI guard)."""
from __future__ import annotations

import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("walkthrough-check-blank-pngs: Pillow not installed; skipping blank check")
    sys.exit(0)


def luminance_range(path: Path) -> int:
    with Image.open(path) as img:
        rgb = img.convert("RGB")
        w, h = rgb.size
        step_x = max(w // 16, 1)
        step_y = max(h // 16, 1)
        min_l = 255
        max_l = 0
        for y in range(0, h, step_y):
            for x in range(0, w, step_x):
                r, g, b = rgb.getpixel((x, y))
                luma = int(0.299 * r + 0.587 * g + 0.114 * b)
                min_l = min(min_l, luma)
                max_l = max(max_l, luma)
        return max_l - min_l


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "walkthrough-screens")
    pngs = sorted(root.glob("flow*.png"))
    if not pngs:
        print(f"UX_WALKTHROUGH_BLANK_CHECK no PNGs under {root}")
        return 1
    bad: list[str] = []
    for path in pngs:
        spread = luminance_range(path)
        if spread < 20:
            bad.append(f"{path.name} (range={spread})")
    if bad:
        print("UX_WALKTHROUGH_BLANK_CHECK FAIL near-uniform PNGs:")
        for line in bad:
            print(f"  {line}")
        return 1
    print(f"UX_WALKTHROUGH_BLANK_CHECK OK {len(pngs)} PNGs")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
