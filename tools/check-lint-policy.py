#!/usr/bin/env python3
"""Drift check: the P4.6 local lint policy.

warningsAsErrors stays on. The only project-level disabled ids are the
signed waivers. The baseline exists (may be empty).
"""
from __future__ import annotations

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
GRADLE = os.path.join(ROOT, "app/build.gradle.kts")
BASELINE = os.path.join(ROOT, "app/lint-baseline.xml")
POLICY = os.path.join(ROOT, "docs/architecture/lint-policy.md")
LINT_XML = os.path.join(ROOT, "app/lint.xml")
ALLOWED_DISABLE = {
    "AndroidGradlePluginVersion",
    "UseKtx",
    "GradleDependency",
}

findings: list[str] = []


def main() -> int:
    gradle = open(GRADLE, encoding="utf-8").read()
    if "warningsAsErrors = true" not in gradle:
        findings.append("app/build.gradle.kts  warningsAsErrors must stay true")
    if "baseline = file(\"lint-baseline.xml\")" not in gradle:
        findings.append("app/build.gradle.kts  lint baseline must stay wired")

    match = re.search(r"disable\s*\+=\s*setOf\(([^)]*)\)", gradle, re.S)
    if match is None:
        findings.append("app/build.gradle.kts  missing lint disable set")
    else:
        got = set(re.findall(r'"([^"]+)"', match.group(1)))
        extra = got - ALLOWED_DISABLE
        missing = ALLOWED_DISABLE - got
        for item in sorted(extra):
            findings.append(f"app/build.gradle.kts  unsigned lint disable {item}")
        for item in sorted(missing):
            findings.append(f"app/build.gradle.kts  missing signed waiver {item}")

    if not os.path.isfile(BASELINE):
        findings.append("app/lint-baseline.xml  missing")
    else:
        body = open(BASELINE, encoding="utf-8").read()
        leftover = set(re.findall(r'<issue\s+id="([^"]+)"', body))
        if leftover:
            findings.append(
                "app/lint-baseline.xml  must be empty after P4.6 "
                f"(found {', '.join(sorted(leftover))})",
            )

    if not os.path.isfile(LINT_XML):
        findings.append("app/lint.xml  missing adaptive-icon ObsoleteSdkInt ignore")
    else:
        lint_xml = open(LINT_XML, encoding="utf-8").read()
        if "mipmap-anydpi-v26" not in lint_xml or "ObsoleteSdkInt" not in lint_xml:
            findings.append("app/lint.xml  must ignore ObsoleteSdkInt on mipmap-anydpi-v26")

    if not os.path.isfile(POLICY):
        findings.append("docs/architecture/lint-policy.md  missing")
    else:
        policy = open(POLICY, encoding="utf-8").read()
        for needle in ALLOWED_DISABLE | {"warningsAsErrors = true", "verification-metadata.xml"}:
            if needle not in policy:
                findings.append(f"lint-policy.md  missing {needle}")

    print(f"{len(findings)} lint-policy finding(s)")
    for item in findings:
        print(item)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
