#!/usr/bin/env python3
"""Fixture proof for the gym-floor versionCode ratchet.

Not wired into preflight: J2 owns the rule, J5 owns checker harnesses.
Run: python3 tools/test_version_ratchet.py
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from version_ratchet import evaluate, list_v_tags, resolve_floor  # noqa: E402

GRADLE = """plugins {{
    id("com.android.application")
}}
val appVersionCode = {code}
val appVersionName = "{name}"
"""


def run(repo: Path, *args: str) -> None:
    subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True)


def init_repo(code: int = 1, name: str = "1.0.0") -> Path:
    root = Path(tempfile.mkdtemp(prefix="version-ratchet-"))
    (root / "app").mkdir()
    (root / "tools").mkdir()
    (root / "app" / "build.gradle.kts").write_text(
        GRADLE.format(code=code, name=name),
        encoding="utf-8",
    )
    (root / "tools" / "released-version-code.txt").write_text("1\n", encoding="utf-8")
    run(root, "init")
    run(root, "config", "user.email", "ratchet@test")
    run(root, "config", "user.name", "Ratchet Test")
    run(root, "add", "app/build.gradle.kts", "tools/released-version-code.txt")
    run(root, "commit", "-m", f"code {code}")
    return root


def tag(repo: Path, name: str) -> None:
    run(repo, "tag", name)


def set_code(repo: Path, code: int, name: str) -> None:
    (repo / "app" / "build.gradle.kts").write_text(
        GRADLE.format(code=code, name=name),
        encoding="utf-8",
    )
    run(repo, "add", "app/build.gradle.kts")
    run(repo, "commit", "-m", f"code {code}")


def expect(ok: bool, result, label: str) -> None:
    if result.ok != ok:
        raise SystemExit(
            f"FAIL {label}: expected ok={ok}, got ok={result.ok} {result.message!r}"
        )
    print(f"ok  {label}: {result.message}")


def main() -> int:
    # Everyday, no v* tags: floor file 1, current 1 passes.
    repo = init_repo(1)
    expect(True, evaluate(repo), "everyday current=1 no v* tags")

    # Everyday current below the file floor fails.
    set_code(repo, 0, "0.0.0")
    expect(False, evaluate(repo), "everyday current=0 below file floor 1")

    # debug-live tags must not become the floor.
    live = init_repo(1)
    tag(live, "debug-live-2026-09-03")
    floor = resolve_floor(live)
    if floor is None or floor.source != "tools/released-version-code.txt" or floor.code != 1:
        raise SystemExit(f"FAIL debug-live must not set the floor: {floor}")
    if list_v_tags(live):
        raise SystemExit(f"FAIL debug-live listed as v*: {list_v_tags(live)}")
    print("ok  debug-live-* tags are not gym-floor releases")

    # First v* may equal 1.
    first = init_repo(1)
    tag(first, "v1.0.0")
    expect(
        True,
        evaluate(first, tag_release=True, exclude_tag="v1.0.0"),
        "first v* current=1",
    )

    # Later v* with the same code fails (the yaml/file contradiction).
    later = init_repo(1)
    tag(later, "v1.0.0")
    set_code(later, 1, "1.0.1")
    tag(later, "v1.0.1")
    expect(
        False,
        evaluate(later, tag_release=True, exclude_tag="v1.0.1"),
        "second v* current=1 not above previous 1",
    )

    # Later v* with a bumped code passes.
    bumped = init_repo(1)
    tag(bumped, "v1.0.0")
    set_code(bumped, 2, "1.1.0")
    tag(bumped, "v1.1.0")
    expect(
        True,
        evaluate(bumped, tag_release=True, exclude_tag="v1.1.0"),
        "second v* current=2 > previous 1",
    )

    # After a v* exists, git is the floor; everyday may sit on the shipped code.
    sitting = init_repo(1)
    tag(sitting, "v1.0.0")
    expect(True, evaluate(sitting), "everyday sitting on last v* code")
    set_code(sitting, 0, "0.0.0")
    expect(False, evaluate(sitting), "everyday below previous v* code")

    # This checkout: no v* tags, current 1, file 1.
    here = Path(__file__).resolve().parents[1]
    live_result = evaluate(here)
    expect(True, live_result, "this checkout")
    if live_result.floor is None or live_result.floor.source != "tools/released-version-code.txt":
        raise SystemExit(
            f"FAIL this checkout must still use the file floor (no v* tags); "
            f"got {live_result.floor}"
        )
    print("ok  this checkout has no v* tags; file floor 1 is the fallback")
    print("test_version_ratchet: all assertions passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
