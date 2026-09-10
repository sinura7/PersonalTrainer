#!/usr/bin/env python3
"""Cross-check lambda arity at named call sites against the declared function type.

WHY THIS EXISTS
---------------
On 7 September 2026 a branch was pushed that did not compile. `SessionLiftStrip`'s
`onStageTargets` grew from five parameters to six; one of its three call sites was
updated and two were not. Twenty static checkers, a 1202-test JVM lane and three
independent reviewers all passed it, because none of them types a lambda and the two
stale callers live in files the diff never touched.

`tools/compile-check.sh` catches this properly — it runs the real compiler — but it
downloads about 186 MB on first use and is deliberately outside `preflight.sh`. This
checker is the offline half: it is regex over source, it knows nothing about types, and
it answers exactly one question. Counting parameters needs no type system.

SCOPE, AND WHY IT IS THIS NARROW
--------------------------------
Only NAMED arguments (`onFoo = { a, b -> }`) are judged. A trailing lambda would need
overload resolution to know which parameter it fills; a named argument names it.

A site is judged only when BOTH sides are unambiguous:
  * every visible declaration of that call name declares that parameter as a plain
    function type `(A, B) -> R` (optionally `suspend`, nullable, or parenthesised), and
  * the argument is a literal lambda whose header parses as a parameter list.
Anything else — a receiver type `T.(A) -> R`, a function reference, a variable, a
lambda whose header is not obviously a parameter list — is skipped, not guessed at.
A false RED here would be worse than the gap it closes.

A lambda with no `->` is accepted against arity 0 or 1: Kotlin gives it the implicit
`it`. Against arity 2+ it is a compile error, and this reports it.
"""
import os, re, sys, collections

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kotlin_source import kotlin_files, kotlin_files_in, strip_comments_and_strings  # noqa: E402
from checker_baseline import load as load_baselines, report as report_baseline  # noqa: E402

# Every root given is both indexed and judged, and one invocation must cover all of them:
# a test source set alone cannot see the declaration it calls, so run it as
#   check-lambda-arity.py app/src/main/java app/src/test/java app/src/androidTest/java ...
# The second stale caller of the defect that prompted this checker was in androidTest.
ROOTS = sys.argv[1:] or ["app/src/main/java"]

PARAM_MODS = (
    r"(?:@\w+(?:\([^)]*\))?\s+|vararg\s+|crossinline\s+|noinline\s+|private\s+"
    r"|internal\s+|public\s+|protected\s+|override\s+|val\s+|var\s+)*"
)
PARAM_RE = re.compile(rf"^{PARAM_MODS}([A-Za-z_]\w*)\s*:\s*(.+)$", re.S)
PRIVATE_RE = re.compile(r"(?:^|[\s;{}])private\s+(?:[a-z]+\s+)*$")

# A lambda header parameter: `a`, `_`, `a: Foo`, or a destructured `(a, b)`.
LAMBDA_PARAM_RE = re.compile(r"^\s*(?:\([^()]*\)|_|[A-Za-z_]\w*)\s*(?::\s*\S.*)?$", re.S)


def balanced(src, op, opener="(", closer=")"):
    depth = 0
    for i in range(op, len(src)):
        if src[i] == opener:
            depth += 1
        elif src[i] == closer:
            depth -= 1
            if depth == 0:
                return i
    return -1


def top_level_split(text):
    """Split on commas outside every bracket, counting `<`/`>` only as type arguments.

    Same rule as check-named-args.py: treating every `>` as a closer drives the depth
    negative on `(() -> Unit)? = null` and swallows every later parameter.
    """
    depth = cur = 0
    parts, buf = [], ""
    for i, ch in enumerate(text):
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "<" and i > 0 and (text[i - 1].isalnum() or text[i - 1] in "_?>"):
            cur += 1
        elif ch == ">" and cur > 0 and text[i - 1] not in "-=":
            cur -= 1
        if ch == "," and depth == 0 and cur == 0:
            parts.append(buf)
            buf = ""
        else:
            buf += ch
    if buf.strip():
        parts.append(buf)
    return parts


