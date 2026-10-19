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
Thread.sleep(, SystemClock.sleep( (with or without android.os.), TimeUnit.<UNIT>.sleep(,
Thread::sleep, SystemClock::sleep, and TimeUnit.*::sleep method references.
Whitespace may appear around dots; TimeUnit chains may break across lines.
Every hit must match an allowlist row: relative path, stripped source line text, reason,
and site count (fourth field, required). An unlisted hit prints
``path:line: real-time sleep not in allowlist: <span>`` and fails. A stale or under-covered
allowlist row fails (Lane B removes a sleep, then drops or tightens the row and ratchets
the ceiling).

Forbidden imports (always fail): ``import java.lang.Thread.sleep``,
``import java.util.concurrent.TimeUnit.<UNIT>``, ``import kotlinx.coroutines.delay as``,
and import aliases of Thread, SystemClock, or TimeUnit.

Family 2, real_time_delays
---------------------------
delay( in a test file that has none of the virtual-time marker strings in
*comment-stripped* source outside import lines: StandardTestDispatcher, UnconfinedTestDispatcher,
runTest, TestScope, advanceTimeBy, TestCoroutineScheduler. A ``// runTest`` comment does not exempt
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
METHOD_REF_SLEEP_RES = [
    re.compile(r"\b(Thread|SystemClock)\s*::\s*sleep\b", _DOTALL),
    re.compile(r"\bTimeUnit(?:\s*\.\s*[A-Z_]+)+\s*::\s*sleep\b", _DOTALL),
]
DELAY_RE = re.compile(r"\bdelay\s*\(")
IMPORT_LINE_RE = re.compile(r"^\s*import\b")
VIRTUAL_MARKER_RES = [
    re.compile(r"\bStandardTestDispatcher\b"),
    re.compile(r"\bUnconfinedTestDispatcher\b"),
    re.compile(r"\brunTest\b"),
    re.compile(r"\bTestScope\b"),
    re.compile(r"\badvanceTimeBy\b"),
    re.compile(r"\bTestCoroutineScheduler\b"),
]
FORBIDDEN_IMPORT_RES: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\bimport\s+java\.lang\.Thread\.sleep\b"), "java.lang.Thread.sleep"),
    (
        re.compile(r"\bimport\s+java\.util\.concurrent\.TimeUnit\.[A-Z_]+\b"),
        "java.util.concurrent.TimeUnit.*",
    ),
    (re.compile(r"\bimport\s+kotlinx\.coroutines\.delay\s+as\b"), "kotlinx.coroutines.delay as"),
    (re.compile(r"\bimport\s+(?:java\.lang\.)?Thread\s+as\b"), "Thread import alias"),
    (re.compile(r"\bimport\s+(?:android\.os\.)?SystemClock\s+as\b"), "SystemClock import alias"),
    (
        re.compile(r"\bimport\s+(?:java\.util\.concurrent\.)?TimeUnit\s+as\b"),
        "TimeUnit import alias",
    ),
]
ASCII_DIGITS = re.compile(r"^[0-9]+$")


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


def normalize_allowlist_path(path_part: str) -> str:
    if os.path.isabs(path_part):
        raise SystemExit(f"allowlist path must be repo-relative, got absolute path {path_part!r}")
    normalized = os.path.normpath(path_part).replace("\\", "/")
    if os.path.isabs(normalized):
        raise SystemExit(f"allowlist path must be repo-relative, got absolute path {path_part!r}")
    if normalized.startswith("../") or normalized == "..":
        raise SystemExit(f"allowlist path must be repo-relative, got {path_part!r}")
    return normalized


def is_test_source_set_name(name: str) -> bool:
    return (
        name.startswith("test")
        or name.startswith("androidTest")
        or name.endswith("Test")
    )


def module_build_file(module_dir: str) -> str | None:
    for filename in ("build.gradle.kts", "build.gradle"):
        if os.path.isfile(os.path.join(module_dir, filename)):
            return filename
    return None


def load_allowlist(path: str) -> list[tuple[str, str, str, int]]:
    rows: list[tuple[str, str, str, int]] = []
    seen: set[tuple[str, str]] = set()
    with open(path, encoding="utf-8-sig") as handle:
        for line_no, raw in enumerate(handle, 1):
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = [part.strip() for part in raw.strip().split("|")]
            if len(parts) != 4:
                raise SystemExit(
                    f"{path}:{line_no}: expected path | code | reason | count, got {len(parts)} fields",
                )
            path_part, code_part, reason, count_text = parts
            try:
                path_part = normalize_allowlist_path(path_part)
            except SystemExit as exc:
                raise SystemExit(f"{path}:{line_no}: {exc}") from None
            if not reason:
                raise SystemExit(f"{path}:{line_no}: allowlist reason must be non-empty")
            if not ASCII_DIGITS.fullmatch(count_text):
                raise SystemExit(
                    f"{path}:{line_no}: site count must be ASCII digits only, got {count_text!r}",
                )
            site_count = int(count_text)
            if site_count < 1:
                raise SystemExit(
                    f"{path}:{line_no}: site count must be >= 1, got {site_count}",
                )
            key = (path_part, normalize_line(code_part))
            if key in seen:
                raise SystemExit(f"{path}:{line_no}: duplicate allowlist row for {path_part!r}")
            seen.add(key)
            rows.append((path_part, key[1], reason, site_count))
    return rows


def line_at(body: str, index: int) -> str:
    start = body.rfind("\n", 0, index) + 1
    end = body.find("\n", index)
    if end < 0:
        end = len(body)
    return body[start:end]


def find_sleeps(path: str, body: str) -> list[tuple[int, str, str]]:
    hits: list[tuple[int, str, str]] = []
    for pattern in SLEEP_RES:
        for match in pattern.finditer(body):
            line_no = body.count("\n", 0, match.start()) + 1
            text = normalize_line(line_at(body, match.start()))
            span = normalize_line(match.group(0))
            hits.append((line_no, text, span))
    return hits


def find_method_ref_sleeps(body: str) -> list[tuple[int, str]]:
    found: list[tuple[int, str]] = []
    for pattern in METHOD_REF_SLEEP_RES:
        for match in pattern.finditer(body):
            line_no = body.count("\n", 0, match.start()) + 1
            found.append((line_no, normalize_line(match.group(0))))
    return found


def find_forbidden_imports(body: str) -> list[tuple[int, str]]:
    found: list[tuple[int, str]] = []
    for pattern, label in FORBIDDEN_IMPORT_RES:
        for match in pattern.finditer(body):
            line_no = body.count("\n", 0, match.start()) + 1
            found.append((line_no, label))
    return found


def file_has_virtual_markers(stripped: str) -> bool:
    for line in stripped.splitlines():
        if IMPORT_LINE_RE.match(line):
            continue
        for pattern in VIRTUAL_MARKER_RES:
            if pattern.search(line):
                return True
    return False


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
    allowlist: list[tuple[str, str, str, int]],
    sleep_hits: list[tuple[str, int, str, str]],
) -> tuple[bool, bool]:
    """Return (unlisted, stale). Consumes hits against rows; leftover hits are unlisted."""
    remaining: Counter[tuple[str, str]] = Counter()
    hit_lines: dict[tuple[str, str], list[tuple[int, str]]] = {}
    for rel, line_no, code, span in sleep_hits:
        remaining[(rel, code)] += 1
        hit_lines.setdefault((rel, code), []).append((line_no, span))

    stale = False
    unlisted = False
    for path, code, _reason, need in allowlist:
        key = (path, code)
        have = remaining[key]
        if have == 0:
            print(
                f"allowlist entry has no matching sleep: {path} | {code}",
            )
            stale = True
        elif have < need:
            print(
                f"allowlist entry under-covered ({have} of {need} sites): {path} | {code}",
            )
            stale = True
            remaining[key] = 0
        elif have > need:
            for line_no, span in hit_lines.get(key, []):
                print(
                    f"{path}:{line_no}: allowlist allows {need} site(s), {have} found: {span}",
                )
            unlisted = True
            remaining[key] = 0
        else:
            remaining[key] -= need

    for (rel, code), left in remaining.items():
        if left <= 0:
            continue
        unlisted = True
        for line_no, span in hit_lines.get((rel, code), [])[:left]:
            print(f"{rel}:{line_no}: real-time sleep not in allowlist: {span}")

    return unlisted, stale


DISCOVERY_SKIP_DIRS = frozenset({".git", ".gradle", ".idea", ".kotlin", "node_modules", "build"})


def _prune_discovery_dirnames(dirnames: list[str], inside_test_set: bool) -> None:
    for name in list(dirnames):
        if name in DISCOVERY_SKIP_DIRS or name.startswith("."):
            dirnames.remove(name)
    if inside_test_set and "src" in dirnames:
        dirnames.remove("src")


def _inside_known_test_set(rel_dir: str, known_roots: set[str]) -> bool:
    for root in known_roots:
        if rel_dir == root or rel_dir.startswith(f"{root}/"):
            return True
    return False


def _nested_src_under_main_source_set(rel_parts: list[str]) -> bool:
    """Ignore nested src/<set> trees under a module's main source set (not Gradle test roots)."""
    for idx in range(len(rel_parts) - 2):
        if rel_parts[idx + 1] == "src" and rel_parts[idx + 2] == "main":
            return True
    return False


def discover_test_source_roots(repo_root: str) -> list[str]:
    """Every module src/<set> tree whose set name is a Gradle test source set."""
    repo_root = os.path.abspath(repo_root)
    roots: list[str] = []
    seen_rel: set[str] = set()

    for dirpath, dirnames, _filenames in os.walk(repo_root):
        rel_dir = os.path.relpath(dirpath, repo_root).replace("\\", "/")
        if rel_dir == ".":
            rel_parts: list[str] = []
        else:
            rel_parts = rel_dir.split("/")
        inside = _inside_known_test_set(rel_dir, seen_rel)
        _prune_discovery_dirnames(dirnames, inside)
        if len(rel_parts) < 2 or rel_parts[-2] != "src":
            continue
        set_name = rel_parts[-1]
        if not is_test_source_set_name(set_name):
            continue
        module_rel = "/".join(rel_parts[:-2])
        module_dir = os.path.join(repo_root, module_rel) if module_rel else repo_root
        rel_set = rel_dir
        if module_build_file(module_dir) is None:
            if inside or _nested_src_under_main_source_set(rel_parts):
                continue
            raise SystemExit(
                f"{rel_set}: test source set is not under a recognised Gradle module "
                f"(expected build.gradle or build.gradle.kts in {module_rel or '.'})",
            )
        if rel_set not in seen_rel:
            seen_rel.add(rel_set)
            roots.append(rel_set)
    return sorted(roots)


def default_roots() -> list[str]:
    return discover_test_source_roots(REPO_ROOT)


def main(argv: list[str]) -> int:
    roots = argv[1:] if len(argv) > 1 else default_roots()
    try:
        allowlist = load_allowlist(ALLOWLIST_PATH)
    except SystemExit as exc:
        print(str(exc), file=sys.stderr)
        return 1

    sleep_hits: list[tuple[str, int, str, str]] = []
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
        for line_no, span in find_method_ref_sleeps(stripped):
            print(f"{rel}:{line_no}: forbidden real-time sleep reference: {span}")
            failed = True
        for line_no, code, span in find_sleeps(rel, stripped):
            sleep_hits.append((rel, line_no, code, span))
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
