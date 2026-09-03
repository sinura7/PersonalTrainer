#!/bin/sh
# Kotlin parse-level check over a source root.
#
# Without the Android SDK on the classpath every Compose, Room and Gson reference is
# unresolved, and everything cascading from those is expected noise. Parse-level
# diagnostics are not: they mean the file is malformed regardless of classpath.
set -e
ROOT="${1:-app/src/main/java}"

# The Gradle cache is the normal source of these jars. PT_JARS is the fallback for
# environments that cannot reach Google's Maven and so can never run a Gradle build —
# see tools/run-domain-tests.sh, which uses the same directory.
CP=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" "${PT_JARS:-build/test-jars}" \( \
  -name "kotlin-compiler-embeddable-2.0.21.jar" \
  -o -name "kotlin-stdlib-2.0.21.jar" \
  -o -name "kotlinx-coroutines-core-jvm-*.jar" \
  -o -name "trove4j-*.jar" \
  -o -name "annotations-*.jar" \) 2>/dev/null | tr '\n' ':')

case "$CP" in
  *kotlin-compiler-embeddable*) ;;
  *)
    echo "No kotlin-compiler-embeddable jar in the Gradle cache or \$PT_JARS; run a build first, or skip this check."
    exit 0
    ;;
esac

OUT=$(mktemp -d)
ERR=$(mktemp)
trap 'rm -rf "$OUT" "$ERR"' EXIT
# A non-zero exit is EXPECTED: without the Android SDK on the classpath every Compose,
# Room and Gson reference is unresolved. `|| true` keeps `set -e` from treating that as
# fatal — it used to be masked only because this ran inside a pipeline, where POSIX sh
# judges the last command. What we actually care about is in "$ERR", below.
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -nowarn -no-stdlib -no-reflect -d "$OUT" "$ROOT" 2>"$ERR" >/dev/null || true

# The compiler failing to START is not a clean parse. Without this the script printed
# NO SYNTAX ERRORS for a NoClassDefFoundError, a missing main class or an OOM — none of
# which match the parse-diagnostic grep below — and preflight accepted that as a pass.
if grep -qE "^(Error: |Exception in thread |Could not find or load main class)" "$ERR"; then
  echo "SYNTAX CHECK DID NOT RUN — the Kotlin compiler failed to start:"
  sed -n 1,5p "$ERR"
  exit 1
fi

grep -iE "expecting|unexpected token|misplaced|is not allowed here|Name expected|Type expected|syntax" "$ERR" \
  || echo "NO SYNTAX ERRORS"
