#!/bin/sh
# tools/compile-check.sh — type-check the Android-side Kotlin without the Android SDK.
#
# WHY THIS EXISTS
# ---------------
# The merge gate is `./gradlew testDebugUnitTest assembleDebug` and stays the source of truth.
# It cannot run in environments with no route to Google's Maven: the Android Gradle Plugin,
# androidx and Play Services all live on dl.google.com, and the project cannot even configure
# there. tools/run-domain-tests.sh covers the pure-Kotlin domain layer, but that leaves the
# whole Android side — every ViewModel, the entire data layer, the Compose screens — with no
# compiler ever looking at it. This lane closes most of that gap: it runs the real Kotlin
# 2.0.21 compiler over the Android-side sources, using REAL library declarations wherever they
# can be fetched from Maven Central and hand-written declaration-only stubs only where nothing
# is downloadable (see tools/compile-stubs/README.md and tools/compose-stubs/README.md).
#
# THREE STAGES, AND THEY ARE NOT EQUALLY STRONG
# ---------------------------------------------
#   base     Every .kt under app/src/main/java that does NOT import androidx.compose, compiled
#            against a classpath with no Compose on it at all. This is the strongest stage: the
#            only substituted APIs are the ones tools/compile-stubs/ declares.
#
#   compose  The WHOLE debug variant — app/src/main/java AND app/src/debug/java, Compose screens
#            included — compiled with the Compose compiler plugin against JetBrains Compose
#            Multiplatform 1.8.2. That is a real build of androidx.compose.*, but it is a
#            DIFFERENT BUILD OF A NEARBY VERSION: this app builds against Compose BOM 2026.06.01,
#            i.e. UI/foundation/runtime 1.11.4 and Material3 1.4.0
#            (docs/architecture/compose-toolchain.md). This is the half of the merge gate that
#            `assembleDebug` covers.
#
#   test     app/src/test/java + app/src/sharedTest/java, compiled AGAINST the compose stage's
#            output with the testImplementation-only jars added, exactly as
#            `./gradlew testDebugUnitTest` — the FIRST half of the merge gate — compiles them.
#            Without it, a method renamed in main with its main call sites updated but not its
#            test is a green here and a red in CI.
#
# All three run by default and all three must be clean. The base stage is kept even though the
# compose stage compiles a superset of its files, because it is the half whose classpath is narrow
# enough to trust on its own: if the Compose substitution is ever found wanting, the base stage's
# result is unaffected.
#
# THE STUBS ARE THEIR OWN MODULE. tools/compile-stubs, tools/compose-stubs and tools/test-stubs are
# compiled first, under -module-name stubs, and go on the CLASSPATH of the app compilation. That is
# not a detail: `internal` in Kotlin means "visible inside this module", so compiling the stubs
# alongside app sources made every `internal` declaration in them reachable from app code —
# 29 `internal constructor`s that Gradle can never reach.
#
# A green from this lane is worth exactly what the SOUNDNESS CAVEATS at the bottom of this file
# say it is worth, and no more. A lane that reports a false green is worse than no lane, so
# every place this one is weaker than Gradle is written down rather than glossed over.
#
# Usage:
#   tools/compile-check.sh                all three stages
#   tools/compile-check.sh --self-test    first prove each stage is not vacuous, then compile
#   tools/compile-check.sh --no-compose   skip the compose stage
#   tools/compile-check.sh --no-tests     skip the test stage
#   tools/compile-check.sh --compose-only compose stage only
#   tools/compile-check.sh --tests-only   test stage only (still builds the debug variant first,
#                                         because that is what the tests compile against)
#   tools/compile-check.sh --explain      print the substitution ledger and exit
#
# Environment:
#   PT_JARS               directory of already-present jars to reuse (default build/test-jars)
#   PT_COMPILE_CACHE      download cache (default ${TMPDIR:-/tmp}/pt-compile-check).
#                         Holds the ~186 MB android-all jar; deliberately NOT inside the repo.
#   PT_ANDROID_ALL_JAR    path to an already-downloaded android-all jar, to skip that download
#   PT_MAVEN              Maven repository base (default https://repo1.maven.org/maven2)
set -u
cd "$(dirname "$0")/.." || exit 2

SELF_TEST=0
RUN_BASE=1
RUN_COMPOSE=1
RUN_TESTS=1
EXPLAIN=0
for arg in "$@"; do
    case "$arg" in
        --self-test)   SELF_TEST=1 ;;
        --no-compose)  RUN_COMPOSE=0 ;;
        --compose-only) RUN_BASE=0; RUN_TESTS=0 ;;
        --no-tests)    RUN_TESTS=0 ;;
        --tests-only)  RUN_BASE=0; RUN_COMPOSE=0 ;;
        --explain)     EXPLAIN=1 ;;
        *) echo "compile-check: unknown argument '$arg'" >&2
           echo "  usage: compile-check.sh [--self-test] [--no-compose|--compose-only]" >&2
           echo "                          [--no-tests|--tests-only] [--explain]" >&2
           exit 2 ;;
    esac
done
if [ "$RUN_BASE" -eq 0 ] && [ "$RUN_COMPOSE" -eq 0 ] && [ "$RUN_TESTS" -eq 0 ]; then
    echo "compile-check: those flags together leave nothing to do." >&2
    exit 2
fi

fail() { echo "compile-check: FAIL — $*" >&2; exit 1; }

if [ "$EXPLAIN" -eq 1 ]; then
    cat <<'LEDGER'
compile-check — substitution ledger
===================================

REAL artifacts, fetched from Maven Central (not stubs):

  android.*                 org.robolectric:android-all:15-robolectric-13954326 — a real AOSP
                            build. NOT the compileSdk 36 android.jar the merge gate uses.
  androidx.compose.*        org.jetbrains.compose:*-desktop:1.8.2 (+ material-icons-core 1.7.3,
                            which JetBrains stopped publishing after 1.7.3), with every
                            desktop-only class STRIPPED OUT (see "the desktop-only strip" in this
                            file). Real Compose, built by JetBrains for the JVM. The app builds
                            against Compose 1.11.4 / Material3 1.4.0, so this is a NEARBY
                            VERSION, not the pinned one.
  androidx.annotation.*     org.jetbrains.compose.annotation-internal:annotation-desktop
  kotlin/kotlinx/gson       the versions gradle/libs.versions.toml pins
  the Compose plugin        org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable, at
                            the same Kotlin version the app uses — this one is exact.
  junit + hamcrest          test stage only, at the catalog's version
  kotlinx-coroutines-test   test stage only, at the catalog's version
  org.robolectric:*         test stage only — RobolectricTestRunner, @Config, Shadows.shadowOf
                            and Robolectric.buildService are the REAL classes at the catalog's
                            version. Robolectric publishes to Maven Central.

STUBBED, because Maven Central has nothing (tools/compile-stubs/, compose-stubs/, test-stubs/):

  androidx.room, androidx.sqlite, androidx.datastore, androidx.work, androidx.lifecycle,
  androidx.core, com.google.android.gms          — see tools/compile-stubs/README.md
  androidx.activity(+.compose, .result)          — Google-only artifact
  androidx.navigation(+.compose)                 — Google-only artifact
  androidx.lifecycle.compose, .viewmodel.compose — the JVM builds JetBrains publishes are
                                                   metadata-only placeholders with no classes
  androidx.compose.ui.platform.LocalContext / LocalView / LocalResources / LocalLocale,
  WindowInfo.containerDpSize, painterResource(Int), Font(resId), Bitmap.asImageBitmap(),
  androidx.compose.ui.tooling.preview.Preview    — Android-only, or newer than 1.8.2
  androidx.test.core / .platform, androidx.room.testing, PreferenceDataStoreFactory,
  ResourcesCompat.getFont                        — test stage only, see tools/test-stubs/README.md

  The stubs are compiled into their OWN module (-module-name stubs) and put on the classpath, so
  their `internal` declarations are invisible to app sources exactly as they would be in a jar.

THE DESKTOP-ONLY STRIP (what keeps the Compose substitution from being a false green)

  Compose Multiplatform's desktop build has APIs that exist in NO version of Android Compose:
  VerticalScrollbar, rememberScrollbarAdapter, Modifier.onClick, Modifier.onPointerEvent,
  androidx.compose.ui.res.useResource, painterResource(String), Font(String), the
  androidx.compose.ui.window desktop API, androidx.compose.ui.awt. Every one of them is deleted
  from the jars before anything is compiled — 703 classes — so no spelling of a call (aliased
  import, star import, fully qualified) can resolve to one. A negative control compiles all of
  them on every run and fails the lane if any still resolves.

  This is NOT a diff against Android Compose: Google's Maven is unreachable here, so no copy of
  Android Compose exists to diff against. The strip set is enumerated mechanically from the jars
  (file facades compiled from *.desktop.kt / *.skiko.kt / *.awt.kt, plus four whole packages) and
  then audited; six facades are KEPT because Android Compose has the same API from its own
  source set. Those six are where the residual risk sits — a kept facade could still carry a
  desktop-only overload of the API it is kept for.

WHAT THE COMPOSE STAGE THEREFORE CANNOT SEE

  * A signature that changed between Compose 1.8.2 and 1.11.4 (or Material3 1.3 and 1.4). If the
    app calls the 1.11 shape and 1.8 accepts it too, this lane is silent either way; if 1.8
    accepts something 1.11 removed, this lane is GREEN where the merge gate is RED.
  * Anything a stub declares more loosely than the real artifact. The stubs are written to be
    no more permissive than the real API and several are deliberately narrower, but they are
    hand-written and that is a promise, not a proof.

WHAT IT DOES CLOSE, that nothing else here could

  * The whole ViewModel-to-screen boundary. A UI-state type or a callback arity that changed on
    one side and not the other is a compile error, and this is the only lane that sees it.
  * The whole ViewModel-to-TEST boundary. A renamed or re-signatured main declaration whose test
    caller was not updated is a compile error in the test stage, which is what
    `./gradlew testDebugUnitTest` would report.
  * Cross-file Compose type flow: a design token, a shared composable's parameter list, a
    repo-internal callback signature.
  * app/src/debug/java, which AGP compiles into the debug variant assembleDebug builds.
  * R references, checked against the real app/src/main/res AND app/src/debug/res.

WHAT NO STAGE COMPILES

  * app/src/androidTest/java (23 files). It needs androidx.compose.ui.test and
    androidx.test.ext.junit, and `assembleDebug` does not build it either — only
    connectedDebugAndroidTest does, which is outside the stated merge gate. It is guarded by
    tools/check-lambda-arity.py, not by a compiler.
LEDGER
    exit 0
fi

JARS="${PT_JARS:-build/test-jars}"
CACHE="${PT_COMPILE_CACHE:-${TMPDIR:-/tmp}/pt-compile-check}"
MAVEN="${PT_MAVEN:-https://repo1.maven.org/maven2}"
CATALOG=gradle/libs.versions.toml
SRC_ROOT=app/src/main/java
# AGP compiles src/debug/java into the debug variant that the merge gate's `assembleDebug`
# builds, and merges src/debug/res into that variant's R class. Both are part of the gate, so
# both are part of this lane: the sources join the compose stage (they are all @Preview
# composables) and the res root is read by the R generator alongside main's.
DEBUG_SRC_ROOT=app/src/debug/java
DEBUG_RES_DIR=app/src/debug/res
# app/build.gradle.kts wires src/sharedTest/java into BOTH the test and androidTest source sets;
# the merge gate's testDebugUnitTest compiles test + sharedTest together.
TEST_SRC_ROOT=app/src/test/java
SHARED_TEST_SRC_ROOT=app/src/sharedTest/java
RES_DIR=app/src/main/res
GRADLE_FILE=app/build.gradle.kts
STUBS=tools/compile-stubs
COMPOSE_STUBS=tools/compose-stubs
TEST_STUBS=tools/test-stubs

