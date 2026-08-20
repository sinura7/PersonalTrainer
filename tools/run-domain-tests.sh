#!/bin/sh
# Run the pure-Kotlin domain unit tests on a plain JVM, without the Android SDK.
#
# `./gradlew test` is the real gate and stays the source of truth. This exists for
# environments that cannot reach Google's Maven or install the Android SDK, where
# the alternative is shipping domain changes with no executed tests at all.
#
# It works because of a rule this project already keeps: domain/ is pure Kotlin.
# Its only outside references are util/ and logging/, and AppLog deliberately
# falls back to stdout when the Android runtime is absent — so a three-line
# android.util.Log stub is enough to link it.
#
# Usage:  tools/run-domain-tests.sh [jar-dir]
# jar-dir defaults to $PT_JARS, then to build/test-jars. Required jars:
#   kotlin-compiler-embeddable, kotlin-stdlib, kotlinx-coroutines-core-jvm,
#   junit, hamcrest-core, trove4j, annotations
set -e

JARS="${1:-${PT_JARS:-build/test-jars}}"
if [ ! -d "$JARS" ]; then
  echo "No jar directory at '$JARS'. Pass one, or set PT_JARS." >&2
  exit 2
fi

CP=$(find "$JARS" -name '*.jar' | tr '\n' ':')
case "$CP" in
  *kotlin-compiler-embeddable*) ;;
  *) echo "kotlin-compiler-embeddable jar not found in '$JARS'." >&2; exit 2 ;;
esac

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/stub/android/util" "$WORK/main" "$WORK/test"

# Compile-time only: AppLog's androidSink references these two symbols, then
# catches the Throwable they raise off-device.
cat > "$WORK/stub/android/util/Log.kt" <<'STUB'
package android.util

object Log {
    @JvmStatic
    fun println(priority: Int, tag: String, message: String): Int =
        throw UnsupportedOperationException("android.util.Log is not available on the JVM")

    @JvmStatic
    fun getStackTraceString(error: Throwable?): String = error?.toString().orEmpty()
}
STUB

kotlinc() {
  java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"
}

SRC=app/src/main/java/com/sinura/personaltrainer
echo "Compiling domain sources..."
kotlinc -nowarn -jvm-target 17 -cp "$CP" -d "$WORK/main" \
  "$SRC/domain" "$SRC/util" "$SRC/logging" "$WORK/stub" 2>&1 | grep -v '^warning:' || true

if [ ! -d "$WORK/main/com" ]; then
  echo "FAILED: domain sources did not compile." >&2
  exit 1
fi

echo "Compiling domain tests..."
# -Xfriend-paths mirrors Gradle's associated test compilation: without it the tests
# cannot see `internal` declarations they legitimately exercise.
kotlinc -nowarn -jvm-target 17 -cp "$CP:$WORK/main" -Xfriend-paths="$WORK/main" -d "$WORK/test" \
  app/src/test/java/com/sinura/personaltrainer/domain 2>&1 | grep -v '^warning:' || true

if [ ! -d "$WORK/test/com" ]; then
  echo "FAILED: domain tests did not compile." >&2
  exit 1
fi

CLASSES=$(cd "$WORK/test" && find . -name '*Test.class' ! -name '*$*' \
  | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
COUNT=$(echo "$CLASSES" | wc -w | tr -d ' ')
echo "Running $COUNT test classes..."
# shellcheck disable=SC2086
java -cp "$CP:$WORK/main:$WORK/test" org.junit.runner.JUnitCore $CLASSES
