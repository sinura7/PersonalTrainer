#!/usr/bin/env python3
"""P5.1 ratchet: domain/ must not import platform time, locale, Android, Room, or Compose.

Nor any package that sits outside it. The platform bans were enough while `domain/` only
ever reached sideways for a clock, but that reach was the whole problem: fifteen files
carried `time: TimePort = JvmTime` defaults, `util/JvmTimePort.kt` calls
`android.os.SystemClock`, and `util` imports `TimePort`, `CivilDate`, `WeightUnit` and
`IdPort` straight back. So `domain/` held no Android import of its own, passed this check
every time, and still could not be compiled without the Android-backed adapter behind it —
a cycle no import ban expressed. `domain` is the bottom of the graph: everything may depend
on it and it may depend on nothing.
"""
from __future__ import annotations

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DOMAIN = os.path.join(ROOT, "app/src/main/java/com/sinura/personaltrainer/domain")

APP = r"com\.sinura\.personaltrainer"

BANNED = [
    (re.compile(r"^import\s+java\.time\."), "java.time"),
    (re.compile(r"^import\s+java\.text\.NumberFormat"), "java.text.NumberFormat"),
    (re.compile(r"^import\s+java\.util\.Locale"), "java.util.Locale"),
    (re.compile(r"^import\s+android\."), "android"),
    (re.compile(r"^import\s+androidx\.room"), "Room"),
    (re.compile(r"^import\s+androidx\.compose"), "Compose"),
    # Every internal package except domain's own. util is the one that mattered — the
    # JvmTime defaults — but data, ui, timer, reminder, workout, logging, insights,
    # diagnostics and activity are the same mistake waiting to be made.
    (re.compile(rf"^import\s+{APP}\.(?!domain\b)\w+"), "an outward app package"),
]

def banned_import_findings(text: str, rel: str = "snippet.kt") -> list[str]:
    found: list[str] = []
    for number, line in enumerate(text.splitlines(), 1):
        for pattern, why in BANNED:
            if pattern.search(line):
                found.append(f"{rel}:{number}  banned {why} import: {line.strip()}")
    return found


def main() -> int:
    if not os.path.isdir(DOMAIN):
        print("domain/ is missing")
        return 1
    found: list[str] = []
    for dirpath, _, filenames in os.walk(DOMAIN):
        for name in filenames:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, ROOT)
            found.extend(banned_import_findings(open(path, encoding="utf-8").read(), rel))
    print(f"{len(found)} domain-seam finding(s)")
    for item in found:
        print(item)
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
