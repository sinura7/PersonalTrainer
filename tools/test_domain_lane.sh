#!/bin/sh
# Exercise the domain runner's process contract without recompiling the app:
# native classpaths, spaced paths, compiler failures after partial output,
# empty test discovery, and a failing JUnit process must all be handled honestly.
# The syntax launcher also has to start from relative fallback jars, and must
# distinguish unresolved Android symbols from parse errors and launch failures.
set -eu
cd "$(dirname "$0")/.."
RUNNER="$PWD/tools/run-domain-tests.sh"
SYNTAX="$PWD/tools/syntax-check.sh"
SCRATCH=$(mktemp -d "${TMPDIR:-/tmp}/temper-domain-lane.XXXXXX")
trap 'rm -rf "$SCRATCH"' EXIT
mkdir -p "$SCRATCH/bin" "$SCRATCH/jars with spaces" "$SCRATCH/trace"
for name in gson-fixture.jar kotlin-compiler-embeddable-fixture.jar runtime-marker.jar \
            trove4j-fixture-sources.jar trove4j-fixture-javadoc.jar; do
  : >"$SCRATCH/jars with spaces/$name"
done
case "$(uname -s)" in
  CYGWIN*|MINGW*|MSYS*)
    TEST_CP_SEPARATOR=';'
    native_jars=$(cygpath -am "$SCRATCH/jars with spaces")
    TEST_EXPECTED_RESOURCES=$(cygpath -am "$PWD/app/src/test/resources")
    ;;
  *) TEST_CP_SEPARATOR=':'; native_jars="$SCRATCH/jars with spaces"; TEST_EXPECTED_RESOURCES="$PWD/app/src/test/resources" ;;
esac
TEST_EXPECTED_CP="$native_jars/gson-fixture.jar$TEST_CP_SEPARATOR$native_jars/kotlin-compiler-embeddable-fixture.jar$TEST_CP_SEPARATOR$native_jars/runtime-marker.jar"
TEST_TRACE="$SCRATCH/trace"
case "$TEST_CP_SEPARATOR" in
  ';') TEST_EXPECTED_SYNTAX_CP="$native_jars/kotlin-compiler-embeddable-fixture.jar" ;;
  ':') TEST_EXPECTED_SYNTAX_CP='jars with spaces/kotlin-compiler-embeddable-fixture.jar' ;;
esac
export TEST_CP_SEPARATOR TEST_EXPECTED_CP TEST_TRACE TEST_EXPECTED_RESOURCES TEST_EXPECTED_SYNTAX_CP

cat >"$SCRATCH/bin/java" <<'JAVA'
#!/bin/sh
set -eu
case "$TEST_MODE" in
  syntax-*)
    [ "$1" = -cp ] && [ "$2" = "$TEST_EXPECTED_SYNTAX_CP" ] && \
      [ "$3" = org.jetbrains.kotlin.cli.jvm.K2JVMCompiler ] || {
        echo 'Error: fixture received wrong syntax compiler classpath' >&2; exit 41;
      }
    case "$TEST_MODE" in
      syntax-unresolved) echo 'Fixture.kt:1:1: error: unresolved reference: android' >&2 ;;
      syntax-parse) echo 'Fixture.kt:1:1: error: Expecting a declaration' >&2 ;;
      syntax-launch) echo 'Error: Could not find or load main class org.jetbrains.kotlin.cli.jvm.K2JVMCompiler' >&2 ;;
    esac
    exit 1
    ;;
