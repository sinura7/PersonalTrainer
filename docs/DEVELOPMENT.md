# Development

How to work on this app day to day. Android Studio is the primary tool; everything here
assumes you are building and installing from it.

## First run

1. Clone, then **File → Open** the folder containing `settings.gradle.kts`.
2. Trust the project, let Gradle sync finish.
3. Plug in the phone (USB debugging on) or start an API 26+ emulator.
4. Press **Run ▶**. That is the whole deployment path for day-to-day work — a debug build
   signed with Android Studio's debug key, installed straight to the device.

Debug and release are **separate installs with different signing keys**. A debug build will
not overwrite your real, release-signed app, and vice versa — which is exactly what you
want, because it means experimenting cannot touch your real training history. They keep
separate databases only if the applicationId differs; it does not here, so installing one
over the other requires an uninstall. Use the emulator for anything risky.

## Project layout

```
app/src/main/java/com/sinura/personaltrainer/
  data/local        Room entities, DAOs, TrainerDatabase
  data/repository   repositories — the only things that touch DAOs
  data/backup       backup document, JSON codec, validator, Drive client
  domain            pure Kotlin: models, units, heat, recommendations, planner, progression
  timer             rest timer: store, controller, foreground service, alarm, notifications
  workout           in-progress workout draft (memory + saved state)
  ui/<screen>       one package per screen: Screen.kt + ViewModel.kt
app/src/test/       JVM unit tests
app/schemas/        Room schema JSON, one per DB version — committed on purpose
docs/               audit, roadmap, recovery runbook, design spec
```

Two rules keep this navigable:

- **`domain/` stays pure Kotlin.** No `android.*` imports. That is what makes it testable
  without a device, and most of the test suite lives there.
- **ViewModels talk to repositories, never to DAOs.** Dependencies come from `AppContainer`
  (manual DI, no Hilt) via `PersonalTrainerApp`.

## Running tests

In Android Studio: right-click `app/src/test` → **Run 'Tests'**. From the terminal:

```bash
./gradlew testDebugUnitTest          # the whole JVM suite
./gradlew testDebugUnitTest --tests '*ProgressionBasisTest*'
```

These are plain JVM tests — no emulator, a few seconds. Run them before every commit; CI
runs them again on push.

Run everything mechanical with one command — the eight static checks plus the domain
suite, which is what every game-plan phase gates on:

```bash
tools/preflight.sh
```

The individual checks in `tools/` cover the gap when you cannot build — they are a
pre-flight, not a substitute for `./gradlew assembleDebug`:

```bash
python3 tools/check-named-args.py app/src/main/java        # named args vs. declarations
python3 tools/check-when-exhaustive.py app/src/main/java   # sealed/enum when coverage
python3 tools/check-unused-imports.py app/src/main/java    # dead imports
python3 tools/check-internal-imports.py app/src/main/java  # in-project names actually exist
python3 tools/check-missing-imports.py                     # names used but never imported
python3 tools/check-design-tokens.py app/src/main/java     # no raw colours/radii/elevation
python3 tools/check-screen-wiring.py app/src/main/java     # every callback is actually called
tools/syntax-check.sh app/src/main/java                    # parse-level diagnostics only
```

Each targets an error class that survives a parse-only check and still breaks the build or
the product: a call site passing a parameter the function no longer has, a `when` that lost
its exhaustiveness, a name that was never declared, a name that was used but never imported,
a colour that escaped the token layer, and a screen that quietly stopped calling one of its
callbacks. See [tools/README.md](../tools/README.md).

`check-missing-imports.py` is the newest and was written from a real miss: `Surface1` shipped
un-imported in `ExerciseDetailScreen`, and before it `LaunchedEffect` in `HomeScreen` and
`ScheduleScreen`. Both were caught by a human opening Android Studio, which is precisely the
loop these checks exist to shorten. It takes both source roots at once, so it needs no
argument.

On a machine with no Android SDK, `tools/run-domain-tests.sh` runs the domain suite on a
plain JVM, which is possible only because `domain/` is pure Kotlin. Point it at a directory
holding the Kotlin compiler, stdlib, coroutines, JUnit and hamcrest jars:

