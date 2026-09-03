#!/bin/sh
# Kotlin parse-level check over a source root.
#
# Without the Android SDK on the classpath every Compose, Room and Gson reference is
# unresolved, and everything cascading from those is expected noise. Parse-level
# diagnostics are not: they mean the file is malformed regardless of classpath.
set -e
ROOT="${1:-app/src/main/java}"
CATALOG="${2:-gradle/libs.versions.toml}"

catalog_ver() {
    key="$1"
    sed -n "/^\\[versions\\]/,/^\\[/{ s/^${key} = \"\\(.*\\)\"/\\1/p; }" "$CATALOG" 2>/dev/null | head -1
}

KOTLIN="${PT_KOTLIN:-$(catalog_ver kotlin)}"
KOTLIN="${KOTLIN:-2.0.21}"
COROUTINES="${PT_COROUTINES:-$(catalog_ver coroutines)}"

# The Gradle cache is the normal source of these jars. PT_JARS is the fallback for
# environments that cannot reach Google's Maven and so can never run a Gradle build —
# see tools/run-domain-tests.sh, which uses the same directory.
compiler_jar="kotlin-compiler-embeddable-${KOTLIN}.jar"
stdlib_jar="kotlin-stdlib-${KOTLIN}.jar"
coroutines_pat="kotlinx-coroutines-core-jvm-${COROUTINES:-*}.jar"

CP=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" "${PT_JARS:-build/test-jars}" \( \
  -name "$compiler_jar" \
  -o -name "$stdlib_jar" \
  -o -name "$coroutines_pat" \
  -o -name "trove4j-*.jar" \
  -o -name "annotations-*.jar" \) 2>/dev/null | tr '\n' ':')

case "$CP" in
  *kotlin-compiler-embeddable*) ;;
  *)
    echo "No kotlin-compiler-embeddable jar in the Gradle cache or \$PT_JARS; run a build first, or skip this check."
    exit 0
    ;;
esac

if ! command -v java >/dev/null 2>&1; then
  echo "SYNTAX CHECK DID NOT RUN — java is not on PATH"
  exit 1
fi

OUT=$(mktemp -d)
ERR=$(mktemp)
trap 'rm -rf "$OUT" "$ERR"' EXIT
# A non-zero exit is EXPECTED: without the Android SDK on the classpath every Compose,
# Room and Gson reference is unresolved. Capture the status instead of `|| true` so a
# compiler that never started cannot print NO SYNTAX ERRORS.
set +e
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -nowarn -no-stdlib -no-reflect -d "$OUT" "$ROOT" 2>"$ERR" >/dev/null
status=$?
set -e

# The compiler failing to START is not a clean parse. Without this the script printed
# NO SYNTAX ERRORS for a NoClassDefFoundError, a missing main class or an OOM — none of
# which match the parse-diagnostic grep below — and preflight accepted that as a pass.
if grep -qE "^(Error: |Exception in thread |Could not find or load main class)" "$ERR"; then
  echo "SYNTAX CHECK DID NOT RUN — the Kotlin compiler failed to start:"
  sed -n 1,5p "$ERR"
  exit 1
fi

# 0 = parsed clean. 1 = compilation errors (unresolved refs are expected).
# 127 = java not found. Anything else is a failed start, not a clean parse.
if [ "$status" -eq 127 ] || [ "$status" -ge 2 ]; then
  echo "SYNTAX CHECK DID NOT RUN — compiler exited $status"
  sed -n 1,8p "$ERR"
  exit 1
fi

if grep -iE "expecting|unexpected token|misplaced|is not allowed here|Name expected|Type expected|syntax" "$ERR"; then
  exit 0
fi
echo "NO SYNTAX ERRORS"
