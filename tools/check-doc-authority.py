#!/usr/bin/env python3
"""Authority and drift checks for active Temper documentation.

Current-voice files must use the runtime vocabulary signed in ADR-001.
Relative markdown links in active documents must resolve. The foundation
program must dispose every issued FND ID. Archives are not phrase-checked
and are not rewritten by this tool.

Usage:
    python3 tools/check-doc-authority.py
"""
from __future__ import annotations

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

CURRENT_VOICE = [
    "README.md",
    "SETUP.md",
    "docs/FOUNDATION_PROGRAM.md",
    "docs/DEVELOPMENT.md",
    "docs/RECOVERY.md",
    "docs/UX_PAGE_PASS.md",
    ".cursor/rules/owner-loop.mdc",
]

ARCHITECTURE_DIR = "docs/architecture"
PROGRAM = "docs/FOUNDATION_PROGRAM.md"
DISPOSITIONS = "docs/architecture/ADR-013-finding-dispositions.md"
ROADMAP = "docs/ROADMAP.md"

# Phrases that, in current-voice files, assert obsolete law or stale runtime.
FORBIDDEN = [
    (
        re.compile(r"sync with your own", re.I),
        "Drive is backup, not sync",
    ),
    (
        re.compile(r"or sync with"),
        "Drive is backup, not sync",
    ),
    (
        re.compile(r"this repo,\s*`main`\s*branch"),
        "sitting branch is trunk",
    ),
    (
        re.compile(r"2\.json is uncommitted", re.I),
        "2.json is committed",
    ),
    (
        re.compile(r"schema for version 2 does not exist", re.I),
        "2.json is committed",
    ),
    (
        re.compile(r"7\s*/\s*14\s*/\s*this week", re.I),
        "Body heat windows are This week and Last 30 days",
    ),
    (
        re.compile(r"Room stays at \*\*version 2\*\*\. Never invent a schema v3"),
        "Room v2 freeze is superseded for the Phase 5 cutover",
    ),
    (
        re.compile(r"Job 6 is polish and intuition"),
        "Job 6 is not the current program",
    ),
]

REQUIRED_IN = [
    (PROGRAM, "FND-037", "program must record FND-037"),
    (PROGRAM, "no finding issued", "program must state FND-037 is not a finding"),
    (PROGRAM, "fallbackToDestructiveMigration", "program must keep the migration ban"),
    (PROGRAM, "SCHEDULE_EXACT_ALARM", "program must sign the exact-rest strategy"),
    (PROGRAM, "one live", "program must sign one-live-activity"),
    (DISPOSITIONS, "FND-037 is a numbering gap", "ADR-013 must close FND-037"),
    (ROADMAP, "FOUNDATION_PROGRAM.md", "ROADMAP must point at the current program"),
    (ROADMAP, "superseded", "ROADMAP must mark superseded constraints"),
    (".cursor/rules/owner-loop.mdc", "FOUNDATION_PROGRAM.md", "owner-loop must point at the program"),
    (
        "docs/architecture/backup-threat-model.md",
        "User-controlled backup is the authoritative recovery path",
        "P3.1 inventory must restate the recovery-path decision",
    ),
    (
        "docs/architecture/backup-threat-model.md",
        "allowBackup=false",
        "P3.1 inventory must record the Auto Backup disable decision",
    ),
    (
        "docs/architecture/backup-threat-model.md",
        "Device theft",
        "P3.1 inventory must cover device theft",
    ),
    (
        "docs/architecture/backup-threat-model.md",
        "File leak",
        "P3.1 inventory must cover file leak",
    ),
    (
        "docs/architecture/backup-threat-model.md",
        "drive.file",
        "P3.1 inventory must cover the Drive channel",
    ),
    (
        "docs/architecture/sdk36-compatibility.md",
        "compileSdk = 36, targetSdk = 36, minSdk = 26",
        "P4.1 review must sign the SDK triple",
    ),
    (
        "docs/architecture/sdk36-compatibility.md",
        "Robolectric 4.16",
        "P4.1 review must record the P4.2 Robolectric lift",
    ),
    (
        "docs/architecture/core-toolchain.md",
        "Lifecycle | 2.8.7 | **2.10.0**",
        "P4.2 review must sign the Lifecycle floor",
    ),
    (
        "docs/architecture/core-toolchain.md",
        "Robolectric | 4.14.1 | **4.16**",
        "P4.2 review must sign Robolectric 4.16",
    ),
    (
        "docs/architecture/compose-toolchain.md",
        "Compose BOM | 2024.12.01 | **2026.06.01**",
        "P4.3 review must sign the Compose BOM floor",
    ),
    (
        "docs/architecture/compose-toolchain.md",
        "Refuse Compose BOM 2026.08.00",
        "P4.3 review must record the AGP 9 refusal",
    ),
    (
        "docs/architecture/persistence-toolchain.md",
        "Room | 2.6.1 | **2.7.2**",
        "P4.4 review must sign the Room floor",
    ),
    (
        "docs/architecture/persistence-toolchain.md",
        "Refuse Room 2.8.x",
        "P4.4 review must record the Room 2.8 refusal",
    ),
    (
        "docs/architecture/drive-auth.md",
        "No `com.google.android.gms.auth.api.signin` types",
        "P4.5 review must ban Google Sign-In remnants",
    ),
    (
        "docs/architecture/drive-auth.md",
        "Scope stays `drive.file`",
        "P4.5 review must keep the Drive scope",
    ),
]

