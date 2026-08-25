#!/usr/bin/env python3
"""P12.2 / FND-048: core recording, history, goals, reminders, and export
stay free of billing, account, analytics, and ad SDKs.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app" / "src" / "main" / "java" / "com" / "sinura" / "personaltrainer"
TOML = ROOT / "gradle" / "libs.versions.toml"
GRADLE_FILES = [
    ROOT / "app" / "build.gradle.kts",
    ROOT / "build.gradle.kts",
    TOML,
]

CORE_PACKAGES = (
    "domain",
    "workout",
    "activity",
    "insights",
    "data/repository",
    "data/backup",
)

FORBIDDEN_IMPORT = re.compile(
    r"^import\s+("
    r"com\.android\.billingclient|"
    r"com\.android\.vending\.billing|"
    r"com\.revenuecat|"
    r"com\.google\.firebase|"
    r"com\.google\.android\.gms\.ads|"
    r"com\.google\.android\.gms\.analytics|"
    r"io\.branch|"
    r"com\.mixpanel|"
    r"com\.amplitude"
    r")\.",
    re.M,
)

FORBIDDEN_DEP = re.compile(
    r"billing|revenuecat|firebase-analytics|firebase-crashlytics|"
    r"play-services-ads|play-services-analytics|mixpanel|amplitude",
    re.I,
)


def main() -> int:
    findings: list[str] = []
    for package in CORE_PACKAGES:
        root = SRC / package
        if not root.is_dir():
            continue
        for path in root.rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            for match in FORBIDDEN_IMPORT.finditer(text):
                rel = path.relative_to(ROOT)
                findings.append(f"{rel}: forbidden import {match.group(1)}")
    for gradle in GRADLE_FILES:
        if not gradle.is_file():
            continue
        text = gradle.read_text(encoding="utf-8")
        for match in FORBIDDEN_DEP.finditer(text):
            # Drive auth is play-services-auth, not ads/analytics.
            token = match.group(0).lower()
            if token == "play-services-ads" or "analytics" in token or token in {
                "billing",
                "revenuecat",
                "firebase-analytics",
                "firebase-crashlytics",
                "mixpanel",
                "amplitude",
            }:
                findings.append(f"{gradle.relative_to(ROOT)}: forbidden dependency {match.group(0)}")
    print(f"{len(findings)} commercial-boundary finding(s)")
    for item in findings:
        print(item)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
