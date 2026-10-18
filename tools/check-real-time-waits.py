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
Whitespace may appear around dots; TimeUnit chains may break across lines.
Every hit must match an allowlist row: relative path, stripped source line text, reason,
and optional site count (fourth field). An unlisted hit prints
``path:line: real-time sleep not in allowlist`` and fails. A stale or under-covered
allowlist row fails (Lane B removes a sleep, then drops or tightens the row and ratchets
the ceiling).

Forbidden imports (always fail): ``import java.lang.Thread.sleep``,
``import java.util.concurrent.TimeUnit.<UNIT>``, ``import kotlinx.coroutines.delay as``.

Family 2, real_time_delays
---------------------------
delay( in a test file that has none of the virtual-time marker strings in
*comment-stripped* source: StandardTestDispatcher, UnconfinedTestDispatcher, runTest,
TestScope, advanceTimeBy, TestCoroutineScheduler. A ``// runTest`` comment does not exempt
the file. File-level heuristic only.

Ceilings in checker-baselines.toml must equal the measured counts exactly; growth and
drops both fail until the baseline is updated.

Usage:  tools/check-real-time-waits.py [test-root ...]
Exit 0 only when every hit is allowlisted, no forbidden import, counts match ceilings.
"""
from __future__ import annotations

import os
import re
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from checker_baseline import ceiling, load as load_baselines  # noqa: E402
from kotlin_source import kotlin_files_in, strip_comments_and_strings  # noqa: E402

ALLOWLIST_PATH = os.path.join(os.path.dirname(__file__), "real-time-waits-allowlist.txt")
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

_DOTALL = re.DOTALL

SLEEP_RES = [
    re.compile(r"\bThread\s*\.\s*sleep\s*\(", _DOTALL),
    re.compile(r"\b(?:android\.os\.)?SystemClock\s*\.\s*sleep\s*\(", _DOTALL),
    re.compile(r"\bTimeUnit(?:\s*\.\s*[A-Z_]+)+\s*\.\s*sleep\s*\(", _DOTALL),
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
FORBIDDEN_IMPORT_RES: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\bimport\s+java\.lang\.Thread\.sleep\b"), "java.lang.Thread.sleep"),
    (
        re.compile(r"\bimport\s+java\.util\.concurrent\.TimeUnit\.[A-Z_]+\b"),
        "java.util.concurrent.TimeUnit.*",
    ),
    (re.compile(r"\bimport\s+kotlinx\.coroutines\.delay\s+as\b"), "kotlinx.coroutines.delay as"),
]


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


def normalize_line(line: str) -> str:
    return " ".join(line.split())


def load_allowlist(path: str) -> list[tuple[str, str, str, int | None]]:
    rows: list[tuple[str, str, str, int | None]] = []
    with open(path, encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = [part.strip() for part in raw.strip().split("|")]
            if len(parts) < 3:
                raise SystemExit(f"{path}: expected path | code | reason [| count], got: {raw!r}")
            path_part, code_part, reason = parts[0], parts[1], parts[2]
            site_count: int | None = None
            if len(parts) >= 4 and parts[3]:
                site_count = int(parts[3])
            rows.append((path_part, normalize_line(code_part), reason, site_count))
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


def find_forbidden_imports(body: str) -> list[tuple[int, str]]:
    found: list[tuple[int, str]] = []
    for pattern, label in FORBIDDEN_IMPORT_RES:
        for match in pattern.finditer(body):
            line_no = body.count("\n", 0, match.start()) + 1
            found.append((line_no, label))
    return found


def file_has_virtual_markers(stripped: str) -> bool:
    return any(marker in stripped for marker in VIRTUAL_MARKERS)


def count_delays(path: str, raw: str, stripped: str) -> int:
    if file_has_virtual_markers(stripped):
        return 0
    return len(DELAY_RE.findall(stripped))


def report_exact_count(
    group: str,
    key: str,
    actual: int,
    table: dict | None = None,
) -> str | None:
    """Fail unless actual equals the committed ceiling (no silent below)."""
    data = table if table is not None else load_baselines()
    limit = ceiling(data, group, key)
    if actual > limit:
        print(f"{key}: {actual}  (baseline {limit})  GREW")
        return f"{key} grew past baseline {limit}: {actual}"
    if actual < limit:
        print(f"{key}: {actual}  (baseline {limit})  below — ratchet checker-baselines.toml")
        return f"{key} below baseline {limit}: {actual} (ratchet checker-baselines.toml)"
    print(f"{key}: {actual}  (baseline {limit})")
    return None


def reconcile_allowlist(
    allowlist: list[tuple[str, str, str, int | None]],
    sleep_hits: list[tuple[str, int, str]],
) -> tuple[bool, bool]:
    """Return (unlisted, stale). Consumes hits against rows; leftover hits are unlisted."""
    remaining: Counter[tuple[str, str]] = Counter()
    for rel, _line_no, code in sleep_hits:
        remaining[(rel, code)] += 1

    stale = False
    for path, code, _reason, need in allowlist:
        key = (path, code)
        have = remaining[key]
        if need is None:
            if have == 0:
                print(
                    f"allowlist entry has no matching sleep: {path} | {code}",
                )
                stale = True
            else:
                remaining[key] = 0
        elif have < need:
            print(
                f"allowlist entry under-covered ({have} of {need} sites): {path} | {code}",
            )
            stale = True
        else:
            remaining[key] -= need

    unlisted = False
    for (rel, code), left in remaining.items():
        if left <= 0:
            continue
        unlisted = True
        print(f"{rel}: allowlist missing {left} site(s) for line text {code!r}")

    return unlisted, stale


def default_roots() -> list[str]:
    return [
        "app/src/test/java",
        "app/src/androidTest/java",
        "app/src/sharedTest/java",
        "tempo/src/test/kotlin",
    ]


def main(argv: list[str]) -> int:
    roots = argv[1:] if len(argv) > 1 else default_roots()
    allowlist = load_allowlist(ALLOWLIST_PATH)

    sleep_hits: list[tuple[str, int, str]] = []
    delay_total = 0
    failed = False

    for path in kotlin_files_in(roots):
        rel = repo_relative(path)
        if "/build/" in rel:
            continue
        raw = open(path, encoding="utf-8").read()
        stripped = strip_comments_and_strings(raw)
        for line_no, label in find_forbidden_imports(stripped):
            print(f"{rel}:{line_no}: forbidden real-time import: {label}")
            failed = True
        for line_no, code in find_sleeps(rel, stripped):
            sleep_hits.append((rel, line_no, code))
        delay_total += count_delays(rel, raw, stripped)

    unlisted, stale = reconcile_allowlist(allowlist, sleep_hits)
    if unlisted or stale:
        failed = True

    if failed:
        return 1

    error = report_exact_count("skips", "real_time_sleeps", len(sleep_hits))
    if error:
        print(error, file=sys.stderr)
        return 1
    error = report_exact_count("skips", "real_time_delays", delay_total)
    if error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
