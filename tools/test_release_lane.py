#!/usr/bin/env python3
"""Fixture proof for tools/check-release-lane.py.

Every rule must still fire on the exact shape it was written for, and stay quiet on
the shape that replaced it. The bad fixtures are release.yml as it stood before
packet X7 (audit X6, BR-2 and BR-7) and a build script without the gate wiring (BR-1).
tools/preflight.sh runs this on every commit.
Run: python3 tools/test_release_lane.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from release_lane import (  # noqa: E402
    expressions_in_run,
    gate_builds_release,
    properties_skip_the_gate,
    setup_android_without_empty_packages,
    swallowed_publishes,
    workflow_findings,
)

ROOT = Path(__file__).resolve().parents[1]

OLD_SETUP = """\
    steps:
      - name: Set up JDK 17
        uses: actions/setup-java@v6
        with:
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699 # v4.0.1

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v6
"""

WITH_OTHER_INPUT = """\
    steps:
      - name: Set up Android SDK
        uses: android-actions/setup-android@v4
        with:
          cmdline-tools-version: 11076708

      - name: Next
        run: echo next
"""

NEW_SETUP = """\
    steps:
      # A comment between steps.
      - name: Set up Android SDK
        uses: android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699 # v4.0.1
        with:
          # The retired `tools` package fails sdkmanager.
          packages: ''

      - uses: android-actions/setup-android@v4
        with:
          packages: ""
"""

# The next step's `packages: ''` must not rescue this one.
NEIGHBOUR = """\
    steps:
      - name: Set up Android SDK
        uses: android-actions/setup-android@v4

      - name: Something else
        uses: some/action@v1
        with:
          packages: ''
"""

OLD_PUBLISH = """\
      - name: Publish GitHub Release
        if: github.ref_type == 'tag'
        env:
          GH_TOKEN: ${{ github.token }}
          APK: ${{ steps.apk.outputs.path }}
        run: |
          tag="$GITHUB_REF_NAME"
          if gh release view "$tag" >/dev/null 2>&1; then
            gh release upload "$tag" "$APK" || \\
              echo "Asset already present on $tag; leaving the existing one untouched."
          else
            gh release create "$tag" "$APK" --verify-tag
          fi
          if [ -f "$mapping" ]; then
            gh release upload "$tag" "$mapping" || echo "mapping.txt already present"
          fi
"""

NEW_PUBLISH = """\
      - name: Publish GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
          APK: ${{ steps.apk.outputs.path }}
        run: |
          attach() {
            if gh release view "$tag" --json assets --jq '.assets[].name' | grep -Fxq "$(basename "$1")"; then
              echo "already there"
            else
              gh release upload "$tag" "$1"
            fi
          }
          # gh release upload "$tag" "$APK" || echo — the old shape, quoted in a comment.
          gh release create "$tag" "$APK" \\
            --title "${tag#v}" \\
            --verify-tag
"""

OTHER_SWALLOWS = """\
      - name: Publish
        run: |
          gh release upload "$tag" "$APK" | tee upload.log
          gh release upload "$tag" "$APK" && echo "uploaded"
          gh release create "$tag" "$APK" 2>&1 | tee create.log
"""

HANDLED = """\
      - name: Publish
        run: |
          gh release create "$tag" "$APK" --verify-tag || { echo "create failed" >&2; exit 1; }
          gh release upload "$tag" "$APK" 2>&1 >/dev/null || exit 3
          gh release upload "$tag" "$APK" >upload.log 2>&1
          gh release upload "$tag" "$APK" || exit 255 # the highest status that is not 0
"""

LOOKS_HANDLED = """\
      - name: Publish
        run: |
          gh release upload "$tag" "$APK" || exit 256
          gh release upload "$tag" "$APK" || { echo "would exit 1"; }
          gh release upload "$tag" "$APK" || { [ -n "$STRICT" ] && exit 1; }
          gh release upload "$tag" "$APK" || exit 0
"""

OLD_VERIFY = """\
      - name: Verify signature
        run: |
          "$apksigner" verify --print-certs --verbose "${{ steps.apk.outputs.path }}"

      - name: Upload APK artifact
        uses: actions/upload-artifact@v7
        with:
          path: ${{ steps.apk.outputs.path }}
"""

INLINE_RUN = """\
      - run: echo "${{ github.event.pull_request.title }}"
      - name: fine
        run: echo "$TITLE"
"""

NEW_VERIFY = """\
      - name: Verify signature
        env:
          APK: ${{ steps.apk.outputs.path }}
        run: |
          "$apksigner" verify --print-certs --verbose "$APK"
