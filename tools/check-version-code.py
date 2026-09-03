#!/usr/bin/env python3
"""Gym-floor versionCode ratchet. See version_ratchet.py for the rule."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from version_ratchet import evaluate  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    parser = argparse.ArgumentParser(description="Gym-floor versionCode ratchet")
    parser.add_argument(
        "--tag-release",
        action="store_true",
        help="First v* may equal 1; later v* must be strictly above the previous v*",
    )
    parser.add_argument(
        "--exclude-tag",
        default=None,
        help="Ignore this v* tag when computing the previous release (the tag being pushed)",
    )
    parser.add_argument(
        "--root",
        default=str(ROOT),
        help="Repository root (tests pass a fixture)",
    )
    args = parser.parse_args()
    result = evaluate(
        Path(args.root),
        tag_release=args.tag_release,
        exclude_tag=args.exclude_tag,
    )
    print(result.message)
    return 0 if result.ok else 1


if __name__ == "__main__":
    sys.exit(main())
