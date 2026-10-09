#!/usr/bin/env python3
"""Generic sealed reads must be indexed, including a deliberately missing case."""
from pathlib import Path
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parent.parent
CHECKER = ROOT / "tools/check-when-exhaustive.py"


def inspect(root: Path, source: str) -> str:
    (root / "Read.kt").write_text(source, encoding="utf-8")
    run = subprocess.run(
        [sys.executable, str(CHECKER), str(root)],
        cwd=ROOT, text=True, capture_output=True, check=False,
    )
    if run.returncode != 0:
        raise AssertionError(f"checker execution failed: {run.stdout}\n{run.stderr}")
    return run.stdout


def main() -> None:
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="when-exhaustive-", dir=build) as temporary:
        root = Path(temporary)
        for kind in ("class", "interface"):
            declaration = f"""
sealed {kind} ReadHealth<out T> {{
    data class Available<T>(val value: T) : ReadHealth<T>{'()' if kind == 'class' else ''}
    data class Degraded<T>(val value: T) : ReadHealth<T>{'()' if kind == 'class' else ''}
    data object Unavailable : ReadHealth<Nothing>{'()' if kind == 'class' else ''}
}}
// An unrelated type shares a name. It must not own the generic read's branches.
sealed class StartOutcome {{
    data object Started : StartOutcome()
    data object Unavailable : StartOutcome()
}}
"""
            complete = declaration + """
fun label(health: ReadHealth<Int>): String = when (health) {
    is ReadHealth.Available -> "ready"
    is ReadHealth.Degraded -> "stale"
    is ReadHealth.Unavailable -> "retry"
}
"""
            result = inspect(root, complete)
            assert "0 non-exhaustive when block(s); 2 types indexed, 0 block(s) skipped" in result, result
            print(f"ok  generic sealed {kind} with variance is indexed and exhaustive")
            missing = complete.replace('    is ReadHealth.Degraded -> "stale"\n', '')
            result = inspect(root, missing)
            assert "when over ReadHealth is missing: ['Degraded']" in result, result
            assert "1 non-exhaustive when block(s); 2 types indexed, 0 block(s) skipped" in result, result
            print(f"ok  missing generic sealed {kind} case is detected despite a shared label")
    print("test_when_exhaustive: all assertions passed")


if __name__ == "__main__":
    main()