```bash
PT_JARS=build/test-jars tools/run-domain-tests.sh
```

## Continuous integration

`.github/workflows/ci.yml` runs the unit tests, lint and `assembleDebug` on every push to
`main` and to `claude/**` and `cursor/**` branches, and uploads the test reports, the
generated Room schemas and a debug APK.

**It has never successfully run.** Every attempt so far fails about three seconds in, with
`runner_id: 0`, zero billable milliseconds and no logs at all, which means GitHub never
assigned a runner rather than the build failing. GitHub's own annotation on the run says the
job was not started because account payments have failed or the spending limit needs raising.
The action versions the workflow pins were checked against their real tags and all exist, so
this is an account-level block on a private repository, not a broken workflow.

Actions minutes are free and unlimited on **public** repositories, and a self-hosted runner is
free on any repository; the default $0 spending limit is what stops the job, so nothing here
has ever been billed. Until one of those is chosen, nothing in CI verifies anything, and
**Android Studio is the only thing that has ever compiled this app.**

## Instrumented tests

Two lanes exist for anything Room touches:

- **JVM lane (primary)**: Robolectric tests under `app/src/test` (e.g.
  `SchemaV1BaselineTest`) run inside `./gradlew testDebugUnitTest` — no device.
  Robolectric bundles its own SQLite, which is not the phone's; green here is
  necessary, never sufficient. **This lane requires a macOS or Linux host**: on
  Windows Robolectric falls back to legacy SQLite (3.7.10), whose `PRAGMA table_info`
  cannot express composite primary keys, so Room schema validation fails falsely for
  entities with compound primary keys. On Windows, use the device lane only.
- **Device lane (truth)**: `app/src/androidTest`, run with

  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

  against a **running API 26+ emulator** (Device Manager → start one first).
  Expected: `BUILD SUCCESSFUL` and a green report at
  `app/build/reports/androidTests/connected/`. **Never point this at the phone**:
  the debug test APK shares the release applicationId and cannot install next to
  the real app (see First run) — and uninstalling the release app to make room
  would delete your training history.

Migration tests must pass in both lanes before a schema change ships.

Repositories, ViewModels and Compose screens remain unverified by automation — see
[ROADMAP.md](ROADMAP.md).

## Things that will bite you

**Database schema changes.** The database is version 1 with schema export on. Changing any
`@Entity` means: bump `version`, write a `Migration`, and commit the new
`app/schemas/…/<n>.json`. Never add `fallbackToDestructiveMigration` — it silently erases
the training history this app exists to accumulate.

**The rest timer cannot be tested with the screen on.** Its whole job is firing while the
phone sleeps. Verify with the screen off and the phone untouched; force Doze with
`adb shell dumpsys deviceidle force-idle` to make it deterministic.

**Backups are destructive on restore.** Restore replaces everything. The validator refuses
malformed and empty documents, and a pre-restore snapshot is written to app-private storage
first, but test restores on the emulator, not on the phone holding your real history.

**Release builds are not what you run day to day.** `assembleRelease` needs
`keystore.properties`; without it the APK is unsigned and cannot update your install. See
[SETUP.md](../SETUP.md).

## Useful adb

```bash
adb shell dumpsys deviceidle force-idle          # force Doze, to test the rest timer
adb shell dumpsys deviceidle unforce             # back to normal
adb shell am kill com.sinura.personaltrainer     # simulate process death (keeps saved state)
adb shell am force-stop com.sinura.personaltrainer  # harsher: clears saved state too
adb logcat --pid=$(adb shell pidof com.sinura.personaltrainer)
```

`am kill` is the honest test for "phone sat in my pocket and the OS reclaimed the app".
`force-stop` is a user-initiated kill and legitimately discards saved state.

## Committing

Branch-per-phase: work lands on `claude/phase-<n>-<slug>` branches, one PR per phase,
merged by the owner — see `docs/gameplan/PROTOCOL.md`. (`main` was empty of app code
until 20 Aug 2026; do not trust older claims of trunk-based flow.)

Write commit messages that explain **why**, not what — the diff already says what. The
existing history is the model to follow.
