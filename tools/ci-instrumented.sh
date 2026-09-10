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
fi
exit "$status"
