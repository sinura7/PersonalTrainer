#!/usr/bin/env python3
"""P12.4 automated Play rehearsal.

Static release facts that can be checked in this repo. Physical TalkBack,
Android Public Candidate, and Play upload stay blocked even when this
script exits 0 — those gates need a phone and a human, not a green CI.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
GRADLE = ROOT / "app" / "build.gradle.kts"
ENVELOPE_TEST = ROOT / "app" / "src" / "test" / "java" / "com" / "sinura" / "personaltrainer" / "data" / "backup" / "BackupEnvelopeTest.kt"
MATRIX = ROOT / "app" / "src" / "main" / "java" / "com" / "sinura" / "personaltrainer" / "domain" / "AccessibilityMatrix.kt"
FLOOR = ROOT / "tools" / "released-version-code.txt"
DOCS = (
    ROOT / "docs" / "PRIVACY.md",
    ROOT / "docs" / "DATA_SAFETY.md",
    ROOT / "docs" / "SUPPORT.md",
    ROOT / "docs" / "COMMERCIAL_BOUNDARY.md",
)


def main() -> int:
    findings: list[str] = []
    manifest = MANIFEST.read_text(encoding="utf-8") if MANIFEST.is_file() else ""
    gradle = GRADLE.read_text(encoding="utf-8") if GRADLE.is_file() else ""
    envelope = ENVELOPE_TEST.read_text(encoding="utf-8") if ENVELOPE_TEST.is_file() else ""
    matrix = MATRIX.read_text(encoding="utf-8") if MATRIX.is_file() else ""

    if 'android:allowBackup="false"' not in manifest:
        findings.append("AndroidManifest.xml: allowBackup must be false")
    if "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" not in manifest:
        findings.append("AndroidManifest.xml: missing FOREGROUND_SERVICE_SPECIAL_USE")
    if "android.permission.SCHEDULE_EXACT_ALARM" not in manifest:
        findings.append("AndroidManifest.xml: missing SCHEDULE_EXACT_ALARM")
    if "android.permission.USE_EXACT_ALARM" in manifest:
        findings.append("AndroidManifest.xml: USE_EXACT_ALARM is banned (ADR-012)")
    if 'android:foregroundServiceType="specialUse"' not in manifest:
        findings.append("AndroidManifest.xml: rest FGS must declare specialUse")
    if "isMinifyEnabled = true" not in gradle:
        findings.append("app/build.gradle.kts: release minify must be on")
    if "isShrinkResources = true" not in gradle:
        findings.append("app/build.gradle.kts: release resource shrinking must be on")
    if "wrong-password" not in envelope:
        findings.append("BackupEnvelopeTest.kt: wrong-password unwrap proof is missing")
    if "physicalTalkBack: Boolean = false" not in matrix:
        findings.append("AccessibilityMatrix.kt: physicalTalkBack must default false")
    if "pages.all { it.physicalTalkBack && it.automatedEvidence }" not in matrix:
        findings.append("AccessibilityMatrix.kt: publicCandidateReady must require physical TalkBack")
    if not FLOOR.is_file():
        findings.append("tools/released-version-code.txt is missing")
    for doc in DOCS:
        if not doc.is_file():
            findings.append(f"{doc.relative_to(ROOT)} is missing")

    if findings:
        print(f"{len(findings)} play-rehearsal finding(s)")
        for item in findings:
            print(item)
        return 1

    print("check-play-rehearsal: static checks OK")
    print("BLOCKED: physical TalkBack")
    print("BLOCKED: Android Public Candidate")
    print("BLOCKED: Play upload / Commercial RC")
    return 0


if __name__ == "__main__":
    sys.exit(main())
