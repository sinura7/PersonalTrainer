#!/usr/bin/env python3
"""Catalog stills stay a 256px thumb pack; Body unlit stays the raised pair.

E2's APK-size proof: the 129 `ex_*` WebPs used to be 768×768 (~2.56 MB).
They must stay 256×256 and under EX_BYTES_CEILING. Family pose fallbacks
match the thumb pack. The Body unlit pair is 1024×1024 so the panel does
not upscale 768. Heat stills stay 768 for the mark.

Usage:  python3 tools/check-still-pack.py
Exit code is nonzero on a size or dimension miss.
"""
from __future__ import annotations

import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "app", "src", "main", "res", "drawable-nodpi")

EX_COUNT = 129
EX_EDGE = 256
POSE_EDGE = 256
UNLIT_EDGE = 1024
HEAT_EDGE = 768
# 129 lossless-ish 256 thumbs were ~0.4 MB in the E2 encode. 700 KB is
# enough room for a slightly noisier still without returning to 768.
EX_BYTES_CEILING = 700_000


def webp_wh(data: bytes) -> tuple[int, int] | None:
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        return None
    off = 12
    while off + 8 <= len(data):
        tag = data[off : off + 4]
        size = int.from_bytes(data[off + 4 : off + 8], "little")
        payload = data[off + 8 : off + 8 + size]
        if tag == b"VP8X" and len(payload) >= 10:
            w = 1 + int.from_bytes(payload[4:7], "little")
            h = 1 + int.from_bytes(payload[7:10], "little")
            return w, h
        if tag == b"VP8 " and payload[3:6] == b"\x9d\x01\x2a":
            w = int.from_bytes(payload[6:8], "little") & 0x3FFF
            h = int.from_bytes(payload[8:10], "little") & 0x3FFF
            return w, h
        if tag == b"VP8L" and len(payload) >= 5:
            n = int.from_bytes(payload[1:5], "little")
            return 1 + (n & 0x3FFF), 1 + ((n >> 14) & 0x3FFF)
        off += 8 + size + (size & 1)
    return None


def fail(msg: str) -> None:
    print(f"check-still-pack: FAIL — {msg}", file=sys.stderr)
    sys.exit(1)


def expect(path: str, edge: int) -> None:
    data = open(path, "rb").read()
    dim = webp_wh(data)
    if dim != (edge, edge):
        fail(f"{os.path.basename(path)} is {dim}, want {edge}x{edge}")


def main() -> None:
    if not os.path.isdir(ROOT):
        fail(f"missing {ROOT}")
    ex = sorted(
        n for n in os.listdir(ROOT) if n.startswith("ex_") and n.endswith(".webp")
    )
    if len(ex) != EX_COUNT:
        fail(f"{len(ex)} ex_* webps, want {EX_COUNT}")
    ex_bytes = 0
    for name in ex:
        path = os.path.join(ROOT, name)
        expect(path, EX_EDGE)
        ex_bytes += os.path.getsize(path)
    if ex_bytes > EX_BYTES_CEILING:
        fail(f"ex_* pack is {ex_bytes} bytes, ceiling {EX_BYTES_CEILING}")
    poses = sorted(
        n for n in os.listdir(ROOT) if n.startswith("temper_pose_") and n.endswith(".webp")
    )
    if not poses:
        fail("no temper_pose_* fallbacks")
    for name in poses:
        expect(os.path.join(ROOT, name), POSE_EDGE)
    for name, edge in (
        ("temper_front_unlit.webp", UNLIT_EDGE),
        ("temper_back_unlit.webp", UNLIT_EDGE),
        ("temper_front_heat.webp", HEAT_EDGE),
        ("temper_back_heat.webp", HEAT_EDGE),
    ):
        expect(os.path.join(ROOT, name), edge)
    print(
        f"check-still-pack: OK — {len(ex)} ex_* at {EX_EDGE}px, "
        f"{ex_bytes} bytes, unlit {UNLIT_EDGE}px"
    )


if __name__ == "__main__":
    main()
