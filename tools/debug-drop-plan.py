#!/usr/bin/env python3
"""Name the next free Temper Debug drop. See debug_drop.py for the rules."""
from __future__ import annotations

import argparse
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from debug_drop import check_code, plan_drop  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="The next free debug-live suffix, and whether the tree may drop at all",
    )
    parser.add_argument(
        "--root",
        default=str(ROOT),
        help="Repository root (tests pass a fixture)",
    )
    parser.add_argument(
        "--today",
        default=None,
        help="YYYY-MM-DD to plan for (defaults to today, UTC — the clock GitHub tags in)",
    )
    parser.add_argument(
        "--no-fetch",
        action="store_true",
        help="Do not refresh tags first (tests pass a fixture with no remote)",
    )
    args = parser.parse_args()
    repo = Path(args.root)
    today = args.today or datetime.now(timezone.utc).strftime("%Y-%m-%d")

    # Tags are the whole answer, and a clone only knows the ones it has fetched.
    # A drop cut minutes ago from another session is invisible until then, so this
    # would cheerfully name a suffix that is already taken. The workflow's own
    # checkout fetches everything; a laptop does not.
    if not args.no_fetch:
        fetched = subprocess.run(
            ["git", "-C", str(repo), "fetch", "origin", "--tags", "--quiet"],
            capture_output=True,
            text=True,
            check=False,
        )
        if fetched.returncode != 0:
            print("debug-drop: could not refresh tags; this view may be stale")

    # The suffix is only half of it. A free name carrying a code that has already
    # shipped is a drop the phone will never be offered, so both are printed and
    # either one failing is a refusal.
    code = check_code(repo)
    plan = plan_drop(repo, today=today)
    print(code.message)
    print(plan.message)
    if code.ok and plan.ok:
        print(f"git push origin <sha>:refs/heads/debug-live/{plan.suffix}")
    return 0 if code.ok and plan.ok else 1


if __name__ == "__main__":
    sys.exit(main())
