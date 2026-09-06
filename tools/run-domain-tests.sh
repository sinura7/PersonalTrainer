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
#            against test/.../data/backup/. Files are named one by one because the
#            directory also holds DriveAuthClient (Play Services) and NetworkChecker
#            (Android connectivity), which cannot link here. DriveAboutJson and
#            DriveFolderJson are Gson-only parsers; DriveHttp and DriveRestClient are
#            java.net plus Gson, and the client takes its transport as a constructor
#            argument so its paging runs against a fake without a socket.
#            The lane exists because a silent backup defect
#            costs the owner their entire training history, and four test files
#            sat here never once executed.
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
mkdir -p "$WORK/stub/android/util" "$WORK/stub/android/os" "$WORK/stub/android/content" \
  "$WORK/stub/android/provider" "$WORK/main" "$WORK/test"

# Stubs, compile-time only. Every one of these throws if it is ever actually called: the code
# under test must not depend on Android behaviour, and a stub that quietly returned a plausible
# value would let it start to without anything noticing. AppLog is the exception it was written
# for — it references Log, catches the Throwable off-device, and falls back to stdout.
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

# Referenced only as a default argument in RestTimerRehydrator.rehydrate, which every caller
# and every test overrides — the whole point of that parameter existing.
cat > "$WORK/stub/android/os/SystemClock.kt" <<'STUB'
package android.os

object SystemClock {
    @JvmStatic
    fun elapsedRealtime(): Long =
        throw UnsupportedOperationException("android.os.SystemClock is not available on the JVM")
}
STUB

# The SharedPreferences-backed persistence class has to compile for the pure rehydration rules
# in the same file to be reachable. Nothing constructs it here.
cat > "$WORK/stub/android/content/Context.kt" <<'STUB'
package android.content

abstract class Context {
    open val applicationContext: Context get() = this
    open val contentResolver: ContentResolver
        get() = throw UnsupportedOperationException("ContentResolver is not available on the JVM")
    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences

    companion object {
        const val MODE_PRIVATE: Int = 0
    }
}

abstract class ContentResolver

interface SharedPreferences {
    fun contains(key: String): Boolean
    fun getLong(key: String, defValue: Long): Long
    fun getInt(key: String, defValue: Int): Int
    fun getString(key: String, defValue: String?): String?
    fun edit(): Editor

    interface Editor {
        fun putLong(key: String, value: Long): Editor
        fun putInt(key: String, value: Int): Editor
        fun putString(key: String, value: String?): Editor
        fun clear(): Editor
        fun apply()
        /** Synchronous flush. Rest-timer persistence uses this so the alarm cannot outrun disk. */
        fun commit(): Boolean
    }
}
STUB

# BootSession catches every exception and reports UNKNOWN, so a thrown stub keeps the JVM
# lane honest: the boot-count comparison only ever runs where a stored stamp met a real one.
cat > "$WORK/stub/android/provider/Settings.kt" <<'STUB'
package android.provider

import android.content.ContentResolver

object Settings {
    object Global {
        const val BOOT_COUNT = "boot_count"

        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun getInt(resolver: ContentResolver, name: String): Int =
            throw UnsupportedOperationException("android.provider.Settings is not available on the JVM")
    }
}
STUB

kotlinc() {
  java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"
}

SRC=app/src/main/java/com/sinura/personaltrainer
TESTS=app/src/test/java/com/sinura/personaltrainer

# Files outside domain/util/logging that carry no Android imports and so link on a plain JVM.
# Named one by one rather than by directory because each package they live in also holds files
# that do not — RestTimerService, StartTrainingDay, and so on. Adding a file here is what makes
# its test directory below runnable; the two lists move together.
EXTRA_MAIN="$SRC/workout/WorkoutDraftCache.kt $SRC/workout/WorkoutDraftRecovery.kt \
            $SRC/timer/RestTimerStore.kt $SRC/timer/RestTimerStatePersistence.kt \
            $SRC/timer/RestAlarmPlan.kt $SRC/timer/CardioTimerPersistence.kt \
            $SRC/timer/BootSession.kt \
            $SRC/diagnostics/DiagnosticRing.kt $SRC/diagnostics/DiagnosticRedaction.kt \
            $SRC/diagnostics/LastCrashStore.kt"
# Workout and timer tests are named: StartTrainingDayTest, WorkoutLifecycleUseCasesTest,
# and RestTimerStatePersistenceTest are Robolectric and cannot compile against these
# stubs. Keep them out of this lane; Gradle still runs them.
# FrozenTime is the one testutil helper with no Android imports; OccurrenceGeneratorTest
# needs it, and leaving it out is how the whole domain lane stopped compiling after J4.
# diagnostics/ tests are passed whole: DiagnosticMetadata is the one Android file in the
# main package and is left out above, and the test directory has no Android imports.
EXTRA_TESTS="$TESTS/util \
             $TESTS/testutil/FrozenTime.kt \
             $TESTS/workout/WorkoutDraftCacheTest.kt \
             $TESTS/workout/WorkoutDraftRecoveryTest.kt \
             $TESTS/timer/RestTimerStoreTest.kt \
             $TESTS/timer/RestTimerRehydratorTest.kt \
             $TESTS/timer/RestAlarmPlanTest.kt \
             $TESTS/timer/CardioElapsedTest.kt \
             $TESTS/diagnostics"

echo "Compiling domain sources..."
# shellcheck disable=SC2086
kotlinc -nowarn -jvm-target 17 -module-name main -cp "$CP" -d "$WORK/main" \
  "$SRC/domain" "$SRC/util" "$SRC/logging" "$WORK/stub" $EXTRA_MAIN 2>&1 | grep -v '^warning:' || true

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
    for f in BackupDocument.kt BackupJson.kt BackupValidator.kt AuthoredInventory.kt \
             SafetySnapshot.kt SafetySnapshotStore.kt RestoreJournal.kt RestoreJournalStore.kt \
             RestoreWitness.kt \
             BackupEnvelope.kt BackupScaleBudget.kt DriveAboutJson.kt DriveFolderJson.kt \
             DriveHttp.kt DriveRestClient.kt; do
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
TEST_SRC="$TESTS/domain $EXTRA_TESTS"
[ -n "$BACKUP_SRC" ] && TEST_SRC="$TEST_SRC $TESTS/data/backup"
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
