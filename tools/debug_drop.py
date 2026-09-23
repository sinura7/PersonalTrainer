"""Temper Debug drop rules: the phone's number, and the drop's name.

A drop is the pre-release Obtainium offers. Two things identify it, and until
this module existed a person typed both:

- `debugLiveCode` in `app/build.gradle.kts`, which Obtainium compares. If it
  does not rise, the phone is never offered the build.
- the drop suffix, `YYYY-MM-DD` or `YYYY-MM-DD-N`, which names the tag and the
  release.

Both were reserved in a pull request and spent minutes later, and on 10 Sep
2026 two packets merging within nine minutes of each other took the same
number and the same suffix. The second drop published nothing: the release
already existed, the upload found the asset name taken, and the run went green
having thrown its APK away.

The rules here are the ones a person cannot lose a race to:

- **The code ratchets against what has actually shipped.** Every
  `debug-live-*` tag names a commit, and that commit's `debugLiveCode` is what
  that drop carried. The working tree's code must be strictly above every one
  of them. Nothing else is a drop, so nothing else moves the floor.
- **The suffix is the first free one for the day.** Free means origin holds
  neither the tag `debug-live-<suffix>` nor the branch `debug-live/<suffix>`.
  The branch is what the push that cuts a drop creates; the tag is what the
  workflow claims once the build succeeds. A drop that failed in between
  leaves the branch and no tag. On 22 Sep 2026 a planner that asked only
  about tags named `-2` again, a name an orphan branch from failed run 113
  already held; the push was refused as non-fast-forward and `-4` was picked
  by hand. Both are asked of origin itself, because that is where the push
  and the claim land, and a clone's own copy of either can be stale.

This is a drop-time rule, not a preflight one, and deliberately so. The moment
a drop of code N is cut, a tag carries N, and a tree still at N would fail —
turning every unrelated pull request red until someone bumped a number they had
no reason to touch. `.github/workflows/debug-live.yml` runs the ratchet where it
belongs: on the way to the phone. `tools/test_debug_drop.py` proves the rule on
every commit without gating on what has shipped.

Fail closed. Where git cannot answer — no repository, no origin to ask, an
unreadable tag, a build file with no `debugLiveCode` — these return a refusal
rather than a pass, because "I could not tell" and "it is fine" are not the
same sentence and only one of them should let an APK reach the phone.
"""
from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

DEBUG_CODE = re.compile(r"^val debugLiveCode = (\d+)\s*$", re.M)
DROP_TAG = re.compile(r"^debug-live-(\d{4}-\d{2}-\d{2})(?:-(\d+))?$")
#: The tag-less spelling: pushing this branch is how a drop is cut.
DROP_BRANCH = re.compile(r"^debug-live/(\d{4}-\d{2}-\d{2})(?:-(\d+))?$")
GRADLE_REL = Path("app") / "build.gradle.kts"

#: How many same-day suffixes to walk before giving up. A day that has already
#: taken forty drops is not a day that wants a forty-first without a person
#: looking at it.
SUFFIX_LIMIT = 40


@dataclass(frozen=True)
class DropCheck:
    ok: bool
    message: str
    code: int | None = None


@dataclass(frozen=True)
class DropPlan:
    ok: bool
    message: str
    suffix: str | None = None
    tag: str | None = None


def parse_debug_live_code(text: str) -> int | None:
    match = DEBUG_CODE.search(text)
    if match is None:
        return None
    return int(match.group(1))


def _git(repo: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True,
        text=True,
        check=False,
    )


def list_drop_tags(repo: Path) -> list[str] | None:
    """Every `debug-live-*` tag, or None when git could not be asked at all."""
    result = _git(repo, "tag", "-l", "debug-live-*")
    if result.returncode != 0:
        return None
    return [
        line.strip()
        for line in result.stdout.splitlines()
        if line.strip() and DROP_TAG.match(line.strip())
    ]


#: What a drop carried before `debugLiveCode` existed. The first two drops were
#: built straight from `appVersionCode`, which was — and still is — 1.
PRE_CONSTANT_CODE = 1


def code_at_tag(repo: Path, tag: str) -> int | None:
    """What Obtainium saw for that drop, or None if the build file is unreadable.

    A tag from before the constant existed is not a hole: `debug-live-2026-08-27`
    and `-08-28` were stamped from `appVersionCode`, so they shipped 1. Reading
    them as [PRE_CONSTANT_CODE] keeps the floor honest without pretending the
    history is tidier than it is.
    """
    result = _git(repo, "show", f"{tag}:{GRADLE_REL.as_posix()}")
    if result.returncode != 0:
        return None
    code = parse_debug_live_code(result.stdout)
    return PRE_CONSTANT_CODE if code is None else code


