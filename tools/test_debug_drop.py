#!/usr/bin/env python3
"""Fixture proof for the Temper Debug drop rules.

Not wired into preflight: the checker is, and this proves the checker's rule
the way test_version_ratchet.py proves the gym-floor one.
Run: python3 tools/test_debug_drop.py
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from debug_drop import (  # noqa: E402
    check_code,
    code_at_tag,
    next_free_suffix,
    plan_drop,
)

GRADLE = """plugins {{
    id("com.android.application")
}}
val appVersionCode = 1
val appVersionName = "1.0.0"
val debugLiveCode = {code}
"""

PRE_CONSTANT = """plugins {
    id("com.android.application")
}
val appVersionCode = 1
val appVersionName = "1.0.0"
"""


def run(repo: Path, *args: str) -> None:
    subprocess.run(["git", "-C", str(repo), *args], check=True, capture_output=True)


def write_gradle(repo: Path, text: str) -> None:
    (repo / "app" / "build.gradle.kts").write_text(text, encoding="utf-8")


def init_repo(code: int = 1) -> Path:
    root = Path(tempfile.mkdtemp(prefix="debug-drop-"))
    (root / "app").mkdir()
    write_gradle(root, GRADLE.format(code=code))
    run(root, "init")
    run(root, "config", "user.email", "drop@test")
    run(root, "config", "user.name", "Drop Test")
    run(root, "add", "app/build.gradle.kts")
    run(root, "commit", "-m", f"code {code}")
    return root


def commit_code(repo: Path, code: int, tag: str | None = None, note: str | None = None) -> None:
    """Commit a drop code, optionally alongside an unrelated change.

    [note] exists because the incident this file replays is two commits that
    changed different app code and carried the SAME drop number. Without
    something else to write, the second commit is empty and git refuses it —
    which would quietly make the replay a different, easier story.
    """
    write_gradle(repo, GRADLE.format(code=code))
    run(repo, "add", "app/build.gradle.kts")
    if note is not None:
        (repo / "app" / "note.txt").write_text(note, encoding="utf-8")
        run(repo, "add", "app/note.txt")
    run(repo, "commit", "-m", f"code {code}")
    if tag is not None:
        run(repo, "tag", tag)


def expect(condition: bool, label: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL {label}")
    print(f"ok  {label}")


def main() -> int:
    repo = init_repo(code=1)

    # No drop tags at all: honest about why, and not a refusal.
    result = check_code(repo)
    expect(result.ok, "a repository that has never dropped passes and says so")
    expect("no debug-live-*" in result.message, "and names the reason")

    # The tree still carries 2 after that drop shipped 2.
    commit_code(repo, 2, tag="debug-live-2026-09-10")
    result = check_code(repo)
    expect(not result.ok, "a code equal to a shipped drop is refused")
    expect("use 3" in result.message, "and the refusal names the number to use")

    commit_code(repo, 3)
    expect(check_code(repo).ok, "a code above every shipped drop passes")

    # The floor is the highest shipped code, not the newest tag's.
    commit_code(repo, 9, tag="debug-live-2026-09-11")
    commit_code(repo, 4, tag="debug-live-2026-09-12")
    commit_code(repo, 5)
    result = check_code(repo)
    expect(not result.ok, "a later drop carrying a lower code does not lower the floor")
    expect("use 10" in result.message, "the floor is the highest ever shipped")

    # THE INCIDENT. Two packets both bumped 34 -> 35 and both dropped; -6 and -7
    # each shipped 35, so Obtainium was never offered the second one.
    incident = init_repo(code=34)
    commit_code(incident, 35, tag="debug-live-2026-09-10-6", note="tick follow-ups")
    commit_code(incident, 35, tag="debug-live-2026-09-10-7", note="the RPE fix")
    result = check_code(incident)
    expect(not result.ok, "the 10 Sep duplicate is caught")
    expect("use 36" in result.message, "and the way out is named")

    # A drop from before the constant existed shipped appVersionCode, which was 1.
    legacy = init_repo(code=1)
    write_gradle(legacy, PRE_CONSTANT)
    run(legacy, "add", "app/build.gradle.kts")
    run(legacy, "commit", "-m", "before the constant")
    run(legacy, "tag", "debug-live-2026-08-27")
    expect(
        code_at_tag(legacy, "debug-live-2026-08-27") == 1,
        "a pre-constant drop reads as the 1 it shipped, not as a hole",
    )

    # Fail closed: not a git repository at all.
    bare = Path(tempfile.mkdtemp(prefix="debug-drop-bare-"))
    (bare / "app").mkdir()
    write_gradle(bare, GRADLE.format(code=5))
    result = check_code(bare)
    expect(not result.ok, "somewhere git cannot answer is a refusal, not a pass")

    # A build file with no code at all is a refusal.
    missing = init_repo(code=1)
    write_gradle(missing, PRE_CONSTANT)
    expect(not check_code(missing).ok, "no debugLiveCode in the tree is a refusal")

    # Suffix allocation: the first free name for the day.
    taken = ["debug-live-2026-09-10", "debug-live-2026-09-10-2"]
    expect(
        next_free_suffix(taken, "2026-09-10") == "2026-09-10-3",
        "the first free same-day suffix is chosen",
    )
    expect(
        next_free_suffix([], "2026-09-10") == "2026-09-10",
        "a day with no drops takes the bare date",
    )
    expect(
        next_free_suffix(["debug-live-2026-09-09-8"], "2026-09-10") == "2026-09-10",
        "yesterday's drops do not push today along",
    )
    expect(
        next_free_suffix(taken, "2026-09-10", limit=2) is None,
        "a day with no room inside the limit refuses rather than guesses",
    )

    plan = plan_drop(repo, today="2026-09-10")
    expect(plan.ok and plan.suffix == "2026-09-10-2", "plan_drop skips the taken suffix")
    expect(plan.tag == "debug-live-2026-09-10-2", "and names the tag it would claim")
    expect(not plan_drop(repo, today="10-09-2026").ok, "a suffix that is not a date is refused")
    expect(not plan_drop(bare, today="2026-09-10").ok, "no git means no plan")

    print("test_debug_drop: all assertions passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
