#!/usr/bin/env python3
"""The gym-floor release lane cannot rot where nobody looks (audit X6, packet X7).

Checks the three workflows under .github/workflows/ and the push gate's wiring in
app/build.gradle.kts against the rules in tools/release_lane.py:

- every android-actions/setup-android step passes `packages: ''` (BR-2);
- no `gh release upload` / `gh release create` has its failure swallowed by `||` (BR-7);
- no `${{ … }}` expression inside a `run:` script (BR-7);
- `assembleDebug` depends on `assembleRelease`, so the push gate builds and shrinks the
  release variant (BR-1: the release had not been built since a library change broke it),
  and gradle.properties does not set `skipStaticChecks`, which would turn that off.

The fixture proof is tools/test_release_lane.py.
Run: python3 tools/check-release-lane.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from release_lane import gate_builds_release, properties_skip_the_gate, workflow_findings  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
GRADLE = ROOT / "app" / "build.gradle.kts"
PROPERTIES = ROOT / "gradle.properties"


def main() -> int:
    findings: list[str] = []
    workflows = sorted(WORKFLOWS.glob("*.yml")) + sorted(WORKFLOWS.glob("*.yaml"))
    if not workflows:
        findings.append(f"{WORKFLOWS.relative_to(ROOT)}: no workflows found")
    for path in workflows:
        text = path.read_text(encoding="utf-8")
        for finding in workflow_findings(text):
            findings.append(f"{path.relative_to(ROOT)}:{finding.line}: {finding.message}")
    gradle = GRADLE.read_text(encoding="utf-8") if GRADLE.is_file() else ""
    if not gate_builds_release(gradle):
        findings.append(
            "app/build.gradle.kts: assembleDebug must depend on assembleRelease, so the push gate "
            "builds and shrinks the release variant (audit X6, BR-1)",
        )

    properties = PROPERTIES.read_text(encoding="utf-8") if PROPERTIES.is_file() else ""
    if properties_skip_the_gate(properties):
        findings.append(
            "gradle.properties: sets skipStaticChecks, which turns off the ratchets and the "
            "release build for every build, CI's included; pass -PskipStaticChecks by hand instead",
        )

    if findings:
        print(f"{len(findings)} release-lane finding(s)")
        for item in findings:
            print(item)
        return 1
    print(f"check-release-lane: {len(workflows)} workflow(s) and the gate wiring OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
