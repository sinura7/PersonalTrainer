#!/usr/bin/env python3
"""Every callback a composable accepts must actually be called.

A screen that takes `onOpenSettings` and never invokes it compiles, renders, and looks
completely finished — the control simply is not there any more. That is the failure mode a
redesign is most likely to introduce and least likely to notice, because nothing about it
is visible in a diff unless you are looking for an absence.

Kotlin's own unused-parameter warning does not cover this: these are used-by-construction
public API parameters, so the compiler stays quiet.

Usage:  tools/check-screen-wiring.py [source-root]
Exit code is the number of unwired callbacks.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kotlin_source import kotlin_files, strip_comments_and_strings  # noqa: E402

ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"

FUN_RE = re.compile(r"^(?:private\s+|internal\s+)?fun ([A-Z]\w*)\(", re.M)
CALLBACK_RE = re.compile(r"\b(on[A-Z]\w*)\s*:")


def closing_paren(src: str, open_index: int) -> int:
    depth = 0
    for i in range(open_index, len(src)):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
            if depth == 0:
                return i
    return -1


def main() -> int:
    problems = []
    for path in kotlin_files(ROOT):
        if path.endswith("ViewModel.kt"):
            continue
        src = strip_comments_and_strings(open(path, encoding="utf-8").read())
        for match in FUN_RE.finditer(src):
            open_index = match.end() - 1
            end = closing_paren(src, open_index)
            if end < 0:
                continue
            params = src[open_index + 1:end]
            body = src[end:]
            for callback in CALLBACK_RE.finditer(params):
                name = callback.group(1)
                if not re.search(rf"\b{name}\b", body):
                    line = src.count("\n", 0, match.start()) + 1
                    problems.append(
                        f"{path}:{line}  {match.group(1)}() never calls its '{name}' parameter"
                    )

    for problem in sorted(problems):
        print(problem)
    print()
    print(f"{len(problems)} unwired callback(s)")
    return len(problems)


if __name__ == "__main__":
    sys.exit(min(main(), 255))
