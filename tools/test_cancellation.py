#!/usr/bin/env python3
"""Fixture proof for check-cancellation.py.

Each shape below is one the codebase actually had: the bare catch under a
`viewModelScope.launch`, the project wrapper that takes a suspend lambda, the
guard clause written with its package name, the rethrow inside the body, and
the JSON parser that must stay unflagged. Preflight runs it.
"""
from __future__ import annotations

import importlib.util
import io
import sys
import tempfile
from contextlib import redirect_stdout
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))
import checker_baseline  # noqa: E402


def load(filename: str):
    path = TOOLS / filename
    spec = importlib.util.spec_from_file_location(path.stem.replace("-", "_"), path)
    if spec is None or spec.loader is None:
        raise SystemExit(f"cannot load {path}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


MAIN = """
package com.example

class FooViewModel {
    private fun launchWrite(block: suspend () -> Unit): Job = viewModelScope.launch { block() }

    fun save() {
        viewModelScope.launch {
            try {
                repository.save()
            } catch (thrown: Exception) {                              // L11 finding: launch
                error.fail(source = "save", message = "no")
            }
        }
    }

    fun saveViaWrapper() = launchWrite {
        try {
            repository.save()
        } catch (thrown: Exception) {                                  // L20 finding: launchWrite
            AppLog.w(TAG, "save failed", thrown)
        }
    }

    fun saveWithMultiLineHeader() {
        viewModelScope.launch(
            start = CoroutineStart.UNDISPATCHED,
        ) {
            try {
                repository.save()
            } catch (thrown: Throwable) {                              // L31 finding: launch(...)
                AppLog.w(TAG, "save failed", thrown)
            }
        }
    }

    private suspend fun persist() {
        try {
            repository.save()
        } catch (thrown: Exception) {                                  // L40 finding: suspend fun
            AppLog.w(TAG, "persist failed", thrown)
        }
    }

    private suspend fun persistGuarded() {
        try {
            repository.save()
        } catch (thrown: kotlinx.coroutines.CancellationException) {
            throw thrown
        } catch (thrown: Exception) {                                  // guarded: earlier clause
            AppLog.w(TAG, "persist failed", thrown)
        }
    }

    private suspend fun persistGuardedTwice() {
        try {
            repository.save()
        } catch (thrown: CancellationException) {
            throw thrown
        } catch (thrown: IOException) {
            AppLog.w(TAG, "io", thrown)
        } catch (thrown: Exception) {                                  // guarded: two clauses back
            AppLog.w(TAG, "persist failed", thrown)
        }
    }

    private suspend fun persistRethrowing() {
        try {
            repository.save()
        } catch (thrown: Exception) {                                  // guarded: rethrow in body
            if (thrown is CancellationException) throw thrown
            AppLog.w(TAG, "persist failed", thrown)
        }
    }

    private suspend fun nestedGuardIsNotThisTrysGuard() {
        try {
            try {
                repository.save()
            } catch (inner: CancellationException) {
                throw inner
            }
            repository.saveMore()
        } catch (thrown: Exception) {                                  // L84 finding: inner guard is not ours
            AppLog.w(TAG, "persist failed", thrown)
        }
    }

    private suspend fun narrowCatchIsFine() {
        try {
            repository.save()
        } catch (thrown: IOException) {                                // not a bare catch
            AppLog.w(TAG, "io", thrown)
        }
    }

    fun parse(json: String): Int = try {
        JSONObject(json).getInt("n")
    } catch (thrown: Exception) {                                      // plain fun: cannot suspend
        0
    }

    fun mention() {
        val note = "catch (thrown: Exception) in a string"
        // catch (thrown: Exception) in a comment
    }
}
"""


def main() -> int:
    checker = load("check-cancellation.py")
    root = Path(tempfile.mkdtemp(prefix="cancellation-"))
    main_path = root / "FooViewModel.kt"
    main_path.write_text(MAIN, encoding="utf-8")

    wrappers = checker.suspend_wrappers([str(main_path)])
    if wrappers != {"launchWrite"}:
        raise SystemExit(f"FAIL suspend-lambda wrappers: {sorted(wrappers)}")
    print("ok  a function declared with a suspend lambda parameter is a suspending opener")

    found = checker.swallowing_catches([str(main_path)], wrappers)
    lines = [line for _, line, _ in found]
    want = [11, 20, 31, 40, 84]
    if lines != want:
        raise SystemExit(f"FAIL expected findings at {want}, got {found}")
    whys = {line: why for _, line, why in found}
    if "launch" not in whys[11] or "launchWrite" not in whys[20] or "launch" not in whys[31]:
        raise SystemExit(f"FAIL builder names: {whys}")
    if "suspend fun" not in whys[40] or "suspend fun" not in whys[84]:
        raise SystemExit(f"FAIL suspend fun context: {whys}")
    print("ok  launch, a project wrapper, a multi-line builder header and a suspend fun are findings")
    print("ok  an earlier CancellationException clause (bare or qualified, one or two back) guards")
    print("ok  a rethrow in the catch body guards; a nested try's guard does not")
    print("ok  a narrow catch, a plain function, a string and a comment are not counted")

    table = {"skips": {"cancellation_swallow": 5}}
    buf = io.StringIO()
    with redirect_stdout(buf):
        equal = checker_baseline.report("skips", "cancellation_swallow", 5, table)
        grew = checker_baseline.report("skips", "cancellation_swallow", 6, table)
    if equal is not None:
        raise SystemExit(f"FAIL equal-to-ceiling should pass: {equal}")
    if grew is None or "grew past baseline 5" not in grew:
        raise SystemExit(f"FAIL growth must error: {grew}")
    print(f"ok  ceiling fails closed: {grew}")
    print("test_cancellation: all assertions passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
