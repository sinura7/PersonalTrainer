#!/usr/bin/env python3
"""Fixture proofs for the sixteen source-reading policy tests that left the JVM.

The checkers are the ratchet. These fixtures go red if a helper swallows a
defect the old JUnit methods used to catch. Run from preflight.

Moved (count drop 16):
  BackupPolicyTest ×3
  LintPolicyTest ×3
  DriveAuthPolicyTest.driveAuthClientDoesNotImportGoogleSignIn
  DriveAuthPolicyTest.catalogPinsPlayServicesAuth216
  DomainSeamPolicyTest.domainSourcesDoNotImportPlatformTimeOrLocale
  SdkTargetTest ×4
  CoreToolchainTest.catalogMatchesTheSignedMatrix
  ComposeToolchainTest.catalogMatchesTheSignedMatrix
  PersistenceToolchainTest.catalogAndSchemasMatchTheSignedMatrix
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent


def load(filename: str):
    path = TOOLS / filename
    spec = importlib.util.spec_from_file_location(path.stem.replace("-", "_"), path)
    if spec is None or spec.loader is None:
        raise SystemExit(f"cannot load {path}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main() -> int:
    sdk = load("check-sdk-target.py")
    seams = load("check-domain-seams.py")
    lint = load("check-lint-policy.py")
    backup = load("check-backup-policy.py")
    supply = load("check-supply-chain.py")

    good_catalog = "\n".join(f'{key} = "{want}"' for key, want in sdk.CATALOG_EXACT.items())
    if sdk.catalog_exact_findings(good_catalog):
        raise SystemExit(f"FAIL exact catalog should pass: {sdk.catalog_exact_findings(good_catalog)}")

    bumped = good_catalog.replace('playServicesAuth = "21.6.0"', 'playServicesAuth = "22.0.0"')
    play = sdk.catalog_exact_findings(bumped)
    if not any("playServicesAuth" in item and "22.0.0" in item for item in play):
        raise SystemExit(f"FAIL play-services-auth 22 must fail exact pin: {play}")

    if sdk.predictive_back_findings('android:enableOnBackInvokedCallback="true"'):
        raise SystemExit("FAIL predictive back true should pass")
    missing_back = sdk.predictive_back_findings("<manifest/>")
    if not missing_back:
        raise SystemExit("FAIL missing predictive back must fail")

    ledger_ok = (
        '<component group="org.jetbrains.kotlin" name="kotlin-stdlib" version="2.0.21"/>'
    )
    if sdk.kotlin_stdlib_ceiling_findings(ledger_ok):
        raise SystemExit("FAIL 2.0.21 stdlib should pass")
    too_new = sdk.kotlin_stdlib_ceiling_findings(
        '<component group="org.jetbrains.kotlin" name="kotlin-stdlib" version="2.2.0"/>',
    )
    if not too_new:
        raise SystemExit("FAIL Kotlin 2.2 stdlib must fail")

    auth_ok = (
        "getAuthorizationClient drive.file clearToken revokeAccess AuthorizationClient"
    )
    if sdk.drive_auth_source_findings(auth_ok):
        raise SystemExit(f"FAIL Drive auth needles should pass: {sdk.drive_auth_source_findings(auth_ok)}")
    sign_in = sdk.drive_auth_source_findings(auth_ok + "\nGoogleSignIn")
    if not any("GoogleSignIn" in item for item in sign_in):
        raise SystemExit(f"FAIL GoogleSignIn remnant must fail: {sign_in}")

    if seams.banned_import_findings("package com.sinura.personaltrainer.domain\n"):
        raise SystemExit("FAIL clean domain snippet should pass")
    banned = seams.banned_import_findings("import java.time.LocalDate\n")
    if not any("java.time" in item for item in banned):
        raise SystemExit(f"FAIL java.time import must fail: {banned}")

    gradle_ok = (
        'warningsAsErrors = true\n'
        'baseline = file("lint-baseline.xml")\n'
        'disable += setOf("AndroidGradlePluginVersion", "UseKtx", "GradleDependency")\n'
    )
    # Computed outside the f-string: a backslash inside an f-string expression is a
    # SyntaxError before Python 3.12, and the owner's Windows host and this preflight
    # both run 3.11. The whole gate died at import, so nothing after it ran either.
    clean_lint = lint.lint_source_findings(gradle_ok, "<?xml version='1.0'?><issues/>")
    if clean_lint:
        raise SystemExit(f"FAIL lint gate should pass: {clean_lint}")
    no_warnings = lint.lint_source_findings("disable += setOf()", "<issues/>")
    if not any("warningsAsErrors" in item for item in no_warnings):
        raise SystemExit(f"FAIL missing warningsAsErrors must fail: {no_warnings}")
    leftover = lint.lint_source_findings(gradle_ok, '<issue id="UnusedResources"/>')
    if not any("UnusedResources" in item for item in leftover):
        raise SystemExit(f"FAIL leftover lint issue must fail: {leftover}")

    if backup.backup_manifest_findings(
        "false",
        "@xml/backup_rules",
        "@xml/data_extraction_rules",
    ):
        raise SystemExit("FAIL backup manifest should pass")
    allow_on = backup.backup_manifest_findings(
        "true",
        "@xml/backup_rules",
        "@xml/data_extraction_rules",
    )
    if not any("allowBackup" in item for item in allow_on):
        raise SystemExit(f"FAIL allowBackup true must fail: {allow_on}")

    ledger_body = (
        "<verify-metadata>true</verify-metadata>\n"
        + ("<sha256/>\n" * 21)
        + '<trust file=".*-sources[.]jar" regex="true"/>\n'
        + '<trust file=".*-javadoc[.]jar" regex="true"/>\n'
        + '<trust group="gradle" name="gradle" file=".*-src[.]zip" regex="true"/>\n'
        + "aapt2-8.9.2-12782657-linux.jar\n"
        + "aapt2-8.9.2-12782657-windows.jar\n"
        + "aapt2-8.9.2-12782657-osx.jar\n"
    )
    if supply.ledger_control_findings(ledger_body):
        raise SystemExit(
            f"FAIL ledger controls should pass: {supply.ledger_control_findings(ledger_body)}",
        )
    no_verify = supply.ledger_control_findings("<verify-metadata>false</verify-metadata>")
    if not any("verify-metadata" in item for item in no_verify):
        raise SystemExit(f"FAIL verify-metadata false must fail: {no_verify}")

    print("ok  catalog exact pin")
    print("ok  play-services-auth 22 is refused")
    print("ok  predictive back")
    print("ok  Kotlin 2.2 stdlib ceiling")
    print("ok  DriveAuth GoogleSignIn remnant")
    print("ok  domain java.time import")
    print("ok  lint warningsAsErrors and empty baseline")
    print("ok  allowBackup false")
    print("ok  verification ledger controls")
    print("test_policy_move: all assertions passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
