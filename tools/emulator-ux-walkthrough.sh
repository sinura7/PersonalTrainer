#!/bin/sh
# Runs UxWalkthroughTest on the disposable CI emulator and pulls PNG artifacts.
# android-emulator-runner executes each line of workflow `script:` as its own sh -c,
# so the Gradle invocation must live in this file, not as continued shell lines.
set -eu

gradle_status=0
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.sinura.personaltrainer.ui.walkthrough.UxWalkthroughTest \
  -Pandroid.testInstrumentationRunnerArguments.syntheticFixture=true \
  -Pandroid.testInstrumentationRunnerArguments.artifactRunId=ux-walkthrough \
  --stacktrace || gradle_status=$?

mkdir -p ux-walkthrough
if adb shell 'ls /sdcard/Download/ux-walkthrough-*.png 2>/dev/null'; then
  adb shell 'ls /sdcard/Download/ux-walkthrough-*.png 2>/dev/null' | tr -d '\r' | while read -r remote; do
    [ -n "$remote" ] || continue
    adb pull "$remote" "ux-walkthrough/$(basename "$remote")"
  done
fi
echo "UX_WALKTHROUGH_PNG_COUNT=$(find ux-walkthrough -name '*.png' 2>/dev/null | wc -l | tr -d ' ')"
exit "$gradle_status"
