"""The release lane's static rules, shared by check-release-lane.py and its fixture proof.

The gym-floor release is the one build nobody runs day to day, so it rotted twice
without anyone seeing (audit X6, BR-1 and BR-2): the shrinker stopped on a library
added five days earlier, and release.yml still asked the SDK action for the retired
`tools` package that had already stopped a debug drop. Neither break could show until
a `v*` tag was pushed. These rules hold the lessons the other two workflows already
learned, so the third cannot fall behind again:

- every `android-actions/setup-android` step passes `packages: ''`;
- a publish command (`gh release upload`, `gh release create`) never has its failure
  swallowed: not by `||` (the shape debug-live.yml removed after the 10 Sep drop that
  published nothing and went green), unless what follows exits nonzero; and not by `&&`
  or a pipe, because under Actions' `bash -e` (no pipefail) neither stops the step;
- no `${{ … }}` expression is written inside a `run:` script. Actions pastes the value
  into the script text before the shell reads it, so a value with a quote or a `$(`
  in it becomes code. Values reach scripts through `env:`, as both publish steps
  already say in their comments;
- `app/build.gradle.kts` makes `assembleDebug`, half of the push gate, build the
  release variant too, so a shrinker break fails the gate the day it lands; and
  `gradle.properties` does not set `skipStaticChecks`, which would switch that off, and
  the ratchets with it, for every build including CI's.

The publish rule reads one logical shell line. `if ! gh release upload …; then …; fi`
and a step-level `continue-on-error: true` are not judged; write a failing branch that
exits nonzero.
`${{ … }}` is judged in `run:` only, not in other script inputs such as
actions/github-script's `script:`.

Workflows are read line by line, not with a YAML parser: the rules only need a step's
extent and a `run:` block's extent, both of which indentation gives, and the tools
here run on a bare Python with no third-party packages.
"""
from __future__ import annotations

import re
from dataclasses import dataclass

SETUP_ANDROID = re.compile(r"""^\s*(?:-\s+)?uses:\s*['"]?android-actions/setup-android@""")
EMPTY_PACKAGES = re.compile(r"""^\s*packages:\s*(''|"")\s*(#.*)?$""")
RUN_KEY = re.compile(r"^(?P<indent>\s*)(?P<dash>-\s+)?run:\s*(?P<value>.*)$")
PUBLISH = re.compile(r"\bgh\s+release\s+(upload|create)\b")
# What follows a publish on the same logical line: `||`, `&&` or a lone `|`.
AFTER_OPERATOR = re.compile(r"\|\||&&|(?<![|>&])\|(?!\|)")
# `|| exit 1`, `|| { echo …; exit 2; }`: the failure is handled, not swallowed. The exit
# must be the group's last command, reached unconditionally (after `{` or `;`), with a
# status of 1 to 255 (256 wraps to 0).
_STATUS = r"(?:[1-9]|[1-9]\d|1\d\d|2[0-4]\d|25[0-5])"
EXITS_NONZERO = re.compile(
    r"^\s*(?:exit\s+" + _STATUS + r"\s*(?:;|#|$)"
    r"|\{(?:[^}]*;)?\s*exit\s+" + _STATUS + r"\s*;?\s*\})",
)
# `skipStaticChecks=true`, `skipStaticChecks true` or a bare `skipStaticChecks` all make
# the property present, which is all the build script asks.
SKIP_PROPERTY = re.compile(r"^[ \t]*skipStaticChecks(?=[ \t=:]|$)", re.M)
EXPRESSION = "${{"
GATE_WIRING = re.compile(
    r'it\.name\s*==\s*"assembleDebug"[\s\S]{0,600}?dependsOn\(\s*"assembleRelease"\s*\)',
)
# One left-to-right pass: strings are matched (and kept) before anything inside them can
# look like a comment. app/build.gradle.kts has globs such as "**/databinding/**", whose
# `/*` a plain comment strip would read as the start of a comment.
KOTLIN_STRINGS_AND_COMMENTS = re.compile(
    r'(?P<string>"""[\s\S]*?"""|"(?:\\.|[^"\\\n])*")|(?P<comment>/\*[\s\S]*?\*/|//[^\n]*)',
)
YAML_TRAILING_COMMENT = re.compile(r"\s+#.*$")


@dataclass(frozen=True)
class Finding:
    line: int
    message: str