ISSUED_FND = [f"FND-{n:03d}" for n in range(1, 49) if n != 37] + [
    "FND-014A",
    "FND-014B",
    "FND-014C",
]
LINK_RE = re.compile(r"!?\[([^\]]*)\]\(([^)]+)\)")
FND_RE = re.compile(r"FND-(\d{3}[A-C]?)")

findings: list[str] = []


def rel(path: str) -> str:
    return os.path.relpath(path, ROOT).replace(os.sep, "/")


def read(path: str) -> str:
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def add(path: str, line: int | None, message: str) -> None:
    loc = f"{rel(path)}:{line}" if line else rel(path)
    findings.append(f"{loc}  {message}")


def current_voice_files() -> list[str]:
    files = [os.path.join(ROOT, p) for p in CURRENT_VOICE]
    arch = os.path.join(ROOT, ARCHITECTURE_DIR)
    if os.path.isdir(arch):
        for name in sorted(os.listdir(arch)):
            if name.endswith(".md"):
                files.append(os.path.join(arch, name))
    return files


def active_markdown() -> list[str]:
    files = []
    for name in ("README.md", "SETUP.md"):
        files.append(os.path.join(ROOT, name))
    docs = os.path.join(ROOT, "docs")
    for dirpath, dirnames, filenames in os.walk(docs):
        dirnames[:] = [
            d
            for d in dirnames
            if d not in {"archive", "artifacts", "ui-redesign", "evidence"}
        ]
        for filename in filenames:
            if filename.endswith(".md"):
                files.append(os.path.join(dirpath, filename))
    return files


def check_forbidden() -> None:
    for path in current_voice_files():
        if not os.path.isfile(path):
            add(path, None, "required current-voice file is missing")
            continue
        for number, line in enumerate(read(path).splitlines(), 1):
            for pattern, why in FORBIDDEN:
                if pattern.search(line):
                    add(path, number, f"forbidden current-voice phrase ({why}): {line.strip()}")


def check_required() -> None:
    for relpath, needle, why in REQUIRED_IN:
        path = os.path.join(ROOT, relpath)
        if not os.path.isfile(path):
            add(path, None, f"missing required file ({why})")
            continue
        if needle not in read(path):
            add(path, None, f"missing required text {needle!r} ({why})")


def check_adrs() -> None:
    readme = os.path.join(ROOT, ARCHITECTURE_DIR, "README.md")
    if not os.path.isfile(readme):
        add(readme, None, "architecture index is missing")
        return
    text = read(readme)
    for match in re.finditer(r"\[[^\]]*\]\((ADR-[^)]+\.md)\)", text):
        target = os.path.join(ROOT, ARCHITECTURE_DIR, match.group(1))
        if not os.path.isfile(target):
            add(readme, None, f"index links to missing ADR {match.group(1)}")


def check_dispositions() -> None:
    path = os.path.join(ROOT, DISPOSITIONS)
    if not os.path.isfile(path):
        add(path, None, "disposition ADR is missing")
        return
    text = read(path)
    found = set(f"FND-{n}" for n in FND_RE.findall(text))
    for fnd in ISSUED_FND:
        if fnd not in found:
            add(path, None, f"{fnd} has no planned disposition")
    if "FND-037" not in found:
        add(path, None, "FND-037 is not recorded")
    elif "no finding" not in text.lower():
        add(path, None, "FND-037 is recorded but not marked as no finding")


def resolve_link(source: str, raw: str) -> str | None:
    target = raw.strip()
    if not target or target.startswith("#"):
        return None
    if re.match(r"^(https?:|mailto:|tel:)", target, re.I):
        return None
    target = target.split()[0].strip("<>")
    target = target.split("#", 1)[0]
    if not target:
        return None
    if target.startswith("/"):
        return os.path.join(ROOT, target.lstrip("/"))
    return os.path.normpath(os.path.join(os.path.dirname(source), target))


def check_links() -> None:
    for path in active_markdown():
        for number, line in enumerate(read(path).splitlines(), 1):
            for match in LINK_RE.finditer(line):
                resolved = resolve_link(path, match.group(2))
                if resolved is None:
                    continue
                if not os.path.exists(resolved):
                    add(
                        path,
                        number,
                        f"broken relative link {match.group(2)!r}",
                    )


def main() -> int:
    os.chdir(ROOT)
    check_forbidden()
    check_required()
    check_adrs()
    check_dispositions()
    check_links()
    for item in findings:
        print(item)
    print(f"\n{len(findings)} authority finding(s)")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
