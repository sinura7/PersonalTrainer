#!/bin/bash
# Bootstrap the Android SDK so `./gradlew testDebugUnitTest assembleDebug` — the merge gate —
# can actually run in a Claude Code on the web session.
#
# WHY THIS EXISTS. For weeks this repo was worked on from environments where Google's Maven
# was refused at the CONNECT, so Gradle could not resolve the Android Gradle Plugin and the
# project could not even configure. On 10 September 2026 dl.google.com started answering, the
# SDK was installed by hand, and `./gradlew assembleDebug` built a real APK in 5m24s while
# `testDebugUnitTest` ran 1,946 tests. This script is that afternoon's work made repeatable.
#
# IT IS NOT THE PRIMARY PATH. tools/cloud-setup-android.sh is: it is the environment's Setup
# script at claude.ai/code, it runs once per environment, and the result is snapshotted, so a
# session in a configured environment pays nothing. This hook is the fallback for the case that
# script cannot cover — an environment whose Setup script was never filled in, or whose snapshot
# has expired. It installs the same three packages, so where that script has already run this
# one finds them present and exits. Keep the two in step.
#
# IT IS ALLOWED TO DO NOTHING. Egress policy is not ours to rely on. If Google's servers are
# refused again this exits 0 with a message, the session starts normally, and the static gate
# and the JVM lane remain the fallback they have always been. A session that cannot reach
# Google must still be a working session.
set -euo pipefail

# A developer machine has its own SDK and its own opinions about where it lives.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

SDK="${ANDROID_SDK_ROOT:-/opt/android-sdk}"

# Export unconditionally, even on the paths that install nothing: Gradle reads ANDROID_HOME,
# and a half-set environment is worse than an unset one.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=$SDK"
    echo "export ANDROID_SDK_ROOT=$SDK"
  } >> "$CLAUDE_ENV_FILE"
fi

# compileSdk is 36 (app/build.gradle.kts). build-tools 36.0.0 is what tools/cloud-setup-android.sh
# installs, and the two must name the same version or a session gets a different toolchain
# depending on which one ran. Accepting the licences also lets AGP fetch a different one itself
# if it ever wants to.
PLATFORM="platforms;android-36"
BUILD_TOOLS="build-tools;36.0.0"
CLT_ZIP="commandlinetools-linux-13114758_latest.zip"

if [ -d "$SDK/platforms/android-36" ] && [ -d "$SDK/build-tools/36.0.0" ] && [ -x "$SDK/platform-tools/adb" ]; then
  echo "android-sdk: already installed at $SDK"
  exit 0
fi

# One cheap request decides whether any of this is possible. `curl -f` so an HTML error page
# from a proxy counts as a refusal rather than a success.
if ! curl -fsS -o /dev/null --max-time 45 \
     "https://dl.google.com/android/repository/repository2-3.xml"; then
  echo "android-sdk: dl.google.com is not reachable from this environment — skipping."
  echo "  ./gradlew cannot resolve the Android plugin here. Use the offline lanes instead:"
  echo "    PT_STATIC_ONLY=1 sh tools/preflight.sh     (static gate)"
  echo "    PT_JARS=build/test-jars tools/run-domain-tests.sh  (host-runnable tests)"
  exit 0
fi

echo "android-sdk: dl.google.com reachable; installing to $SDK"
mkdir -p "$SDK/cmdline-tools"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  echo "android-sdk: fetching $CLT_ZIP"
  curl -fsSL --max-time 900 -o "$TMP/clt.zip" \
    "https://dl.google.com/android/repository/$CLT_ZIP"
  unzip -q "$TMP/clt.zip" -d "$TMP/x"
  # The zip unpacks to cmdline-tools/, and sdkmanager insists on living under latest/.
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$TMP/x/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"

# `yes` closes its pipe when sdkmanager exits, which is a SIGPIPE under pipefail; the `|| true`
# is about that, not about ignoring a real failure — the install below is what is checked.
echo "android-sdk: accepting licences"
yes 2>/dev/null | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true

echo "android-sdk: installing platform-tools, $PLATFORM, $BUILD_TOOLS"
"$SDKMANAGER" --install "platform-tools" "$PLATFORM" "$BUILD_TOOLS" > /dev/null

# Verify rather than assume: sdkmanager can exit 0 having installed nothing.
missing=""
[ -d "$SDK/platforms/android-36" ]   || missing="$missing $PLATFORM"
[ -d "$SDK/build-tools/36.0.0" ]     || missing="$missing $BUILD_TOOLS"
[ -x "$SDK/platform-tools/adb" ]     || missing="$missing platform-tools"
if [ -n "$missing" ]; then
  echo "android-sdk: FAILED — missing:$missing" >&2
  exit 1
fi

echo "android-sdk: ready. ./gradlew testDebugUnitTest assembleDebug can now run."
