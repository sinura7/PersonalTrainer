#!/usr/bin/env python3
"""Drift check: P4.6 supply-chain controls.

Repositories stay closed. Catalog versions stay exact. Checksums exist.
"""
from __future__ import annotations

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SETTINGS = os.path.join(ROOT, "settings.gradle.kts")
CATALOG = os.path.join(ROOT, "gradle/libs.versions.toml")
VERIFY = os.path.join(ROOT, "gradle/verification-metadata.xml")

findings: list[str] = []


def ledger_control_findings(body: str) -> list[str]:
    found: list[str] = []
    if "<verify-metadata>true</verify-metadata>" not in body:
        found.append("verification-metadata.xml  verify-metadata must be true")
    if body.count("<sha256") < 20:
        found.append("verification-metadata.xml  sha256 component set looks empty")
    if '<trust file=".*-sources[.]jar" regex="true"/>' not in body:
        found.append("verification-metadata.xml  Studio sources jars must stay trusted")
    if '<trust file=".*-javadoc[.]jar" regex="true"/>' not in body:
        found.append("verification-metadata.xml  javadoc jars must stay trusted")
    if '<trust group="gradle" name="gradle" file=".*-src[.]zip" regex="true"/>' not in body:
        found.append("verification-metadata.xml  Gradle distribution src.zip must stay trusted")
    for host_jar in (
        "aapt2-8.9.2-12782657-linux.jar",
        "aapt2-8.9.2-12782657-windows.jar",
        "aapt2-8.9.2-12782657-osx.jar",
    ):
        if host_jar not in body:
            found.append(f"verification-metadata.xml  missing host aapt2 {host_jar}")
    return found


def main() -> int:
    settings = open(SETTINGS, encoding="utf-8").read()
    if "FAIL_ON_PROJECT_REPOS" not in settings:
        findings.append("settings.gradle.kts  must fail on project repos")
    for repo in ("jcenter()", "mavenLocal()", "maven {", "exclusiveContent"):
        if repo in settings:
            findings.append(f"settings.gradle.kts  unexpected repository surface {repo}")
    if "google()" not in settings or "mavenCentral()" not in settings:
        findings.append("settings.gradle.kts  google() and mavenCentral() are required")

    catalog = open(CATALOG, encoding="utf-8").read()
    for match in re.finditer(r'^([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"', catalog, re.M):
        key, value = match.group(1), match.group(2)
        if value in {"+", "latest", "latest.release", "latest.integration"} or value.endswith("+"):
            findings.append(f"gradle/libs.versions.toml  {key} is dynamic ({value})")

    if not os.path.isfile(VERIFY):
        findings.append("gradle/verification-metadata.xml  missing checksum ledger")
    else:
        findings.extend(ledger_control_findings(open(VERIFY, encoding="utf-8").read()))

    print(f"{len(findings)} supply-chain finding(s)")
    for item in findings:
        print(item)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
