#!/bin/sh
# tools/verify.sh — one local verification command for foundation-program packets.
#
# Always: preflight, unit tests, lint (baseline + new issues), JaCoCo ratchet.
# If an emulator or device is already up: connectedDebugAndroidTest against
# com.sinura.personaltrainer.debug. Never points at the release applicationId.
set -u
cd "$(dirname "$0")/.." || exit 2

fail() { echo "verify: FAIL — $*" >&2; exit 1; }
step() { printf '\n== %s\n' "$*"; }

if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi
if [ -n "${ANDROID_HOME:-}" ]; then
    export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
fi

step "tools/preflight.sh"
tools/preflight.sh || fail "preflight"

step "./gradlew testDebugUnitTest lintDebug jacocoTestReport"
./gradlew testDebugUnitTest lintDebug jacocoTestReport --stacktrace || fail "gradle verification"

step "tools/check-coverage.py"
python3 tools/check-coverage.py || fail "coverage floors"

if command -v adb >/dev/null 2>&1; then
    devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
    if [ -n "$devices" ]; then
        step "./gradlew connectedDebugAndroidTest"
        echo "verify: using device(s): $(echo "$devices" | tr '\n' ' ')"
        ./gradlew connectedDebugAndroidTest --stacktrace || fail "connectedDebugAndroidTest"
    else
        echo "verify: no adb device — skipping connectedDebugAndroidTest"
        echo "verify: start an API 29+ emulator, then re-run this script for FND-004"
    fi
else
    echo "verify: adb not on PATH — skipping connectedDebugAndroidTest"
fi

printf '\nverify: OK\n'
