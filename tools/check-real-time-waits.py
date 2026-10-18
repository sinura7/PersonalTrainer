#!/usr/bin/env python3
"""Fail preflight when tests add real-time sleeps or unmarked delay() calls.

The gap this closes
-------------------
Wall-clock sleeps and bare delay() in tests pass on a fast machine and flake on a
hosted runner. Nothing in preflight stopped a new one. check-unbounded-waits.py
already gates ViewModel-flow waits; this checker gates Thread.sleep, SystemClock.sleep,
TimeUnit sleeps, and delay() in files with no virtual-time marker.

Family 1, real_time_sleeps
--------------------------
Thread.sleep(, SystemClock.sleep( (with or without android.os.), TimeUnit.<UNIT>.sleep(.
Every hit must match an allowlist row: relative path, stripped source line text, reason.
An unlisted hit prints ``path:line: real-time sleep not in allowlist`` and fails.
A stale allowlist row passes with a ratchet hint (Lane B may remove a sleep without
editing this file).

Family 2, real_time_delays
---------------------------
delay( in a test file that has none of: StandardTestDispatcher, UnconfinedTestDispatcher,
runTest, TestScope, advanceTimeBy, TestCoroutineScheduler. File-level heuristic only:
a delay() under runTest in the same file is still counted if the marker string is absent
elsewhere in the file. Blind spots match check-unbounded-waits.py style: kotlinx.coroutines.delay
with a fully qualified name is still ``delay(`` after stripping; a delay in a comment is not.

Usage:  tools/check-real-time-waits.py [test-root ...]
Exit 0 unless an unlisted sleep exists or a count grew past the ceiling.
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from checker_baseline import report as report_baseline  # noqa: E402
from kotlin_source import kotlin_files_in, strip_comments_and_strings  # noqa: E402

ALLOWLIST_PATH = os.path.join(os.path.dirname(__file__), "real-time-waits-allowlist.txt")
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def repo_relative(path: str) -> str:
    abs_path = os.path.abspath(path)
    root = os.path.abspath(REPO_ROOT)
    try:
        common = os.path.commonpath([abs_path, root])
    except ValueError:
        return abs_path.replace("\\", "/")
    if common == root:
        return os.path.relpath(abs_path, root).replace("\\", "/")
    return abs_path.replace("\\", "/")

SLEEP_RES = [
    re.compile(r"\bThread\.sleep\s*\("),
    re.compile(r"\b(?:android\.os\.)?SystemClock\.sleep\s*\("),
    re.compile(r"\bTimeUnit\.[A-Z_]+\.sleep\s*\("),
]
DELAY_RE = re.compile(r"\bdelay\s*\(")
VIRTUAL_MARKERS = (
    "StandardTestDispatcher",
    "UnconfinedTestDispatcher",
    "runTest",
    "TestScope",
    "advanceTimeBy",
    "TestCoroutineScheduler",
)


def normalize_line(line: str) -> str:
    return " ".join(line.split())


def load_allowlist(path: str) -> list[tuple[str, str, str]]:
    rows: list[tuple[str, str, str]] = []
    with open(path, encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = [part.strip() for part in line.split("|", 2)]
            if len(parts) != 3:
                raise SystemExit(f"{path}: expected path | code | reason, got: {raw!r}")
            rows.append((parts[0], normalize_line(parts[1]), parts[2]))
    return rows


def line_at(body: str, index: int) -> str:
    start = body.rfind("\n", 0, index) + 1
    end = body.find("\n", index)
    if end < 0:
        end = len(body)
    return body[start:end]


def find_sleeps(path: str, body: str) -> list[tuple[int, str]]:
    hits: list[tuple[int, str]] = []
    for pattern in SLEEP_RES:
        for match in pattern.finditer(body):
            text = normalize_line(line_at(body, match.start()))
            hits.append((body.count("\n", 0, match.start()) + 1, text))
    return hits


def file_has_virtual_markers(raw: str) -> bool:
    return any(marker in raw for marker in VIRTUAL_MARKERS)


def count_delays(path: str, raw: str, stripped: str) -> int:
    if file_has_virtual_markers(raw):
        return 0
    return len(DELAY_RE.findall(stripped))


def main(argv: list[str]) -> int:
    roots = argv[1:] if len(argv) > 1 else [
        "app/src/test/java",
        "app/src/androidTest/java",
        "app/src/sharedTest/java",
    ]
    allowlist = load_allowlist(ALLOWLIST_PATH)
    by_path: dict[str, list[tuple[str, str]]] = {}
    for path, code, reason in allowlist:
        by_path.setdefault(path, []).append((code, reason))

    sleep_hits: list[tuple[str, int, str]] = []
    delay_total = 0
    matched_keys: set[tuple[str, str]] = set()
    unlisted = False

    for path in kotlin_files_in(roots):
        rel = repo_relative(path)
        if "/build/" in rel:
            continue
        raw = open(path, encoding="utf-8").read()
        stripped = strip_comments_and_strings(raw)
        for line_no, code in find_sleeps(rel, stripped):
            sleep_hits.append((rel, line_no, code))
            allowed = by_path.get(rel, [])
            if any(entry_code == code for entry_code, _ in allowed):
                matched_keys.add((rel, code))
            else:
                print(f"{rel}:{line_no}: real-time sleep not in allowlist")
                unlisted = True
        delay_total += count_delays(rel, raw, stripped)

    if unlisted:
        return 1

    stale = [
        (path, code, reason)
        for path, code, reason in allowlist
        if (path, code) not in matched_keys
    ]
    for path, code, _reason in stale:
        print(
            f"hint: allowlist entry has no matching sleep (safe to remove after ratchet): "
            f"{path} | {code}",
        )

    error = report_baseline("skips", "real_time_sleeps", len(sleep_hits))
    if error:
        print(error, file=sys.stderr)
        return 1
    error = report_baseline("skips", "real_time_delays", delay_total)
    if error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