esac
[ "$1" = -cp ] && [ "$2" = "$TEST_EXPECTED_CP" ] || {
  # JUnit also needs the compiled output directories on its classpath.
  expected="$TEST_EXPECTED_CP$TEST_CP_SEPARATOR$(cat "$TEST_TRACE/main")$TEST_CP_SEPARATOR$(cat "$TEST_TRACE/backup")$TEST_CP_SEPARATOR$(cat "$TEST_TRACE/test")$TEST_CP_SEPARATOR$TEST_EXPECTED_RESOURCES"
  [ "$1" = -cp ] && [ "$2" = "$expected" ] || { echo 'fixture: wrong JVM classpath' >&2; exit 41; }
}
entry="$3"
shift 3
case "$entry" in
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler)
    module=''; output=''; cp=''; friends=''
    while [ "$#" -gt 0 ]; do
      case "$1" in
        -module-name) module="$2"; shift ;;
        -d) output="$2"; shift ;;
        -cp) cp="$2"; shift ;;
        -Xfriend-paths=*) friends="${1#-Xfriend-paths=}" ;;
      esac
      shift
    done
    [ -n "$module" ] && [ -n "$output" ] || exit 42
    case "$module" in
      main) expected="$TEST_EXPECTED_CP" ;;
      backup) expected="$TEST_EXPECTED_CP$TEST_CP_SEPARATOR$(cat "$TEST_TRACE/main")" ;;
      test)
        expected="$TEST_EXPECTED_CP$TEST_CP_SEPARATOR$(cat "$TEST_TRACE/main")$TEST_CP_SEPARATOR$(cat "$TEST_TRACE/backup")"
        [ "$friends" = "$(cat "$TEST_TRACE/main"),$(cat "$TEST_TRACE/backup")" ] || exit 43
        ;;
      *) exit 44 ;;
    esac
    [ "$cp" = "$expected" ] || { echo 'fixture: wrong compiler classpath' >&2; exit 45; }
    printf '%s\n' "$output" >"$TEST_TRACE/$module"
    mkdir -p "$output/com/fixture"
    if [ "$module" = test ] && [ "$TEST_MODE" != empty ]; then
      : >"$output/com/fixture/SmokeTest.class"
    fi
    # Deliberately leave output behind before failing. The runner must trust
    # the exit code rather than mistake that partial output for a good compile.
    [ "$TEST_MODE" != "fail-$module" ] || exit 9
    ;;
  org.junit.runner.JUnitCore)
    : >"$TEST_TRACE/junit-ran"
    # Real JUnit returns success for zero requested classes. Mimic that so
    # the empty-discovery regression proves the runner rejects the empty set.
    if [ "$#" -eq 0 ]; then echo 'fixture: JUnit completed zero tests'; exit 0; fi
    [ "$#" -eq 1 ] && [ "$1" = com.fixture.SmokeTest ] || exit 46
    [ "$TEST_MODE" != fail-junit ] || exit 7
    echo 'fixture: JUnit completed'
    ;;
  *) exit 47 ;;
esac
JAVA
chmod +x "$SCRATCH/bin/java"

check() {
  description="$1"; expectation="$2"; TEST_MODE="$3"
  export TEST_MODE
  rm -f "$TEST_TRACE/main" "$TEST_TRACE/backup" "$TEST_TRACE/test" "$TEST_TRACE/junit-ran"
  if PATH="$SCRATCH/bin:$PATH" sh "$RUNNER" "$SCRATCH/jars with spaces" >"$SCRATCH/output" 2>&1; then
    result=pass
  else
    result=fail
  fi
  if [ "$result" != "$expectation" ]; then
    cat "$SCRATCH/output" >&2
    echo "FAIL $description: expected $expectation, got $result" >&2
    exit 1
  fi
  case "$TEST_MODE" in
    success|fail-junit) [ -f "$TEST_TRACE/junit-ran" ] || { cat "$SCRATCH/output" >&2; exit 1; } ;;
    *) [ ! -f "$TEST_TRACE/junit-ran" ] || { echo 'FAIL JUnit ran after a compile/discovery failure' >&2; exit 1; } ;;
  esac
  echo "ok  $description"
}

check 'native classpaths/friend paths preserve spaces; source archives excluded; test resources included' pass success
check 'domain compiler failure remains a failure after partial output' fail fail-main
check 'backup compiler failure remains a failure after partial output' fail fail-backup
check 'test compiler failure remains a failure after partial output' fail fail-test
check 'zero discovered tests is a failure' fail empty
check 'JUnit failure propagates' fail fail-junit

mkdir -p "$SCRATCH/source"
printf 'val fixture = android.example\n' >"$SCRATCH/source/Fixture.kt"
for TEST_MODE in syntax-unresolved syntax-parse syntax-launch; do
  export TEST_MODE
  if (cd "$SCRATCH" && PATH="$SCRATCH/bin:$PATH" GRADLE_USER_HOME="$SCRATCH/no-cache" \
      PT_JARS='jars with spaces' PT_KOTLIN=fixture PT_COROUTINES=fixture \
      sh "$SYNTAX" "$SCRATCH/source") >"$SCRATCH/syntax-output" 2>&1; then
    syntax_status=0
  else
    syntax_status=$?
  fi
  clean=0
  grep -q '^NO SYNTAX ERRORS$' "$SCRATCH/syntax-output" && clean=1
  case "$TEST_MODE:$syntax_status:$clean" in
    syntax-unresolved:0:1) echo 'ok  syntax launcher accepts unresolved Android symbols with relative spaced fallback jars' ;;
    syntax-parse:0:0) echo 'ok  syntax parse findings never produce a clean result' ;;
    syntax-launch:1:0) echo 'ok  syntax compiler launch failure remains a failure' ;;
    *) cat "$SCRATCH/syntax-output" >&2; echo "FAIL $TEST_MODE (exit=$syntax_status, clean=$clean)" >&2; exit 1 ;;
  esac
done
echo 'test_domain_lane: all assertions passed'
