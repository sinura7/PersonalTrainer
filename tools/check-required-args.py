#!/usr/bin/env python3
"""Find call sites that omit a required parameter.

The gap this closes
-------------------
`check-named-args.py` asks "does every name you passed exist on the declaration?" That is
half the contract. The other half — "did you pass everything the declaration requires?" — was
checked by nothing, and it is the half that broke.

Phase 6b removed a composable's whole-card tap by deleting the ARGUMENT and leaving the
PARAMETER. `ThisWeekCard` went on declaring six parameters with no defaults while its only
call site passed five. Every one of the ten static checks reported clean, because every name
that WAS passed existed. The branch did not compile for three phases.

Why it is conservative
----------------------
Positional arguments make the general case hard: matching them to parameters means resolving
overloads and trailing-lambda syntax correctly, and being wrong produces noise on working
code. So this reports only the case it can be certain about — a call where **every argument
is named** — plus a trailing lambda, which by Kotlin's rule supplies the last parameter.

That restriction costs almost nothing here. This codebase's Compose call sites are named-
argument style throughout, which is exactly where a forgotten parameter hides: with five named
arguments spread over eight lines, the missing sixth is invisible to the reader too.

Overloads are satisfied if ANY declaration of that name accepts the call, matching how
check-named-args resolves them.

Usage:  tools/check-required-args.py [root ...]
Exit code is the number of findings.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kotlin_source import kotlin_files, strip_comments_and_strings  # noqa: E402

ROOTS = sys.argv[1:] or ["app/src/main/java", "app/src/test/java"]

FUN_RE = re.compile(r"\bfun\s*(?:<[^>]*>\s*)?(?:[A-Za-z_][\w.]*(?:<[^>]*>)?\??\.)?([A-Za-z_]\w*)\s*\(")
CLASS_RE = re.compile(
    r"\b(?:data\s+|value\s+|enum\s+)?class\s+([A-Za-z_]\w*)\s*(?:<[^>]*>\s*)?(?:@\w+\s*)?\("
)
CALL_RE = re.compile(r"(?<![\w.])([A-Z]\w*)\s*\(")
NAMED_ARG_RE = re.compile(r"^\s*([a-z]\w*)\s*=(?!=)")
# A parameter has a default when a top-level `=` follows its type.
PARAM_MODS = (
    r"(?:@\w+(?:\([^)]*\))?\s+|vararg\s+|crossinline\s+|noinline\s+|private\s+|internal\s+"
    r"|public\s+|protected\s+|override\s+|val\s+|var\s+)*"
)
PARAM_RE = re.compile(rf"^{PARAM_MODS}([A-Za-z_]\w*)\s*:(.*)$", re.S)


def balanced(text, open_index, opener="(", closer=")"):
    depth, i = 0, open_index
    while i < len(text):
        c = text[i]
        if c == opener:
            depth += 1
        elif c == closer:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def _spans(text, track_angles):
    """Top-level comma-separated spans as (start, end) index pairs into `text`.

    Two callers with genuinely different lexical rules:

    - A PARAMETER list nests generics, so `<` and `>` must be tracked or `Map<String, Int>`
      splits in half.
    - An ARGUMENT list does not. Inside a call, `>` is overwhelmingly a comparison —
      `if (item.targetSets > 0)` — and treating it as a closing bracket drives the depth
      negative, after which nothing splits correctly. Generic type arguments in a call sit
      before the parenthesis, never inside it.

    `->` is skipped in both: a lambda type's arrow is not a bracket, and reading it as one is
    what made the first draft of this tool report a hundred false positives.
    """
    opens = "([{<" if track_angles else "([{"
    closes = ")]}>" if track_angles else ")]}"
    spans, depth, start, i = [], 0, 0, 0
    while i < len(text):
        c = text[i]
        if c == "-" and i + 1 < len(text) and text[i + 1] == ">":
            i += 2
            continue
        if c in opens:
            depth += 1
        elif c in closes:
            depth -= 1
        elif c == "," and depth == 0:
            spans.append((start, i))
            start = i + 1
        i += 1
    spans.append((start, len(text)))
    return spans


def split_params(text):
    return [text[a:b] for a, b in _spans(text, track_angles=True)]


def parameters(param_text):
    """[(name, has_default)] in declaration order, or None if anything is unparseable."""
    result = []
    for chunk in split_params(param_text):
        if not chunk.strip():
            continue
        match = PARAM_RE.match(chunk.strip())
        if not match:
            return None
        name, rest = match.group(1), match.group(2)
        # A default is a top-level `=` after the type. `->` is skipped for the same reason as
        # in split_top_level — `(() -> Unit)? = null` is a defaulted parameter, and reading the
        # arrow's `>` as a closing bracket hides that.
        depth, has_default = 0, False
        i = 0
        while i < len(rest):
            c = rest[i]
            if c == "-" and i + 1 < len(rest) and rest[i + 1] == ">":
                i += 2
                continue
            if c in "([{<":
                depth += 1
            elif c in ")]}>":
                depth -= 1
            elif c == "=" and depth == 0:
                before = rest[i - 1] if i else ""
                after = rest[i + 1] if i + 1 < len(rest) else ""
                if before not in "!<>=" and after != "=":
                    has_default = True
                    break
            i += 1
        result.append((name, has_default))
    return result


def declarations(files):
    """name -> list of parameter lists, for functions and constructors declared in-project."""
    index = {}
    for path in files:
        body = strip_comments_and_strings(open(path, encoding="utf-8").read())
        for regex in (FUN_RE, CLASS_RE):
            for match in regex.finditer(body):
                open_paren = body.index("(", match.end() - 1)
                close = balanced(body, open_paren)
                if close < 0:
                    continue
                params = parameters(body[open_paren + 1:close])
                if params is None:
                    continue
                index.setdefault(match.group(1), []).append(params)
    return index


def trailing_lambda(body, close_index):
    """True when a `{` follows the call's `)` with only whitespace between."""
    i = close_index + 1
    while i < len(body) and body[i] in " \t\n\r":
        i += 1
    return i < len(body) and body[i] == "{"


