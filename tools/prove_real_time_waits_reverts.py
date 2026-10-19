#!/usr/bin/env python3
"""Scratch-copy proof that key fixtures fail when their checker fix is reverted."""
from __future__ import annotations

import importlib.util
import io
import shutil
import sys
import tempfile
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

TOOLS = Path(__file__).resolve().parent


def load_checker(scratch_tools: Path):
    path = scratch_tools / "check-real-time-waits.py"
    spec = importlib.util.spec_from_file_location("checker", path)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


def load_tests(scratch_tools: Path):
    path = scratch_tools / "test_real_time_waits.py"
    spec = importlib.util.spec_from_file_location("tests", path)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.path.insert(0, str(scratch_tools))
    spec.loader.exec_module(mod)
    return mod


def patch(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"revert anchor missing in {path.name}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def run_fixture_suite(scratch_tools: Path) -> bool:
    buf_out, buf_err = io.StringIO(), io.StringIO()
    with redirect_stdout(buf_out), redirect_stderr(buf_err):
        try:
            tests = load_tests(scratch_tools)
            tests.main()
        except SystemExit as exc:
            return exc.code not in (0, None)
        except Exception:
            return True
    return "all assertions passed" not in buf_out.getvalue()


def revert_case(label: str, fn) -> str:
    with tempfile.TemporaryDirectory(prefix="rtw-revert-") as tmp:
        scratch = Path(tmp) / "tools"
        shutil.copytree(TOOLS, scratch, ignore=shutil.ignore_patterns("prove_real_time_waits_reverts.py"))
        fn(scratch)
        failed = run_fixture_suite(scratch)
        status = "FAIL (expected)" if failed else "PASS (unexpected)"
        return f"| {label} | {status} |"


def main() -> int:
    cases = [
        (
            "default_roots uses discover_test_source_roots",
            lambda s: patch(
                s / "check-real-time-waits.py",
                "def default_roots() -> list[str]:\n    return discover_test_source_roots(REPO_ROOT)",
                "def default_roots() -> list[str]:\n    return ['app/src/test/java']",
            ),
        ),
        (
            "normalize_allowlist_path / normpath duplicates",
            lambda s: patch(
                s / "check-real-time-waits.py",
                "path_part = normalize_allowlist_path(path_part)",
                "path_part = path_part",
            ),
        ),
        (
            "orphan test set under unrecognised module",
            lambda s: patch(
                s / "check-real-time-waits.py",
                "        if module_build_file(module_dir) is None:\n",
                "        if False and module_build_file(module_dir) is None:\n",
            ),
        ),
        (
            "build.gradle Groovy module marker",
            lambda s: patch(
                s / "check-real-time-waits.py",
                '    for filename in ("build.gradle.kts", "build.gradle"):',
                '    for filename in ("build.gradle.kts",):',
            ),
        ),
        (
            "androidTestDebug via startswith(androidTest)",
            lambda s: patch(
                s / "check-real-time-waits.py",
                '        or name.startswith("androidTest")',
                '        or name == "androidTest"',
            ),
        ),
        (
            "TimeUnit::sleep method reference",
            lambda s: patch(
                s / "check-real-time-waits.py",
                '    re.compile(r"\\bTimeUnit(?:\\s*\\.\\s*[A-Z_]+)+\\s*::\\s*sleep\\b", _DOTALL),\n',
                "",
            ),
        ),
    ]
    print("| Reverted fix | Fixture suite |")
    print("|---|---|")
    for label, fn in cases:
        print(revert_case(label, fn))
    return 0


if __name__ == "__main__":
    sys.exit(main())
