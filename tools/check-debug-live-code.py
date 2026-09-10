#!/usr/bin/env python3
"""Temper Debug drop ratchet. See debug_drop.py for the rule."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from debug_drop import check_code  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    parser = argparse.ArgumentParser(description="Temper Debug drop versionCode ratchet")
    parser.add_argument(
        "--root",
        default=str(ROOT),
        help="Repository root (tests pass a fixture)",
    )
    args = parser.parse_args()
    result = check_code(Path(args.root))
    print(result.message)
    return 0 if result.ok else 1


if __name__ == "__main__":
    sys.exit(main())
