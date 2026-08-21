#!/bin/sh
# tools/preflight.sh — the mechanical half of every phase's definition of done.
#
# Runs the eight static checks, then the domain tests, and exits nonzero on the
# first failure. Run it before every push. It is a pre-flight, not a substitute
# for `./gradlew testDebugUnitTest` + `assembleDebug` — the Robolectric and
# instrumented tests only run under Gradle (see docs/DEVELOPMENT.md).
#
# Domain tests need a directory of seven jars (see tools/run-domain-tests.sh
# header). If $PT_JARS / build/test-jars is absent, this script assembles it by
# symlinking jars found in the Gradle module cache or a Gradle distribution's
# lib/ directory. If no jars can be found anywhere, it falls back to
# `./gradlew testDebugUnitTest`, and fails if Gradle cannot run either.
set -u
cd "$(dirname "$0")/.." || exit 2

fail() { echo "preflight: FAIL — $*" >&2; exit 1; }
step() { printf '\n== %s\n' "$*"; }

# --- jar bootstrap (needed by syntax-check.sh and run-domain-tests.sh) --------
JARS="${PT_JARS:-build/test-jars}"

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
    # Kotlin jars must be 2.x: a 1.9 compiler from an old Gradle distribution
    # cannot be trusted to compile this project's Kotlin 2.0 sources.
    for pat in 'kotlin-compiler-embeddable-2*.jar' 'kotlin-stdlib-2*.jar' \
               'kotlinx-coroutines-core-jvm-*.jar' 'junit-4*.jar' \
               'hamcrest-core-*.jar' 'trove4j-*.jar' 'annotations-*.jar'; do
        jar=""
        for r in $roots; do
            jar="$(find "$r" -name "$pat" 2>/dev/null | sort | tail -1)"
            [ -n "$jar" ] && break
        done
        if [ -z "$jar" ]; then
            echo "preflight: could not find $pat — removing partial $dest" >&2
            rm -rf "$dest"
            return 1
        fi
        ln -sf "$jar" "$dest/"
    done
    echo "preflight: assembled domain-test jars in $dest"
}

if [ -z "$(find "$JARS" -name '*.jar' 2>/dev/null | head -1)" ]; then
    bootstrap_jars "$JARS" || echo "preflight: no jar directory; will fall back to Gradle for tests" >&2
fi
export PT_JARS="$JARS"   # syntax-check.sh and run-domain-tests.sh both read this

# --- static checks judged by exit code ----------------------------------------
for c in "check-internal-imports.py app/src/main/java" \
         "check-missing-imports.py" \
         "check-design-tokens.py app/src/main/java" \
         "check-screen-wiring.py app/src/main/java" \
         "check-state-members.py app/src/main/java" \
         "check-annotation-targets.py" \
         "check-required-args.py"; do
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
    printf '%s\n' "$out" | grep -qF "$want" || { printf '%s\n' "$out"; fail "$label"; }
}
summary "check-named-args (main)" "0 mismatch(es)" \
    python3 tools/check-named-args.py app/src/main/java
summary "check-named-args (test)" "0 mismatch(es)" \
    python3 tools/check-named-args.py app/src/test/java
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

# --- domain tests -------------------------------------------------------------
if [ -d "$JARS" ]; then
    step "domain tests (tools/run-domain-tests.sh $JARS)"
    tools/run-domain-tests.sh "$JARS" || fail "domain tests"
elif [ -x ./gradlew ] && ./gradlew -q help >/dev/null 2>&1; then
    step "domain tests (./gradlew testDebugUnitTest — no jar directory found)"
    ./gradlew testDebugUnitTest || fail "testDebugUnitTest"
else
    fail "no test lane: no domain-test jars found and Gradle cannot run here (see tools/run-domain-tests.sh header for the jar list)"
fi

printf '\npreflight: OK\n'
