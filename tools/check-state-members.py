#!/usr/bin/env python3
"""Find `state.foo` reads where `foo` is not a property of that screen's UiState.

The gap this closes
-------------------
Every other tool here works on names and imports. None of them can see *member access*:
`state.loggedEpochDays` is, to a regex, a dotted expression like any other, and
check-missing-imports.py deliberately skips dotted names because they are resolved by a
receiver rather than an import. So a screen can read a field its own state class does not
have, pass all eight checks, and fail in Android Studio.

That is not hypothetical. Phase 6b added a week strip to Home that read
`state.loggedEpochDays`; HomeUiState had no such property, and nothing caught it until the
field was checked by hand.

Why this is checkable when member access in general is not
---------------------------------------------------------
The project has two rigid conventions that together make the receiver knowable without a
type checker. Every screen collects its view model into a local named exactly `state`; and
every view model exposes that state as a single `val uiState: StateFlow<SomethingUiState>`.
A screen almost never names its state type — it writes `viewModel: HomeViewModel` and lets
inference do the rest — so the receiver is resolved in two hops: the one view model the
file mentions, then that view model's declared uiState type. A file that mentions no view
model, or more than one, is skipped rather than guessed at.

Properties are read from both places Kotlin allows them: the constructor parameter list,
and the class body — `val canFinish: Boolean get() = totalSets >= 1` is as real a property
as a constructor `val`, and treating the body as empty would report it as missing.

Usage:  tools/check-state-members.py [main-root]
Exit code is the number of unresolved reads.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kotlin_source import kotlin_files, strip_comments_and_strings  # noqa: E402

MAIN_ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"

STATE_CLASS_RE = re.compile(r"data class (\w*UiState)\s*\(", re.S)
PROP_RE = re.compile(r"\bva[lr]\s+(\w+)\s*(?::|=)")
VM_CLASS_RE = re.compile(r"\bclass\s+(\w+ViewModel)\b")
VM_STATE_RE = re.compile(r"\bval\s+uiState\s*:\s*StateFlow<\s*(\w+)\s*>")
MENTION_VM_RE = re.compile(r"\b(\w+ViewModel)\b")
READ_RE = re.compile(r"\bstate\.(\w+)")

# Members every data class has without declaring them.
DATA_CLASS_MEMBERS = {"copy", "equals", "hashCode", "toString"}


def balanced_end(text, start, opener, closer):
    """Index of the closer matching the opener at `start`, or len(text)."""
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


def state_classes(files):
    """UiState class name -> the set of property names it exposes."""
    index = {}
    for path in files:
        body = strip_comments_and_strings(open(path, encoding="utf-8").read())
        for match in STATE_CLASS_RE.finditer(body):
            params_end = balanced_end(body, match.end() - 1, "(", ")")
            names = set(PROP_RE.findall(body[match.end():params_end]))
            # A class body is optional; when present its properties count too.
            rest = body[params_end + 1:]
            brace = rest.find("{")
            if brace != -1 and rest[:brace].strip() == "":
                names.update(PROP_RE.findall(rest[brace:balanced_end(rest, brace, "{", "}")]))
            index[match.group(1)] = names
    return index


def view_model_states(files):
    """ViewModel class name -> the UiState type its `uiState` property is declared as."""
    index = {}
    for path in files:
        body = strip_comments_and_strings(open(path, encoding="utf-8").read())
        state = VM_STATE_RE.search(body)
        if not state:
            continue
        for name in VM_CLASS_RE.findall(body):
            index[name] = state.group(1)
    return index


def main():
    if not os.path.isdir(MAIN_ROOT):
        print(f"No Kotlin sources under {MAIN_ROOT}", file=sys.stderr)
        return 0
    files = kotlin_files(MAIN_ROOT)
    index = state_classes(files)
    view_models = view_model_states(files)

    findings, skipped, checked = [], 0, 0
    for path in sorted(files):
        body = strip_comments_and_strings(open(path, encoding="utf-8").read())
        reads = set(READ_RE.findall(body))
        if not reads:
            continue
        owners = {view_models[name] for name in MENTION_VM_RE.findall(body) if name in view_models}
        owners &= index.keys()
        if len(owners) != 1:
            # Ambiguous receiver: two view models in one file, or a `state` that belongs to
            # something else. Guessing here would produce noise, not findings.
            skipped += 1
            continue
        owner = owners.pop()
        checked += 1
        for name in sorted(reads - index[owner] - DATA_CLASS_MEMBERS):
            findings.append((path, owner, name))

    for path, owner, name in findings:
        print(f"{path}: 'state.{name}' is not a property of {owner}")

    print(f"\n{len(findings)} unresolved state member(s); "
          f"{checked} file(s) checked against {len(index)} state class(es), "
          f"{skipped} skipped as ambiguous")
    return len(findings)


if __name__ == "__main__":
    sys.exit(main())