[ -d "$SRC_ROOT" ] || fail "no $SRC_ROOT — run this from anywhere inside the repo."
# The res roots AGP merges for the debug variant, in AGP's override order (later wins; nothing
# in this project overrides, and the generator would union them anyway).
RES_DIRS="$RES_DIR"
[ -d "$DEBUG_RES_DIR" ] && RES_DIRS="$RES_DIRS $DEBUG_RES_DIR"
command -v java >/dev/null 2>&1 || fail "java is not on PATH."
command -v python3 >/dev/null 2>&1 || fail "python3 is not on PATH (the R/BuildConfig generators need it)."

catalog_ver() {
    sed -n "/^\\[versions\\]/,/^\\[/{ s/^$1 = \"\\(.*\\)\"/\\1/p; }" "$CATALOG" | head -1
}
KOTLIN="$(catalog_ver kotlin)";          KOTLIN="${KOTLIN:-2.0.21}"
COROUTINES="$(catalog_ver coroutines)";  COROUTINES="${COROUTINES:-1.10.2}"
GSON="$(catalog_ver gson)";              GSON="${GSON:-2.11.0}"
JUNIT="$(catalog_ver junit)";            JUNIT="${JUNIT:-4.13.2}"
ROBOLECTRIC="$(catalog_ver robolectric)"; ROBOLECTRIC="${ROBOLECTRIC:-4.16}"
# The catalog pins the INSTRUMENTED android-all ("...-i7"), which Robolectric needs at runtime.
# This lane wants the plain one: the instrumented classes carry Robolectric's ShadowedObject
# supertype and $$robo$ synthetic members, which both break subclassing (`class
# PersonalTrainerApp : Application()` cannot implement $$robo$getData) and add members that
# could absorb a typo. Same AOSP build, same signatures, minus the instrumentation.
AA_VER="$(catalog_ver robolectricAndroidAll)"; AA_VER="${AA_VER:-15-robolectric-13954326-i7}"
AA_VER="$(printf '%s' "$AA_VER" | sed 's/-i[0-9]*$//')"
# androidx.annotation is on its own version line: JetBrains' republish of it (the only route to
# androidx.annotation.DrawableRes on Maven Central) has exactly one published version.
ANNOTATION_VER=1.8.0-alpha01
# Compose Multiplatform, the substitute for the app's androidx.compose.* — see --explain.
# NOT taken from the version catalog on purpose: the catalog pins a Compose BOM that resolves on
# Google's Maven and has no meaning here, so pinning this separately keeps the substitution
# visible instead of dressing it up as the version the app really builds against.
# 1.9.x is NOT an option: from CMP 1.9 the runtime artifact on Maven Central is an empty
# placeholder that redirects to androidx.compose.runtime on Google's Maven (verified:
# runtime-desktop-1.9.3.jar is 416 bytes and contains no classes).
COMPOSE_MP=1.8.2
# JetBrains published no material-icons after 1.7.3. Mixing it with 1.8.2 is verified to compile,
# and all eight icons the app imports are in the -core artifact, so -extended (37 MB) is not used.
COMPOSE_ICONS=1.7.3

mkdir -p "$CACHE" || fail "cannot create cache directory $CACHE"

# --- jar resolution -----------------------------------------------------------------------
# THREE classpaths, kept apart on purpose.
#
#   TOOL_CP  runs the compiler itself (java -cp). It needs kotlin-reflect, kotlin-script-runtime
#            and trove4j, none of which the app depends on.
#   APP_CP   is what app sources are compiled AGAINST (-cp) in the base stage. It holds exactly
#            the app's real `implementation` dependencies plus the Android framework.
#   CMP_CP   is added to APP_CP for the compose stage only, and holds nothing else.
#
# Merging them is a quiet false-green: with kotlin-reflect on the compile classpath a main
# source could call full reflection and pass here while Gradle rejects it, and the same goes for
# the JUnit and hamcrest jars sitting in build/test-jars, which are testImplementation-only.
# Keeping CMP_CP separate is the same argument one level down — the base stage's guarantee is
# that no Compose declaration was in scope at all, and that is only true if the list is separate.
# That is also why no list is ever built with `find $JARS -name '*.jar'` — every jar is named,
# and anything else in those directories is ignored.
TOOL_CP=""
APP_CP=""
CMP_CP=""
TEST_CP=""
RESOLVED=""
resolve() { # resolve <tool|app|both|compose|test|none> <filename> <maven path under $MAVEN>
    use="$1"; name="$2"; path="$3"
    if [ -f "$JARS/$name" ]; then found="$JARS/$name"
    elif [ -f "$CACHE/$name" ]; then found="$CACHE/$name"
    else
        printf 'compile-check: fetching %s\n' "$name" >&2
        if ! curl -sfL -o "$CACHE/$name.part" "$MAVEN/$path"; then
            rm -f "$CACHE/$name.part"
            fail "cannot download $MAVEN/$path
  This lane needs Maven Central. Everything it fetches is published there; nothing it needs
  comes from Google's Maven. If you are offline, pre-populate \$PT_COMPILE_CACHE ($CACHE)."
        fi
        mv "$CACHE/$name.part" "$CACHE/$name"
        found="$CACHE/$name"
    fi
    case "$use" in
        tool|both) TOOL_CP="$TOOL_CP${TOOL_CP:+:}$found" ;;
    esac
    case "$use" in
        app|both) APP_CP="$APP_CP${APP_CP:+:}$found" ;;
    esac
    case "$use" in
        compose) CMP_CP="$CMP_CP${CMP_CP:+:}$found" ;;
    esac
    case "$use" in
        test) TEST_CP="$TEST_CP${TEST_CP:+:}$found" ;;
    esac
    # `none` fetches and caches without joining any classpath — used for the compiler plugin,
    # which is passed by path with -Xplugin and belongs on no compile classpath at all.
    RESOLVED="$found"
}

# The compiler's own runtime. kotlin-reflect, kotlin-script-runtime and trove4j are here and
# NOT on APP_CP: the compiler will not start without them, and the app does not depend on them.
resolve tool "kotlin-compiler-embeddable-$KOTLIN.jar" "org/jetbrains/kotlin/kotlin-compiler-embeddable/$KOTLIN/kotlin-compiler-embeddable-$KOTLIN.jar"
resolve tool "kotlin-reflect-$KOTLIN.jar"             "org/jetbrains/kotlin/kotlin-reflect/$KOTLIN/kotlin-reflect-$KOTLIN.jar"
resolve tool "kotlin-script-runtime-$KOTLIN.jar"      "org/jetbrains/kotlin/kotlin-script-runtime/$KOTLIN/kotlin-script-runtime-$KOTLIN.jar"
resolve tool "trove4j-1.0.20200330.jar"               "org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar"
# Both: the compiler needs them to run, and app sources compile against them.
resolve both "kotlin-stdlib-$KOTLIN.jar"              "org/jetbrains/kotlin/kotlin-stdlib/$KOTLIN/kotlin-stdlib-$KOTLIN.jar"
resolve both "annotations-13.0.jar"                   "org/jetbrains/annotations/13.0/annotations-13.0.jar"
resolve both "kotlinx-coroutines-core-jvm-$COROUTINES.jar" "org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/$COROUTINES/kotlinx-coroutines-core-jvm-$COROUTINES.jar"
# App dependencies only, at the versions gradle/libs.versions.toml pins.
resolve app  "gson-$GSON.jar"                         "com/google/code/gson/gson/$GSON/gson-$GSON.jar"
resolve app  "annotation-desktop-$ANNOTATION_VER.jar" "org/jetbrains/compose/annotation-internal/annotation-desktop/$ANNOTATION_VER/annotation-desktop-$ANNOTATION_VER.jar"

# --- the Compose classpath (compose stage only) ----------------------------------------------
# This list is deliberately MINIMAL — it is the smallest set the app's sources actually resolve
# against, verified by removing jars until something broke. Two that a dependency resolver would
# have pulled in are left out on purpose:
#
#   ui-backhandler-desktop  declares androidx.compose.ui.backhandler.BackHandler, the Compose
#                           Multiplatform back handler. It takes the same arguments as the
#                           Android androidx.activity.compose.BackHandler that this app really
#                           uses, so leaving it on the classpath would let a wrong import compile
#                           here and fail under Gradle. Off the classpath, that import is an
#                           unresolved reference, which is the answer the merge gate gives.
#   material-desktop        Material 2. The app is Material 3 only; nothing should resolve there.
COMPOSE_PLUGIN=""
# The test stage compiles against the debug variant's classes, which include the Compose screens,
# so it needs the same Compose classpath and the same plugin the compose stage uses.
if [ "$RUN_COMPOSE" -eq 1 ] || [ "$RUN_TESTS" -eq 1 ]; then
    resolve compose "runtime-desktop-$COMPOSE_MP.jar"          "org/jetbrains/compose/runtime/runtime-desktop/$COMPOSE_MP/runtime-desktop-$COMPOSE_MP.jar"
    resolve compose "runtime-saveable-desktop-$COMPOSE_MP.jar" "org/jetbrains/compose/runtime/runtime-saveable-desktop/$COMPOSE_MP/runtime-saveable-desktop-$COMPOSE_MP.jar"
    resolve compose "ui-desktop-$COMPOSE_MP.jar"               "org/jetbrains/compose/ui/ui-desktop/$COMPOSE_MP/ui-desktop-$COMPOSE_MP.jar"
    resolve compose "ui-geometry-desktop-$COMPOSE_MP.jar"      "org/jetbrains/compose/ui/ui-geometry-desktop/$COMPOSE_MP/ui-geometry-desktop-$COMPOSE_MP.jar"
    resolve compose "ui-graphics-desktop-$COMPOSE_MP.jar"      "org/jetbrains/compose/ui/ui-graphics-desktop/$COMPOSE_MP/ui-graphics-desktop-$COMPOSE_MP.jar"
    resolve compose "ui-text-desktop-$COMPOSE_MP.jar"          "org/jetbrains/compose/ui/ui-text-desktop/$COMPOSE_MP/ui-text-desktop-$COMPOSE_MP.jar"
    resolve compose "ui-unit-desktop-$COMPOSE_MP.jar"          "org/jetbrains/compose/ui/ui-unit-desktop/$COMPOSE_MP/ui-unit-desktop-$COMPOSE_MP.jar"
    resolve compose "ui-util-desktop-$COMPOSE_MP.jar"          "org/jetbrains/compose/ui/ui-util-desktop/$COMPOSE_MP/ui-util-desktop-$COMPOSE_MP.jar"
    resolve compose "foundation-desktop-$COMPOSE_MP.jar"       "org/jetbrains/compose/foundation/foundation-desktop/$COMPOSE_MP/foundation-desktop-$COMPOSE_MP.jar"
    resolve compose "foundation-layout-desktop-$COMPOSE_MP.jar" "org/jetbrains/compose/foundation/foundation-layout-desktop/$COMPOSE_MP/foundation-layout-desktop-$COMPOSE_MP.jar"
    resolve compose "material3-desktop-$COMPOSE_MP.jar"        "org/jetbrains/compose/material3/material3-desktop/$COMPOSE_MP/material3-desktop-$COMPOSE_MP.jar"
    resolve compose "animation-desktop-$COMPOSE_MP.jar"        "org/jetbrains/compose/animation/animation-desktop/$COMPOSE_MP/animation-desktop-$COMPOSE_MP.jar"
    resolve compose "animation-core-desktop-$COMPOSE_MP.jar"   "org/jetbrains/compose/animation/animation-core-desktop/$COMPOSE_MP/animation-core-desktop-$COMPOSE_MP.jar"
    resolve compose "material-icons-core-desktop-$COMPOSE_ICONS.jar" "org/jetbrains/compose/material/material-icons-core-desktop/$COMPOSE_ICONS/material-icons-core-desktop-$COMPOSE_ICONS.jar"
    # The Compose compiler plugin. This one is NOT a substitution: it is the same
    # org.jetbrains.kotlin.plugin.compose the app applies, at the same Kotlin version, so the
    # @Composable transform and its diagnostics are exactly the merge gate's.
    resolve none "kotlin-compose-compiler-plugin-embeddable-$KOTLIN.jar" "org/jetbrains/kotlin/kotlin-compose-compiler-plugin-embeddable/$KOTLIN/kotlin-compose-compiler-plugin-embeddable-$KOTLIN.jar"
    COMPOSE_PLUGIN="$RESOLVED"
    [ -d "$COMPOSE_STUBS" ] || fail "no $COMPOSE_STUBS — the compose stage cannot run without it."

    # --- the desktop-only strip -------------------------------------------------------------
    # THE ONE WAY THE COMPOSE SUBSTITUTION CAN PRODUCE A FALSE GREEN is an app source calling
    # something that exists in JetBrains Compose Multiplatform and NOT in the Android Compose the
    # merge gate builds against. This used to be five greps over the sources; greps are not a
    # guard. Two of them matched only a literal `"` after the open paren and three were anchored
    # to ^import, so a fully-qualified call, an aliased import or a star import walked straight
    # past them — VerticalScrollbar, rememberScrollbarAdapter, Modifier.onClick,
    # Modifier.onPointerEvent, androidx.compose.ui.res.useResource and painterResource(<a String
    # expression>) all compiled clean while existing in no version of Android Compose.
    #
    # They are removed from the classpath instead. The jars are rewritten once into filtered
    # copies with every desktop-only class deleted, so no spelling of a call can resolve to one:
    # it is not a pattern that has to anticipate how the call is written, it is the absence of
    # the declaration. The negative control in the compose stage compiles all six and fails the
    # lane if any of them still resolves.
    #
    # HOW THE STRIP SET IS DERIVED, and where its residual risk is, is written out in the Python
    # below. The honest summary: Google's Maven is unreachable here, so there is NO copy of
    # Android Compose to diff against; the set is enumerated mechanically from the CMP jars and
    # then audited, and the audit is what carries the risk.
    CMP_FILTERED="$CACHE/cmp-filtered-$COMPOSE_MP"
    cmp_stale=0
    for j in $(printf '%s' "$CMP_CP" | tr ':' ' '); do
        f="$CMP_FILTERED/$(basename "$j")"
        if [ ! -f "$f" ] || [ "$j" -nt "$f" ] || [ "$0" -nt "$f" ]; then cmp_stale=1; fi
    done
    if [ "$cmp_stale" -eq 1 ]; then
        # shellcheck disable=SC2086
        python3 - "$CMP_FILTERED" $(printf '%s' "$CMP_CP" | tr ':' ' ') <<'CMPFILTEREOF' || fail "could not filter the Compose Multiplatform jars"
