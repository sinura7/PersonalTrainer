#!/bin/sh
# Runs the instrumented suite on the CI emulator and, on failure, dumps the
# device log before the emulator is torn down.
#
# reactivecircus/android-emulator-runner runs every line of its `script:` as a
# separate `sh -c`, so anything more than one command has to live in a file.
# The runner kills the emulator as soon as this script returns, which is why
# logcat is read here and not in a later step: by then there is no device.
# The Gradle failure line alone never says why a test failed; the device log
# has the caught exceptions, the runner's own messages and anything the app
# logged under its `PT/` tags.
set -u

./gradlew connectedDebugAndroidTest --stacktrace
status=$?
if [ "$status" -ne 0 ]; then
  echo '::group::logcat (last 400 matching lines)'
  adb logcat -d | grep -E 'PT/|TestRunner|AndroidJUnitRunner|Exception|Error' | tail -n 400
  echo '::endgroup::'
  # A golden mismatch writes the actual and diff PNGs to the device. They are
  # pulled into the uploaded report directory AND printed as base64, because
  # the artifact is not reachable from every network. To rebuild one from the
  # raw log, whose lines carry a timestamp prefix:
  #
  #   sed -n '/::group::png NAME/,/::endgroup::/p' raw.log |
  #     sed '1d;$d' | cut -d' ' -f2- | tr -d ' ' | base64 -d > NAME
  #
  # `tr -d '\r'` because adb's shell terminates lines with CR on some images.
  dir=app/build/reports/androidTests/goldens
  mkdir -p "$dir"
  adb shell 'ls /sdcard/Download/*.png 2>/dev/null' | tr -d '\r' | while read -r png; do
    [ -n "$png" ] || continue
    name=$(basename "$png")
    if adb pull "$png" "$dir/$name" >/dev/null 2>&1; then
      echo "::group::png $name ($(wc -c < "$dir/$name") bytes, base64)"
      base64 "$dir/$name"
      echo '::endgroup::'
    else
      echo "::warning::could not pull $png off the device"
    fi
  done
fi
exit "$status"
