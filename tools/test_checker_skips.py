#!/usr/bin/env python3
"""Prove skip ceilings fail closed when a count grows."""
from __future__ import annotations

import io
import sys
from contextlib import redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import checker_baseline  # noqa: E402


def main() -> int:
    table = {
        "skips": {"state_members": 1, "required_args_mixed": 169},
        "tokens": {"haptic": 0},
    }
    buf = io.StringIO()
    with redirect_stdout(buf):
        ok = checker_baseline.report("skips", "state_members", 1, table)
        grew = checker_baseline.report("skips", "state_members", 2, table)
        drop = checker_baseline.report("skips", "state_members", 0, table)
        haptic = checker_baseline.report("tokens", "haptic", 1, table)
    if ok is not None:
        raise SystemExit(f"FAIL equal-to-ceiling should pass: {ok}")
    if grew is None or "grew past baseline 1" not in grew:
        raise SystemExit(f"FAIL growth must error: {grew}")
    if drop is not None:
        raise SystemExit(f"FAIL a drop must still pass: {drop}")
    if haptic is None:
        raise SystemExit("FAIL haptic 0→1 must error")
    print("ok  equal-to-ceiling passes")
    print(f"ok  growth fails: {grew}")
    print("ok  drop still passes")
    print(f"ok  advisory ceiling 0 fails closed: {haptic}")
    print("test_checker_skips: all assertions passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