import os, re, sys, zipfile

# Rewrite each Compose Multiplatform jar with its desktop-only classes removed, so that an app
# source cannot resolve to one however the call is spelled.
#
# GROUND TRUTH, OR THE LACK OF IT. The right check would be "is this declaration in the Android
# Compose artifact?", and it cannot be asked here: androidx.compose lives on Google's Maven and
# this environment has no route to it. What CAN be asked mechanically is "which source set did
# this class come from?", because Kotlin names a file facade after its source file and Compose
# Multiplatform keeps its non-Android code in *.desktop.kt / *.skiko.kt / *.awt.kt files under
# desktopMain/ and skikoMain/ (verified against foundation-desktop-1.8.2-sources.jar). That is a
# candidate set, not an answer: some of those files hold Compose Multiplatform's own
# implementation of an API that Android Compose ALSO has, from its own androidMain. Those are the
# KEEP entries below — each was found by stripping everything and compiling the app until it
# named the symbol that had gone missing, and each is where this guard's residual risk lives: a
# kept facade may still carry a desktop-only overload of the API it is kept for.
#
# Everything else is safe in the direction that matters. Removing a class Android does have can
# only produce a RED the merge gate would not — visible, and fixed by adding a KEEP entry with a
# reason. It cannot produce a green the merge gate would not.

KEEP = {
    # DropdownMenu / DropdownMenuItem — Material 3 on Android has both.
    'androidx/compose/material3/SkikoMenu_skikoKt',
    # AlertDialog / BasicAlertDialog.
    'androidx/compose/material3/AlertDialog_skikoKt',
    # navigationBarsPadding / imePadding / statusBarsPadding and the rest of the window-insets
    # modifiers, which on Android live in foundation-layout just the same.
    'androidx/compose/foundation/layout/WindowInsetsPadding_skikoKt',
    # The Path() factory behind androidx.compose.ui.graphics.Path.
    'androidx/compose/ui/graphics/SkiaBackedPath_skikoKt',
    # Dialog and Popup, which are real androidx.compose.ui.window API.
    'androidx/compose/ui/window/Dialog_skikoKt',
    'androidx/compose/ui/window/Popup_skikoKt',
}

# androidx.compose.ui.window on Android is a SMALL, stable package: Dialog, DialogProperties,
# Popup, PopupProperties, PopupPositionProvider, SecureFlagPolicy, DialogWindowProvider. The
# desktop build puts its entire windowing and menu-bar API in that same package, so this rule is
# inverted — keep the ones Android has, drop the rest (Window, application, Tray, MenuBar,
# WindowState, FrameWindowScope, ...).
WINDOW_KEEP = {
    'Dialog_skikoKt', 'Popup_skikoKt', 'DialogProperties', 'PopupProperties',
    'PopupPositionProvider', 'SecureFlagPolicy', 'DialogWindowProvider',
    'AlignmentOffsetPositionProvider',
}

STRIP = [
    # File facades compiled from a *.desktop.kt / *.skiko.kt / *.awt.kt source file.
    re.compile(r'_(?:desktop|skiko|awt)Kt$'),
    # Whole packages with no Android counterpart at all.
    re.compile(r'^androidx/compose/ui/awt/'),         # ComposeWindow, ComposePanel, SwingPanel
    re.compile(r'^androidx/compose/ui/scene/'),       # ComposeScene, the desktop scene host
    re.compile(r'^androidx/compose/desktop/'),
    re.compile(r'^androidx/compose/foundation/v2/'),  # the v2 scrollbar API
    # Desktop-only classes in a package Android does have.
    re.compile(r'^androidx/compose/foundation/'
               r'(Scrollbar|ScrollbarAdapter|ScrollbarStyle|DesktopPlatform'
               r'|NewScrollbarAdapterAsOld|OldScrollbarAdapterAsNew)'),
]
WINDOW = re.compile(r'^androidx/compose/ui/window/([^/]+)$')

def is_desktop_only(outer):
    if outer in KEEP:
        return False
    m = WINDOW.match(outer)
    if m and m.group(1) not in WINDOW_KEEP:
        return True
    return any(p.search(outer) for p in STRIP)

outdir, jars = sys.argv[1], sys.argv[2:]
os.makedirs(outdir, exist_ok=True)
total = 0
for jar in jars:
    dest = os.path.join(outdir, os.path.basename(jar))
    removed = 0
    with zipfile.ZipFile(jar) as z, zipfile.ZipFile(dest + '.part', 'w', zipfile.ZIP_STORED) as o:
        for info in z.infolist():
            n = info.filename
            if n.endswith('.class') and is_desktop_only(n[:-6].split('$', 1)[0]):
                removed += 1
                continue
            o.writestr(info, z.read(info))
    os.replace(dest + '.part', dest)
    total += removed
if total == 0:
    sys.exit("compile-check: the desktop-only strip removed NOTHING. Either the Compose\n"
             "Multiplatform jars changed shape or the rules above stopped matching; either way\n"
             "the guard against desktop-only APIs is not in force. Fix it rather than proceed.")
sys.stderr.write("compile-check: removed %d desktop-only classes from %d Compose Multiplatform jars\n"
                 % (total, len(jars)))
CMPFILTEREOF
    fi
    CMP_FILTERED_CP=""
    for j in $(printf '%s' "$CMP_CP" | tr ':' ' '); do
        f="$CMP_FILTERED/$(basename "$j")"
        [ -f "$f" ] || fail "the filtered Compose jar $f was not produced."
        CMP_FILTERED_CP="$CMP_FILTERED_CP${CMP_FILTERED_CP:+:}$f"
    done
    CMP_CP="$CMP_FILTERED_CP"
fi
# --- test-only dependencies (test stage only) -----------------------------------------------
# `testImplementation` and nothing else. These are kept on their own classpath (TEST_CP) so they
# cannot leak into the base or compose stages: JUnit or coroutines-test visible from a main
# source would be a green here and a red under Gradle.
#
# Robolectric itself is REAL, not a stub — org.robolectric publishes to Maven Central, so
# RobolectricTestRunner, @Config, Shadows.shadowOf and Robolectric.buildService are the actual
# classes at the version gradle/libs.versions.toml pins. Only androidx.test:core,
# androidx.test:monitor and androidx.room:room-testing have no Maven Central route; those three
# are stubbed in tools/test-stubs/.
if [ "$RUN_TESTS" -eq 1 ]; then
    resolve test "junit-$JUNIT.jar"          "junit/junit/$JUNIT/junit-$JUNIT.jar"
    # hamcrest-core is junit 4.13.2's own dependency; org.junit.Assert's signatures name
    # org.hamcrest.Matcher, so the compiler needs it even though no test imports it.
    resolve test "hamcrest-core-1.3.jar"     "org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
    resolve test "kotlinx-coroutines-test-jvm-$COROUTINES.jar" \
        "org/jetbrains/kotlinx/kotlinx-coroutines-test-jvm/$COROUTINES/kotlinx-coroutines-test-jvm-$COROUTINES.jar"
    resolve test "robolectric-$ROBOLECTRIC.jar"          "org/robolectric/robolectric/$ROBOLECTRIC/robolectric-$ROBOLECTRIC.jar"
    resolve test "robolectric-annotations-$ROBOLECTRIC.jar"       "org/robolectric/annotations/$ROBOLECTRIC/annotations-$ROBOLECTRIC.jar"
    resolve test "robolectric-shadowapi-$ROBOLECTRIC.jar"         "org/robolectric/shadowapi/$ROBOLECTRIC/shadowapi-$ROBOLECTRIC.jar"
    resolve test "robolectric-shadows-framework-$ROBOLECTRIC.jar" "org/robolectric/shadows-framework/$ROBOLECTRIC/shadows-framework-$ROBOLECTRIC.jar"
    resolve test "robolectric-junit-$ROBOLECTRIC.jar"             "org/robolectric/junit/$ROBOLECTRIC/junit-$ROBOLECTRIC.jar"
    [ -d "$TEST_STUBS" ] || fail "no $TEST_STUBS — the test stage cannot run without it."
fi

