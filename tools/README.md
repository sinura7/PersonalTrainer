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
python3 tools/check-named-args.py app/src/main/java app/src/test/java \
    app/src/androidTest/java app/src/debug/java app/src/sharedTest/java
```

Exits quietly with `0 mismatch(es)` when clean. Nothing to install.

Declarations are indexed by simple name, and this index is the *only* source of truth for a
name — the Android and Compose APIs are not on the classpath here, so a name the project
declares is the only definition it has. That makes visibility load-bearing rather than a nicety:
a `private data class Row` in one domain file was, for one preflight run, the project's entire
definition of `Row`, and 66 correct `Row(modifier = …)` call sites across the UI were reported
as broken. Private declarations are therefore recorded against their own file and considered
only for calls in it; everything else is visible project-wide.

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
counted as references. Four names are suppressed outright in `ALWAYS_IN_SCOPE`, each because
the project genuinely imports it somewhere and genuinely uses it un-imported elsewhere.

A third pass covers what the first two structurally cannot. Both work from dictionaries of
names seen elsewhere, so a name that resolves to *nothing at all* is absent from both and
passes silently. That is what happens when a composable is lifted into a new file and its
`private val` dimensions stay behind: the new file references `TODAY_MARKER_WIDTH`, no file
declares it visibly, and an unknown name looks the same as a fine one. For
SCREAMING_SNAKE_CASE names — always constants in this project, never a receiver member or a
bound parameter — "declared in no visible scope" therefore means "will not compile". Two
subtractions keep it honest: an enum entry is in scope un-qualified inside its own enum's
body (`LAST_30_DAYS` is an entry, not a constant), and a class extending a type from outside
the project inherits constants nothing here can enumerate (`START_STICKY` comes from
`android.app.Service`), so such a file opts out. Without those, the pass reported fifteen
non-bugs alongside its three real ones.

## `check-unused-imports.py`

Reports imports whose name appears nowhere else in the file.

```bash
python3 tools/check-unused-imports.py app/src/main/java
```

Deliberately conservative: under-reporting is the safe direction for output you act on by
deleting lines, so a name that also exists as a member of an unrelated type counts as used.

## `check-state-members.py`

Checks every `state.foo` read against the properties its screen's `UiState` actually
declares.

```bash
python3 tools/check-state-members.py app/src/main/java
```

Member access was the one error class nothing else here could see: `check-missing-imports.py`
skips dotted names deliberately, because they are resolved by a receiver rather than an
import. So a screen could read a field its state class never had, pass all eight other checks,
and fail in Android Studio — which is exactly what Phase 6b did with `state.loggedEpochDays`
on a `HomeUiState` that did not have it.

Resolving the receiver without a type checker works only because the project is rigidly
consistent about two things: a screen's collected state is always the local `state`, and a
view model always declares `val uiState: StateFlow<SomethingUiState>`. The screen names the
view model, the view model names the state type. A file mentioning no view model or several is
skipped rather than guessed at, and the four members every data class gets for free (`copy`,
`equals`, `hashCode`, `toString`) are never reported.

## `check-lambda-arity.py`

The third question about a call: not *does this name exist* and not *did you pass everything*,
but *does the lambda you passed take the number of parameters the declaration wants*.

```bash
python3 tools/check-lambda-arity.py app/src/main/java app/src/test/java \
    app/src/androidTest/java app/src/debug/java app/src/sharedTest/java
