# tools

Small static checks that answer questions the Kotlin compiler answers better — but only
when you have an Android SDK to hand. They exist because most of this codebase cannot be
compiled outside Android Studio or CI, and a broken build discovered on the phone at the gym
is worse than one discovered in five seconds at a terminal.

They are a pre-flight check, **not** a substitute for `./gradlew assembleDebug`.

## `check-named-args.py`

Cross-checks every named argument at every call site against the declaration it targets, for
the whole source tree. This catches the one error class that survives a parse-only check and
still breaks the build: a call passing a parameter name the function does not have — the
usual result of renaming a parameter and missing a caller.

```bash
python3 tools/check-named-args.py app/src/main/java
python3 tools/check-named-args.py app/src/test/java
```

Exits quietly with `0 mismatch(es)` when clean. Nothing to install.

## `check-when-exhaustive.py`

Finds `when` blocks over the project's own enum and sealed types that miss a case. That is a
compile *error* in Kotlin, not a warning, and it is the error a refactor leaves behind: add a
variant and every `when` without an `else` breaks at once.

```bash
python3 tools/check-when-exhaustive.py app/src/main/java
```

The subject's type is inferred from the branch labels rather than by type analysis, so it
needs no symbol table. Blocks whose type cannot be pinned down are skipped and counted, never
guessed at — the summary line reports how many, so a quiet run is not mistaken for a thorough
one.

## `check-missing-imports.py`

The mirror of `check-internal-imports.py`, and the reason both exist. That one validates the
imports a file *has*; a symbol used but never imported leaves no import line to validate, so
it is structurally invisible there and still fails the build in Android Studio.

```bash
python3 tools/check-missing-imports.py            # both source roots
```

Two passes. **Project** names come from this codebase's own top-level declarations —
`Surface1` shipped un-imported in `ExerciseDetailScreen` and this is what catches it.
**External** names come from the project's own import lines used as a dictionary: there is no
Android SDK here to enumerate the framework, but if twenty files import `LaunchedEffect` from
`androidx.compose.runtime`, a twenty-first using the bare name is the same bug — which is
exactly what shipped in `HomeScreen` and `ScheduleScreen`. That makes the second pass a
convention check, not a compiler: a symbol the project has never imported anywhere is
invisible to it.

Quiet by construction, because a noisy check does not get run. Private top-level declarations
are excluded (they are file-scoped, so no other file could have imported them); the test
source set is indexed separately from main (main cannot see test, and a test helper named
`session` would otherwise indict every `session` lambda parameter in the app); and names being
*introduced* — declarations, value parameters, lambda parameters, named arguments — are never
counted as references. Three names are suppressed outright in `ALWAYS_IN_SCOPE`, each because
the project genuinely imports it somewhere and genuinely uses it un-imported elsewhere.

## `check-unused-imports.py`

Reports imports whose name appears nowhere else in the file.

```bash
python3 tools/check-unused-imports.py app/src/main/java
```

Deliberately conservative: under-reporting is the safe direction for output you act on by
deleting lines, so a name that also exists as a member of an unrelated type counts as used.

## `syntax-check.sh`

Runs the Kotlin front end over a source root and reports only parse-level diagnostics.
Semantic errors are expected noise without the Android SDK on the classpath, so they are
filtered out; unbalanced braces, stray tokens and malformed declarations are not.

```bash
tools/syntax-check.sh app/src/main/java
```

Needs a `kotlin-compiler-embeddable` jar in the Gradle cache, which any prior build leaves
behind. If it cannot find one, skip it and let Android Studio do the work.

## `kotlin_source.py`

Shared by the Python checks. Blanks comments and string literal *text* while preserving
offsets — and, importantly, while preserving `${...}` template interpolations, which hold real
code. Both mistakes it now avoids were made first: treating a preceding dot as proof an import
was unused (extensions are always called that way) reported 228 live imports as dead, and
blanking whole string literals hid the only use of several others.

## preflight.sh

`tools/preflight.sh` is the mechanical half of every game-plan phase's definition of done:
run it before every push. It chains the eight static checks above and then the domain
suite, exiting non-zero on the first failure.

Three of the checks (`check-named-args`, `check-when-exhaustive`, `check-unused-imports`)
and `syntax-check.sh` always exit 0, so preflight judges them on their summary line rather
than their status; the other four exit by finding-count and are judged on that.

The domain tests need a directory of seven jars (see `run-domain-tests.sh`'s header). If
`$PT_JARS` / `build/test-jars` is absent, preflight assembles it by symlinking jars found in
the Gradle module cache, the wrapper distributions, or a Gradle distribution's `lib/`. If no
jars can be found anywhere it falls back to `./gradlew testDebugUnitTest`, and if Gradle
cannot run either it fails loudly — it never silently skips the tests.

It is a pre-flight, not a substitute for `./gradlew testDebugUnitTest assembleDebug`: the
Robolectric and instrumented tests only run under Gradle (see `docs/DEVELOPMENT.md`).
