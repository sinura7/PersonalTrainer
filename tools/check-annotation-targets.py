#!/usr/bin/env python3
"""Find annotations that no longer sit on a declaration.

The gap this closes
-------------------
Inserting a function above an existing one is a two-line edit that a regex-based patch gets
subtly wrong: the new text lands between the old function's KDoc-plus-`@Composable` and the
`fun` they belonged to. The result reads fine and is a hard compile error — the stranded
`@Composable` binds to the NEW function, which already has its own, so Kotlin rejects it as a
duplicate annotation. Every other check here passed on exactly that file.

`syntax-check.sh` cannot see it either: annotation binding is resolution, not parsing, and the
diagnostic it would produce is filtered out with the rest of the semantic noise that an
Android-SDK-less classpath generates.

What it reports
---------------
An annotation line is a finding when the next meaningful line is neither another annotation nor
something an annotation can attach to. Comments and blank lines are skipped, since an annotation
followed by a KDoc and then a declaration is legal Kotlin — but the SAME annotation appearing
twice across that gap is not, and that is the duplicate case.

Usage:  tools/check-annotation-targets.py [root ...]
Exit code is the number of findings.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kotlin_source import kotlin_files  # noqa: E402

ROOTS = sys.argv[1:] or ["app/src/main/java", "app/src/test/java"]

ANNOTATION_RE = re.compile(r"^\s*@([A-Za-z_]\w*)(?:\([^)]*\))?\s*$")
# What an annotation may precede: another annotation, a declaration, or a parameter/receiver.
DECLARES_RE = re.compile(
    r"^\s*(?:@|(?:public|private|internal|protected|open|abstract|sealed|data|value|enum|"
    r"annotation|inline|suspend|external|override|operator|infix|tailrec|const|lateinit|"
    r"expect|actual|companion|inner|final)\s+)*"
    r"(?:class|object|interface|fun|val|var|typealias|constructor|init|get|set)\b"
)
COMMENT_START_RE = re.compile(r"^\s*(?://|/\*)")

# Kotlin lets these sit on an expression or a statement, not only on a declaration —
# `@Suppress("DEPRECATION") vibrator.vibrate(...)` is correct code. They are exempt from the
# "must be followed by a declaration" half of this check; the duplicate-annotation half still
# applies to them, because two of the same across a doc comment is wrong wherever it appears.
EXPRESSION_TARGETABLE = {"Suppress", "OptIn"}


def findings_for(path):
    lines = open(path, encoding="utf-8").read().split("\n")
    found = []
    in_block_comment = False
    for i, line in enumerate(lines):
        stripped = line.strip()
        if in_block_comment:
            if "*/" in stripped:
                in_block_comment = False
            continue
        if stripped.startswith("/*") and "*/" not in stripped:
            in_block_comment = True
            continue
        match = ANNOTATION_RE.match(line)
        if not match:
            continue

        # Walk forward past comments and blank lines to the next real line.
        seen_comment = False
        j = i + 1
        while j < len(lines):
            nxt = lines[j].strip()
            if not nxt:
                j += 1
                continue
            if COMMENT_START_RE.match(nxt) or nxt.startswith("*"):
                seen_comment = True
                if nxt.startswith("/*") and "*/" not in nxt:
                    while j < len(lines) and "*/" not in lines[j]:
                        j += 1
                j += 1
                continue
            break
        if j >= len(lines):
            found.append((i + 1, match.group(1), "nothing follows it"))
            continue

        target = lines[j]
        if ANNOTATION_RE.match(target):
            # Two of the same annotation with a doc comment between them is the stranded case:
            # the first belonged to a declaration that something was inserted in front of.
            if seen_comment and ANNOTATION_RE.match(target).group(1) == match.group(1):
                found.append((i + 1, match.group(1), "duplicated across an inserted declaration"))
            continue
        if not DECLARES_RE.match(target) and match.group(1) not in EXPRESSION_TARGETABLE:
            found.append((i + 1, match.group(1), f"followed by: {target.strip()[:60]}"))
    return found


def main():
    total, scanned = 0, 0
    for root in ROOTS:
        if not os.path.isdir(root):
            continue
        for path in sorted(kotlin_files(root)):
            scanned += 1
            for line, name, why in findings_for(path):
                total += 1
                print(f"{path}:{line}  @{name} is not on a declaration — {why}")
    print(f"\n{total} stranded annotation(s) across {scanned} files")
    return total


if __name__ == "__main__":
    sys.exit(main())
