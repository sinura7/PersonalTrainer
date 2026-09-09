#!/usr/bin/env python3
"""Fixture proof for check-unbounded-waits.py.

A checker nobody has watched fail is a checker nobody knows works. This one
was written after two wedged CI runs, so each shape below is a shape that
either wedged or was mistaken for one. Preflight runs it.
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

class FooViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FooUiState())
    val uiState: StateFlow<FooUiState> = _uiState.asStateFlow()
    val error = errors.messages.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val finished = _finished.asStateFlow()
    val suggestedLift: Flow<Lift?> = repository.observeSuggestion()
    val insights: StateFlow<List<Insight>> = source.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

class FooStore {
    val uiState: StateFlow<Int> = MutableStateFlow(0)
    val snapshot: StateFlow<Int> = MutableStateFlow(0)
}
"""

TEST = """
package com.example

class FooViewModelTest {
    private var viewModel: FooViewModel? = null
    private lateinit var deps: FakeAppDependencies

    @Test
    fun unboundedPredicateOnState() = runBlocking {
        val vm = createViewModel()
        val state = vm.uiState.first { it.loaded }                      // L11 finding
        val again = viewModel!!.uiState.first { it.loaded }              // L12 finding
        val inline = createViewModel("missing").uiState.first { it.gone } // L13 finding
        val chained = createViewModel(handle).error.first { it != null } // L14 finding
        assertEquals(state, again)
    }

    @Test
    fun boundedIsFine() = runBlocking {
        val vm = createViewModel()
        withTimeout(TestWaits.FLOW_MS) { vm.uiState.first { it.loaded } }
        val multi = withTimeout(TestWaits.FLOW_MS) {
            vm.uiState.first {
                it.loaded && it.error == null
            }
        }
        val orNull = withTimeoutOrNull(1_000L) { vm.finished.first { it } }
        val current = vm.uiState.first()                                  // StateFlow.first() is the value
        val text = "vm.uiState.first { in a string }"
        // vm.uiState.first { in a comment }
    }

    @Test
    fun notViewModelWaits() = runBlocking {
        val vm = createViewModel()
        deps.workoutRepository.observeSession(id).first { it != null }    // repository
        deps.snapshot.first { it.running }                                 // store, not a ViewModel
        val state = vm.uiState.first { it.loaded }                        // L38 finding
        val row = state.insights.first { it.id == "x" }                   // a List named like a flow
        it.uiState.first { true }                                          // lambda parameter, not a handle
    }

    @Test
    fun plainFlowFirstWaits() = runBlocking {
        val vm = createViewModel()
        val lift = vm.suggestedLift.first()                                // L46 finding: not a StateFlow
        val helper = vm.awaitState { it.loaded }
        val leaky = vm.leakyState { it.loaded }
    }

    private suspend fun FooViewModel.awaitState(
        predicate: (FooUiState) -> Boolean,
    ): FooUiState = withTimeout(TestWaits.FLOW_MS) {
        uiState.first(predicate)
    }

    private suspend fun FooViewModel.leakyState(
        predicate: (FooUiState) -> Boolean,
    ): FooUiState = uiState.first(predicate)                                // L59 finding: name is not a ceiling
}
"""

OTHER_FILE = """
class FooRepositoryTest {
    @Test
    fun x() = runBlocking {
        val vm = createViewModel()
        vm.uiState.first { it.loaded }
    }
}
"""


def main() -> int:
    checker = load("check-unbounded-waits.py")
    root = Path(tempfile.mkdtemp(prefix="unbounded-waits-"))
    main_dir = root / "main"
    test_dir = root / "test"
    main_dir.mkdir()
    test_dir.mkdir()
    (main_dir / "FooViewModel.kt").write_text(MAIN, encoding="utf-8")
    test_path = test_dir / "FooViewModelTest.kt"
    test_path.write_text(TEST, encoding="utf-8")
    (test_dir / "FooRepositoryTest.kt").write_text(OTHER_FILE, encoding="utf-8")

    state, plain = checker.viewmodel_flow_properties([str(main_dir / "FooViewModel.kt")])
    if state != {"uiState", "error", "finished", "insights"}:
        raise SystemExit(f"FAIL state properties: {sorted(state)}")
    if plain != {"suggestedLift"}:
        raise SystemExit(f"FAIL plain flow properties: {sorted(plain)}")
    print("ok  properties come from *ViewModel classes only (FooStore.snapshot ignored)")

    found = checker.unbounded_waits(
        [str(test_path), str(test_dir / "FooRepositoryTest.kt")],
        state,
        plain,
    )
    lines = [line for path, line in found if path == str(test_path)]
    want = [11, 12, 13, 14, 38, 46, 59]
    if lines != want:
        raise SystemExit(f"FAIL expected findings at {want}, got {lines}")
    if any(path != str(test_path) for path, _ in found):
        raise SystemExit(f"FAIL only *ViewModelTest.kt files are read: {found}")
    print("ok  bare, !!-chained, inline-created and chained-property waits are findings")
    print("ok  withTimeout / withTimeoutOrNull / multi-line lambda regions are bounded")
    print("ok  StateFlow.first() is a value, not a wait; a plain Flow's first() is")
    print("ok  strings, comments, repository and store waits, List.first and `it.` are ignored")
    print("ok  an extension helper is bounded by its withTimeout, never by its name")
    print("ok  a repository test file is not read")

    table = {"skips": {"unbounded_waits": 7}}
    buf = io.StringIO()
    with redirect_stdout(buf):
        equal = checker_baseline.report("skips", "unbounded_waits", 7, table)
        grew = checker_baseline.report("skips", "unbounded_waits", 8, table)
    if equal is not None:
        raise SystemExit(f"FAIL equal-to-ceiling should pass: {equal}")
    if grew is None or "grew past baseline 7" not in grew:
        raise SystemExit(f"FAIL growth must error: {grew}")
    print(f"ok  ceiling fails closed: {grew}")
    print("test_unbounded_waits: all assertions passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
