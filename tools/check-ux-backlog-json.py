#!/usr/bin/env python3
"""Fail preflight when UX-Backlog.json is invalid or still has merge conflict markers."""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
BACKLOG = ROOT / "docs" / "ux-program" / "UX-Backlog.json"
MARKER_RE = re.compile(r"^(<<<<<<<|=======|>>>>>>>)( |$)", re.MULTILINE)


def duplicate_keys_in_object(pairs: list[tuple[str, Any]]) -> str | None:
    counts = Counter(key for key, _ in pairs)
    dups = sorted(key for key, count in counts.items() if count > 1)
    if not dups:
        return None
    return ", ".join(dups)


def validate_backlog_text(text: str) -> list[str]:
    errors: list[str] = []
    if MARKER_RE.search(text):
        errors.append("conflict marker (Git merge) found in UX-Backlog.json")
    try:

        def object_pairs_hook(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
            message = duplicate_keys_in_object(pairs)
            if message is not None:
                raise json.JSONDecodeError(
                    f"duplicate keys: {message}",
                    text,
                    0,
                )
            return dict(pairs)

        json.loads(text, object_pairs_hook=object_pairs_hook)
    except json.JSONDecodeError as err:
        errors.append(f"invalid JSON: {err.msg}")
    return errors


def main() -> int:
    if not BACKLOG.is_file():
        print(f"check-ux-backlog-json: missing {BACKLOG}", file=sys.stderr)
        return 1
    text = BACKLOG.read_text(encoding="utf-8")
    errors = validate_backlog_text(text)
    if errors:
        for err in errors:
            print(f"check-ux-backlog-json: {err}", file=sys.stderr)
        return 1
    print("check-ux-backlog-json: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