# --- the Android framework ------------------------------------------------------------------
# Robolectric's android-all jar is a real AOSP build: android.app.Application,
# android.content.Context, android.os.*, android.database.sqlite.*, org.json.* — actual
# signatures, not stubs. It is ~186 MB, so it is cached outside the repo and never re-fetched.
AA_NAME="android-all-$AA_VER.jar"
AA_SRC="${PT_ANDROID_ALL_JAR:-}"
if [ -n "$AA_SRC" ]; then
    [ -f "$AA_SRC" ] || fail "PT_ANDROID_ALL_JAR points at $AA_SRC, which does not exist."
elif [ -f "$CACHE/$AA_NAME" ]; then
    AA_SRC="$CACHE/$AA_NAME"
else
    printf 'compile-check: fetching %s (~186 MB, cached in %s)\n' "$AA_NAME" "$CACHE" >&2
    curl -sfL -o "$CACHE/$AA_NAME.part" \
        "$MAVEN/org/robolectric/android-all/$AA_VER/android-all-$AA_VER.jar" \
        || { rm -f "$CACHE/$AA_NAME.part"; fail "cannot download android-all $AA_VER from Maven Central."; }
    mv "$CACHE/$AA_NAME.part" "$CACHE/$AA_NAME"
    AA_SRC="$CACHE/$AA_NAME"
fi

# android-all also ships Android's forks of javax.xml, org.w3c.dom and org.xml.sax, which
# SHADOW the JDK's. Nothing in this app touches XML, so it changes no result today — but a
# classpath that quietly replaces JDK types is exactly the kind of thing that makes a lane
# untrustworthy later, so they are stripped once into a filtered copy. java/* is stripped for
# the same reason (those entries are Android-internal helpers, not JDK classes, but the rule is
# cheaper to keep absolute than to re-audit).
AA_JAR="$CACHE/android-all-$AA_VER-nojdk.jar"
if [ ! -f "$AA_JAR" ] || [ "$AA_SRC" -nt "$AA_JAR" ]; then
    command -v zip >/dev/null 2>&1 || fail "the 'zip' command is needed once, to strip JDK-shadowing packages from android-all."
    printf 'compile-check: stripping JDK-shadowing packages from %s\n' "$AA_NAME" >&2
    cp "$AA_SRC" "$AA_JAR.part" || fail "cannot write $AA_JAR.part"
    zip -q -d "$AA_JAR.part" 'java/*' 'javax/xml/*' 'org/w3c/*' 'org/xml/*' >/dev/null 2>&1
    mv "$AA_JAR.part" "$AA_JAR"
fi
APP_CP="$APP_CP:$AA_JAR"
WORK=$(mktemp -d) || fail "cannot create a work directory."
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen/common" "$WORK/out"

# --- generated sources ----------------------------------------------------------------------
# R and BuildConfig are emitted by AGP from res/ and the Gradle config. Both are DERIVED here
# from those same inputs rather than hand-written, so `R.drawable.foo` for a drawable that does
# not exist is a real compile error, exactly as in the merge gate.
python3 - com.sinura.personaltrainer "$WORK/gen/common/R.kt" $RES_DIRS <<'PYEOF' || fail "could not generate R.kt"
import os, re, sys, xml.etree.ElementTree as ET

pkg, out, res_dirs = sys.argv[1], sys.argv[2], sys.argv[3:]
if not res_dirs:
    sys.exit("compile-check: the R generator was given no res roots.")
files, ids, values = {}, set(), {}
VALUE_TAG_TO_TYPE = {
    'string': 'string', 'color': 'color', 'dimen': 'dimen', 'integer': 'integer',
    'bool': 'bool', 'style': 'style', 'string-array': 'array', 'integer-array': 'array',
    'array': 'array', 'plurals': 'plurals', 'attr': 'attr', 'drawable': 'drawable',
    'fraction': 'fraction', 'declare-styleable': 'styleable',
}
for res_dir in res_dirs:
  if not os.path.isdir(res_dir):
    sys.exit("compile-check: res root %s does not exist." % res_dir)
  for entry in sorted(os.listdir(res_dir)):
    d = os.path.join(res_dir, entry)
    if not os.path.isdir(d):
        continue
    rtype = entry.split('-', 1)[0]
    for f in sorted(os.listdir(d)):
        path = os.path.join(d, f)
        if not os.path.isfile(path):
            continue
        if rtype == 'values':
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError as e:
                sys.exit("compile-check: cannot parse %s: %s" % (path, e))
            for child in root:
                if not isinstance(child.tag, str):
                    continue
                name = child.get('name')
                if not name:
                    continue
                t = child.get('type') if child.tag == 'item' else VALUE_TAG_TO_TYPE.get(child.tag, child.tag)
                if t:
                    values.setdefault(t, set()).add(name.replace('.', '_'))
        else:
            base = f[:-6] if f.endswith('.9.png') else os.path.splitext(f)[0]
            files.setdefault(rtype, set()).add(base)
        if f.endswith('.xml'):
            with open(path, encoding='utf-8', errors='replace') as fh:
                for m in re.finditer(r'@\+id/([A-Za-z_][A-Za-z0-9_]*)', fh.read()):
                    ids.add(m.group(1))

merged = {}
for src in (files, values):
    for t, names in src.items():
        merged.setdefault(t, set()).update(names)
if ids:
    merged.setdefault('id', set()).update(ids)

lines = [
    '// GENERATED by tools/compile-check.sh — not committed, never packaged.',
    '// Res roots read: %s' % ', '.join(res_dirs),
    '//',
    '// Stands in for the R class AGP generates with aapt2. Every entry corresponds to a real',
    '// file or <resources> entry under one of those roots, so R.drawable.foo for a drawable that',
    '// does not exist is a compile error here exactly as in the merge gate. The Int VALUES are',
    '// sequential placeholders and are meaningless: nothing here is executed or shipped.',
    '//',
    '// NOT `const val`, ON PURPOSE. android.nonFinalResIds defaults to true from AGP 8.0 and',
    '// gradle.properties does not turn it off, so AGP 8.9.2 emits R ids as NON-FINAL static',
    '// fields. From Kotlin that means R.drawable.foo is an ordinary Int expression and NOT a',
    '// compile-time constant: `const val ICON = R.drawable.foo` and `@Anno(R.string.bar)` are',
    '// both errors under the merge gate. Emitting `const val` here would make them compile.',
    '//',
    '// NOT MODELLED: resources merged in from library AARs, and android.R framework resources.',
    '',
    'package %s' % pkg,
    '',
    'object R {',
]
n = 0x7f010000
for t in sorted(merged):
    if t == 'styleable':
        continue
    lines.append('    object %s {' % t)
    for name in sorted(merged[t]):
        n += 1
        lines.append('        @JvmField val %s: Int = %d' % (name, n))
    lines.append('    }')
