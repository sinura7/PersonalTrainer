#!/usr/bin/env python3
"""Crop Temper unlit stills into one 256px muscle picture per Body row.

Each still is the ChatGPT person, tight on that muscle, with a fixed Heat3
wash on the working plates (identity, not live load). Body's figure already
paints live heat; these are the row pictures.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
FIGURE = ROOT / "app/src/main/java/com/sinura/personaltrainer/ui/components/FigureArt.kt"
NODPI = ROOT / "app/src/main/res/drawable-nodpi"
EDGE = 256
FIGURE_ASPECT = 0.52
HEAT3 = (0xE2, 0x5A, 0x50)
PIT = (0x07, 0x09, 0x0B, 255)
HEAT_ALPHA = 0.72
PIT_LUMA = 10

# Close-up on the viewer's-right plate when a pair would otherwise span the
# whole figure (arms, one thigh). Torso groups stay both sides. Shoulders
# keep both caps so the crop is a person, not a floating delt in the pit.
CROP_SIDE = {
    "CHEST": "all",
    "BACK": "all",
    "SHOULDERS": "all",
    "BICEPS": "right",
    "TRICEPS": "right",
    "QUADRICEPS": "right",
    "HAMSTRINGS": "right",
    "GLUTES": "all",
    "CALVES": "right",
    "CORE": "all",
}

PAD = {
    "CHEST": 0.32,
    "BACK": 0.22,
    "SHOULDERS": 0.0,
    "BICEPS": 0.62,
    "TRICEPS": 0.62,
    "QUADRICEPS": 0.42,
    "HAMSTRINGS": 0.48,
    "GLUTES": 0.42,
    "CALVES": 0.48,
    "CORE": 0.38,
}

# Figure-fraction window when the plate bbox would square into the whole
# person (both delts span the silhouette). Upper-body square: full width,
# height 0.52, which is one pixel-square of the 0.52-aspect figure box.
WINDOW = {
    "SHOULDERS": (0.0, 0.0, 1.0, 0.52),
}

SOURCE = {
    "CHEST": "temper_front_unlit.webp",
    "BACK": "temper_back_unlit.webp",
    "SHOULDERS": "temper_front_unlit.webp",
    "BICEPS": "temper_front_unlit.webp",
    "TRICEPS": "temper_back_unlit.webp",
    "QUADRICEPS": "temper_front_unlit.webp",
    "HAMSTRINGS": "temper_back_unlit.webp",
    "GLUTES": "temper_back_unlit.webp",
    "CALVES": "temper_back_unlit.webp",
    "CORE": "temper_front_unlit.webp",
}


def parse_plates(source: str) -> dict[str, list[list[tuple[float, float]]]]:
    sections = {
        "SHARED": slice_block(source, "internal val FIGURE_SHARED_STRUCTURE"),
        "FRONT": slice_block(source, "private val FRONT_WORKING"),
        "BACK": slice_block(source, "private val BACK_WORKING"),
    }
    plates: dict[str, list[list[tuple[float, float]]]] = {}
    for name, block in sections.items():
        view = "FRONT" if name == "FRONT" else "BACK" if name == "BACK" else "SHARED"
        for match in re.finditer(
            r"plate\(\s*(null|CanonicalMuscle\.(\w+)),\s*([^)]+)\)",
            block,
        ):
            muscle = match.group(2)
            if muscle is None:
                continue
            nums = [float(n) for n in re.findall(r"[\d.]+", match.group(3))]
            points = list(zip(nums[0::2], nums[1::2]))
            if len(points) < 3:
                continue
            plates.setdefault(muscle, []).append(points)
            plates.setdefault(f"{muscle}@{view}", []).append(points)
    return plates


def slice_block(source: str, header: str) -> str:
    start = source.index(header)
    nxt = source.find("\ninternal val ", start + 1)
    nxt2 = source.find("\nprivate val ", start + 1)
    ends = [i for i in (nxt, nxt2) if i > start]
    end = min(ends) if ends else len(source)
    return source[start:end]


def figure_crop(still: Image.Image) -> tuple[Image.Image, int, int]:
    width, height = still.size
    src_w = round(height * FIGURE_ASPECT)
    src_x = (width - src_w) // 2
    return still.crop((src_x, 0, src_x + src_w, height)), src_x, src_w


def to_px(points: list[tuple[float, float]], src_w: int, src_h: int) -> list[tuple[int, int]]:
    return [(round(x * src_w), round(y * src_h)) for x, y in points]


def centroid_x(points: list[tuple[float, float]]) -> float:
    return sum(p[0] for p in points) / len(points)


def choose_plates(
    plates: list[list[tuple[float, float]]],
    side: str,
) -> list[list[tuple[float, float]]]:
    if side == "all":
        return plates
    chosen = [p for p in plates if centroid_x(p) > 0.50]
    return chosen or plates


def bbox(plates: list[list[tuple[float, float]]]) -> tuple[float, float, float, float]:
    xs = [x for plate in plates for x, _ in plate]
    ys = [y for plate in plates for _, y in plate]
    return min(xs), min(ys), max(xs), max(ys)


def square_window(
    left: float,
    top: float,
    right: float,
    bottom: float,
    pad: float,
) -> tuple[float, float, float, float]:
    width = right - left
    height = bottom - top
    left -= width * pad
    right += width * pad
    top -= height * pad
    bottom += height * pad
    width = right - left
    height = bottom - top
    if width > height:
        extra = (width - height) / 2
        top -= extra
        bottom += extra
    elif height > width:
        extra = (height - width) / 2
        left -= extra
        right += extra
    return left, top, right, bottom


def clamp_square(
    left: float,
    top: float,
    right: float,
    bottom: float,
) -> tuple[float, float, float, float]:
    width = right - left
    height = bottom - top
    if left < 0:
        right -= left
        left = 0
    if top < 0:
        bottom -= top
        top = 0
    if right > 1:
        left -= right - 1
        right = 1
    if bottom > 1:
        top -= bottom - 1
        bottom = 1
    left = max(0.0, left)
    top = max(0.0, top)
    right = min(1.0, right)
    bottom = min(1.0, bottom)
    width = right - left
    height = bottom - top
    edge = min(width, height)
    if width > edge:
        left += (width - edge) / 2
        right = left + edge
    if height > edge:
        top += (height - edge) / 2
        bottom = top + edge
    return left, top, right, bottom


def wash_plates(
    figure: Image.Image,
    plates: list[list[tuple[float, float]]],
    src_w: int,
    src_h: int,
) -> Image.Image:
    overlay = Image.new("RGBA", figure.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, "RGBA")
    fill = (*HEAT3, round(255 * HEAT_ALPHA))
    for plate in plates:
        draw.polygon(to_px(plate, src_w, src_h), fill=fill)
    overlay = overlay.filter(ImageFilter.GaussianBlur(radius=max(1.0, src_w * 0.002)))
    base = figure.convert("RGBA")
    pixels = base.load()
    mask = overlay.load()
    for y in range(figure.height):
        for x in range(figure.width):
            r, g, b, a = pixels[x, y]
            if max(r, g, b) <= PIT_LUMA:
                mask[x, y] = (0, 0, 0, 0)
    return Image.alpha_composite(base, overlay)


def render_one(
    muscle: str,
    plates_by_name: dict[str, list[list[tuple[float, float]]]],
) -> Image.Image:
    still = Image.open(NODPI / SOURCE[muscle]).convert("RGBA")
    figure, _, src_w = figure_crop(still)
    src_h = figure.height
    view = "FRONT" if "front" in SOURCE[muscle] else "BACK"
    plates = plates_by_name.get(f"{muscle}@{view}") or plates_by_name.get(muscle) or []
    if not plates:
        raise SystemExit(f"no plates for {muscle}")
    chosen = choose_plates(plates, CROP_SIDE[muscle])
    washed = wash_plates(figure, chosen, src_w, src_h)
    if muscle in WINDOW:
        left, top, right, bottom = WINDOW[muscle]
    else:
        left, top, right, bottom = bbox(chosen)
        left, top, right, bottom = square_window(
            left, top, right, bottom, pad=PAD[muscle],
        )
        left, top, right, bottom = clamp_square(left, top, right, bottom)
    box = (
        round(left * src_w),
        round(top * src_h),
        round(right * src_w),
        round(bottom * src_h),
    )
    crop = washed.crop(box)
    canvas = Image.new("RGBA", (EDGE, EDGE), PIT)
    fitted = crop.resize((EDGE, EDGE), Image.Resampling.LANCZOS)
    canvas.paste(fitted, (0, 0))
    return canvas.convert("RGB")


def main() -> int:
    plates = parse_plates(FIGURE.read_text())
    missing = [m for m in CROP_SIDE if not (plates.get(m) or plates.get(f"{m}@FRONT") or plates.get(f"{m}@BACK"))]
    if missing:
        print(f"render-muscle-stills: FAIL — no plates for {missing}", file=sys.stderr)
        return 1
    for muscle in CROP_SIDE:
        out = NODPI / f"muscle_{muscle.lower()}.webp"
        render_one(muscle, plates).save(out, "WEBP", quality=82, method=6)
        print(f"wrote {out.name} ({out.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
