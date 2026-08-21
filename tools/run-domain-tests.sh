#!/bin/sh
# Run the pure-Kotlin unit tests on a plain JVM, without the Android SDK.
#
# `./gradlew test` is the real gate and stays the source of truth. This exists for
# environments that cannot reach Google's Maven or install the Android SDK, where
# the alternative is shipping changes with no executed tests at all.
#
# It works because of a rule this project already keeps: domain/ is pure Kotlin.
# Its only outside references are util/ and logging/, and AppLog deliberately
# falls back to stdout when the Android runtime is absent — so a three-line
# android.util.Log stub is enough to link it.
#
# Two lanes:
#   domain — always. domain/, util/, logging/ against test/.../domain/.
#   backup — when a Gson jar is present. BackupDocument/BackupJson/BackupValidator
#            against test/.../data/backup/. These three files are the only ones in
#            data/backup/ with no Android imports; the Drive clients are excluded
#            by name rather than by directory for exactly that reason. The lane
#            exists because a silent backup defect costs the owner their entire
#            training history, and four test files sat here never once executed.
#
# Usage:  tools/run-domain-tests.sh [jar-dir]
# jar-dir defaults to $PT_JARS, then to build/test-jars. Required jars:
#   kotlin-compiler-embeddable, kotlin-stdlib, kotlinx-coroutines-core-jvm,
#   junit, hamcrest-core, trove4j, annotations
# Optional: gson (enables the backup lane; without it that lane is skipped aloud).
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
kotlinc -nowarn -jvm-target 17 -module-name main -cp "$CP" -d "$WORK/main" \
  "$SRC/domain" "$SRC/util" "$SRC/logging" "$WORK/stub" 2>&1 | grep -v '^warning:' || true

if [ ! -d "$WORK/main/com" ]; then
  echo "FAILED: domain sources did not compile." >&2
  exit 1
fi

# --- backup lane -------------------------------------------------------------
# Opt-in on the jar being there. A missing Gson must not fail the domain lane,
# but it must be said out loud: a silent skip is how a lane stops running and
# nobody notices for three phases.
BACKUP_SRC=""
case "$CP" in
  *gson*)
    BACKUP="app/src/main/java/com/sinura/personaltrainer/data/backup"
    for f in BackupDocument.kt BackupJson.kt BackupValidator.kt; do
      [ -f "$BACKUP/$f" ] || { echo "FAILED: $BACKUP/$f is missing." >&2; exit 1; }
      BACKUP_SRC="$BACKUP_SRC $BACKUP/$f"
    done
    ;;
  *) echo "NOTE: no gson jar on the classpath — backup lane skipped." ;;
esac

# Its own output directory, and its own module name. Compiling a second time into
# $WORK/main would overwrite META-INF/main.kotlin_module — the index the compiler
# reads to find top-level functions — and every `decideStart`/`toWeightLabel` in
# the domain tests would stop resolving. Separate directories keep both indexes.
MAIN_CP="$WORK/main"
FRIENDS="$WORK/main"
if [ -n "$BACKUP_SRC" ]; then
  echo "Compiling backup sources..."
  mkdir -p "$WORK/backup"
  # shellcheck disable=SC2086
  kotlinc -nowarn -jvm-target 17 -module-name backup -cp "$CP:$WORK/main" -d "$WORK/backup" \
    $BACKUP_SRC 2>&1 | grep -v '^warning:' || true
  if [ ! -d "$WORK/backup/com" ]; then
    echo "FAILED: backup sources did not compile." >&2
    exit 1
  fi
  MAIN_CP="$WORK/main:$WORK/backup"
  # -Xfriend-paths is comma-separated; -cp is colon-separated. Not the same list.
  FRIENDS="$WORK/main,$WORK/backup"
fi

echo "Compiling tests..."
TEST_SRC="app/src/test/java/com/sinura/personaltrainer/domain"
[ -n "$BACKUP_SRC" ] && TEST_SRC="$TEST_SRC app/src/test/java/com/sinura/personaltrainer/data/backup"
# -Xfriend-paths mirrors Gradle's associated test compilation: without it the tests
# cannot see `internal` declarations they legitimately exercise.
# shellcheck disable=SC2086
kotlinc -nowarn -jvm-target 17 -module-name test -cp "$CP:$MAIN_CP" -Xfriend-paths="$FRIENDS" -d "$WORK/test" \
  $TEST_SRC 2>&1 | grep -v '^warning:' || true

if [ ! -d "$WORK/test/com" ]; then
  echo "FAILED: tests did not compile." >&2
  exit 1
fi

CLASSES=$(cd "$WORK/test" && find . -name '*Test.class' ! -name '*$*' \
  | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort)
COUNT=$(echo "$CLASSES" | wc -w | tr -d ' ')
echo "Running $COUNT test classes..."
# shellcheck disable=SC2086
java -cp "$CP:$MAIN_CP:$WORK/test" org.junit.runner.JUnitCore $CLASSES