def function_type_arity(type_text):
    """Parameter count of a plain function type, or None when this is not one.

    Accepts `(A, B) -> R`, `suspend (A) -> R`, `((A) -> R)?` and any nesting of those.
    Refuses a receiver type (`T.(A) -> R`): its lambda takes one fewer parameter than
    the parentheses show, and the receiver is written `this`, so counting would lie.
    """
    t = type_text.strip()
    # Strip a default value: `(A) -> R = { }`. The `=` must be outside every bracket.
    depth = cur = 0
    for i, ch in enumerate(t):
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "<" and i > 0 and (t[i - 1].isalnum() or t[i - 1] in "_?>"):
            cur += 1
        elif ch == ">" and cur > 0 and t[i - 1] not in "-=":
            cur -= 1
        elif ch == "=" and depth == 0 and cur == 0 and t[i:i + 2] != "==" and (i == 0 or t[i - 1] not in "=!<>"):
            t = t[:i]
            break
    t = t.strip()

    for _ in range(6):  # unwrap `( ... )?` and `( ... )` a bounded number of times
        t = t.strip()
        if t.startswith("suspend "):
            t = t[len("suspend "):].strip()
            continue
        if t.endswith("?"):
            t = t[:-1].strip()
            continue
        if t.startswith("(") and balanced(t, 0) == len(t) - 1:
            inner = t[1:-1].strip()
            # `(A, B) -> R` unwrapped to `A, B` is NOT a nested function type; only
            # unwrap when what is inside still has a top-level arrow of its own.
            if "->" in inner and _top_level_arrow(inner) is not None:
                t = inner
                continue
        break

    arrow = _top_level_arrow(t)
    if arrow is None or not t.startswith("("):
        return None
    close = balanced(t, 0)
    if close < 0 or t[close + 1:arrow].strip() != "":
        return None  # something between the parens and the arrow: not a plain function type
    params = t[1:close].strip()
    return 0 if not params else len(top_level_split(params))


def _top_level_arrow(text):
    """Index of the first `->` outside every bracket, or None."""
    depth = cur = 0
    for i, ch in enumerate(text):
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "<" and i > 0 and (text[i - 1].isalnum() or text[i - 1] in "_?>"):
            cur += 1
        elif ch == ">" and cur > 0 and text[i - 1] not in "-=":
            cur -= 1
        elif ch == "-" and depth == 0 and cur == 0 and text[i:i + 2] == "->":
            return i
    return None


def lambda_arity(body):
    """Parameters a literal lambda declares, or None when its header is not readable.

    `None` means "do not judge this site". A header is only trusted when every chunk
    before the arrow parses as a lambda parameter; `{ save(a, b) }` has no top-level
    arrow and reads as the implicit-`it` case, while `{ x + (y) -> }` does not parse
    and is skipped rather than counted wrong.
    """
    arrow = _top_level_arrow(body)
    if arrow is None:
        return 1  # implicit `it`; also satisfies arity 0, handled by the caller
    head = body[:arrow].strip()
    if not head:
        return None
    chunks = top_level_split(head)
    if not chunks or any(not LAMBDA_PARAM_RE.match(c) for c in chunks):
        return None
    return len(chunks)


def visibility_scope(src, start, path):
    return path if PRIVATE_RE.search(src[max(0, start - 80):start]) else None


files = kotlin_files_in(ROOTS)
clean = {p: strip_comments_and_strings(open(p, encoding="utf-8").read()) for p in files}

# name -> list of (scope, {param name: arity or None})
decls = collections.defaultdict(list)


def record(name, scope, param_text):
    table = {}
    for chunk in top_level_split(param_text):
        m = PARAM_RE.match(chunk.strip())
        if m:
            table[m.group(1)] = function_type_arity(m.group(2))
    if table:
        decls[name].append((scope, table))


