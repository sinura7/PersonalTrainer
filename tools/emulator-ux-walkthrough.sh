#!/bin/sh
# Runs UxWalkthroughTest on the disposable CI emulator and pulls PNG artifacts.
# android-emulator-runner executes each line of workflow `script:` as its own sh -c,
# so the Gradle invocation must live in this file, not as continued shell lines.
set -eu

PACKAGE=com.sinura.personaltrainer.debug
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell cmd appops set "$PACKAGE" SCHEDULE_EXACT_ALARM allow 2>/dev/null || true
adb shell dumpsys deviceidle whitelist +"$PACKAGE" 2>/dev/null || true

gradle_status=0
for flow in 1 2 3 4 5 6 7 8 9; do
  adb shell pm clear "$PACKAGE" >/dev/null 2>&1 || true
  if ! ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.sinura.personaltrainer.ui.walkthrough.UxWalkthroughTest \
    -Pandroid.testInstrumentationRunnerArguments.syntheticFixture=true \
    -Pandroid.testInstrumentationRunnerArguments.artifactRunId="ux-walkthrough-f$flow" \
    -Pandroid.testInstrumentationRunnerArguments.uxFlow="$flow" \
    --stacktrace; then
    gradle_status=1
  fi
done

mkdir -p ux-walkthrough
if adb shell 'ls /sdcard/Download/ux-walkthrough-*.png 2>/dev/null'; then
  adb shell 'ls /sdcard/Download/ux-walkthrough-*.png 2>/dev/null' | tr -d '\r' | while read -r remote; do
    [ -n "$remote" ] || continue
    adb pull "$remote" "ux-walkthrough/$(basename "$remote")"
  done
fi
echo "UX_WALKTHROUGH_PNG_COUNT=$(find ux-walkthrough -name '*.png' 2>/dev/null | wc -l | tr -d ' ')"
if [ "$gradle_status" -ne 0 ]; then
  mkdir -p app/build/reports/androidTests
  adb logcat -d > app/build/reports/androidTests/logcat.txt 2>/dev/null || true
  echo '::group::logcat (last 200 lines)'
  tail -n 200 app/build/reports/androidTests/logcat.txt 2>/dev/null || true
  echo '::endgroup::'
fi
exit "$gradle_status"