def _indent(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def _is_blank_or_comment(line: str) -> bool:
    stripped = line.strip()
    return not stripped or stripped.startswith("#")


def _step_extent(lines: list[str], index: int) -> tuple[int, int]:
    """The step (list item) that contains lines[index], as a half-open range."""
    start = index
    item_indent = _indent(lines[index])
    while start >= 0:
        line = lines[start]
        if not _is_blank_or_comment(line) and line.lstrip().startswith("- ") and _indent(line) <= item_indent:
            item_indent = _indent(line)
            break
        start -= 1
    if start < 0:
        start, item_indent = index, _indent(lines[index])
    end = start + 1
    while end < len(lines):
        line = lines[end]
        if not _is_blank_or_comment(line) and _indent(line) <= item_indent:
            break
        end += 1
    return start, end


def setup_android_without_empty_packages(text: str) -> list[Finding]:
    lines = text.splitlines()
    findings = []
    for i, line in enumerate(lines):
        if not SETUP_ANDROID.match(line):
            continue
        start, end = _step_extent(lines, i)
        if not any(EMPTY_PACKAGES.match(lines[j]) for j in range(start, end)):
            findings.append(
                Finding(
                    i + 1,
                    "android-actions/setup-android without `packages: ''` — its default asks "
                    "sdkmanager for the retired `tools` package and fails before any project step",
                ),
            )
    return findings


def run_scripts(text: str) -> list[list[tuple[int, str]]]:
    """Every `run:` script as (1-based line number, text) pairs, block or inline."""
    lines = text.splitlines()
    scripts = []
    i = 0
    while i < len(lines):
        match = RUN_KEY.match(lines[i])
        if not match or lines[i].lstrip().startswith("#"):
            i += 1
            continue
        key_column = len(match.group("indent")) + len(match.group("dash") or "")
        value = match.group("value").strip()
        if value[:1] in ("|", ">"):
            body = []
            j = i + 1
            while j < len(lines):
                line = lines[j]
                if line.strip() and _indent(line) <= key_column:
                    break
                body.append((j + 1, line))
                j += 1
            scripts.append(body)
            i = j
        else:
            if value[:1] not in ("'", '"'):
                value = YAML_TRAILING_COMMENT.sub("", value)
            scripts.append([(i + 1, value)])
            i += 1
    return scripts


def _logical_lines(script: list[tuple[int, str]]) -> list[tuple[int, str]]:
    joined = []
    pending_line, pending = None, ""
    for number, line in script:
        stripped = line.strip()
        if pending_line is None:
            pending_line = number
        if stripped.endswith("\\"):
            pending += stripped[:-1] + " "
            continue
        joined.append((pending_line, pending + stripped))
        pending_line, pending = None, ""
    if pending_line is not None:
        joined.append((pending_line, pending))
    return joined


def swallowed_publishes(text: str) -> list[Finding]:
    findings = []
    for script in run_scripts(text):
        for number, line in _logical_lines(script):
            if line.lstrip().startswith("#"):
                continue
            match = PUBLISH.search(line)
            if not match:
                continue
            rest = line[match.end():]
            operator = AFTER_OPERATOR.search(rest)
            if not operator:
                continue
            if operator.group(0) == "||" and EXITS_NONZERO.match(rest[operator.end():]):
                continue
            findings.append(
                Finding(
                    number,
                    f"`gh release {match.group(1)}` followed by `{operator.group(0)}` — a publish "
                    "that fails must fail the job, not print a line and go green",
                ),
            )
    return findings


def expressions_in_run(text: str) -> list[Finding]:
    findings = []
    for script in run_scripts(text):
        for number, line in script:
            if EXPRESSION in line:
                findings.append(
                    Finding(
                        number,
                        "`${{ … }}` inside a `run:` script — pass the value through `env:` "
                        "and read it as a shell variable",
                    ),
                )
    return findings


def workflow_findings(text: str) -> list[Finding]:
    found = (
        setup_android_without_empty_packages(text)
        + swallowed_publishes(text)
        + expressions_in_run(text)
    )
    return sorted(found, key=lambda f: f.line)


def properties_skip_the_gate(properties_text: str) -> bool:
    """True when gradle.properties sets skipStaticChecks for every build."""
    return SKIP_PROPERTY.search(properties_text) is not None


def gate_builds_release(gradle_text: str) -> bool:
    """True when assembleDebug depends on assembleRelease in live code.

    Comments are stripped first: commenting the block out "for now" is the likeliest way
    to lose it, and a match inside a comment would keep this check green. Strings are
    skipped over, so a glob such as "**/x/**" cannot open a phantom comment. Not a full
    Kotlin lexer: nested block comments (legal Kotlin) end at the first `*/`.
    """
    live = KOTLIN_STRINGS_AND_COMMENTS.sub(
        lambda m: m.group("string") if m.group("string") is not None else "",
        gradle_text,
    )
    return GATE_WIRING.search(live) is not None
