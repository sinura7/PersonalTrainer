#!/usr/bin/env python3
"""Fail preflight when UX-Backlog.json is invalid or still has merge conflict markers."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKLOG = ROOT / "docs" / "ux-program" / "UX-Backlog.json"
MARKERS = ("<<<<<<<", "=======", ">>>>>>>")


def main() -> int:
    if not BACKLOG.is_file():
        print(f"check-ux-backlog-json: missing {BACKLOG}", file=sys.stderr)
        return 1
    text = BACKLOG.read_text(encoding="utf-8")
    for marker in MARKERS:
        if marker in text:
            print(
                f"check-ux-backlog-json: conflict marker {marker!r} in {BACKLOG}",
                file=sys.stderr,
            )
            return 1
    try:
        json.loads(text)
    except json.JSONDecodeError as err:
        print(f"check-ux-backlog-json: invalid JSON in {BACKLOG}: {err}", file=sys.stderr)
        return 1
    print("check-ux-backlog-json: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
