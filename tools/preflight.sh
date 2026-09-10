#!/bin/sh
# tools/preflight.sh — the mechanical half of every phase's definition of done.
#
# Runs the static checks, then the JVM tests, and exits nonzero on the
# first failure. Run it before every push. It is a pre-flight, not a substitute
# for `./gradlew testDebugUnitTest` + `assembleDebug` — the Robolectric and
# instrumented tests only run under Gradle (see docs/DEVELOPMENT.md).
#
# The tests need a directory of seven jars, plus Gson to enable the backup lane
# (see tools/run-domain-tests.sh header). If $PT_JARS / build/test-jars is
# absent, this script assembles it by symlinking jars found in the Gradle module
# cache or a Gradle distribution's lib/ directory. If no jars can be found
# anywhere, it falls back to `./gradlew testDebugUnitTest`, and fails if Gradle
# cannot run either.
set -u
cd "$(dirname "$0")/.." || exit 2

fail() { echo "preflight: FAIL — $*" >&2; exit 1; }
step() { printf '\n== %s\n' "$*"; }

# --- jar bootstrap (needed by syntax-check.sh and run-domain-tests.sh) --------
JARS="${PT_JARS:-build/test-jars}"
CATALOG=gradle/libs.versions.toml

catalog_ver() {
    key="$1"
    sed -n "/^\\[versions\\]/,/^\\[/{ s/^${key} = \"\\(.*\\)\"/\\1/p; }" "$CATALOG" | head -1
}

KOTLIN="$(catalog_ver kotlin)"
COROUTINES="$(catalog_ver coroutines)"
JUNIT="$(catalog_ver junit)"
GSON="$(catalog_ver gson)"
KOTLIN="${KOTLIN:-2.0.21}"
export PT_KOTLIN="$KOTLIN"
export PT_COROUTINES="$COROUTINES"

bootstrap_jars() {
    dest="$1"
    roots=""
    for r in "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" \
             "${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists" \
             /opt/gradle-*/lib; do
        [ -d "$r" ] && roots="$roots $r"
    done
    g="$(command -v gradle || true)"
    if [ -n "$g" ]; then
        glib="$(cd "$(dirname "$g")/.." 2>/dev/null && pwd)/lib"
        [ -d "$glib" ] && roots="$roots $glib"
    fi
    [ -n "$roots" ] || return 1
    mkdir -p "$dest"
    # Catalogue versions for Kotlin / coroutines / junit / gson. Hamcrest, Trove
    # and annotations are not in the version catalogue; those still use a glob
    # plus a contents check so `sort | tail -1` cannot pick a 2.2 stdlib or
    # Robolectric's annotations jar.
    find_jar() {
        pat="$1"
        must="${2:-}"
        jar=""
        for r in $roots; do
            if [ -n "$must" ]; then
                jar="$(find "$r" -path "*$must*" -name "$pat" 2>/dev/null | sort | tail -1)"
            else
                jar="$(find "$r" -name "$pat" 2>/dev/null | sort | tail -1)"
            fi
            [ -n "$jar" ] && break
        done
        if [ -z "$jar" ]; then
            echo "preflight: could not find $pat${must:+ ($must)} — removing partial $dest" >&2
            rm -rf "$dest"
            return 1
        fi
        ln -sf "$jar" "$dest/"
    }
    find_jar "kotlin-compiler-embeddable-${KOTLIN}.jar" || return 1
    find_jar "kotlin-stdlib-${KOTLIN}.jar" || return 1
    find_jar "kotlinx-coroutines-core-jvm-${COROUTINES}.jar" || return 1
    find_jar "junit-${JUNIT}.jar" 'junit/junit/' || return 1
    find_jar 'hamcrest-core-*.jar' || return 1
    find_jar 'trove4j-*.jar' || return 1
    find_jar 'annotations-*.jar' 'org.jetbrains/annotations/' || return 1
    # Optional: absence disables the backup lane but must not fail the bootstrap,
    # so this runs as its own loop rather than being added to the list above.
    if [ -n "$GSON" ]; then
        jar=""
        for r in $roots; do
            jar="$(find "$r" -name "gson-${GSON}.jar" 2>/dev/null | sort | tail -1)"
            [ -n "$jar" ] && break
        done
        if [ -n "$jar" ]; then
            ln -sf "$jar" "$dest/"
        else
            echo "preflight: no gson-${GSON}.jar found — backup tests will be skipped" >&2
        fi
    fi
    echo "preflight: assembled test jars in $dest (kotlin=$KOTLIN coroutines=$COROUTINES junit=$JUNIT)"
}

jar_has_class() {
    jar="$1"
    class="$2"
    [ -e "$jar" ] || return 1
    unzip -l "$jar" 2>/dev/null | grep -q "$class"
}

jars_usable() {
    [ -n "$(find "$JARS" -name '*.jar' 2>/dev/null | head -1)" ] || return 1
    # Judge by what the jar CONTAINS, not by where it came from. A directory
    # assembled by hand — curl from Maven Central, an offline mirror — must
    # still pass if the classes are the ones the domain lane needs.
    jar_has_class "$JARS/kotlin-stdlib-${KOTLIN}.jar" 'kotlin/jvm/internal/Intrinsics.class' || return 1
    jar_has_class "$JARS/kotlin-compiler-embeddable-${KOTLIN}.jar" \
        'org/jetbrains/kotlin/cli/jvm/K2JVMCompiler.class' || return 1
    if [ -n "$COROUTINES" ]; then
        jar_has_class "$JARS/kotlinx-coroutines-core-jvm-${COROUTINES}.jar" \
            'kotlinx/coroutines/Dispatchers.class' || return 1
    fi
    if [ -n "$JUNIT" ]; then
        jar_has_class "$JARS/junit-${JUNIT}.jar" 'org/junit/Test.class' || return 1
    fi
    for f in "$JARS"/annotations-*.jar; do
        [ -e "$f" ] || return 1
        if unzip -l "$f" 2>/dev/null | grep -q 'org/jetbrains/annotations/NotNull\.class'; then
            return 0
        fi
        return 1
    done
    return 1
}

