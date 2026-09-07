#!/usr/bin/env python3
"""Prove check-lambda-arity.py finds the defect it exists for, and stays quiet otherwise.

The defect is real: `SessionLiftStrip`'s `onStageTargets` grew to six parameters on
7 September 2026 and two call sites kept five. The FIXTURES below are that shape,
plus every shape the checker must NOT report, because a checker that cries wolf gets
switched off and then the wolf arrives.
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

TOOL = Path(__file__).resolve().parent / "check-lambda-arity.py"

DECLS = """package t

@Composable
fun Strip(
    label: String,
    onStage: (String, Int?, Int?, Int?, Double?, String?) -> Unit,
    onDone: () -> Unit,
    onPick: (Int) -> Unit,
    onMaybe: ((Int, Int) -> Unit)? = null,
    onSuspend: suspend (Int, Int) -> Unit,
    onReceiver: Scope.(Int) -> Unit,
    content: @Composable () -> Unit,
) = Unit
"""

# Each case: (name, kotlin, expected finding count)
CASES = [
    ("the real defect: five for six", """
fun a() = Strip(label = "x", onStage = { id, s, r, t, kg -> use(id) })
""", 1),
    ("the fix: six for six", """
fun a() = Strip(label = "x", onStage = { id, s, r, t, kg, why -> use(id) })
""", 0),
    ("one too many", """
fun a() = Strip(label = "x", onPick = { i, j -> use(i) })
""", 1),
    ("implicit it satisfies arity one", """
fun a() = Strip(label = "x", onPick = { use(it) })
""", 0),
    ("implicit it satisfies arity zero", """
fun a() = Strip(label = "x", onDone = { use(1) })
""", 0),
    ("no-arg lambda for arity two is a finding", """
fun a() = Strip(label = "x", onSuspend = { use(1) })
""", 1),
    ("nullable function type is read through", """
fun a() = Strip(label = "x", onMaybe = { i -> use(i) })
""", 1),
    ("a receiver type is never judged", """
fun a() = Strip(label = "x", onReceiver = { use(1) })
""", 0),
    ("a when inside the body is not a parameter arrow", """
fun a() = Strip(label = "x", onPick = { i -> when (i) { 1 -> use(1) else -> use(2) } })
""", 0),
    ("a nested lambda is not counted for the outer parameter", """
fun a() = Strip(label = "x", onPick = { i -> listOf(1).map { j -> use(j) } })
""", 0),
    ("a trailing lambda is out of scope", """
fun a() = Strip(label = "x", onPick = { use(it) }) { use(0) }
""", 0),
    ("a function reference is out of scope", """
fun a() = Strip(label = "x", onPick = ::use)
""", 0),
    ("a destructured parameter counts as one", """
fun a() = Strip(label = "x", onPick = { (p, q) -> use(p) })
""", 0),
    ("typed lambda parameters still count", """
fun a() = Strip(label = "x", onStage = { id: String, s: Int?, r: Int?, t: Int?, kg: Double?, why: String? -> use(id) })
""", 0),
    ("a commented-out call is not a call", """
// fun a() = Strip(label = "x", onStage = { id, s, r, t, kg -> use(id) })
fun a() = Unit
""", 0),
]


def run(root: Path) -> tuple[int, str]:
    p = subprocess.run(
        [sys.executable, str(TOOL), str(root)],
        capture_output=True, text=True, check=False,
    )
    return p.returncode, p.stdout


def main() -> int:
    failures = []
    for name, body, want in CASES:
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root / "Decls.kt").write_text(DECLS, encoding="utf-8")
            (root / "Case.kt").write_text("package t\n" + body, encoding="utf-8")
            code, out = run(root)
            got = int(out.strip().rsplit("\n", 1)[-1].split()[0])
            if got != want:
                failures.append(f"FAIL {name}: expected {want}, got {got}\n{out}")
            elif (code != 0) != (want > 0):
                failures.append(f"FAIL {name}: exit {code} disagrees with {want} finding(s)")
            else:
                print(f"ok  {name}")
    if failures:
        for f in failures:
            print(f)
        return 1
    print("test_lambda_arity: all assertions passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
