"""Committed skip / advisory ceilings for the static checkers.

A checker that cannot decide a case must say so. Growth past the committed
number fails the gate; a drop prints a ratchet hint and still passes.
"""
from __future__ import annotations

import sys
from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError:  # pragma: no cover — Python 3.10
    tomllib = None  # type: ignore[assignment]

BASELINE_PATH = Path(__file__).resolve().parent / "checker-baselines.toml"


def load() -> dict:
    if tomllib is None:
        print("checker_baseline: Python 3.11+ required (tomllib)", file=sys.stderr)
        sys.exit(2)
    with BASELINE_PATH.open("rb") as fh:
        return tomllib.load(fh)


def ceiling(table: dict, group: str, key: str) -> int:
    try:
        return int(table[group][key])
    except (KeyError, TypeError, ValueError):
        print(
            f"checker_baseline: missing [{group}].{key} in {BASELINE_PATH.name}",
            file=sys.stderr,
        )
        sys.exit(2)


def report(group: str, key: str, actual: int, table: dict | None = None) -> str | None:
    """Print the count. Return an error line if actual grew past the ceiling."""
    data = table if table is not None else load()
    limit = ceiling(data, group, key)
    if actual > limit:
        print(f"{key}: {actual}  (baseline {limit})  GREW")
        return f"{key} grew past baseline {limit}: {actual}"
    if actual < limit:
        print(f"{key}: {actual}  (baseline {limit})  below — ratchet {BASELINE_PATH.name}")
        return None
    print(f"{key}: {actual}  (baseline {limit})")
    return None
