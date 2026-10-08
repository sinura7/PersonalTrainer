#!/bin/sh
# Failure-injection checks for the disposable CI device boundary. These exercise
# the real runner script with mock adb/Gradle; they never contact a device or build.
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
ci_script="$script_dir/ci-instrumented.sh"
temp_parent=$(CDPATH= cd -- "${TMPDIR:-/tmp}" && pwd -P)
test_root=$(mktemp -d "$temp_parent/temper-ci-test.XXXXXX")
cleanup() {
  # Both creation and deletion use this shell, and the resolved target must stay
  # inside the exact temporary parent/name that this harness created.
  resolved=$(CDPATH= cd -- "$test_root" && pwd -P) || return 1
  [ "$resolved" = "$test_root" ] || return 1
  case "$resolved" in "$temp_parent"/temper-ci-test.*) rm -rf -- "$resolved" ;; *) return 1 ;; esac
}
trap cleanup 0
mkdir -p "$test_root/bin"
cat > "$test_root/bin/adb" <<'MOCK'
#!/bin/sh
set -eu
printf 'adb %s\n' "$*" >> "$MOCK_ROOT/calls"
if [ "$1" = devices ]; then
  printf 'List of devices attached\nemulator-5554\tdevice\n'
  if [ -f "$MOCK_ROOT/second-device" ]; then printf 'phone-serial\tdevice\n'; fi
  exit 0
fi
[ "$1" = -s ] && [ "$2" = emulator-5554 ] || exit 90
shift 2
case "$*" in
  'shell getprop ro.hardware') cat "$MOCK_ROOT/hardware" ;;
  'shell getprop ro.build.fingerprint') cat "$MOCK_ROOT/fingerprint" ;;
  'shell getprop ro.kernel.qemu.avd_name'|'shell getprop ro.boot.qemu.avd_name') cat "$MOCK_ROOT/avd" ;;
  'shell getprop ro.build.version.sdk') cat "$MOCK_ROOT/api" ;;
  'shell pm list packages') cat "$MOCK_ROOT/packages" ;;
  'shell settings get global wifi_on') cat "$MOCK_ROOT/wifi" ;;
  'shell settings get global mobile_data') cat "$MOCK_ROOT/data" ;;
  'shell dumpsys connectivity')
    if [ -f "$MOCK_ROOT/unknown-connectivity" ]; then printf 'Unrecognized snapshot\n'; exit 0; fi
    if [ -f "$MOCK_ROOT/restore-connectivity-failure" ] && [ -f "$MOCK_ROOT/gradle-called" ]; then
      printf 'Active default network: none\n'
    elif [ -f "$MOCK_ROOT/stays-online" ] || [ "$(cat "$MOCK_ROOT/wifi")/$(cat "$MOCK_ROOT/data")" != 0/0 ]; then
      printf 'Active default network: 100\n'
    else
      printf 'Active default network: none\n'
    fi
    # Internet request records remain even offline; they are not active networks.
    printf 'Network Requests:\n  NetworkRequest [ Capabilities: INTERNET ]\n'
    ;;
  'shell svc wifi disable') printf '0\n' > "$MOCK_ROOT/wifi" ;;
  'shell svc data disable')
    printf '0\n' > "$MOCK_ROOT/data"
    if [ -f "$MOCK_ROOT/setup-failure" ] && [ ! -f "$MOCK_ROOT/setup-failed-once" ]; then
      touch "$MOCK_ROOT/setup-failed-once"
      exit 13
    fi
    ;;
  'shell svc wifi enable')
    if [ ! -f "$MOCK_ROOT/restore-failure" ] || [ ! -f "$MOCK_ROOT/gradle-called" ]; then printf '1\n' > "$MOCK_ROOT/wifi"; fi
    ;;
  'shell svc data enable') printf '1\n' > "$MOCK_ROOT/data" ;;
  'logcat -d') printf 'TestRunner: synthetic failure evidence\n' ;;
  'shell ls /sdcard/Download/*.png 2>/dev/null') ;;
  *) printf 'Unexpected mock adb call: %s\n' "$*" >&2; exit 91 ;;
esac
MOCK
cat > "$test_root/bin/date" <<'MOCK'
#!/bin/sh
set -eu
[ "$1" = +%s ] || exit 92
tick=100
if [ -f "$MOCK_ROOT/tick" ]; then tick=$(cat "$MOCK_ROOT/tick"); fi
tick=$((tick + 10))
printf '%s\n' "$tick" > "$MOCK_ROOT/tick"
printf '%s\n' "$tick"
MOCK
cat > "$test_root/bin/sleep" <<'MOCK'
#!/bin/sh
exit 0
MOCK
cat > "$test_root/gradlew" <<'MOCK'
#!/bin/sh
set -eu
printf 'gradle %s\n' "$*" >> "$MOCK_ROOT/calls"
[ "$*" = 'connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.syntheticFixture=true --stacktrace' ] || exit 93
[ "$(cat "$MOCK_ROOT/wifi")/$(cat "$MOCK_ROOT/data")" = 0/0 ] || exit 94
touch "$MOCK_ROOT/gradle-called"
if [ -f "$MOCK_ROOT/signal" ]; then kill -TERM "$PPID"; exit 0; fi
cat "$MOCK_ROOT/gradle-output"
exit "$(cat "$MOCK_ROOT/gradle-status")"
MOCK
chmod +x "$test_root/bin/adb" "$test_root/bin/date" "$test_root/bin/sleep" "$test_root/gradlew"

