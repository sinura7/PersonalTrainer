# tools

Two small static checks that answer questions the Kotlin compiler answers better — but only
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

## `syntax-check.sh`

Runs the Kotlin front end over a source root and reports only parse-level diagnostics.
Semantic errors are expected noise without the Android SDK on the classpath, so they are
filtered out; unbalanced braces, stray tokens and malformed declarations are not.

```bash
tools/syntax-check.sh app/src/main/java
```

Needs a `kotlin-compiler-embeddable` jar in the Gradle cache, which any prior build leaves
behind. If it cannot find one, skip it and let Android Studio do the work.