def current_code(repo: Path) -> int | None:
    path = repo / GRADLE_REL
    if not path.is_file():
        return None
    return parse_debug_live_code(path.read_text(encoding="utf-8"))


def check_code(repo: Path) -> DropCheck:
    """The working tree's drop code must be above every drop already shipped.

    A repository with no drop tags is not a violation — it is a repository that
    has never dropped, or a shallow clone that did not fetch tags. It says so
    rather than passing quietly, because those two are worth telling apart when
    this check is the reason a drop was allowed.
    """
    code = current_code(repo)
    if code is None:
        return DropCheck(
            ok=False,
            message=f"debug-drop: no `val debugLiveCode` in {GRADLE_REL.as_posix()}",
        )
    tags = list_drop_tags(repo)
    if tags is None:
        return DropCheck(
            ok=False,
            message="debug-drop: git could not list tags; cannot tell what has shipped",
            code=code,
        )
    shipped: list[tuple[str, int]] = []
    for tag in tags:
        at = code_at_tag(repo, tag)
        if at is None:
            # A tag whose build file cannot be read at all is a hole in the floor,
            # and a hole in the floor is not a pass.
            return DropCheck(
                ok=False,
                message=f"debug-drop: cannot read {GRADLE_REL.as_posix()} at {tag}",
                code=code,
            )
        shipped.append((tag, at))
    if not shipped:
        return DropCheck(
            ok=True,
            message=f"debug-drop: {code} (no debug-live-* tags to compare against)",
            code=code,
        )
    highest_tag, highest = max(shipped, key=lambda pair: pair[1])
    if code > highest:
        return DropCheck(
            ok=True,
            message=f"debug-drop: {code} is above {highest} ({highest_tag})",
            code=code,
        )
    return DropCheck(
        ok=False,
        message=(
            f"debug-drop: debugLiveCode {code} is not above {highest}, which "
            f"{highest_tag} already shipped. Obtainium offers an update only when "
            f"the number rises — use {highest + 1}."
        ),
        code=code,
    )


def drop_suffix(name: str) -> str | None:
    """The suffix a drop tag (`debug-live-<s>`) or drop branch (`debug-live/<s>`) spends."""
    if DROP_TAG.match(name):
        return name[len("debug-live-"):]
    if DROP_BRANCH.match(name):
        return name[len("debug-live/"):]
    return None


def list_origin_drop_refs(repo: Path) -> list[str] | None:
    """Every drop tag and drop branch on origin, or None when origin could not be asked.

    Tags come back as `debug-live-<suffix>` and branches as `debug-live/<suffix>`.
    Each spelling counts only in its own namespace: a branch called
    `debug-live-<suffix>` is neither what the push creates nor what the workflow
    claims, so it spends nothing.

    `ls-remote` rather than this clone's refs: a remote-tracking branch is only as
    fresh as the last fetch, and nothing prunes the ones origin has dropped.
    """
    result = _git(
        repo,
        "ls-remote",
        "origin",
        "refs/heads/debug-live/*",
        "refs/tags/debug-live-*",
    )
    if result.returncode != 0:
        return None
    names: set[str] = set()
    for line in result.stdout.splitlines():
        ref = line.partition("\t")[2].strip()
        for namespace, pattern in (("refs/tags/", DROP_TAG), ("refs/heads/", DROP_BRANCH)):
            name = ref[len(namespace):]
            if ref.startswith(namespace) and pattern.match(name):
                names.add(name)
    return sorted(names)


def next_free_suffix(
    names: list[str],
    today: str,
    limit: int = SUFFIX_LIMIT,
) -> str | None:
    """The first `YYYY-MM-DD[-N]` for [today] that no drop tag or drop branch has taken."""
    taken = {suffix for suffix in map(drop_suffix, names) if suffix is not None}
    for index in range(1, limit + 1):
        suffix = today if index == 1 else f"{today}-{index}"
        if suffix not in taken:
            return suffix
    return None


def plan_drop(repo: Path, today: str, limit: int = SUFFIX_LIMIT) -> DropPlan:
    """Name the drop that is free to cut today, refusing when origin cannot say."""
    if not DROP_TAG.match(f"debug-live-{today}"):
        return DropPlan(ok=False, message=f"debug-drop: {today} is not YYYY-MM-DD")
    names = list_origin_drop_refs(repo)
    if names is None:
        return DropPlan(
            ok=False,
            message=(
                "debug-drop: could not ask origin for its debug-live tags and "
                "branches; cannot tell which suffix is free"
            ),
        )
    suffix = next_free_suffix(names, today, limit=limit)
    if suffix is None:
        return DropPlan(
            ok=False,
            message=f"debug-drop: {today} has no free suffix inside {limit}",
        )
    return DropPlan(
        ok=True,
        message=f"debug-drop: debug-live/{suffix} is free",
        suffix=suffix,
        tag=f"debug-live-{suffix}",
    )