"""

GRADLE_WIRED = """\
run {
    val skip = providers.gradleProperty("skipStaticChecks").isPresent
    if (!skip) {
        tasks.matching { it.name == "assembleDebug" }.configureEach {
            dependsOn("assembleRelease")
        }
    }
}
"""

GRADLE_COMMENTED_OUT = """\
run {
    // Slow on the laptop; back on after the demo.
    // tasks.matching { it.name == "assembleDebug" }.configureEach {
    //     dependsOn("assembleRelease")
    // }
    /* tasks.matching { it.name == "assembleDebug" }.configureEach { dependsOn("assembleRelease") } */
}
"""

# The real script's shape: a glob whose `/*` is not a comment, before the wiring, and a
# KDoc-style note after it. A strip that ignores strings would eat the wiring.
GRADLE_GLOBS_AND_KDOC = """\
val jacocoExcludes = listOf("**/R.class", "**/databinding/**")
run {
    tasks.matching { it.name == "assembleDebug" }.configureEach {
        dependsOn("assembleRelease")
    }
}
/** The release rename. It closes the glob's phantom comment: */
val url = "https://example.invalid/path" // a URL is a string, not a comment
"""

QUOTED_USES = """\
      - name: Set up Android SDK
        uses: 'android-actions/setup-android@v4'
"""

INLINE_RUN_WITH_COMMENT = """\
      - run: ./gradlew assembleDebug  # not ${{ github.sha }}, which would be pasted in
"""

GRADLE_RENAME_ONLY = """\
run {
    tasks.matching { it.name == "assembleRelease" }.configureEach {
        doLast { }
    }
}
"""


def expect(label: str, got: list, lines: list[int]) -> None:
    actual = [f.line for f in got]
    if actual != lines:
        print(f"FAIL {label}: expected lines {lines}, got {actual}: {[f.message for f in got]}")
        sys.exit(1)
    print(f"ok  {label}")


def main() -> int:
    expect("setup-android with no inputs is caught", setup_android_without_empty_packages(OLD_SETUP), [8])
    expect("setup-android with other inputs only is caught",
           setup_android_without_empty_packages(WITH_OTHER_INPUT), [3])
    expect("packages: '' and \"\" both pass, comments allowed",
           setup_android_without_empty_packages(NEW_SETUP), [])
    expect("a later step's packages input does not count",
           setup_android_without_empty_packages(NEIGHBOUR), [3])
    expect("a quoted uses: is still seen", setup_android_without_empty_packages(QUOTED_USES), [2])

    expect("the pre-X7 publish step's two swallowed uploads are caught",
           swallowed_publishes(OLD_PUBLISH), [9, 15])
    expect("checked, unswallowed publishes and a quoted comment pass", swallowed_publishes(NEW_PUBLISH), [])
    expect("a pipe or && after a publish is caught (bash -e, no pipefail)", swallowed_publishes(OTHER_SWALLOWS), [3, 4, 5])
    expect("|| that exits nonzero and plain redirections pass", swallowed_publishes(HANDLED), [])
    expect("an exit that wraps to 0, is only quoted, conditional or 0 is caught",
           swallowed_publishes(LOOKS_HANDLED), [3, 4, 5, 6])

    expect("an expression in a run block is caught; env and with are not", expressions_in_run(OLD_VERIFY), [3])
    expect("an expression in an inline run is caught", expressions_in_run(INLINE_RUN), [1])
    expect("a value passed through env passes", expressions_in_run(NEW_VERIFY), [])
    expect("a YAML comment after an inline run is not run text",
           expressions_in_run(INLINE_RUN_WITH_COMMENT), [])
    expect("env values on a publish step are not run text", expressions_in_run(NEW_PUBLISH), [])

    if not gate_builds_release(GRADLE_WIRED):
        print("FAIL the gate wiring is not recognised")
        return 1
    print("ok  the gate wiring is recognised")
    if gate_builds_release(GRADLE_RENAME_ONLY):
        print("FAIL the release rename alone was taken for the gate wiring")
        return 1
    print("ok  the release rename alone is not the gate wiring")
    if gate_builds_release(GRADLE_COMMENTED_OUT):
        print("FAIL a commented-out wiring was taken for the gate wiring")
        return 1
    print("ok  a commented-out wiring is not the gate wiring")
    if not gate_builds_release(GRADLE_GLOBS_AND_KDOC):
        print("FAIL a glob's `/*` before the wiring hid it")
        return 1
    print("ok  a glob's `/*` and a later KDoc do not hide the wiring")
    for setting in ("skipStaticChecks=true", "skipStaticChecks true", "skipStaticChecks", "  skipStaticChecks: yes"):
        if not properties_skip_the_gate(f"org.gradle.caching=true\n{setting}\n"):
            print(f"FAIL {setting!r} in gradle.properties is not caught")
            return 1
    if properties_skip_the_gate("# skipStaticChecks=true is never set here\nskipStaticChecksNote=1\n"):
        print("FAIL a comment naming skipStaticChecks was taken for the setting")
        return 1
    print("ok  skipStaticChecks set in gradle.properties is caught; a comment is not")
    gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    if not gate_builds_release(gradle):
        print("FAIL app/build.gradle.kts does not wire the release into assembleDebug")
        return 1
    print("ok  this repository's build script wires it")

    for path in sorted((ROOT / ".github" / "workflows").glob("*.yml")):
        found = workflow_findings(path.read_text(encoding="utf-8"))
        if found:
            print(f"FAIL {path.relative_to(ROOT)}: {[(f.line, f.message) for f in found]}")
            return 1
    print("ok  this repository's workflows pass")
    print("test_release_lane: all assertions passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
