# Phase 2 — Test Substrate

> Executor packet. Standalone by design: assume no memory of any prior session. Execution
> protocol (branch, PR, sign-off): docs/gameplan/PROTOCOL.md — referenced, not duplicated.
> That file is committed under `docs/gameplan/` and IS present on your base — its presence
> proves nothing. The has-Phase-0-merged test is the one PROTOCOL §4 prescribes:
> `grep -c "Signed:" docs/ROADMAP.md` must return greater than 0. If it returns 0, the Phase 0
> PR has not merged — stop and report, do not improvise a protocol. Ground truth: all code
> lives on `claude/app-hierarchy-navigation-cjzigo` (main still holds only the initial commit
> until Phase 0 merges); work on branch `claude/phase-2-test-substrate` (the `claude/**` prefix
> matches ci.yml:13's triggers).
>
> **Execution slot: Phase 2 is the FIRST code phase.** Phase numbers are identifiers, not
> sequence. The order is 0 → **2** → 1 → 3 → 4 → 5 → 6a → 6b → 7 → 8. Start as soon as the
> Phase 0 PR merges; do NOT wait for Phase 1 (session hygiene), which now runs after this
> phase and depends on it.
>
> **Mandatory first commit: the re-baseline report.** Every count, line number, and repo-state
> assertion in this packet is a baseline as of audit commit 2212628 / branch tip 4028c93, not
> an oracle. Your first commit on the phase branch records: current trunk tip, the actual
> domain-test count and class count, and every packet literal that has drifted with its
> verified current value. Drift fully explained by merged prior phases or by the game plan's
> own commits is EXPECTED — proceed. Stop only on a mismatch with no such explanation.

## 1. Mission

Phase 3 rewrites the owner's only real database (schema v1 → v2), and today there is no
harness that could ever run a `MigrationTestHelper` test: no androidTest source set, no
test runner, no room-testing dependency, and a CI that has never once executed
(docs/DEVELOPMENT.md:95-105 — account billing block, `runner_id: 0`). This phase builds the
two test lanes that migration suite will run in — a Robolectric JVM lane (primary, runs
anywhere Gradle runs) and an instrumented device lane (the truth check) — plus
`tools/preflight.sh`, the single mechanical gate every later phase runs before every push.
It ships zero changes to `app/src/main`.

## 2. Read first

1. `docs/gameplan/PROTOCOL.md` — branch-per-phase, PR-per-phase, owner sign-off closes the phase.
2. `docs/DEVELOPMENT.md` — the test lanes as they exist: JVM suite + eight static checks (43-79), the domain-test jar lane (81-87), CI's never-ran state (89-108), debug/release sharing one applicationId (15-18), the schema-change rules (112-115).
3. `app/build.gradle.kts` — what is and is not wired: no `testInstrumentationRunner` in defaultConfig (30-37), androidTest assets already pointed at `app/schemas` (39-42), Room schema export (90-92), test deps are junit + coroutines-test only (142-143).
4. `gradle/libs.versions.toml` — Room 2.6.1 (line 10); no robolectric/androidx.test/room-testing artifacts exist yet. Your new entries go here.
5. `tools/run-domain-tests.sh` — the jar contract (header lines 13-16), jar-dir resolution (19-29), and the fact it compiles ONLY `domain/`, `util/`, `logging/` sources and ONLY tests under `app/src/test/.../domain` (54-57, 67-68). Your Robolectric test must stay out of its reach.
6. `tools/README.md` + `tools/syntax-check.sh` — the eight static checks preflight chains; note syntax-check.sh always exits 0 (its `| grep … || echo` pipeline, lines 30-33) and legitimately skips when no compiler jar is found (20-26).
7. `.github/workflows/ci.yml` — the existing `verify` job (28-98) and its triggers (13). You add one job; you do not touch `verify`.
8. `app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/1.json` — the committed v1 baseline (identityHash `6d58ad40d5c03785ab29aaf61157f369`, six tables) your smoke test opens. Never hand-edit it (ROADMAP.md:16-18).
9. `app/src/main/java/com/sinura/personaltrainer/data/local/TrainerDatabase.kt` — version 1, `exportSchema = true` (17-29); no `fallbackToDestructiveMigration` anywhere (35-42) and none may ever be added.
10. `settings.gradle.kts` — repositories are `google()` + `mavenCentral()` (16-21); robolectric resolves from Maven Central, androidx.test from Google Maven.

## 3. Binding doctrine

- **REVISED_STRUCTURE Phase 2 definition** (lines 112-118) and **critique item 7** (35-38): Robolectric-on-JVM is the primary `MigrationTestHelper` lane; `connectedDebugAndroidTest` is the device-truth lane; the GitHub billing fix is a standing **owner errand that gates nothing**.
- **REVISED_STRUCTURE execution protocol** (226-240): `tools/preflight.sh` green (all eight static checks + domain tests) before every push is the mechanical half of every phase's definition of done — this phase creates that script.
- **Accepted attack findings this packet satisfies** (attacks.md): tech "no androidTest scaffold exists, permit the Robolectric route"; scope "3.95's primary path is not executable by the model" — the gate here is reachable with zero account changes; executor "3.95's gate can pass while leaving the migration suite nowhere to run" — the gate objects below are named, committed tests, not "one green run"; contradictions "gate objects that don't exist when their gate runs" — same; executor "the domain-test runner is not bootstrappable from a fresh clone / no single preflight script" — work item 4 closes both.
- **docs/DEVELOPMENT.md:112-115**: schema changes require a bumped version, a hand-written Migration, and a committed schema JSON; destructive fallback is forbidden. This phase adds the harness only — no schema change.
- **docs/UI_REDESIGN.md §8** (guardrails) and **DESIGN_AUDIT**: not otherwise engaged — this phase touches no `app/src/main` file, no UI, no tokens. `check-design-tokens.py` (which enforces §8, UI_REDESIGN.md:188) runs inside preflight as proof.
- **docs/ROADMAP.md:144**: "No instrumented tests; repositories, DAOs, ViewModels, screens untested" — this phase is the first half of retiring that row.

## 4. Settled decisions

Stated as settled. Do not reopen.

- **This phase runs before Phase 1, and that is deliberate.** Phase 2 ships `tools/preflight.sh`
  and the Robolectric lane, so running it first (a) keeps this packet's verified gate literals
  (188 tests / 24 classes / 23 files) true at execution time instead of stale, (b) gives Phase 1's
  gates a working domain-test lane instead of a command that exits 2 on a cold clone
  (`tools/run-domain-tests.sh:20-24` needs a jar dir that this phase's bootstrap creates), and
  (c) gives Phase 1's repository writes (restoreSet, repeatSession, deleteFinishedSession) a
  Robolectric lane they otherwise lack. Accepted cost: session hygiene reaches the owner ~1-2
  executor-days later.
- **Host-OS constraint: the JVM/Robolectric lane requires macOS or Linux.** Robolectric 4.14.1
  defaults to NATIVE SQLite everywhere EXCEPT Windows, where `SQLiteModeConfigurer.defaultValue()`
  hard-falls back to LEGACY mode (SQLite 3.7.10). LEGACY's `PRAGMA table_info` cannot express
  composite primary keys, so Room schema validation fails FALSELY for entities with compound PKs —
  exactly what Phase 3's `exercise_muscles` (`PRIMARY KEY(exerciseId, muscleKey)`) uses. On a
  Windows host the `connectedDebugAndroidTest` emulator lane is therefore the ONLY valid migration
  lane. This is written into the test KDoc (WI-2) and into the DEVELOPMENT.md runbook (WI-3).
- **Two lanes, one truth statement.** Robolectric runs Room against Robolectric's own bundled SQLite on the JVM — it is NOT the SQLite build on the owner's phone. The JVM lane is the fast, always-available check; `connectedDebugAndroidTest` on an emulator is the truth check. Phase 3's migration suite must pass in **both**. This limitation is written into the test's KDoc and into DEVELOPMENT.md (work item 3).
- **Device lane runs on an emulator, never the owner's phone.** Debug and release share `applicationId com.sinura.personaltrainer` with different signing keys, so the debug test APK cannot install alongside the release app (DEVELOPMENT.md:15-18). The runbook targets an API 26+ emulator; uninstalling the release app to force it is forbidden.
- **Version-catalog entries** (literal):
  - `[versions]`: `robolectric = "4.14.1"`, `androidxTestCore = "1.6.1"`, `androidxTestRunner = "1.6.2"`, `androidxTestRules = "1.6.1"`, `androidxTestExtJunit = "1.2.1"`.
  - `[libraries]`: `robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }`; `androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }`; `androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }`; `androidx-test-rules = { group = "androidx.test", name = "rules", version.ref = "androidxTestRules" }`; `androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }`; `androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }` (rides the existing `room = "2.6.1"`).
- **Runner string**: `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `defaultConfig`.
- **Robolectric tests live in the unit-test source set but OUTSIDE `domain/`** — at `app/src/test/java/com/sinura/personaltrainer/data/local/`. Reason: `tools/run-domain-tests.sh` compiles the whole `app/src/test/.../domain` tree with no Android classpath (run-domain-tests.sh:67-68); a Robolectric import there would break the jar lane. The Robolectric test therefore runs **only under Gradle** (`testDebugUnitTest`, Studio, CI) — stated plainly, not hidden.
- **Test names (the gate objects)**: `SchemaV1BaselineTest` (JVM/Robolectric), `InstrumentationSmokeTest` and `SchemaV1BaselineDeviceTest` (androidTest).
- **Schema assets for the JVM lane**: `sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")` plus `testOptions { unitTests { isIncludeAndroidResources = true } }` — mirroring the androidTest wiring that already exists at app/build.gradle.kts:39-42.
- **preflight.sh semantics** (all verified by execution in an SDK-less environment): four checks are judged by exit code because they `sys.exit(count)` — check-internal-imports.py:128, check-missing-imports.py:201, check-design-tokens.py:77, check-screen-wiring.py:69. Three always exit 0 and are judged on their summary line — check-named-args ("0 mismatch(es)"), check-when-exhaustive ("0 non-exhaustive"), check-unused-imports ("0 unused import(s)"). syntax-check.sh always exits 0 (syntax-check.sh:30-33) and is judged on "NO SYNTAX ERRORS", with a warn-and-continue on its no-jar skip message.
- **Jar bootstrap** for `run-domain-tests.sh` (jar list from its header, lines 13-16: kotlin-compiler-embeddable, kotlin-stdlib, kotlinx-coroutines-core-jvm, junit, hamcrest-core, trove4j, annotations). Search order: (1) the Gradle module cache `~/.gradle/caches/modules-2` — on any machine that has built this app, KSP has already fetched kotlin-compiler-embeddable 2.0.21 there (this is the same source tools/syntax-check.sh:13 already trusts); (2) `~/.gradle/wrapper/dists`; (3) a Gradle distribution's `lib/` directory (`/opt/gradle-*/lib`, or `$(dirname $(command -v gradle))/../lib`). Verified: a gradle-8.14.3 distribution's `lib/` contains all seven jars, and the domain suite passes against them (24 classes, 188 tests, OK). **Re-baseline caveat (applies to every 188/24/23 literal in this packet — §7 and §8 included):** those numbers are a verified baseline as of branch tip 4028c93, and because this phase runs first they are expected to hold at execution time. Recount them in your re-baseline commit anyway; drift explained by merged prior phases or by the game plan's own commits is expected — record the current value and proceed. The kotlin jars are pattern-pinned to `2*` because the repo's own wrapper is gradle-8.9 (gradle/wrapper/gradle-wrapper.properties), whose distribution carries a 1.9.x compiler that must not be picked up. **Honest fallback**: when no jars can be found anywhere, preflight runs `./gradlew testDebugUnitTest`; when Gradle cannot run either, preflight fails loudly — it never silently skips tests.
- **ci.yml**: one new job, `instrumented-smoke`, `continue-on-error: true` at job level, `verify` untouched. Because CI has never run (billing), this job is **unverifiable in-phase**; the in-phase gate on it is YAML validity only. The billing fix (card / public repo / self-hosted runner, DEVELOPMENT.md:102-105) is an owner errand recorded in the hand-back — **it gates nothing**. It is now a REQUESTED (still non-gating) item on Phase 0's owner checklist, so this job may start running sooner than this packet assumes; that changes nothing about the gates. **No gate in this or any phase may depend on a CI run** — every gate line is owner-machine output pasted in the PR, with CI green as an ADDITIONAL check once the billing errand is done.
- **Zero `app/src/main` changes.** The diff touches: `gradle/libs.versions.toml`, `app/build.gradle.kts`, two test source files, one androidTest source dir (two files), `tools/preflight.sh`, `tools/README.md`, `docs/DEVELOPMENT.md`, `.github/workflows/ci.yml`. Nothing else.

## 5. Work items

### WI-1: Gradle wiring for both lanes

Modify `gradle/libs.versions.toml`: add the `[versions]` and `[libraries]` entries exactly as in Settled decisions.

Modify `app/build.gradle.kts`:

1. In `defaultConfig` (after line 36): `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`.
2. In `sourceSets` (next to the existing androidTest line at 39-42, keep its comment):
   ```kotlin
   // Same substrate for the JVM (Robolectric) migration lane.
   getByName("test").assets.srcDir("$projectDir/schemas")
   ```
3. New block inside `android {}`:
   ```kotlin
   testOptions {
       unitTests {
           // Robolectric: serves the app's (and test source set's) assets —
           // MigrationTestHelper reads app/schemas from there.
           isIncludeAndroidResources = true
       }
   }
   ```
4. Dependencies (append to the existing block at 121-144):
   ```kotlin
   testImplementation(libs.robolectric)
   testImplementation(libs.androidx.test.core)
   testImplementation(libs.androidx.room.testing)
   androidTestImplementation(libs.junit)
   androidTestImplementation(libs.androidx.test.ext.junit)
   androidTestImplementation(libs.androidx.test.runner)
   androidTestImplementation(libs.androidx.test.rules)
   androidTestImplementation(libs.androidx.room.testing)
   ```

**Tests**: none of its own — WI-2/WI-3 are its tests. Static proof in-session: `tools/preflight.sh` still green (this item changes no Kotlin source).

### WI-2: Robolectric JVM lane hosting MigrationTestHelper

Create `app/src/test/java/com/sinura/personaltrainer/data/local/SchemaV1BaselineTest.kt`:

```kotlin
package com.sinura.personaltrainer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves the committed v1 schema baseline (app/schemas/.../1.json) opens through
 * MigrationTestHelper on the JVM. This is the substrate Phase 3's v1→v2 migration
 * suite builds on.
 *
 * Known limitation, on purpose: Robolectric bundles its own SQLite — it is NOT the
 * SQLite on the owner's phone. Green here is necessary, never sufficient; the
 * device-truth lane is connectedDebugAndroidTest (SchemaV1BaselineDeviceTest).
 *
 * Host OS matters: Robolectric 4.14.1 uses NATIVE SQLite everywhere except Windows,
 * where SQLiteModeConfigurer.defaultValue() falls back to LEGACY (SQLite 3.7.10).
 * LEGACY's PRAGMA table_info cannot express composite primary keys, so Room schema
 * validation fails falsely for entities with compound PKs (Phase 3's exercise_muscles).
 * Run this lane on macOS or Linux; on Windows the emulator lane is the only valid one.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaV1BaselineTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrainerDatabase::class.java,
    )

    @Test
    fun v1BaselineOpensWithAllSixTables() {
        val db = helper.createDatabase("schema-v1-smoke", 1)
        val tables = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%' " +
                "AND name != 'room_master_table'"
        ).use { cursor ->
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
        }
        db.close()
        assertEquals(
            setOf(
                "exercises", "routines", "routine_exercises",
                "workout_sessions", "session_exercises", "set_logs",
            ),
            tables,
        )
    }
}
```

The six table names are the v1 schema's `tableName` entries in 1.json — verified. `createDatabase(name, 1)` builds the database FROM the committed JSON, which is exactly the mechanism Phase 3's migration tests will use to build the "before" state.

Notes for the executor: Robolectric downloads an `android-all` jar from Maven Central on first run (one-time, cached) — this test cannot execute in an environment without Maven Central access; it runs under `./gradlew testDebugUnitTest` on the owner's machine and in CI. If the helper reports it cannot find `TrainerDatabase/1.json` in assets, the `sourceSets` test-assets line or `isIncludeAndroidResources` from WI-1 is missing — fix the wiring, do not copy schema files.

**Tests**: this file IS the test. In-session proof is static only (preflight green; the file's imports resolve against the WI-1 deps by inspection); executed proof is the owner-machine gate.

### WI-3: androidTest scaffold + device runbook

Create `app/src/androidTest/java/com/sinura/personaltrainer/InstrumentationSmokeTest.kt`:

```kotlin
package com.sinura.personaltrainer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the instrumented lane itself: runner declared, deps resolve, APK installs. */
@RunWith(AndroidJUnit4::class)
class InstrumentationSmokeTest {
    @Test
    fun targetContextIsTheApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.sinura.personaltrainer", context.packageName)
    }
}
```

Create `app/src/androidTest/java/com/sinura/personaltrainer/data/local/SchemaV1BaselineDeviceTest.kt` (`package com.sinura.personaltrainer.data.local`): same body as `SchemaV1BaselineTest` minus the Robolectric pieces — `@RunWith(AndroidJUnit4::class)`, same `MigrationTestHelper` rule, same `v1BaselineOpensWithAllSixTables` test, KDoc stating it is the device-truth twin. The androidTest assets wiring it needs already exists (app/build.gradle.kts:39-42). This is the device-truth twin: real Android SQLite.

Add to `docs/DEVELOPMENT.md`, replacing the now-false sentence at 107-108 ("There are no instrumented (`androidTest`) tests yet…"), a new section — the **runbook, verbatim**:

````markdown
## Instrumented tests

Two lanes exist for anything Room touches:

- **JVM lane (primary)**: Robolectric tests under `app/src/test` (e.g.
  `SchemaV1BaselineTest`) run inside `./gradlew testDebugUnitTest` — no device.
  Robolectric bundles its own SQLite, which is not the phone's; green here is
  necessary, never sufficient. **This lane requires a macOS or Linux host.** On
  Windows, Robolectric falls back to LEGACY SQLite (3.7.10), whose
  `PRAGMA table_info` cannot express composite primary keys, so Room schema
  validation fails falsely for any table with a compound primary key. On a Windows
  machine, use the device lane below as the only migration lane.
- **Device lane (truth)**: `app/src/androidTest`, run with

  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

  against a **running API 26+ emulator** (Device Manager → start one first).
  Expected: `BUILD SUCCESSful` and a green report at
  `app/build/reports/androidTests/connected/`. **Never point this at the phone**:
  the debug test APK shares the release applicationId and cannot install next to
  the real app (see First run) — and uninstalling the release app to make room
  would delete your training history.

Migration tests must pass in both lanes before a schema change ships.
````

(Fix the casing typo — `BUILD SUCCESSFUL` — when writing; shown here to force a careful copy, not a blind paste.)

**Tests**: the two files are the tests; the runbook's gradle command is the executed proof (owner checklist).

### WI-4: tools/preflight.sh

Create `tools/preflight.sh`, mode 755, with these **literal contents** (this exact script was executed in an SDK-less clone of this repo: cold-state bootstrap assembled the seven jars, all eight checks passed, 188 domain tests ran OK, exit 0; a planted unused import made it exit 1 at the right step):

```sh
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
         "check-screen-wiring.py app/src/main/java"; do
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
```

Also: add a `## preflight.sh` section to `tools/README.md` (one paragraph: what it chains, the jar bootstrap, the Gradle fallback, "run before every push"), and in `docs/DEVELOPMENT.md`'s "Running tests" section add the one-liner `tools/preflight.sh` above the individual check list as the way to run them all.

**Tests**: (a) fresh-state run — `rm -rf build/test-jars && tools/preflight.sh` exits 0 ending `preflight: OK` with `OK (188 tests)` above it; (b) failure-path run — append a junk import to any main-source file, preflight exits 1 with `preflight: FAIL — check-unused-imports`, revert; (c) `sh -n tools/preflight.sh` parses clean.

### WI-5: ci.yml — non-blocking instrumented job (safe-only)

Append one job to `.github/workflows/ci.yml` after `verify` (do not modify `verify`; note the JVM Robolectric test already rides `verify`'s existing `testDebugUnitTest` step with no yml change):

```yaml
  # Non-blocking by design. CI on this repo has never been assigned a runner
  # (account billing — see docs/DEVELOPMENT.md), so this job is written blind: it
  # exists so the instrumented smoke starts running the day the owner fixes
  # billing, with zero further changes. continue-on-error keeps it from ever
  # gating a merge, even once it runs — emulator jobs flake.
  instrumented-smoke:
    name: Instrumented smoke (non-blocking)
    runs-on: ubuntu-latest
    timeout-minutes: 45
    continue-on-error: true
    steps:
      - name: Check out
        uses: actions/checkout@v7

      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v4

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v5

      - name: Make gradlew executable
        run: chmod +x ./gradlew

      - name: Enable KVM for the emulator
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm

      - name: Instrumented tests on emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 29
          arch: x86_64
          disable-animations: true
          script: ./gradlew connectedDebugAndroidTest --stacktrace

      - name: Upload instrumented reports
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: instrumented-reports
          path: app/build/reports/androidTests/
          retention-days: 14
          if-no-files-found: ignore
```

**Tests**: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('yaml ok')"` prints `yaml ok`. That is the whole in-phase gate for this item — the job cannot execute until the owner errand resolves, and the packet says so instead of pretending. Keep the job non-blocking (`continue-on-error: true`) even after the errand lands: no phase gate may depend on a CI run. The errand is a REQUESTED (non-gating) item on Phase 0's checklist, so if it has already resolved you may see this job execute — a red result on it never blocks the PR.

## 6. Out of scope

- Any schema change, any Migration, any v1→v2 test. The v1-open smoke is the ceiling.
- Fixing the GitHub billing block, flipping the repo public, or standing up a self-hosted runner — owner errand, recorded in hand-back, gates nothing.
- The A1 DI seam (AppViewModel/AppContainer) — cut from the critical path by REVISED_STRUCTURE; instrumented ViewModel tests do not exist to justify it.
- Espresso/Compose UI tests, screenshot tests, ViewModel/repository Robolectric tests — Phase 3+ decides what it needs.
- Making `lint` blocking, or any other change to the `verify` job.
- Emulator/AVD provisioning scripts; the runbook uses Android Studio's Device Manager.
- Any file under `app/src/main`. Any dependency version bump beyond the new entries (no AGP/Kotlin/Room/Compose changes).
- Anything touching the owner's phone.

## 7. Acceptance gate

Executor, in-session (SDK-less environment), from a clean tree on `claude/phase-2-test-substrate`:

```bash
rm -rf build/test-jars && tools/preflight.sh
# expected: eight check sections, each green ("0 mismatch(es)", "0 non-exhaustive",
# "0 unused import(s)", "0 unresolved name(s)", "0 missing import(s)",
# "0 design-token violation(s)", "0 unwired callback(s)", "NO SYNTAX ERRORS"),
# then "Running 24 test classes..." / "OK (188 tests)", final line "preflight: OK", exit 0.
# (The Robolectric test is NOT in this count — it lives outside domain/ and runs only under Gradle.)

sh -n tools/preflight.sh          # expected: silence, exit 0
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('yaml ok')"
                                  # expected: yaml ok

# TRUNK = the branch you forked claude/phase-2-test-substrate from, per
# docs/gameplan/PROTOCOL.md: main if Phase 0 merged the working branch there,
# otherwise claude/app-hierarchy-navigation-cjzigo.
git diff --stat "$(git merge-base HEAD "$TRUNK")" -- app/src/main
                                  # expected: empty output — zero main-source changes
```

Owner's machine (Android Studio installed; see §8 for the walked version):

```bash
./gradlew testDebugUnitTest --tests '*SchemaV1BaselineTest*'   # BUILD SUCCESSFUL, 1 test, 0 failed
./gradlew testDebugUnitTest                                    # BUILD SUCCESSFUL, full JVM suite green
./gradlew connectedDebugAndroidTest                            # emulator running; BUILD SUCCESSFUL, 2 tests
tools/preflight.sh                                             # preflight: OK
```

Domain-test list by name — all pre-existing, all must stay green in the jar lane (24 JUnit classes across 23 files, 188 tests): DayLabelTest, ExerciseHistoryBuilderTest, ExerciseUsageTest, HeatWindowTest, MuscleLoadCalculatorTest, MuscleNormalizerTest, NumericEntryTest, PersonalRecordsTest, ProgressionBasisTest, ProgressionCalculatorTest, RecommendationEngineTest, RestTimerTest, RoutineEditorLoadTest, RoutineEditorPolicyTest, SetLogRulesTest, TrainingCalendarBuilderTest, TrainingInsightsCalculatorTest, VolumeLabelTest, WeeklySchedulePlannerTest, WeightConverterTest, WeightFormatTest (second class inside ProgressionCalculatorTest.kt), WorkingVolumeAgreementTest, WorkoutSessionSelectionTest, WorkoutSummaryBuilderTest. New this phase: **SchemaV1BaselineTest** (Gradle JVM lane), **InstrumentationSmokeTest** and **SchemaV1BaselineDeviceTest** (device lane).

The phase closes only on owner sign-off of §8, per docs/gameplan/PROTOCOL.md.

## 8. Owner device checklist

All on your computer and an emulator. Your phone is not involved and must not be.

1. Pull the phase branch (or the merged PR) and open the project in Android Studio; let Gradle sync finish. It downloads a handful of new test-only libraries — network required.
2. In the terminal at the project root, run `./gradlew testDebugUnitTest`. First run downloads a large one-time Robolectric artifact (~100 MB); let it finish. **Observe**: `BUILD SUCCESSFUL`, and in the report it opens (or at `app/build/reports/tests/testDebugUnitTest/index.html`) a green row for `SchemaV1BaselineTest`, zero failures anywhere.
3. In Android Studio: Device Manager → start any emulator with API 26 or newer (create one if none exists — defaults are fine). Wait for it to reach the home screen.
4. Run `./gradlew connectedDebugAndroidTest`. **Observe**: `BUILD SUCCESSFUL`, and at `app/build/reports/androidTests/connected/` a report showing **2 tests, 0 failures** (`InstrumentationSmokeTest`, `SchemaV1BaselineDeviceTest`). If it says no connected devices, the emulator isn't running — go back to step 3. Do NOT plug in your phone for this: the test build cannot install next to your real app, and it must never replace it.
5. Run `tools/preflight.sh`. **Observe**: a series of check sections, then `OK (188 tests)`, then the final line `preflight: OK`.
6. Paste the three outputs (steps 2, 4, 5) into the PR and approve it. Standing errand, no deadline, gates nothing: CI has still never run — the fix is yours alone (add a payment method / raise the $0 spending limit, or make the repo public, or attach a self-hosted runner; docs/DEVELOPMENT.md "Continuous integration"). The day you do, both CI jobs start running on every push with no further changes.

## 9. Estimates

- **Executor**: 1-2 days. Day 1: WI-1 through WI-4 plus in-session gates (the preflight script and jar bootstrap are already proven against this repo — transcription and wiring, not research). The buffer is for WI-2/WI-3 compile fixes after the owner's first Gradle sync, since no in-session compiler sees androidx.test imports.
- **Owner**: 0.5 day — one evening: sync, two Gradle runs, one emulator run, preflight, PR approval. First-run downloads dominate the clock.

## 10. Hand-back

The completion report to the owner must contain:

1. The PR link and one-paragraph summary: two test lanes now exist; Phase 3's migration suite has somewhere to run; zero app-code changes.
2. The full `tools/preflight.sh` transcript from the executor environment (proving the jar bootstrap worked from a cold clone) and the diffstat showing `app/src/main` untouched.
3. The three gate objects by name and path (`SchemaV1BaselineTest`, `InstrumentationSmokeTest`, `SchemaV1BaselineDeviceTest`) and the plain statement of the lane contract: Robolectric SQLite is not device SQLite; JVM lane is fast proof, emulator lane is truth; Phase 3 must run its migration tests in both.
4. The standing owner errand, restated verbatim: CI has never executed (account billing block, docs/DEVELOPMENT.md:95-105); fix = payment method / public repo / self-hosted runner; nothing in this or any phase gates on it; the new `instrumented-smoke` CI job is written but unexecuted and is non-blocking by construction.
5. What §8 asked the owner to verify and their pasted outputs, confirming the phase closed on sign-off, not on static evidence.
6. The next phase, named: **Phase 1 — Session hygiene**. Its two packets
   (`docs/gameplan/PHASE_1A_SESSION_LIFECYCLE.md` then `docs/gameplan/PHASE_1B_LOG_REPAIR.md`) are
   handed to ONE executor session and run in order — 1A fully, its gate green, then 1B on top — on
   ONE branch `claude/phase-1-session-hygiene`, as ONE PR, closing on ONE combined owner evening.
   Note for that executor what this phase just handed them: `tools/preflight.sh` with a cold-clone
   jar bootstrap, and a Robolectric lane their repository writes can finally be tested in.
