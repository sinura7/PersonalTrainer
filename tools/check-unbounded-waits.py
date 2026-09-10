#!/usr/bin/env python3
"""Find test waits on a ViewModel flow that have no ceiling.

The gap this closes
-------------------
A `first { }` on a ViewModel's StateFlow returns when the predicate holds and never
otherwise. When the value it waits for is written and then overwritten before the
collector runs -- which is what a shared error slot did, twice, on 9 Sep 2026 -- the
test is not a failure but a wedged JVM. Under `runBlocking` there is nothing to time
it out, and on a hosted runner it sits until the job's own timeout kills it, thirty
minutes later, uploading nothing. Three test classes had the same hazard in one day.

The rule
--------
In a `*ViewModelTest.kt`, a `.first { }` / `.first(predicate)` whose receiver is a
ViewModel flow must sit inside a `withTimeout(...)` or `withTimeoutOrNull(...)` call.
Anything else is a skip. A helper's name proves nothing: `awaitState` counts as bounded
only because its body is a `withTimeout`.

What "a ViewModel flow" is
--------------------------
Decided from main sources, not guessed from the receiver's name. Every `val x: StateFlow<>`,
`val x = ....stateIn()` / `.asStateFlow()` declared in a `*ViewModel` class is a state
property; every `Flow<>` / `SharedFlow<>` / `.shareIn()` / `.asSharedFlow()` one is a
plain flow. A bare `first()` on a state property returns the current value and cannot
wait, so only a predicate counts there; on a plain flow a bare `first()` waits too.

The receiver has to be a ViewModel: a name the test file declares with a `*ViewModel`
type, assigns from a `*ViewModel(...)` constructor or `create*ViewModel(...)` factory, an
inline `createViewModel(...).uiState`, or the implicit receiver inside a
`fun FooViewModel.helper()` extension. `deps.workoutRepository.observeSession(id).first { }`
is a repository wait with its own ceiling concerns, not this one, and is not counted.

Blind spots, on purpose: a wait reached through an operator (`vm.uiState.map { }.first { }`)
or through a local alias (`val state = vm.uiState; state.first { }`) is not seen.
Both are rare here and cheap to write in the visible form.

Usage:  tools/check-unbounded-waits.py [test-root] [main-root]
Exit code is 0 unless the count grew past the committed ceiling.
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from checker_baseline import report as report_baseline  # noqa: E402
from kotlin_source import kotlin_files_in, strip_comments_and_strings  # noqa: E402

VM_CLASS_RE = re.compile(r"\bclass\s+\w+ViewModel\b")
STATE_PROP_RE = re.compile(
    r"\bval\s+(\w+)\s*(?::\s*StateFlow<|=[^\n]*\.(?:stateIn|asStateFlow)\()",
)
FLOW_PROP_RE = re.compile(
    r"\bval\s+(\w+)\s*(?::\s*(?:Shared)?Flow<|=[^\n]*\.(?:shareIn|asSharedFlow)\()",
)

# One call's argument list, allowing one level of nested parentheses:
# `createViewModel(SavedStateHandle(mapOf(...)))` is two levels and stays a blind spot.
CALL = r"\((?:[^()]|\([^()]*\))*\)"
# `vm.`, `viewModel!!.`, `createViewModel(...).` -- the chain in front of the property.
WAIT_RE = re.compile(
    r"((?:[\w!?]+(?:" + CALL + r")?[!?]*\.)+)(\w+)\.first\s*([\{(])",
)
# The implicit receiver inside `fun FooViewModel.helper()`: `uiState.first(predicate)`.
BARE_WAIT_RE = re.compile(r"(?<![\w.])(\w+)\.first\s*([\{(])")
BARE_CALL_RE = re.compile(r"^\(\s*\)")
BOUNDED_CALL_RE = re.compile(r"\bwithTimeout(?:OrNull)?\s*\(")
VM_HANDLE_TYPED_RE = re.compile(r"\b(\w+)\s*:\s*[\w.]*ViewModel\??\b")
VM_HANDLE_ASSIGNED_RE = re.compile(
    r"\b(\w+)\s*=\s*(?:lazy\s*\{\s*)?(?:create\w*ViewModel|[\w.]*ViewModel)\s*\(",
)
VM_CALL_RE = re.compile(r"^(?:create\w*ViewModel|[\w.]*ViewModel)" + CALL + r"$")
VM_EXTENSION_RE = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?[\w.]*ViewModel\.\w+\s*\(")
# A line that carries the previous expression on: `.map`, `?.let`, `?: x`, `&& y`, `else`.
CONTINUATION_RE = re.compile(r"[ \t]*(?:\.|\?\.|\?:|&&|\|\||else\b)")


def balanced_end(text: str, start: int, opener: str, closer: str) -> int:
    depth, i = 0, start
    while i < len(text):
        if text[i] == opener:
            depth += 1
        elif text[i] == closer:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(text)


def viewmodel_flow_properties(main_files: list[str]) -> tuple[set[str], set[str]]:
    """(state properties, plain flow properties) declared inside *ViewModel classes."""
    state: set[str] = set()
    plain: set[str] = set()
    for path in main_files:
        body = strip_comments_and_strings(open(path, encoding="utf-8").read())
        for match in VM_CLASS_RE.finditer(body):
            open_at = body.find("{", match.end())
            if open_at < 0:
                continue
            end = balanced_end(body, open_at, "{", "}")
            for prop in STATE_PROP_RE.finditer(body, open_at, end):
                state.add(prop.group(1))
            for prop in FLOW_PROP_RE.finditer(body, open_at, end):
                plain.add(prop.group(1))
    return state, plain


def bounded_regions(body: str) -> list[tuple[int, int]]:
    """Spans of every withTimeout / withTimeoutOrNull call, trailing lambda included."""
    regions: list[tuple[int, int]] = []
    for call in BOUNDED_CALL_RE.finditer(body):
        args_end = balanced_end(body, call.end() - 1, "(", ")")
        cursor = args_end + 1
        while cursor < len(body) and body[cursor] in " \t\r\n":
            cursor += 1
        end = balanced_end(body, cursor, "{", "}") if body.startswith("{", cursor) else args_end
        regions.append((call.start(), end))
    return regions


def expression_end(body: str, start: int) -> int:
    """Where an `= expression` body ends: a depth-0 newline not followed by a continuation."""
    depth, i = 0, start
    while i < len(body):
        char = body[i]
        if char in "({[":
            depth += 1
        elif char in ")}]":
            depth -= 1
            if depth < 0:
                return i
        elif char == "\n" and depth == 0 and not CONTINUATION_RE.match(body, i + 1):
            return i
        i += 1
    return len(body)


def declaration_body(body: str, params_at: int) -> tuple[int, int]:
    """Span of a function's body, block or expression, given the index of its `(`."""
    i = balanced_end(body, params_at, "(", ")") + 1
    depth = 0
    while i < len(body):
        char = body[i]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif depth == 0 and char == "{":
            return i, balanced_end(body, i, "{", "}")
        elif depth == 0 and char == "=":
            return i, expression_end(body, i + 1)
        i += 1
    return len(body), len(body)


