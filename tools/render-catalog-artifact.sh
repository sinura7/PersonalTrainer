#!/bin/sh
# Re-render the committed catalog review artifact from CatalogReviewRenderer.
#
# The artifact is golden-file tested (CatalogReviewArtifactTest), so it can never be
# edited by hand — which leaves exactly one supported way to change it: bump the
# catalog, run this, commit both. Each catalog version writes its own file, so a
# batch bump produces a new artifact beside the previous one rather than a rewrite
# whose diff hides what actually changed.
#
# Same jar bootstrap as run-domain-tests.sh, for the same reason: this environment
# has no Android SDK, and domain/ is pure Kotlin so it does not need one.
#
# Usage:  tools/render-catalog-artifact.sh [jar-dir]
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
mkdir -p "$WORK/stub/android/util" "$WORK/main" "$WORK/gen"

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

cat > "$WORK/gen/Main.kt" <<'GEN'
import com.sinura.personaltrainer.domain.CatalogReviewRenderer
import java.io.File

fun main() {
    val name = CatalogReviewRenderer.artifactName()
    val file = File("docs/gameplan/artifacts/$name")
    file.parentFile.mkdirs()
    file.writeText(CatalogReviewRenderer.render())
    println("wrote ${file.path}")
}
GEN

kotlinc() {
  java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"
}

SRC=app/src/main/java/com/sinura/personaltrainer
kotlinc -nowarn -jvm-target 17 -cp "$CP" -d "$WORK/main" \
  "$SRC/domain" "$SRC/util" "$SRC/logging" "$WORK/stub" 2>&1 | grep -v '^warning:' || true

if [ ! -d "$WORK/main/com" ]; then
  echo "FAILED: domain sources did not compile." >&2
  exit 1
fi

kotlinc -nowarn -jvm-target 17 -cp "$CP:$WORK/main" -d "$WORK/gen-out" \
  "$WORK/gen" 2>&1 | grep -v '^warning:' || true

java -cp "$CP:$WORK/main:$WORK/gen-out" MainKt