if ! jars_usable; then
    rm -rf "$JARS"
    bootstrap_jars "$JARS" || echo "preflight: no jar directory; will fall back to Gradle for tests" >&2
fi
export PT_JARS="$JARS"   # syntax-check.sh and run-domain-tests.sh both read this

# --- static checks judged by exit code ----------------------------------------
for c in "check-internal-imports.py" \
         "check-missing-imports.py" \
         "check-design-tokens.py" \
         "check-screen-wiring.py app/src/main/java" \
         "check-state-members.py" \
         "check-annotation-targets.py" \
         "check-required-args.py" \
         "check-lambda-arity.py app/src/main/java app/src/test/java app/src/androidTest/java app/src/debug/java app/src/sharedTest/java" \
         "check-import-hygiene.py" \
         "check-doc-authority.py" \
         "check-backup-policy.py" \
         "check-sdk-target.py" \
         "check-lint-policy.py" \
         "check-supply-chain.py" \
         "check-domain-seams.py" \
         "check-commercial-boundary.py" \
         "check-version-code.py" \
         "check-play-rehearsal.py" \
         "check-still-pack.py" \
         "check-unbounded-waits.py" \
         "check-cancellation.py" \
         "test_policy_move.py" \
         "test_checker_skips.py" \
         "test_lambda_arity.py" \
         "test_unbounded_waits.py" \
         "test_cancellation.py"; do
    step "$c"
    # shellcheck disable=SC2086
    python3 tools/$c || fail "$c"
done

# --- static checks that always exit 0: judged on their summary line -----------
summary() {
    label="$1"; want="$2"; shift 2
    step "$label"
    out="$("$@")" || fail "$label crashed"
    printf '%s\n' "$out" | tail -1
    # The count must START a line, not merely appear in one. `grep -F "0 mismatch(es)"`
    # is satisfied by "10 mismatch(es) across 680 files", so this gate reported clean at
    # 10, 20, 30 ... findings for all three checkers below — a false green that had been
    # sitting in the shared gate. awk's index()==1 is a literal prefix test, so there is
    # no regex to escape and no metacharacter in "(es)" to get wrong. Every checker here
    # prints its count at the start of a line; tools/test_summary_gate.sh proves both
    # directions.
    printf '%s\n' "$out" | awk -v want="$want" 'index($0, want) == 1 { hit = 1 } END { exit !hit }' \
        || { printf '%s\n' "$out"; fail "$label"; }
}
# The gate below is only worth its exit code if it actually fails on a finding. It did not
# until 10 Sep 2026; this proves both directions before any of it is trusted.
step "test_summary_gate.sh"
sh tools/test_summary_gate.sh || fail "test_summary_gate.sh"

# One run over every source set, not one per set. A root scanned alone is a false clean:
# nothing outside it is in the declaration index, so every call into another source set is
# skipped — which for app/src/test and app/src/androidTest is most of them.
summary "check-named-args" "0 mismatch(es)" \
    python3 tools/check-named-args.py app/src/main/java app/src/test/java \
        app/src/androidTest/java app/src/debug/java app/src/sharedTest/java
summary "check-when-exhaustive" "0 non-exhaustive" \
    python3 tools/check-when-exhaustive.py app/src/main/java
summary "check-unused-imports" "0 unused import(s)" \
    python3 tools/check-unused-imports.py app/src/main/java

# syntax-check.sh exits 0 even on findings, and legitimately skips when no
# compiler jar exists: pass on "NO SYNTAX ERRORS", warn on skip, fail otherwise.
step "syntax-check (main)"
out="$(tools/syntax-check.sh app/src/main/java)" || fail "syntax-check crashed"
printf '%s\n' "$out" | tail -1
case "$out" in
    *"NO SYNTAX ERRORS"*) ;;
    *"No kotlin-compiler-embeddable"*) echo "preflight: WARNING — syntax check skipped (no compiler jar)" ;;
    *) fail "syntax-check" ;;
esac

# --- JVM tests -------------------------------------------------------------
# PT_STATIC_ONLY=1 runs just the checks above. CI uses it: Gradle runs the
# full unit suite in its own step, so preflight there only needs the static
# gate — and duplicating the checker list in ci.yml is how the two drift.
if [ "${PT_STATIC_ONLY:-}" = "1" ]; then
    printf '\npreflight: OK (static only)\n'
    exit 0
fi
if [ -d "$JARS" ]; then
    step "JVM tests (tools/run-domain-tests.sh $JARS)"
    tools/run-domain-tests.sh "$JARS" || fail "JVM tests"
elif [ -x ./gradlew ] && ./gradlew -q help >/dev/null 2>&1; then
    step "JVM tests (./gradlew testDebugUnitTest — no jar directory found)"
    ./gradlew testDebugUnitTest || fail "testDebugUnitTest"
else
    fail "no test lane: no domain-test jars found and Gradle cannot run here (see tools/run-domain-tests.sh header for the jar list)"
fi

printf '\npreflight: OK\n'
