#!/usr/bin/env python3
"""Drift check: SDK 36 plus the P4.2/P4.3 family floors.

P4.1 signed the SDK triple. P4.2 ratchets Core KTX, Lifecycle, Activity,
coroutines, Robolectric, and AndroidX Test. The serialization *ceiling*
(1.8.1) still blocks Room 2.8; the artifact is not shipped. P4.3 ratchets
Compose BOM, Navigation, and the Kotlin Compose compiler pin. P4.4
ratchets Room and DataStore. P4.5 ratchets play-services-auth and
forbids Google Sign-In remnants.
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
CORE_TOOLCHAIN = os.path.join(ROOT, "app/src/main/java/com/sinura/personaltrainer/toolchain/CoreToolchain.kt")
COMPOSE_TOOLCHAIN = os.path.join(ROOT, "app/src/main/java/com/sinura/personaltrainer/toolchain/ComposeToolchain.kt")
PERSISTENCE_TOOLCHAIN = os.path.join(
    ROOT,
    "app/src/main/java/com/sinura/personaltrainer/toolchain/PersistenceToolchain.kt",
)
SCHEMA_V1 = os.path.join(
    ROOT,
    "app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/1.json",
)
SCHEMA_V2 = os.path.join(
    ROOT,
    "app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/2.json",
)
DRIVE_AUTH = os.path.join(
    ROOT,
    "app/src/main/java/com/sinura/personaltrainer/data/backup/DriveAuthClient.kt",
)

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
    "robolectric": ((4, 16, 0), "4.16"),
    "androidxTestCore": ((1, 7, 0), "1.7.0"),
    "androidxTestRunner": ((1, 7, 0), "1.7.0"),
    "androidxTestRules": ((1, 7, 0), "1.7.0"),
    "androidxTestExtJunit": ((1, 3, 0), "1.3.0"),
    "composeBom": ((2026, 6, 1), "2026.06.01"),
    "navigationCompose": ((2, 9, 8), "2.9.8"),
    "kotlin": ((2, 0, 21), "2.0.21"),
    "room": ((2, 7, 2), "2.7.2"),
    "datastore": ((1, 2, 1), "1.2.1"),
    "playServicesAuth": ((21, 6, 0), "21.6.0"),
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

    if "kotlin-serialization" in catalog:
        findings.append("gradle/libs.versions.toml  still ships kotlin-serialization")
    if "kotlinx-serialization-json" in catalog:
        findings.append("gradle/libs.versions.toml  still ships kotlinx-serialization-json")
    if "material-icons-extended" in catalog:
        findings.append("gradle/libs.versions.toml  still ships material-icons-extended")

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

    if not os.path.isfile(CORE_TOOLCHAIN):
        findings.append("CoreToolchain.kt  missing signed P4.2 matrix")
    else:
        body = open(CORE_TOOLCHAIN, encoding="utf-8").read()
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

    if not os.path.isfile(COMPOSE_TOOLCHAIN):
        findings.append("ComposeToolchain.kt  missing signed P4.3 matrix")
    else:
        body = open(COMPOSE_TOOLCHAIN, encoding="utf-8").read()
        for needle in (
            'composeBom = "2026.06.01"',
            'navigation = "2.9.8"',
            'compiler = "2.0.21"',
            'composeUi = "1.11.4"',
            'material3 = "1.4.0"',
        ):
            if needle not in body:
                findings.append(f"ComposeToolchain.kt  missing {needle}")

    if not os.path.isfile(PERSISTENCE_TOOLCHAIN):
        findings.append("PersistenceToolchain.kt  missing signed P4.4 matrix")
    else:
        body = open(PERSISTENCE_TOOLCHAIN, encoding="utf-8").read()
        for needle in (
            'room = "2.7.2"',
            'datastore = "1.2.1"',
            'schemaV1 = "6d58ad40d5c03785ab29aaf61157f369"',
            'schemaV2 = "3eedd5301f0344b7802f5d0da2f68b3e"',
        ):
            if needle not in body:
                findings.append(f"PersistenceToolchain.kt  missing {needle}")

    for path, expected in (
        (SCHEMA_V1, "6d58ad40d5c03785ab29aaf61157f369"),
        (SCHEMA_V2, "3eedd5301f0344b7802f5d0da2f68b3e"),
    ):
        if not os.path.isfile(path):
            findings.append(f"{os.path.relpath(path, ROOT)}  missing committed schema")
            continue
        body = open(path, encoding="utf-8").read()
        if f'"identityHash": "{expected}"' not in body and f'"identityHash":"{expected}"' not in body:
            findings.append(
                f"{os.path.relpath(path, ROOT)}  identityHash must stay {expected}",
            )

    if not os.path.isfile(DRIVE_AUTH):
        findings.append("DriveAuthClient.kt  missing")
    else:
        body = open(DRIVE_AUTH, encoding="utf-8").read()
        for banned in (
            "com.google.android.gms.auth.api.signin",
            "GoogleSignIn",
            "toGoogleSignInAccount",
            "getSignInClient",
        ):
            if banned in body:
                findings.append(f"DriveAuthClient.kt  still uses {banned}")
        for needle in ("getAuthorizationClient", "drive.file", "clearToken", "revokeAccess"):
            if needle not in body:
                findings.append(f"DriveAuthClient.kt  missing {needle}")

    time_port = os.path.join(
        ROOT,
        "app/src/main/java/com/sinura/personaltrainer/domain/TimePort.kt",
    )
    if not os.path.isfile(time_port):
        findings.append("TimePort.kt  missing P5.1 time seam")
    else:
        body = open(time_port, encoding="utf-8").read()
        for needle in (
            "CapturedCivilTime",
            "DstOverlapChoice",
            "DstGapPolicy",
            "fun interface IdPort",
        ):
            if needle not in body:
                findings.append(f"TimePort.kt  missing {needle}")

    activity_session = os.path.join(
        ROOT,
        "app/src/main/java/com/sinura/personaltrainer/domain/ActivitySession.kt",
    )
    if not os.path.isfile(activity_session):
        findings.append("ActivitySession.kt  missing P5.2 activity envelope")
    else:
        body = open(activity_session, encoding="utf-8").read()
        for needle in (
            "data class ActivitySession",
            "enum class ActivityOrigin",
            "data class CardioBlock",
            "data class StrengthSet",
        ):
            if needle not in body:
                findings.append(f"ActivitySession.kt  missing {needle}")

    temper_db = os.path.join(
        ROOT,
        "app/src/main/java/com/sinura/personaltrainer/data/local/TemperDatabase.kt",
    )
    if not os.path.isfile(temper_db):
        findings.append("TemperDatabase.kt  missing P5.3 foundation database")
    else:
        body = open(temper_db, encoding="utf-8").read()
        if ".fallbackToDestructiveMigration" in body:
            findings.append("TemperDatabase.kt  uses fallbackToDestructiveMigration")
        for needle in ("ActivitySessionEntity", "FoundationGeneration.VERSION"):
            if needle not in body:
                findings.append(f"TemperDatabase.kt  missing {needle}")

    generation = os.path.join(
        ROOT,
        "app/src/main/java/com/sinura/personaltrainer/data/local/FoundationGeneration.kt",
    )
    if not os.path.isfile(generation):
        findings.append("FoundationGeneration.kt  missing P5.7 freeze marker")
    else:
        body = open(generation, encoding="utf-8").read()
        if "const val FROZEN = true" not in body:
            findings.append("FoundationGeneration.kt  FROZEN must stay true after P5.7")

    print(f"{len(findings)} sdk-target finding(s)")
    for item in findings:
        print(item)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