def extension_bodies(body: str) -> list[tuple[int, int]]:
    """Spans of `fun FooViewModel.helper(...)` bodies, where the receiver is implicit."""
    return [declaration_body(body, fn.end() - 1) for fn in VM_EXTENSION_RE.finditer(body)]


def viewmodel_handles(body: str) -> set[str]:
    """Names this test file binds to a ViewModel."""
    return set(VM_HANDLE_TYPED_RE.findall(body)) | set(VM_HANDLE_ASSIGNED_RE.findall(body))


def is_wait(prop: str, opener: str, after: str, state: set[str], plain: set[str]) -> bool:
    if prop in plain:
        return True
    if prop not in state:
        return False
    # `first()` on a StateFlow is the current value, not a wait.
    return not (opener == "(" and BARE_CALL_RE.match(after))


def unbounded_waits(
    test_files: list[str],
    state: set[str],
    plain: set[str],
) -> list[tuple[str, int]]:
    found: list[tuple[str, int]] = []
    for path in test_files:
        if not path.endswith("ViewModelTest.kt"):
            continue
        body = strip_comments_and_strings(open(path, encoding="utf-8").read())
        regions = bounded_regions(body)
        handles = viewmodel_handles(body)
        extensions = extension_bodies(body)

        def bounded(at: int) -> bool:
            return any(start <= at <= end for start, end in regions)

        hits: set[int] = set()
        for wait in WAIT_RE.finditer(body):
            chain, prop, opener = wait.group(1), wait.group(2), wait.group(3)
            if not is_wait(prop, opener, body[wait.end() - 1:wait.end() + 8], state, plain):
                continue
            root = chain.split(".")[0].rstrip("!?")
            if not (VM_CALL_RE.match(root) or root in handles):
                continue
            if not bounded(wait.start()):
                hits.add(wait.start())
        for wait in BARE_WAIT_RE.finditer(body):
            prop, opener = wait.group(1), wait.group(2)
            if not any(start <= wait.start() <= end for start, end in extensions):
                continue
            if not is_wait(prop, opener, body[wait.end() - 1:wait.end() + 8], state, plain):
                continue
            if not bounded(wait.start()):
                hits.add(wait.start())
        for at in sorted(hits):
            found.append((path, body.count("\n", 0, at) + 1))
    return found


def main(argv: list[str]) -> int:
    test_roots = [argv[1]] if len(argv) > 1 else ["app/src/test/java"]
    main_roots = [argv[2]] if len(argv) > 2 else ["app/src/main/java"]
    state, plain = viewmodel_flow_properties(kotlin_files_in(main_roots))
    waits = unbounded_waits(kotlin_files_in(test_roots), state, plain)
    for path, line in waits:
        print(f"{path}:{line}: unbounded ViewModel wait")
    error = report_baseline("skips", "unbounded_waits", len(waits))
    if error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
