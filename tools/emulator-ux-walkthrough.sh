#!/usr/bin/env bash
# Runs UxWalkthroughTest on the disposable CI emulator and pulls PNG artifacts.
# android-emulator-runner executes this file directly; bash is required for pipefail.
set -eu
set -o pipefail

PACKAGE=com.sinura.personaltrainer.debug
OUT_DIR=walkthrough-screens
FILTER_PATTERN="AndroidRuntime|FATAL EXCEPTION|FATAL EXCEPTION:|UXWalkthrough|UX_WALKTHROUGH|PT/DbMaintenance|PT/OnboardingVM|Seeding the default exercise catalog|Catalog name collisions|${PACKAGE}|com.sinura.personaltrainer"

capture_catalog_seed_logcat() {
  flow="$1"
  mkdir -p "$OUT_DIR/logcat"
  out="$OUT_DIR/logcat/f${flow}-catalog-seed.txt"
  adb logcat -d -v time 2>/dev/null | grep -E "UX_WALKTHROUGH_CATALOG_|UX_WALKTHROUGH_PLAN_PREVIEW_|UX_WALKTHROUGH_POST_SAVE_ROUTE|UX_WALKTHROUGH_POST_CARDIO_ROUTE|PT/DbMaintenance|PT/OnboardingVM|Seeding the default exercise catalog|Catalog name collisions" >"$out" || true
  line_count=$(wc -l <"$out" 2>/dev/null | tr -d ' ')
  echo "UX_WALKTHROUGH_CATALOG_LOGCAT f${flow} ${line_count} lines -> $out"
  echo "::group::Catalog seed timestamps flow ${flow}"
  cat "$out" 2>/dev/null || true
  echo "::endgroup::"
}

suppress_system_anr_dialogs() {
  adb shell settings put global hide_error_dialogs 1 2>/dev/null || true
}

stabilize_emulator_ui() {
  suppress_system_anr_dialogs
  adb shell settings put global window_animation_scale 0 2>/dev/null || true
  adb shell settings put global transition_animation_scale 0 2>/dev/null || true
  adb shell settings put global animator_duration_scale 0 2>/dev/null || true
  adb shell settings put secure show_ime_with_hard_keyboard 0 2>/dev/null || true
  adb shell cmd locale set-app-locale "$PACKAGE" en-CA 2>/dev/null || true
  adb shell setprop persist.sys.timezone America/Toronto 2>/dev/null || true
}

grant_permissions() {
  stabilize_emulator_ui
  adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
  adb shell cmd appops set "$PACKAGE" SCHEDULE_EXACT_ALARM allow 2>/dev/null || true
  adb shell dumpsys deviceidle whitelist +"$PACKAGE" 2>/dev/null || true
}

capture_flow_logcat() {
  flow="$1"
  mkdir -p "$OUT_DIR/logcat"
  out="$OUT_DIR/logcat/f${flow}-crash.txt"
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

stabilize_emulator_ui

flows_from_spec() {
  spec="${1:-1-9}"
  if [[ "$spec" =~ ^([0-9]+)-([0-9]+)$ ]]; then
    seq "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}"
  else
    echo "$spec" | tr ',' ' '
  fi
}

FLOW_SPEC="${UX_WALKTHROUGH_FLOWS:-${UX_WALKTHROUGH_FLOW_RANGE:-1-9}}"
echo "UX_WALKTHROUGH_FLOW_SPEC=$FLOW_SPEC"

mkdir -p "$OUT_DIR"
gradle_status=0
for flow in $(flows_from_spec "$FLOW_SPEC"); do
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
  capture_catalog_seed_logcat "$flow"
done
if adb shell 'ls /sdcard/Download/ux-walkthrough-*.png 2>/dev/null'; then
  adb shell 'ls /sdcard/Download/ux-walkthrough-*.png 2>/dev/null' | tr -d '\r' | while read -r remote; do
    [ -n "$remote" ] || continue
    base=$(basename "$remote")
    dest="$base"
    if [[ "$base" =~ ^ux-walkthrough-f[0-9]+-(.+)$ ]]; then
      dest="${BASH_REMATCH[1]}"
    fi
    adb pull "$remote" "$OUT_DIR/$dest"
  done
fi
echo "UX_WALKTHROUGH_PNG_COUNT=$(find "$OUT_DIR" -maxdepth 1 -name 'flow*.png' 2>/dev/null | wc -l | tr -d ' ')"
echo "UX_WALKTHROUGH_LOGCAT_COUNT=$(find "$OUT_DIR/logcat" -name 'f*-crash.txt' 2>/dev/null | wc -l | tr -d ' ')"
if [ "$gradle_status" -ne 0 ]; then
  mkdir -p app/build/reports/androidTests
  adb logcat -d 2>/dev/null | grep -E "$FILTER_PATTERN" > app/build/reports/androidTests/logcat-filtered.txt || true
  echo '::group::logcat filtered (last 200 lines)'
  tail -n 200 app/build/reports/androidTests/logcat-filtered.txt 2>/dev/null || true
  echo '::endgroup::'
fi
exit "$gradle_status"
