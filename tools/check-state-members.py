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

Members are read from every place Kotlin allows them: the constructor parameter list, and the
class body — `val canFinish: Boolean get() = totalSets >= 1` is as real a property as a
constructor `val`, and a body `fun swapCandidates(id)` is as real a member as either. Treating
the body as empty reports all three as missing.

Usage:  tools/check-state-members.py [main-root]
Exit code is the number of unresolved reads.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from checker_baseline import load as load_baselines, report as report_baseline  # noqa: E402
from kotlin_source import kotlin_files_in, strip_comments_and_strings  # noqa: E402

ROOTS = sys.argv[1:] or ["app/src/main/java", "app/src/debug/java"]

# Owner is the StateFlow type name, not the *UiState suffix. RestTimerScreenState
# is a screen state that the old suffix-only regex skipped.
STATE_CLASS_RE = re.compile(r"data class (\w+)\s*\(", re.S)
PROP_RE = re.compile(r"\bva[lr]\s+(\w+)\s*(?::|=)")
# Members reached with `state.foo(...)` are just as real as `state.foo`, and a state class
# earns a body function whenever a derivation depends on more than one of its own fields.
METHOD_RE = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(\w+)\s*\(")
VM_CLASS_RE = re.compile(r"\bclass\s+(\w+ViewModel)\b")
VM_STATE_RE = re.compile(r"\bval\s+uiState\s*:\s*StateFlow<\s*(\w+)\s*\??\s*>")
MENTION_VM_RE = re.compile(r"\b(\w+ViewModel)\b")
READ_RE = re.compile(r"\bstate\.(\w+)")
STATE_PARAM_RE = re.compile(r"\bstate\s*:\s*(\w+)")

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
            # A fresh name, NOT a reassignment of `body`: the finditer above walks
            # the original string, and truncating it here made every LATER state
            # class in the same file index against garbage offsets — the second
            # class silently indexed zero members.
            rest = body[params_end + 1:]
            brace = rest.find("{")
            if brace != -1 and rest[:brace].strip() == "":
                class_body = rest[brace:balanced_end(rest, brace, "{", "}")]
                names.update(PROP_RE.findall(class_body))
                names.update(METHOD_RE.findall(class_body))
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


def owner_for(path, body, view_models, index):
    mentioned = [name for name in MENTION_VM_RE.findall(body) if name in view_models]
    owners = {view_models[name] for name in mentioned} & index.keys()
    if len(owners) == 1:
        return owners.pop(), None
    param_types = {name for name in STATE_PARAM_RE.findall(body) if name in index}
    if len(param_types) == 1:
        return param_types.pop(), None
    if not mentioned and not param_types:
        return None, f"{path}: skipped — no typed uiState ViewModel or state: parameter"
    return None, (
        f"{path}: skipped — typed state receiver is not unique "
        f"(view models {sorted(set(mentioned))}, "
        f"state: types {sorted(param_types)})"
    )


def main():
    files = kotlin_files_in(ROOTS)
    if not files:
        print(f"No Kotlin sources under {', '.join(ROOTS)}", file=sys.stderr)
        return 0
    index = state_classes(files)
    view_models = view_model_states(files)

    findings, skipped_lines, checked = [], [], 0
    for path in sorted(files):
        body = strip_comments_and_strings(open(path, encoding="utf-8").read())
        reads = set(READ_RE.findall(body))
        if not reads:
            continue
        owner, reason = owner_for(path, body, view_models, index)
        if reason is not None:
            skipped_lines.append(reason)
            continue
        checked += 1
        for name in sorted(reads - index[owner] - DATA_CLASS_MEMBERS):
            findings.append((path, owner, name))

    for path, owner, name in findings:
        print(f"{path}: 'state.{name}' is not a property of {owner}")
    for line in skipped_lines:
        print(line)

    print(f"\n{len(findings)} unresolved state member(s); "
          f"{checked} file(s) checked against {len(index)} state class(es), "
          f"{len(skipped_lines)} skipped")
    baselines = load_baselines()
    growth = report_baseline("skips", "state_members", len(skipped_lines), baselines)
    # 1, not len(findings): POSIX truncates exit status to 8 bits, so exactly
    # 256 findings would exit 0 and pass preflight.
    if findings or growth:
        if growth:
            print(growth)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
