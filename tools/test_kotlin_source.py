#!/usr/bin/env python3
"""Fixture proof for kotlin_source.strip_comments_and_strings, which every checker reads through.

Run: python3 tools/test_kotlin_source.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from kotlin_source import strip_comments_and_strings as strip  # noqa: E402


def expect(condition: bool, label: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL {label}")
    print(f"ok  {label}")


def main() -> int:
    plain = 'val a = "HIDDEN_TEXT" // A_COMMENT\n/* BLOCK_TEXT */ val b = 1\n'
    out = strip(plain)
    expect(len(out) == len(plain) and out.count("\n") == plain.count("\n"), "offsets and lines are kept")
    expect("HIDDEN_TEXT" not in out and "A_COMMENT" not in out and "BLOCK_TEXT" not in out,
           "string text and comments are blanked")
    expect("val a" in out and "val b = 1" in out, "code around them stays")

    templated = 'val w = "${weight.toWeightLabel(unit)} kg"\n'
    out = strip(templated)
    expect("weight.toWeightLabel(unit)" in out and "kg" not in out,
           "an interpolation's code is kept, the literal text is not")

    # The 23 September false positive: the nested literal read as an undeclared constant.
    nested = 'val e = "${System.getenv("PT_FOO_BAR")} tail"\n'
    out = strip(nested)
    expect("System.getenv(" in out, "code inside an interpolation stays")
    expect("PT_FOO_BAR" not in out and "tail" not in out, "a string nested in an interpolation is blanked")

    brace = 'val s = "${f("}")} AFTER_TEXT"\nval next = OUTSIDE_CODE\n'
    out = strip(brace)
    expect("AFTER_TEXT" not in out, "a brace inside a nested string does not end the interpolation")
    expect("OUTSIDE_CODE" in out, "code after the string is still code")

    simple = 'val t = "$name has sets"\n'
    out = strip(simple)
    expect("name" in out and "has sets" not in out, "a simple $name interpolation keeps the name")

    print("test_kotlin_source: all assertions passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
