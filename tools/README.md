# tools

Four small static checks that answer questions the Kotlin compiler answers better — but only
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
