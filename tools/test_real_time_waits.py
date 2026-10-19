#!/usr/bin/env python3
"""Fixture proof for check-real-time-waits.py."""
from __future__ import annotations

import importlib.util
import io
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
    print("ok  unlisted Thread.sleep fails with path:line and span")

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
    print("ok  allowlisted sleep passes")

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
    print("ok  SystemClock and TimeUnit sleep forms are caught")

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
    print("ok  TimeUnit.sleep matches across line breaks")

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
    print("ok  comment and string sleeps ignored")

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
    print("ok  stale allowlist entry fails closed")

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
    print("ok  delay in runTest file is not counted")

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
    print("ok  runTest in comment does not exempt delay()")

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
    print("ok  delay without virtual-time marker counts")

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
    print("ok  allowlist matches by code text when line number shifts")

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
    print("ok  forbidden sleep/delay import forms fail")

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
    print("ok  optional allowlist site-count pins duplicate line text")

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
    print("ok  checker_baseline.report still hints below for other checkers")

    strict = load("check-real-time-waits.py")
    if strict.report_exact_count("skips", "real_time_sleeps", 1) is None:
        raise SystemExit("FAIL report_exact_count should fail when below baseline")
    print("ok  report_exact_count fails until baseline ratchets down")

    nested_root = Path(tempfile.mkdtemp(prefix="real-time-waits-nested-comment-"))
    nested_dir = nested_root / "test" / "com" / "example"
    nested_dir.mkdir(parents=True)
    nested = nested_dir / "NestedCommentSleepTest.kt"
    nested.write_text(
        """
        class NestedCommentSleepTest {
            /*
             * outer /* inner Thread.sleep(99) */ still comment
             */
            @Test fun x() { Thread.sleep(3) }
        }
        """.strip(),
        encoding="utf-8",
    )
    rel_n = str(nested.relative_to(nested_root))
    stripped_n = strip_comments_and_strings(nested.read_text())
    hits_n = mod.find_sleeps(rel_n, stripped_n)
    if len(hits_n) != 1:
        raise SystemExit(f"FAIL nested block comment hid inner sleep only: {hits_n}")
    print("ok  nested block comments keep outer comment text blanked")

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
    print("ok  Thread.sleep split across lines is caught")

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
    print("ok  tempo/src/test/kotlin root is scannable")

    bad_allow_root = Path(tempfile.mkdtemp(prefix="real-time-waits-bad-allow-"))
    bad_path = bad_allow_root / "real-time-waits-allowlist.txt"
    bad_path.write_text("x.kt | Thread.sleep(1) | reason | 0\n", encoding="utf-8")
    mod.ALLOWLIST_PATH = str(bad_path)
    try:
        mod.load_allowlist(str(bad_path))
    except SystemExit:
        pass
    else:
        raise SystemExit("FAIL | 0 allowlist row should exit")
    bad_path.write_text("x.kt | Thread.sleep(1) | reason | abc\n", encoding="utf-8")
    try:
        mod.load_allowlist(str(bad_path))
    except SystemExit:
        pass
    else:
        raise SystemExit("FAIL non-digit allowlist count should exit")
    bad_path.write_text("x.kt | Thread.sleep(1) | reason | 1 | extra\n", encoding="utf-8")
    try:
        mod.load_allowlist(str(bad_path))
    except SystemExit:
        pass
    else:
        raise SystemExit("FAIL extra allowlist field should exit")
    bad_path.write_text("x.kt | Thread.sleep(1) | reason\n", encoding="utf-8")
    try:
        mod.load_allowlist(str(bad_path))
    except SystemExit:
        pass
    else:
        raise SystemExit("FAIL missing allowlist count should exit")
    print("ok  allowlist | N validation rejects bad rows with path:line")

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
    print("ok  Thread import alias fails")

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
    print("ok  SystemClock::sleep method reference fails")

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
    print("ok  runTest on import line does not exempt delay()")

    boundary = delay_dir / "NotRunTestMarkerDelayTest.kt"
    boundary.write_text(
        "class NotRunTestMarkerDelayTest { @Test fun x() = runBlocking { delay(1) } }",
        encoding="utf-8",
    )
    rel_b = str(boundary.relative_to(delay_root))
    raw_b = boundary.read_text()
    stripped_b = strip_comments_and_strings(raw_b + "\nfun notRunTestHook() {}")
    if mod.count_delays(rel_b, raw_b, stripped_b) != 1:
        raise SystemExit("FAIL notRunTest substring must not count as runTest marker")
    print("ok  runTest marker uses word boundaries")

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
    print("ok  .kts files under test roots are scanned")

    print("test_real_time_waits: all assertions passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
