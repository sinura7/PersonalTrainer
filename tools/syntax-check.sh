#!/bin/sh
# Kotlin parse-level check over a source root.
#
# Without the Android SDK on the classpath every Compose, Room and Gson reference is
# unresolved, and everything cascading from those is expected noise. Parse-level
# diagnostics are not: they mean the file is malformed regardless of classpath.
set -e
ROOT="${1:-app/src/main/java}"

CP=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" \( \
  -name "kotlin-compiler-embeddable-*.jar" \
  -o -name "kotlin-stdlib-2*.jar" \
  -o -name "kotlinx-coroutines-core-jvm-*.jar" \
  -o -name "trove4j-*.jar" \
  -o -name "annotations-1*.jar" \) 2>/dev/null | tr '\n' ':')

case "$CP" in
  *kotlin-compiler-embeddable*) ;;
  *)
    echo "No kotlin-compiler-embeddable jar in the Gradle cache; run a build first, or skip this check."
    exit 0
    ;;
esac

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -nowarn -no-stdlib -no-reflect -d "$OUT" "$ROOT" 2>&1 >/dev/null \
  | grep -iE "expecting|unexpected token|misplaced|is not allowed here|Name expected|Type expected|syntax" \
  || echo "NO SYNTAX ERRORS"
