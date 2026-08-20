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
from collections import defaultdict

ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"


def strip_comments_and_strings(src):
    out = list(src)
    i, n = 0, len(src)

    def blank(a, b):
        for k in range(a, min(b, n)):
            if out[k] != "\n":
                out[k] = " "

    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            blank(i, j); i = j; continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            blank(i, j); i = j; continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            blank(i, j); i = j; continue
        if src[i] == '"':
            j = i + 1
            while j < n and src[j] != '"':
                if src[j] == "\\":
                    j += 1
                j += 1
            blank(i, j + 1); i = j + 1; continue
        i += 1
    return "".join(out)


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


files = []
for dirpath, _, names in os.walk(ROOT):
    for n in names:
        if n.endswith(".kt"):
            files.append(os.path.join(dirpath, n))
clean = {p: strip_comments_and_strings(open(p).read()) for p in files}

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
        for lm in re.finditer(r"(^|\n)([^\n]*?)->", body):
            label = lm.group(2).strip()
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