def scan(files, index):
    findings = []
    for path in sorted(files):
        raw = open(path, encoding="utf-8").read()
        body = strip_comments_and_strings(raw)
        for match in CALL_RE.finditer(body):
            name = match.group(1)
            overloads = index.get(name)
            if not overloads:
                continue
            open_paren = body.index("(", match.end() - 1)
            close = balanced(body, open_paren)
            if close < 0:
                continue
            inner = body[open_paren + 1:close]
            raw_inner = raw[open_paren + 1:close]
            supplied = set()
            all_named = True
            # Emptiness is judged on the RAW text. The source stripper blanks a string
            # literal's quotes as well as its content, so `Kicker("Rest")` arrives here as
            # `Kicker(      )` — a positional argument that looks like an empty argument list.
            # Offsets are preserved, so the raw slice answers the question the stripped one
            # cannot, while the stripped one still does the splitting (commas inside a string
            # must not split).
            if raw_inner.strip():
                spans = _spans(inner, track_angles=False)
                # Kotlin allows a trailing comma and this codebase uses one on every
                # multi-line call, which leaves a final all-whitespace span. Read as an
                # argument it is positional, which disqualified every such call — i.e. all of
                # them — and made this tool silently check nothing.
                if len(spans) > 1 and not raw_inner[spans[-1][0]:spans[-1][1]].strip():
                    spans = spans[:-1]
                for a, b in spans:
                    named = NAMED_ARG_RE.match(inner[a:b])
                    if named and raw_inner[a:b].strip():
                        supplied.add(named.group(1))
                    else:
                        all_named = False
                        break
            # Only the all-named case is decidable without resolving positional matching.
            if not all_named:
                continue

            has_lambda = trailing_lambda(body, close)
            missing_per_overload = []
            for params in overloads:
                required = [n for n, has_default in params if not has_default]
                if has_lambda and params:
                    # Kotlin's trailing-lambda rule: the block supplies the LAST parameter.
                    supplied_here = supplied | {params[-1][0]}
                else:
                    supplied_here = supplied
                missing = [n for n in required if n not in supplied_here]
                if not missing:
                    missing_per_overload = []
                    break
                missing_per_overload.append(missing)
            if missing_per_overload:
                # Report against the overload that comes closest to being satisfied.
                best = min(missing_per_overload, key=len)
                line = body[:match.start()].count("\n") + 1
                findings.append((path, line, name, best))
    return findings


def main():
    files = []
    for root in ROOTS:
        if os.path.isdir(root):
            files.extend(kotlin_files(root))
    if not files:
        print(f"No Kotlin sources under {', '.join(ROOTS)}", file=sys.stderr)
        return 0

    index = declarations(files)
    findings = scan(files, index)
    for path, line, name, missing in findings:
        plural = "s" if len(missing) > 1 else ""
        print(f"{path}:{line}  {name}(...) is missing required argument{plural}: {missing}")
    print(f"\n{len(findings)} missing required argument(s) across {len(files)} files")
    return len(findings)


if __name__ == "__main__":
    sys.exit(main())
