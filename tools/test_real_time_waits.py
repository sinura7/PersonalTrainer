#!/usr/bin/env python3
"""Fixture proof for check-real-time-waits.py."""
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
from kotlin_source import strip_comments_and_strings  # noqa: E402


def load(filename: str):
    path = TOOLS / filename
    spec = importlib.util.spec_from_file_location(path.stem.replace("-", "_"), path)
    if spec is None or spec.loader is None:
        raise SystemExit(f"cannot load {path}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def write_allowlist(root: Path, rows: list[tuple[str, str, str]]) -> Path:
    path = root / "real-time-waits-allowlist.txt"
    lines = [f"{p} | {code} | {reason}" for p, code, reason in rows]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def run_checker(
    mod,
    allowlist: Path,
    test_root: Path,
    monkeypatch_allowlist: bool = True,
) -> tuple[int, str, str]:
    if monkeypatch_allowlist:
        mod.ALLOWLIST_PATH = str(allowlist)
    buf_out, buf_err = io.StringIO(), io.StringIO()
    argv = ["check-real-time-waits.py", str(test_root)]
    with redirect_stdout(buf_out):
        code = mod.main(argv)
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
    if code == 0 or "real-time sleep not in allowlist" not in out:
        raise SystemExit(f"FAIL unlisted sleep should fail: code={code} out={out}")
    print("ok  unlisted Thread.sleep fails with path and line")

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
    _ln, line_text = mod.find_sleeps(rel_listed, stripped_listed)[0]
    allowlist = write_allowlist(
        listed_root,
        [(rel_listed, line_text, "fixture")],
    )
    mod.REPO_ROOT = str(listed_root.resolve())
    mod.ALLOWLIST_PATH = str(allowlist)
    code, out, _err = run_checker(mod, allowlist, listed_root / "test", False)
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
        _line_no, code_text = hits[0]
        allowlist = write_allowlist(snippet_root, [(rel_p, code_text, "x")])
        mod.ALLOWLIST_PATH = str(allowlist)
        if run_checker(mod, allowlist, snippet_root / "test", False)[0] != 0:
            raise SystemExit(f"FAIL {label} allowlisted run")
    print("ok  SystemClock and TimeUnit sleep forms are caught")

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
            ),
        ],
    )
    mod.ALLOWLIST_PATH = str(allowlist)
    _code, out, _err = run_checker(mod, allowlist, stale_root / "test", False)
    if "hint: allowlist entry has no matching sleep" not in out:
        raise SystemExit(f"FAIL stale allowlist hint missing: {out}")
    print("ok  stale allowlist entry emits ratchet hint")

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
    _ln, line_text = mod.find_sleeps(rel_m, stripped_before)[0]
    allowlist = write_allowlist(move_root, [(rel_m, line_text, "moved")])
    mod.ALLOWLIST_PATH = str(allowlist)
    moved.write_text(
        "class MovedLineTest {\n\n    @Test fun x() { Thread.sleep(7) }\n}\n",
        encoding="utf-8",
    )
    code, out, _err = run_checker(mod, allowlist, move_root / "test", False)
    if code != 0:
        raise SystemExit(f"FAIL allowlist matches by code text after line move: {out}")
    print("ok  allowlist matches by code text when line number shifts")

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
    print("ok  ceiling equal, growth fails, drop hints")

    print("test_real_time_waits: all assertions passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
