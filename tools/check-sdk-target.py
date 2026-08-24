#!/usr/bin/env python3
"""Drift check: SDK 36 plus the P4.2 core-family floors.

P4.1 signed the SDK triple. P4.2 ratchets Core KTX, Lifecycle, Activity,
coroutines, serialization, Robolectric, and AndroidX Test. Compose, Room,
and Sign-In stay for later packets.
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
TOOLCHAIN = os.path.join(ROOT, "app/src/main/java/com/sinura/personaltrainer/toolchain/CoreToolchain.kt")

REQUIRED = {
    "compileSdk": 36,
    "targetSdk": 36,
    "minSdk": 26,
}
MIN_AGP = (8, 9, 1)
MIN_GRADLE = (8, 11, 1)
CATALOG_MIN = {
    "coreKtx": ((1, 17, 0), "1.17.0"),
    "lifecycleRuntimeKtx": ((2, 10, 0), "2.10.0"),
    "activityCompose": ((1, 12, 4), "1.12.4"),
    "coroutines": ((1, 10, 2), "1.10.2"),
    "serialization": ((1, 8, 1), "1.8.1"),
    "robolectric": ((4, 16, 0), "4.16"),
    "androidxTestCore": ((1, 7, 0), "1.7.0"),
    "androidxTestRunner": ((1, 7, 0), "1.7.0"),
    "androidxTestRules": ((1, 7, 0), "1.7.0"),
    "androidxTestExtJunit": ((1, 3, 0), "1.3.0"),
}

findings: list[str] = []


def parse_semver(raw: str) -> tuple[int, ...]:
    parts = re.findall(r"\d+", raw)
    return tuple(int(part) for part in parts[:3]) + (0,) * max(0, 3 - len(parts))


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

    for key, (minimum, label) in CATALOG_MIN.items():
        match = re.search(rf'^{key}\s*=\s*"([^"]+)"', catalog, re.M)
        if match is None:
            findings.append(f"gradle/libs.versions.toml  missing {key}")
            continue
        got = parse_semver(match.group(1))
        if got < minimum:
            findings.append(
                f"gradle/libs.versions.toml  {key} must be >= {label} (found {match.group(1)})",
            )

    if "kotlin-serialization" not in catalog:
        findings.append("gradle/libs.versions.toml  missing kotlin-serialization plugin")
    if "kotlinx-serialization-json" not in catalog:
        findings.append("gradle/libs.versions.toml  missing kotlinx-serialization-json")

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
        findings.append("robolectric.properties  missing; JVM lane must pin sdk=36")
    else:
        props = open(ROBOLECTRIC, encoding="utf-8").read()
        if not re.search(r"(?m)^sdk=36\s*$", props):
            findings.append("robolectric.properties  must pin sdk=36 after Robolectric 4.16")

    if not os.path.isfile(TOOLCHAIN):
        findings.append("CoreToolchain.kt  missing signed P4.2 matrix")
    else:
        body = open(TOOLCHAIN, encoding="utf-8").read()
        for needle in (
            'coreKtx = "1.17.0"',
            'lifecycle = "2.10.0"',
            'activity = "1.12.4"',
            'coroutines = "1.10.2"',
            'serialization = "1.8.1"',
            'robolectric = "4.16"',
        ):
            if needle not in body:
                findings.append(f"CoreToolchain.kt  missing {needle}")

    print(f"{len(findings)} sdk-target finding(s)")
    for item in findings:
        print(item)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
