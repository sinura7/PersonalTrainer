#!/usr/bin/env python3
"""Report imports whose name appears nowhere else in the file.

Deliberately conservative — under-reporting is the safe direction for something whose output
you act on by deleting lines. A name that also exists as a member of some unrelated type
(`.map` on a List against an imported `Flow.map`) counts as used and is left alone.

Two things this must get right, both of which it got wrong first:

* Extensions are called as `receiver.name(...)`, so a preceding dot is a *use*. Treating it as
  a disqualifier reported 228 in-use imports as dead.
* String templates hold real code. `"${weight.toWeightLabel(unit)}"` is the only use of that
  import in some files; blanking the whole literal reported it as unused.

Names Kotlin resolves without ever spelling them — operator and delegate conventions — are
never reported, because their imports are invisible in the body by design.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kotlin_source import kotlin_files, strip_comments_and_strings  # noqa: E402

ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"

IMPLICIT = {
    "getValue", "setValue", "provideDelegate",
    "plus", "minus", "times", "div", "rem", "unaryMinus", "unaryPlus",
    "inc", "dec", "compareTo", "contains", "rangeTo", "rangeUntil",
    "invoke", "get", "set", "iterator", "equals", "hashCode",
    "component1", "component2", "component3", "component4", "component5",
}

IMPORT_RE = re.compile(r"\s*import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$")

found = 0
for path in kotlin_files(ROOT):
    src = strip_comments_and_strings(open(path, encoding="utf-8").read())
    lines = src.split("\n")
    body = "\n".join(line for line in lines if not line.lstrip().startswith("import "))
    for number, line in enumerate(lines, 1):
        m = IMPORT_RE.match(line)
        if not m:
            continue
        fq, alias = m.group(1), m.group(2)
        if fq.endswith(".*"):
            continue
        name = alias or fq.rsplit(".", 1)[-1]
        if name in IMPLICIT:
            continue
        if not re.search(rf"(?<!\w){re.escape(name)}\b", body):
            print(f"{path}:{number}  unused: {fq}")
            found += 1
print(f"\n{found} unused import(s)")
