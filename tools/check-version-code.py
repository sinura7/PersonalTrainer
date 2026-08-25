#!/usr/bin/env python3
"""P12.3: versionCode in app/build.gradle.kts must never go backwards."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app" / "build.gradle.kts"
FLOOR = ROOT / "tools" / "released-version-code.txt"

CODE = re.compile(r"^val appVersionCode = (\d+)\s*$", re.M)


def main() -> int:
    gradle = GRADLE.read_text(encoding="utf-8")
    match = CODE.search(gradle)
    if match is None:
        print("check-version-code: appVersionCode not found in app/build.gradle.kts")
        return 1
    current = int(match.group(1))
    floor = int(FLOOR.read_text(encoding="utf-8").strip())
    if current < floor:
        print(f"check-version-code: versionCode {current} is below released floor {floor}")
        return 1
    print(f"check-version-code: versionCode {current} >= released {floor}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
