#!/usr/bin/env python3
"""Fixture proof for check-real-time-waits.py."""
from __future__ import annotations

import importlib.util
import io
import re
import sys
import tempfile
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))
import checker_baseline  # noqa: E402
from kotlin_source import strip_comments_and_strings  # noqa: E402


def load(filename: str):
    path = TOOLS / filename
    spec = importlib.util.spec_from_file_location(path.stem.replace("-", "_"), path)
    if spec is None or spec.loader is None:
        raise SystemExit(f"cannot load {path}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


FIXTURE_CASES: list[str] = []


def ok(label: str) -> None:
    FIXTURE_CASES.append(label)
    print(f"ok  {label}")


def expect_allowlist_systemexit(mod, path: Path, needle: str) -> None:
    try:
        mod.load_allowlist(str(path))
    except SystemExit as exc:
        msg = str(exc)
        if needle not in msg:
            raise SystemExit(f"FAIL allowlist error missing {needle!r}: {msg}") from exc
        if re.search(r":\d+:", msg) is None:
            raise SystemExit(f"FAIL allowlist error missing :line: {msg}") from exc
    except ValueError as exc:
        raise SystemExit(f"FAIL allowlist raised ValueError, expected SystemExit: {exc}") from exc
    else:
        raise SystemExit(f"FAIL allowlist should exit for {path}")


def write_allowlist(root: Path, rows: list[tuple[str, str, str, str]]) -> Path:
    path = root / "real-time-waits-allowlist.txt"
    lines = [f"{p} | {code} | {reason} | {count}" for p, code, reason, count in rows]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def run_checker(
    mod,
    allowlist: Path,
    test_root: Path,
    monkeypatch_allowlist: bool = True,
    *,
    sleep_ceiling: int | None = None,
    delay_ceiling: int | None = None,
) -> tuple[int, str, str]:
    if monkeypatch_allowlist:
        mod.ALLOWLIST_PATH = str(allowlist)
    real_report = mod.report_exact_count
    if sleep_ceiling is not None or delay_ceiling is not None:
        table = {
            "skips": {
                "real_time_sleeps": sleep_ceiling if sleep_ceiling is not None else 0,
                "real_time_delays": delay_ceiling if delay_ceiling is not None else 0,
            },
        }

        def _patched(group: str, key: str, actual: int, table_override=None):
            return real_report(group, key, actual, table_override or table)

        mod.report_exact_count = _patched
    buf_out, buf_err = io.StringIO(), io.StringIO()
    argv = ["check-real-time-waits.py", str(test_root)]
    with redirect_stdout(buf_out), redirect_stderr(buf_err):
        code = mod.main(argv)
    mod.report_exact_count = real_report
    return code, buf_out.getvalue(), buf_err.getvalue()


def main() -> int:
    checker = load("check-real-time-waits.py")
    root = Path(tempfile.mkdtemp(prefix="real-time-waits-"))
    test_dir = root / "test" / "com" / "example"
    test_dir.mkdir(parents=True)

    unlisted = test_dir / "UnlistedSleepTest.kt"
    unlisted.write_text(
        """
        class UnlistedSleepTest {
            @Test fun x() { Thread.sleep(10) }
        }
        """.strip(),
        encoding="utf-8",
    )
    allowlist = write_allowlist(root, [])
    mod = load("check-real-time-waits.py")
    mod.REPO_ROOT = str(root.resolve())
    mod.ALLOWLIST_PATH = str(allowlist)
    rel = str(unlisted.relative_to(root))
    body = strip_comments_and_strings(unlisted.read_text(encoding="utf-8"))
    hits = mod.find_sleeps(rel, body)
    if len(hits) != 1:
        raise SystemExit(f"FAIL expected one sleep in fixture, got {hits}")
    code, out, _err = run_checker(mod, allowlist, root / "test", False)
    if code == 0 or "real-time sleep not in allowlist" not in out or ":2:" not in out:
        raise SystemExit(f"FAIL unlisted sleep should fail: code={code} out={out}")
    if "Thread.sleep(" not in out:
        raise SystemExit(f"FAIL unlisted sleep message must include span text: {out}")
    ok("unlisted Thread.sleep fails with path:line and span")

    listed_root = Path(tempfile.mkdtemp(prefix="real-time-waits-listed-"))
    listed_dir = listed_root / "test" / "com" / "example"
    listed_dir.mkdir(parents=True)
    listed = listed_dir / "ListedSleepTest.kt"
    listed.write_text(
        "class ListedSleepTest { @Test fun x() { Thread.sleep(10) } }",
        encoding="utf-8",
    )
    rel_listed = str(listed.relative_to(listed_root))
    stripped_listed = strip_comments_and_strings(listed.read_text())
    _ln, line_text, _span = mod.find_sleeps(rel_listed, stripped_listed)[0]
    allowlist = write_allowlist(
        listed_root,
        [(rel_listed, line_text, "fixture", "1")],
    )
    mod.REPO_ROOT = str(listed_root.resolve())
    mod.ALLOWLIST_PATH = str(allowlist)
    code, out, _err = run_checker(
        mod, allowlist, listed_root / "test", False, sleep_ceiling=1, delay_ceiling=0,
    )
    if code != 0 or "real-time sleep not in allowlist" in out:
        raise SystemExit(f"FAIL listed sleep should pass: {code} {out}")
    ok("allowlisted sleep passes")

    for snippet, label in [
        ("SystemClock.sleep(1)", "SystemClock.sleep"),
        ("android.os.SystemClock.sleep(2)", "android.os.SystemClock.sleep"),
        ("TimeUnit.MILLISECONDS.sleep(3)", "TimeUnit.MILLISECONDS.sleep"),
    ]:
        snippet_root = Path(tempfile.mkdtemp(prefix="real-time-waits-forms-"))
        snippet_dir = snippet_root / "test" / "com" / "example"
        snippet_dir.mkdir(parents=True)
        mod.REPO_ROOT = str(snippet_root.resolve())
        path = snippet_dir / "SnippetTest.kt"
        path.write_text(f"class T {{ @Test fun x() {{ {snippet} }} }}", encoding="utf-8")
        rel_p = str(path.relative_to(snippet_root))
        stripped = strip_comments_and_strings(path.read_text())
        hits = mod.find_sleeps(rel_p, stripped)
        if not hits:
            raise SystemExit(f"FAIL {label} not detected")
        _line_no, code_text, _span = hits[0]
        allowlist = write_allowlist(snippet_root, [(rel_p, code_text, "x", "1")])
        mod.ALLOWLIST_PATH = str(allowlist)
        if (
            run_checker(
                mod, allowlist, snippet_root / "test", False, sleep_ceiling=1, delay_ceiling=0,
            )[0]
            != 0
        ):
            raise SystemExit(f"FAIL {label} allowlisted run")
    ok("SystemClock and TimeUnit sleep forms are caught")

    multiline_root = Path(tempfile.mkdtemp(prefix="real-time-waits-multiline-"))
    multiline_dir = multiline_root / "test" / "com" / "example"
    multiline_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(multiline_root.resolve())
    multiline = multiline_dir / "MultilineTimeUnitTest.kt"
    multiline.write_text(
        """
        class MultilineTimeUnitTest {
            @Test fun x() {
                TimeUnit
                    .MILLISECONDS
                    .sleep(4)
            }
        }
        """.strip(),
        encoding="utf-8",
    )
    rel_ml = str(multiline.relative_to(multiline_root))
    stripped_ml = strip_comments_and_strings(multiline.read_text())
    if not mod.find_sleeps(rel_ml, stripped_ml):
        raise SystemExit("FAIL multiline TimeUnit.sleep not detected")
    ok("TimeUnit.sleep matches across line breaks")

    comment_root = Path(tempfile.mkdtemp(prefix="real-time-waits-comment-"))
    comment_dir = comment_root / "test" / "com" / "example"
    comment_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(comment_root.resolve())
    commented = comment_dir / "CommentSleepTest.kt"
    commented.write_text(
        """
        class CommentSleepTest {
            // Thread.sleep(99)
            @Test fun x() { val s = "Thread.sleep(1)"; }
        }
        """.strip(),
        encoding="utf-8",
    )
    rel_c = str(commented.relative_to(comment_root))
    if mod.find_sleeps(rel_c, strip_comments_and_strings(commented.read_text())):
        raise SystemExit("FAIL sleep in comment/string counted")
    ok("comment and string sleeps ignored")

    stale_root = Path(tempfile.mkdtemp(prefix="real-time-waits-stale-"))
    stale_dir = stale_root / "test" / "com" / "example"
    stale_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(stale_root.resolve())
    stale_path = stale_dir / "StaleAllowTest.kt"
    stale_path.write_text("class StaleAllowTest { @Test fun x() {} }", encoding="utf-8")
    allowlist = write_allowlist(
        stale_root,
        [
            (
                str(stale_path.relative_to(stale_root)),
                "Thread.sleep(1)",
                "removed sleep",
                "1",
            ),
        ],
    )
    mod.ALLOWLIST_PATH = str(allowlist)
    code, out, _err = run_checker(mod, allowlist, stale_root / "test", False)
    if code == 0 or "allowlist entry has no matching sleep" not in out:
        raise SystemExit(f"FAIL stale allowlist should fail: code={code} out={out}")
    ok("stale allowlist entry fails closed")

    delay_root = Path(tempfile.mkdtemp(prefix="real-time-waits-delay-"))
    delay_dir = delay_root / "test" / "com" / "example"
    delay_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(delay_root.resolve())
    virtual = delay_dir / "VirtualDelayTest.kt"
    virtual.write_text(
        """
        class VirtualDelayTest {
            @Test fun x() = runTest { delay(1) }
        }
        """.strip(),
        encoding="utf-8",
    )
    rel_v = str(virtual.relative_to(delay_root))
    raw = virtual.read_text()
    stripped = strip_comments_and_strings(raw)
    if mod.count_delays(rel_v, raw, stripped) != 0:
        raise SystemExit("FAIL delay under runTest should not count")
    ok("delay in runTest file is not counted")

    comment_marker = delay_dir / "CommentMarkerDelayTest.kt"
    comment_marker.write_text(
        """
        class CommentMarkerDelayTest {
            // runTest would hide this if we scanned raw text
            @Test fun x() = runBlocking { delay(1) }
        }
        """.strip(),
        encoding="utf-8",
    )
    rel_cm = str(comment_marker.relative_to(delay_root))
    raw_cm = comment_marker.read_text()
    stripped_cm = strip_comments_and_strings(raw_cm)
    if mod.count_delays(rel_cm, raw_cm, stripped_cm) != 1:
        raise SystemExit("FAIL delay counts when runTest only appears in comment")
    ok("runTest in comment does not exempt delay()")

    real_delay = delay_dir / "RealDelayTest.kt"
    real_delay.write_text(
        "class RealDelayTest { @Test fun x() = runBlocking { delay(1) } }",
        encoding="utf-8",
    )
    rel_d = str(real_delay.relative_to(delay_root))
    raw_d = real_delay.read_text()
    stripped_d = strip_comments_and_strings(raw_d)
    if mod.count_delays(rel_d, raw_d, stripped_d) != 1:
        raise SystemExit("FAIL bare delay should count")
    ok("delay without virtual-time marker counts")

    move_root = Path(tempfile.mkdtemp(prefix="real-time-waits-move-"))
    move_dir = move_root / "test" / "com" / "example"
    move_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(move_root.resolve())
    moved = move_dir / "MovedLineTest.kt"
    moved.write_text(
        "class MovedLineTest {\n    @Test fun x() { Thread.sleep(7) }\n}\n",
        encoding="utf-8",
    )
    rel_m = str(moved.relative_to(move_root))
    stripped_before = strip_comments_and_strings(moved.read_text())
    _ln, line_text, _span = mod.find_sleeps(rel_m, stripped_before)[0]
    allowlist = write_allowlist(move_root, [(rel_m, line_text, "moved", "1")])
    mod.ALLOWLIST_PATH = str(allowlist)
    moved.write_text(
        "class MovedLineTest {\n\n    @Test fun x() { Thread.sleep(7) }\n}\n",
        encoding="utf-8",
    )
    code, out, _err = run_checker(
        mod, allowlist, move_root / "test", False, sleep_ceiling=1, delay_ceiling=0,
    )
    if code != 0:
        raise SystemExit(f"FAIL allowlist matches by code text after line move: {out}")
    ok("allowlist matches by code text when line number shifts")

    import_root = Path(tempfile.mkdtemp(prefix="real-time-waits-import-"))
    import_dir = import_root / "test" / "com" / "example"
    import_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(import_root.resolve())
    for src, needle in [
        ("import java.lang.Thread.sleep\nclass T { @Test fun x() { sleep(1) } }", "Thread.sleep"),
        (
            "import java.util.concurrent.TimeUnit.MILLISECONDS\n"
            "class T { @Test fun x() { MILLISECONDS.sleep(1) } }",
            "TimeUnit",
        ),
        (
            "import kotlinx.coroutines.delay as wait\n"
            "class T { @Test fun x() = runBlocking { wait(1) } }",
            "delay as",
        ),
    ]:
        path = import_dir / f"ImportTest_{needle.replace(' ', '_')}.kt"
        path.write_text(src, encoding="utf-8")
        allowlist = write_allowlist(import_root, [])
        mod.ALLOWLIST_PATH = str(allowlist)
        code, out, _err = run_checker(mod, allowlist, import_root / "test", False)
        if code == 0 or "forbidden real-time import" not in out:
            raise SystemExit(f"FAIL forbidden import ({needle}): {code} {out}")
    ok("forbidden sleep/delay import forms fail")

    count_root = Path(tempfile.mkdtemp(prefix="real-time-waits-count-"))
    count_dir = count_root / "test" / "com" / "example"
    count_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(count_root.resolve())
    dup = count_dir / "DupSleepTest.kt"
    dup.write_text(
        "class DupSleepTest {\n"
        "  @Test fun a() {\n"
        "    Thread.sleep(2)\n"
        "  }\n"
        "  @Test fun b() {\n"
        "    Thread.sleep(2)\n"
        "  }\n"
        "}\n",
        encoding="utf-8",
    )
    rel_dup = str(dup.relative_to(count_root))
    stripped_dup = strip_comments_and_strings(dup.read_text())
    _ln, dup_code, _span = mod.find_sleeps(rel_dup, stripped_dup)[0]
    allowlist = write_allowlist(
        count_root,
        [(rel_dup, dup_code, "two sites", "2")],
    )
    mod.ALLOWLIST_PATH = str(allowlist)
    if (
        run_checker(
            mod, allowlist, count_root / "test", False, sleep_ceiling=2, delay_ceiling=0,
        )[0]
        != 0
    ):
        raise SystemExit("FAIL allowlist site-count 2 should pass")
    allowlist_short = write_allowlist(
        count_root,
        [(rel_dup, dup_code, "expects three", "3")],
    )
    mod.ALLOWLIST_PATH = str(allowlist_short)
    code, out, _ = run_checker(mod, allowlist_short, count_root / "test", False)
    if code == 0 or "under-covered" not in out:
        raise SystemExit(f"FAIL under-covered allowlist count: {code} {out}")
    if "not in allowlist" in out:
        raise SystemExit(f"FAIL under-covered must not also report unlisted sites: {out}")
    ok("optional allowlist site-count pins duplicate line text")

    allowlist_one = write_allowlist(count_root, [(rel_dup, dup_code, "one site", "1")])
    mod.ALLOWLIST_PATH = str(allowlist_one)
    code, out, _ = run_checker(mod, allowlist_one, count_root / "test", False)
    if code == 0 or "allows 1 site(s), 2 found" not in out:
        raise SystemExit(f"FAIL over-covered allowlist sites: {code} {out}")
    if out.count("allows 1 site(s), 2 found") != 2:
        raise SystemExit(f"FAIL over-covered must list every site: {out}")
    ok("over-covered allowlist reports every path:line with N allowed, M found")

    table = {"skips": {"real_time_sleeps": 2, "real_time_delays": 1}}
    equal = checker_baseline.report("skips", "real_time_sleeps", 2, table)
    grew = checker_baseline.report("skips", "real_time_sleeps", 3, table)
    if equal is not None:
        raise SystemExit(f"FAIL equal ceiling: {equal}")
    if grew is None or "grew past baseline" not in grew:
        raise SystemExit(f"FAIL growth: {grew}")
    buf_below = io.StringIO()
    with redirect_stdout(buf_below):
        below = checker_baseline.report("skips", "real_time_sleeps", 1, table)
    if below is not None:
        raise SystemExit(f"FAIL below ceiling should not error: {below}")
    if "below" not in buf_below.getvalue():
        raise SystemExit(f"FAIL below ceiling hint missing: {buf_below.getvalue()}")
    ok("checker_baseline.report still hints below for other checkers")

    strict = load("check-real-time-waits.py")
    if strict.report_exact_count("skips", "real_time_sleeps", 1) is None:
        raise SystemExit("FAIL report_exact_count should fail when below baseline")
    ok("report_exact_count fails until baseline ratchets down")

    nested_root = Path(tempfile.mkdtemp(prefix="real-time-waits-nested-comment-"))
    nested_dir = nested_root / "test" / "com" / "example"
    nested_dir.mkdir(parents=True)
    nested = nested_dir / "NestedCommentSleepTest.kt"
    nested.write_text(
        "class NestedCommentSleepTest {\n"
        "    /* a /* b */ Thread.sleep(99) */\n"
        "    @Test fun x() { Thread.sleep(3) }\n"
        "}\n",
        encoding="utf-8",
    )
    rel_n = str(nested.relative_to(nested_root))
    stripped_n = strip_comments_and_strings(nested.read_text())
    hits_n = mod.find_sleeps(rel_n, stripped_n)
    if len(hits_n) != 1:
        raise SystemExit(f"FAIL nested block comment hid inner sleep only: {hits_n}")
    ok("nested block comments keep outer comment text blanked")

    split_root = Path(tempfile.mkdtemp(prefix="real-time-waits-thread-split-"))
    split_dir = split_root / "test" / "com" / "example"
    split_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(split_root.resolve())
    split = split_dir / "SplitThreadSleepTest.kt"
    split.write_text(
        """
        class SplitThreadSleepTest {
            @Test fun x() {
                Thread
                    .sleep(6)
            }
        }
        """.strip(),
        encoding="utf-8",
    )
    rel_s = str(split.relative_to(split_root))
    stripped_s = strip_comments_and_strings(split.read_text())
    if not mod.find_sleeps(rel_s, stripped_s):
        raise SystemExit("FAIL Thread.sleep split across lines not detected")
    ok("Thread.sleep split across lines is caught")

    tempo_root = Path(tempfile.mkdtemp(prefix="real-time-waits-tempo-root-"))
    tempo_dir = tempo_root / "tempo" / "src" / "test" / "kotlin" / "com" / "example"
    tempo_dir.mkdir(parents=True)
    tempo_file = tempo_dir / "TempoSleepTest.kt"
    tempo_file.write_text("class TempoSleepTest { @Test fun x() { Thread.sleep(1) } }", encoding="utf-8")
    mod.REPO_ROOT = str(tempo_root.resolve())
    rel_t = str(tempo_file.relative_to(tempo_root))
    _ln, line_text, _span = mod.find_sleeps(
        rel_t, strip_comments_and_strings(tempo_file.read_text()),
    )[0]
    allowlist = write_allowlist(tempo_root, [(rel_t, line_text, "tempo fixture", "1")])
    mod.ALLOWLIST_PATH = str(allowlist)
    if (
        run_checker(
            mod,
            allowlist,
            str(tempo_root / "tempo" / "src" / "test" / "kotlin"),
            False,
            sleep_ceiling=1,
            delay_ceiling=0,
        )[0]
        != 0
    ):
        raise SystemExit("FAIL tempo test source root scan")
    ok("tempo/src/test/kotlin root is scannable")

    bad_allow_root = Path(tempfile.mkdtemp(prefix="real-time-waits-bad-allow-"))
    bad_path = bad_allow_root / "real-time-waits-allowlist.txt"
    bad_path.write_text("x.kt | Thread.sleep(1) | reason | 0\n", encoding="utf-8")
    expect_allowlist_systemexit(mod, bad_path, "site count must be >= 1")
    bad_path.write_text("x.kt | Thread.sleep(1) | reason | abc\n", encoding="utf-8")
    expect_allowlist_systemexit(mod, bad_path, "ASCII digits")
    bad_path.write_text("x.kt | Thread.sleep(1) | reason | 1 | extra\n", encoding="utf-8")
    expect_allowlist_systemexit(mod, bad_path, "expected path | code | reason | count")
    bad_path.write_text("x.kt | Thread.sleep(1) | reason\n", encoding="utf-8")
    expect_allowlist_systemexit(mod, bad_path, "expected path | code | reason | count")
    bad_path.write_text("x.kt | Thread.sleep(1) |  | 1\n", encoding="utf-8")
    expect_allowlist_systemexit(mod, bad_path, "reason must be non-empty")
    bad_path.write_text(
        "x.kt | Thread.sleep(1) | r1 | 1\nx.kt | Thread.sleep(1) | r2 | 1\n",
        encoding="utf-8",
    )
    expect_allowlist_systemexit(mod, bad_path, "duplicate allowlist row")
    bad_path.write_bytes("\ufeffx.kt | Thread.sleep(1) | bom | 1\n".encode("utf-8"))
    rows = mod.load_allowlist(str(bad_path))
    if len(rows) != 1 or rows[0][0] != "x.kt" or rows[0][2] != "bom":
        raise SystemExit(f"FAIL utf-8-sig BOM allowlist row: {rows}")
    bad_path.write_text("x.kt | Thread.sleep(1) | reason | \u0661\n", encoding="utf-8")
    expect_allowlist_systemexit(mod, bad_path, "ASCII digits")
    bad_path.write_text(
        "./app/a.kt | Thread.sleep(1) | r1 | 1\n"
        "app/a.kt | Thread.sleep(1) | r2 | 1\n",
        encoding="utf-8",
    )
    expect_allowlist_systemexit(mod, bad_path, "duplicate allowlist row")
    bad_path.write_text("/etc/passwd | Thread.sleep(1) | abs | 1\n", encoding="utf-8")
    expect_allowlist_systemexit(mod, bad_path, "absolute path")
    ok("allowlist | N validation rejects bad rows with path:line")

    alias_root = Path(tempfile.mkdtemp(prefix="real-time-waits-alias-"))
    alias_dir = alias_root / "test" / "com" / "example"
    alias_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(alias_root.resolve())
    alias_file = alias_dir / "AliasThreadTest.kt"
    alias_file.write_text(
        "import java.lang.Thread as Sleeper\nclass T { @Test fun x() { Sleeper.sleep(1) } }",
        encoding="utf-8",
    )
    allowlist = write_allowlist(alias_root, [])
    mod.ALLOWLIST_PATH = str(allowlist)
    code, out, _ = run_checker(mod, allowlist, alias_root / "test", False)
    if code == 0 or "Thread import alias" not in out:
        raise SystemExit(f"FAIL Thread import alias: {code} {out}")
    ok("Thread import alias fails")

    for src, label in [
        (
            "import android.os.SystemClock as Nap\nclass T { @Test fun x() { Nap.sleep(1) } }",
            "SystemClock import alias",
        ),
        (
            "import java.util.concurrent.TimeUnit as TU\n"
            "class T { @Test fun x() { TU.MILLISECONDS.sleep(1) } }",
            "TimeUnit import alias",
        ),
    ]:
        path = alias_dir / f"Alias_{label.replace(' ', '_')}.kt"
        path.write_text(src, encoding="utf-8")
        allowlist = write_allowlist(alias_root, [])
        mod.ALLOWLIST_PATH = str(allowlist)
        code, out, _ = run_checker(mod, allowlist, alias_root / "test", False)
        if code == 0 or label not in out:
            raise SystemExit(f"FAIL {label}: {code} {out}")
    ok("SystemClock and TimeUnit import aliases fail")

    mref_root = Path(tempfile.mkdtemp(prefix="real-time-waits-mref-"))
    mref_dir = mref_root / "test" / "com" / "example"
    mref_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(mref_root.resolve())
    mref = mref_dir / "MethodRefSleepTest.kt"
    mref.write_text("class T { @Test fun x() { SystemClock::sleep(1) } }", encoding="utf-8")
    allowlist = write_allowlist(mref_root, [])
    mod.ALLOWLIST_PATH = str(allowlist)
    code, out, _ = run_checker(mod, allowlist, mref_root / "test", False)
    if code == 0 or "forbidden real-time sleep reference" not in out:
        raise SystemExit(f"FAIL SystemClock::sleep: {code} {out}")
    ok("SystemClock::sleep method reference fails")

    thread_mref = mref_dir / "ThreadMethodRefSleepTest.kt"
    thread_mref.write_text("class T { @Test fun x() { Thread::sleep(1) } }", encoding="utf-8")
    code, out, _ = run_checker(mod, allowlist, mref_root / "test", False)
    if code == 0 or "Thread::sleep" not in out:
        raise SystemExit(f"FAIL Thread::sleep: {code} {out}")
    ok("Thread::sleep method reference fails")

    tu_mref = mref_dir / "TimeUnitMethodRefSleepTest.kt"
    tu_mref.write_text(
        "class T { @Test fun x() { TimeUnit.SECONDS::sleep(1) } }",
        encoding="utf-8",
    )
    code, out, _ = run_checker(mod, allowlist, mref_root / "test", False)
    if code == 0 or "TimeUnit" not in out or "forbidden real-time sleep reference" not in out:
        raise SystemExit(f"FAIL TimeUnit.SECONDS::sleep: {code} {out}")
    ok("TimeUnit.SECONDS::sleep method reference fails")

    import_runtest = delay_dir / "ImportRunTestOnlyDelayTest.kt"
    import_runtest.write_text(
        """
        import kotlinx.coroutines.test.runTest
        class ImportRunTestOnlyDelayTest {
            @Test fun x() = runBlocking { delay(1) }
        }
        """.strip(),
        encoding="utf-8",
    )
    rel_ir = str(import_runtest.relative_to(delay_root))
    raw_ir = import_runtest.read_text()
    stripped_ir = strip_comments_and_strings(raw_ir)
    if mod.count_delays(rel_ir, raw_ir, stripped_ir) != 1:
        raise SystemExit("FAIL runTest on import line must not exempt delay()")
    ok("runTest on import line does not exempt delay()")

    boundary = delay_dir / "NotRunTestMarkerDelayTest.kt"
    boundary.write_text(
        "class NotRunTestMarkerDelayTest { @Test fun x() = runBlocking { delay(1) } }",
        encoding="utf-8",
    )
    rel_b = str(boundary.relative_to(delay_root))
    raw_b = boundary.read_text()
    stripped_b = strip_comments_and_strings(
        raw_b + "\nfun runTestish() {}\nfun notrunTestX() {}",
    )
    if mod.count_delays(rel_b, raw_b, stripped_b) != 1:
        raise SystemExit("FAIL runTestish/notrunTestX must not count as runTest marker")
    ok("runTest marker uses word boundaries")

    kts_root = Path(tempfile.mkdtemp(prefix="real-time-waits-kts-"))
    kts_dir = kts_root / "test" / "com" / "example"
    kts_dir.mkdir(parents=True)
    mod.REPO_ROOT = str(kts_root.resolve())
    kts = kts_dir / "SleepScript.kts"
    kts.write_text("Thread.sleep(8)", encoding="utf-8")
    rel_k = str(kts.relative_to(kts_root))
    _ln, kts_code, _span = mod.find_sleeps(rel_k, strip_comments_and_strings(kts.read_text()))[0]
    allowlist = write_allowlist(kts_root, [(rel_k, kts_code, "kts", "1")])
    mod.ALLOWLIST_PATH = str(allowlist)
    if (
        run_checker(mod, allowlist, kts_root / "test", False, sleep_ceiling=1, delay_ceiling=0)[0]
        != 0
    ):
        raise SystemExit("FAIL .kts test sources should scan")
    ok(".kts files under test roots are scanned")

    repo = TOOLS.parent
    mod.REPO_ROOT = str(repo.resolve())
    roots = mod.default_roots()
    expected_roots = [
        "app/src/androidTest",
        "app/src/sharedTest",
        "app/src/test",
        "tempo/src/test",
    ]
    if sorted(roots) != expected_roots:
        raise SystemExit(f"FAIL default_roots exact set: {roots}")
    if "tempo/src/test" not in roots or "app/src/sharedTest" not in roots:
        raise SystemExit(f"FAIL default_roots missing tempo or sharedTest: {roots}")
    ok("default_roots() discovers exactly the four repo test trees")

    disc_root = Path(tempfile.mkdtemp(prefix="real-time-waits-discover-"))
    app_mod = disc_root / "app"
    app_mod.mkdir(parents=True)
    (app_mod / "build.gradle.kts").write_text("", encoding="utf-8")
    set_paths = {
        "app/src/test/kotlin/T.kt": "test",
        "app/src/androidTest/kotlin/A.kt": "androidTest",
        "app/src/androidTestDebug/kotlin/D.kt": "androidTestDebug",
        "app/src/sharedTest/java/S.kt": "sharedTest",
        "app/src/testFixtures/kotlin/F.kt": "testFixtures",
    }
    for rel, _kind in set_paths.items():
        path = disc_root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("class X { @Test fun x() { Thread.sleep(1) } }", encoding="utf-8")
    (disc_root / "tempo").mkdir(parents=True)
    (disc_root / "tempo" / "build.gradle.kts").write_text("", encoding="utf-8")
    legacy = disc_root / "legacy"
    legacy.mkdir(parents=True)
    (legacy / "build.gradle").write_text("", encoding="utf-8")
    legacy_test = legacy / "src" / "test" / "kotlin" / "Legacy.kt"
    legacy_test.parent.mkdir(parents=True, exist_ok=True)
    legacy_test.write_text("class X { @Test fun x() { Thread.sleep(1) } }", encoding="utf-8")
    tempo_java = disc_root / "tempo" / "src" / "test" / "java" / "T.kt"
    tempo_java.parent.mkdir(parents=True, exist_ok=True)
    tempo_java.write_text("class X { @Test fun x() { Thread.sleep(1) } }", encoding="utf-8")
    mod.REPO_ROOT = str(disc_root.resolve())
    found_roots = mod.discover_test_source_roots(str(disc_root))
    for need in (
        "app/src/test",
        "app/src/androidTest",
        "app/src/androidTestDebug",
        "app/src/sharedTest",
        "app/src/testFixtures",
        "tempo/src/test",
        "legacy/src/test",
    ):
        if need not in found_roots:
            raise SystemExit(f"FAIL discovery fixture missing {need}: {found_roots}")
    orphan = disc_root / "nested" / "src" / "test" / "Orphan.kt"
    orphan.parent.mkdir(parents=True, exist_ok=True)
    orphan.write_text("class O { @Test fun x() { Thread.sleep(1) } }", encoding="utf-8")
    try:
        mod.discover_test_source_roots(str(disc_root))
    except SystemExit as exc:
        if "nested/src/test" not in str(exc) or "recognised Gradle module" not in str(exc):
            raise SystemExit(f"FAIL orphan test set message: {exc}") from exc
    else:
        raise SystemExit("FAIL orphan test source set should exit")
    allow_rows = []
    for rel in list(set_paths) + ["tempo/src/test/java/T.kt", "legacy/src/test/kotlin/Legacy.kt"]:
        p = disc_root / rel
        stripped = strip_comments_and_strings(p.read_text())
        _ln, code_text, _span = mod.find_sleeps(rel, stripped)[0]
        allow_rows.append((rel, code_text, "discovery fixture", "1"))
    allowlist = write_allowlist(disc_root, allow_rows)
    mod.ALLOWLIST_PATH = str(allowlist)
    real_report = mod.report_exact_count
    mod.report_exact_count = lambda g, k, a, t=None: real_report(
        g, k, a, {"skips": {"real_time_sleeps": 7, "real_time_delays": 0}},
    )
    abs_roots = [str(disc_root / r) for r in found_roots]
    buf = io.StringIO()
    with redirect_stdout(buf):
        scan_code = mod.main(["check-real-time-waits.py", *abs_roots])
    mod.report_exact_count = real_report
    if scan_code != 0:
        raise SystemExit(f"FAIL discovered test source roots scan: {buf.getvalue()}")
    ok("discovered test source set directories are scanned")

    fp_root = Path(tempfile.mkdtemp(prefix="real-time-waits-fp-"))
    app_fp = fp_root / "app"
    app_fp.mkdir(parents=True)
    (app_fp / "build.gradle.kts").write_text("", encoding="utf-8")
    gradle_trap = fp_root / ".gradle" / "src" / "test" / "Trap.kt"
    gradle_trap.parent.mkdir(parents=True, exist_ok=True)
    gradle_trap.write_text("class Trap { @Test fun x() { Thread.sleep(1) } }", encoding="utf-8")
    main_trap = app_fp / "src" / "main" / "src" / "test" / "MainTrap.kt"
    main_trap.parent.mkdir(parents=True, exist_ok=True)
    main_trap.write_text("class MainTrap { @Test fun x() { Thread.sleep(1) } }", encoding="utf-8")
    legit = app_fp / "src" / "test" / "kotlin" / "Legit.kt"
    legit.parent.mkdir(parents=True, exist_ok=True)
    legit.write_text("class Legit { @Test fun x() { Thread.sleep(1) } }", encoding="utf-8")
    fp_roots = mod.discover_test_source_roots(str(fp_root))
    if fp_roots != ["app/src/test"]:
        raise SystemExit(f"FAIL discovery false positives included: {fp_roots}")
    ok("discovery ignores .gradle and nested src/test under main")

    count = len(FIXTURE_CASES)
    print(f"test_real_time_waits: all assertions passed ({count} fixture cases)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