```

Pass EVERY source root in one invocation. A test root alone cannot see the declaration it
calls, so running it by itself reports a false clean.

On 7 September 2026 a branch was pushed that did not compile. `SessionLiftStrip.onStageTargets`
grew from five parameters to six to carry the rule a rejected box broke; one of its three call
sites was updated. Twenty static checkers, a 1202-test JVM lane and three independent reviewers
passed it. Neither `check-named-args` nor `check-required-args` could see it — the argument was
named, the name existed, and every required parameter was supplied. What was wrong was inside
the value. And a review that reads the diff cannot find a caller that broke *because it did not
change*: both stale call sites are in files the diff never touched.

`tools/compile-check.sh` catches this properly, with a real compiler, but it fetches about
186 MB on first run and stays out of the preflight for that reason. This is the offline half.
It knows nothing about types; counting parameters needs no type system.

Conservative in the same way as its two siblings, and for the same reason — a false RED gets a
checker switched off. Only **named** arguments are judged, because a trailing lambda would need
overload resolution to know which parameter it fills. A site is skipped, not guessed at, unless
both sides are unambiguous: every visible declaration of that call name must declare the
parameter as a plain function type (`(A, B) -> R`, optionally `suspend`, nullable or
parenthesised), and the argument must be a literal lambda whose header parses as a parameter
list. A receiver type (`T.(A) -> R`) is never judged: its lambda takes one fewer parameter than
the parentheses show. A lambda with no `->` is accepted against arity 0 or 1 — Kotlin gives it
the implicit `it` — and reported against 2 or more, which is what Kotlin does too.

`tools/test_lambda_arity.py` runs the checker over fifteen fixtures, including the exact defect
above, the fix for it, and every shape that must stay quiet: a `when` inside the body, a nested
lambda, a destructured parameter, a function reference, a trailing lambda, a commented-out call.

### Three source sets nothing was reading

The same day, `check-required-args.py`, `check-missing-imports.py` and `check-named-args.py`
were widened to read `app/src/androidTest/java`, `app/src/debug/java` and
`app/src/sharedTest/java` as well as main and test. Until then nothing looked at those three at
all — which is how the second stale caller of the arity defect sat unseen in an instrumented
test while `assembleDebug` was red.

`check-named-args.py` had a second, quieter hole: it took ONE root, and a root scanned alone is
a false clean. Its index holds only that root's declarations, so `if name not in decls: continue`
skipped every call into another source set — which for `app/src/test` was most of them, and the
preflight had been running it on test alone since it was written. It now takes every root in one
invocation and reports across all 662 files.

`check-missing-imports.py` treats every root after the first as a **satellite**: it sees main
and itself, and not the other satellites, because androidTest cannot see a unit test's private
helper any more than main can. Both widenings were negative-controlled rather than assumed —
dropping a required argument from an androidTest call, and using an unimported project symbol
in an androidTest file, each produce exactly one finding, and neither did before. Widening
`check-required-args` also revealed one mixed-argument call in an instrumented test, now fully
named, so its skip ratchet holds at 178 across 662 files instead of 624.

## `check-required-args.py`

The inverse of `check-named-args.py`: did the call supply everything the declaration requires?

```bash
python3 tools/check-required-args.py
```

`check-named-args` asks whether every name you passed exists. That is half the contract, and
the other half was checked by nothing. Phase 6b removed a composable's whole-card tap by
deleting the ARGUMENT and leaving the PARAMETER: `ThisWeekCard` went on declaring six required
parameters while its only call site passed five. All ten other checks reported clean — every
name that *was* passed existed — and the branch did not compile for three phases.

Deliberately conservative. Matching positional arguments to parameters means resolving
overloads and trailing-lambda syntax, and being wrong there produces noise on working code, so
this reports only calls where **every argument is named**, plus a trailing lambda, which by
Kotlin's rule supplies the last parameter. That costs almost nothing in this codebase, whose
Compose call sites are named-argument style throughout — which is exactly where a forgotten
parameter hides, because five named arguments over eight lines make the missing sixth invisible
to the reader too.

Three parsing rules it needs to get right, each of which produced a wave of false positives
before it was fixed. Emptiness is judged against the RAW source, because `strip_comments_and_strings`
blanks a string literal's quotes as well as its content, so `Kicker("Rest")` arrives as
`Kicker(      )` — a positional argument that looks like an empty argument list. Angle brackets
nest in a parameter list (generics) but not in an argument list, where `>` is almost always a
comparison. And a trailing comma leaves a final all-whitespace span that must not be read as a
positional argument — this codebase puts one on every multi-line call, so getting it wrong made
the tool skip every call it existed to check.

## `check-annotation-targets.py`

Finds annotations that are no longer attached to a declaration.

```bash
python3 tools/check-annotation-targets.py
```

Inserting a composable above an existing one is a two-line edit that a scripted patch gets
subtly wrong: the new function lands between the old one's KDoc-plus-`@Composable` and the `fun`
they belonged to. The stranded `@Composable` then binds to the *new* function, which has its
own, and Kotlin rejects the duplicate. It reads perfectly and every other check here passed on
the file it happened to.

`syntax-check.sh` cannot cover it: annotation binding is resolution, not parsing, and the
diagnostic is filtered out with the rest of the semantic noise an SDK-less classpath produces.

`@Suppress` and `@OptIn` are exempt from the "must precede a declaration" rule, because Kotlin
genuinely allows them on an expression. The duplicate-across-a-comment rule still applies to
them, since two of the same annotation separated by a doc comment is wrong wherever it appears.

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

## `check-coverage.py`

Compares JaCoCo package instruction coverage to [coverage-floors.txt](coverage-floors.txt).

```bash
./gradlew jacocoTestReport
python3 tools/check-coverage.py
```

Floors are a ratchet. Raise them when a packet adds tests. Do not lower a floor
to hide a regression. Generated Room/Compose/R/BuildConfig classes are excluded
by the `jacocoTestReport` task.

## `verify.sh`

One local verification command for a foundation-program packet:

```bash
tools/verify.sh
```

Runs preflight, unit tests, lint against `app/lint-baseline.xml` (new issues
fail), coverage ratchet, and `connectedDebugAndroidTest` if an emulator is
already up. The connected lane always targets
`com.sinura.personaltrainer.debug`.

## `check-doc-authority.py`

Guards the documentation authority signed in `docs/architecture/ADR-001-documentation-authority.md`.

```bash
python3 tools/check-doc-authority.py
```

Current-voice files (README, SETUP, DEVELOPMENT, RECOVERY, UX page-pass binding
rules, the foundation program, ADRs, owner-loop) must not assert obsolete law:
Drive as sync, `main` as the sitting branch, uncommitted `2.json`, 7/14-day heat
windows, Job 6 as the current program, or a permanent Room v2 freeze. Relative
markdown links in active documents must resolve. Every issued FND ID must have a
disposition in ADR-013. Archives are not phrase-checked.

## `check-still-pack.py`

E2's APK-size proof for catalog stills. `ex_*` WebPs must be 256×256 and
under 700 KB together. Family pose fallbacks match. The Body unlit pair
is 1024×1024 so the panel is not upscaling 768. Heat stays 768.

```bash
python3 tools/check-still-pack.py
```

## `test_policy_move.py`

J4 remainder. Fixture proofs for the sixteen source-reading policy tests that
moved out of the JVM suite. Preflight runs it. A helper that swallows
`allowBackup=true`, a Kotlin 2.2 stdlib, or play-services-auth 22 fails here.

```bash
python3 tools/test_policy_move.py
```

## preflight.sh

`tools/preflight.sh` is the mechanical half of every foundation-program packet's definition of done:
run it before every push. It chains the static checks above and then the domain
suite, exiting non-zero on the first failure.

Three of the checks (`check-named-args`, `check-when-exhaustive`, `check-unused-imports`)
and `syntax-check.sh` always exit 0, so preflight judges them on their summary line rather
than their status; the others exit by finding-count and are judged on that.

The domain tests need a directory of seven jars (see `run-domain-tests.sh`'s header). If
`$PT_JARS` / `build/test-jars` is absent, preflight assembles it by symlinking jars found in
the Gradle module cache, the wrapper distributions, or a Gradle distribution's `lib/`. If no
jars can be found anywhere it falls back to `./gradlew testDebugUnitTest`, and if Gradle
cannot run either it fails loudly — it never silently skips the tests.

It is a pre-flight, not a substitute for `./gradlew testDebugUnitTest assembleDebug`: the
Robolectric and instrumented tests only run under Gradle (see `docs/DEVELOPMENT.md`).
