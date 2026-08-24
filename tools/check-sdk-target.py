#!/usr/bin/env python3
"""Drift check: compile and target stay on API 36, minSdk stays 26.

P4.1. Fail if app/build.gradle.kts quietly drops back to 35.
"""
from __future__ import annotations

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
GRADLE = os.path.join(ROOT, "app/build.gradle.kts")
CATALOG = os.path.join(ROOT, "gradle/libs.versions.toml")
WRAPPER = os.path.join(ROOT, "gradle/wrapper/gradle-wrapper.properties")
ROBOLECTRIC = os.path.join(ROOT, "app/src/test/resources/robolectric.properties")

REQUIRED = {
    "compileSdk": 36,
    "targetSdk": 36,
    "minSdk": 26,
}
MIN_AGP = (8, 9, 1)
MIN_GRADLE = (8, 11, 1)

findings: list[str] = []


def main() -> int:
    text = open(GRADLE, encoding="utf-8").read()
    for name, want in REQUIRED.items():
        match = re.search(rf"{name}\s*=\s*(\d+)", text)
        if match is None:
            findings.append(f"app/build.gradle.kts  missing {name}")
            continue
        got = int(match.group(1))
        if got != want:
            findings.append(
                f"app/build.gradle.kts  {name} must be {want} (found {got})",
            )

    catalog = open(CATALOG, encoding="utf-8").read()
    agp = re.search(r'^agp\s*=\s*"(\d+)\.(\d+)\.(\d+)"', catalog, re.M)
    if agp is None:
        findings.append("gradle/libs.versions.toml  missing agp version")
    else:
        got = tuple(int(part) for part in agp.groups())
        if got < MIN_AGP:
            findings.append(
                f"gradle/libs.versions.toml  agp must be >= {'.'.join(map(str, MIN_AGP))} (found {'.'.join(map(str, got))})",
            )

    wrapper = open(WRAPPER, encoding="utf-8").read()
    gradle = re.search(r"gradle-(\d+)\.(\d+)\.(\d+)-bin\.zip", wrapper)
    if gradle is None:
        findings.append("gradle-wrapper.properties  missing Gradle distribution")
    else:
        got = tuple(int(part) for part in gradle.groups())
        if got < MIN_GRADLE:
            findings.append(
                f"gradle-wrapper.properties  Gradle must be >= {'.'.join(map(str, MIN_GRADLE))} (found {'.'.join(map(str, got))})",
            )

    if not os.path.isfile(ROBOLECTRIC):
        findings.append("robolectric.properties  missing; JVM lane must pin sdk=35")
    else:
        props = open(ROBOLECTRIC, encoding="utf-8").read()
        if not re.search(r"(?m)^sdk=35\s*$", props):
            findings.append(
                "robolectric.properties  must pin sdk=35 until P4.2 ships a runner with API 36",
            )
    print(f"{len(findings)} sdk-target finding(s)")
    for item in findings:
        print(item)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
