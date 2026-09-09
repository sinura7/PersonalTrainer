#!/usr/bin/env python3
"""Find a `catch (Exception)` that can swallow a coroutine's cancellation.

The gap this closes
-------------------
`CancellationException` extends `Exception`. A `catch (thrown: Exception)` around a
suspending call therefore catches the cancellation too: the coroutine that was told to
stop logs "failed", falls back, and carries on -- `mapLatest` keeps computing the value
nobody wants, a ViewModel writes a refusal for an action the user navigated away from.
`util/CoroutineErrors.kt` has the fix (`runCatchingCancellable`, `recoverWith`); this
finds the sites that still need it.

The rule
--------
In main sources, a `catch (x: Exception)` or `catch (x: Throwable)` whose `try` can see
a suspension must be guarded. "Can see a suspension" means the try sits inside a
`suspend fun`, or inside a lambda handed to a coroutine builder (`launch`, `async`,
`withContext`, `flow`, `coroutineScope`, ...) or to a project function declared with a
`suspend` lambda parameter (`launchWrite`, `write`, `runBackupAction`, `serialized`, ...),
read from the same sources. "Guarded" means an earlier `catch (_: CancellationException)`
clause on the same `try`, or a rethrow (`is CancellationException`) in the catch body.

A bare catch in a plain function is not counted: nothing in it can suspend, so nothing
in it can be cancelled that way. The 76 JSON parsers and Android system calls are fine.

Usage:  tools/check-cancellation.py [main-root]
Exit code is 0 unless the count grew past the committed ceiling.
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from checker_baseline import report as report_baseline  # noqa: E402
from kotlin_source import kotlin_files_in, strip_comments_and_strings  # noqa: E402

BARE_CATCH_RE = re.compile(r"\bcatch\s*\(\s*\w+\s*:\s*(?:Exception|Throwable)\s*\)")
# Written both bare and as `kotlinx.coroutines.CancellationException` in this codebase.
GUARD_CLAUSE_RE = re.compile(r"\bcatch\s*\(\s*\w+\s*:\s*(?:[\w.]+\.)?CancellationException\s*\)\s*$")
RETHROW_RE = re.compile(r"\bis\s+(?:[\w.]+\.)?CancellationException\b")
CATCH_CLAUSE_RE = re.compile(r"\bcatch\s*\([^)]*\)\s*$")
TRY_RE = re.compile(r"\btry\s*$")
FUN_HEADER_RE = re.compile(r"\bfun\b")
SUSPEND_FUN_RE = re.compile(r"\bsuspend\s+fun\b")
# A function whose parameter list takes a suspend lambda: its trailing lambda suspends.
SUSPEND_PARAM_FUN_RE = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\([^)]*:\s*suspend\b")
IDENT_BEFORE_RE = re.compile(r"(\w+)\s*$")

BUILDERS = {
    "launch", "async", "withContext", "flow", "coroutineScope", "supervisorScope",
    "withTimeout", "withTimeoutOrNull", "runBlocking", "channelFlow", "callbackFlow",
    "collect", "collectLatest", "transform", "mapLatest", "flatMapLatest", "onEach",
    "produce", "actor", "select", "withLock", "withPermit", "repeatOnLifecycle",
}


def balanced_open(text: str, close_at: int) -> int:
    """Index of the `{` matching the `}` at close_at."""
    depth, i = 0, close_at
    while i >= 0:
        if text[i] == "}":
            depth += 1
        elif text[i] == "{":
            depth -= 1
            if depth == 0:
                return i
        i -= 1
    return 0


def paren_open(text: str, close_at: int) -> int:
    depth, i = 0, close_at
    while i >= 0:
        if text[i] == ")":
            depth += 1
        elif text[i] == "(":
            depth -= 1
            if depth == 0:
                return i
        i -= 1
    return 0


def opener_name(body: str, brace_at: int) -> str | None:
    """The call a trailing lambda belongs to: `launch {`, `launch(...) {`, `launchWrite {`."""
    i = brace_at - 1
    while i >= 0 and body[i] in " \t\r\n":
        i -= 1
    if i < 0:
        return None
    if body[i] == ")":
        i = paren_open(body, i) - 1
        while i >= 0 and body[i] in " \t\r\n":
            i -= 1
    match = IDENT_BEFORE_RE.search(body[max(0, i - 80):i + 1])
    return match.group(1) if match else None


def header_line(body: str, brace_at: int) -> str:
    start = body.rfind("\n", 0, brace_at) + 1
    return body[start:brace_at]


def suspend_wrappers(main_files: list[str]) -> set[str]:
    names: set[str] = set()
    for path in main_files:
        body = strip_comments_and_strings(open(path, encoding="utf-8").read())
        names.update(SUSPEND_PARAM_FUN_RE.findall(body))
    return names


def suspending_context(body: str, at: int, wrappers: set[str]) -> str | None:
    """Why this position can see a suspension, or None when nothing around it can."""
    depth, i = 0, at
    while i > 0:
        i -= 1
        char = body[i]
        if char == "}":
            depth += 1
        elif char == "{":
            if depth > 0:
                depth -= 1
                continue
            name = opener_name(body, i)
            if name in BUILDERS or name in wrappers:
                return f"inside `{name} {{ }}`"
            header = header_line(body, i)
            if FUN_HEADER_RE.search(header):
                return "inside a suspend fun" if SUSPEND_FUN_RE.search(header) else None
    return None


def guarded(body: str, catch_at: int) -> bool:
    """An earlier CancellationException clause on this try, or a rethrow in the body."""
    open_at = body.find("{", catch_at)
    if open_at >= 0:
        close_at = balanced_close(body, open_at)
        if RETHROW_RE.search(body[open_at:close_at]):
            return True
    i = catch_at - 1
    while i > 0:
        while i > 0 and body[i] in " \t\r\n":
            i -= 1
        if body[i] != "}":
            return False
        block_open = balanced_open(body, i)
        head = body[max(0, block_open - 200):block_open].rstrip()
        if GUARD_CLAUSE_RE.search(head):
            return True
        if TRY_RE.search(head):
            return False
        if CATCH_CLAUSE_RE.search(head) is None:
            return False
        # An earlier catch clause on the same try: keep walking back through it.
        i = body.rfind("catch", 0, block_open) - 1
    return False


def balanced_close(text: str, open_at: int) -> int:
    depth, i = 0, open_at
    while i < len(text):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(text)


def swallowing_catches(main_files: list[str], wrappers: set[str]) -> list[tuple[str, int, str]]:
    found: list[tuple[str, int, str]] = []
    for path in main_files:
        body = strip_comments_and_strings(open(path, encoding="utf-8").read())
        for match in BARE_CATCH_RE.finditer(body):
            why = suspending_context(body, match.start(), wrappers)
            if why is None or guarded(body, match.start()):
                continue
            found.append((path, body.count("\n", 0, match.start()) + 1, why))
    return found


def main(argv: list[str]) -> int:
    roots = [argv[1]] if len(argv) > 1 else ["app/src/main/java"]
    files = kotlin_files_in(roots)
    found = swallowing_catches(files, suspend_wrappers(files))
    for path, line, why in found:
        print(f"{path}:{line}: bare catch can swallow cancellation ({why})")
    error = report_baseline("skips", "cancellation_swallow", len(found))
    if error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
