#!/usr/bin/env python3
"""P5.1 ratchet: domain/ must not import platform time, locale, Android, Room, or Compose."""
from __future__ import annotations

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DOMAIN = os.path.join(ROOT, "app/src/main/java/com/sinura/personaltrainer/domain")

BANNED = [
    (re.compile(r"^import\s+java\.time\."), "java.time"),
    (re.compile(r"^import\s+java\.text\.NumberFormat"), "java.text.NumberFormat"),
    (re.compile(r"^import\s+java\.util\.Locale"), "java.util.Locale"),
    (re.compile(r"^import\s+android\."), "android"),
    (re.compile(r"^import\s+androidx\.room"), "Room"),
    (re.compile(r"^import\s+androidx\.compose"), "Compose"),
]

findings: list[str] = []


def main() -> int:
    if not os.path.isdir(DOMAIN):
        print("domain/ is missing")
        return 1
    for dirpath, _, filenames in os.walk(DOMAIN):
        for name in filenames:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, ROOT)
            for number, line in enumerate(open(path, encoding="utf-8"), 1):
                for pattern, why in BANNED:
                    if pattern.search(line):
                        findings.append(f"{rel}:{number}  banned {why} import: {line.strip()}")
    print(f"{len(findings)} domain-seam finding(s)")
    for item in findings:
        print(item)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
