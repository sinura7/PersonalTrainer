#!/bin/sh
# Runs UxWalkthroughTest on the disposable CI emulator and pulls PNG artifacts.
# android-emulator-runner executes each line of workflow `script:` as its own sh -c,
# so the Gradle invocation must live in this file, not as continued shell lines.
set -eu
set -o pipefail

PACKAGE=com.sinura.personaltrainer.debug
FILTER_PATTERN="AndroidRuntime|FATAL EXCEPTION|FATAL EXCEPTION:|UXWalkthrough|UX_WALKTHROUGH|${PACKAGE}|com.sinura.personaltrainer"

grant_permissions() {
  adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
  adb shell cmd appops set "$PACKAGE" SCHEDULE_EXACT_ALARM allow 2>/dev/null || true
  adb shell dumpsys deviceidle whitelist +"$PACKAGE" 2>/dev/null || true
}

capture_flow_logcat() {
  flow="$1"
  mkdir -p ux-walkthrough
  out="ux-walkthrough/logcat-f${flow}-crash.txt"
  adb logcat -d 2>/dev/null | grep -E "$FILTER_PATTERN" >"$out" || true
  if [ ! -s "$out" ]; then
    adb logcat -d 2>/dev/null | tail -n 400 >"$out" || true
  fi
  line_count=$(wc -l <"$out" 2>/dev/null | tr -d ' ')
  echo "UX_WALKTHROUGH_LOGCAT f${flow} ${line_count} lines -> $out"
  echo "::group::UX walkthrough logcat flow ${flow} (filtered)"
  cat "$out" 2>/dev/null || true
  echo "::endgroup::"
}

grant_permissions

mkdir -p ux-walkthrough
gradle_status=0
for flow in 1 2 3 4 5 6 7 8 9; do
  adb logcat -c 2>/dev/null || true
  adb shell pm clear "$PACKAGE" >/dev/null 2>&1 || true
  grant_permissions
  flow_log="/tmp/ux-walkthrough-f${flow}.gradle.log"
  if ! ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.sinura.personaltrainer.ui.walkthrough.UxWalkthroughTest \
    -Pandroid.testInstrumentationRunnerArguments.syntheticFixture=true \
    -Pandroid.testInstrumentationRunnerArguments.artifactRunId="ux-walkthrough-f$flow" \
    -Pandroid.testInstrumentationRunnerArguments.uxFlow="$flow" \
    --stacktrace 2>&1 | tee "$flow_log"; then
    gradle_status=1
    capture_flow_logcat "$flow"
  elif grep -q "Process crashed" "$flow_log" 2>/dev/null; then
    gradle_status=1
    capture_flow_logcat "$flow"
  fi
done
if adb shell 'ls /sdcard/Download/ux-walkthrough-*.png 2>/dev/null'; then
  adb shell 'ls /sdcard/Download/ux-walkthrough-*.png 2>/dev/null' | tr -d '\r' | while read -r remote; do
    [ -n "$remote" ] || continue
    adb pull "$remote" "ux-walkthrough/$(basename "$remote")"
  done
fi
echo "UX_WALKTHROUGH_PNG_COUNT=$(find ux-walkthrough -name '*.png' 2>/dev/null | wc -l | tr -d ' ')"
echo "UX_WALKTHROUGH_LOGCAT_COUNT=$(find ux-walkthrough -name 'logcat-f*-crash.txt' 2>/dev/null | wc -l | tr -d ' ')"
if [ "$gradle_status" -ne 0 ]; then
  mkdir -p app/build/reports/androidTests
  adb logcat -d 2>/dev/null | grep -E "$FILTER_PATTERN" > app/build/reports/androidTests/logcat-filtered.txt || true
  echo '::group::logcat filtered (last 200 lines)'
  tail -n 200 app/build/reports/androidTests/logcat-filtered.txt 2>/dev/null || true
  echo '::endgroup::'
fi
exit "$gradle_status"
