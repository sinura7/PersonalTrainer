#!/usr/bin/env python3
"""Find `when` blocks over the project's own enum and sealed types that miss a case.

A non-exhaustive `when` on a sealed or enum subject is a compile *error* in Kotlin, not a
warning — so it is worth catching without a compiler. It is also the error a refactor
leaves behind: add a variant, and every `when` that lacked an `else` breaks at once.

The subject's type is inferred from the branch labels rather than from type analysis, which
needs no symbol table: if a block's labels name members of exactly one known type, that is
the type. Blocks whose type cannot be pinned down are skipped and counted, never guessed at.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from checker_baseline import load as load_baselines, report as report_baseline  # noqa: E402
from kotlin_source import kotlin_files, strip_comments_and_strings  # noqa: E402
from collections import defaultdict

ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"



def block_end(src, open_brace):
    depth = 0
    for i in range(open_brace, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return i
    return -1


def paren_end(src, open_paren):
    depth = 0
    for i in range(open_paren, len(src)):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
            if depth == 0:
                return i
    return -1


files = kotlin_files(ROOT)
clean = {p: strip_comments_and_strings(open(p, encoding="utf-8").read()) for p in files}

# type name -> set of case labels
cases = {}

for src in clean.values():
    for m in re.finditer(r"\benum\s+class\s+([A-Za-z_]\w*)\s*(?:\([^)]*\))?\s*(?::[^{]*)?\{", src):
        end = block_end(src, src.index("{", m.end() - 1))
        body = src[m.end():end]
        # Entries run until the first `;` or, if there is none, the first member declaration.
        head = re.split(r";|\n\s*(?:override|private|internal|public|val|var|fun|companion|init)\b", body)[0]
        entries = set()
        for chunk in head.split(","):
            e = re.match(r"\s*(?:@\w+\s*)*([A-Z][A-Za-z0-9_]*)\s*(?:\(|$|\{)", chunk)
            if e:
                entries.add(e.group(1))
        if entries:
            cases[m.group(1)] = entries

    for m in re.finditer(r"\bsealed\s+(?:class|interface)\s+([A-Za-z_]\w*)\s*(?:\([^)]*\))?\s*(?::[^{]*)?\{", src):
        end = block_end(src, src.index("{", m.end() - 1))
        body = src[m.end():end]
        subs = set(re.findall(r"\b(?:data\s+)?(?:object|class)\s+([A-Z]\w*)", body))
        if subs:
            cases[m.group(1)] = subs

problems, skipped = [], 0

for path, src in clean.items():
    for m in re.finditer(r"\bwhen\s*\(", src):
        pe = paren_end(src, src.index("(", m.end() - 1))
        if pe < 0:
            continue
        brace = src.find("{", pe)
        if brace < 0 or src[pe + 1:brace].strip():
            continue
        end = block_end(src, brace)
        if end < 0:
            continue
        body = src[brace + 1:end]
        # Branch labels: everything left of a top-level `->`.
        labels = set()
        has_else = False
        depth = 0
        line_start = 0
        for i, ch in enumerate(body):
            if ch in "({[":
                depth += 1
            elif ch in ")}]":
                depth -= 1
            elif ch == "\n" and depth == 0:
                line_start = i + 1
        lines = body.split("\n")
        # A branch may list its labels over several lines with the arrow on its own:
        #
        #     Action.A,
        #     Action.B,
        #     -> true
        #
        # Reading only the arrow's line finds no labels there at all, and the block then looks
        # as though it never handled A or B — a FAIL naming cases the code plainly covers.
        # Continuation lines are taken only when they are a bare (optionally qualified) name
        # followed by a comma, which a branch *body* essentially never is.
        continuation = re.compile(r"^(?:is\s+)?(?:[A-Za-z_]\w*\.)*[A-Za-z_]\w*\s*,$")
        for index, line in enumerate(lines):
            arrow = line.find("->")
            if arrow < 0:
                continue
            label = line[:arrow].strip()
            back = index - 1
            while back >= 0 and continuation.match(lines[back].strip()):
                label = lines[back].strip() + " " + label
                back -= 1
            if label.startswith("else"):
                has_else = True
                continue
            for token in re.findall(r"(?:is\s+)?(?:[A-Za-z_]\w*\.)*([A-Z][A-Za-z0-9_]*)", label):
                labels.add(token)
        if not labels:
            continue

        candidates = [(t, c) for t, c in cases.items() if labels & c]
        if not candidates:
            continue
        best = max(candidates, key=lambda tc: len(labels & tc[1]))
        tied = [t for t, c in candidates if len(labels & c) == len(labels & best[1])]
        if len(tied) > 1:
            skipped += 1
            continue
        type_name, members = best
        # Only judge blocks whose labels are entirely that type's cases.
        if not labels <= members:
            skipped += 1
            continue
        missing = members - labels
        if missing and not has_else:
            line = src[:m.start()].count("\n") + 1
            problems.append((path, line, type_name, sorted(missing)))

for path, line, type_name, missing in sorted(problems):
    print(f"{path}:{line}  when over {type_name} is missing: {missing}")
print(f"\n{len(problems)} non-exhaustive when block(s); "
      f"{len(cases)} types indexed, {skipped} block(s) skipped as ambiguous")
_growth = report_baseline("skips", "when_exhaustive", skipped, load_baselines())
if _growth:
    print(_growth)
    sys.exit(1)
