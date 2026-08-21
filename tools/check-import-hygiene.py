#!/usr/bin/env python3
"""Two import mistakes the compiler rejects and every other check here misses.

**Duplicate imports.** Kotlin 2.0's K2 frontend rejects a byte-identical import line
that appears twice: `error: conflicting import: imported name 'X' is ambiguous`, once
per occurrence. It reads as a harmless copy-paste and is a hard build failure.
WorkoutRepository.kt carried the same import five times.

**Delegate operators.** `var x by remember { mutableStateOf(...) }` desugars to calls to
`getValue` and `setValue`, which for Compose's MutableState are top-level extension
operators in `androidx.compose.runtime` — they must be imported. Neither name appears
anywhere in the source text, so an importedness check driven by the tokens actually
written can never see them; HomeScreen.kt imported `getValue`, assigned to the property
in two places, and would not have compiled.

Exit code 1 on any finding.
"""
import re
import sys
from pathlib import Path

# Delegate producers whose getValue/setValue live in androidx.compose.runtime. `by lazy`
# and `by viewModels()` are deliberately absent: their operators come from packages that
# are default-imported, so they need nothing.
COMPOSE_DELEGATES = re.compile(
    r"\b(remember|rememberSaveable|mutableStateOf|mutableIntStateOf|mutableLongStateOf|"
    r"mutableFloatStateOf|mutableDoubleStateOf|derivedStateOf|collectAsState|"
    r"collectAsStateWithLifecycle|rememberCoroutineScope|produceState|"
    r"animateFloatAsState|animateDpAsState|animateColorAsState)\b"
)
DELEGATE_DECL = re.compile(r"^\s*(?:private\s+|internal\s+)?(val|var)\s+\w+\s+by\s+(.*)$")
IMPORT = re.compile(r"^import\s+(\S+)")


def check(path: Path) -> list[str]:
    findings: list[str] = []
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.split("\n")

    imports: dict[str, int] = {}
    names: set[str] = set()
    wildcards: set[str] = set()
    for number, line in enumerate(lines, start=1):
        match = IMPORT.match(line)
        if not match:
            continue
        target = match.group(1)
        if target.endswith(".*"):
            wildcards.add(target[:-2])
            continue
        if target in imports:
            findings.append(
                f"{path}:{number}  duplicate import: {target} "
                f"(already imported at line {imports[target]})"
            )
        else:
            imports[target] = number
        names.add(target.rsplit(".", 1)[-1])

    compose_wild = "androidx.compose.runtime" in wildcards
    for number, line in enumerate(lines, start=1):
        decl = DELEGATE_DECL.match(line)
        if not decl:
            continue
        kind, delegate = decl.group(1), decl.group(2)
        if not COMPOSE_DELEGATES.search(delegate):
            continue
        if compose_wild:
            continue
        needed = ["getValue"] if kind == "val" else ["getValue", "setValue"]
        missing = [n for n in needed if n not in names]
        if missing:
            findings.append(
                f"{path}:{number}  `{kind} … by` needs "
                + " and ".join(f"androidx.compose.runtime.{n}" for n in missing)
            )
    return findings


def main() -> int:
    roots = sys.argv[1:] or ["app/src/main/java", "app/src/test/java"]
    files = sorted(f for root in roots for f in Path(root).rglob("*.kt"))
    findings = [f for path in files for f in check(path)]
    for finding in findings:
        print(finding)
    print(f"\n{len(findings)} import problem(s) across {len(files)} files")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