lines.append('}')
open(out, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
sys.stderr.write('compile-check: generated R with %s\n'
                 % ', '.join('%s=%d' % (t, len(v)) for t, v in sorted(merged.items())))
PYEOF

python3 - "$GRADLE_FILE" "$WORK/gen/common/BuildConfig.kt" <<'PYEOF' || fail "could not generate BuildConfig.kt"
import re, sys
gradle, out = sys.argv[1], sys.argv[2]
src = open(gradle, encoding='utf-8').read()

def need(pattern, what):
    m = re.search(pattern, src)
    if not m:
        sys.exit("compile-check: cannot read %s from %s — the BuildConfig generator needs updating."
                 % (what, gradle))
    return m.group(1)

namespace    = need(r'namespace\s*=\s*"([^"]+)"', 'namespace')
app_id       = need(r'applicationId\s*=\s*"([^"]+)"', 'applicationId')
suffix       = re.search(r'applicationIdSuffix\s*=\s*"([^"]+)"', src)
version_name = need(r'val\s+appVersionName\s*=\s*"([^"]+)"', 'appVersionName')
version_code = need(r'val\s+appVersionCode\s*=\s*(\d+)', 'appVersionCode')

# A custom buildConfigField would add a constant this generator does not know about. Failing
# loudly is the only honest option: silently omitting it produces a false RED that looks like a
# repo defect, and guessing its type produces something worse.
if 'buildConfigField' in src:
    sys.exit("compile-check: %s declares buildConfigField(); the BuildConfig generator in\n"
             "tools/compile-check.sh models only the AGP defaults and must be taught the new field."
             % gradle)

open(out, 'w', encoding='utf-8').write('''// GENERATED by tools/compile-check.sh from app/build.gradle.kts — not committed, never packaged.
//
// Stands in for AGP's BuildConfig. The values are READ OUT of the Gradle build file rather than
// invented, and the generator aborts if that file grows a buildConfigField() it does not model,
// so this cannot quietly drift.
//
// DEBUG is deliberately not a `const`: AGP emits Boolean.parseBoolean("true") so the flag is not
// folded at compile time, and matching that keeps `if (BuildConfig.DEBUG) { ... }` from being
// reported as unreachable code here when it is not under Gradle. This lane models the DEBUG
// variant, which is what the merge gate's assembleDebug builds.

package %s

object BuildConfig {
    @JvmField val DEBUG: Boolean = java.lang.Boolean.parseBoolean("true")
    const val APPLICATION_ID: String = "%s"
    const val BUILD_TYPE: String = "debug"
    const val VERSION_CODE: Int = %s
    const val VERSION_NAME: String = "%s"
}
''' % (namespace, app_id + (suffix.group(1) if suffix else ''), version_code, version_name))
PYEOF

# --- compose boundary (base stage only) --------------------------------------------------------
# Four non-Compose files reference four declarations that happen to live INSIDE Compose files:
# MainActivity, RestLockActivity, TemperStillCache and WorkoutTestTags. The base stage cannot
# compile those Compose files, and dropping the four referrers is not an option — it cascades
# through PersonalTrainerApp into AppViewModel and from there into all 21 ViewModels, which are
# the whole point of that stage.
#
# So this generator emits a minimal declaration for each — and, crucially, VERIFIES IT FIRST.
# Every entry names the file that really declares the symbol and a regex that must still match
# there; the lane hard-fails if it does not. A rename, a deletion, or a change to
# TemperStillCache.bind's parameter type therefore turns this lane RED rather than sliding past
# it. String constants are copied out of the real source, not invented.
#
# THE COMPOSE STAGE DOES NOT USE ANY OF THIS. It compiles the whole main source set at once, so
# it has the four real declarations and needs no stand-in — which is why these files land in
# $WORK/gen/boundary and only the base stage is handed that directory. Where both stages run,
# the compose stage is what actually checks the four declarations these stand in for.
if [ "$RUN_BASE" -eq 1 ]; then
mkdir -p "$WORK/gen/boundary"
python3 - "$WORK/gen/boundary" <<'PYEOF' || fail "compose-boundary check failed"
import os, re, sys
outdir = sys.argv[1]

BOUNDARY = [
    dict(
        name="MainActivity",
        why="Intent target only: reminder/ReminderNotifications.kt and timer/RestTimerNotifications.kt "
            "build Intent(context, MainActivity::class.java).",
        file="app/src/main/java/com/sinura/personaltrainer/MainActivity.kt",
        verify=[r'(?m)^class\s+MainActivity\s*:\s*ComponentActivity\(\)'],
        emit="package com.sinura.personaltrainer\n\nclass MainActivity : android.app.Activity()\n",
    ),
    dict(
        name="RestLockActivity",
        why="Intent target only: timer/RestTimerNotifications.kt builds "
            "Intent(context, RestLockActivity::class.java).",
        file="app/src/main/java/com/sinura/personaltrainer/timer/RestLockActivity.kt",
        verify=[r'(?m)^class\s+RestLockActivity\s*:\s*ComponentActivity\(\)'],
        emit="package com.sinura.personaltrainer.timer\n\nclass RestLockActivity : android.app.Activity()\n",
    ),
    dict(
        name="TemperStillCache",
        why="PersonalTrainerApp.kt calls TemperStillCache.bind(resources) at startup. The verify "
            "regex pins the full parameter list, so a signature change is caught here.",
        file="app/src/main/java/com/sinura/personaltrainer/ui/components/PoseArtwork.kt",
        verify=[r'(?m)^internal object TemperStillCache\b',
                r'(?m)^\s*fun bind\(resources: Resources\)\s*\{'],
        emit=("package com.sinura.personaltrainer.ui.components\n\n"
              "import android.content.res.Resources\n\n"
              "internal object TemperStillCache {\n"
              "    fun bind(resources: Resources) { TODO(\"compile-only boundary stub\") }\n"
              "}\n"),
    ),
    dict(
        name="WorkoutTestTags",
        why="ui/workout/LogLoopBringIntoView.kt declares `const val ANCHOR_TAG = "
            "WorkoutTestTags.SET_ENTRY`, so the constant must be a compile-time String. Its "
            "value is copied out of the real source rather than invented.",
        file="app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutScreen.kt",
        verify=[r'(?m)^object WorkoutTestTags\b',
                r'(?m)^\s*const val SET_ENTRY = "([^"]*)"'],
        emit=("package com.sinura.personaltrainer.ui.workout\n\n"
              "object WorkoutTestTags {\n"
              "    const val SET_ENTRY = \"%s\"\n"
              "}\n"),
    ),
]

HEADER = (
    "// GENERATED by tools/compile-check.sh — not committed, never packaged.\n"
    "//\n"
    "// Boundary declaration for a symbol that lives inside a Compose file, which this phase of\n"
    "// the lane cannot compile. It was VERIFIED against its real declaration site before being\n"
    "// emitted (see the BOUNDARY table in tools/compile-check.sh); if the declaration it stands\n"
    "// for no longer matches, the lane fails instead of generating this file.\n"
    "//\n"
)
for entry in BOUNDARY:
    try:
        src = open(entry["file"], encoding="utf-8").read()
    except OSError as e:
        sys.exit("compile-check: compose boundary — cannot read %s (%s).\n"
                 "  The lane declares a stand-in for a symbol that file is supposed to declare.\n"
                 "  If the file moved, update the BOUNDARY table in tools/compile-check.sh."
                 % (entry["file"], e))
    groups = []
    for pattern in entry["verify"]:
        m = re.search(pattern, src)
        if not m:
            sys.exit("compile-check: compose boundary — %s no longer matches\n"
                     "    %s\n"
                     "  The lane emits a stand-in declaration for it and will NOT guess. Either the\n"
                     "  declaration changed (fix the BOUNDARY table in tools/compile-check.sh and the\n"
                     "  emitted stand-in to match) or it was removed (fix the callers)."
                     % (entry["file"], pattern))
        groups.extend(m.groups())
    body = entry["emit"] % tuple(groups) if groups else entry["emit"]
    # One file per declaration: Kotlin allows a single package statement per file.
    with open(os.path.join(outdir, entry["name"] + ".kt"), "w", encoding="utf-8") as fh:
        fh.write(HEADER + "// %s\n// verified against %s\n\n" % (entry["why"], entry["file"]) + body)
PYEOF
fi

# --- the source set ----------------------------------------------------------------------------
# The base stage takes everything under app/src/main/java that does not import androidx.compose.
# The compose stage takes ALL of it. The partition is computed here rather than listed, and both
# counts are printed on every run, so the boundary cannot silently drift.
find "$SRC_ROOT" -name '*.kt' | sort > "$WORK/all.txt"
grep -rl 'androidx\.compose' "$SRC_ROOT" --include='*.kt' | sort > "$WORK/compose.txt"
comm -23 "$WORK/all.txt" "$WORK/compose.txt" > "$WORK/base.txt"

# app/src/debug/java is part of the DEBUG VARIANT AGP assembles, so `assembleDebug` — the second
# half of the merge gate — compiles it. Every file in it is Compose (@Preview galleries and their
# fixtures), so it joins the compose stage and not the base stage.
: > "$WORK/debug.txt"
[ -d "$DEBUG_SRC_ROOT" ] && find "$DEBUG_SRC_ROOT" -name '*.kt' | sort > "$WORK/debug.txt"
cat "$WORK/all.txt" "$WORK/debug.txt" > "$WORK/compose-all.txt"

TOTAL=$(wc -l < "$WORK/all.txt" | tr -d ' ')
COMPOSE=$(wc -l < "$WORK/compose.txt" | tr -d ' ')
BASE=$(wc -l < "$WORK/base.txt" | tr -d ' ')
DEBUG_FILES=$(wc -l < "$WORK/debug.txt" | tr -d ' ')
COMPOSE_TOTAL=$(wc -l < "$WORK/compose-all.txt" | tr -d ' ')
[ "$BASE" -gt 0 ] || fail "the base source set is empty — the compose filter or $SRC_ROOT is wrong."
[ "$TOTAL" -gt 0 ] || fail "no Kotlin sources found under $SRC_ROOT."
if [ -d "$DEBUG_SRC_ROOT" ] && [ "$DEBUG_FILES" -eq 0 ]; then
    fail "$DEBUG_SRC_ROOT exists but holds no .kt — the debug source set went missing from this lane."
fi

# The unit-test source set: app/src/test/java plus app/src/sharedTest/java, which
# app/build.gradle.kts wires into the `test` source set with
# getByName("test").java.srcDir("$projectDir/src/sharedTest/java").
: > "$WORK/test-all.txt"
if [ "$RUN_TESTS" -eq 1 ]; then
    for root in "$TEST_SRC_ROOT" "$SHARED_TEST_SRC_ROOT"; do
        [ -d "$root" ] && find "$root" -name '*.kt' >> "$WORK/test-all.txt"
    done
    # One sort over BOTH roots: comm(1) below needs a globally sorted list, not two sorted runs.
    sort -o "$WORK/test-all.txt" "$WORK/test-all.txt"
    [ -s "$WORK/test-all.txt" ] || fail "no Kotlin sources under $TEST_SRC_ROOT or $SHARED_TEST_SRC_ROOT."
fi

# .java in any source set this lane claims to cover would be invisible to it entirely, so say so
# rather than pass quietly.
for jroot in app/src/main app/src/debug app/src/test app/src/sharedTest; do
    if [ -n "$(find "$jroot" -name '*.java' -print -quit 2>/dev/null)" ]; then
        fail "$jroot now contains .java sources, which this lane does not compile at all."
    fi
done

# --- the substitution guard ---------------------------------------------------------------------
# It used to live here, as five greps over app/src. It is now structural and lives on the
# CLASSPATH: the desktop-only classes are deleted from the Compose Multiplatform jars before
# anything is compiled (see "the desktop-only strip" above), so there is no declaration left for
# an app source to resolve to, however the call is spelled. The negative control that proves the
# strip is still in force runs with the compose stage, below.

# --- compiling ----------------------------------------------------------------------------------
kotlinc() {
    java -Xmx4g -cp "$TOOL_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"
}

# `compile <out-dir> <file-list> <log>` — 0 when the compiler reported no error.
# CP, SRC_DIRS and PLUGIN_ARGS are set by the caller: the two stages differ only in those three.
CP=""
SRC_DIRS=""
PLUGIN_ARGS=""
MODULE_NAME=app
compile() {
    outdir="$1"; list="$2"; log="$3"
    rm -rf "$outdir"; mkdir -p "$outdir"
    # -nowarn: this lane is about errors. Warnings are Gradle's job, and the stubs would produce
    # noise ("expected performance impact" on the inline WorkManager helper) that means nothing.
    # -no-stdlib/-no-reflect: the stdlib on the compile classpath is the one resolve() fetched at
    # the catalog's version, not whatever the compiler jar happens to carry.
    # shellcheck disable=SC2046
    kotlinc -nowarn -no-stdlib -no-reflect -jvm-target 17 -module-name "$MODULE_NAME" \
        $PLUGIN_ARGS -cp "$CP" -d "$outdir" \
        $SRC_DIRS $(cat "$list") > "$log" 2>&1
    status=$?
    # Three independent signals, because each alone can lie. A compiler that dies (OOM, an
    # internal error, a bad classpath) exits nonzero having printed no "error:" line at all, and
    # reporting OK on that would be the worst kind of false green. A compiler handed an empty or
    # wrong source list exits 0 having compiled nothing. Clean means all three: exit 0, no error
    # line, and classes actually on disk.
    if [ "$status" -ne 0 ]; then
        [ "${QUIET_COMPILE:-0}" -eq 1 ] || printf 'compile-check: the Kotlin compiler exited %s.\n' "$status" >&2
        return 1
    fi
    grep -q 'error:' "$log" && return 1
    if [ ! -d "$outdir/com/sinura" ]; then
        echo "compile-check: the compiler reported success but wrote no classes — the source list is wrong." >&2
        return 1
    fi
    return 0
}

# --- the stubs module ---------------------------------------------------------------------------
# The stubs are compiled FIRST, into their own output directory under their own -module-name, and
# that output goes on the classpath of the app compilation. They are NOT handed to the app compile
# as source directories.
#
# WHY THAT MATTERS: `internal` in Kotlin means "visible inside this MODULE". Compiling the stubs
# and app/src/main together under one -module-name put them in the same module, so every
# `internal` declaration in tools/compile-stubs/ and tools/compose-stubs/ was reachable from app
# code — 29 `internal constructor`s among them. `Preferences.Key<Boolean>("x")`,
# `OneTimeWorkRequest.Builder()`, `MutablePreferences()`, `androidx.work.Data()`,
# `WorkerParameters()` and `AuthorizationRequest.Builder()` all compiled clean and all fail under
# Gradle, where those constructors live in another artifact. Precompiling makes `internal` mean
# what it means in a real jar: invisible.
#
# The stubs output is placed BEFORE the Compose Multiplatform jars on the classpath, because
# tools/compose-stubs/compose_ui_windowinfo.kt deliberately SHADOWS the 1.8.2 WindowInfo (see that
# file). Source used to win over the classpath for free; now it is classpath-order, and
# --self-test's positive control asserts the shadow still applies.
STUBS_OUT="$WORK/stubs"
# NOTE the stub_-prefixed variable names: POSIX sh has no locals, and this function is called
# from inside the self-tests, which have their own $st/$out.
build_stubs() { # build_stubs <out-dir> <classpath> <plugin args> <source dirs...>
    stub_out="$1"; stub_cp="$2"; stub_plugin="$3"; shift 3
    rm -rf "$stub_out"; mkdir -p "$stub_out"
    # shellcheck disable=SC2086
    kotlinc -nowarn -no-stdlib -no-reflect -jvm-target 17 -module-name stubs \
        $stub_plugin -cp "$stub_cp" -d "$stub_out" "$@" > "$WORK/stubs.log" 2>&1
    stub_status=$?
    if [ "$stub_status" -ne 0 ] || grep -q 'error:' "$WORK/stubs.log"; then
        grep 'error:' "$WORK/stubs.log" >&2
        fail "the stubs in $* do not compile on their own.
  They are built as a separate module (-module-name stubs) so that their \`internal\`
  declarations are invisible to app sources, exactly as they would be in a real artifact."
    fi
}

use_base_classpath() {
    build_stubs "$STUBS_OUT-base" "$APP_CP" "" "$STUBS"
    CP="$STUBS_OUT-base:$APP_CP"
    SRC_DIRS="$WORK/gen/common $WORK/gen/boundary"
    PLUGIN_ARGS=""
    MODULE_NAME=app
}
use_compose_classpath() {
    build_stubs "$STUBS_OUT-compose" "$APP_CP:$CMP_CP" "-Xplugin=$COMPOSE_PLUGIN" \
        "$STUBS" "$COMPOSE_STUBS"
    CP="$STUBS_OUT-compose:$APP_CP:$CMP_CP"
    SRC_DIRS="$WORK/gen/common"
    PLUGIN_ARGS="-Xplugin=$COMPOSE_PLUGIN"
    MODULE_NAME=app
}
# The unit-test compilation. It mirrors what AGP does for testDebugUnitTest:
#   * compiled AGAINST the debug variant's classes ($WORK/out-compose), not alongside them;
#   * with the main output as a FRIEND PATH, because a Kotlin unit-test compilation is an
#     associated compilation of main and can therefore see main's `internal` declarations. Drop
#     -Xfriend-paths and every test that touches an internal main declaration becomes a false RED;
#   * with the Compose plugin, which AGP applies to every Kotlin compilation in the module;
#   * with the testImplementation-only jars, which no other stage may see.
# tools/test-stubs is its own module for the same reason tools/compile-stubs is: `internal`
# there must not be visible to test sources.
use_test_classpath() {
    build_stubs "$STUBS_OUT-test" "$STUBS_OUT-compose:$APP_CP:$CMP_CP:$TEST_CP" "" "$TEST_STUBS"
    CP="$WORK/out-compose:$STUBS_OUT-compose:$STUBS_OUT-test:$APP_CP:$CMP_CP:$TEST_CP"
    SRC_DIRS=""
    PLUGIN_ARGS="-Xplugin=$COMPOSE_PLUGIN -Xfriend-paths=$WORK/out-compose"
    MODULE_NAME=app_test
}

# --- self-test: prove each stage is not vacuous --------------------------------------------------
# A lane that silently type-checks nothing looks exactly like a lane that passes. Every mutation
# below is applied to a COPY of a real repo file — never to app/ — and each must produce an error.

self_test_base() {
    st="$WORK/selftest-base"; mkdir -p "$st"
    prefs="$SRC_ROOT/com/sinura/personaltrainer/data/repository/PreferencesRepository.kt"
    vm="$SRC_ROOT/com/sinura/personaltrainer/ui/home/HomeViewModel.kt"
    for f in "$prefs" "$vm"; do
        [ -f "$f" ] || fail "self-test cannot find $f; update the mutation targets."
    done
    grep -q 'booleanPreferencesKey(' "$prefs" || fail "self-test: no booleanPreferencesKey( in $prefs."
    grep -q 'viewModelScope.launch' "$vm"     || fail "self-test: no viewModelScope.launch in $vm."

    # (1) Retype every boolean preference key as a Long key. Every read compared with `?: false`
    #     and every `prefs[K] = someBoolean` must now be a type error.
    sed 's/booleanPreferencesKey(/longPreferencesKey(/g' "$prefs" > "$st/PreferencesRepository.kt"
    # (2) Misspell a coroutine launch inside a ViewModel body.
    sed 's/viewModelScope\.launch/viewModelScope.launchNope/' "$vm" > "$st/HomeViewModel.kt"

    grep -v -e "$prefs" -e "$vm" "$WORK/base.txt" > "$WORK/base-mutated.txt"
    printf '%s\n%s\n' "$st/PreferencesRepository.kt" "$st/HomeViewModel.kt" >> "$WORK/base-mutated.txt"

    printf 'compile-check: self-test (base) — compiling two deliberately broken copies...\n'
    use_base_classpath
    # A nonzero compiler exit is the POINT here, so do not narrate it.
    if QUIET_COMPILE=1 compile "$WORK/out-st-base" "$WORK/base-mutated.txt" "$WORK/selftest-base.log"; then
        fail "self-test: the base stage reported a clean compile on deliberately broken source.
  It is type-checking nothing useful. Do not trust a green from it until this passes."
    fi
    grep -q "$st/PreferencesRepository.kt.*error:" "$WORK/selftest-base.log" \
        || fail "self-test: the mistyped preference keys were NOT reported. The Preferences.Key<T>
  stubs in tools/compile-stubs/datastore_preferences.kt have gone loose."
    grep -q "$st/HomeViewModel.kt.*error:.*launchNope" "$WORK/selftest-base.log" \
        || fail "self-test: the misspelled call inside a ViewModel body was NOT reported."
    printf 'compile-check: self-test (base) OK — %s errors on the mutated copies.\n' \
        "$(grep -c 'error:' "$WORK/selftest-base.log")"
}

# The compose stage needs three separate proofs, because it has three separate ways to be a lie.
self_test_compose() {
    st="$WORK/selftest-compose"; mkdir -p "$st"
    use_compose_classpath

    # (1) IS THE COMPOSE PLUGIN ACTUALLY RUNNING? Without it, @Composable is an inert annotation
    #     and calling remember() from an ordinary function compiles fine — the whole stage would
    #     silently degrade to "Kotlin with some Compose types on the classpath". The plugin's
    #     calling-context diagnostic is the cheapest thing that only the plugin can produce.
    cat > "$st/PluginActive.kt" <<'KT'
package pt.compilecheck.selftest
import androidx.compose.runtime.remember
fun notAComposable() { remember { 1 } }
KT
    printf 'compile-check: self-test (compose) — checking the Compose plugin is active...\n'
    kotlinc -nowarn -no-stdlib -no-reflect -jvm-target 17 -module-name selftest \
        $PLUGIN_ARGS -cp "$CP" -d "$WORK/out-st-plugin" \
        "$st/PluginActive.kt" > "$WORK/selftest-plugin.log" 2>&1
    grep -q '@Composable' "$WORK/selftest-plugin.log" \
        || fail "self-test: calling remember() from a plain function was accepted.
  The Compose compiler plugin is NOT running, so nothing Compose-specific is being checked.
  Expected -Xplugin=$COMPOSE_PLUGIN to be loaded."

    # (2) POSITIVE CONTROL. Every Android-only declaration tools/compose-stubs/ supplies, exercised
    #     once. This must COMPILE. Its job is to tell a reader which half is broken when the real
    #     compile goes red: if this fails too, the substitution layer has drifted from the jars and
    #     the red is the lane's fault, not the repo's.
    cat > "$st/PositiveControl.kt" <<'KT'
package pt.compilecheck.selftest

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ControlViewModel : ViewModel()

@Composable
@Preview(name = "control", widthDp = 100, heightDp = 100)
fun PositiveControl(flow: Flow<Int>, state: StateFlow<String>, bitmap: Bitmap) {
    // The Android-only composition locals, including the two that postdate Compose 1.8.2.
    LocalContext.current.packageName
    LocalView.current.isAttachedToWindow
    LocalResources.current.displayMetrics
    LocalLocale.current.platformLocale
    // containerDpSize is Compose 1.9+, supplied by the WindowInfo shadow in compose-stubs.
    val width: Dp = LocalWindowInfo.current.containerDpSize.width
    // Resource-id overloads, absent from the desktop build.
    painterResource(0)
    val family: FontFamily = FontFamily(Font(0, FontWeight.Medium))
    val image: ImageBitmap = bitmap.asImageBitmap()
    // Both collectAsStateWithLifecycle overloads: the Flow one needs an initial value, the
    // StateFlow one must not.
    val a: Int = flow.collectAsStateWithLifecycle(0).value
    val b: String = state.collectAsStateWithLifecycle().value
    val vm: ControlViewModel = viewModel()
    BackHandler(enabled = true) {}
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.path
    }
}
KT
    printf 'compile-check: self-test (compose) — positive control over the Android-only stubs...\n'
    kotlinc -nowarn -no-stdlib -no-reflect -jvm-target 17 -module-name selftest \
        $PLUGIN_ARGS -cp "$CP" -d "$WORK/out-st-control" \
        "$st/PositiveControl.kt" > "$WORK/selftest-control.log" 2>&1
    if grep -q 'error:' "$WORK/selftest-control.log"; then
        grep 'error:' "$WORK/selftest-control.log" >&2
        fail "self-test: the positive control does not compile.
  The Android-only declarations in tools/compose-stubs/ no longer line up with the Compose
  Multiplatform $COMPOSE_MP jars. Any red from the compose stage right now is this lane's fault,
  not the repo's — fix the stubs before reading the real compile."
    fi

    # (3) IS IT REALLY CHECKING THE SCREENS, ACROSS FILES? Retype one design token in
    #     ui/theme/Metrics.kt from Dp to Int and demand that the damage shows up in OTHER files.
    #     Errors only in the mutated file would prove nothing; errors in several screens prove the
    #     Compose sources are being analysed and that type flow between them is real.
    metrics="$SRC_ROOT/com/sinura/personaltrainer/ui/theme/Metrics.kt"
    [ -f "$metrics" ] || fail "self-test cannot find $metrics; update the mutation target."
    grep -q 'val space2: Dp = 8.dp' "$metrics" \
        || fail "self-test: 'val space2: Dp = 8.dp' is no longer in $metrics.
  The compose self-test mutates that line; point it at another Dp token and update this check."
    sed 's/val space2: Dp = 8.dp/val space2: Int = 8/' "$metrics" > "$st/Metrics.kt"

    grep -v -e "^$metrics$" "$WORK/compose-all.txt" > "$WORK/compose-mutated.txt"
    echo "$st/Metrics.kt" >> "$WORK/compose-mutated.txt"

    printf 'compile-check: self-test (compose) — compiling a deliberately broken design token...\n'
    if QUIET_COMPILE=1 compile "$WORK/out-st-compose" "$WORK/compose-mutated.txt" "$WORK/selftest-compose.log"; then
        fail "self-test: the compose stage reported a clean compile with a Dp token retyped to Int.
  It is type-checking nothing useful. Do not trust a green from it until this passes."
    fi
    # "the compile failed" proves nothing on its own — the tree might already be red for an
    # unrelated reason. What is counted is errors that NAME the type that was broken, in files
    # OTHER than the mutated copy. Only real cross-file type flow through the screens produces
    # those, and nothing pre-existing can fake them.
    hits=$(grep 'error:' "$WORK/selftest-compose.log" \
        | grep 'androidx\.compose\.ui\.unit\.Dp' \
        | grep -oE '^[^ ]*\.kt' | grep -v "^$st/" | sort -u | wc -l | tr -d ' ')
    [ "$hits" -ge 3 ] || fail "self-test: retyping a shared Dp design token produced Dp type errors in
  only $hits file(s) other than the mutated one. Cross-file type flow through the Compose screens
  is not being checked, so a green from this stage would mean very little."
    printf 'compile-check: self-test (compose) OK — Dp errors in %s other files (%s errors total).\n' \
        "$hits" "$(grep -c 'error:' "$WORK/selftest-compose.log")"
}

# The test stage has two ways to be a lie: it can compile no test file at all, and it can compile
# them against something other than the app. One mutation proves both at once — a real test file,
# copied and given two deliberately broken declarations, one of which names a MAIN type.
self_test_tests() {
    st="$WORK/selftest-test"; mkdir -p "$st"
    victim=$(head -1 "$WORK/test.txt")
    [ -n "$victim" ] && [ -f "$victim" ] || fail "self-test: the test source list is empty."
    maintype=com.sinura.personaltrainer.data.local.TemperDatabase
    [ -f "$SRC_ROOT/com/sinura/personaltrainer/data/local/TemperDatabase.kt" ] \
        || fail "self-test: $maintype is gone; point the test-stage self-test at another main type."
    cp "$victim" "$st/SelfTestMutant.kt" || fail "self-test: cannot copy $victim."
    cat >> "$st/SelfTestMutant.kt" <<KT

// APPENDED BY --self-test. Two errors, on purpose:
//   (1) a plain type error, which only appears if this file was compiled at all;
//   (2) a type error whose message must NAME a main declaration, which only appears if the test
//       compilation really resolves against the app's own classes.
private fun ptCompileCheckSelfTestBroken(): Int = "not an Int"
private fun ptCompileCheckSelfTestCrossBoundary(db: $maintype): Int = db
KT
    grep -v -e "^$victim$" "$WORK/test.txt" > "$WORK/test-mutated.txt"
    echo "$st/SelfTestMutant.kt" >> "$WORK/test-mutated.txt"

    printf 'compile-check: self-test (test) — compiling a deliberately broken copy of\n'
    printf '                        %s\n' "$victim"
    use_test_classpath
    if QUIET_COMPILE=1 compile "$WORK/out-st-test" "$WORK/test-mutated.txt" "$WORK/selftest-test.log"; then
        fail "self-test: the test stage reported a clean compile on deliberately broken source.
  It is type-checking nothing useful. Do not trust a green from it until this passes."
    fi
    grep -q "SelfTestMutant.kt.*error:.*'kotlin.String'" "$WORK/selftest-test.log" \
        || fail "self-test: the broken declaration appended to a real test file was NOT reported,
  so the test source list is not reaching the compiler."
    grep -q "SelfTestMutant.kt.*error:.*'$maintype'" "$WORK/selftest-test.log" \
        || fail "self-test: a test source used $maintype and the compiler did not report the type
  mismatch that names it. The test stage is not resolving against the app's own classes, so a
  test left behind by a main-source rename would still pass here."
    printf 'compile-check: self-test (test) OK — %s errors on the mutated copy, one of them naming a main type.\n' \
        "$(grep -c 'error:' "$WORK/selftest-test.log")"
}

# --- negative control: the desktop-only strip is still in force ---------------------------------
# The strip is only a guard if the declarations really are gone. This compiles every bypass that
# was demonstrated against the old grep guard — fully qualified, star-imported and aliased, so
# none of them would have matched a source-text pattern — and FAILS THE LANE if any of them still
# resolves. It is the inverse of the positive control: that one must compile, this one must not.
# It runs on every invocation, not only under --self-test, because it is what the compose stage's
# green depends on.
negative_control() {
    nc="$WORK/negctl"; mkdir -p "$nc"
    cat > "$nc/DesktopOnly.kt" <<'NEGCTLEOF'
package pt.compilecheck.negativecontrol

import androidx.compose.foundation.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.res.*
import androidx.compose.ui.res.painterResource as aliasedPainterResource
import androidx.compose.ui.text.font.Font

@Composable
fun everyKnownBypass(modifier: Modifier) {
    // Fully qualified: no import line for a ^import guard to anchor on.
    androidx.compose.foundation.VerticalScrollbar(
        adapter = androidx.compose.foundation.rememberScrollbarAdapter(rememberScrollState()),
    )
    // Star-imported desktop modifiers.
    modifier.onClick { }
    modifier.onPointerEvent(PointerEventType.Move) { }
    // Star-imported desktop resource loader.
    useResource("x") { it }
    // A String EXPRESSION, not a literal: the old guard matched only painterResource("...
    val path: String = "a" + "b"
    aliasedPainterResource(path)
    Font(path)
    // The desktop window API and the AWT interop package.
    androidx.compose.ui.window.application { }
    val window: androidx.compose.ui.awt.ComposeWindow = TODO()
}
NEGCTLEOF
    kotlinc -nowarn -no-stdlib -no-reflect -jvm-target 17 -module-name negativecontrol \
        $PLUGIN_ARGS -cp "$CP" -d "$WORK/out-negctl" \
        "$nc/DesktopOnly.kt" > "$WORK/negctl.log" 2>&1
    missing=""
    for sym in VerticalScrollbar rememberScrollbarAdapter onClick onPointerEvent useResource \
               application ComposeWindow; do
        grep -q "unresolved reference '$sym'" "$WORK/negctl.log" || missing="$missing $sym"
    done
    # painterResource(String) and Font(String) do not vanish — tools/compose-stubs declares the
    # Android Int overloads of both — so what must be true of them is that the String call no
    # longer has anything to resolve to. Checked by message, deliberately: if a future Kotlin
    # words this differently the control fails, the lane goes RED, and someone looks. That is the
    # safe direction.
    if [ "$(grep -c "actual type is 'kotlin.String', but 'kotlin.Int' was expected" "$WORK/negctl.log")" -lt 2 ]; then
        missing="$missing painterResource/Font(String)"
    fi
    if [ -n "$missing" ]; then
        grep 'error:' "$WORK/negctl.log" >&2
        fail "negative control: desktop-only Compose APIs still resolve —$missing
  Those exist in JetBrains Compose Multiplatform and in NO version of Android Compose, so the
  compose stage would report a green the merge gate would not. The strip that is supposed to
  remove them from the jars (see 'the desktop-only strip' in this file) is not doing its job."
    fi
    printf 'compile-check: negative control OK — %s desktop-only Compose APIs are unreachable.\n' \
        "$(grep -c 'error:' "$WORK/negctl.log")"
}

[ "$SELF_TEST" -eq 1 ] && [ "$RUN_BASE" -eq 1 ] && self_test_base
[ "$SELF_TEST" -eq 1 ] && [ "$RUN_COMPOSE" -eq 1 ] && self_test_compose

# --- the real compiles ---------------------------------------------------------------------------
STATUS=0
# Set to 1 only when the debug variant's classes are actually on disk and complete. The test
# stage compiles against them, so without this a FAILED compose stage still left an
# out-compose directory behind — kotlinc creates it before it gives up — and the test stage
# ran anyway, burying one real error under twenty-one thousand cascade errors and then
# reporting the wrong cause. A prerequisite that did not build is a skip, not an input.
COMPOSE_BUILT=0

if [ "$RUN_BASE" -eq 1 ]; then
    printf 'compile-check: base    — %s of %s files (no androidx.compose on the classpath).\n' \
        "$BASE" "$TOTAL"
    use_base_classpath
    if compile "$WORK/out" "$WORK/base.txt" "$WORK/base.log"; then
        printf 'compile-check: base    OK (%s files)\n' "$BASE"
    else
        grep 'error:' "$WORK/base.log" >&2
        printf 'compile-check: base    FAILED — %s compile errors (%s files in scope)\n' \
            "$(grep -c 'error:' "$WORK/base.log")" "$BASE" >&2
        STATUS=1
    fi
fi

if [ "$RUN_COMPOSE" -eq 1 ] || [ "$RUN_TESTS" -eq 1 ]; then
    use_compose_classpath
    negative_control
fi

if [ "$RUN_COMPOSE" -eq 1 ]; then
    printf 'compile-check: compose — %s files (%s main + %s debug; %s of main are Compose) against Compose Multiplatform %s.\n' \
        "$COMPOSE_TOTAL" "$TOTAL" "$DEBUG_FILES" "$COMPOSE" "$COMPOSE_MP"
    use_compose_classpath
    if compile "$WORK/out-compose" "$WORK/compose-all.txt" "$WORK/compose.log"; then
        printf 'compile-check: compose OK (%s files)\n' "$COMPOSE_TOTAL"
        COMPOSE_BUILT=1
    else
        grep 'error:' "$WORK/compose.log" >&2
        printf 'compile-check: compose FAILED — %s compile errors (%s files in scope)\n' \
            "$(grep -c 'error:' "$WORK/compose.log")" "$COMPOSE_TOTAL" >&2
        echo "  Before reading these as repo defects, check whether any names a Compose API: this" >&2
        echo "  stage substitutes Compose Multiplatform $COMPOSE_MP for the app's Compose 1.11.4." >&2
        echo "  tools/compile-check.sh --explain lists exactly what that swaps." >&2
        STATUS=1
    fi
fi

if [ "$RUN_TESTS" -eq 1 ]; then
    # `./gradlew testDebugUnitTest` is the FIRST half of the merge gate and it compiles this
    # source set against the debug variant's classes. Without this stage a method renamed in main
    # with its main call sites updated but not its test is a GREEN here and a RED in CI.
    #
    # The debug variant's classes are the prerequisite, exactly as they are under Gradle. If the
    # compose stage already built them, reuse that output; otherwise build it here and say so.
    if [ "$COMPOSE_BUILT" -eq 0 ] && [ "$RUN_COMPOSE" -eq 1 ]; then
        # The compose stage already ran and failed. Its errors are printed above and they are
        # the finding; running the tests against a half-built variant would print thousands
        # more that say nothing except "the prerequisite is missing".
        printf 'compile-check: test    SKIPPED — the debug variant does not compile. Fix the\n' >&2
        printf '                        compose errors above and run again.\n' >&2
        exit 1
    fi
    if [ "$COMPOSE_BUILT" -eq 0 ]; then
        printf 'compile-check: test    — building the debug variant first (prerequisite)...\n'
        use_compose_classpath
        if ! compile "$WORK/out-compose" "$WORK/compose-all.txt" "$WORK/compose.log"; then
            grep 'error:' "$WORK/compose.log" >&2
            printf 'compile-check: test    SKIPPED — the debug variant does not compile (%s errors).\n' \
                "$(grep -c 'error:' "$WORK/compose.log")" >&2
            exit 1
        fi
        COMPOSE_BUILT=1
    fi

    # ------------------------------------------------------------------------------------------
    # EXCLUSIONS. Six files are compiled separately rather than with the rest, for ONE reason,
    # named here in full:
    #
    #   Robolectric's android-all jar is built from AOSP framework source, where
    #   Context.getSystemService(Class<T>) carries @android.annotation.Nullable. Kotlin therefore
    #   types it T?. The compileSdk 36 android.jar the merge gate compiles against does not make
    #   that a hard error — these six tests are long-standing and `./gradlew testDebugUnitTest`
    #   accepts them, e.g. `val alarmManager: AlarmManager =
    #   context.getSystemService(AlarmManager::class.java)`. So this is a FALSE RED created by the
    #   framework substitution, not a repo defect.
    #
    # They are NOT dropped. They are compiled on their own immediately afterwards, and:
    #   * if they now compile CLEAN, the exclusion is stale and the lane fails, so it cannot
    #     outlive its reason;
    #   * every error they produce is matched against the exact message set below, and anything
    #     unrecognised fails the lane. A real defect introduced into one of these six files is
    #     therefore still caught.
    TEST_EXCLUDED="app/src/test/java/com/sinura/personaltrainer/timer/RestSoundTest.kt
app/src/test/java/com/sinura/personaltrainer/timer/RestTimerAlarmSchedulerTest.kt
app/src/test/java/com/sinura/personaltrainer/timer/RestTimerCompletionTest.kt
app/src/test/java/com/sinura/personaltrainer/timer/RestTimerControllerTest.kt
app/src/test/java/com/sinura/personaltrainer/timer/RestTimerNotificationsTest.kt
app/src/test/java/com/sinura/personaltrainer/timer/RestTimerServiceTest.kt"
    # The only compiler messages the exclusion is allowed to absorb. Anything else in those files
    # is a real error. Kept as fixed strings, not a loose pattern: a wording change in a future
    # Kotlin makes this stop matching, which turns the lane RED, which is the safe direction.
    TEST_EXCLUDED_OK="only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'android.app.NotificationManager?'.
type mismatch: inferred type is 'T?', but 'android.app.AlarmManager' was expected.
initializer type mismatch: expected 'android.app.AlarmManager', actual 'android.app.AlarmManager?'."

    printf '%s\n' "$TEST_EXCLUDED" | sed '/^$/d' | sort > "$WORK/test-excluded.txt"
    while read -r f; do
        [ -f "$f" ] || fail "the test-stage exclusion list names $f, which no longer exists.
  Remove it from TEST_EXCLUDED in tools/compile-check.sh."
        grep -qxF "$f" "$WORK/test-all.txt" \
            || fail "the test-stage exclusion list names $f, which is not in the test source set."
    done < "$WORK/test-excluded.txt"
    comm -23 "$WORK/test-all.txt" "$WORK/test-excluded.txt" > "$WORK/test.txt"
    TEST_FILES=$(wc -l < "$WORK/test.txt" | tr -d ' ')
    TEST_SKIPPED=$(wc -l < "$WORK/test-excluded.txt" | tr -d ' ')

    [ "$SELF_TEST" -eq 1 ] && self_test_tests

    printf 'compile-check: test    — %s of %s unit-test files (app/src/test + app/src/sharedTest).\n' \
        "$TEST_FILES" "$((TEST_FILES + TEST_SKIPPED))"
    use_test_classpath
    if compile "$WORK/out-test" "$WORK/test.txt" "$WORK/test.log"; then
        printf 'compile-check: test    OK (%s files)\n' "$TEST_FILES"
    else
        grep 'error:' "$WORK/test.log" >&2
        printf 'compile-check: test    FAILED — %s compile errors (%s files in scope)\n' \
            "$(grep -c 'error:' "$WORK/test.log")" "$TEST_FILES" >&2
        STATUS=1
    fi

    # The excluded six, compiled on their own so the exclusion cannot hide anything but its
    # stated reason. The main test output joins the classpath: these files reference helpers
    # (FakeAppDependencies and the testutil package) that live in the compiled set.
    if [ "$TEST_SKIPPED" -gt 0 ]; then
        CP="$WORK/out-test:$CP"
        QUIET_COMPILE=1 compile "$WORK/out-test-excluded" "$WORK/test-excluded.txt" \
            "$WORK/test-excluded.log"
        excluded_clean=$?
        if [ "$excluded_clean" -eq 0 ]; then
            fail "the test-stage exclusion list is STALE: all $TEST_SKIPPED excluded files now
  compile. Delete TEST_EXCLUDED from tools/compile-check.sh and let them into the main list."
        fi
        # Strip the file:line:col prefix, then subtract the messages the exclusion is for.
        sed -n 's/^.*\.kt:[0-9]*:[0-9]*: error: //p' "$WORK/test-excluded.log" | sort -u \
            > "$WORK/test-excluded-messages.txt"
        printf '%s\n' "$TEST_EXCLUDED_OK" | sed '/^$/d' | sort -u > "$WORK/test-excluded-ok.txt"
        comm -23 "$WORK/test-excluded-messages.txt" "$WORK/test-excluded-ok.txt" \
            > "$WORK/test-excluded-new.txt"
        printf 'compile-check: test    — %s file(s) compiled separately: Robolectric android-all\n' \
            "$TEST_SKIPPED"
        printf '                        types Context.getSystemService(Class<T>) as T? (AOSP marks it\n'
        printf '                        @Nullable); compileSdk 36 does not. These are the framework\n'
        printf '                        substitution reporting a FALSE RED, not repo defects:\n'
        sed 's|^|                          |' "$WORK/test-excluded.txt"
        if [ -s "$WORK/test-excluded-new.txt" ]; then
            echo "compile-check: those files also produced errors the exclusion does NOT cover:" >&2
            sed 's/^/    /' "$WORK/test-excluded-new.txt" >&2
            grep 'error:' "$WORK/test-excluded.log" >&2
            printf 'compile-check: test    FAILED — an excluded file has a real error.\n' >&2
            STATUS=1
        fi
    fi
fi

exit $STATUS

# --- SOUNDNESS CAVEATS — what a green from this lane does NOT prove ------------------------------
#
# COMMON TO ALL THREE STAGES
#
#  1. No annotation processing runs. Room's KSP processor is what validates @Query SQL against
#     the schema, checks DAO return types against projections, verifies @Entity/@ForeignKey
#     against the exported schemas in app/schemas/, and generates the *_Impl classes. None of
#     that happens here: this lane only checks that the annotations are well-typed Kotlin.
#  2. tools/compile-stubs/, tools/compose-stubs/ and tools/test-stubs/ are declaration-only
#     stand-ins. They are written to be no more permissive than the real APIs, and several are
#     deliberately narrower — but they are not the real artifacts, and a member the real API has
#     that they omit shows up as a RED that is the stub's fault, not the repo's.
#  3. androidx.lifecycle in particular is hand-written: ViewModel, AndroidViewModel,
#     viewModelScope, SavedStateHandle, and (for the compose stage) Lifecycle/LifecycleOwner.
#     No jar with those classes exists on Maven Central.
#  4. android.* comes from Robolectric's android-all jar (a real AOSP build, API 35) rather than
#     from the compileSdk 36 android.jar the merge gate uses. An API added in 36 would be
#     missing here, @hide/@SystemApi members that android.jar strips are visible here, and the
#     nullability annotations differ: AOSP marks Context.getSystemService(Class<T>) @Nullable,
#     which is why six timer tests are compiled in a separate pass (see TEST_EXCLUDED).
#  5. R and BuildConfig are generated from app/src/{main,debug}/res and app/build.gradle.kts, not
#     by aapt2. Resources merged in from AARs are not modelled, and neither is the release
#     build type. R ids are emitted as non-`const val`, matching AGP 8.9.2's non-final res ids.
#  6. Nothing is executed. This is a type-check, not a test: passing says the code compiles,
#     never that it behaves. tools/run-domain-tests.sh runs the domain tests; only
#     `./gradlew testDebugUnitTest` runs the Robolectric ones.
#  7. Lint, ProGuard/R8, manifest merging and packaging are not modelled at all.
#  8. The compile classpath is a hand-maintained list of the app's `implementation` and
#     `testImplementation` dependencies. It is deliberately split four ways (compiler runtime,
#     app, Compose, test-only) and it is a LIST rather than a directory sweep — but it is
#     maintained by hand, so a dependency added to app/build.gradle.kts and used from source
#     will show up as a RED until it is added to the resolve() block above (or stubbed, if
#     Maven Central does not have it).
#  9. Only the DEBUG variant is modelled. There is no release-specific source set in this project
#     today; if one appears it is invisible to this lane.
# 10. app/src/androidTest/java IS NOT COMPILED BY ANY STAGE. 23 files, and the merge gate does
#     not build them either (`assembleDebug` does not; only connectedDebugAndroidTest does, and
#     that needs a device). Compiling them would need declaration-only stubs for the whole
#     androidx.compose.ui.test semantics-matcher and finder API plus androidx.test.ext.junit —
#     a stub surface large enough that its false REDs would cost more than the hole. The defect
#     class that lived there (a stale caller after a signature change) is guarded by
#     tools/check-lambda-arity.py, which does read that source set. That is a checker, not a
#     compiler, and the difference is written down here rather than glossed over.
#
# SPECIFIC TO THE COMPOSE STAGE — read these before trusting its green
#
# 11. THE VERSION IS NOT THE ONE THE APP SHIPS. JetBrains Compose Multiplatform 1.8.2 stands in
#     for Compose UI/foundation/runtime 1.11.4 and Material3 1.4.0. Where the two agree, the
#     check is real. Where they differ:
#       - an API REMOVED between 1.8.2 and 1.11.4 still resolves here → GREEN where the merge
#         gate is RED. The desktop-only strip removes the class of these that is enumerable
#         (everything Compose Multiplatform has that Android never had); "everything removed in
#         three minor versions of Android Compose" is not an enumerable set from here.
#       - an API ADDED after 1.8.2 does not resolve here → RED that the merge gate would not
#         report. Those are visible, and the four the app actually uses (LocalResources,
#         LocalLocale, WindowInfo.containerDpSize, and the Android resource overloads) are
#         supplied by tools/compose-stubs/ at the signature Android declares.
#     Anything this stage reports that names a Compose symbol deserves that check before it is
#     called a repo defect. Anything it reports that names only repo declarations — an arity, a
#     parameter name, a UI-state type — is as real as a Gradle error.
# 12. THE DESKTOP-ONLY STRIP IS AUDITED, NOT DERIVED. Google's Maven is unreachable, so there is
#     no Android Compose to diff against. Six facades are KEPT because Android Compose has the
#     same API (SkikoMenu, AlertDialog, WindowInsetsPadding, SkiaBackedPath, Dialog, Popup) and
#     a kept facade could still carry a desktop-only overload of the API it was kept for. That
#     is the strip's residual risk, and it is the whole of it: everything else is deleted, and a
#     deletion can only cause a false RED.
# 13. androidx.compose.ui.platform.WindowInfo is SHADOWED by tools/compose-stubs/, because
#     containerDpSize has to resolve as a member. Since the stubs became their own module that
#     shadowing is CLASSPATH ORDER — the stubs output precedes the Compose jars — rather than
#     source-beats-classpath. It still fails safe (if the shadow stopped applying,
#     containerDpSize would become unresolved, i.e. a RED), and --self-test's positive control
#     asserts it still holds.
# 14. androidx.activity, androidx.navigation and the two lifecycle-compose artifacts are stubbed
#     rather than substituted — Maven Central has no usable build of any of them. Their real
#     behaviour (what a NavHost does with a route, what a launcher contract returns at runtime)
#     is entirely outside this lane; only the shapes the app calls are checked.
# 15. @Preview functions are compiled, but nothing renders them, and ui-tooling's own checks on
#     preview parameters do not run.
# 16. The Compose compiler plugin IS the merge gate's — same org.jetbrains.kotlin.plugin.compose,
#     same Kotlin 2.0.21 — so its diagnostics (calling context, @Composable inference) are exact.
#     Its stability inference is not, because that depends on the classes on the classpath, but
#     stability affects recomposition performance, not whether the code compiles.
# 17. Opt-in markers drift between the two builds. ModalBottomSheet, for instance, is
#     @ExperimentalMaterial3Api in Compose Multiplatform 1.8.2 and may not be in Material3 1.4.0.
#     Where the app has the @OptIn both builds accept it. Where a marker exists in 1.11.4 but not
#     in 1.8.2, a MISSING @OptIn is an error under Gradle and invisible here; the reverse is only
#     an "unnecessary opt-in" warning, and -nowarn hides it. Neither direction can change whether
#     the code is otherwise well-typed.
#
# SPECIFIC TO THE TEST STAGE
#
# 18. It compiles the tests; it does not RUN them. Every assertion, every Robolectric shadow,
#     every MigrationTestHelper schema validation is outside this lane. `./gradlew
#     testDebugUnitTest` is still the only thing that executes them, and
#     tools/run-domain-tests.sh is still the only thing here that executes anything.
# 19. Six files are compiled in a separate pass rather than with the rest (TEST_EXCLUDED, above),
#     because android-all types Context.getSystemService(Class<T>) as nullable and compileSdk 36
#     does not. They are not dropped — the lane fails if they start compiling clean (a stale
#     exclusion) and fails if they produce any error other than the three that exclusion is for.
# 20. MigrationTestHelper, ApplicationProvider and InstrumentationRegistry are stubs with no
#     behaviour. A migration test that compiles here says nothing about whether the migration
#     produces the schema app/schemas/*.json records.
