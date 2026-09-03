"""Gym-floor versionCode floor.

Until the first `v*` tag exists, the floor is 1.
`tools/released-version-code.txt` is the fallback when git has no gym-floor
tags (or git is unavailable). After a `v*` tag, that tag's `appVersionCode`
in git is the floor — the file is not source of truth.

`debug-live-*` tags are not gym-floor releases and must not move the floor.

Rules, one place, used by check-version-code.py, check-play-rehearsal.py,
and `.github/workflows/release.yml`:

- Everyday / preflight: current >= floor.
- Tag release (`tag_release=True`): the first `v*` may equal 1; a later
  `v*` must be strictly greater than the previous `v*` tag's code. Pass
  `exclude_tag` so a tag push does not compare against itself.
"""
from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

CODE = re.compile(r"^val appVersionCode = (\d+)\s*$", re.M)
V_TAG = re.compile(r"^v\d")
FALLBACK_FILE = Path("tools") / "released-version-code.txt"
GRADLE_REL = Path("app") / "build.gradle.kts"


def parse_app_version_code(text: str) -> int | None:
    match = CODE.search(text)
    if match is None:
        return None
    return int(match.group(1))


def read_file_floor(repo: Path) -> int | None:
    path = repo / FALLBACK_FILE
    if not path.is_file():
        return None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        try:
            return int(line)
        except ValueError:
            return None
    return None


def _git(repo: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True,
        text=True,
        check=False,
    )


def list_v_tags(repo: Path) -> list[str]:
    result = _git(repo, "tag", "-l", "v*")
    if result.returncode != 0:
        return []
    return [line.strip() for line in result.stdout.splitlines() if line.strip() and V_TAG.match(line.strip())]


def version_code_at_tag(repo: Path, tag: str) -> int | None:
    result = _git(repo, "show", f"{tag}:{GRADLE_REL.as_posix()}")
    if result.returncode != 0:
        return None
    return parse_app_version_code(result.stdout)


@dataclass(frozen=True)
class PreviousRelease:
    code: int
    tag: str


@dataclass(frozen=True)
class PreviousScan:
    release: PreviousRelease | None
    tags_seen: tuple[str, ...]
    unreadable: tuple[str, ...]


def scan_previous(repo: Path, exclude_tag: str | None = None) -> PreviousScan:
    tags = tuple(
        tag for tag in list_v_tags(repo)
        if exclude_tag is None or tag != exclude_tag
    )
    best: PreviousRelease | None = None
    unreadable: list[str] = []
    for tag in tags:
        code = version_code_at_tag(repo, tag)
        if code is None:
            unreadable.append(tag)
            continue
        if best is None or code > best.code:
            best = PreviousRelease(code=code, tag=tag)
    return PreviousScan(release=best, tags_seen=tags, unreadable=tuple(unreadable))


def previous_release(repo: Path, exclude_tag: str | None = None) -> PreviousRelease | None:
    return scan_previous(repo, exclude_tag=exclude_tag).release


@dataclass(frozen=True)
class Floor:
    code: int
    source: str


def resolve_floor(repo: Path, exclude_tag: str | None = None) -> Floor | None:
    scan = scan_previous(repo, exclude_tag=exclude_tag)
    if scan.release is not None:
        return Floor(code=scan.release.code, source=f"git tag {scan.release.tag}")
    if scan.tags_seen:
        return None
    file_floor = read_file_floor(repo)
    if file_floor is not None:
        return Floor(code=file_floor, source=str(FALLBACK_FILE))
    return None


@dataclass(frozen=True)
class CheckResult:
    ok: bool
    message: str
    current: int | None
    floor: Floor | None


def evaluate(
    repo: Path,
    *,
    tag_release: bool = False,
    exclude_tag: str | None = None,
) -> CheckResult:
    gradle = repo / GRADLE_REL
    if not gradle.is_file():
        return CheckResult(False, "check-version-code: app/build.gradle.kts not found", None, None)
    current = parse_app_version_code(gradle.read_text(encoding="utf-8"))
    if current is None:
        return CheckResult(
            False,
            "check-version-code: appVersionCode not found in app/build.gradle.kts",
            None,
            None,
        )

    scan = scan_previous(repo, exclude_tag=exclude_tag)
    if scan.tags_seen and scan.release is None:
        names = ", ".join(scan.unreadable) or "(none)"
        return CheckResult(
            False,
            "check-version-code: v* tags exist but appVersionCode could not be "
            f"read from: {names}",
            current,
            None,
        )

    floor = resolve_floor(repo, exclude_tag=exclude_tag)

    if tag_release:
        if scan.release is None:
            # First gym-floor tag: may equal 1.
            if current < 1:
                return CheckResult(
                    False,
                    f"check-version-code: first v* tag has versionCode {current}; minimum is 1",
                    current,
                    floor,
                )
            return CheckResult(
                True,
                f"check-version-code: versionCode {current} is the first v* "
                "(no previous gym-floor release)",
                current,
                floor,
            )
        if current <= scan.release.code:
            return CheckResult(
                False,
                f"check-version-code: versionCode {current} is not above previous "
                f"v* {scan.release.tag} (code {scan.release.code}). Bump appVersionCode.",
                current,
                floor,
            )
        return CheckResult(
            True,
            f"check-version-code: versionCode {current} > previous "
            f"{scan.release.tag} (code {scan.release.code})",
            current,
            floor,
        )

    if floor is None:
        return CheckResult(
            False,
            "check-version-code: no v* tags and tools/released-version-code.txt is missing",
            current,
            None,
        )
    if current < floor.code:
        return CheckResult(
            False,
            f"check-version-code: versionCode {current} is below released floor "
            f"{floor.code} ({floor.source})",
            current,
            floor,
        )
    return CheckResult(
        True,
        f"check-version-code: versionCode {current} >= released {floor.code} "
        f"({floor.source})",
        current,
        floor,
    )
