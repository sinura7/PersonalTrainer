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
set -eu

fail() { echo "ci-instrumented: $*" >&2; exit 1; }

# This suite starts real MainActivity before the workout journeys. A release check
# fetched by an earlier class survives in the shared process/DataStore even after
# a later fixture goes offline. Keep this fresh, synthetic device offline for the
# entire suite; the host still downloads Gradle dependencies normally.
serial=${ANDROID_SERIAL:-emulator-5554}
case "$serial" in emulator-*) port=${serial#emulator-} ;; *) fail 'Refusing a non-emulator serial.' ;; esac
case "$port" in ''|*[!0-9]*) fail 'Invalid emulator serial.' ;; esac
export ANDROID_SERIAL="$serial"
command -v timeout >/dev/null || fail 'A bounded shell timeout is required.'
device_adb() { MSYS_NO_PATHCONV=1 timeout 10 adb -s "$serial" "$@"; }
shell_value() {
  value=$(device_adb shell "$@") || return $?
  printf '%s\n' "$value" | tr -d '\r'
}

# connectedDebugAndroidTest must not discover another attached device. Binding
# adb alone would not be a sufficient guard around a connected Gradle task.
devices=$(timeout 10 adb devices) || fail 'Cannot enumerate devices.'
connected=$(printf '%s\n' "$devices" | tr -d '\r' | awk 'NR > 1 && NF { print $1 " " $2 }')
[ "$connected" = "$serial device" ] || fail 'Require exactly one ready emulator and no other attached device.'
hardware=$(shell_value getprop ro.hardware) || fail 'Cannot read emulator hardware.'
case "$hardware" in ranchu|goldfish) ;; *) fail 'Refusing non-SDK emulator hardware.' ;; esac
fingerprint=$(shell_value getprop ro.build.fingerprint) || fail 'Cannot read emulator fingerprint.'
case "$fingerprint" in Android/sdk_*) ;; *) fail 'Refusing a non-SDK emulator fingerprint.' ;; esac
avd=$(shell_value getprop ro.kernel.qemu.avd_name) || fail 'Cannot read AVD name.'
if [ -z "$avd" ]; then avd=$(shell_value getprop ro.boot.qemu.avd_name) || fail 'Cannot read AVD name.'; fi
api=$(shell_value getprop ro.build.version.sdk) || fail 'Cannot read emulator API.'
if [ "${GITHUB_ACTIONS:-false}" = true ]; then
  [ "$avd/$api" = test/29 ] || fail 'Hosted fixtures require the disposable CI test/API29 AVD.'
else
  [ "${TEMPER_DISPOSABLE_AVD:-}" = "$avd" ] || fail 'Local runs require TEMPER_DISPOSABLE_AVD set to the owned AVD name.'
  case "$avd/$api" in
    temper-tests-api26/26|temper-tests-api29/29|temper-tests-api36/36) ;;
    *) fail 'Local fixtures require a named repository AVD with its matching API.' ;;
  esac
fi
packages=$(shell_value pm list packages) || fail 'Cannot establish fresh package state.'
printf '%s\n' "$packages" | grep -qx 'package:android' || fail 'Package inventory is unavailable; leaving device unchanged.'
if printf '%s\n' "$packages" | grep -Eq '^package:com\.sinura\.personaltrainer(\.|$)'; then
  fail 'Require a fresh emulator without Temper packages; never clear an existing app to run this suite.'
fi
# The task below is deliberately fixed to Debug. Its instrumentation asserts the
# exact com.sinura.personaltrainer.debug target; this script installs no APK itself.

network_posture() {
  snapshot=$(shell_value dumpsys connectivity) || return $?
  active=$(printf '%s\n' "$snapshot" | sed -n 's/^Active default network: //p')
  case "$active" in
    none) printf 'none\n' ;;
    ''|*[!0-9]*) echo 'ci-instrumented: Unrecognized active-network snapshot.' >&2; return 1 ;;
    *) printf 'active\n' ;;
  esac
}
wifi_before=$(shell_value settings get global wifi_on) || fail 'Cannot capture WiFi state.'
data_before=$(shell_value settings get global mobile_data) || fail 'Cannot capture mobile-data state.'
case "$wifi_before/$data_before" in 0/0|0/1|1/0|1/1) ;; *) fail 'Unsupported original network settings; leaving device unchanged.' ;; esac
network_before=$(network_posture) || fail 'Cannot capture original connectivity.'
echo "CI_SYNTHETIC_NETWORK serial=$serial avd=$avd api=$api target=com.sinura.personaltrainer.debug originalWifi=$wifi_before originalData=$data_before originalDefault=$network_before"