case_count=0
start_case() {
  case_name=$1
  case_root="$test_root/$case_name"
  mkdir -p "$case_root/project"
  cp "$test_root/gradlew" "$case_root/project/gradlew"
  printf 'ranchu\n' > "$case_root/hardware"
  printf 'Android/sdk_phone_x86_64/test:10/sdk:test-keys\n' > "$case_root/fingerprint"
  printf 'test\n' > "$case_root/avd"
  printf '29\n' > "$case_root/api"
  printf '1\n' > "$case_root/wifi"
  printf '1\n' > "$case_root/data"
  printf '0\n' > "$case_root/gradle-status"
  printf 'package:android\n' > "$case_root/packages"
  : > "$case_root/calls"
  : > "$case_root/gradle-output"
  hosted=true local_opt_in= serial=emulator-5554
}
run_case() {
  expected=$1
  actual=0
  (
    cd "$case_root/project"
    PATH="$test_root/bin:$PATH" MOCK_ROOT="$case_root" GITHUB_ACTIONS="$hosted" \
      TEMPER_DISPOSABLE_AVD="$local_opt_in" ANDROID_SERIAL="$serial" sh "$ci_script"
  ) > "$case_root/output" 2>&1 || actual=$?
  if [ "$actual" != "$expected" ]; then
    cat "$case_root/output" >&2
    echo "$case_name: expected exit $expected, got $actual" >&2
    exit 1
  fi
  case_count=$((case_count + 1))
}
assert_no_mutation() {
  if grep -Eq 'shell svc |^gradle ' "$case_root/calls"; then cat "$case_root/calls" >&2; exit 1; fi
}
assert_restored() {
  [ "$(cat "$case_root/wifi")/$(cat "$case_root/data")" = "$1/$2" ]
  grep -q 'CI_SYNTHETIC_NETWORK_RESTORED' "$case_root/output"
}

start_case physical_serial; serial=phone-serial; run_case 1; assert_no_mutation
start_case physical_hardware; printf 'qcom\n' > "$case_root/hardware"; run_case 1; assert_no_mutation
start_case non_sdk_fingerprint; printf 'vendor/phone/build\n' > "$case_root/fingerprint"; run_case 1; assert_no_mutation
start_case second_device; touch "$case_root/second-device"; run_case 1; assert_no_mutation
start_case unknown_ci_avd; printf 'personal-avd\n' > "$case_root/avd"; run_case 1; assert_no_mutation
start_case local_without_opt_in; hosted=false; printf 'temper-tests-api29\n' > "$case_root/avd"; run_case 1; assert_no_mutation
start_case unrelated_local_opt_in; hosted=false local_opt_in=personal-avd; printf 'personal-avd\n' > "$case_root/avd"; run_case 1; assert_no_mutation
start_case existing_debug; printf 'package:com.sinura.personaltrainer.debug\n' >> "$case_root/packages"; run_case 1; assert_no_mutation
start_case existing_release; printf 'package:com.sinura.personaltrainer\n' >> "$case_root/packages"; run_case 1; assert_no_mutation
start_case unreadable_packages; printf 'Package manager unavailable\n' > "$case_root/packages"; run_case 1; assert_no_mutation
start_case unknown_setting; printf '2\n' > "$case_root/wifi"; run_case 1; assert_no_mutation
start_case unreadable_connectivity; touch "$case_root/unknown-connectivity"; run_case 1; assert_no_mutation

start_case fresh_ci_success; run_case 0; assert_restored 1 1
grep -q 'CI_SYNTHETIC_NETWORK_OFFLINE verified before Gradle' "$case_root/output"
test -f "$case_root/gradle-called"
start_case local_opt_in_success; hosted=false local_opt_in=temper-tests-api29; printf 'temper-tests-api29\n' > "$case_root/avd"; run_case 0; assert_restored 1 1
start_case preserve_mixed_original; printf '0\n' > "$case_root/wifi"; run_case 0; assert_restored 0 1
start_case preserve_original_offline; printf '0\n' > "$case_root/wifi"; printf '0\n' > "$case_root/data"; run_case 0; assert_restored 0 0
start_case partial_setup_failure; touch "$case_root/setup-failure"; run_case 13; assert_restored 1 1
test ! -f "$case_root/gradle-called"
start_case false_offline; touch "$case_root/stays-online"; run_case 1; assert_restored 1 1
test ! -f "$case_root/gradle-called"
start_case failing_gradle; printf '7\n' > "$case_root/gradle-status"; run_case 7; assert_restored 1 1
grep -q 'TestRunner: synthetic failure evidence' "$case_root/project/app/build/reports/androidTests/logcat.txt"
start_case restore_false_success; touch "$case_root/restore-failure"; run_case 1
grep -q 'Network restoration failed' "$case_root/output"
start_case restore_connectivity_failure; touch "$case_root/restore-connectivity-failure"; run_case 1
grep -q 'Network restoration failed' "$case_root/output"
start_case original_failure_survives_restore_failure; touch "$case_root/restore-failure"; printf '7\n' > "$case_root/gradle-status"; run_case 7
grep -q 'Network restoration failed (original exit=7)' "$case_root/output"
start_case interrupted_gradle; touch "$case_root/signal"; run_case 143; assert_restored 1 1

printf 'ci-instrumented shell checks: %s passed (mock adb/Gradle; no device/build).\n' "$case_count"
