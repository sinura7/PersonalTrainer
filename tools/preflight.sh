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
    # Each line is "filename-glob [optional path fragment]". The path fragment
    # keeps Robolectric's annotations-4.14.1.jar and junit-4.14.1.jar from
    # winning the sort: those packages do not contain
    # org.jetbrains.annotations.NotNull, and the domain-test lane then dies
    # with NoClassDefFoundError on the first Kotlin file that uses it.
    # Kotlin jars must be 2.x: a 1.9 compiler from an old Gradle distribution
    # cannot be trusted to compile this project's Kotlin 2.0 sources.
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
    find_jar 'kotlin-compiler-embeddable-2*.jar' || return 1
    find_jar 'kotlin-stdlib-2*.jar' || return 1
    find_jar 'kotlinx-coroutines-core-jvm-*.jar' || return 1
    find_jar 'junit-4*.jar' 'junit/junit/' || return 1
    find_jar 'hamcrest-core-*.jar' || return 1
    find_jar 'trove4j-*.jar' || return 1
    find_jar 'annotations-*.jar' 'org.jetbrains/annotations/' || return 1
    # Optional: absence disables the backup lane but must not fail the bootstrap,
    # so this runs as its own loop rather than being added to the list above.
    for pat in 'gson-2*.jar'; do
        jar=""
        for r in $roots; do
            jar="$(find "$r" -name "$pat" 2>/dev/null | sort | tail -1)"
            [ -n "$jar" ] && break
        done
        if [ -n "$jar" ]; then
            ln -sf "$jar" "$dest/"
        else
            echo "preflight: no $pat found — backup tests will be skipped" >&2
        fi
    done
    echo "preflight: assembled test jars in $dest"
}

jars_usable() {
    [ -n "$(find "$JARS" -name '*.jar' 2>/dev/null | head -1)" ] || return 1
    # A previous bootstrap could have linked Robolectric's annotations jar.
    # Rebuild rather than compile against a package that has no NotNull.
    for f in "$JARS"/annotations-*.jar; do
        [ -e "$f" ] || return 1
        target="$(readlink -f "$f" 2>/dev/null || readlink "$f" || echo "$f")"
        case "$target" in
            *org.jetbrains/annotations*) return 0 ;;
        esac
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
for c in "check-internal-imports.py app/src/main/java" \
         "check-missing-imports.py" \
         "check-design-tokens.py app/src/main/java" \
         "check-screen-wiring.py app/src/main/java" \
         "check-state-members.py app/src/main/java" \
         "check-annotation-targets.py" \
         "check-required-args.py" \
         "check-import-hygiene.py" \
         "check-doc-authority.py" \
         "check-backup-policy.py" \
         "check-sdk-target.py" \
         "check-lint-policy.py" \
         "check-supply-chain.py" \
         "check-domain-seams.py" \
         "check-commercial-boundary.py" \
         "check-version-code.py" \
         "check-play-rehearsal.py"; do
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
