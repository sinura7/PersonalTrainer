#!/usr/bin/env python3
"""Drift check: Auto Backup stays off and exclusion rules name every store.

P3.5 / ADR-009. Fail if the shipping manifest re-enables the channel or if
the rule files drop a store from docs/architecture/backup-threat-model.md §3.
"""
from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MANIFEST = os.path.join(ROOT, "app/src/main/AndroidManifest.xml")
LEGACY = os.path.join(ROOT, "app/src/main/res/xml/backup_rules.xml")
EXTRACTION = os.path.join(ROOT, "app/src/main/res/xml/data_extraction_rules.xml")

ANDROID = "{http://schemas.android.com/apk/res/android}"

REQUIRED_PATHS = [
    "personal_trainer.db",
    "temper.db",
    "datastore",
    "safety-snapshots",
    "restore-journal",
    "pre-migration",
    "diagnostics",
    "rest_timer_state.xml",
    "schema_marker.xml",
]

REQUIRED_DOMAINS = {"root", "file", "database", "sharedpref", "external"}

findings: list[str] = []


def backup_manifest_findings(
    allow: str | None,
    full: str | None,
    extraction: str | None,
) -> list[str]:
    found: list[str] = []
    if allow != "false":
        found.append(f'allowBackup must be "false" (found {allow!r})')
    if full != "@xml/backup_rules":
        found.append(
            f"fullBackupContent must reference @xml/backup_rules (found {full!r})",
        )
    if extraction != "@xml/data_extraction_rules":
        found.append(
            "dataExtractionRules must reference @xml/data_extraction_rules "
            f"(found {extraction!r})",
        )
    return found


def add(path: str, message: str) -> None:
    findings.append(f"{os.path.relpath(path, ROOT)}  {message}")


def android_attr(el: ET.Element, name: str) -> str | None:
    return el.attrib.get(f"{ANDROID}{name}") or el.attrib.get(name)


def exclude_paths(el: ET.Element) -> set[str]:
    found: set[str] = set()
    for child in el.iter():
        if child.tag.split("}")[-1] != "exclude":
            continue
        path = child.attrib.get("path")
        if path:
            found.add(path)
    return found


def exclude_domains(el: ET.Element) -> set[str]:
    found: set[str] = set()
    for child in el.iter():
        if child.tag.split("}")[-1] != "exclude":
            continue
        domain = child.attrib.get("domain")
        if domain:
            found.add(domain)
    return found


def main() -> int:
    tree = ET.parse(MANIFEST)
    app = tree.getroot().find("application")
    if app is None:
        add(MANIFEST, "missing <application>")
        print(f"{len(findings)} backup-policy finding(s)")
        return 1

    allow = android_attr(app, "allowBackup")
    full = android_attr(app, "fullBackupContent")
    extraction = android_attr(app, "dataExtractionRules")
    for message in backup_manifest_findings(allow, full, extraction):
        add(MANIFEST, message)

    for path in (LEGACY, EXTRACTION):
        if not os.path.isfile(path):
            add(path, "missing exclusion rules file")
            continue
        root = ET.parse(path).getroot()
        domains = exclude_domains(root)
        missing_domains = REQUIRED_DOMAINS - domains
        if missing_domains:
            add(path, "missing domain excludes: " + ", ".join(sorted(missing_domains)))
        paths = exclude_paths(root)
        missing_paths = [p for p in REQUIRED_PATHS if p not in paths]
        if missing_paths:
            add(path, "missing store excludes: " + ", ".join(missing_paths))

    extra_root = ET.parse(EXTRACTION).getroot() if os.path.isfile(EXTRACTION) else None
    if extra_root is not None:
        tags = {child.tag.split("}")[-1] for child in extra_root}
        if "cloud-backup" not in tags:
            add(EXTRACTION, "missing <cloud-backup>")
        if "device-transfer" not in tags:
            add(EXTRACTION, "missing <device-transfer>")

    print(f"{len(findings)} backup-policy finding(s)")
    for item in findings:
        print(item)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
