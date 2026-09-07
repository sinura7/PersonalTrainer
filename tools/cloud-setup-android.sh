#!/bin/bash
# Setup script for the Temper cloud environment.
#
# Paste this file's contents into the "Setup script" field of the cloud
# environment at claude.ai/code. It is kept in the repository so it is
# versioned, reviewable, and fixable by a session that finds it wrong.
#
# Prerequisite: the environment's Network access must be Custom, with
#   dl.google.com
#   maven.google.com
# in Allowed domains and "Also include default list of common package
# managers" ticked. The Trusted list already carries Maven Central, Gradle
# and the Ubuntu archives; it does not carry Google's two Android hosts.
#
# Contract this script has to honour (docs/en/cloud-environments):
#   * exit 0, or the session fails to start;
#   * finish inside about five minutes, or the snapshot is not cached.
# Every step is therefore guarded with `|| true`, and the SDK is installed
# before the optional JDK so it wins the time budget.
#
# The result is snapshotted, so this runs once per environment rather than
# once per session. It re-runs when the script or the allowed hosts change,
# and after the cache expires.

set -u

SDK_ROOT=/opt/android-sdk
BASE=https://dl.google.com/android/repository

# --- Android command-line tools -------------------------------------------
# The build number in the filename moves. Read it off Google's own manifest
# rather than pinning a guess, and keep a known build as the fallback.
ZIP=$(curl -fsSL "$BASE/repository2-3.xml" 2>/dev/null |
    grep -o 'commandlinetools-linux-[0-9]\+_latest\.zip' | head -1)
[ -n "${ZIP:-}" ] || ZIP=commandlinetools-linux-11076708_latest.zip

mkdir -p "$SDK_ROOT/cmdline-tools" || true
curl -fsSL -o /tmp/cmdline-tools.zip "$BASE/$ZIP" || true
unzip -q -o /tmp/cmdline-tools.zip -d "$SDK_ROOT/cmdline-tools" || true
# The zip unpacks to cmdline-tools/; sdkmanager insists on cmdline-tools/latest/.
if [ -d "$SDK_ROOT/cmdline-tools/cmdline-tools" ]; then
    rm -rf "$SDK_ROOT/cmdline-tools/latest" || true
    mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest" || true
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$PATH"

# compileSdk 36 and AGP 8.9.2, per app/build.gradle.kts and gradle/libs.versions.toml.
yes 2>/dev/null | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0" \
    >/dev/null 2>&1 || true

# Login shells; the environment's own "Environment variables" field is the
# reliable path and should carry ANDROID_HOME and ANDROID_SDK_ROOT too.
cat >/etc/profile.d/android-sdk.sh <<'PROFILE' || true
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
PROFILE
chmod 0644 /etc/profile.d/android-sdk.sh || true

# --- JDK 17, last, because it is optional ---------------------------------
# ci.yml builds on temurin 17 and the project targets Java 17. The image
# ships 21, which AGP 8.9.2 and Gradle 8.11.1 both support, so this only
# buys an exact match with CI. Adoptium is not reachable; Ubuntu's build of
# the same OpenJDK is.
apt-get update -qq >/dev/null 2>&1 || true
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk unzip \
    >/dev/null 2>&1 || true

# --- Leave evidence of what actually happened -----------------------------
{
    echo "ran:            $(date -u +%FT%TZ)"
    echo "sdk root:       $SDK_ROOT"
    echo "cmdline-tools:  $ZIP"
    echo "sdkmanager:     $(command -v sdkmanager || echo MISSING)"
    echo "platforms:      $(ls "$SDK_ROOT/platforms" 2>/dev/null | tr '\n' ' ' || echo NONE)"
    echo "build-tools:    $(ls "$SDK_ROOT/build-tools" 2>/dev/null | tr '\n' ' ' || echo NONE)"
    echo "jdk17:          $([ -d /usr/lib/jvm/java-17-openjdk-amd64 ] && echo present || echo absent)"
} >/opt/android-sdk-setup.log 2>&1 || true

exit 0
