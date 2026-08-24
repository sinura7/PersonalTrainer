#!/usr/bin/env python3
"""Compare JaCoCo package instruction coverage against tools/coverage-floors.txt.

The report is a risk indicator, not a game. Generated/framework classes are
excluded in the Gradle JacocoReport task. Floors only cover packages we have
chosen to ratchet.
"""
from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
FLOORS = os.path.join(ROOT, "tools", "coverage-floors.txt")
REPORT_CANDIDATES = [
    os.path.join(ROOT, "app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"),
    os.path.join(ROOT, "app/build/reports/jacoco/test/jacocoTestReport.xml"),
    os.path.join(ROOT, "app/build/reports/coverage/test/debug/report.xml"),
]


def load_floors(path: str) -> dict[str, float]:
    floors = {}
    with open(path, encoding="utf-8") as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            package, percent = line.split()
            floors[package] = float(percent)
    return floors


def find_report() -> str:
    for candidate in REPORT_CANDIDATES:
        if os.path.isfile(candidate):
            return candidate
    raise SystemExit(
        "check-coverage: no JaCoCo XML found. Run ./gradlew jacocoTestReport first.\n"
        "looked in:\n  " + "\n  ".join(REPORT_CANDIDATES)
    )


def package_instruction_percent(report: str) -> dict[str, float]:
    tree = ET.parse(report)
    found: dict[str, float] = {}
    for package in tree.getroot().iter("package"):
        name = package.attrib.get("name", "").replace("/", ".")
        for counter in package.findall("counter"):
            if counter.attrib.get("type") != "INSTRUCTION":
                continue
            missed = float(counter.attrib.get("missed", "0"))
            covered = float(counter.attrib.get("covered", "0"))
            total = missed + covered
            if total <= 0:
                continue
            found[name] = 100.0 * covered / total
    return found


def main() -> int:
    floors = load_floors(FLOORS)
    report = find_report()
    measured = package_instruction_percent(report)
    findings = 0
    print(f"check-coverage: {os.path.relpath(report, ROOT)}")
    for package, floor in sorted(floors.items()):
        actual = measured.get(package)
        if actual is None:
            print(f"FAIL  {package}: no package in report (floor {floor:.1f}%)")
            findings += 1
            continue
        status = "ok" if actual + 1e-6 >= floor else "FAIL"
        if status == "FAIL":
            findings += 1
        print(f"{status:4}  {package}: {actual:.1f}%  floor {floor:.1f}%")
    print(f"\n{findings} coverage finding(s)")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