await_network() {
  wanted_wifi=$1 wanted_data=$2 wanted_network=$3
  deadline=$(( $(date +%s) + 30 ))
  while :; do
    actual_wifi=$(shell_value settings get global wifi_on) || return $?
    actual_data=$(shell_value settings get global mobile_data) || return $?
    actual_network=$(network_posture) || return $?
    if [ "$actual_wifi/$actual_data/$actual_network" = "$wanted_wifi/$wanted_data/$wanted_network" ]; then return 0; fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
      echo "ci-instrumented: Network state did not settle: expected=$wanted_wifi/$wanted_data/$wanted_network actual=$actual_wifi/$actual_data/$actual_network" >&2
      return 1
    fi
    sleep 1
  done
}
restore_network() {
  original_status=$?
  trap - 0 HUP INT TERM
  restore_failed=0
  data_action=disable; [ "$data_before" = 0 ] || data_action=enable
  wifi_action=disable; [ "$wifi_before" = 0 ] || wifi_action=enable
  device_adb shell svc data "$data_action" || restore_failed=1
  device_adb shell svc wifi "$wifi_action" || restore_failed=1
  await_network "$wifi_before" "$data_before" "$network_before" || restore_failed=1
  if [ "$restore_failed" = 0 ]; then
    echo "CI_SYNTHETIC_NETWORK_RESTORED wifi=$wifi_before data=$data_before default=$network_before"
  else
    echo "ci-instrumented: Network restoration failed (original exit=$original_status)." >&2
    [ "$original_status" -ne 0 ] || original_status=1
  fi
  exit "$original_status"
}
# Install rollback before the first change, including a setup command that partly
# changes state and then reports failure. Signals take the same checked exit path.
trap restore_network 0
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
device_adb shell svc wifi disable
device_adb shell svc data disable
await_network 0 0 none
echo 'CI_SYNTHETIC_NETWORK_OFFLINE verified before Gradle/first app launch'

status=0
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.syntheticFixture=true --stacktrace || status=$?
if [ "$status" -ne 0 ]; then
  # Diagnostic failures must not replace the original Gradle exit status.
  set +e
  # Retain the full log as well as the original concise job-log failure excerpt.
  mkdir -p app/build/reports/androidTests
  device_adb logcat -d > app/build/reports/androidTests/logcat.txt || true
  echo '::group::logcat (last 400 matching lines)'
  grep -E 'PT/|TestRunner|AndroidJUnitRunner|Exception|Error' app/build/reports/androidTests/logcat.txt | tail -n 400 || true
  echo '::endgroup::'
  # Native-window captures and layout diagnostics write PNGs to the device
  # (NativeArtifacts). They are pulled into the uploaded report directory,
  # which keeps its old `goldens` name, AND printed as base64, because
  # the artifact is not reachable from every network. To rebuild one from the
  # raw log, whose lines carry a timestamp prefix:
  #
  #   sed -n '/::group::png NAME/,/::endgroup::/p' raw.log |
  #     sed '1d;$d' | cut -d' ' -f2- | tr -d ' ' | base64 -d > NAME
  #
  # `tr -d '\r'` because adb's shell terminates lines with CR on some images.
  dir=app/build/reports/androidTests/goldens
  mkdir -p "$dir"
  device_adb shell 'ls /sdcard/Download/*.png 2>/dev/null' | tr -d '\r' | while read -r png; do
    [ -n "$png" ] || continue
    name=$(basename "$png")
    if device_adb pull "$png" "$dir/$name" >/dev/null 2>&1; then
      echo "::group::png $name ($(wc -c < "$dir/$name") bytes, base64)"
      base64 "$dir/$name"
      echo '::endgroup::'
    else
      echo "::warning::could not pull $png off the device"
    fi
  done
fi
exit "$status"
