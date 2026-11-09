#!/usr/bin/env python3
"""Fails if gym-specific equipment sets or gymId = appear outside allowed files."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOTLIN = ROOT / "app" / "src" / "main" / "java"

ALLOWED_GYM_STACK = {
    KOTLIN / "com/sinura/personaltrainer/domain/GymScope.kt",
    KOTLIN / "com/sinura/personaltrainer/domain/ExtraEquipment.kt",
}

GYM_STACK_PATTERN = re.compile(
    r"EquipmentType\.(MACHINE|CABLE|SMITH|HYPER_PRO)",
)
GYM_ID_EQUALS = re.compile(r"gymId\s*=")


def main() -> int:
    violations: list[str] = []
    for path in KOTLIN.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        if re.search(r"gymId\s*=\s*:", text) and "SET gymId" not in text:
            violations.append(f"{path.relative_to(ROOT)}: uses gymId = comparison in SQL")
        if path.resolve() in ALLOWED_GYM_STACK:
            continue
        if "GYM_STACK" in text and GYM_STACK_PATTERN.search(text):
            # Building the four-type set outside GymScope / ExtraEquipment
            if "GymScope.GYM_SPECIFIC" not in text:
                violations.append(f"{path.relative_to(ROOT)}: gym-specific equipment set outside GymScope")
    if violations:
        print("check-gym-scope: FAIL", file=sys.stderr)
        for line in violations:
            print(f"  {line}", file=sys.stderr)
        return 1
    print("check-gym-scope: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
