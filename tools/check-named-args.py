#!/usr/bin/env python3
"""Cross-check named arguments at call sites against declarations, app-wide.

No Android SDK here, so the real compiler never sees this code. This catches the one
class of error that survives a syntax check and breaks the build in Android Studio:
a call passing a parameter name the declaration does not have.
"""
import os, re, sys, collections

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kotlin_source import kotlin_files, strip_comments_and_strings  # noqa: E402

ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"


PARAM_MODS = r"(?:@\w+(?:\([^)]*\))?\s+|vararg\s+|crossinline\s+|noinline\s+|private\s+|internal\s+|public\s+|protected\s+|override\s+|val\s+|var\s+)*"
PARAM_RE = re.compile(rf"^{PARAM_MODS}([A-Za-z_]\w*)\s*:")

def balanced(src, op):
    depth = 0
    for i in range(op, len(src)):
        if src[i] == "(": depth += 1
        elif src[i] == ")":
            depth -= 1
            if depth == 0: return i
    return -1

def top_level_split(text):
    """Split on commas that are not inside brackets.

    `->` was the trap: counting every `>` as a closer made `(() -> Unit)? = null` drive the
    depth negative, so every later parameter was swallowed into one chunk and silently lost.
    `<`/`>` only count as brackets when they actually delimit type arguments.
    """
    depth, cur, out = 0, 0, []
    parts, buf = [], ""
    n = len(text)
    for i, ch in enumerate(text):
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "<" and i > 0 and (text[i-1].isalnum() or text[i-1] in "_?>"):
            cur += 1
        elif ch == ">" and cur > 0 and text[i-1] not in "-=":
            cur -= 1
        if ch == "," and depth == 0 and cur == 0:
            parts.append(buf); buf = ""
        else:
            buf += ch
    if buf.strip(): parts.append(buf)
    return parts

def param_names(text):
    return [m.group(1) for m in (PARAM_RE.match(p.strip()) for p in top_level_split(text)) if m]

files = kotlin_files(ROOT)

clean = {p: strip_comments_and_strings(open(p).read()) for p in files}
decls = collections.defaultdict(list)

for path, src in clean.items():
    for m in re.finditer(r"\bfun\s*(?:<[^>]*>\s*)?(?:[A-Za-z_][\w.]*(?:<[^>]*>)?\??\.)?([A-Za-z_]\w*)\s*\(", src):
        op = src.index("(", m.end() - 1); cl = balanced(src, op)
        if cl > 0: decls[m.group(1)].append(set(param_names(src[op+1:cl])))
    for m in re.finditer(r"\b(?:data\s+|value\s+|enum\s+)?class\s+([A-Za-z_]\w*)\s*(?:<[^>]*>\s*)?(?:@\w+\s*)?\(", src):
        op = src.index("(", m.end() - 1); cl = balanced(src, op)
        if cl > 0: decls[m.group(1)].append(set(param_names(src[op+1:cl])))

problems = []
for path, src in clean.items():
    for m in re.finditer(r"(?<![\w.])([A-Za-z]\w*)\s*\(", src):
        name = m.group(1)
        if name not in decls: continue
        op = src.index("(", m.end() - 1); cl = balanced(src, op)
        if cl < 0: continue
        used = {n for n in (
            PARAM_RE.sub("", "") or None for _ in ()
        )} if False else set()
        for chunk in top_level_split(src[op+1:cl]):
            a = re.match(r"^\s*([a-z]\w*)\s*=(?!=)", chunk)
            if a: used.add(a.group(1))
        if not used: continue
        if any(used <= allowed for allowed in decls[name]): continue
        best = max(decls[name], key=lambda a: len(used & a))
        problems.append((path, src[:m.start()].count("\n") + 1, name, sorted(used - best)))

for path, line, name, bad in sorted(problems):
    print(f"{path}:{line}  {name}(...)  unknown named args: {bad}")
print(f"\n{len(problems)} mismatch(es) across {len(files)} files")
