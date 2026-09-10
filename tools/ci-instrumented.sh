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
  # A golden mismatch writes the actual and diff PNGs to the device. The
  # report artifact is not reachable from every network, so they ride the
  # log as base64 too: decode with `base64 -d` to see, or to re-record.
  for png in $(adb shell 'ls /sdcard/Download/*.png 2>/dev/null'); do
    name=$(basename "$png")
    if adb pull "$png" "$name" >/dev/null 2>&1; then
      echo "::group::png $name ($(wc -c < "$name") bytes, base64)"
      base64 "$name"
      echo '::endgroup::'
    fi
  done
fi
exit "$status"