for path, src in clean.items():
    for m in re.finditer(r"\bfun\s*(?:<[^>]*>\s*)?(?:[A-Za-z_][\w.]*(?:<[^>]*>)?\??\.)?([A-Za-z_]\w*)\s*\(", src):
        op = src.index("(", m.end() - 1)
        cl = balanced(src, op)
        if cl > 0:
            record(m.group(1), visibility_scope(src, m.start(), path), src[op + 1:cl])
    for m in re.finditer(r"\b(?:data\s+|value\s+|enum\s+)?class\s+([A-Za-z_]\w*)\s*(?:<[^>]*>\s*)?(?:@\w+\s*)?\(", src):
        op = src.index("(", m.end() - 1)
        cl = balanced(src, op)
        if cl > 0:
            record(m.group(1), visibility_scope(src, m.start(), path), src[op + 1:cl])

problems = []
# Every site this checker declines to judge, so the gap is measured rather than assumed
# clean. The repo's convention: a checker that skips reports how much, and the count is
# ratcheted so the skipped set cannot quietly grow.
skipped_unreadable = 0   # the lambda header does not parse as a parameter list
skipped_untyped = 0      # some visible declaration is not a plain function type
skipped_qualified = 0    # a fully-qualified call, e.g. `a.b.C.f(onX = { ... })`

# A fully-qualified call is invisible to the call-site regex below, whose negative
# lookbehind excludes a leading dot so that `vm.method(...)` is not judged against a
# same-named top-level declaration. Count the ones that carry a named lambda argument, so
# the blind spot has a number instead of a silence.
for path, src in clean.items():
    for m in re.finditer(r"[\w.]+\.([A-Za-z]\w*)\s*\(", src):
        name = m.group(1)
        if name not in decls:
            continue
        op = src.index("(", m.end() - 1)
        cl = balanced(src, op)
        if cl < 0:
            continue
        if any(re.match(r"^\s*([a-z]\w*)\s*=(?!=)\s*\{", chunk)
               for chunk in top_level_split(src[op + 1:cl])):
            skipped_qualified += 1

for path, src in clean.items():
    for m in re.finditer(r"(?<![\w.])([A-Za-z]\w*)\s*\(", src):
        name = m.group(1)
        if name not in decls:
            continue
        visible = [t for scope, t in decls[name] if scope is None or scope == path]
        if not visible:
            continue
        op = src.index("(", m.end() - 1)
        cl = balanced(src, op)
        if cl < 0:
            continue
        consumed = 0
        for chunk in top_level_split(src[op + 1:cl]):
            here = consumed
            consumed += len(chunk) + 1  # the comma top_level_split dropped
            a = re.match(r"^\s*([a-z]\w*)\s*=(?!=)\s*\{", chunk)
            if not a:
                continue
            arg = a.group(1)
            lb = chunk.index("{", a.end() - 1)
            rb = balanced(chunk, lb, "{", "}")
            if rb < 0:
                continue
            got = lambda_arity(chunk[lb + 1:rb])
            if got is None:
                skipped_unreadable += 1
                continue
            # Every overload that declares this parameter must disagree before it is a
            # finding: one matching declaration is a legal call.
            wanted = [t[arg] for t in visible if arg in t and t[arg] is not None]
            if not wanted or len(wanted) != sum(1 for t in visible if arg in t):
                skipped_untyped += 1
                continue  # some visible declaration is not a plain function type
            if any(w == got or (got == 1 and w == 0 and _top_level_arrow(chunk[lb + 1:rb]) is None) for w in wanted):
                continue
            args = src[op + 1:cl]
            line = src[:op].count("\n") + args[:here + a.start(1)].count("\n") + 1
            problems.append((path, line, name, arg, got, sorted(set(wanted))))

for path, line, name, arg, got, wanted in sorted(problems):
    want = wanted[0] if len(wanted) == 1 else wanted
    print(f"{path}:{line}  {name}(..., {arg} = {{ … }})  lambda takes {got}, declaration takes {want}")
print(f"\n{len(problems)} arity mismatch(es) across {len(files)} files")
skipped = skipped_unreadable + skipped_untyped + skipped_qualified
print(
    f"{skipped} call site(s) declined: {skipped_unreadable} unreadable lambda header, "
    f"{skipped_untyped} non-plain function type, {skipped_qualified} fully qualified"
)
baselines = load_baselines()
complaint = report_baseline("skips", "lambda_arity_declined", skipped, baselines)
if complaint:
    print(complaint)
sys.exit(1 if problems or complaint else 0)
